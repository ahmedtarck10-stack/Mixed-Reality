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

    // Snapshot in ARCore world coordinates captured only from a tracking anchor + tracking camera.
    // Never use a stopped/paused anchor's subsequently changing pose as the recovery destination.
    private var lastTrackedAnchorPose: Pose? = null
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
                    val hits = runCatching { frame.hitTest(hitX, pending.offset.y) }
                        .onFailure { Log.w(TAG, "[$modeName] Hit test failed", it) }
                        .getOrNull()
                        .orEmpty()
                    val floorHit = hits.firstOrNull { hit ->
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
                    val candidateState = candidate?.let { runCatching { it.trackingState }.getOrNull() }
                    when {
                        candidate == null -> Log.w(TAG, "[$modeName] No tracked horizontal plane hit inside its polygon")
                        candidateState == TrackingState.STOPPED -> {
                            runCatching { candidate.detach() }
                            Log.w(TAG, "[$modeName] Placement rejected: new anchor is STOPPED")
                        }
                        else -> {
                            // ARCore can report a newly-created, valid plane anchor as PAUSED while
                            // its pose update is pending. Keep this one real hit anchor and wait for
                            // TRACKING instead of rejecting it or creating duplicates on later frames.
                            val oldAnchor = activeAnchor
                            activeAnchor = candidate
                            anchorTrackingState = candidateState
                            lastTrackedAnchorPose = floorHit.hitPose
                            recoveryCandidate?.let { runCatching { it.detach() } }
                            recoveryCandidate = null
                            recoveryGate.reset()
                            runCatching { oldAnchor?.detach() }
                            if (candidateState == TrackingState.TRACKING && cameraState == TrackingState.TRACKING) {
                                lastTrackedAnchorPose = runCatching { candidate.pose }.getOrNull() ?: floorHit.hitPose
                                Log.i(TAG, "[$modeName] Placed on a tracking ARCore horizontal-plane hit")
                                onHapticFeedback()
                                onPlaced()
                            } else {
                                pendingPlacementConfirmation = true
                                Log.i(TAG, "[$modeName] Valid floor-hit anchor is PAUSED; waiting for ARCore tracking")
                            }
                        }
                    }
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

            if (state == TrackingState.TRACKING && cameraState == TrackingState.TRACKING) {
                lastTrackedAnchorPose = runCatching { anchor.pose }
                    .onFailure { Log.w(TAG, "[$modeName] Could not read tracking anchor pose", it) }
                    .getOrNull() ?: lastTrackedAnchorPose
                recoveryGate.reset()
                if (pendingPlacementConfirmation) {
                    pendingPlacementConfirmation = false
                    onHapticFeedback()
                    onPlaced()
                }
            } else if (state == TrackingState.STOPPED && cameraState == TrackingState.TRACKING) {
                val originalPose = lastTrackedAnchorPose
                val candidate = recoveryCandidate
                if (candidate != null) {
                    when (runCatching { candidate.trackingState }.getOrNull()) {
                        TrackingState.TRACKING -> {
                            val previousAnchor = activeAnchor
                            activeAnchor = candidate
                            anchorTrackingState = TrackingState.TRACKING
                            lastTrackedAnchorPose = runCatching { candidate.pose }.getOrNull() ?: originalPose
                            recoveryCandidate = null
                            recoveryGate.reset()
                            runCatching { previousAnchor?.detach() }
                            Log.i(TAG, "[$modeName] Recovered anchor at its saved world pose")
                        }
                        TrackingState.STOPPED, null -> {
                            runCatching { candidate.detach() }
                            recoveryCandidate = null
                            Log.w(TAG, "[$modeName] Recovery candidate stopped; bounded retry remains enabled")
                        }
                        TrackingState.PAUSED -> Unit // Wait for ARCore's asynchronous pose update.
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

                    // Don't spend a retry while there is no plane containing the exact saved pose.
                    // A matching plane arms one attempt; a PAUSED replacement is retained and
                    // polled, so subsequent frames cannot create a chain of duplicate anchors.
                    if (targetPlane != null && recoveryGate.shouldAttempt()) {
                        recoveryCandidate = runCatching { targetPlane.createAnchor(originalPose) }
                            .onFailure { Log.w(TAG, "[$modeName] Anchor recovery creation failed", it) }
                            .getOrNull()
                        if (recoveryCandidate == null) {
                            Log.w(TAG, "[$modeName] Recovery creation failed; retry will be rate-limited")
                        }
                    }
                } else {
                    Log.w(TAG, "[$modeName] No saved tracked world pose; refusing arbitrary recovery")
                }
            }
            // PAUSED anchors are retained and allowed to resume naturally. Recreating them while
            // ARCore is temporarily updating tracking would risk duplicates and a world-space jump.
        }

        val hasTrackedFloor = cameraState == TrackingState.TRACKING && runCatching {
            session.getAllTrackables(Plane::class.java).any { plane ->
                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.subsumedBy == null
            }
        }.getOrDefault(false)
        if (hasTrackedFloor != isPlaneDetected) {
            isPlaneDetected = hasTrackedFloor
            Log.d(TAG, "[$modeName] Tracked upward-facing ARCore floor plane detected: $hasTrackedFloor")
        }
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing anchor and tracking state")
        runCatching { activeAnchor?.detach() }
        runCatching { recoveryCandidate?.detach() }
        activeAnchor = null
        recoveryCandidate = null
        anchorTrackingState = null
        lastTrackedAnchorPose = null
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
        isPlaneDetected -> "تم رصد الأرضية • انقر لتثبيت النموذج" to NeonGreen
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
