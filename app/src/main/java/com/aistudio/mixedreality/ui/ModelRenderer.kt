package com.aistudio.mixedreality.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.R
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.ViewNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val MMAP_GLB_THRESHOLD_BYTES = 12L * 1024L * 1024L
private const val HEAVY_MESH_SHADOW_THRESHOLD_BYTES = 2L * 1024L * 1024L

private fun glbHeaderContainsWebp(file: File): Boolean {
    return runCatching {
        val inspectLen = minOf(file.length(), 2L * 1024L * 1024L).toInt()
        if (inspectLen < 20) return@runCatching false
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            if (header[0] != 'g'.code.toByte() || header[1] != 'l'.code.toByte() ||
                header[2] != 'T'.code.toByte() || header[3] != 'F'.code.toByte()
            ) {
                return@runCatching false
            }
            val chunkLen = ByteBuffer.wrap(ByteArray(4).also { raf.readFully(it) })
                .order(ByteOrder.LITTLE_ENDIAN).int
            val jsonReadLen = chunkLen.coerceIn(0, inspectLen - 20)
            if (jsonReadLen <= 0) return@runCatching false
            raf.seek(20L)
            val jsonBytes = ByteArray(jsonReadLen)
            raf.readFully(jsonBytes)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)
            jsonStr.contains("image/webp", ignoreCase = true)
        }
    }.getOrDefault(false)
}

/**
 * Loads large local .glb models via zero-copy read-only FileChannel memory mapping so Android's
 * Java heap is never duplicated, and cleanly frees replaced/cleared GPU FilamentAssets.
 */
@Composable
fun rememberOptimizedModelInstance(
    modelLoader: ModelLoader,
    customModelFile: File?
): ModelInstance? {
    val filePath = customModelFile?.absolutePath
    val instance by produceState<ModelInstance?>(
        initialValue = null,
        key1 = modelLoader,
        key2 = filePath
    ) {
        val file = customModelFile
        if (file == null || !file.exists() || file.length() <= 0L) {
            value = null
            return@produceState
        }

        val useZeroCopyMmap = file.extension.equals("glb", ignoreCase = true) &&
            file.length() >= MMAP_GLB_THRESHOLD_BYTES &&
            !glbHeaderContainsWebp(file)

        if (useZeroCopyMmap) {
            val mappedBuffer = withContext(Dispatchers.IO) {
                runCatching {
                    RandomAccessFile(file, "r").channel.use { channel ->
                        channel.map(FileChannel.MapMode.READ_ONLY, 0L, file.length())
                    }
                }.getOrNull()
            }
            if (mappedBuffer != null) {
                val created = runCatching {
                    val asset = modelLoader.createModel(
                        buffer = mappedBuffer,
                        releaseSourceData = true
                    )
                    modelLoader.createInstance(asset)
                }.getOrNull()
                if (created != null) {
                    value = created
                    return@produceState
                }
            }
        }

        value = runCatching {
            modelLoader.loadModelInstance("file://${file.absolutePath}")
        }.getOrNull()
    }

    val cleanupHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(modelLoader, instance) {
        val activeInstance = instance
        onDispose {
            if (activeInstance != null) {
                cleanupHandler.post {
                    runCatching { modelLoader.destroyModel(activeInstance.asset) }
                }
            }
        }
    }

    return instance
}

enum class ViewportMode(val title: String, val titleRes: Int = 0) {
    MR("MR", R.string.mode_ar),
    AR("AR", R.string.mode_ar),
    OBJECT("Object", R.string.mode_3d)
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

// Liquid glass capsule pill styling colors matching user reference
val PillContainerColor = Color(0x8010141D)
val PillBorderColor = Color(0x40FFFFFF)
val PillSelectedColor = Color(0x38FFFFFF)
val PillSelectedBorderColor = Color(0x99FFFFFF)
val PillTextColor = Color.White
val PillUnselectedTextColor = Color(0xB3FFFFFF)
val RecButtonColor = Color(0xFFFF263E)

/**
 * Common 3D model node rendering inside a SceneScope.
 * Handles custom imported .glb/.gltf/.obj/.usdz/.3mf/.stl/.ply models, preset models, and procedural geometry.
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
    rotationX: Float = 0f,
    rotationZ: Float = 0f,
    position: Position = Position(0f, 0f, 0f),
    orientation: SpatialQuaternion? = null,
    disableShadows: Boolean = false
) {
    val safeScale = if (scale.isFinite() && scale > 0f) scale.coerceIn(0.1f, 2500.0f) else 1.0f
    val safeRotX = if (rotationX.isFinite()) ((rotationX % 360f) + 360f) % 360f else 0f
    val safeRotY = if (rotationY.isFinite()) ((rotationY % 360f) + 360f) % 360f else 0f
    val safeRotZ = if (rotationZ.isFinite()) ((rotationZ % 360f) + 360f) % 360f else 0f

    val targetQuat = remember(orientation, safeRotX, safeRotY, safeRotZ) {
        (orientation ?: SpatialQuaternion.fromEulerZYX(safeRotX, safeRotY, safeRotZ)).normalized()
    }
    var rotNode by remember { mutableStateOf<Node?>(null) }
    DisposableEffect(rotNode, targetQuat.x, targetQuat.y, targetQuat.z, targetQuat.w) {
        rotNode?.quaternion = Quaternion(targetQuat.x, targetQuat.y, targetQuat.z, targetQuat.w)
        onDispose {}
    }

    // Parent Node receives position and uniform scale; single quaternion child node gives gimbal-free 360° X, Y, Z rotation
    Node(
        position = position,
        scale = Scale(safeScale)
    ) {
        Node(
            apply = {
                rotNode = this
                quaternion = Quaternion(targetQuat.x, targetQuat.y, targetQuat.z, targetQuat.w)
            }
        ) {
            // If user loaded a custom file and instance is ready, render it
            if (customModelFile != null) {
                if (customModelInstance != null) {
                    val isHeavyMesh = remember(customModelFile.absolutePath, disableShadows) {
                        disableShadows || customModelFile.length() > HEAVY_MESH_SHADOW_THRESHOLD_BYTES
                    }
                    ModelNode(
                        modelInstance = customModelInstance,
                        scaleToUnits = 1.0f,
                        centerOrigin = Position(0f, 0f, 0f),
                        autoAnimate = true,
                        apply = {
                            if (isHeavyMesh) {
                                isShadowCaster = false
                                isShadowReceiver = false
                            }
                        }
                    )
                }
            } else {
                        // Otherwise render selected preset model with canonical dimensions
                        when (modelItem) {
            ModelItem.VISION_HEADSET -> {
                // High-fidelity Vision Pro Spatial Visor (strictly non-overlapping geometry to eliminate Z-fighting & flicker)
                Node(
                    rotation = Rotation(x = 10f, y = 0f, z = 0f)
                ) {
                    // Front dark laminated glass visor (strictly in front: Z in [0.06, 0.14])
                    CubeNode(
                        size = Size(1.90f, 0.82f, 0.08f),
                        materialInstance = visorGlassMaterial,
                        position = Position(0f, 0f, 0.10f)
                    )
                    // Aerospace aluminum chassis frame positioned cleanly behind glass (Z in [-0.09, 0.05])
                    CubeNode(
                        size = Size(2.00f, 0.90f, 0.14f),
                        materialInstance = pbrMaterial,
                        position = Position(0f, 0f, -0.02f)
                    )
                    // Left headband side arm
                    CubeNode(
                        size = Size(0.12f, 0.35f, 0.70f),
                        materialInstance = visorGlassMaterial,
                        position = Position(-0.95f, 0f, -0.42f)
                    )
                    // Right headband side arm
                    CubeNode(
                        size = Size(0.12f, 0.35f, 0.70f),
                        materialInstance = visorGlassMaterial,
                        position = Position(0.95f, 0f, -0.42f)
                    )
                    // Rear headband fit strap
                    CubeNode(
                        size = Size(1.85f, 0.28f, 0.10f),
                        materialInstance = visorGlassMaterial,
                        position = Position(0f, 0f, -0.78f)
                    )
                }
            }
            ModelItem.TOY_CAR -> {
                toyCarModel?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 1.2f,
                        centerOrigin = Position(0f, 0f, 0f),
                        autoAnimate = true
                    )
                } ?: CubeNode(
                    size = Size(0.7f, 0.7f, 0.7f),
                    materialInstance = pbrMaterial,
                    rotation = Rotation(x = 15f, y = 0f, z = 0f)
                )
            }
            ModelItem.HOLO_CUBE -> {
                CubeNode(
                    size = Size(0.85f, 0.85f, 0.85f),
                    materialInstance = pbrMaterial,
                    rotation = Rotation(x = 20f, y = 0f, z = 0f)
                )
            }
            ModelItem.QUANTUM_ORB -> {
                SphereNode(
                    radius = 0.55f,
                    materialInstance = pbrMaterial
                )
            }
            ModelItem.SPATIAL_CARD -> {
                if (windowManager != null) {
                    ViewNode(
                        windowManager = windowManager,
                        unlit = true
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
                        size = Size(0.8f, 0.5f, 0.08f),
                        materialInstance = pbrMaterial
                    )
                }
            }
        }
            }
        }
    }
}


