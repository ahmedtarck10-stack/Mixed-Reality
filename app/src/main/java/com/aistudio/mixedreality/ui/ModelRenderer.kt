package com.aistudio.mixedreality.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference

private const val HEAVY_MESH_SHADOW_THRESHOLD_BYTES = 2L * 1024L * 1024L

sealed class ModelLoadState {
    data object Idle : ModelLoadState()
    data class Loading(val progress: Float, val status: String) : ModelLoadState()
    data class Ready(val instance: ModelInstance) : ModelLoadState()
    data class Error(val reason: String) : ModelLoadState()
}

private suspend fun mapOrStreamDirectBuffer(file: File): ByteBuffer = withContext(Dispatchers.IO) {
    runCatching {
        java.io.FileInputStream(file).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0L, file.length())
        }
    }.getOrElse {
        val len = file.length()
        require(len in 1L..Int.MAX_VALUE.toLong()) { "File size out of native ByteBuffer range ($len bytes)" }
        val direct = ByteBuffer.allocateDirect(len.toInt())
        val chunk = ByteArray(262_144)
        java.io.BufferedInputStream(java.io.FileInputStream(file), 262_144).use { bis ->
            while (true) {
                val read = bis.read(chunk)
                if (read <= 0) break
                direct.put(chunk, 0, read)
            }
        }
        direct.flip()
        direct
    }
}

/**
 * Loads .glb/.gltf models via zero-copy read-only mmap and Filament async resource upload
 * (yielding per frame to avoid Main-thread ANR on multi-million-polygon meshes), automatically
 * retrying with mobile texture downsampling under memory pressure, and freeing GPU memory
 * synchronously when replaced/cleared.
 */
@Composable
fun rememberOptimizedModelState(
    modelLoader: ModelLoader,
    customModelFile: File?,
    onModelError: ((String) -> Unit)? = null
): ModelLoadState {
    val filePath = customModelFile?.absolutePath
    var state by remember(modelLoader, filePath) {
        mutableStateOf<ModelLoadState>(
            if (customModelFile != null && customModelFile.exists() && customModelFile.length() > 0L) {
                ModelLoadState.Loading(0f, "Mapping 3D model...")
            } else {
                ModelLoadState.Idle
            }
        )
    }

    val activeAssetRef = remember(modelLoader, filePath) { AtomicReference<FilamentAsset?>(null) }
    val activeLegacyInstanceRef = remember(modelLoader, filePath) { AtomicReference<ModelInstance?>(null) }

    DisposableEffect(modelLoader, filePath) {
        onDispose {
            // Destroy active GPU FilamentAsset immediately on mode switch / clear to prevent 2x VRAM spikes
            activeAssetRef.getAndSet(null)?.let { asset ->
                runCatching { modelLoader.assetLoader.destroyAsset(asset) }
            }
            activeLegacyInstanceRef.getAndSet(null)?.let { inst ->
                runCatching { modelLoader.assetLoader.destroyAsset(inst.asset) }
            }
        }
    }

    LaunchedEffect(modelLoader, filePath) {
        val file = customModelFile
        if (file == null || !file.exists() || file.length() <= 0L) {
            state = ModelLoadState.Idle
            return@LaunchedEffect
        }

        suspend fun loadSingleFile(targetFile: File, allowFallback: Boolean): ModelLoadState {
            var activeResourceLoader: ResourceLoader? = null
            var createdAsset: FilamentAsset? = null
            try {
                state = ModelLoadState.Loading(0.05f, "Streaming geometry buffer...")
                val mappedBuffer = mapOrStreamDirectBuffer(targetFile)

                val asset = modelLoader.assetLoader.createAsset(mappedBuffer)
                    ?: error("Filament could not parse ${targetFile.name} (unsupported or corrupt binary layout)")
                createdAsset = asset

                val resLoader = ResourceLoader(
                    modelLoader.engine,
                    /* normalizeSkinningWeights = */ false
                )
                activeResourceLoader = resLoader

                // Attach external resource URIs via zero-copy mmap if .gltf references sidecar files
                val baseDir = targetFile.parentFile
                val uris = asset.resourceUris
                if (uris != null && baseDir != null) {
                    for (resUri in uris) {
                        if (resUri != null && !resUri.startsWith("data:", ignoreCase = true)) {
                            val sidecar = File(baseDir, resUri)
                            if (sidecar.exists() && sidecar.length() > 0L) {
                                val sidecarBuf = mapOrStreamDirectBuffer(sidecar)
                                resLoader.addResourceData(resUri, sidecarBuf)
                            }
                        }
                    }
                }

                state = ModelLoadState.Loading(0.15f, "Uploading mesh to GPU...")
                if (!resLoader.asyncBeginLoad(asset)) {
                    resLoader.loadResources(asset)
                } else {
                    while (resLoader.asyncGetLoadProgress() < 1.0f) {
                        resLoader.asyncUpdateLoad()
                        val prog = 0.15f + (resLoader.asyncGetLoadProgress().coerceIn(0f, 1f) * 0.80f)
                        state = ModelLoadState.Loading(prog, "Decompressing & uploading (${(prog * 100).toInt()}%)...")
                        delay(12L)
                    }
                }

                asset.releaseSourceData()
                resLoader.evictResourceData()
                resLoader.destroy()
                activeResourceLoader = null

                val instance = asset.instance ?: modelLoader.assetLoader.createInstance(asset)
                    ?: error("Filament failed to instantiate ${targetFile.name}")
                activeAssetRef.set(asset)
                createdAsset = null // Transferred ownership to activeAssetRef + DisposableEffect
                return ModelLoadState.Ready(instance)
            } catch (oom: OutOfMemoryError) {
                runCatching { activeResourceLoader?.asyncCancelLoad() }
                runCatching { activeResourceLoader?.evictResourceData() }
                runCatching { activeResourceLoader?.destroy() }
                runCatching { createdAsset?.let { modelLoader.assetLoader.destroyAsset(it) } }
                System.gc()

                if (allowFallback) {
                    state = ModelLoadState.Loading(0.1f, "Optimizing textures for mobile RAM...")
                    val downsampled = withContext(Dispatchers.IO) {
                        val cacheParent = targetFile.parentFile ?: targetFile.absoluteFile.parentFile
                        if (cacheParent != null) {
                            com.aistudio.mixedreality.util.StreamingMeshProcessor.downsampleGlbTexturesForMobile(
                                cacheParent,
                                targetFile,
                                maxEdge = 1024
                            )
                        } else null
                    }
                    if (downsampled != null && downsampled.exists()) {
                        return loadSingleFile(downsampled, allowFallback = false)
                    }
                }
                return ModelLoadState.Error("Memory limit exceeded loading ${targetFile.name}: ${oom.message ?: "Out of memory"}")
            } catch (t: Throwable) {
                runCatching { activeResourceLoader?.asyncCancelLoad() }
                runCatching { activeResourceLoader?.evictResourceData() }
                runCatching { activeResourceLoader?.destroy() }
                runCatching { createdAsset?.let { modelLoader.assetLoader.destroyAsset(it) } }
                if (t is kotlinx.coroutines.CancellationException) throw t

                // Legacy fallback via ModelLoader for WebP if native WebP fails on legacy GPU or non-standard glTF
                if (allowFallback && targetFile.length() < 48L * 1024L * 1024L) {
                    val fallbackInstance = runCatching {
                        modelLoader.loadModelInstance("file://${targetFile.absolutePath}")
                    }.getOrNull()
                    if (fallbackInstance != null) {
                        activeLegacyInstanceRef.set(fallbackInstance)
                        return ModelLoadState.Ready(fallbackInstance)
                    }
                }
                val reason = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
                return ModelLoadState.Error("Failed to render ${targetFile.name}: $reason")
            }
        }

        val result = loadSingleFile(file, allowFallback = true)
        state = result
        if (result is ModelLoadState.Error) {
            android.util.Log.e("ModelRenderer", result.reason)
            onModelError?.invoke(result.reason)
        }
    }

    return state
}

@Composable
fun rememberOptimizedModelInstance(
    modelLoader: ModelLoader,
    customModelFile: File?
): ModelInstance? {
    val state = rememberOptimizedModelState(modelLoader, customModelFile)
    return (state as? ModelLoadState.Ready)?.instance
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


