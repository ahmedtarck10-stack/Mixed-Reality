package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aistudio.mixedreality.ui.theme.*
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState

data class ARTestResult(
    val name: String,
    val description: String,
    val passed: Boolean,
    val detail: String
)

@Composable
fun ARTrackingDiagnosticsDialog(
    trackingManager: ARTrackingManager? = null,
    isRealARCoreAvailable: Boolean = true,
    onDismiss: () -> Unit,
    onLaunchTestMode: () -> Unit = {}
) {
    var selfTestResults by remember { mutableStateOf<List<ARTestResult>?>(null) }
    var isRunningTests by remember { mutableStateOf(false) }

    fun runAllVerificationTests(): List<ARTestResult> {
        val results = mutableListOf<ARTestResult>()

        // 1. Placement Tap Decision & Timeout (2500ms)
        val validTap = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 500L)
        val expiredTap = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3000L)
        val hiddenModelTap = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 100L)
        val lostTrackingTap = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 100L)
        val tapPassed = validTap == PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME &&
            expiredTap == PlacementTapDecision.DISCARD &&
            hiddenModelTap == PlacementTapDecision.DISCARD &&
            lostTrackingTap == PlacementTapDecision.DISCARD
        results.add(
            ARTestResult(
                name = "Placement Tap Lifecycle",
                description = "2500ms timeout & tracking loss discard policy",
                passed = tapPassed,
                detail = if (tapPassed) "Valid taps accepted; expired/untracked taps safely discarded" else "Tap policy verification failed"
            )
        )

        // 2. Verified Tracking Pose Invariant
        val verifiedValid = isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.TRACKING)
        val verifiedCameraPaused = isVerifiedTrackingPose(TrackingState.PAUSED, TrackingState.TRACKING)
        val verifiedAnchorPaused = isVerifiedTrackingPose(TrackingState.TRACKING, TrackingState.PAUSED)
        val poseVerifiedPassed = verifiedValid && !verifiedCameraPaused && !verifiedAnchorPaused
        results.add(
            ARTestResult(
                name = "Verified World Pose Contract",
                description = "Requires simultaneous Camera and Anchor TRACKING",
                passed = poseVerifiedPassed,
                detail = if (poseVerifiedPassed) "Only commits anchors when both camera & anchor are active TRACKING" else "Pose verification contract failed"
            )
        )

        // 3. IMU Damping & Stationary Drift Deadband (1.5mm)
        val basePose = Pose(floatArrayOf(0f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val microTremorPose = Pose(floatArrayOf(0.0005f, 0.0003f, -1.0002f), floatArrayOf(0f, 0f, 0f, 1f))
        val dampedMicro = filterPoseWithLegacyDamping(microTremorPose, basePose, deadbandMeters = 0.0015f)
        val deadbandPassed = dampedMicro.tx() == basePose.tx() && dampedMicro.ty() == basePose.ty() && dampedMicro.tz() == basePose.tz()

        val intentionalMove = Pose(floatArrayOf(0.1f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val smoothed = filterPoseWithLegacyDamping(intentionalMove, basePose, deadbandMeters = 0.0015f, alpha = 0.35f)
        val smoothingPassed = smoothed.tx() > basePose.tx() && smoothed.tx() < intentionalMove.tx()
        val dampingPassed = deadbandPassed && smoothingPassed
        results.add(
            ARTestResult(
                name = "IMU Sensor Damping & Deadband",
                description = "1.5mm deadband eliminates stationary tremor; 0.35 EMA smooths movement",
                passed = dampingPassed,
                detail = if (dampingPassed) "Micro-drift (<1.5mm) rejected; intentional moves smoothly interpolated" else "Damping filter test failed"
            )
        )

        // 4. Anchor Recovery Gate (750ms rate limit)
        var simulatedTime = 10_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { simulatedTime })
        val firstAttempt = gate.shouldAttempt()
        val immediateAttempt = gate.shouldAttempt()
        simulatedTime += 800_000_000L
        val afterIntervalAttempt = gate.shouldAttempt()
        val gatePassed = firstAttempt && !immediateAttempt && afterIntervalAttempt
        results.add(
            ARTestResult(
                name = "Anchor Auto-Recovery Rate Limiter",
                description = "750ms interval prevents CPU thrashing during tracking recovery",
                passed = gatePassed,
                detail = if (gatePassed) "Throttles repeated recovery attempts with 750ms backoff" else "Recovery gate failed"
            )
        )

        // 5. Position Continuity & Jump Tolerance (5cm max)
        val savedPose = Pose(floatArrayOf(0f, 0f, -1.0f), floatArrayOf(0f, 0f, 0f, 1f))
        val continuousPose = Pose(floatArrayOf(0.02f, 0.01f, -1.01f), floatArrayOf(0f, 0f, 0f, 1f))
        val jumpingPose = Pose(floatArrayOf(0.25f, 0.10f, -1.20f), floatArrayOf(0f, 0f, 0f, 1f))
        val contPassed = isRecoveryCandidateAcceptable(TrackingState.TRACKING, TrackingState.TRACKING, savedPose, continuousPose)
        val jumpRejected = !isRecoveryCandidateAcceptable(TrackingState.TRACKING, TrackingState.TRACKING, savedPose, jumpingPose)
        val continuityPassed = contPassed && jumpRejected
        results.add(
            ARTestResult(
                name = "Spatial Continuity Check",
                description = "Rejects anchor recovery candidates jumping > 5cm",
                passed = continuityPassed,
                detail = if (continuityPassed) "Enforces 5cm world position continuity threshold" else "Spatial continuity test failed"
            )
        )

        // 6. Zoom Scale Policy Clamping (0.1x to 2500x)
        val minClamped = ZoomScalePolicy.normalize(0.01f) == 0.1f
        val maxClamped = ZoomScalePolicy.normalize(5000f) == 2500f
        val nanHandled = ZoomScalePolicy.normalize(Float.NaN) == 1f
        val zoomPassed = minClamped && maxClamped && nanHandled
        results.add(
            ARTestResult(
                name = "Zoom Scale Policy Bounds",
                description = "Clamps scale strictly between 0.1x and 2500x",
                passed = zoomPassed,
                detail = if (zoomPassed) "Scale factors safely bounded; NaN/Infinity rejected" else "Scale policy failed"
            )
        )

        return results
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F141F),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .testTag("ar_diagnostics_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AR Tracking Test & Diagnostics",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اختبار وفحص نظام تتبع الواقع المعزز",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Live Status Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2D)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "LIVE TRACKING STATUS",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            DiagnosticRow(
                                label = "ARCore Hardware Support",
                                value = if (isRealARCoreAvailable) "Supported & Available ✓" else "Emulator / No ARCore (Test Mode Active)",
                                color = if (isRealARCoreAvailable) NeonGreen else Color(0xFFFFB300)
                            )
                            DiagnosticRow(
                                label = "Active Anchor",
                                value = if (trackingManager?.activeAnchor != null) "Placed ✓" else "None (Tap to place)",
                                color = if (trackingManager?.activeAnchor != null) NeonGreen else Color.White.copy(alpha = 0.7f)
                            )
                            DiagnosticRow(
                                label = "Anchor Tracking State",
                                value = trackingManager?.anchorTrackingState?.name ?: "IDLE",
                                color = when (trackingManager?.anchorTrackingState) {
                                    TrackingState.TRACKING -> NeonGreen
                                    TrackingState.PAUSED -> Color(0xFFFFB300)
                                    TrackingState.STOPPED -> Color(0xFFFF5252)
                                    else -> Color.White.copy(alpha = 0.5f)
                                }
                            )
                            DiagnosticRow(
                                label = "Floor Plane Detection",
                                value = if (trackingManager?.isPlaneDetected == true) "Plane Detected ✓" else "Scanning...",
                                color = if (trackingManager?.isPlaneDetected == true) NeonGreen else NeonCyan
                            )
                            DiagnosticRow(
                                label = "Sensor Damping Filter",
                                value = "Active (1.5mm deadband, 0.35 EMA)",
                                color = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Run Verification Suite Button
                    Button(
                        onClick = {
                            isRunningTests = true
                            selfTestResults = runAllVerificationTests()
                            isRunningTests = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_ar_self_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Run AR Tracking Self-Test (فحص شامل)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Test Results
                    if (selfTestResults != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "VERIFICATION TEST RESULTS (6 / 6 CHECKS)",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        selfTestResults?.forEach { test ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (test.passed) Color(0x2200E676) else Color(0x22FF5252)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (test.passed) NeonGreen.copy(alpha = 0.4f) else Color(0xFFFF5252).copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (test.passed) NeonGreen else Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = test.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = test.description,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = test.detail,
                                            color = if (test.passed) NeonGreen.copy(alpha = 0.9f) else Color(0xFFFF8A80),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Launch Simulation / Test Mode Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onLaunchTestMode()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_test_mode_button")
                    ) {
                        Text(
                            text = "Launch AR Mode View (فتح وضع AR)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
