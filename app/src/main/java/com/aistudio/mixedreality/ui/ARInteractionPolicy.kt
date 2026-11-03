package com.aistudio.mixedreality.ui

/** Shared placement request lifetime used by runtime logic and tests. */
internal const val PLACEMENT_TIMEOUT_MS = 2_500L

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

/** Allows a single recovery attempt until the replacement anchor resumes tracking. */
internal class AnchorRecoveryGate {
    private var recoveryInProgress = false

    fun shouldAttempt(): Boolean {
        if (recoveryInProgress) return false
        recoveryInProgress = true
        return true
    }

    fun reset() {
        recoveryInProgress = false
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
