package com.aistudio.mixedreality.ui

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import com.google.ar.core.Config
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.PlacementScene
import io.github.sceneview.ar.node.AnchorNode
import com.google.android.filament.LightManager
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberViewNodeManager
import java.io.File

import com.google.android.filament.Engine
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.rememberEnvironment

/**
 * Checks whether Google Play Services for AR is installed without triggering Play Store install IPC.
 */
fun isARCoreSupportedAndInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.google.ar.core", 0)
        val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(context)
        availability == com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
    } catch (_: Throwable) {
        false
    }
}

/**
 * Live ARCore scene with surface plane finding, instant placement, and anchor tracking.
 */
@Composable
fun ARMixedRealityView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    scale: Float,
    clearTrigger: Int,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modifier: Modifier = Modifier
) {
    val customModelInstance = key(customModelFile?.absolutePath) {
        customModelFile?.let { file ->
            rememberModelInstance(
                modelLoader = modelLoader,
                fileLocation = "file://${file.absolutePath}"
            )
        }
    }

    // Key on clearTrigger to detach all anchors when Clear is clicked
    key(clearTrigger) {
        val material = remember(materialLoader, color) {
            materialLoader.createColorInstance(
                color = color,
                metallic = 0.2f,
                roughness = 0.5f
            )
        }

        val visorMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(
                color = Color(0xFF141923),
                metallic = 0.85f,
                roughness = 0.15f
            )
        }

        Box(modifier = modifier.fillMaxSize()) {
            PlacementScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
                instantPlacement = false,
                fadePlaneOnFirstPlacement = true,
                showReticle = isModelVisible,
                onPlaced = { anchor ->
                    if (!isModelVisible) return@PlacementScene
                    AnchorNode(anchor = anchor) {
                        if (customModelFile != null) {
                            if (customModelInstance != null) {
                                ModelNode(
                                    modelInstance = customModelInstance,
                                    scaleToUnits = 0.35f * scale,
                                    centerOrigin = Position(0f, 0f, 0f),
                                    position = Position(0f, 0f, 0f),
                                    autoAnimate = true
                                )
                            }
                            // Do not show cube while model is loading or parsing
                        } else {
                            when (modelItem) {
                                ModelItem.VISION_HEADSET -> {
                                    Node {
                                        SphereNode(
                                            radius = 0.14f * scale,
                                            materialInstance = visorMaterial,
                                            position = Position(0f, 0f, 0.02f * scale)
                                        )
                                        CubeNode(
                                            size = Size(0.32f * scale, 0.16f * scale, 0.03f * scale),
                                            materialInstance = material,
                                            position = Position(0f, 0f, -0.02f * scale)
                                        )
                                    }
                                }
                                ModelItem.HOLO_CUBE -> {
                                    CubeNode(
                                        size = Size(0.22f * scale, 0.22f * scale, 0.22f * scale),
                                        materialInstance = material
                                    )
                                }
                                ModelItem.QUANTUM_ORB -> {
                                    SphereNode(
                                        radius = 0.14f * scale,
                                        materialInstance = material
                                    )
                                }
                                ModelItem.TOY_CAR, ModelItem.SPATIAL_CARD -> {
                                    CubeNode(
                                        size = Size(0.2f * scale, 0.2f * scale, 0.2f * scale),
                                        materialInstance = material
                                    )
                                }
                            }
                        }
                    }
                }
            )

            // AR Placement Guidance HUD
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isModelVisible) "Scan surfaces • Tap to place 3D model" else "Scene Cleared • Tap Open to load model",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

data class SimSpatialAnchor(
    val id: Long,
    val position: Position,
    val modelItem: ModelItem,
    val customModelFile: File?,
    val color: Color,
    val scale: Float
)

/**
 * Spatial AR simulation view using SceneView and virtual ground tracking.
 * Used on emulators or systems where Google Play Services for AR is not available.
 */
@Composable
fun SpatialSimulationARView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    scale: Float,
    clearTrigger: Int,
    isRecording: Boolean,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val placedNodes = remember { mutableStateListOf<SimSpatialAnchor>() }

    var viewportWidth by remember { mutableIntStateOf(1080) }
    var viewportHeight by remember { mutableIntStateOf(1920) }

    LaunchedEffect(clearTrigger) {
        placedNodes.clear()
    }

    val windowManager = rememberViewNodeManager()

    val pbrMaterial = remember(materialLoader, color) {
        materialLoader.createColorInstance(
            color = color,
            metallic = 0.2f,
            roughness = 0.5f
        )
    }

    val visorGlassMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF141923),
            metallic = 0.85f,
            roughness = 0.15f
        )
    }

    val floorGridMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0x3300E5FF),
            metallic = 0.4f,
            roughness = 0.6f
        )
    }

    val toyCarModel = rememberModelInstance(
        modelLoader = modelLoader,
        fileLocation = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb"
    )

    val customModelInstance = key(customModelFile?.absolutePath) {
        customModelFile?.let { file ->
            rememberModelInstance(
                modelLoader = modelLoader,
                fileLocation = "file://${file.absolutePath}"
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                viewportWidth = it.width
                viewportHeight = it.height
            }
    ) {
        // Camera passthrough background for spatial AR simulation
        CameraPassthroughBackground(modifier = Modifier.fillMaxSize())

        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            viewNodeWindowManager = windowManager,
            cameraManipulator = rememberCameraManipulator(),
            cameraNode = rememberCameraNode(engine) {
                // Stepped 1 step back for spatial AR viewing
                position = Position(0f, 0.4f, 3.6f)
            },
            mainLightNode = rememberMainLightNode(engine),
            fillLightNode = rememberFillLightNode(engine),
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { event: MotionEvent, node: Node? ->
                    if (node != null || !isModelVisible) return@rememberOnGestureListener
                    val normX = (event.x / viewportWidth.coerceAtLeast(1)) * 2f - 1f
                    val normY = (event.y / viewportHeight.coerceAtLeast(1)) * 2f - 1f
                    val posX = normX * 1.4f
                    val posY = -0.3f - (normY * 0.25f)
                    val posZ = -1.5f - ((1f - normY) * 1.5f)
                    placedNodes.add(
                        SimSpatialAnchor(
                            id = System.currentTimeMillis() + placedNodes.size,
                            position = Position(posX, posY, posZ),
                            modelItem = modelItem,
                            customModelFile = customModelFile,
                            color = color,
                            scale = scale
                        )
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        ) {
            // Virtual Ground Tracking Surface
            CubeNode(
                size = Size(6f, 0.005f, 6f),
                position = Position(0f, -0.6f, -1.2f),
                materialInstance = floorGridMaterial
            )

            // Placed Anchors
            if (isModelVisible) {
                placedNodes.forEach { anchor ->
                    if (anchor.customModelFile != null) {
                        if (customModelInstance != null) {
                            ModelNode(
                                modelInstance = customModelInstance,
                                scaleToUnits = 0.5f * anchor.scale,
                                centerOrigin = Position(0f, 0f, 0f),
                                autoAnimate = true,
                                position = anchor.position
                            )
                        }
                        // Do not render cube when custom model is loaded or loading
                    } else {
                        when (anchor.modelItem) {
                            ModelItem.VISION_HEADSET -> {
                                SphereNode(
                                    radius = 0.25f * anchor.scale,
                                    materialInstance = visorGlassMaterial,
                                    position = anchor.position
                                ) {
                                    CylinderNode(
                                        radius = 0.27f * anchor.scale,
                                        height = 0.08f * anchor.scale,
                                        materialInstance = pbrMaterial,
                                        position = Position(0f, 0f, -0.05f)
                                    )
                                }
                            }
                            ModelItem.TOY_CAR -> {
                                toyCarModel?.let { instance ->
                                    ModelNode(
                                        modelInstance = instance,
                                        scaleToUnits = 0.6f * anchor.scale,
                                        position = anchor.position
                                    )
                                } ?: CubeNode(
                                    size = Size(0.35f * anchor.scale, 0.35f * anchor.scale, 0.35f * anchor.scale),
                                    materialInstance = pbrMaterial,
                                    position = anchor.position
                                )
                            }
                            ModelItem.HOLO_CUBE -> {
                                CubeNode(
                                    size = Size(0.4f * anchor.scale, 0.4f * anchor.scale, 0.4f * anchor.scale),
                                    materialInstance = pbrMaterial,
                                    position = anchor.position
                                )
                            }
                            ModelItem.QUANTUM_ORB -> {
                                SphereNode(
                                    radius = 0.25f * anchor.scale,
                                    materialInstance = pbrMaterial,
                                    position = anchor.position
                                )
                            }
                            ModelItem.SPATIAL_CARD -> {
                                CubeNode(
                                    size = Size(0.45f * anchor.scale, 0.3f * anchor.scale, 0.05f * anchor.scale),
                                    materialInstance = pbrMaterial,
                                    position = anchor.position
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Guidance Pill
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NeonCyan)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isModelVisible) "Spatial AR Simulation • Tap ground to place" else "Scene Cleared • Tap Open to load 3D model",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Camera permission request card placeholder.
 */
@Composable
fun PermissionRequestPlaceholder(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = NeonGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mixed Reality AR mode requires camera permissions to detect surfaces and track spatial objects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_camera_permission_button")
                ) {
                    Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
