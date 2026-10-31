package com.aistudio.mixedreality.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Spatial Gesture Modifier for MR / 3D / AR operations:
 * - One tap: Anchor / place model onto the real floor or scene.
 * - One finger drag: Smooth yaw rotation around the upright Y-axis.
 * - Two fingers: Zoom (scale multiplier: pinch to minify, spread to magnify).
 * - Double tap: Toggle Move Mode (moving model position across the ground).
 * - Triple tap: Toggle Full Screen Mode (hiding/showing top and bottom navigation bars).
 */
fun Modifier.spatialMRGestures(
    scope: CoroutineScope,
    isMoveMode: Boolean,
    onSingleTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
    onOneFingerRotate: (dx: Float) -> Unit,
    onTwoFingerZoom: (zoomMultiplier: Float) -> Unit,
    onMove: (panX: Float, panY: Float) -> Unit
): Modifier = pointerInput(isMoveMode) {
    var tapCount = 0
    var lastTapTime = 0L
    var lastTapOffset = Offset.Zero
    var tapTimeoutJob: Job? = null

    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        val downOffset = down.position

        var isGestureDrag = false
        var lastPointerPosition = downOffset
        var prevCentroid = currentEvent.calculateCentroid()
        var prevSpan = currentEvent.calculateSpan()

        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }

            if (activeChanges.isEmpty()) {
                // All fingers released: inspect if it's a tap gesture
                val upTime = System.currentTimeMillis()
                val totalDuration = upTime - downTime

                if (!isGestureDrag && totalDuration < 380L) {
                    val distanceMoved = (downOffset - lastPointerPosition).getDistance()
                    if (distanceMoved < touchSlop * 2.5f) {
                        val tapTime = System.currentTimeMillis()
                        val diffTime = tapTime - lastTapTime
                        val diffDist = (downOffset - lastTapOffset).getDistance()

                        if (diffTime < 340L && diffDist < 120f) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = tapTime
                        lastTapOffset = downOffset

                        tapTimeoutJob?.cancel()

                        if (tapCount == 3) {
                            tapCount = 0
                            onTripleTap()
                        } else {
                            tapTimeoutJob = scope.launch {
                                val currentCount = tapCount
                                delay(320L)
                                if (currentCount == tapCount) {
                                    if (tapCount == 1) {
                                        onSingleTap(lastTapOffset)
                                    } else if (tapCount == 2) {
                                        onDoubleTap()
                                    }
                                    tapCount = 0
                                }
                            }
                        }
                    }
                }
                break
            }

            lastPointerPosition = activeChanges[0].position

            if (activeChanges.size == 1) {
                val change = activeChanges[0]
                val panChange = change.positionChange()
                val distanceMoved = (change.position - downOffset).getDistance()

                if (distanceMoved > touchSlop) {
                    isGestureDrag = true
                    tapTimeoutJob?.cancel()
                    tapCount = 0

                    if (isMoveMode) {
                        onMove(panChange.x, panChange.y)
                    } else {
                        onOneFingerRotate(panChange.x)
                    }
                    change.consume()
                }
            } else if (activeChanges.size >= 2) {
                isGestureDrag = true
                tapTimeoutJob?.cancel()
                tapCount = 0

                val currentCentroid = event.calculateCentroid()
                val currentSpan = event.calculateSpan()

                if (prevSpan > 0f && currentSpan > 0f) {
                    val zoomFactor = currentSpan / prevSpan
                    if (abs(zoomFactor - 1f) > 0.002f) {
                        onTwoFingerZoom(zoomFactor)
                    }
                }

                val panX = currentCentroid.x - prevCentroid.x
                val panY = currentCentroid.y - prevCentroid.y

                if (isMoveMode || abs(panX) > 1.2f || abs(panY) > 1.2f) {
                    onMove(panX, panY)
                }

                prevCentroid = currentCentroid
                prevSpan = currentSpan

                activeChanges.forEach { it.consume() }
            }
        }
    }
}

private fun PointerEvent.calculateCentroid(): Offset {
    var totalX = 0f
    var totalY = 0f
    var count = 0
    for (change in changes) {
        if (change.pressed) {
            totalX += change.position.x
            totalY += change.position.y
            count++
        }
    }
    return if (count > 0) Offset(totalX / count, totalY / count) else Offset.Zero
}

private fun PointerEvent.calculateSpan(): Float {
    val pressedChanges = changes.filter { it.pressed }
    if (pressedChanges.size < 2) return 0f
    val p1 = pressedChanges[0].position
    val p2 = pressedChanges[1].position
    return hypot(p1.x - p2.x, p1.y - p2.y)
}
