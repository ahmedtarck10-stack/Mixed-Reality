package com.aistudio.mixedreality.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Calculates angle in degrees between two touch pointers.
 */
private fun angleDegrees(p1: Offset, p2: Offset): Float {
    val rad = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
    return Math.toDegrees(rad).toFloat()
}

/**
 * Spatial Gesture Modifier for MR / 3D / AR operations:
 * - One tap: Anchor / place model onto the real floor or scene.
 * - One finger drag: 360-degree rotation across X, Y, and Z axes.
 *     • Center drag: 360° rotation on X (pitch) and Y (yaw).
 *     • Screen edge drag (outer 15% width): Direct 1-finger 360° roll on Z-axis!
 * - Two fingers:
 *     • Zoom (scale 0.1x to 2500x) when in Zoom mode.
 *     • Move (translate 3D position) when in Move mode.
 *     • Twist / rotate around Z-axis (roll) 360 degrees.
 * - Double tap: Toggle between Zoom Mode and Move Mode.
 * - Triple tap: Toggle Full Screen (hide/show upper and lower bars).
 */
@Composable
fun Modifier.spatialMRGestures(
    scope: CoroutineScope,
    isMoveMode: Boolean,
    activeAxis: RotationAxis = RotationAxis.ALL,
    onSingleTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
    onOneFingerRotate: (dx: Float, dy: Float, isEdgeDrag: Boolean) -> Unit,
    onTwoFingerZoom: (zoomMultiplier: Float) -> Unit,
    onTwoFingerRotateZ: (deltaAngle: Float) -> Unit = {},
    onMove: (panX: Float, panY: Float) -> Unit
): Modifier {
    val currentIsMoveMode by rememberUpdatedState(isMoveMode)
    val currentActiveAxis by rememberUpdatedState(activeAxis)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTripleTap by rememberUpdatedState(onTripleTap)
    val currentOnOneFingerRotate by rememberUpdatedState(onOneFingerRotate)
    val currentOnTwoFingerZoom by rememberUpdatedState(onTwoFingerZoom)
    val currentOnTwoFingerRotateZ by rememberUpdatedState(onTwoFingerRotateZ)
    val currentOnMove by rememberUpdatedState(onMove)

    return this.pointerInput(Unit) {
        var tapCount = 0
        var lastTapTime = 0L
        var lastTapOffset = Offset.Zero
        var tapTimeoutJob: Job? = null
        var zoomFlushJob: Job? = null
        val zoomAccumulator = ZoomGestureAccumulator()
        val touchSlop = viewConfiguration.touchSlop
        var maxPointersSeen = 1
        var skipNextRotationDelta = false

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downTime = System.currentTimeMillis()
            val downOffset = down.position
            val isEdgeStart = (downOffset.x < size.width * 0.14f || downOffset.x > size.width * 0.86f)
            var isGestureDrag = false
            var lastPointerPosition = downOffset
            var prevCentroid = downOffset
            var prevSpan = 0f
            var prevAngle = 0f
            var lastPointerCount = 1
            maxPointersSeen = 1
            skipNextRotationDelta = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val pressedChanges = event.changes.filter { it.pressed }
                val currentCount = pressedChanges.size
                if (currentCount > maxPointersSeen) maxPointersSeen = currentCount

                if (currentCount > 1) {
                    tapTimeoutJob?.cancel()
                    tapCount = 0
                }

                if (currentCount == 0) {
                    val totalDuration = System.currentTimeMillis() - downTime
                    val totalDistance = (lastPointerPosition - downOffset).getDistance()
                    if (maxPointersSeen == 1 && !isGestureDrag && totalDuration < 400L && totalDistance < touchSlop * 2f) {
                        val tapTime = System.currentTimeMillis()
                        val diffTime = tapTime - lastTapTime
                        val diffDist = (downOffset - lastTapOffset).getDistance()
                        tapCount = if (diffTime < 400L && diffDist < 120f) tapCount + 1 else 1
                        lastTapTime = tapTime
                        lastTapOffset = downOffset
                        tapTimeoutJob?.cancel()
                        when {
                            tapCount >= 3 -> {
                                tapCount = 0
                                currentOnTripleTap()
                            }
                            tapCount == 2 -> {
                                currentOnDoubleTap()
                                tapCount = 0
                            }
                            tapCount == 1 -> {
                                tapTimeoutJob = scope.launch {
                                    delay(260L)
                                    if (tapCount == 1) {
                                        currentOnSingleTap(lastTapOffset)
                                        tapCount = 0
                                    }
                                }
                            }
                        }
                    }
                    break
                }

                if (currentCount != lastPointerCount) {
                    lastPointerCount = currentCount
                    skipNextRotationDelta = true
                    if (currentCount == 1) {
                        lastPointerPosition = pressedChanges[0].position
                    } else if (currentCount == 2) {
                        val p1 = pressedChanges[0].position
                        val p2 = pressedChanges[1].position
                        prevCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                        prevSpan = hypot(p1.x - p2.x, p1.y - p2.y)
                        prevAngle = angleDegrees(p1, p2)
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
                            currentOnOneFingerRotate(dx, dy, isEdgeStart)
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
                    val currentAngle = angleDegrees(p1, p2)

                    if (currentIsMoveMode) {
                        val panX = currentCentroid.x - prevCentroid.x
                        val panY = currentCentroid.y - prevCentroid.y
                        if (abs(panX) > 0.5f || abs(panY) > 0.5f) {
                            currentOnMove(panX, panY)
                        }
                    } else {
                        // Pinch to Zoom
                        if (prevSpan > 15f && currentSpan > 15f) {
                            val zoomFactor = currentSpan / prevSpan
                            if (zoomFactor.isFinite() && zoomFactor in 0.5f..2f && abs(zoomFactor - 1f) > 0.0001f) {
                                zoomAccumulator.add(zoomFactor)
                                if (zoomFlushJob?.isActive != true) {
                                    zoomFlushJob = scope.launch {
                                        withFrameNanos { }
                                        val factor = ZoomScalePolicy.apply(1f, zoomAccumulator.consume())
                                        if (factor != 1f) currentOnTwoFingerZoom(factor)
                                        zoomFlushJob = null
                                    }
                                }
                            }
                        }

                        // Twist to rotate around Z-axis (360 degrees roll)
                        var angleDiff = currentAngle - prevAngle
                        while (angleDiff < -180f) angleDiff += 360f
                        while (angleDiff > 180f) angleDiff -= 360f
                        if (abs(angleDiff) > 0.25f && abs(angleDiff) < 60f) {
                            currentOnTwoFingerRotateZ(angleDiff)
                        }
                    }
                    prevCentroid = currentCentroid
                    prevSpan = currentSpan
                    prevAngle = currentAngle
                    pressedChanges.forEach { it.consume() }
                } else if (currentCount >= 3) {
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
