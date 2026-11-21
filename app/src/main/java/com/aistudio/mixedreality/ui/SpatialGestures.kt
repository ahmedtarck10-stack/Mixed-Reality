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
import kotlin.math.min

/**
 * Locked single-finger rotation axis chosen per direction segment so X, Y, and Z remain clean and non-overlapping.
 */
enum class OneFingerStrokeAxis {
    UNDECIDED,
    Y_YAW,
    X_PITCH,
    Z_ROLL
}

/**
 * Picks either pure Y (horizontal swipe) or pure X (vertical swipe) once finger travel crosses [thresholdPx].
 */
fun decideOneFingerStrokeAxis(
    totalDx: Float,
    totalDy: Float,
    thresholdPx: Float = 16f
): OneFingerStrokeAxis {
    if (!totalDx.isFinite() || !totalDy.isFinite()) return OneFingerStrokeAxis.UNDECIDED
    val dist = hypot(totalDx, totalDy)
    if (dist < thresholdPx.coerceAtLeast(1f)) return OneFingerStrokeAxis.UNDECIDED
    return if (abs(totalDx) >= abs(totalDy) * 1.05f) {
        OneFingerStrokeAxis.Y_YAW
    } else {
        OneFingerStrokeAxis.X_PITCH
    }
}

/**
 * Dynamically transitions between [OneFingerStrokeAxis.Y_YAW] and [OneFingerStrokeAxis.X_PITCH]
 * when the user steers strongly perpendicular to the active axis mid-stroke, giving smooth,
 * non-sticky X/Y control without lifting the finger while still eliminating accidental diagonal wobble.
 */
fun evaluateOneFingerAxisSwitch(
    currentLock: OneFingerStrokeAxis,
    windowDx: Float,
    windowDy: Float,
    switchThresholdPx: Float = 36f
): OneFingerStrokeAxis {
    if (!windowDx.isFinite() || !windowDy.isFinite()) return currentLock
    val threshold = switchThresholdPx.coerceAtLeast(12f)
    return when (currentLock) {
        OneFingerStrokeAxis.Y_YAW -> {
            if (abs(windowDy) >= threshold && abs(windowDy) >= abs(windowDx) * 1.55f) {
                OneFingerStrokeAxis.X_PITCH
            } else {
                currentLock
            }
        }
        OneFingerStrokeAxis.X_PITCH -> {
            if (abs(windowDx) >= threshold && abs(windowDx) >= abs(windowDy) * 1.55f) {
                OneFingerStrokeAxis.Y_YAW
            } else {
                currentLock
            }
        }
        OneFingerStrokeAxis.Z_ROLL,
        OneFingerStrokeAxis.UNDECIDED -> currentLock
    }
}

/**
 * Computes consistent degrees-per-pixel rotation sensitivity across phones and tablets so
 * dragging across the shorter viewport dimension turns smoothly ~320°.
 */
fun computeRotationSensitivity(
    viewportWidthPx: Float,
    viewportHeightPx: Float
): Float {
    if (!viewportWidthPx.isFinite() || !viewportHeightPx.isFinite() ||
        viewportWidthPx <= 0f || viewportHeightPx <= 0f
    ) {
        return 0.30f
    }
    val refSpan = min(viewportWidthPx, viewportHeightPx).coerceIn(720f, 1600f)
    return (320f / refSpan).coerceIn(0.20f, 0.45f)
}

/**
 * Returns true if the touch started inside the outer viewport border ring used for 360° Z roll.
 */
fun isOuterZRingTouch(
    touch: Offset,
    viewportWidthPx: Float,
    viewportHeightPx: Float
): Boolean {
    if (viewportWidthPx <= 1f || viewportHeightPx <= 1f) return false
    val cx = viewportWidthPx * 0.5f
    val cy = viewportHeightPx * 0.5f
    val rx = (cx * 0.82f).coerceAtLeast(1f)
    val ry = (cy * 0.78f).coerceAtLeast(1f)
    val normDist = hypot((touch.x - cx) / rx, (touch.y - cy) / ry)
    return normDist > 1.0f
}

/**
 * Computes raw geometric angular delta (in degrees) around [center] for 1-finger Z rotation.
 */
fun calculateOneFingerZRollDelta(
    prev: Offset,
    curr: Offset,
    center: Offset,
    minRadiusPx: Float = 24f
): Float {
    val prevDx = prev.x - center.x
    val prevDy = prev.y - center.y
    val currDx = curr.x - center.x
    val currDy = curr.y - center.y
    val prevR = hypot(prevDx, prevDy)
    val currR = hypot(currDx, currDy)
    if (prevR < minRadiusPx || currR < minRadiusPx) return 0f

    val prevAngle = atan2(prevDy.toDouble(), prevDx.toDouble())
    val currAngle = atan2(currDy.toDouble(), currDx.toDouble())
    var delta = Math.toDegrees(currAngle - prevAngle).toFloat()
    while (delta < -180f) delta += 360f
    while (delta > 180f) delta -= 360f
    return if (delta.isFinite()) delta else 0f
}

/**
 * Computes radius-compensated 1-finger Z-roll delta so top/bottom and side rim drags rotate
 * at the same comfortable angular pace as X and Y rotations without edge sluggishness.
 */
fun calculateResponsiveZRollDelta(
    prev: Offset,
    curr: Offset,
    center: Offset,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    minRadiusPx: Float = 24f
): Float {
    val rawDelta = calculateOneFingerZRollDelta(prev, curr, center, minRadiusPx)
    if (rawDelta == 0f) return 0f
    val currR = hypot(curr.x - center.x, curr.y - center.y)
    val refRadius = (min(viewportWidthPx, viewportHeightPx) * 0.42f).coerceAtLeast(100f)
    val gain = 1.45f * (currR / refRadius).coerceIn(1.0f, 2.8f)
    return (rawDelta * gain).coerceIn(-24f, 24f)
}

/**
 * Pure check for triggering a 1-finger long press gesture (used to reset all model gestures).
 */
fun shouldTriggerOneFingerLongPress(
    maxPointersSeen: Int,
    isGestureDrag: Boolean,
    elapsedMs: Long,
    totalDistancePx: Float,
    touchSlopPx: Float,
    thresholdMs: Long = 520L
): Boolean {
    if (!totalDistancePx.isFinite() || !touchSlopPx.isFinite()) return false
    return maxPointersSeen == 1 &&
        !isGestureDrag &&
        elapsedMs >= thresholdMs &&
        totalDistancePx <= touchSlopPx * 1.5f
}

/**
 * Dispatches 1-finger rotation deltas cleanly across X, Y, and Z with viewport-normalized
 * sensitivity and lossless snapshot updates when [onRotateAxesDelta] is supplied.
 */
internal fun dispatchViewportRotation(
    activeAxis: RotationAxis,
    dx: Float,
    dy: Float,
    isEdgeDrag: Boolean,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    currentRotX: Float,
    currentRotY: Float,
    currentRotZ: Float,
    currentOrientation: SpatialQuaternion?,
    onRotateAxesDelta: ((dPitchDeg: Float, dYawDeg: Float, dRollDeg: Float) -> Unit)?,
    onOrientationChange: ((SpatialQuaternion) -> Unit)?,
    onRotX: (Float) -> Unit,
    onRotY: (Float) -> Unit,
    onRotZ: (Float) -> Unit
) {
    if (!dx.isFinite() || !dy.isFinite()) return
    val sens = computeRotationSensitivity(viewportWidthPx, viewportHeightPx)
    if (onRotateAxesDelta != null) {
        when (activeAxis) {
            RotationAxis.ALL -> {
                val dPitch = (dy * sens).coerceIn(-28f, 28f)
                val dYaw = (dx * sens).coerceIn(-28f, 28f)
                val dRoll = if (isEdgeDrag) (dy * sens).coerceIn(-28f, 28f) else 0f
                onRotateAxesDelta(dPitch, dYaw, dRoll)
            }
            RotationAxis.X -> {
                val raw = if (abs(dy) >= abs(dx)) dy else dx
                onRotateAxesDelta((raw * sens).coerceIn(-28f, 28f), 0f, 0f)
            }
            RotationAxis.Y -> {
                val raw = if (abs(dx) >= abs(dy)) dx else -dy
                onRotateAxesDelta(0f, (raw * sens).coerceIn(-28f, 28f), 0f)
            }
            RotationAxis.Z -> {
                val raw = if (abs(dy) >= abs(dx)) dy else dx
                onRotateAxesDelta(0f, 0f, (raw * sens).coerceIn(-28f, 28f))
            }
        }
        return
    }

    if (activeAxis == RotationAxis.ALL && onOrientationChange != null && currentOrientation != null) {
        var updated = currentOrientation
        if (dx != 0f) updated = updated.rotateWorldY((dx * sens).coerceIn(-28f, 28f))
        if (dy != 0f) updated = updated.rotateWorldX((dy * sens).coerceIn(-28f, 28f))
        onOrientationChange(updated)
    }
    applyRotationDelta(
        axis = activeAxis,
        dx = dx,
        dy = dy,
        isEdgeDrag = isEdgeDrag,
        currentRotX = currentRotX,
        currentRotY = currentRotY,
        currentRotZ = currentRotZ,
        onRotX = onRotX,
        onRotY = onRotY,
        onRotZ = onRotZ,
        sensitivity = sens
    )
}

internal fun dispatchViewportZRoll(
    deltaAngle: Float,
    currentRotZ: Float,
    currentOrientation: SpatialQuaternion?,
    onRotateAxesDelta: ((dPitchDeg: Float, dYawDeg: Float, dRollDeg: Float) -> Unit)?,
    onOrientationChange: ((SpatialQuaternion) -> Unit)?,
    onRotZ: (Float) -> Unit
) {
    if (!deltaAngle.isFinite()) return
    val clamped = deltaAngle.coerceIn(-28f, 28f)
    if (onRotateAxesDelta != null) {
        onRotateAxesDelta(0f, 0f, -clamped)
        return
    }
    if (onOrientationChange != null && currentOrientation != null) {
        onOrientationChange(currentOrientation.rotateWorldZ(-clamped))
    }
    val newRotZ = ((currentRotZ + clamped) % 360f + 360f) % 360f
    onRotZ(newRotZ)
}

/**
 * Spatial Gesture Modifier for MR / 3D / AR operations:
 * - One tap: Anchor / place model onto the real floor or scene.
 * - One long tap (hold): Reset all gestures (rotation X/Y/Z, scale, move/position) to default.
 * - One finger drag ONLY for responsive, gimbal-free 360° rotation across X, Y, and Z axes:
 *     • Inner zone horizontal drag -> pure Y (Yaw) 360° rotation (smoothly steers to X if curved).
 *     • Inner zone vertical drag   -> pure X (Pitch) 360° rotation (follows finger naturally).
 *     • Outer perimeter ring drag  -> smooth radius-normalized Z (Roll) 360° rotation.
 * - Two fingers ONLY for Move or Scale:
 *     • Zoom (scale 0.1x to 2500x) when in Scale mode.
 *     • Move (translate 3D position) when in Move mode.
 * - Double tap: Switch between Move mode and Scale mode.
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
    onLongPress: (Offset) -> Unit = {},
    onOneFingerRotate: (dx: Float, dy: Float, isEdgeDrag: Boolean) -> Unit,
    onOneFingerRotateZ: (deltaAngle: Float) -> Unit = {},
    onTwoFingerZoom: (zoomMultiplier: Float) -> Unit,
    onTwoFingerRotateZ: (deltaAngle: Float) -> Unit = {},
    onMove: (panX: Float, panY: Float) -> Unit
): Modifier {
    val currentIsMoveMode by rememberUpdatedState(isMoveMode)
    val currentActiveAxis by rememberUpdatedState(activeAxis)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTripleTap by rememberUpdatedState(onTripleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnOneFingerRotate by rememberUpdatedState(onOneFingerRotate)
    val currentOnOneFingerRotateZ by rememberUpdatedState(onOneFingerRotateZ)
    val currentOnTwoFingerZoom by rememberUpdatedState(onTwoFingerZoom)
    val currentOnMove by rememberUpdatedState(onMove)

    return this.pointerInput(Unit) {
        var tapCount = 0
        var lastTapTime = 0L
        var lastTapOffset = Offset.Zero
        var tapTimeoutJob: Job? = null
        var zoomFlushJob: Job? = null
        val zoomAccumulator = ZoomGestureAccumulator()
        val touchSlop = viewConfiguration.touchSlop
        val axisLockThreshold = maxOf(touchSlop * 0.9f, 12f)
        val axisSwitchThreshold = maxOf(touchSlop * 1.8f, 36f)
        val longPressTimeoutMs = 520L
        var maxPointersSeen = 1
        var skipNextRotationDelta = false

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downTime = System.currentTimeMillis()
            var strokeStartOffset = down.position
            val center = Offset(size.width / 2f, size.height / 2f)
            var strokeAxisLock = if (
                currentActiveAxis == RotationAxis.ALL &&
                isOuterZRingTouch(down.position, size.width.toFloat(), size.height.toFloat())
            ) {
                OneFingerStrokeAxis.Z_ROLL
            } else {
                OneFingerStrokeAxis.UNDECIDED
            }
            var axisWindowDx = 0f
            var axisWindowDy = 0f
            var isGestureDrag = false
            var triggeredLongPress = false
            var lastPointerPosition = down.position
            var prevCentroid = down.position
            var prevSpan = 0f
            var lastPointerCount = 1
            maxPointersSeen = 1
            skipNextRotationDelta = false

            var longPressJob: Job? = scope.launch {
                delay(longPressTimeoutMs)
                val dist = (lastPointerPosition - strokeStartOffset).getDistance()
                val elapsed = System.currentTimeMillis() - downTime
                if (shouldTriggerOneFingerLongPress(
                        maxPointersSeen = maxPointersSeen,
                        isGestureDrag = isGestureDrag,
                        elapsedMs = elapsed,
                        totalDistancePx = dist,
                        touchSlopPx = touchSlop,
                        thresholdMs = longPressTimeoutMs
                    )
                ) {
                    triggeredLongPress = true
                    tapTimeoutJob?.cancel()
                    tapCount = 0
                    currentOnLongPress(lastPointerPosition)
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val pressedChanges = event.changes.filter { it.pressed }
                    val currentCount = pressedChanges.size
                    if (currentCount > maxPointersSeen) maxPointersSeen = currentCount

                    if (currentCount > 1) {
                        longPressJob?.cancel()
                        tapTimeoutJob?.cancel()
                        tapCount = 0
                    }

                    if (currentCount == 0) {
                        longPressJob?.cancel()
                        if (!triggeredLongPress) {
                            val totalDuration = System.currentTimeMillis() - downTime
                            val totalDistance = (lastPointerPosition - strokeStartOffset).getDistance()
                            if (maxPointersSeen == 1 && !isGestureDrag && totalDuration < 450L && totalDistance < touchSlop * 2.5f) {
                                val tapTime = System.currentTimeMillis()
                                val diffTime = tapTime - lastTapTime
                                val diffDist = (down.position - lastTapOffset).getDistance()
                                tapCount = if (diffTime < 450L && diffDist < 160f) tapCount + 1 else 1
                                lastTapTime = tapTime
                                lastTapOffset = down.position
                                tapTimeoutJob?.cancel()
                                when (tapCount) {
                                    1 -> {
                                        tapTimeoutJob = scope.launch {
                                            delay(280L)
                                            if (tapCount == 1) {
                                                currentOnSingleTap(lastTapOffset)
                                                tapCount = 0
                                            }
                                        }
                                    }
                                    2 -> {
                                        tapTimeoutJob = scope.launch {
                                            delay(280L)
                                            if (tapCount == 2) {
                                                currentOnDoubleTap()
                                                tapCount = 0
                                            }
                                        }
                                    }
                                    else -> {
                                        // Triple tap (3 clicks): Toggle full screen mode
                                        tapTimeoutJob?.cancel()
                                        tapCount = 0
                                        currentOnTripleTap()
                                    }
                                }
                            }
                        }
                        break
                    }

                    if (triggeredLongPress) {
                        pressedChanges.forEach { it.consume() }
                        continue
                    }

                    if (currentCount != lastPointerCount) {
                        lastPointerCount = currentCount
                        skipNextRotationDelta = true
                        axisWindowDx = 0f
                        axisWindowDy = 0f
                        if (currentCount == 1) {
                            val pos = pressedChanges[0].position
                            lastPointerPosition = pos
                            strokeStartOffset = pos
                            strokeAxisLock = if (
                                currentActiveAxis == RotationAxis.ALL &&
                                isOuterZRingTouch(pos, size.width.toFloat(), size.height.toFloat())
                            ) {
                                OneFingerStrokeAxis.Z_ROLL
                            } else {
                                OneFingerStrokeAxis.UNDECIDED
                            }
                        } else if (currentCount == 2) {
                            val p1 = pressedChanges[0].position
                            val p2 = pressedChanges[1].position
                            prevCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            prevSpan = hypot(p1.x - p2.x, p1.y - p2.y)
                        }
                        continue
                    }

                    if (currentCount == 1) {
                        val currentPos = pressedChanges[0].position
                        val distFromDown = (currentPos - strokeStartOffset).getDistance()
                        if (skipNextRotationDelta) {
                            skipNextRotationDelta = false
                            lastPointerPosition = currentPos
                            continue
                        }

                        if (isGestureDrag || distFromDown > touchSlop) {
                            if (!isGestureDrag) {
                                isGestureDrag = true
                                longPressJob?.cancel()
                                tapTimeoutJob?.cancel()
                                tapCount = 0
                            }

                            if (currentActiveAxis == RotationAxis.ALL) {
                                if (strokeAxisLock == OneFingerStrokeAxis.UNDECIDED) {
                                    strokeAxisLock = decideOneFingerStrokeAxis(
                                        totalDx = currentPos.x - strokeStartOffset.x,
                                        totalDy = currentPos.y - strokeStartOffset.y,
                                        thresholdPx = axisLockThreshold
                                    )
                                    if (strokeAxisLock == OneFingerStrokeAxis.UNDECIDED) {
                                        lastPointerPosition = currentPos
                                        pressedChanges[0].consume()
                                        continue
                                    }
                                    axisWindowDx = 0f
                                    axisWindowDy = 0f
                                }

                                val dx = currentPos.x - lastPointerPosition.x
                                val dy = currentPos.y - lastPointerPosition.y

                                if (strokeAxisLock == OneFingerStrokeAxis.Y_YAW || strokeAxisLock == OneFingerStrokeAxis.X_PITCH) {
                                    axisWindowDx += dx
                                    axisWindowDy += dy
                                    val switched = evaluateOneFingerAxisSwitch(
                                        currentLock = strokeAxisLock,
                                        windowDx = axisWindowDx,
                                        windowDy = axisWindowDy,
                                        switchThresholdPx = axisSwitchThreshold
                                    )
                                    if (switched != strokeAxisLock) {
                                        strokeAxisLock = switched
                                        axisWindowDx = 0f
                                        axisWindowDy = 0f
                                    } else if (hypot(axisWindowDx, axisWindowDy) > axisSwitchThreshold * 2.5f) {
                                        // Keep steering window responsive over long continuous sweeps
                                        axisWindowDx *= 0.45f
                                        axisWindowDy *= 0.45f
                                    }
                                }

                                when (strokeAxisLock) {
                                    OneFingerStrokeAxis.Z_ROLL -> {
                                        val dRoll = calculateResponsiveZRollDelta(
                                            prev = lastPointerPosition,
                                            curr = currentPos,
                                            center = center,
                                            viewportWidthPx = size.width.toFloat(),
                                            viewportHeightPx = size.height.toFloat()
                                        )
                                        if (abs(dRoll) > 0.05f) {
                                            currentOnOneFingerRotateZ(dRoll)
                                        }
                                    }
                                    OneFingerStrokeAxis.Y_YAW -> {
                                        if (abs(dx) > 0.15f) {
                                            currentOnOneFingerRotate(dx, 0f, false)
                                        }
                                    }
                                    OneFingerStrokeAxis.X_PITCH -> {
                                        if (abs(dy) > 0.15f) {
                                            currentOnOneFingerRotate(0f, dy, false)
                                        }
                                    }
                                    OneFingerStrokeAxis.UNDECIDED -> Unit
                                }
                            } else {
                                val dx = currentPos.x - lastPointerPosition.x
                                val dy = currentPos.y - lastPointerPosition.y
                                if (abs(dx) > 0.15f || abs(dy) > 0.15f) {
                                    currentOnOneFingerRotate(dx, dy, false)
                                }
                            }
                            pressedChanges[0].consume()
                        }
                        lastPointerPosition = currentPos
                    } else if (currentCount == 2) {
                        isGestureDrag = true
                        longPressJob?.cancel()
                        tapTimeoutJob?.cancel()
                        tapCount = 0
                        val p1 = pressedChanges[0].position
                        val p2 = pressedChanges[1].position
                        val currentCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                        val currentSpan = hypot(p1.x - p2.x, p1.y - p2.y)

                        if (currentIsMoveMode) {
                            val panX = currentCentroid.x - prevCentroid.x
                            val panY = currentCentroid.y - prevCentroid.y
                            if (abs(panX) > 0.5f || abs(panY) > 0.5f) {
                                currentOnMove(panX, panY)
                            }
                        } else {
                            // Pinch to Zoom (Scale mode)
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
                        }

                        prevCentroid = currentCentroid
                        prevSpan = currentSpan
                        pressedChanges.forEach { it.consume() }
                    } else if (currentCount >= 3) {
                        isGestureDrag = true
                        longPressJob?.cancel()
                        tapTimeoutJob?.cancel()
                        tapCount = 0
                        currentOnTripleTap()
                        pressedChanges.forEach { it.consume() }
                        break
                    }
                }
            } finally {
                longPressJob?.cancel()
            }
        }
    }
}
