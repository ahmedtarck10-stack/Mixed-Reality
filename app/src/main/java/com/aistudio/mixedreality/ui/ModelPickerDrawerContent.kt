package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import java.io.File
import java.util.Locale

/**
 * Bottom sheet content for model selection, file import, and visual properties.
 */
@Composable
fun ModelPickerDrawerContent(
    customModelFile: File?,
    onPickFileClick: () -> Unit,
    selectedModel: ModelItem,
    onSelectModel: (ModelItem) -> Unit,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    metallic: Float,
    onMetallicChange: (Float) -> Unit,
    roughness: Float,
    onRoughnessChange: (Float) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    rotationY: Float = 0f,
    onRotationYChange: (Float) -> Unit = {},
    rotationZ: Float = 0f,
    onRotationZChange: (Float) -> Unit = {},
    isAutoRotating: Boolean,
    onToggleAutoRotate: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "3D Models & Files",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Pick a local 3D file (.glb/.gltf) or choose a preset",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FILE PICKER ACTION BUTTON (Requirement 4)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (customModelFile != null) NeonCyan.copy(alpha = 0.2f) else SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (customModelFile != null) NeonCyan else Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickFileClick() }
                .testTag("pick_custom_model_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = NeonCyan.copy(alpha = 0.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (customModelFile != null) "Custom Model: ${customModelFile.name}" else "Select 3D Model from Files (Open)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (customModelFile != null) NeonCyan else TextPrimary
                    )
                    Text(
                        text = "Supports 3D formats (.glb / .gltf)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PRESET MODELS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Preset Model List
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ModelItem.values()) { item ->
                val isSelected = selectedModel == item && customModelFile == null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) NeonCyan else Color.Transparent
                    ),
                    modifier = Modifier
                        .clickable { onSelectModel(item) }
                        .testTag("model_item_${item.name.lowercase(Locale.ROOT)}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = item.iconDesc, fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) NeonCyan else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Color selection palette
        Text(
            text = "MATERIAL COLOR",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        val colorPalette = listOf(
            Color(0xFF00E5FF), // Neon Cyan
            Color(0xFF7C4DFF), // Vision Purple
            Color(0xFF69F0AE), // Matrix Green
            Color(0xFFFF5252), // Flame Red
            Color(0xFFFFD740), // Gold
            Color(0xFFFFFFFF), // Pure White
            Color(0xFF263238)  // Obsidian Black
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            colorPalette.forEach { color ->
                val isSelected = selectedColor == color
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metallic slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Metallic",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = metallic,
                onValueChange = onMetallicChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "%.0f%%".format(metallic * 100),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        // Scale slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = scale.coerceIn(0.4f, 2.5f),
                onValueChange = onScaleChange,
                valueRange = 0.4f..2.5f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "%.1fx".format(scale),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3D SPATIAL ROTATION (360° ON ALL AXES)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "3D ROTATION (360° SPATIAL)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextSecondary
            )
            TextButton(
                onClick = {
                    onRotationXChange(0f)
                    onRotationYChange(0f)
                    onRotationZChange(0f)
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Reset 0°", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        val safeX = ((rotationX % 360f) + 360f) % 360f
        val safeY = ((rotationY % 360f) + 360f) % 360f
        val safeZ = ((rotationZ % 360f) + 360f) % 360f

        // Rotation X (Pitch)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Pitch (X)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = safeX,
                onValueChange = { onRotationXChange(it) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "${safeX.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        // Rotation Y (Yaw)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Yaw (Y)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = safeY,
                onValueChange = { onRotationYChange(it) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "${safeY.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        // Rotation Z (Roll)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Roll (Z)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = safeZ,
                onValueChange = { onRotationZChange(it) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "${safeZ.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Auto rotate switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Auto-Orbit Rotation",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Switch(
                checked = isAutoRotating,
                onCheckedChange = { onToggleAutoRotate() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonCyan
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
