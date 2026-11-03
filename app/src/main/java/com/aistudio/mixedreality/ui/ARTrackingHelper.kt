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

    // Keep the last world pose observed while camera and anchor are both tracking. A stopped
    // anchor's pose is not a trustworthy recovery target after ARCore loses/relocalizes the map.
    private var lastTrackedAnchorPose: Pose? = null

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
            val decision = placementTapDecision(
                isModelVisible = isModelVisible,
                cameraIsTracking = cameraState == TrackingState.TRACKING,
                elapsedMs = (System.currentTimeMillis() - pending.creationTime).coerceAtLeast(0L)
            )
            when (decision) {
                PlacementTapDecision.DISCARD -> {
                    pendingPlacementRef.compareAndSet(pending, null)
                    Log.d(TAG, "[$modeName] Discarded expired, hidden-model, or untracked placement tap")
                }
                PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME -> {
                    // Consume once: never replay old screen coordinates against a later camera pose.
                    if (pendingPlacementRef.compareAndSet(pending, null)) {
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
                        // A valid hit alone is not enough: reject paused/stopped anchors instead of
                        // marking placement successful or leaving a hidden, unrecoverable anchor.
                        val candidateTracking = candidate?.let {
                            runCatching { it.trackingState }.getOrNull()
                        }
                        val candidatePose = if (candidateTracking == TrackingState.TRACKING &&
                            cameraState == TrackingState.TRACKING
                        ) candidate?.let { runCatching { it.pose }.getOrNull() } else null

                        if (candidate != null && candidateTracking == TrackingState.TRACKING && candidatePose != null) {
                            val previousAnchor = activeAnchor
                            activeAnchor = candidate
                            anchorTrackingState = TrackingState.TRACKING
                            lastTrackedAnchorPose = candidatePose
                            recoveryGate.reset()
                            runCatching { previousAnchor?.detach() }
                            Log.i(TAG, "[$modeName] Model anchored to a tracking ARCore horizontal-plane hit")
                            onHapticFeedback()
                            onPlaced()
                        } else {
                            runCatching { candidate?.detach() }
                            Log.w(TAG, "[$modeName] Placement rejected: no tracking anchor from a valid horizontal-plane hit")
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
                // Refresh recovery target only from a fully tracked frame.
                lastTrackedAnchorPose = runCatching { anchor.pose }
                    .onFailure { Log.w(TAG, "[$modeName] Could not read tracking anchor pose", it) }
                    .getOrNull() ?: lastTrackedAnchorPose
                recoveryGate.reset()
            } else if (state == TrackingState.STOPPED && cameraState == TrackingState.TRACKING) {
                // Recover only at the saved world pose, on a currently tracked, non-subsumed ARCore
                // upward-facing plane whose polygon contains that exact pose. No unrelated plane,
                // inferred height, instant placement, or camera-gaze fallback is permitted.
                val originalPose = lastTrackedAnchorPose
                val targetPlane = if (originalPose == null) null else runCatching {
                    session.getAllTrackables(Plane::class.java).firstOrNull { plane ->
                        plane.trackingState == TrackingState.TRACKING &&
                            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.subsumedBy == null && plane.isPoseInPolygon(originalPose)
                    }
                }.onFailure {
                    Log.w(TAG, "[$modeName] Could not inspect tracked planes for anchor recovery", it)
                }.getOrNull()

                // No matching plane does not consume an attempt. A bounded retry interval prevents
                // anchor churn while allowing recovery when ARCore's real plane map converges later.
                if (targetPlane != null && originalPose != null && recoveryGate.shouldAttempt()) {
                    val recovered = runCatching { targetPlane.createAnchor(originalPose) }
                        .onFailure { Log.w(TAG, "[$modeName] Anchor recovery creation failed", it) }
                        .getOrNull()
                    val recoveredTracking = recovered?.let {
                        runCatching { it.trackingState }.getOrNull()
                    }
                    if (recovered != null && recoveredTracking == TrackingState.TRACKING) {
                        activeAnchor = recovered
                        anchorTrackingState = TrackingState.TRACKING
                        lastTrackedAnchorPose = runCatching { recovered.pose }.getOrNull() ?: originalPose
                        runCatching { anchor.detach() }
                        recoveryGate.reset()
                        Log.i(TAG, "[$modeName] Recovered anchor at its saved world pose")
                    } else {
                        runCatching { recovered?.detach() }
                        Log.w(TAG, "[$modeName] Recovery candidate is not tracking; retry remains enabled")
                    }
                }
            }
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
        activeAnchor = null
        anchorTrackingState = null
        lastTrackedAnchorPose = null
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
