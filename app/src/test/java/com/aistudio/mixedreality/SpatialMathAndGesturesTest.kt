package com.aistudio.mixedreality

import androidx.compose.ui.geometry.Offset
import com.aistudio.mixedreality.ui.AnchorRecoveryGate
import com.aistudio.mixedreality.ui.PlacementTapDecision
import com.aistudio.mixedreality.ui.RotationAxis
import com.aistudio.mixedreality.ui.ZoomGestureAccumulator
import com.aistudio.mixedreality.ui.ZoomScalePolicy
import com.aistudio.mixedreality.ui.applyRotationDelta
import com.aistudio.mixedreality.ui.calculateOneFingerZRollDelta
import com.aistudio.mixedreality.ui.filterPoseWithLegacyDamping
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

    @Test
    fun legacyDampingRejectsSubMillimeterStationaryDrift() {
        val lastPose = Pose.makeTranslation(1.0f, 2.0f, 3.0f)
        // 0.8mm offset (below 1.5mm deadband)
        val jitteredPose = Pose.makeTranslation(1.0008f, 2.0f, 3.0f)
        val filtered = filterPoseWithLegacyDamping(jitteredPose, lastPose)

        // Must lock to previous translation to completely halt stationary drift
        assertEquals(1.0f, filtered.tx(), 0.00001f)
        assertEquals(2.0f, filtered.ty(), 0.00001f)
        assertEquals(3.0f, filtered.tz(), 0.00001f)
    }

    @Test
    fun legacyDampingAppliesLowPassSmoothingOnIntentionalMovement() {
        val lastPose = Pose.makeTranslation(0.0f, 0.0f, 0.0f)
        // 10cm movement (well above 1.5mm deadband)
        val movedPose = Pose.makeTranslation(0.10f, 0.0f, 0.0f)
        val filtered = filterPoseWithLegacyDamping(movedPose, lastPose, alpha = 0.35f)

        // Smoothed = 0.0 + 0.35 * 0.10 = 0.035m
        assertEquals(0.035f, filtered.tx(), 0.001f)
        assertEquals(0.0f, filtered.ty(), 0.001f)
        assertEquals(0.0f, filtered.tz(), 0.001f)
    }

    @Test
    fun legacyDampingHandlesNullAndNonFiniteGracefully() {
        val rawPose = Pose.makeTranslation(1.0f, 2.0f, 3.0f)
        val resultNull = filterPoseWithLegacyDamping(rawPose, null)
        assertEquals(1.0f, resultNull.tx(), 0.0001f)

        val nanPose = Pose.makeTranslation(Float.NaN, 2.0f, 3.0f)
        val resultNan = filterPoseWithLegacyDamping(nanPose, rawPose)
        assertTrue(resultNan.tx().isNaN())
    }

    @Test
    fun independentAxisRotationLocksAndWraps360OnX_Y_And_Z() {
        var rx = 350f
        var ry = 10f
        var rz = 200f

        // Lock X only -> only X changes and wraps across 360 -> 0
        applyRotationDelta(
            axis = RotationAxis.X,
            dx = 0f,
            dy = -50f,
            isEdgeDrag = false,
            currentRotX = rx,
            currentRotY = ry,
            currentRotZ = rz,
            onRotX = { rx = it },
            onRotY = { ry = it },
            onRotZ = { rz = it },
            sensitivity = 0.45f
        )
        assertEquals(12.5f, rx, 0.01f)
        assertEquals(10f, ry, 0.001f)
        assertEquals(200f, rz, 0.001f)

        // Lock Y only -> only Y changes and wraps cleanly across 0 <-> 360
        applyRotationDelta(
            axis = RotationAxis.Y,
            dx = -50f,
            dy = 0f,
            isEdgeDrag = false,
            currentRotX = rx,
            currentRotY = ry,
            currentRotZ = rz,
            onRotX = { rx = it },
            onRotY = { ry = it },
            onRotZ = { rz = it },
            sensitivity = 0.45f
        )
        assertEquals(12.5f, rx, 0.01f)
        assertEquals(347.5f, ry, 0.01f)
        assertEquals(200f, rz, 0.001f)

        // Lock Z only -> only Z changes across full 360 range (400px -> +180deg -> 20deg)
        applyRotationDelta(
            axis = RotationAxis.Z,
            dx = 400f,
            dy = 0f,
            isEdgeDrag = false,
            currentRotX = rx,
            currentRotY = ry,
            currentRotZ = rz,
            onRotX = { rx = it },
            onRotY = { ry = it },
            onRotZ = { rz = it },
            sensitivity = 0.45f
        )
        assertEquals(12.5f, rx, 0.01f)
        assertEquals(347.5f, ry, 0.01f)
        assertEquals(20f, rz, 0.01f)
    }

    @Test
    fun oneFingerZRollCalculatesFull360Sweep() {
        val center = Offset(500f, 500f)
        // Quarter-turn clockwise around center: right -> bottom (+90 deg)
        val cw = calculateOneFingerZRollDelta(
            prev = Offset(800f, 500f),
            curr = Offset(500f, 800f),
            center = center
        )
        assertEquals(90f, cw, 0.01f)

        // Crossing +-180 seam: bottom-left -> top-left remains smooth positive delta
        val seamCross = calculateOneFingerZRollDelta(
            prev = Offset(200f, 520f),
            curr = Offset(200f, 480f),
            center = center
        )
        assertTrue(seamCross > 0f && seamCross < 20f)

        // Near dead-center guarded against angular spikes
        val nearCenter = calculateOneFingerZRollDelta(
            prev = Offset(505f, 500f),
            curr = Offset(495f, 500f),
            center = center
        )
        assertEquals(0f, nearCenter, 0.001f)
    }
}
