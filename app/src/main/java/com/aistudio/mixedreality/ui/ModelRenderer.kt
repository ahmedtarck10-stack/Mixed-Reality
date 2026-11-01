package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.ViewNode
import java.io.File

enum class ViewportMode(val title: String) {
    MR("MR"),
    AR("AR"),
    OBJECT("Object")
}

enum class ModelItem(
    val title: String,
    val subtitle: String,
    val iconDesc: String
) {
    VISION_HEADSET("Vision Headset", "Mixed Reality Spatial Visor", "🥽"),
    TOY_CAR("Khronos Toy Car", "Realistic 3D glTF Model", "🏎️"),
    HOLO_CUBE("Hologram Cube", "PBR Reflective Geometry", "🧊"),
    QUANTUM_ORB("Quantum Orb", "Metallic Specular Sphere", "🔮"),
    SPATIAL_CARD("Spatial Interface", "3D Floating UI Plane", "📄")
}

// Capsule pill styling colors matching user reference
val PillContainerColor = Color(0xFFAEB4BC)
val PillSelectedColor = Color(0xFF8B929B)
val PillTextColor = Color(0xFF1E2227)
val PillUnselectedTextColor = Color(0xFF33383F)
val RecButtonColor = Color(0xFFE53935)

/**
 * Common 3D model node rendering inside a SceneScope.
 * Handles custom imported .glb/.gltf models, preset models, and procedural geometry.
 */
@Composable
fun SceneScope.Render3DModel(
    modelItem: ModelItem,
    customModelFile: File?,
    customModelInstance: ModelInstance?,
    pbrMaterial: MaterialInstance,
    visorGlassMaterial: MaterialInstance,
    toyCarModel: ModelInstance?,
    windowManager: ViewNode.WindowManager?,
    scale: Float,
    rotationY: Float,
    position: Position = Position(0f, 0f, 0f)
) {
    // If user loaded a custom file and instance is ready, render it
    if (customModelFile != null) {
        if (customModelInstance != null) {
            ModelNode(
                modelInstance = customModelInstance,
                scaleToUnits = 1.0f * scale,
                centerOrigin = Position(0f, 0f, 0f),
                autoAnimate = true,
                position = position,
                rotation = Rotation(x = 0f, y = rotationY, z = 0f)
            )
        }
        // Do not render a cube while loading or parsing custom 3D models
        return
    }

    // Otherwise render selected preset model
    when (modelItem) {
        ModelItem.VISION_HEADSET -> {
            // High-fidelity Vision Pro Spatial Visor (strictly non-overlapping geometry to eliminate Z-fighting & flicker)
            Node(
                position = position,
                rotation = Rotation(x = 10f, y = rotationY, z = 0f)
            ) {
                // Front dark laminated glass visor (strictly in front: Z in [0.06, 0.14])
                CubeNode(
                    size = Size(1.90f * scale, 0.82f * scale, 0.08f * scale),
                    materialInstance = visorGlassMaterial,
                    position = Position(0f, 0f, 0.10f * scale)
                )
                // Aerospace aluminum chassis frame positioned cleanly behind glass (Z in [-0.09, 0.05])
                CubeNode(
                    size = Size(2.00f * scale, 0.90f * scale, 0.14f * scale),
                    materialInstance = pbrMaterial,
                    position = Position(0f, 0f, -0.02f * scale)
                )
                // Left headband side arm
                CubeNode(
                    size = Size(0.12f * scale, 0.35f * scale, 0.70f * scale),
                    materialInstance = visorGlassMaterial,
                    position = Position(-0.95f * scale, 0f, -0.42f * scale)
                )
                // Right headband side arm
                CubeNode(
                    size = Size(0.12f * scale, 0.35f * scale, 0.70f * scale),
                    materialInstance = visorGlassMaterial,
                    position = Position(0.95f * scale, 0f, -0.42f * scale)
                )
                // Rear headband fit strap
                CubeNode(
                    size = Size(1.85f * scale, 0.28f * scale, 0.10f * scale),
                    materialInstance = visorGlassMaterial,
                    position = Position(0f, 0f, -0.78f * scale)
                )
            }
        }
        ModelItem.TOY_CAR -> {
            toyCarModel?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 1.2f * scale,
                    autoAnimate = true,
                    position = position,
                    rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                )
            } ?: CubeNode(
                size = Size(0.7f * scale, 0.7f * scale, 0.7f * scale),
                materialInstance = pbrMaterial,
                position = position,
                rotation = Rotation(x = 15f, y = rotationY, z = 0f)
            )
        }
        ModelItem.HOLO_CUBE -> {
            CubeNode(
                size = Size(0.85f * scale, 0.85f * scale, 0.85f * scale),
                materialInstance = pbrMaterial,
                position = position,
                rotation = Rotation(x = 20f, y = rotationY, z = 0f)
            )
        }
        ModelItem.QUANTUM_ORB -> {
            SphereNode(
                radius = 0.55f * scale,
                materialInstance = pbrMaterial,
                position = position,
                rotation = Rotation(x = 0f, y = rotationY, z = 0f)
            )
        }
        ModelItem.SPATIAL_CARD -> {
            if (windowManager != null) {
                ViewNode(
                    windowManager = windowManager,
                    unlit = true,
                    position = position,
                    rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardNavy),
                        border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .width(260.dp)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🥽", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mixed Reality",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Spatial 3D Model Interface",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                CubeNode(
                    size = Size(0.8f * scale, 0.5f * scale, 0.08f * scale),
                    materialInstance = pbrMaterial,
                    position = position,
                    rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                )
            }
        }
    }
}
