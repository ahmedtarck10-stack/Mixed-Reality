package com.aistudio.mixedreality.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader

/**
 * Interactive AR Tracking Test & Simulation View.
 * Provides an on-device simulation of ARCore plane tracking, tap-to-place anchoring,
 * IMU sensor damping (1.5mm deadband), and spatial gestures for emulators and devices
 * without Google Play Services for AR.
 */
@Composable
fun ARTrackingSimulationView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    metallic: Float = 0.25f,
    roughness: Float = 0.45f,
    scale: Float,
    onScaleChange: (Float) -> Unit = {},
    rotationY: Float = 0f,
    onRotationYChange: (Float) -> Unit = {},
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    rotationZ: Float = 0f,
    onRotationZChange: (Float) -> Unit = {},
    clearTrigger: Int = 0,
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
    activeRotationAxis: RotationAxis = RotationAxis.ALL,
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    isFlashlightOn: Boolean = false,
    onStopAutoRotate: () -> Unit = {},
    onFallbackToObject: () -> Unit = {},
    engine: Engine,
    modelLoader: ModelLoader? = null,
    materialLoader: MaterialLoader? = null,
    environmentLoader: EnvironmentLoader? = null
) {
    val localModelLoader = rememberModelLoader(engine)
    val localMaterialLoader = rememberMaterialLoader(engine)
    val localEnvironmentLoader = rememberEnvironmentLoader(engine)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Simulation tracking states
    var simulatedTrackingState by remember { mutableStateOf(TrackingState.TRACKING) }
    var isSimulatedPlaneDetected by remember { mutableStateOf(false) }
    var simulatedModelPlaced by remember { mutableStateOf(false) }
    var modelPosition by remember { mutableStateOf(Position(0f, -0.2f, -1.2f)) }
    var lastRawPose by remember { mutableStateOf<Pose?>(null) }
    var isAnchorLocked by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    // Plane detection delay simulation
    LaunchedEffect(Unit) {
        delay(600L)
        isSimulatedPlaneDetected = true
    }

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            simulatedModelPlaced = false
            modelPosition = Position(0f, -0.2f, -1.2f)
            lastRawPose = null
            isAnchorLocked = false
        }
    }

    val pbrMaterial = remember(localMaterialLoader, color, metallic, roughness) {
        localMaterialLoader.createColorInstance(
            color = color,
            metallic = metallic.coerceIn(0.0f, 0.5f),
            roughness = roughness.coerceIn(0.2f, 0.85f)
        )
    }

    val visorGlassMaterial = remember(localMaterialLoader) {
        localMaterialLoader.createColorInstance(
            color = Color(0xFF141923),
            metallic = 0.85f,
            roughness = 0.15f
        )
    }

    val floorGridMaterial = remember(localMaterialLoader) {
        localMaterialLoader.createColorInstance(
            color = Color(0x3300E5FF),
            metallic = 0.1f,
            roughness = 0.9f
        )
    }

    val reticleMaterial = remember(localMaterialLoader) {
        localMaterialLoader.createColorInstance(
            color = Color(0x8800E5FF),
            metallic = 0.2f,
            roughness = 0.5f
        )
    }

    val toyCarModel = if (customModelFile == null && modelItem == ModelItem.TOY_CAR) {
        rememberModelInstance(
            modelLoader = localModelLoader,
            fileLocation = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb"
        )
    } else {
        null
    }

    val customModelInstance = key(customModelFile?.absolutePath) {
        customModelFile?.let { file ->
            rememberModelInstance(
                modelLoader = localModelLoader,
                fileLocation = "file://${file.absolutePath}"
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                activeAxis = activeRotationAxis,
                onSingleTap = { tapOffset ->
                    if (!isModelVisible) return@spatialMRGestures
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    // Convert screen tap to simulated floor plane placement with damping
                    val screenNormX = (tapOffset.x / 1080f - 0.5f) * 1.4f
                    val screenNormZ = -1.2f + (tapOffset.y / 1920f - 0.5f) * 0.8f

                    val rawPose = Pose(floatArrayOf(screenNormX, -0.2f, screenNormZ), floatArrayOf(0f, 0f, 0f, 1f))
                    val filteredPose = filterPoseWithLegacyDamping(
                        rawPose = rawPose,
                        lastPose = lastRawPose,
                        deadbandMeters = 0.0015f,
                        alpha = 0.35f
                    )
                    lastRawPose = filteredPose

                    modelPosition = Position(filteredPose.tx(), filteredPose.ty(), filteredPose.tz())
                    simulatedModelPlaced = true
                },
                onDoubleTap = {
                    onToggleMoveMode()
                },
                onTripleTap = {
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx, dy, isEdgeDrag ->
                    onStopAutoRotate()
                    applyRotationDelta(
                        axis = activeRotationAxis,
                        dx = dx,
                        dy = dy,
                        isEdgeDrag = isEdgeDrag,
                        currentRotX = rotationX,
                        currentRotY = rotationY,
                        currentRotZ = rotationZ,
                        onRotX = onRotationXChange,
                        onRotY = onRotationYChange,
                        onRotZ = onRotationZChange,
                        sensitivity = 0.45f
                    )
                },
                onOneFingerRotateZ = { dAngle ->
                    onStopAutoRotate()
                    val newRotZ = ((rotationZ + dAngle) % 360f + 360f) % 360f
                    onRotationZChange(newRotZ)
                },
                onTwoFingerZoom = { zoomMultiplier ->
                    val newScale = (scale * zoomMultiplier).coerceIn(0.1f, 2500.0f)
                    onScaleChange(newScale)
                },
                onMove = { panX, panY ->
                    modelPosition = Position(
                        x = modelPosition.x + (panX * 0.0025f),
                        y = modelPosition.y,
                        z = modelPosition.z + (panY * 0.0025f)
                    )
                }
            )
            .testTag("ar_tracking_simulation_view")
    ) {
        // Camera passthrough background
        CameraPassthroughBackground(
            modifier = Modifier.fillMaxSize(),
            isFlashlightOn = isFlashlightOn
        )

        // 3D SceneView with lights & placement plane
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = localModelLoader,
            materialLoader = localMaterialLoader,
            environmentLoader = localEnvironmentLoader,
            mainLightNode = rememberMainLightNode(engine) {
                intensity = 55_000.0f
            },
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 0.4f, 0.6f)
                lookAt(Position(0f, -0.2f, -1.2f))
            }
        ) {
            // Simulated horizontal floor grid
            if (isSimulatedPlaneDetected) {
                CubeNode(
                    size = Size(2.2f, 0.005f, 2.2f),
                    position = Position(0f, -0.22f, -1.2f),
                    materialInstance = floorGridMaterial
                )
            }

            // Placement reticle indicator on the floor
            if (!simulatedModelPlaced && isSimulatedPlaneDetected) {
                CylinderNode(
                    radius = 0.14f,
                    height = 0.006f,
                    position = Position(0f, -0.21f, -1.2f),
                    materialInstance = reticleMaterial
                )
            }

            // Placed 3D Model
            if (isModelVisible && (simulatedModelPlaced || isSimulatedPlaneDetected)) {
                val safeScale = if (scale.isFinite() && scale > 0f) scale.coerceIn(0.1f, 2500.0f) else 1.0f
                val safeRotX = if (rotationX.isFinite()) ((rotationX % 360f) + 360f) % 360f else 0f
                val safeRotY = if (rotationY.isFinite()) ((rotationY % 360f) + 360f) % 360f else 0f
                val safeRotZ = if (rotationZ.isFinite()) ((rotationZ % 360f) + 360f) % 360f else 0f

                Node(
                    position = modelPosition,
                    scale = Scale(safeScale)
                ) {
                    Node(rotation = Rotation(y = safeRotY)) {
                        Node(rotation = Rotation(x = safeRotX)) {
                            Node(rotation = Rotation(z = safeRotZ)) {
                                if (customModelFile != null) {
                                    customModelInstance?.let { instance ->
                                        ModelNode(
                                            modelInstance = instance,
                                            scaleToUnits = 0.35f,
                                            centerOrigin = Position(0f, 0f, 0f),
                                            autoAnimate = true
                                        )
                                    }
                                } else {
                                    when (modelItem) {
                                        ModelItem.VISION_HEADSET -> {
                                            SphereNode(
                                                radius = 0.16f,
                                                materialInstance = visorGlassMaterial,
                                                position = Position(0f, 0.04f, 0.08f)
                                            )
                                            CylinderNode(
                                                radius = 0.18f,
                                                height = 0.06f,
                                                materialInstance = pbrMaterial,
                                                position = Position(0f, 0.04f, -0.04f)
                                            )
                                        }
                                        ModelItem.TOY_CAR -> {
                                            toyCarModel?.let { instance ->
                                                ModelNode(
                                                    modelInstance = instance,
                                                    scaleToUnits = 0.40f,
                                                    centerOrigin = Position(0f, 0f, 0f)
                                                )
                                            } ?: CubeNode(
                                                size = Size(0.25f, 0.25f, 0.25f),
                                                materialInstance = pbrMaterial
                                            )
                                        }
                                        ModelItem.HOLO_CUBE -> {
                                            CubeNode(
                                                size = Size(0.24f, 0.24f, 0.24f),
                                                materialInstance = pbrMaterial
                                            )
                                        }
                                        ModelItem.QUANTUM_ORB -> {
                                            SphereNode(
                                                radius = 0.18f,
                                                materialInstance = pbrMaterial
                                            )
                                        }
                                        ModelItem.SPATIAL_CARD -> {
                                            CubeNode(
                                                size = Size(0.35f, 0.22f, 0.03f),
                                                materialInstance = pbrMaterial
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- OVERLAYS ---

        // Floating status pills, Move banners, Anchor Lock, and Reset Anchor intentionally hidden

        // 4. Diagnostics Dialog
        if (showDiagnosticsDialog) {
            ARTrackingDiagnosticsDialog(
                trackingManager = null,
                isRealARCoreAvailable = false,
                onDismiss = { showDiagnosticsDialog = false }
            )
        }
    }
}
