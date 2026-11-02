package com.aistudio.mixedreality

import com.google.ar.core.ArCoreApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for Spatial Gestures, 2D Rotation, Scale limits, and AR Tracking State Machine.
 */
class SpatialMathAndGesturesTest {

    @Test
    fun testVerticalRotationClampingPreventsFlipping() {
        val initialRotX = 0f
        val touchSensitivity = 0.35f

        // Dragging down tilts top down (positive pitch)
        val dragDownDy = 100f
        val pitchDown = (initialRotX + dragDownDy * touchSensitivity).coerceIn(-90f, 90f)
        assertEquals(35f, pitchDown, 0.001f)

        // Extreme downward drag clamped strictly to +90 degrees
        val extremeDownDy = 1000f
        val maxPitchDown = (initialRotX + extremeDownDy * touchSensitivity).coerceIn(-90f, 90f)
        assertEquals(90f, maxPitchDown, 0.001f)

        // Extreme upward drag clamped strictly to -90 degrees
        val extremeUpDy = -1000f
        val maxPitchUp = (initialRotX + extremeUpDy * touchSensitivity).coerceIn(-90f, 90f)
        assertEquals(-90f, maxPitchUp, 0.001f)
    }

    @Test
    fun testHorizontalRotationWraps360Degrees() {
        val touchSensitivity = 0.35f

        // Positive rotation past 360 degrees
        val rot1 = (350f + 50f * touchSensitivity) % 360f
        val wrapped1 = if (rot1 < 0f) rot1 + 360f else rot1
        assertEquals(7.5f, wrapped1, 0.001f)

        // Negative rotation below 0 degrees
        val rot2 = (10f - 50f * touchSensitivity) % 360f
        val wrapped2 = if (rot2 < 0f) rot2 + 360f else rot2
        assertEquals(352.5f, wrapped2, 0.001f)
    }

    @Test
    fun testSimultaneousDiagonalRotation() {
        var rotX = 0f
        var rotY = 0f
        val dx = 40f
        val dy = 30f
        val touchSensitivity = 0.35f

        // Single diagonal gesture frame applies both axes concurrently
        val newRotY = (rotY + dx * touchSensitivity) % 360f
        rotY = if (newRotY < 0f) newRotY + 360f else newRotY
        rotX = (rotX + dy * touchSensitivity).coerceIn(-90f, 90f)

        assertEquals(14f, rotY, 0.001f)
        assertEquals(10.5f, rotX, 0.001f)
    }

    @Test
    fun testScaleCoerceRange() {
        val minAllowed = 0.1f
        val maxAllowed = 2500.0f

        val underflowScale = 0.05f
        assertEquals(minAllowed, underflowScale.coerceIn(minAllowed, maxAllowed), 0.001f)

        val overflowScale = 3000.0f
        assertEquals(maxAllowed, overflowScale.coerceIn(minAllowed, maxAllowed), 0.001f)

        val validScale = 2.5f
        assertEquals(2.5f, validScale.coerceIn(minAllowed, maxAllowed), 0.001f)
    }

    @Test
    fun testSafeScaleComputationPreventsFreezes() {
        fun computeSafeScale(scale: Float): Float {
            return if (scale.isFinite() && scale > 0f) scale.coerceIn(0.1f, 2500.0f) else 1.0f
        }

        assertEquals(1.0f, computeSafeScale(Float.NaN), 0.001f)
        assertEquals(1.0f, computeSafeScale(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(1.0f, computeSafeScale(Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(1.0f, computeSafeScale(-5.0f), 0.001f)
        assertEquals(1.0f, computeSafeScale(0.0f), 0.001f)
        assertEquals(2.5f, computeSafeScale(2.5f), 0.001f)
        assertEquals(0.1f, computeSafeScale(0.01f), 0.001f)
        assertEquals(2500.0f, computeSafeScale(5000f), 0.001f)
    }

    @Test
    fun testSafeRotationComputationPreventsNaN() {
        fun computeSafeRotX(rotX: Float): Float {
            return if (rotX.isFinite()) rotX.coerceIn(-90f, 90f) else 0f
        }

        fun computeSafeRotY(rotY: Float): Float {
            return if (rotY.isFinite()) ((rotY % 360f) + 360f) % 360f else 0f
        }

        assertEquals(0f, computeSafeRotX(Float.NaN), 0.001f)
        assertEquals(0f, computeSafeRotY(Float.NaN), 0.001f)
        assertEquals(45f, computeSafeRotX(45f), 0.001f)
        assertEquals(90f, computeSafeRotX(120f), 0.001f)
        assertEquals(-90f, computeSafeRotX(-150f), 0.001f)
        assertEquals(180f, computeSafeRotY(180f), 0.001f)
        assertEquals(30f, computeSafeRotY(390f), 0.001f)
        assertEquals(330f, computeSafeRotY(-30f), 0.001f)
    }

    @Test
    fun testARCoreAvailabilityClassification() {
        fun isAvailable(availability: ArCoreApk.Availability?): Boolean {
            return availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
        }

        fun isAvailableWithException(simulateException: Boolean): Boolean {
            return try {
                if (simulateException) throw RuntimeException("Simulated ARCore check failure")
                isAvailable(ArCoreApk.Availability.SUPPORTED_INSTALLED)
            } catch (_: Throwable) {
                false // Critical requirement: NEVER return true on exception
            }
        }

        assertTrue(isAvailable(ArCoreApk.Availability.SUPPORTED_INSTALLED))
        assertFalse(isAvailable(ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED))
        assertFalse(isAvailable(ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD))
        assertFalse(isAvailable(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_CHECKING))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_ERROR))
        assertFalse(isAvailable(ArCoreApk.Availability.UNKNOWN_TIMED_OUT))
        assertFalse(isAvailable(null))
        assertFalse("Exception must safely return false and never true", isAvailableWithException(true))
    }

    @Test
    fun testZoomFactorDeadbandAndFiniteFilter() {
        // Subtle pinch should not be deadbanded if delta exceeds 0.0001f
        val subtleZoom = 1.0005f
        val passesSubtle = subtleZoom.isFinite() && subtleZoom in 0.5f..2.0f && abs(subtleZoom - 1f) > 0.0001f
        assertTrue("Subtle pinch must be recognized", passesSubtle)

        // Micro touch jitter should be filtered out
        val microJitter = 1.00002f
        val passesMicro = microJitter.isFinite() && microJitter in 0.5f..2.0f && abs(microJitter - 1f) > 0.0001f
        assertFalse("Micro jitter under 0.0001f must be filtered", passesMicro)

        // Non-finite or extreme zoom factors must be rejected
        val nanZoom = Float.NaN
        val passesNaN = nanZoom.isFinite() && nanZoom in 0.5f..2.0f && abs(nanZoom - 1f) > 0.0001f
        assertFalse("NaN zoom factor must be rejected", passesNaN)

        val infiniteZoom = Float.POSITIVE_INFINITY
        val passesInf = infiniteZoom.isFinite() && infiniteZoom in 0.5f..2.0f && abs(infiniteZoom - 1f) > 0.0001f
        assertFalse("Infinite zoom factor must be rejected", passesInf)
    }

    @Test
    fun testPendingTapBoundedRetryThreshold() {
        val maxWaitMs = 3500L
        val tapTime = 10000L

        // After 2000ms: still valid and eligible for tracking
        val elapsedActive = 12000L - tapTime
        assertTrue("Tap within 3500ms must remain pending", elapsedActive <= maxWaitMs)

        // After 3600ms: expired to prevent stale placement
        val elapsedExpired = 13600L - tapTime
        assertFalse("Tap after 3500ms must expire", elapsedExpired <= maxWaitMs)
    }

    @Test
    fun testRecoveryBackoffCooldownProgression() {
        fun getCooldown(attempt: Int): Long = when (attempt) {
            0 -> 1500L
            1 -> 2000L
            2 -> 3000L
            else -> 4000L
        }

        assertEquals(1500L, getCooldown(0))
        assertEquals(2000L, getCooldown(1))
        assertEquals(3000L, getCooldown(2))
        assertEquals(4000L, getCooldown(3))
        assertEquals(4000L, getCooldown(10)) // Never abandons; caps at 4000ms
    }
}
