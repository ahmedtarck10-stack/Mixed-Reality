package com.aistudio.mixedreality.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Encapsulates a pending tap placement with creation timestamp and tracked attempt counter.
 */
data class PendingPlacementTap(
    val offset: Offset,
    val creationTime: Long = System.currentTimeMillis(),
    var trackedFrameAttempts: Int = 0
)

/**
 * Unified, thread-safe AR/MR tracking and anchor manager.
 * Implements:
 * 1. Bounded retry strategy waiting for camera tracking and real horizontal planes (up to 3500ms).
 * 2. Safe, non-abandoning anchor recovery with progressive backoff (1.5s -> 2.0s -> 3.0s -> 4.0s).
 * 3. Consistent tracking behavior and comprehensive logging across both AR and MR modes.
 */
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
    private var lastRecoveryAttemptTime by mutableLongStateOf(0L)
    private var recoveryAttemptsCount by mutableIntStateOf(0)
    private var lastCameraTrackingState: TrackingState? = null

    fun onSingleTap(tapOffset: Offset, isModelVisible: Boolean) {
        if (!isModelVisible) {
            Log.d(TAG, "[$modeName] Tap ignored: model is not visible")
            return
        }
        val tap = PendingPlacementTap(offset = tapOffset)
        pendingPlacementRef.set(tap)
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

        // 1. Bounded retry strategy for pending placement tap
        val pending = pendingPlacementRef.get()
        if (pending != null && isModelVisible) {
            val now = System.currentTimeMillis()
            val elapsedMs = now - pending.creationTime
            val maxWaitMs = 3500L

            if (elapsedMs > maxWaitMs) {
                Log.w(
                    TAG,
                    "[$modeName] Placement tap at (${pending.offset}) expired after ${elapsedMs}ms (${pending.trackedFrameAttempts} tracked attempts): No valid horizontal plane"
                )
                pendingPlacementRef.compareAndSet(pending, null)
            } else if (cameraState == TrackingState.TRACKING) {
                pending.trackedFrameAttempts++

                val hitX = if (splitWidthPx > 0f && pending.offset.x > splitWidthPx) {
                    pending.offset.x - splitWidthPx
                } else {
                    pending.offset.x
                }
                val hitY = pending.offset.y

                val hits = runCatching { frame.hitTest(hitX, hitY) }.getOrNull() ?: emptyList()
                val floorHit = hits.firstOrNull { hit ->
                    val t = hit.trackable
                    t is Plane &&
                        t.trackingState == TrackingState.TRACKING &&
                        t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                        t.isPoseInPolygon(hit.hitPose)
                }

                if (floorHit != null) {
                    val newAnchor = runCatching { floorHit.createAnchor() }.getOrNull()
                    if (newAnchor != null) {
                        Log.i(
                            TAG,
                            "[$modeName] Placement succeeded on attempt #${pending.trackedFrameAttempts} at ($hitX, $hitY): anchor=${newAnchor.hashCode()}"
                        )
                        pendingPlacementRef.set(null)
                        val oldAnchor = activeAnchor
                        activeAnchor = newAnchor
                        anchorTrackingState = newAnchor.trackingState
                        recoveryAttemptsCount = 0
                        runCatching { oldAnchor?.detach() }
                        onHapticFeedback()
                        onPlaced()
                    } else {
                        Log.w(TAG, "[$modeName] floorHit.createAnchor() returned null or threw exception")
                    }
                } else {
                    if (pending.trackedFrameAttempts % 30 == 1) {
                        Log.d(
                            TAG,
                            "[$modeName] Waiting for real horizontal plane under tap ($hitX, $hitY), attempt #${pending.trackedFrameAttempts}"
                        )
                    }
                }
            } else {
                // Camera is PAUSED or STOPPED; keep waiting without penalizing frame attempts
                if (elapsedMs % 1000 < 50) {
                    Log.d(TAG, "[$modeName] Tap placement waiting for camera TRACKING (current: $cameraState)")
                }
            }
        }

        // 2. Continuous anchor tracking state synchronization
        val currentAnchor = activeAnchor
        if (currentAnchor != null) {
            val currentTrackState = currentAnchor.trackingState
            if (anchorTrackingState != currentTrackState) {
                Log.i(TAG, "[$modeName] Active anchor tracking state: $anchorTrackingState -> $currentTrackState")
                anchorTrackingState = currentTrackState
            }

            // 3. Safe, non-abandoning anchor recovery strategy
            // Does NOT abandon tracking after 3 attempts; uses progressive backoff (1.5s -> 2.0s -> 3.0s -> 4.0s)
            // Does NOT thrash every frame.
            if (currentTrackState == TrackingState.STOPPED && cameraState == TrackingState.TRACKING) {
                val cooldownMs = when (recoveryAttemptsCount) {
                    0 -> 1500L
                    1 -> 2000L
                    2 -> 3000L
                    else -> 4000L
                }
                val now = System.currentTimeMillis()
                if (now - lastRecoveryAttemptTime > cooldownMs) {
                    lastRecoveryAttemptTime = now
                    recoveryAttemptsCount++
                    Log.d(
                        TAG,
                        "[$modeName] Anchor recovery attempt #$recoveryAttemptsCount (cooldown=${cooldownMs}ms)"
                    )

                    val planes = session.getAllTrackables(Plane::class.java)
                    val targetPlane = planes.firstOrNull { plane ->
                        plane.trackingState == TrackingState.TRACKING &&
                            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.subsumedBy == null &&
                            plane.isPoseInPolygon(currentAnchor.pose)
                    }

                    if (targetPlane != null) {
                        val migratedAnchor = runCatching { targetPlane.createAnchor(currentAnchor.pose) }.getOrNull()
                        if (migratedAnchor != null) {
                            Log.i(
                                TAG,
                                "[$modeName] Anchor recovery succeeded on plane ${targetPlane.hashCode()}; new anchor=${migratedAnchor.hashCode()}"
                            )
                            runCatching { currentAnchor.detach() }
                            activeAnchor = migratedAnchor
                            anchorTrackingState = migratedAnchor.trackingState
                            recoveryAttemptsCount = 0
                        } else {
                            Log.w(TAG, "[$modeName] createAnchor failed on targetPlane during recovery")
                        }
                    } else {
                        Log.d(
                            TAG,
                            "[$modeName] No tracked horizontal plane contains anchor pose on recovery attempt #$recoveryAttemptsCount"
                        )
                    }
                }
            }
        }

        // 4. Update horizontal plane presence state
        val planes = session.getAllTrackables(Plane::class.java)
        val hasTrackedPlane = planes.any {
            it.trackingState == TrackingState.TRACKING &&
                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        }
        if (hasTrackedPlane != isPlaneDetected) {
            Log.d(TAG, "[$modeName] Horizontal plane detected: $hasTrackedPlane")
            isPlaneDetected = hasTrackedPlane
        }
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing anchor and tracking state")
        runCatching { activeAnchor?.detach() }
        activeAnchor = null
        anchorTrackingState = null
        pendingPlacementRef.set(null)
        recoveryAttemptsCount = 0
    }

    fun dispose() {
        Log.d(TAG, "[$modeName] Disposing tracking manager")
        clear()
    }

    fun getHudStatus(): Pair<String, Color> {
        return when {
            activeAnchor != null && anchorTrackingState == TrackingState.TRACKING -> {
                "النموذج مثبت على الأرضية • اسحب للتدوير أو التكبير" to NeonGreen
            }
            activeAnchor != null && anchorTrackingState == TrackingState.PAUSED -> {
                "جاري استعادة تتبع السطح... حرّك الجهاز ببطء" to Color(0xFFFFB300)
            }
            activeAnchor != null && anchorTrackingState == TrackingState.STOPPED -> {
                "فُقد التتبع • جاري محاولة الاستعادة أو انقر لإعادة التثبيت" to Color(0xFFFF5252)
            }
            activeAnchor != null -> {
                "النموذج مثبت على الأرضية • اسحب للتدوير أو التكبير" to NeonGreen
            }
            isPlaneDetected -> {
                "تم رصد الأرضية • انقر لتثبيت النموذج" to NeonGreen
            }
            else -> {
                "وجّه الكاميرا نحو الأرضية لرصد السطح الحقيقي" to NeonCyan
            }
        }
    }
}

/**
 * Creates and remembers an [ARTrackingManager] instance.
 */
@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager {
    return remember(modeName) {
        ARTrackingManager(
            modeName = modeName,
            onHapticFeedback = onHapticFeedback
        )
    }
}
