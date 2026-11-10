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

data class PendingPlacementTap(
    val offset: Offset,
    val creationTime: Long = System.currentTimeMillis()
)

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

    private val pendingPlacementRef =
        AtomicReference<PendingPlacementTap?>(null)

    private val recoveryGate = AnchorRecoveryGate()

    private var lastCameraTrackingState: TrackingState? = null
    private var lastVerifiedWorldPose: Pose? = null
    private var trackingLostSinceNanos: Long? = null

    fun onSingleTap(
        tapOffset: Offset,
        isModelVisible: Boolean
    ) {
        if (!isModelVisible) {
            Log.d(TAG, "[$modeName] Tap ignored: model hidden")
            return
        }

        pendingPlacementRef.set(
            PendingPlacementTap(offset = tapOffset)
        )

        Log.d(TAG, "[$modeName] Placement tap queued")
    }

    fun onAnchorTrackingStateChanged(
        newState: TrackingState
    ) {
        if (anchorTrackingState != newState) {
            Log.i(
                TAG,
                "[$modeName] Anchor state: $anchorTrackingState -> $newState"
            )
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
        val cameraState = runCatching {
            frame.camera.trackingState
        }.getOrNull()

        if (lastCameraTrackingState != cameraState) {
            Log.i(
                TAG,
                "[$modeName] Camera state: $lastCameraTrackingState -> $cameraState"
            )
            lastCameraTrackingState = cameraState
        }

        if (cameraState != TrackingState.TRACKING) {
            updateAnchorStateWithoutPoseUpdate()
            return
        }

        processPendingPlacement(
            frame = frame,
            isModelVisible = isModelVisible,
            splitWidthPx = splitWidthPx,
            onPlaced = onPlaced
        )

        processActiveAnchor(
            session = session,
            cameraState = cameraState,
            onPlaced = onPlaced
        )

        updateRealHorizontalPlaneStatus(session, cameraState)
    }

    private fun processPendingPlacement(
        frame: Frame,
        isModelVisible: Boolean,
        splitWidthPx: Float,
        onPlaced: () -> Unit
    ) {
        val pending = pendingPlacementRef.get() ?: return

        val elapsedMs = (
            System.currentTimeMillis() - pending.creationTime
        ).coerceAtLeast(0L)

        when (
            placementTapDecision(
                isModelVisible = isModelVisible,
                cameraIsTracking = true,
                elapsedMs = elapsedMs
            )
        ) {
            PlacementTapDecision.DISCARD -> {
                pendingPlacementRef.compareAndSet(pending, null)
                Log.d(TAG, "[$modeName] Expired placement tap discarded")
                return
            }

            PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME -> Unit
        }

        if (!pendingPlacementRef.compareAndSet(pending, null)) {
            return
        }

        val hitX = if (
            splitWidthPx > 0f &&
            pending.offset.x > splitWidthPx
        ) {
            pending.offset.x - splitWidthPx
        } else {
            pending.offset.x
        }

        // Only accept a physically detected, tracked horizontal floor plane.
        // Estimated Instant Placement points are intentionally excluded.
        val floorHit = runCatching {
            frame.hitTest(
                hitX,
                pending.offset.y
            ).firstOrNull { hit ->
                val plane = hit.trackable as? Plane
                    ?: return@firstOrNull false

                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    plane.subsumedBy == null &&
                    plane.isPoseInPolygon(hit.hitPose)
            }
        }.onFailure {
            Log.w(TAG, "[$modeName] Physical plane hit test failed", it)
        }.getOrNull()

        if (floorHit == null) {
            Log.w(
                TAG,
                "[$modeName] No tracked horizontal floor plane found"
            )
            return
        }

        val candidate = runCatching {
            floorHit.createAnchor()
        }.onFailure {
            Log.e(TAG, "[$modeName] Anchor creation failed", it)
        }.getOrNull() ?: return

        // Never commit a PAUSED or STOPPED placement anchor.
        val verifiedPose = verifiedAnchorPose(
            anchor = candidate,
            cameraState = TrackingState.TRACKING
        )

        if (verifiedPose == null) {
            runCatching { candidate.detach() }
            Log.w(
                TAG,
                "[$modeName] Placement rejected: anchor is not TRACKING"
            )
            return
        }

        promotePlacementAnchor(
            candidate = candidate,
            verifiedPose = verifiedPose,
            onPlaced = onPlaced
        )

        onHapticFeedback()
    }

    private fun processActiveAnchor(
        session: Session,
        cameraState: TrackingState?,
        onPlaced: () -> Unit
    ) {
        val anchor = activeAnchor ?: return

        val state = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (state != null && anchorTrackingState != state) {
            anchorTrackingState = state
            Log.i(TAG, "[$modeName] Active anchor state: $state")
        }

        when (state) {
            TrackingState.TRACKING -> {
                val verifiedPose = verifiedAnchorPose(
                    anchor,
                    cameraState
                )

                if (verifiedPose != null) {
                    val dampedPose = filterPoseWithLegacyDamping(
                        rawPose = verifiedPose,
                        lastPose = lastVerifiedWorldPose,
                        deadbandMeters = 0.0015f,
                        alpha = 0.35f
                    )
                    lastVerifiedWorldPose = dampedPose
                    trackingLostSinceNanos = null
                    recoveryGate.reset()
                }
            }

            TrackingState.PAUSED,
            TrackingState.STOPPED -> {
                val now = System.nanoTime()

                val lostSince = trackingLostSinceNanos
                    ?: now.also {
                        trackingLostSinceNanos = it
                    }

                val lostDuration = (
                    now - lostSince
                ).coerceAtLeast(0L)

                val canRecover = shouldAttemptAnchorRecovery(
                    cameraState = cameraState,
                    anchorState = state,
                    lostDurationNanos = lostDuration,
                    gracePeriodNanos = RECOVERY_GRACE_NANOS
                )

                if (!canRecover || !recoveryGate.shouldAttempt()) {
                    return
                }

                val savedPose = lastVerifiedWorldPose

                if (savedPose == null) {
                    Log.w(
                        TAG,
                        "[$modeName] Recovery skipped: no verified saved pose"
                    )
                    return
                }

                val candidate = runCatching {
                    session.createAnchor(savedPose)
                }.onFailure {
                    Log.w(TAG, "[$modeName] Anchor recovery failed", it)
                }.getOrNull() ?: return

                val candidatePose = verifiedAnchorPose(
                    candidate,
                    cameraState
                )

                val acceptable = isRecoveryCandidateAcceptable(
                    cameraState = cameraState,
                    candidateState = candidate.trackingState,
                    savedPose = savedPose,
                    candidatePose = candidatePose
                )

                if (acceptable && candidatePose != null) {
                    promotePlacementAnchor(
                        candidate = candidate,
                        verifiedPose = candidatePose,
                        onPlaced = onPlaced
                    )

                    Log.i(
                        TAG,
                        "[$modeName] Anchor recovered with verified pose"
                    )
                } else {
                    runCatching { candidate.detach() }

                    Log.w(
                        TAG,
                        "[$modeName] Recovery candidate rejected"
                    )
                }
            }

            null -> {
                Log.w(
                    TAG,
                    "[$modeName] Unable to read anchor tracking state"
                )
            }
        }
    }

    private fun verifiedAnchorPose(
        anchor: Anchor,
        cameraState: TrackingState?
    ): Pose? {
        val before = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (!isVerifiedTrackingPose(cameraState, before)) {
            return null
        }

        val pose = runCatching {
            anchor.pose
        }.onFailure {
            Log.w(TAG, "[$modeName] Anchor pose read failed", it)
        }.getOrNull() ?: return null

        val after = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (!isVerifiedTrackingPose(cameraState, after)) {
            return null
        }

        if (
            !pose.tx().isFinite() ||
            !pose.ty().isFinite() ||
            !pose.tz().isFinite()
        ) {
            return null
        }

        return pose
    }

    private fun promotePlacementAnchor(
        candidate: Anchor,
        verifiedPose: Pose,
        onPlaced: () -> Unit
    ) {
        val previousAnchor = activeAnchor

        activeAnchor = candidate
        anchorTrackingState = TrackingState.TRACKING
        lastVerifiedWorldPose = verifiedPose

        trackingLostSinceNanos = null
        recoveryGate.reset()

        if (previousAnchor !== candidate) {
            runCatching {
                previousAnchor?.detach()
            }
        }

        Log.i(
            TAG,
            "[$modeName] Verified anchor committed"
        )

        onPlaced()
    }

    private fun updateAnchorStateWithoutPoseUpdate() {
        val anchor = activeAnchor ?: return

        val state = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (state != null && anchorTrackingState != state) {
            anchorTrackingState = state
            Log.i(
                TAG,
                "[$modeName] Anchor state while camera unavailable: $state"
            )
        }
    }

    private fun updateRealHorizontalPlaneStatus(
        session: Session,
        cameraState: TrackingState?
    ) {
        val detected =
            cameraState == TrackingState.TRACKING &&
                runCatching {
                    session.getAllTrackables(Plane::class.java).any { plane ->
                        plane.trackingState == TrackingState.TRACKING &&
                            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.subsumedBy == null
                    }
                }.getOrDefault(false)

        if (detected != isPlaneDetected) {
            isPlaneDetected = detected

            Log.d(
                TAG,
                "[$modeName] Real horizontal plane detected: $detected"
            )
        }
    }

    fun clear() {
        Log.d(TAG, "[$modeName] Clearing tracking state")

        runCatching {
            activeAnchor?.detach()
        }

        activeAnchor = null
        anchorTrackingState = null
        lastVerifiedWorldPose = null
        trackingLostSinceNanos = null

        pendingPlacementRef.set(null)
        recoveryGate.reset()

        isPlaneDetected = false
        lastCameraTrackingState = null
    }

    fun dispose() = clear()

    fun getHudStatus(): Pair<String, Color> = when {
        activeAnchor != null &&
            anchorTrackingState == TrackingState.TRACKING ->
            "Model placed • Tap floor to move" to NeonGreen

        activeAnchor != null &&
            anchorTrackingState == TrackingState.PAUSED ->
            "Tracking paused • Move device slowly" to Color(0xFFFFB300)

        activeAnchor != null &&
            anchorTrackingState == TrackingState.STOPPED ->
            "Tracking lost • Scan floor and tap to place" to Color(0xFFFF5252)

        activeAnchor != null ->
            "Model placed • Tap floor to move" to NeonGreen

        isPlaneDetected ->
            "Tracked floor detected • Tap to place" to NeonGreen

        else ->
            "Point camera at floor and move slowly" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(
        modeName = modeName,
        onHapticFeedback = onHapticFeedback
    )
}
