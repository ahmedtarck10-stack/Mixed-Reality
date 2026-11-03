package com.aistudio.mixedreality.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aistudio.mixedreality.ui.theme.NeonCyan
import com.aistudio.mixedreality.ui.theme.NeonGreen
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ARTracking"
private const val PLANE_SCAN_INTERVAL_NANOS = 250_000_000L

/** A screen-space tap waiting for a valid current camera frame. */
data class PendingPlacementTap(
    val offset: Offset,
    val creationTime: Long = System.currentTimeMillis()
)

/** Owns AR anchors and resolves user placement taps against tracked horizontal planes. */
class ARTrackingManager(
    val modeName: String,
    val onHapticFeedback: () -> Unit = {}
) {
    var activeAnchor by mutableStateOf<Anchor?>(null)
        private set

    var anchorTrackingState by mutableStateOf<TrackingState?>(null)
        private set

    var isPlaneDetected by mutableStateOf(false)
        private set

    private val pendingPlacementRef = AtomicReference<PendingPlacementTap?>(null)
    private val recoveryGate = AnchorRecoveryGate()
    private var lastCameraTrackingState: TrackingState? = null
    private var lastPlaneScanNanos = 0L
    private var lastRecoveryPlaneScanNanos = 0L
    private var cachedHasTrackedHorizontalSurface = false

    // Only an anchor pose read while both anchor and camera track can become this baseline.
    private var lastVerifiedWorldPose: Pose? = null
    private var pendingPlacementAnchor: Anchor? = null
    private var pendingPlacementPlane: Plane? = null
    private var recoveryCandidate: Anchor? = null
    private var recoveryPlane: Plane? = null
    private var trackingLostSinceNanos: Long? = null

    fun onSingleTap(tapOffset: Offset, isModelVisible: Boolean) {
        if (!isModelVisible) {
            Log.d(TAG, "[$modeName] Tap ignored: model is not visible")
            return
        }
        pendingPlacementRef.set(PendingPlacementTap(offset = tapOffset))
        Log.d(TAG, "[$modeName] Placement tap queued at ($tapOffset)")
    }

    fun onAnchorTrackingStateChanged(newState: TrackingState) {
        if (anchorTrackingState != newState) {
            Log.i(TAG, "[$modeName] Anchor tracking state changed: $anchorTrackingState -> $newState")
            anchorTrackingState = newState
        }
    }

    fun onSessionUpdated(
        session: Session,
        frame: Frame,
        isModelVisible: Boolean,
        splitWidthPx: Float = 0f,
        onPlaced: () -> Unit = {}
    ) {
        val cameraState = runCatching { frame.camera.trackingState }.getOrNull()
        if (lastCameraTrackingState != cameraState) {
            Log.i(TAG, "[$modeName] Camera tracking state changed: $lastCameraTrackingState -> $cameraState")
            lastCameraTrackingState = cameraState
        }

        val pending = pendingPlacementRef.get()
        if (pending != null) {
            when (placementTapDecision(
                isModelVisible = isModelVisible,
                cameraIsTracking = cameraState == TrackingState.TRACKING,
                elapsedMs = (System.currentTimeMillis() - pending.creationTime).coerceAtLeast(0L)
            )) {
                PlacementTapDecision.DISCARD -> {
                    pendingPlacementRef.compareAndSet(pending, null)
                    Log.d(TAG, "[$modeName] Discarded expired, hidden-model, or untracked placement tap")
                }
                PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME -> if (pendingPlacementRef.compareAndSet(pending, null)) {
                    val hitX = if (splitWidthPx > 0f && pending.offset.x > splitWidthPx) {
                        pending.offset.x - splitWidthPx
                    } else {
                        pending.offset.x
                    }
                    val floorHit = runCatching { frame.hitTest(hitX, pending.offset.y) }
                        .onFailure { Log.w(TAG, "[$modeName] Hit test failed", it) }
                        .getOrNull()
                        .orEmpty()
                        .firstOrNull { hit ->
                            val plane = hit.trackable as? Plane
                            plane != null && isEligiblePhysicalFloorPlane(
                                plane = plane,
                                cameraIsTracking = cameraState == TrackingState.TRACKING,
                                hitPose = hit.hitPose
                            )
                        }
                    val candidate = floorHit?.let { hit ->
                        runCatching { hit.createAnchor() }
                            .onFailure { Log.w(TAG, "[$modeName] Could not create anchor from valid physical floor hit", it) }
                            .getOrNull()
                    }
                    when {
                        candidate == null -> Log.w(TAG, "[$modeName] No tracked physical horizontal plane hit inside its polygon")
                        runCatching { candidate.trackingState }.getOrNull() == TrackingState.STOPPED -> {
                            runCatching { candidate.detach() }
                            Log.w(TAG, "[$modeName] Placement rejected: new anchor is STOPPED")
                        }
                        else -> {
                            val pose = verifiedAnchorPose(candidate, cameraState)
                            val plane = floorHit?.trackable as? Plane
                            if (pose != null && plane != null && isEligiblePhysicalFloorPlane(plane, cameraState == TrackingState.TRACKING, pose)) {
                                promotePlacementAnchor(candidate, pose, onPlaced)
                                onHapticFeedback()
                            } else {
                                runCatching { pendingPlacementAnchor?.detach() }
                                pendingPlacementAnchor = candidate
                                pendingPlacementPlane = plane
                                Log.i(TAG, "[$modeName] Physical floor-hit anchor is awaiting valid tracking")
                            }
                        }
                    }
                }
            }
        }

        pendingPlacementAnchor?.let { candidate ->
            val pose = verifiedAnchorPose(candidate, cameraState)
            val plane = pendingPlacementPlane
            when {
                pose != null && plane != null && isEligiblePhysicalFloorPlane(
                    plane = plane,
                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                    hitPose = pose
                ) -> {
                    promotePlacementAnchor(candidate, pose, onPlaced)
                    onHapticFeedback()
                }
                runCatching { candidate.trackingState }.getOrNull() == TrackingState.STOPPED ||
                    plane == null || (pose != null && !isEligiblePhysicalFloorPlane(plane, cameraState == TrackingState.TRACKING, pose)) -> {
                    runCatching { candidate.detach() }
                    pendingPlacementAnchor = null
                    pendingPlacementPlane = null
                    Log.w(TAG, "[$modeName] Pending placement rejected: its physical floor plane is no longer valid")
                }
            }
        }

        val anchor = activeAnchor
        if (anchor != null) {
            val state = runCatching { anchor.trackingState }.getOrNull()
            if (state != null && anchorTrackingState != state) {
                anchorTrackingState = state
                Log.i(TAG, "[$modeName] Active anchor tracking state: $state")
            }

            val verifiedPose = verifiedAnchorPose(anchor, cameraState)
            if (verifiedPose != null) {
                lastVerifiedWorldPose = verifiedPose
                trackingLostSinceNanos = null
                // Natural relocalization wins; discard any unused replacement.
                runCatching { recoveryCandidate?.detach() }
                recoveryCandidate = null
                recoveryPlane = null
                recoveryGate.reset()
            } else if (shouldAttemptAnchorRecovery(
                    cameraState = cameraState,
                    anchorState = state,
                    lostDurationNanos = trackingLossDurationNanos(cameraState)
                )
            ) {
                val originalPose = lastVerifiedWorldPose
                val candidate = recoveryCandidate
                if (candidate != null) {
                    val candidateState = runCatching { candidate.trackingState }.getOrNull()
                    val candidatePose = verifiedAnchorPose(candidate, cameraState)
                    val plane = recoveryPlane
                    val planeStillValid = plane != null && candidatePose != null &&
                        isEligiblePhysicalFloorPlane(plane, cameraState == TrackingState.TRACKING, candidatePose)
                    when {
                        planeStillValid && isRecoveryCandidateAcceptable(
                            cameraState, candidateState, originalPose, candidatePose
                        ) -> {
                            val safeCandidatePose = candidatePose ?: return
                            if (isRecoveryTranslationContinuous(
                                    originalPose?.tx() ?: Float.NaN,
                                    originalPose?.ty() ?: Float.NaN,
                                    originalPose?.tz() ?: Float.NaN,
                                    safeCandidatePose.tx(),
                                    safeCandidatePose.ty(),
                                    safeCandidatePose.tz()
                                )
                            ) {
                                activeAnchor = candidate
                                anchorTrackingState = TrackingState.TRACKING
                                lastVerifiedWorldPose = originalPose
                                recoveryCandidate = null
                                recoveryPlane = null
                                trackingLostSinceNanos = null
                                recoveryGate.reset()
                                runCatching { anchor.detach() }
                                Log.i(TAG, "[$modeName] Recovered on a tracked physical floor plane within 5 cm")
                            } else {
                                rejectRecoveryCandidate(candidate, "would jump >5 cm")
                            }
                        }
                        candidateState == TrackingState.STOPPED || candidateState == null || !planeStillValid ->
                            rejectRecoveryCandidate(candidate, "physical plane stopped or no longer contains the saved pose")
                        // A PAUSED candidate on its still-tracked physical plane is kept and polled.
                    }
                } else if (originalPose != null) {
                    val now = System.nanoTime()
                    val plane = if (now - lastRecoveryPlaneScanNanos >= PLANE_SCAN_INTERVAL_NANOS) {
                        lastRecoveryPlaneScanNanos = now
                        runCatching {
                            session.getAllTrackables(Plane::class.java).firstOrNull { trackedPlane ->
                                isEligiblePhysicalFloorPlane(
                                    plane = trackedPlane,
                                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                                    hitPose = originalPose
                                )
                            }
                        }.onFailure {
                            Log.w(TAG, "[$modeName] Could not inspect tracked physical planes for recovery", it)
                        }.getOrNull()
                    } else {
                        null
                    }
                    if (plane != null && recoveryGate.shouldAttempt()) {
                        recoveryPlane = plane
                        recoveryCandidate = runCatching { plane.createAnchor(originalPose) }
                            .onFailure { Log.w(TAG, "[$modeName] Physical-plane recovery anchor creation failed", it) }
                            .getOrNull()
                        if (recoveryCandidate == null) {
                            recoveryPlane = null
                            Log.w(TAG, "[$modeName] Recovery failed; retry is rate-limited")
                        }
                    } else if (plane == null) {
                        Log.w(TAG, "[$modeName] Recovery rejected: saved pose is not inside a tracked physical floor plane")
                    }
                } else {
                    Log.w(TAG, "[$modeName] No verified world pose; refusing arbitrary recovery")
                }
            } else if (cameraState != TrackingState.TRACKING) {
                trackingLostSinceNanos = null
            }
        }

        val now = System.nanoTime()
        if (shouldRefreshPlaneDetection(cameraState, now)) {
            cachedHasTrackedHorizontalSurface = detectTrackedHorizontalSurface(session, cameraState)
            lastPlaneScanNanos = now
        }
        if (cachedHasTrackedHorizontalSurface != isPlaneDetected) {
            isPlaneDetected = cachedHasTrackedHorizontalSurface
            Log.d(TAG, "[$modeName] Tracked physical horizontal plane detected: $cachedHasTrackedHorizontalSurface")
        }
    }

    private fun rejectRecoveryCandidate(candidate: Anchor, reason: String) {
        runCatching { candidate.detach() }
        recoveryCandidate = null
        recoveryPlane = null
        Log.w(TAG, "[$modeName] Recovery rejected: $reason; kept active anchor and verified pose")
    }

    private fun isEligiblePhysicalFloorPlane(
        plane: Plane,
        cameraIsTracking: Boolean,
        hitPose: Pose?
    ): Boolean = cameraIsTracking &&
        plane.trackingState == TrackingState.TRACKING &&
        plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
        plane.subsumedBy == null &&
        hitPose != null && runCatching { plane.isPoseInPolygon(hitPose) }.getOrDefault(false)

    private fun shouldRefreshPlaneDetection(cameraState: TrackingState?, nowNanos: Long): Boolean =
        cameraState != TrackingState.TRACKING || nowNanos - lastPlaneScanNanos >= PLANE_SCAN_INTERVAL_NANOS

    private fun detectTrackedHorizontalSurface(session: Session, cameraState: TrackingState?): Boolean {
        if (cameraState != TrackingState.TRACKING) return false
        return runCatching {
            session.getAllTrackables(Plane::class.java).any { plane ->
                isEligiblePhysicalFloorPlane(plane, true, plane.centerPose)
            }
        }.getOrDefault(false)
    }

    private fun trackingLossDurationNanos(cameraState: TrackingState?): Long {
        if (cameraState != TrackingState.TRACKING) {
            trackingLostSinceNanos = null
            return 0L
        }
        val now = System.nanoTime()
        val since = trackingLostSinceNanos ?: now.also { trackingLostSinceNanos = it }
        return (now - since).coerceAtLeast(0L)
    }

    private fun verifiedAnchorPose(anchor: Anchor, cameraState: TrackingState?): Pose? {
        val before = runCatching { anchor.trackingState }.getOrNull()
        if (!isVerifiedTrackingPose(cameraState, before)) return null
        val pose = runCatching { anchor.pose }
            .onFailure { Log.w(TAG, "[$modeName] Could not read anchor pose", it) }
            .getOrNull() ?: return null
        val after = runCatching { anchor.trackingState }.getOrNull()
        return pose.takeIf { isVerifiedTrackingPose(cameraState, after) }
    }

    private fun promotePlacementAnchor(candidate: Anchor, verifiedPose: Pose, onPlaced: () -> Unit) {
        val previousAnchor = activeAnchor
        activeAnchor = candidate
        anchorTrackingState = TrackingState.TRACKING
        lastVerifiedWorldPose = verifiedPose
        pendingPlacementAnchor = null
        pendingPlacementPlane = null
        runCatching { recoveryCandidate?.detach() }
        recoveryCandidate = null
        recoveryPlane = null
        trackingLostSinceNanos = null
        recoveryGate.reset()
        runCatching { previousAnchor?.takeIf { it !== candidate }?.detach() }
        Log.i(TAG, "[$modeName] Committed placement from a verified physical-plane tracking anchor")
        onPlaced()
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing anchor and tracking state")
        runCatching { activeAnchor?.detach() }
        runCatching { pendingPlacementAnchor?.detach() }
        runCatching { recoveryCandidate?.detach() }
        activeAnchor = null
        pendingPlacementAnchor = null
        pendingPlacementPlane = null
        recoveryCandidate = null
        recoveryPlane = null
        anchorTrackingState = null
        lastVerifiedWorldPose = null
        trackingLostSinceNanos = null
        pendingPlacementRef.set(null)
        recoveryGate.reset()
        lastPlaneScanNanos = 0L
        lastRecoveryPlaneScanNanos = 0L
        cachedHasTrackedHorizontalSurface = false
        isPlaneDetected = false
    }

    fun dispose() = clear()

    fun getHudStatus(): Pair<String, Color> = when {
        activeAnchor != null && anchorTrackingState == TrackingState.TRACKING ->
            "النموذج مثبت • انقر على أرضية أخرى لنقله" to NeonGreen
        activeAnchor != null && anchorTrackingState == TrackingState.PAUSED ->
            "التتبع متوقف مؤقتاً • حرّك الجهاز ببطء" to Color(0xFFFFB300)
        activeAnchor != null && anchorTrackingState == TrackingState.STOPPED ->
            "فُقد التتبع • امسح الأرضية ثم انقر لإعادة التثبيت" to Color(0xFFFF5252)
        activeAnchor != null -> "النموذج مثبت • انقر على أرضية أخرى لنقله" to NeonGreen
        isPlaneDetected -> "تم رصد أرضية حقيقية • انقر لتثبيت النموذج" to NeonGreen
        else -> "Searching for a real surface" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(modeName = modeName, onHapticFeedback = onHapticFeedback)
}
