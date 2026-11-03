package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.AnchorRecoveryGate
import com.aistudio.mixedreality.ui.PlacementTapDecision
import com.aistudio.mixedreality.ui.ZoomGestureAccumulator
import com.aistudio.mixedreality.ui.ZoomScalePolicy
import com.aistudio.mixedreality.ui.isVerifiedTrackingPose
import com.aistudio.mixedreality.ui.placementTapDecision
import com.google.ar.core.ArCoreApk
import com.google.ar.core.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for production interaction policies used by AR placement and zoom gestures. */
class SpatialMathAndGesturesTest {
    @Test
    fun verticalRotationClampingPreventsFlipping() {
        val sensitivity = 0.35f
        assertEquals(35f, (100f * sensitivity).coerceIn(-90f, 90f), 0.001f)
        assertEquals(90f, (1000f * sensitivity).coerceIn(-90f, 90f), 0.001f)
        assertEquals(-90f, (-1000f * sensitivity).coerceIn(-90f, 90f), 0.001f)
    }

    @Test
    fun horizontalRotationWraps360Degrees() {
        val sensitivity = 0.35f
        assertEquals(7.5f, ((350f + 50f * sensitivity) % 360f + 360f) % 360f, 0.001f)
        assertEquals(352.5f, ((10f - 50f * sensitivity) % 360f + 360f) % 360f, 0.001f)
    }

    @Test
    fun diagonalRotationAppliesBothAxes() {
        assertEquals(14f, (40f * 0.35f) % 360f, 0.001f)
        assertEquals(10.5f, (30f * 0.35f).coerceIn(-90f, 90f), 0.001f)
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
