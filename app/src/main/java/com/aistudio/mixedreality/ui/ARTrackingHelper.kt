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
private const val RECOVERY_GRACE_NANOS = 1_000_000_000L

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

    // Only an anchor pose read while both anchor and camera track can become this baseline.
    private var lastVerifiedWorldPose: Pose? = null
    private var pendingPlacementAnchor: Anchor? = null
    private var recoveryCandidate: Anchor? = null
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

        // Standard requirement: Strictly ignore hit-testing, anchor creation, and pose updates
        // when the camera tracking state is PAUSED or STOPPED (caused by IMU/gyroscope divergence)
        // to prevent jitter, jumpy coordinates, or disappearing models.
        if (cameraState != TrackingState.TRACKING) {
            return
        }

        val pending = pendingPlacementRef.get()
        if (pending != null) {
            when (placementTapDecision(
                isModelVisible = isModelVisible,
                cameraIsTracking = true,
                elapsedMs = (System.currentTimeMillis() - pending.creationTime).coerceAtLeast(0L)
            )) {
                PlacementTapDecision.DISCARD -> {
                    pendingPlacementRef.compareAndSet(pending, null)
                    Log.d(TAG, "[$modeName] Discarded expired or hidden-model placement tap")
                }
                PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME -> if (pendingPlacementRef.compareAndSet(pending, null)) {
                    val hitX = if (splitWidthPx > 0f && pending.offset.x > splitWidthPx) {
                        pending.offset.x - splitWidthPx
                    } else {
                        pending.offset.x
                    }
                    val floorHit = runCatching {
                        // Standard instant placement at default 2.0m distance with fallback to standard hitTest
                        val instantHits = runCatching {
                            frame.hitTestInstantPlacement(hitX, pending.offset.y, 2.0f)
                        }.getOrNull().orEmpty()
                        if (instantHits.isNotEmpty()) instantHits else frame.hitTest(hitX, pending.offset.y)
                    }.onFailure { Log.w(TAG, "[$modeName] Hit test failed", it) }
                        .getOrNull()
                        .orEmpty()
                        .firstOrNull { hit ->
                            val trackable = hit.trackable
                            when (trackable) {
                                is Plane -> {
                                    trackable.trackingState == TrackingState.TRACKING &&
                                        trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                        trackable.subsumedBy == null &&
                                        (trackable.isPoseInPolygon(hit.hitPose) || trackable.isPoseInExtents(hit.hitPose))
                                }
                                is com.google.ar.core.InstantPlacementPoint -> {
                                    trackable.trackingState == TrackingState.TRACKING
                                }
                                is com.google.ar.core.Point -> {
                                    trackable.trackingState == TrackingState.TRACKING &&
                                        trackable.orientationMode == com.google.ar.core.Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                                }
                                else -> false
                            }
                        }
                    val candidate = floorHit?.let { hit ->
                        runCatching { hit.createAnchor() }
                            .onFailure { Log.w(TAG, "[$modeName] Could not create anchor from hit", it) }
                            .getOrNull()
                    }
                    when {
                        candidate == null -> Log.w(TAG, "[$modeName] No surface hit detected yet; point camera at floor")
                        candidate.trackingState == TrackingState.STOPPED -> {
                            runCatching { candidate.detach() }
                            Log.w(TAG, "[$modeName] Placement candidate stopped")
                        }
                        else -> {
                            val previousAnchor = activeAnchor
                            activeAnchor = candidate
                            anchorTrackingState = candidate.trackingState
                            lastVerifiedWorldPose = runCatching { candidate.pose }.getOrNull()
                            pendingPlacementAnchor = null
                            runCatching { previousAnchor?.takeIf { it !== candidate }?.detach() }
                            Log.i(TAG, "[$modeName] Instant placement succeeded; anchor created and committed")
                            onPlaced()
                            onHapticFeedback()
                        }
                    }
                }
            }
        }

        // Keep anchor continuously registered without tearing it down or churning
        val anchor = activeAnchor
        if (anchor != null) {
            val state = runCatching { anchor.trackingState }.getOrNull()
            if (state != null && anchorTrackingState != state) {
                anchorTrackingState = state
                Log.i(TAG, "[$modeName] Active anchor tracking state: $state")
            }
            if (state == TrackingState.TRACKING) {
                lastVerifiedWorldPose = runCatching { anchor.pose }.getOrNull()
            }
        }

        val hasTrackedHorizontalSurface = cameraState == TrackingState.TRACKING && runCatching {
            session.getAllTrackables(Plane::class.java).any { plane ->
                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.subsumedBy == null
            } || session.getAllTrackables(com.google.ar.core.InstantPlacementPoint::class.java).any { point ->
                point.trackingState == TrackingState.TRACKING
            } || session.getAllTrackables(com.google.ar.core.Point::class.java).any { point ->
                point.trackingState == TrackingState.TRACKING
            }
        }.getOrDefault(false)
        if (hasTrackedHorizontalSurface != isPlaneDetected) {
            isPlaneDetected = hasTrackedHorizontalSurface
            Log.d(TAG, "[$modeName] Instant surface detected: $hasTrackedHorizontalSurface")
        }
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
        runCatching { recoveryCandidate?.detach() }
        recoveryCandidate = null
        trackingLostSinceNanos = null
        recoveryGate.reset()
        runCatching { previousAnchor?.takeIf { it !== candidate }?.detach() }
        Log.i(TAG, "[$modeName] Committed placement from a verified tracking-anchor pose")
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
        trackingLostSinceNanos = null
        pendingPlacementRef.set(null)
        recoveryGate.reset()
    }

    fun dispose() = clear()

    fun getHudStatus(): Pair<String, Color> = when {
        activeAnchor != null && anchorTrackingState == TrackingState.TRACKING ->
            "Model placed • Tap floor to move" to NeonGreen
        activeAnchor != null && anchorTrackingState == TrackingState.PAUSED ->
            "Tracking paused • Move device slowly" to Color(0xFFFFB300)
        activeAnchor != null && anchorTrackingState == TrackingState.STOPPED ->
            "Tracking lost • Scan floor and tap to place" to Color(0xFFFF5252)
        activeAnchor != null -> "Model placed • Tap floor to move" to NeonGreen
        isPlaneDetected -> "Tracked surface detected • Tap to place" to NeonGreen
        else -> "Point camera at floor and move slowly" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(modeName = modeName, onHapticFeedback = onHapticFeedback)
}
