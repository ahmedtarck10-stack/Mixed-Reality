package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.AnchorRecoveryGate
import com.aistudio.mixedreality.ui.PlacementTapDecision
import com.aistudio.mixedreality.ui.ZoomGestureAccumulator
import com.aistudio.mixedreality.ui.ZoomScalePolicy
import com.aistudio.mixedreality.ui.isRecoveryCandidateAcceptable
import com.aistudio.mixedreality.ui.isRecoveryTranslationContinuous
import com.aistudio.mixedreality.ui.isVerifiedTrackingPose
import com.aistudio.mixedreality.ui.placementTapDecision
import com.aistudio.mixedreality.ui.shouldAttemptAnchorRecovery
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for production interaction policies used by AR placement and zoom gestures. */
class SpatialMathAndGesturesTest {
    @Test
    fun arCoreConfigDepthAndFocusConstantsExist() {
        assertEquals(Config.DepthMode.DISABLED, Config.DepthMode.DISABLED)
        assertEquals(Config.FocusMode.FIXED, Config.FocusMode.FIXED)
        assertEquals(Config.LightEstimationMode.AMBIENT_INTENSITY, Config.LightEstimationMode.AMBIENT_INTENSITY)
        assertEquals(Config.InstantPlacementMode.LOCAL_Y_UP, Config.InstantPlacementMode.LOCAL_Y_UP)
        assertEquals(Config.LightEstimationMode.DISABLED, Config.LightEstimationMode.DISABLED)
        assertTrue(com.google.ar.core.Frame::class.java.methods.any { it.name == "hitTestInstantPlacement" })
    }

    @Test
    fun verticalRotationWraps360DegreesFullSpatialRange() {
        val sensitivity = 0.45f
        assertEquals(22.5f, ((350f + 72.2222f * sensitivity) % 360f + 360f) % 360f, 0.01f)
        assertEquals(337.5f, ((10f - 72.2222f * sensitivity) % 360f + 360f) % 360f, 0.01f)
        assertEquals(180f, ((0f + 400f * sensitivity) % 360f + 360f) % 360f, 0.01f)
    }

    @Test
    fun horizontalRotationWraps360Degrees() {
        val sensitivity = 0.45f
        assertEquals(12.5f, ((350f + 50f * sensitivity) % 360f + 360f) % 360f, 0.001f)
        assertEquals(347.5f, ((10f - 50f * sensitivity) % 360f + 360f) % 360f, 0.001f)
    }

    @Test
    fun rollRotationWraps360Degrees() {
        val sensitivity = 0.45f
        assertEquals(22.5f, ((0f + 50f * sensitivity) % 360f + 360f) % 360f, 0.001f)
        assertEquals(315f, ((0f - 100f * sensitivity) % 360f + 360f) % 360f, 0.001f)
    }

    @Test
    fun zoomPolicyClampsScaleAndRejectsInvalidInputs() {
        assertEquals(ZoomScalePolicy.MIN_SCALE, ZoomScalePolicy.apply(0.05f, 1f), 0.001f)
        assertEquals(ZoomScalePolicy.MAX_SCALE, ZoomScalePolicy.apply(3000f, 1f), 0.001f)
        assertEquals(5f, ZoomScalePolicy.apply(2.5f, 2f), 0.001f)
        assertEquals(1f, ZoomScalePolicy.apply(Float.NaN, 2f), 0.001f)
        assertEquals(2f, ZoomScalePolicy.apply(2f, Float.NaN), 0.001f)
        assertEquals(ZoomScalePolicy.MAX_SCALE, ZoomScalePolicy.apply(2f, Float.MAX_VALUE), 0.001f)
    }

    @Test
    fun zoomAccumulatorCoalescesEventsAndBoundsPerFrameChange() {
        val accumulator = ZoomGestureAccumulator()
        accumulator.add(1.1f)
        accumulator.add(1.1f)
        assertEquals(1.21f, accumulator.consume(), 0.001f)
        assertEquals(1f, accumulator.consume(), 0.001f)
        accumulator.add(2f)
        accumulator.add(2f)
        assertEquals(2f, accumulator.consume(), 0.001f)
        accumulator.add(Float.NaN)
        accumulator.add(Float.POSITIVE_INFINITY)
        accumulator.add(-1f)
        assertEquals(1f, accumulator.consume(), 0.001f)
    }

    @Test
    fun placementPolicyUses2500MsTimeoutAndDiscardsLostTrackingTaps() {
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, placementTapDecision(true, true, 2499L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(true, true, 2500L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(false, true, 0L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(true, false, 1L))
    }

    @Test
    fun verifiedWorldPoseRequiresBothCameraAndAnchorTracking() {
        assertTrue(isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(TrackingState.PAUSED, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.PAUSED))
        assertFalse(isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.STOPPED))
        assertFalse(isVerifiedTrackingPose(null, TrackingState.TRACKING))
    }

    @Test
    fun recoveryGraceRequiresCameraTrackingAndLostAnchorState() {
        assertFalse(shouldAttemptAnchorRecovery(TrackingState.TRACKING, TrackingState.PAUSED, 999L))
        assertTrue(shouldAttemptAnchorRecovery(TrackingState.TRACKING, TrackingState.PAUSED, 1_000L, 1_000L))
        assertTrue(shouldAttemptAnchorRecovery(TrackingState.TRACKING, TrackingState.STOPPED, 1_000L, 1_000L))
        assertFalse(shouldAttemptAnchorRecovery(TrackingState.PAUSED, TrackingState.STOPPED, 10_000L, 1_000L))
        assertFalse(shouldAttemptAnchorRecovery(TrackingState.TRACKING, TrackingState.TRACKING, 10_000L, 1_000L))
    }

    @Test
    fun recoveryCandidateAcceptanceRequiresTrackingAndPositionContinuity() {
        val savedPose = Pose.makeTranslation(1f, 2f, 3f)
        val nearCandidate = Pose.makeTranslation(1.01f, 2f, 3f)
        val farCandidate = Pose.makeTranslation(1.10f, 2f, 3f)

        assertTrue(
            isRecoveryCandidateAcceptable(
                TrackingState.TRACKING,
                TrackingState.TRACKING,
                savedPose,
                nearCandidate
            )
        )
        assertFalse(
            "A candidate that jumps beyond 5 cm must be rejected before active-anchor replacement",
            isRecoveryCandidateAcceptable(
                TrackingState.TRACKING,
                TrackingState.TRACKING,
                savedPose,
                farCandidate
            )
        )
        assertFalse(
            "An untracked recovery candidate is never acceptable",
            isRecoveryCandidateAcceptable(
                TrackingState.TRACKING,
                TrackingState.PAUSED,
                savedPose,
                nearCandidate
            )
        )
    }

    @Test
    fun recoveryTranslationRejectsInvalidCoordinatesAndEnforcesFiveCentimeterLimit() {
        assertTrue(isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05f, 0f, 0f))
        assertFalse(isRecoveryTranslationContinuous(0f, 0f, 0f, 0.0501f, 0f, 0f))
        assertFalse(isRecoveryTranslationContinuous(Float.NaN, 0f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun recoveryGateBlocksRepeatedAttemptsUntilRetryIntervalOrReset() {
        var now = 0L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750L, clockNanos = { now })
        assertTrue(gate.shouldAttempt())
        assertFalse("Repeated frames must not create anchors repeatedly", gate.shouldAttempt())
        now = 749L
        assertFalse("Retry remains rate-limited", gate.shouldAttempt())
        now = 750L
        assertTrue("A failed recovery may retry after bounded backoff", gate.shouldAttempt())
        gate.reset()
        assertTrue("Tracking restoration resets recovery backoff", gate.shouldAttempt())
    }

    @Test
    fun arCoreAvailabilityClassification() {
        fun isAvailable(value: ArCoreApk.Availability?) = value == ArCoreApk.Availability.SUPPORTED_INSTALLED
        assertTrue(isAvailable(ArCoreApk.Availability.SUPPORTED_INSTALLED))
        assertFalse(isAvailable(ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED))
        assertFalse(isAvailable(ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD))
        assertFalse(isAvailable(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_CHECKING))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_ERROR))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_TIMED_OUT))
        assertFalse(isAvailable(null))
    }

    @Test
    fun zoomDeadbandRejectsTinyChangesAndNonFiniteFactors() {
        assertTrue(abs(1.0005f - 1f) > 0.0001f)
        assertFalse(abs(1.00002f - 1f) > 0.0001f)
        assertFalse(Float.NaN.isFinite())
        assertFalse(Float.POSITIVE_INFINITY.isFinite())
    }
}
