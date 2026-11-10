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
            if (isEdgeDrag) {
                // Dragging along screen edge turns Z (Roll) 360° directly with 1 finger
                val newZ = ((safeCurrentZ + dy * sensitivity) % 360f + 360f) % 360f
                onRotZ(newZ)
            } else {
                // Center drag: dx modifies Yaw (Y) 360°, dy modifies Pitch (X) 360°
                val newY = ((safeCurrentY + dx * sensitivity) % 360f + 360f) % 360f
                val newX = ((safeCurrentX - dy * sensitivity) % 360f + 360f) % 360f
                onRotY(newY)
                onRotX(newX)
            }
        }
        RotationAxis.X -> {
            val delta = if (abs(dy) > abs(dx)) -dy else -dx
            val newX = ((safeCurrentX + delta * sensitivity) % 360f + 360f) % 360f
            onRotX(newX)
        }
        RotationAxis.Y -> {
            val newY = ((safeCurrentY + dx * sensitivity) % 360f + 360f) % 360f
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
 * - See current degrees for X, Y, and Z.
 * - Lock/select specific rotation axis (ALL, X Pitch, Y Yaw, Z Roll).
 * - Quickly rotate ±45°, 180° flip, or reset to 0°.
 * - Use a continuous slider for precise 0°..360° rotation.
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

    val safeX = ((rotationX % 360f) + 360f) % 360f
    val safeY = ((rotationY % 360f) + 360f) % 360f
    val safeZ = ((rotationZ % 360f) + 360f) % 360f

    Column(
        modifier = modifier
            .wrapContentSize()
            .testTag("spatial_rotation_controller"),
        horizontalAlignment = Alignment.End
    ) {
        // Toggle pill button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
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
                    color = if (isExpanded) NeonCyan else Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("toggle_rotation_hud_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⟳ 360°",
                    color = if (isExpanded) NeonCyan else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "X:${safeX.toInt()}° Y:${safeY.toInt()}° Z:${safeZ.toInt()}°",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
        }

        // Expanded Control Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(min = 280.dp, max = 340.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEB10141D),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.6f)),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title and Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "360° Spatial Rotation (X, Y, Z)",
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Axis Selection Chips: [ALL] [X] [Y] [Z]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RotationAxis.values().forEach { axis ->
                            val isSelected = activeAxis == axis
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onActiveAxisChange(axis) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = axis.shortName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NeonCyan else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Angle Slider for active axis
                    val (currentAngle, onAngleChange, axisName) = when (activeAxis) {
                        RotationAxis.X -> Triple(safeX, onRotationXChange, "X (Pitch)")
                        RotationAxis.Y -> Triple(safeY, onRotationYChange, "Y (Yaw)")
                        RotationAxis.Z -> Triple(safeZ, onRotationZChange, "Z (Roll)")
                        RotationAxis.ALL -> Triple(safeY, onRotationYChange, "Y (Yaw)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$axisName: ${currentAngle.toInt()}°",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan,
                            modifier = Modifier.width(90.dp)
                        )
                        Slider(
                            value = currentAngle,
                            onValueChange = {
                                onStopAutoRotate()
                                onAngleChange(it)
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Stepper Buttons: [-45°] [+45°] [180° Flip] [Reset 0°]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentAngle - 45f) % 360f + 360f) % 360f
                                onAngleChange(newAngle)
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("-45°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentAngle + 45f) % 360f + 360f) % 360f
                                onAngleChange(newAngle)
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("+45°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                val newAngle = ((currentAngle + 180f) % 360f + 360f) % 360f
                                onAngleChange(newAngle)
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638))
                        ) {
                            Text("180°", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onStopAutoRotate()
                                onRotationXChange(0f)
                                onRotationYChange(0f)
                                onRotationZChange(0f)
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f))
                        ) {
                            Text("Reset", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "💡 اسحب بإصبع واحد للتدوير 360°. اسحب عند حافة الشاشة لتدوير Z (Roll)!",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
