package com.aistudio.mixedreality.ui

import com.google.ar.core.Pose
import com.google.ar.core.TrackingState

/** Shared placement request lifetime used by runtime logic and tests. */
internal const val PLACEMENT_TIMEOUT_MS = 2_500L
internal const val MAX_RECOVERY_TRANSLATION_METERS = 0.05f

internal enum class PlacementTapDecision {
    HIT_TEST_ON_CURRENT_FRAME,
    DISCARD
}

/** A screen-space tap cannot be replayed after tracking loss or after the 2500ms window. */
internal fun placementTapDecision(
    isModelVisible: Boolean,
    cameraIsTracking: Boolean,
    elapsedMs: Long
): PlacementTapDecision = when {
    !isModelVisible || elapsedMs >= PLACEMENT_TIMEOUT_MS -> PlacementTapDecision.DISCARD
    !cameraIsTracking -> PlacementTapDecision.DISCARD
    else -> PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME
}

/** A world pose is suitable for the recovery baseline only while both ARCore objects track. */
internal fun isVerifiedTrackingPose(
    cameraState: TrackingState?,
    anchorState: TrackingState?
): Boolean = cameraState == TrackingState.TRACKING && anchorState == TrackingState.TRACKING

/** Whether a paused/stopped anchor has waited long enough for recovery to be considered. */
internal fun shouldAttemptAnchorRecovery(
    cameraState: TrackingState?,
    anchorState: TrackingState?,
    lostDurationNanos: Long,
    gracePeriodNanos: Long = 1_000_000_000L
): Boolean = cameraState == TrackingState.TRACKING &&
    (anchorState == TrackingState.PAUSED || anchorState == TrackingState.STOPPED) &&
    lostDurationNanos >= gracePeriodNanos

/** Accept a replacement only when it tracks and remains within the saved world-position tolerance. */
internal fun isRecoveryCandidateAcceptable(
    cameraState: TrackingState?,
    candidateState: TrackingState?,
    savedPose: Pose?,
    candidatePose: Pose?,
    maxDistanceMeters: Float = MAX_RECOVERY_TRANSLATION_METERS
): Boolean {
    if (!isVerifiedTrackingPose(cameraState, candidateState) || savedPose == null || candidatePose == null) {
        return false
    }
    return isRecoveryTranslationContinuous(
        savedX = savedPose.tx(),
        savedY = savedPose.ty(),
        savedZ = savedPose.tz(),
        candidateX = candidatePose.tx(),
        candidateY = candidatePose.ty(),
        candidateZ = candidatePose.tz(),
        maxDistanceMeters = maxDistanceMeters
    )
}

/** Reject recovery replacements whose world translation would jump away from the verified pose. */
internal fun isRecoveryTranslationContinuous(
    savedX: Float,
    savedY: Float,
    savedZ: Float,
    candidateX: Float,
    candidateY: Float,
    candidateZ: Float,
    maxDistanceMeters: Float = MAX_RECOVERY_TRANSLATION_METERS
): Boolean {
    if (!savedX.isFinite() || !savedY.isFinite() || !savedZ.isFinite() ||
        !candidateX.isFinite() || !candidateY.isFinite() || !candidateZ.isFinite() ||
        !maxDistanceMeters.isFinite() || maxDistanceMeters < 0f
    ) return false

    val dx = candidateX - savedX
    val dy = candidateY - savedY
    val dz = candidateZ - savedZ
    return dx * dx + dy * dy + dz * dz <= maxDistanceMeters * maxDistanceMeters
}

/** Retries failed anchor recovery with a bounded rate until tracking resumes. */
internal class AnchorRecoveryGate(
    private val retryIntervalNanos: Long = 750_000_000L,
    private val clockNanos: () -> Long = System::nanoTime
) {
    private var lastAttemptNanos: Long? = null

    fun shouldAttempt(): Boolean {
        val now = clockNanos()
        val previous = lastAttemptNanos
        if (previous != null && now - previous < retryIntervalNanos) return false
        lastAttemptNanos = now
        return true
    }

    fun reset() {
        lastAttemptNanos = null
    }
}

/** Coalesces rapid pinch events into one bounded multiplier per rendered frame. */
internal class ZoomGestureAccumulator {
    private var pendingFactor = 1.0

    fun add(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        pendingFactor = (pendingFactor * factor.toDouble()).coerceIn(0.5, 2.0)
    }

    fun consume(): Float {
        val result = pendingFactor.toFloat()
        pendingFactor = 1.0
        return result
    }
}

/** Shared zoom scale policy used by AR, MR and Object renderers. */
internal object ZoomScalePolicy {
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 2500f

    fun normalize(scale: Float): Float =
        if (scale.isFinite() && scale > 0f) scale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f

    fun apply(currentScale: Float, factor: Float): Float {
        if (!currentScale.isFinite() || currentScale <= 0f) return 1f
        val current = currentScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (!factor.isFinite() || factor <= 0f) return current
        return (current.toDouble() * factor.toDouble())
            .coerceIn(MIN_SCALE.toDouble(), MAX_SCALE.toDouble())
            .toFloat()
    }
}
