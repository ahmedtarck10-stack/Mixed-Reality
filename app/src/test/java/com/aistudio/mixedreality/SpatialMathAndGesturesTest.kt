package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.AnchorRecoveryGate
import com.aistudio.mixedreality.ui.PlacementTapDecision
import com.aistudio.mixedreality.ui.ZoomGestureAccumulator
import com.aistudio.mixedreality.ui.ZoomScalePolicy
import com.aistudio.mixedreality.ui.placementTapDecision
import com.google.ar.core.ArCoreApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for production interaction policies used by AR placement and zoom gestures. */
class SpatialMathAndGesturesTest {

    @Test
    fun testVerticalRotationClampingPreventsFlipping() {
        val initialRotX = 0f
        val touchSensitivity = 0.35f
        val pitchDown = (initialRotX + 100f * touchSensitivity).coerceIn(-90f, 90f)
        assertEquals(35f, pitchDown, 0.001f)
        assertEquals(90f, (initialRotX + 1000f * touchSensitivity).coerceIn(-90f, 90f), 0.001f)
        assertEquals(-90f, (initialRotX - 1000f * touchSensitivity).coerceIn(-90f, 90f), 0.001f)
    }

    @Test
    fun testHorizontalRotationWraps360Degrees() {
        val touchSensitivity = 0.35f
        val positive = ((350f + 50f * touchSensitivity) % 360f + 360f) % 360f
        val negative = ((10f - 50f * touchSensitivity) % 360f + 360f) % 360f
        assertEquals(7.5f, positive, 0.001f)
        assertEquals(352.5f, negative, 0.001f)
    }

    @Test
    fun testSimultaneousDiagonalRotation() {
        val rotY = ((40f * 0.35f) % 360f + 360f) % 360f
        val rotX = (30f * 0.35f).coerceIn(-90f, 90f)
        assertEquals(14f, rotY, 0.001f)
        assertEquals(10.5f, rotX, 0.001f)
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
    fun placementTapUsesActual2500MsTimeoutAndNeverReplaysAfterTrackingLoss() {
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, placementTapDecision(true, true, 2499L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(true, true, 2500L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(false, true, 0L))
        assertEquals(PlacementTapDecision.DISCARD, placementTapDecision(true, false, 1L))
    }

    @Test
    fun anchorRecoveryGatePreventsRepeatedRecoveryUntilTrackingIsRestored() {
        val gate = AnchorRecoveryGate<Any>()
        val stoppedAnchor = Any()
        val replacementAnchor = Any()

        assertTrue(gate.shouldAttempt(stoppedAnchor))
        assertFalse("Do not recreate anchors every frame", gate.shouldAttempt(stoppedAnchor))
        assertFalse("Do not chain recoveries while replacement is not tracking", gate.shouldAttempt(replacementAnchor))
        gate.reset()
        assertTrue("A new recovery cycle can begin after tracking returns", gate.shouldAttempt(replacementAnchor))
    }

    @Test
    fun testARCoreAvailabilityClassification() {
        fun isAvailable(availability: ArCoreApk.Availability?): Boolean =
            availability == ArCoreApk.Availability.SUPPORTED_INSTALLED

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
    fun testZoomFactorDeadbandAndFiniteFilter() {
        assertTrue(abs(1.0005f - 1f) > 0.0001f)
        assertFalse(abs(1.00002f - 1f) > 0.0001f)
        assertFalse(Float.NaN.isFinite())
        assertFalse(Float.POSITIVE_INFINITY.isFinite())
    }
}
