package com.aistudio.mixedreality.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ARTracking"
private const val PLACEMENT_TIMEOUT_MS = 2_500L

/** A screen-space tap waiting for the next trackable camera frame. */
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
    private var lastCameraTrackingState: TrackingState? = null

    /** A deliberate tap places or repositions the model on the next tracked frame. */
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
        val cameraState = frame.camera.trackingState
        if (lastCameraTrackingState != cameraState) {
            Log.i(TAG, "[$modeName] Camera tracking state changed: $lastCameraTrackingState -> $cameraState")
            lastCameraTrackingState = cameraState
        }

        val pending = pendingPlacementRef.get()
        if (pending != null) {
            val elapsedMs = System.currentTimeMillis() - pending.creationTime
            when {
                !isModelVisible -> pendingPlacementRef.compareAndSet(pending, null)
                elapsedMs > PLACEMENT_TIMEOUT_MS -> {
                    pendingPlacementRef.compareAndSet(pending, null)
                    Log.w(TAG, "[$modeName] Placement tap expired; scan the floor and tap again")
                }
                cameraState == TrackingState.TRACKING -> {
                    // Consume the tap on this frame. Retrying its old screen coordinates on later
                    // frames makes a moving camera cast a different ray and silently moves the
                    // requested placement point while the user is waiting for plane detection.
                    if (pendingPlacementRef.compareAndSet(pending, null)) {
                        val hitX = if (splitWidthPx > 0f && pending.offset.x > splitWidthPx) {
                            pending.offset.x - splitWidthPx
                        } else {
                            pending.offset.x
                        }
                        val hits = runCatching { frame.hitTest(hitX, pending.offset.y) }.getOrNull().orEmpty()
                        val floorHit = hits.firstOrNull { hit ->
                            val plane = hit.trackable as? Plane
                            plane != null &&
                                plane.trackingState == TrackingState.TRACKING &&
                                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.subsumedBy == null &&
                                plane.isPoseInPolygon(hit.hitPose)
                        }

                        val newAnchor = floorHit?.let { hit ->
                            runCatching { hit.createAnchor() }
                                .onFailure { Log.w(TAG, "[$modeName] Could not create anchor", it) }
                                .getOrNull()
                        }

                        if (newAnchor != null) {
                            // Keep the existing anchor until the replacement was created
                            // successfully, so a failed relocation never makes the model vanish.
                            val oldAnchor = activeAnchor
                            activeAnchor = newAnchor
                            anchorTrackingState = newAnchor.trackingState
                            runCatching { oldAnchor?.detach() }
                            Log.i(TAG, "[$modeName] Model anchored to a tracked upward-facing plane")
                            onHapticFeedback()
                            onPlaced()
                        } else {
                            Log.w(TAG, "[$modeName] No tracked floor plane under tap; scan the floor and tap again")
                        }
                    }
                }
                else -> Unit // Wait briefly for camera tracking, but never re-hit-test stale taps.
            }
        }

        activeAnchor?.let { anchor ->
            val state = runCatching { anchor.trackingState }.getOrNull()
            if (state != null && anchorTrackingState != state) {
                Log.i(TAG, "[$modeName] Active anchor tracking state: $anchorTrackingState -> $state")
                anchorTrackingState = state
            }
        }

        val hasTrackedFloor = runCatching {
            session.getAllTrackables(Plane::class.java).any { plane ->
                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    plane.subsumedBy == null
            }
        }.getOrDefault(false)
        if (hasTrackedFloor != isPlaneDetected) {
            Log.d(TAG, "[$modeName] Tracked upward-facing floor plane detected: $hasTrackedFloor")
            isPlaneDetected = hasTrackedFloor
        }
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing anchor and tracking state")
        runCatching { activeAnchor?.detach() }
        activeAnchor = null
        anchorTrackingState = null
        pendingPlacementRef.set(null)
    }

    fun dispose() = clear()

    fun getHudStatus(): Pair<String, Color> = when {
        activeAnchor != null && anchorTrackingState == TrackingState.TRACKING ->
            "النموذج مثبت • انقر على أرضية أخرى لنقله" to NeonGreen
        activeAnchor != null && anchorTrackingState == TrackingState.PAUSED ->
            "التتبع متوقف مؤقتاً • حرّك الجهاز ببطء" to Color(0xFFFFB300)
        activeAnchor != null && anchorTrackingState == TrackingState.STOPPED ->
            "فُقد التتبع • امسح الأرضية ثم انقر لإعادة التثبيت" to Color(0xFFFF5252)
        activeAnchor != null ->
            "النموذج مثبت • انقر على أرضية أخرى لنقله" to NeonGreen
        isPlaneDetected ->
            "تم رصد الأرضية • انقر لتثبيت النموذج" to NeonGreen
        else ->
            "وجّه الكاميرا نحو الأرضية وحرّكها ببطء" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(modeName = modeName, onHapticFeedback = onHapticFeedback)
}
