package com.aistudio.mixedreality.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * - Two fingers: Zoom (scale multiplier: pinch to minify, spread to magnify, 0.1x to 2500x).
 * - Double tap: Toggle Move Mode (switching between moving model position across ground and scaling).
 * - Triple tap: Toggle Full Screen Mode (hiding/showing top and bottom navigation bars).
 */
@Composable
fun Modifier.spatialMRGestures(
    scope: CoroutineScope,
    isMoveMode: Boolean,
    onSingleTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
    onOneFingerRotate: (dx: Float, dy: Float) -> Unit,
    onTwoFingerZoom: (zoomMultiplier: Float) -> Unit,
    onMove: (panX: Float, panY: Float) -> Unit
): Modifier {
    val currentIsMoveMode by rememberUpdatedState(isMoveMode)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTripleTap by rememberUpdatedState(onTripleTap)
    val currentOnOneFingerRotate by rememberUpdatedState(onOneFingerRotate)
    val currentOnTwoFingerZoom by rememberUpdatedState(onTwoFingerZoom)
    val currentOnMove by rememberUpdatedState(onMove)

    return this.pointerInput(Unit) {
        var tapCount = 0
        var lastTapTime = 0L
        var lastTapOffset = Offset.Zero
        var tapTimeoutJob: Job? = null

        val touchSlop = viewConfiguration.touchSlop

        var maxPointersSeen = 1
        var skipNextRotationDelta = false

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downTime = System.currentTimeMillis()
            val downOffset = down.position

            var isGestureDrag = false
            var lastPointerPosition = downOffset
            var prevCentroid = downOffset
            var prevSpan = 0f
            var lastPointerCount = 1
            maxPointersSeen = 1
            skipNextRotationDelta = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val pressedChanges = event.changes.filter { it.pressed }
                val currentCount = pressedChanges.size

                if (currentCount > maxPointersSeen) {
                    maxPointersSeen = currentCount
                }

                // If multi-finger touch is active, immediately cancel any pending single-tap anchor placement
                if (currentCount > 1) {
                    tapTimeoutJob?.cancel()
                    tapCount = 0
                }

                if (currentCount == 0) {
                    // All fingers released: check if it qualifies strictly as a 1-finger tap
                    val upTime = System.currentTimeMillis()
                    val totalDuration = upTime - downTime
                    val totalDistance = (lastPointerPosition - downOffset).getDistance()

                    // Ensure NO multi-touch occurred during this touch sequence
                    if (maxPointersSeen == 1 && !isGestureDrag && totalDuration < 400L && totalDistance < touchSlop * 2.0f) {
                        val tapTime = System.currentTimeMillis()
                        val diffTime = tapTime - lastTapTime
                        val diffDist = (downOffset - lastTapOffset).getDistance()

                        if (diffTime < 400L && diffDist < 120f) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = tapTime
                        lastTapOffset = downOffset

                        tapTimeoutJob?.cancel()

                        if (tapCount >= 3) {
                            tapCount = 0
                            currentOnTripleTap()
                        } else if (tapCount == 2) {
                            // Double tap: toggle Move mode immediately, cancel any anchor placement
                            currentOnDoubleTap()
                            tapCount = 0
                        } else if (tapCount == 1) {
                            // Single tap: delay to verify not a double tap before placing anchor
                            tapTimeoutJob = scope.launch {
                                delay(260L)
                                if (tapCount == 1) {
                                    currentOnSingleTap(lastTapOffset)
                                    tapCount = 0
                                }
                            }
                        }
                    }
                    break
                }

                // If number of fingers changed (e.g. 1 -> 2, or 2 -> 1)
                if (currentCount != lastPointerCount) {
                    lastPointerCount = currentCount
                    skipNextRotationDelta = true // Ignore sudden frame delta on transition
                    if (currentCount == 1) {
                        lastPointerPosition = pressedChanges[0].position
                    } else if (currentCount == 2) {
                        prevCentroid = Offset(
                            (pressedChanges[0].position.x + pressedChanges[1].position.x) / 2f,
                            (pressedChanges[0].position.y + pressedChanges[1].position.y) / 2f
                        )
                        prevSpan = hypot(
                            pressedChanges[0].position.x - pressedChanges[1].position.x,
                            pressedChanges[0].position.y - pressedChanges[1].position.y
                        )
                    }
                    continue
                }

                if (currentCount == 1) {
                    val currentPos = pressedChanges[0].position
                    val distFromDown = (currentPos - downOffset).getDistance()

                    if (skipNextRotationDelta) {
                        skipNextRotationDelta = false
                        lastPointerPosition = currentPos
                        continue
                    }

                    if (isGestureDrag || distFromDown > touchSlop) {
                        isGestureDrag = true
                        tapTimeoutJob?.cancel()
                        tapCount = 0

                        val dx = currentPos.x - lastPointerPosition.x
                        val dy = currentPos.y - lastPointerPosition.y
                        if (abs(dx) > 0.05f || abs(dy) > 0.05f) {
                            currentOnOneFingerRotate(dx, dy)
                        }
                        pressedChanges[0].consume()
                    }
                    lastPointerPosition = currentPos
                } else if (currentCount == 2) {
                    isGestureDrag = true
                    tapTimeoutJob?.cancel()
                    tapCount = 0

                    val p1 = pressedChanges[0].position
                    val p2 = pressedChanges[1].position
                    val currentCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                    val currentSpan = hypot(p1.x - p2.x, p1.y - p2.y)

                    if (currentIsMoveMode) {
                        // Two fingers move the model across space in Move Mode
                        val panX = currentCentroid.x - prevCentroid.x
                        val panY = currentCentroid.y - prevCentroid.y
                        if (abs(panX) > 0.5f || abs(panY) > 0.5f) {
                            currentOnMove(panX, panY)
                        }
                    } else {
                        // Two fingers scale the model in Scale Mode (pinch / spread: 0.1x to 2500x)
                        if (prevSpan > 15f && currentSpan > 15f) {
                            val zoomFactor = currentSpan / prevSpan
                            if (zoomFactor.isFinite() && zoomFactor in 0.5f..2.0f && abs(zoomFactor - 1f) > 0.0001f) {
                                currentOnTwoFingerZoom(zoomFactor)
                            }
                        }
                    }

                    prevCentroid = currentCentroid
                    prevSpan = currentSpan
                    pressedChanges.forEach { it.consume() }
                } else if (currentCount >= 3) {
                    // Triple finger touch detected: toggle full screen
                    isGestureDrag = true
                    tapTimeoutJob?.cancel()
                    tapCount = 0
                    currentOnTripleTap()
                    pressedChanges.forEach { it.consume() }
                    break
                }
            }
        }
    }
}
