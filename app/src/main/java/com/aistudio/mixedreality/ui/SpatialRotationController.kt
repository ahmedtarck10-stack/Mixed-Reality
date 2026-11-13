package com.aistudio.mixedreality.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.NeonCyan
import com.aistudio.mixedreality.ui.theme.TextPrimary
import com.aistudio.mixedreality.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Available rotation axes for 360-degree spatial manipulation.
 */
enum class RotationAxis(val label: String, val shortName: String, val icon: String) {
    ALL("XYZ Free", "ALL", "⟳"),
    X("Pitch (X)", "X", "↕"),
    Y("Yaw (Y)", "Y", "↔"),
    Z("Roll (Z)", "Z", "🔄")
}

/**
 * Applies delta gestures to rotation angles wrapping seamlessly 0°..360°.
 */
fun applyRotationDelta(
    axis: RotationAxis,
    dx: Float,
    dy: Float,
    isEdgeDrag: Boolean,
    currentRotX: Float,
    currentRotY: Float,
    currentRotZ: Float,
    onRotX: (Float) -> Unit,
    onRotY: (Float) -> Unit,
    onRotZ: (Float) -> Unit,
    sensitivity: Float = 0.45f
) {
    if (!dx.isFinite() || !dy.isFinite()) return
    val safeCurrentX = if (currentRotX.isFinite()) currentRotX else 0f
    val safeCurrentY = if (currentRotY.isFinite()) currentRotY else 0f
    val safeCurrentZ = if (currentRotZ.isFinite()) currentRotZ else 0f

    when (axis) {
        RotationAxis.ALL -> {
            // Full 360-degree rotation on 1-finger:
            // Horizontal drag (dx) rotates Y (Yaw) 360° seamlessly
            // Vertical drag (dy) rotates X (Pitch) 360° seamlessly
            val newY = ((safeCurrentY + dx * sensitivity) % 360f + 360f) % 360f
            val newX = ((safeCurrentX - dy * sensitivity) % 360f + 360f) % 360f
            onRotY(newY)
            onRotX(newX)
            if (isEdgeDrag) {
                // Outer edge drag also applies Roll on Z
                val newZ = ((safeCurrentZ + dy * sensitivity) % 360f + 360f) % 360f
                onRotZ(newZ)
            }
        }
        RotationAxis.X -> {
            val delta = if (abs(dy) > abs(dx)) -dy else -dx
            val newX = ((safeCurrentX + delta * sensitivity) % 360f + 360f) % 360f
            onRotX(newX)
        }
        RotationAxis.Y -> {
            val delta = if (abs(dx) >= abs(dy)) dx else -dy
            val newY = ((safeCurrentY + delta * sensitivity) % 360f + 360f) % 360f
            onRotY(newY)
        }
        RotationAxis.Z -> {
            val delta = if (abs(dy) > abs(dx)) dy else dx
            val newZ = ((safeCurrentZ + delta * sensitivity) % 360f + 360f) % 360f
            onRotZ(newZ)
        }
    }
}

/**
 * Floating 360-degree rotation controller HUD.
 * Allows users in Object, AR, and MR modes to:
 * - Switch 1-finger 360° drag lock directly between ALL, X, Y, and Z independently.
 * - Expand independent 0°..360° sliders for X, Y, and Z simultaneously.
 * - Quickly rotate ±45°, 180° flip, or reset individual axes / all axes to 0°.
 */
@Composable
fun SpatialRotationController(
    rotationX: Float,
    onRotationXChange: (Float) -> Unit,
    rotationY: Float,
    onRotationYChange: (Float) -> Unit,
    rotationZ: Float,
    onRotationZChange: (Float) -> Unit,
    activeAxis: RotationAxis,
    onActiveAxisChange: (RotationAxis) -> Unit,
    onStopAutoRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val safeX = if (rotationX.isFinite()) ((rotationX % 360f) + 360f) % 360f else 0f
    val safeY = if (rotationY.isFinite()) ((rotationY % 360f) + 360f) % 360f else 0f
    val safeZ = if (rotationZ.isFinite()) ((rotationZ % 360f) + 360f) % 360f else 0f

    val targetStepperAxis = if (activeAxis == RotationAxis.ALL) RotationAxis.Z else activeAxis
    val (currentStepAngle, onStepAngleChange, stepAxisLabel) = when (targetStepperAxis) {
        RotationAxis.X -> Triple(safeX, onRotationXChange, "X")
        RotationAxis.Y -> Triple(safeY, onRotationYChange, "Y")
        RotationAxis.Z -> Triple(safeZ, onRotationZChange, "Z")
        RotationAxis.ALL -> Triple(safeZ, onRotationZChange, "Z")
    }

    Column(
        modifier = modifier
            .wrapContentSize()
            .testTag("spatial_rotation_controller"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expanded Independent 3-Axis Control Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .widthIn(min = 290.dp, max = 356.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEB10141D),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.6f)),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header + Reset All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "360° Independent Axes (X · Y · Z)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = {
                                onStopAutoRotate()
                                onRotationXChange(0f)
                                onRotationYChange(0f)
                                onRotationZChange(0f)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("reset_all_rotations_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset all rotations",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    AxisAngleSliderRow(
                        axisLabel = "X",
                        angleDegrees = safeX,
                        isHighlighted = activeAxis == RotationAxis.X,
                        sliderTestTag = "slider_rotation_x",
                        onAngleChange = {
                            onStopAutoRotate()
                            onActiveAxisChange(RotationAxis.X)
                            onRotationXChange(it)
                        }
                    )

                    AxisAngleSliderRow(
                        axisLabel = "Y",
                        angleDegrees = safeY,
                        isHighlighted = activeAxis == RotationAxis.Y,
                        sliderTestTag = "slider_rotation_y",
                        onAngleChange = {
                            onStopAutoRotate()
                            onActiveAxisChange(RotationAxis.Y)
                            onRotationYChange(it)
                        }
                    )

                    AxisAngleSliderRow(
                        axisLabel = "Z",
                        angleDegrees = safeZ,
                        isHighlighted = activeAxis == RotationAxis.Z,
                        sliderTestTag = "slider_rotation_z",
                        onAngleChange = {
                            onStopAutoRotate()
                            onActiveAxisChange(RotationAxis.Z)
                            onRotationZChange(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Stepper Buttons for active axis + Reset All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentStepAngle - 45f) % 360f + 360f) % 360f
                                onStepAngleChange(newAngle)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("step_minus_45_button"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("$stepAxisLabel -45°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentStepAngle + 45f) % 360f + 360f) % 360f
                                onStepAngleChange(newAngle)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("step_plus_45_button"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("$stepAxisLabel +45°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentStepAngle + 180f) % 360f + 360f) % 360f
                                onStepAngleChange(newAngle)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("step_180_button"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("$stepAxisLabel 180°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                onRotationXChange(0f)
                                onRotationYChange(0f)
                                onRotationZChange(0f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("reset_rotation_button"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f))
                        ) {
                            Text("Reset", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "اختر X أو Y أو Z للتدوير 360° بإصبع واحد لكل محور منفرداً",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Compact 1-tap Axis Lock + 360° Expand Bar
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99202532),
                            Color(0xB310141D)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isExpanded) NeonCyan else Color.White.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RotationAxis.values().forEach { axis ->
                val isSelected = activeAxis == axis
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) NeonCyan.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onActiveAxisChange(axis) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("axis_chip_${axis.shortName.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = axis.shortName,
                        color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.85f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isExpanded) NeonCyan.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("toggle_rotation_hud_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⟳ ${safeX.toInt()}°·${safeY.toInt()}°·${safeZ.toInt()}°",
                    color = if (isExpanded) NeonCyan else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AxisAngleSliderRow(
    axisLabel: String,
    angleDegrees: Float,
    isHighlighted: Boolean,
    sliderTestTag: String,
    onAngleChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$axisLabel: ${angleDegrees.toInt()}°",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) NeonCyan else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = angleDegrees.coerceIn(0f, 360f),
            onValueChange = onAngleChange,
            valueRange = 0f..360f,
            modifier = Modifier
                .weight(1f)
                .testTag(sliderTestTag),
            colors = SliderDefaults.colors(
                thumbColor = if (isHighlighted) NeonCyan else Color.White,
                activeTrackColor = if (isHighlighted) NeonCyan else Color(0xFF70D6FF),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}
