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

    // Recovery may use only a pose read from an anchor while both anchor and camera track.
    private var lastVerifiedWorldPose: Pose? = null

    // A real hit can initially create a PAUSED anchor. Keep it separate from the active anchor
    // until ARCore tracks it; this avoids replacing a working placement with an unverified pose.
    private var pendingPlacementAnchor: Anchor? = null

    // At most one replacement recovery anchor exists at a time. PAUSED candidates are polled;
    // STOPPED/failed candidates are detached before the rate-limited retry is allowed.
    private var recoveryCandidate: Anchor? = null
    private var pendingPlacementConfirmation = false

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
                            plane != null && plane.trackingState == TrackingState.TRACKING &&
                                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.subsumedBy == null && plane.isPoseInPolygon(hit.hitPose)
                        }
                    val candidate = floorHit?.let { hit ->
                        runCatching { hit.createAnchor() }
                            .onFailure { Log.w(TAG, "[$modeName] Could not create anchor from real floor hit", it) }
                            .getOrNull()
                    }
                    when {
                        candidate == null -> Log.w(TAG, "[$modeName] No tracked horizontal plane hit inside its polygon")
                        runCatching { candidate.trackingState }.getOrNull() == TrackingState.STOPPED -> {
                            runCatching { candidate.detach() }
                            Log.w(TAG, "[$modeName] Placement rejected: new anchor is STOPPED")
                        }
                        isVerifiedTrackingPose(
                            cameraState,
                            runCatching { candidate.trackingState }.getOrNull()
                        ) -> {
                            promotePlacementAnchor(candidate, onPlaced)
                            onHapticFeedback()
                        }
                        else -> {
                            // A hit pose is not a verified recovery pose. Hold this single anchor
                            // separately and await a later frame where its own pose is tracked.
                            runCatching { pendingPlacementAnchor?.detach() }
                            pendingPlacementAnchor = candidate
                            pendingPlacementConfirmation = true
                            Log.i(TAG, "[$modeName] Real floor-hit anchor is PAUSED; retaining existing placement until tracked")
                        }
                    }
                }
            }
        }

        // Promote a delayed placement only after ARCore reports that both the candidate anchor
        // and camera are TRACKING. Save its actual anchor pose, never HitResult.hitPose, as the
        // recovery baseline. A stopped candidate is discarded; there is no guessed fallback.
        pendingPlacementAnchor?.let { candidate ->
            val candidateState = runCatching { candidate.trackingState }.getOrNull()
            when {
                isVerifiedTrackingPose(cameraState, candidateState) -> {
                    promotePlacementAnchor(candidate, onPlaced)
                    onHapticFeedback()
                }
                candidateState == TrackingState.STOPPED || candidateState == null -> {
                    runCatching { candidate.detach() }
                    pendingPlacementAnchor = null
                    pendingPlacementConfirmation = false
                    Log.w(TAG, "[$modeName] Delayed placement anchor stopped before tracking; discarded")
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

            if (isVerifiedTrackingPose(cameraState, state)) {
                // Only this confirmed world-space anchor pose is eligible as a recovery target.
                lastVerifiedWorldPose = runCatching { anchor.pose }
                    .onFailure { Log.w(TAG, "[$modeName] Could not read tracking anchor pose", it) }
                    .getOrNull() ?: lastVerifiedWorldPose
                recoveryGate.reset()
                if (pendingPlacementConfirmation) {
                    pendingPlacementConfirmation = false
                    onPlaced()
                }
            } else if (state == TrackingState.STOPPED && cameraState == TrackingState.TRACKING) {
                val originalPose = lastVerifiedWorldPose
                val candidate = recoveryCandidate
                if (candidate != null) {
                    when (runCatching { candidate.trackingState }.getOrNull()) {
                        TrackingState.TRACKING -> {
                            val previousAnchor = activeAnchor
                            activeAnchor = candidate
                            anchorTrackingState = TrackingState.TRACKING
                            lastVerifiedWorldPose = runCatching { candidate.pose }.getOrNull() ?: originalPose
                            recoveryCandidate = null
                            recoveryGate.reset()
                            runCatching { previousAnchor?.detach() }
                            Log.i(TAG, "[$modeName] Recovered anchor at its verified world pose")
                        }
                        TrackingState.STOPPED, null -> {
                            runCatching { candidate.detach() }
                            recoveryCandidate = null
                            Log.w(TAG, "[$modeName] Recovery candidate stopped; bounded retry remains enabled")
                        }
                        TrackingState.PAUSED -> Unit // Hold the single candidate for ARCore's update.
                    }
                } else if (originalPose != null) {
                    val targetPlane = runCatching {
                        session.getAllTrackables(Plane::class.java).firstOrNull { plane ->
                            plane.trackingState == TrackingState.TRACKING &&
                                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.subsumedBy == null && plane.isPoseInPolygon(originalPose)
                        }
                    }.onFailure {
                        Log.w(TAG, "[$modeName] Could not inspect tracked planes for anchor recovery", it)
                    }.getOrNull()

                    // Absence of the exact containing plane does not consume an attempt. A matching
                    // plane may create one candidate; no further anchor is made until it tracks or
                    // is discarded, preventing duplicate recovery anchors and world-space jumps.
                    if (targetPlane != null && recoveryGate.shouldAttempt()) {
                        recoveryCandidate = runCatching { targetPlane.createAnchor(originalPose) }
                            .onFailure { Log.w(TAG, "[$modeName] Anchor recovery creation failed", it) }
                            .getOrNull()
                        if (recoveryCandidate == null) {
                            Log.w(TAG, "[$modeName] Recovery creation failed; retry will be rate-limited")
                        }
                    }
                } else {
                    Log.w(TAG, "[$modeName] No verified world pose; refusing arbitrary recovery")
                }
            }
            // PAUSED anchors resume naturally. Never replace them with guessed or unrelated poses.
        }

        val hasTrackedFloor = cameraState == TrackingState.TRACKING && runCatching {
            session.getAllTrackables(Plane::class.java).any { plane ->
                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.subsumedBy == null
            }
        }.getOrDefault(false)
        if (hasTrackedFloor != isPlaneDetected) {
            isPlaneDetected = hasTrackedFloor
            Log.d(TAG, "[$modeName] Tracked upward-facing ARCore horizontal surface detected: $hasTrackedFloor")
        }
    }

    private fun promotePlacementAnchor(candidate: Anchor, onPlaced: () -> Unit) {
        val verifiedPose = runCatching { candidate.pose }.getOrNull()
        if (verifiedPose == null) {
            runCatching { candidate.detach() }
            pendingPlacementAnchor = null
            pendingPlacementConfirmation = false
            Log.w(TAG, "[$modeName] Tracking anchor pose unavailable; placement not committed")
            return
        }
        val previousAnchor = activeAnchor
        activeAnchor = candidate
        anchorTrackingState = TrackingState.TRACKING
        lastVerifiedWorldPose = verifiedPose
        pendingPlacementAnchor = null
        pendingPlacementConfirmation = false
        runCatching { recoveryCandidate?.detach() }
        recoveryCandidate = null
        recoveryGate.reset()
        runCatching { previousAnchor?.takeIf { it !== candidate }?.detach() }
        Log.i(TAG, "[$modeName] Committed placement from a tracking ARCore anchor pose")
        onPlaced()
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing anchor and tracking state")
        runCatching { activeAnchor?.detach() }
        runCatching { pendingPlacementAnchor?.detach() }
        runCatching { recoveryCandidate?.detach() }
        activeAnchor = null
        pendingPlacementAnchor = null
        recoveryCandidate = null
        anchorTrackingState = null
        lastVerifiedWorldPose = null
        pendingPlacementConfirmation = false
        pendingPlacementRef.set(null)
        recoveryGate.reset()
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
        isPlaneDetected -> "تم رصد سطح أفقي متتبّع • انقر لتثبيت النموذج" to NeonGreen
        else -> "وجّه الكاميرا نحو الأرضية وحرّكها ببطء" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(modeName = modeName, onHapticFeedback = onHapticFeedback)
}
