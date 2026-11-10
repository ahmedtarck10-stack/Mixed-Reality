package com.aistudio.mixedreality

import androidx.compose.ui.geometry.Offset
import com.aistudio.mixedreality.ui.*
import com.aistudio.mixedreality.ui.theme.NeonCyan
import com.aistudio.mixedreality.ui.theme.NeonGreen
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import org.junit.Assert.*
import org.junit.Test

class ARTrackingSelfTestVerification {

    @Test
    fun test01_placementTapLifecycleEnforces2500MsTimeout() {
        // Valid tap within 500ms
        assertEquals(
            PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME,
            placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 500L)
        )
        // Expired tap at 2501ms
        assertEquals(
            PlacementTapDecision.DISCARD,
            placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2501L)
        )
        // Hidden model tap discarded
        assertEquals(
            PlacementTapDecision.DISCARD,
            placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 100L)
        )
        // Lost camera tracking tap discarded
        assertEquals(
            PlacementTapDecision.DISCARD,
            placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 100L)
        )
    }

    @Test
    fun test02_verifiedWorldPoseRequiresSimultaneousCameraAndAnchorTracking() {
        assertTrue(isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(TrackingState.PAUSED, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.PAUSED))
        assertFalse(isVerifiedTrackingPose(TrackingState.STOPPED, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(null, TrackingState.TRACKING))
        assertFalse(isVerifiedTrackingPose(TrackingState.TRACKING, null))
    }

    @Test
    fun test03_imuDampingDeadbandRejectsMicroTremorsBelow1_5Mm() {
        val basePose = Pose(floatArrayOf(0f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val microTremorPose = Pose(floatArrayOf(0.0004f, 0.0003f, -1.0002f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(microTremorPose, basePose, deadbandMeters = 0.0015f)

        assertEquals(basePose.tx(), filtered.tx(), 0.00001f)
        assertEquals(basePose.ty(), filtered.ty(), 0.00001f)
        assertEquals(basePose.tz(), filtered.tz(), 0.00001f)
    }

    @Test
    fun test04_imuDampingAppliesLowPassSmoothingOnIntentionalMotion() {
        val basePose = Pose(floatArrayOf(0f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val intentionalMovement = Pose(floatArrayOf(0.10f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val smoothed = filterPoseWithLegacyDamping(intentionalMovement, basePose, deadbandMeters = 0.0015f, alpha = 0.35f)

        assertTrue(smoothed.tx() > basePose.tx())
        assertTrue(smoothed.tx() < intentionalMovement.tx())
        assertEquals(0.035f, smoothed.tx(), 0.001f)
    }

    @Test
    fun test05_anchorRecoveryGateRateLimitsAttemptsTo750MsInterval() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })

        assertTrue(gate.shouldAttempt())
        assertFalse(gate.shouldAttempt())

        clock += 500_000_000L // 500ms later - still blocked
        assertFalse(gate.shouldAttempt())

        clock += 300_000_000L // 800ms total - allowed
        assertTrue(gate.shouldAttempt())
    }

    @Test
    fun test06_spatialContinuityRejectsWorldPositionJumpsExceedingFiveCentimeters() {
        val saved = Pose(floatArrayOf(0f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val continuous = Pose(floatArrayOf(0.02f, 0.01f, -1.01f), floatArrayOf(0f, 0f, 0f, 1f))
        val jump = Pose(floatArrayOf(0.15f, 0.10f, -1.15f), floatArrayOf(0f, 0f, 0f, 1f))

        assertTrue(isRecoveryCandidateAcceptable(TrackingState.TRACKING, TrackingState.TRACKING, saved, continuous))
        assertFalse(isRecoveryCandidateAcceptable(TrackingState.TRACKING, TrackingState.TRACKING, saved, jump))
    }

    @Test
    fun test07_zoomScalePolicyClampsWithinExpectedRange() {
        assertEquals(0.1f, ZoomScalePolicy.normalize(0.001f), 0.001f)
        assertEquals(2500f, ZoomScalePolicy.normalize(10000f), 0.001f)
        assertEquals(1.0f, ZoomScalePolicy.normalize(Float.NaN), 0.001f)
        assertEquals(1.0f, ZoomScalePolicy.normalize(-5.0f), 0.001f)
    }

    @Test
    fun test08_arTrackingManagerDefaultHudState() {
        val manager = ARTrackingManager(modeName = "AR_TEST")
        val (text, color) = manager.getHudStatus()
        assertEquals("Point camera at floor and move slowly", text)
        assertEquals(NeonCyan, color)
    }
}
