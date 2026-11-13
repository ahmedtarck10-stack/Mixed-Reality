package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberSurfaceMirrorer
import io.github.sceneview.rememberViewNodeManager
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File

/**
 * Single-viewport 3D inspection studio (Object Mode).
 * Supports custom gestures:
 * - 1 Tap: Place model at tapped screen location
 * - 1 Finger Drag: Isolated 360-degree X, Y, and Z rotation (gimbal-free quaternion)
 * - 2 Fingers Drag/Pinch: Scale (0.1x to 2500x) or Move depending on mode
 * - Double Tap: Toggle between Move Mode and Scale Mode (no scaling)
 * - Triple Tap: Toggle Full Screen (hide/show upper and down bars)
 */
@Composable
fun Studio3DView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    metallic: Float,
    roughness: Float,
    scale: Float,
    onScaleChange: (Float) -> Unit = {},
    rotationY: Float,
    onRotationYChange: (Float) -> Unit = {},
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    rotationZ: Float = 0f,
    onRotationZChange: (Float) -> Unit = {},
    orientation: SpatialQuaternion? = null,
    onOrientationChange: ((SpatialQuaternion) -> Unit)? = null,
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
    activeRotationAxis: RotationAxis = RotationAxis.ALL,
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    onStopAutoRotate: () -> Unit = {},
    onOpenClick: () -> Unit,
    engine: Engine,
    modelLoader: ModelLoader? = null,
    materialLoader: MaterialLoader? = null,
    environmentLoader: EnvironmentLoader? = null,
    surfaceMirrorer: SurfaceMirrorer? = null,
    onSurfaceMirrorerChanged: (SurfaceMirrorer?) -> Unit = {}
) {
    val localModelLoader = modelLoader ?: rememberModelLoader(engine)
    val localMaterialLoader = materialLoader ?: rememberMaterialLoader(engine)
    val localEnvironmentLoader = environmentLoader ?: rememberEnvironmentLoader(engine)
    val localSurfaceMirrorer = rememberSurfaceMirrorer()

    DisposableEffect(localSurfaceMirrorer) {
        onSurfaceMirrorerChanged(localSurfaceMirrorer)
        onDispose {
            onSurfaceMirrorerChanged(null)
        }
    }

    // Allocate offscreen ViewNode WindowManager only when Spatial Interface card is active
    val needsViewNodeManager = customModelFile == null && modelItem == ModelItem.SPATIAL_CARD
    val windowManager = if (needsViewNodeManager) rememberViewNodeManager() else null
    val scope = rememberCoroutineScope()
    var modelPosition by remember { mutableStateOf(Position(0f, 0f, 0f)) }

    val pbrMaterial = remember(localMaterialLoader, color, metallic, roughness) {
        localMaterialLoader.createColorInstance(
            color = color,
            metallic = metallic.coerceIn(0.0f, 0.5f),
            roughness = roughness.coerceIn(0.2f, 0.85f)
        )
    }

    val visorGlassMaterial = remember(localMaterialLoader) {
        localMaterialLoader.createColorInstance(
            color = Color(0xFF161A22),
            metallic = 0.35f,
            roughness = 0.45f
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
    val customModelInstance = rememberOptimizedModelInstance(
        modelLoader = localModelLoader,
        customModelFile = customModelFile
    )

    val environment = rememberEnvironment(localEnvironmentLoader) {
        createEnvironment(localEnvironmentLoader)
    }

    val currentOrientation by rememberUpdatedState(orientation)
    val renderQuality = remember(customModelFile?.absolutePath) {
        if (customModelFile != null && customModelFile.length() > 4L * 1024L * 1024L) {
            RenderQuality.Performance
        } else {
            RenderQuality.Default
        }
    }

    var viewWidthPx by remember { mutableFloatStateOf(1080f) }
    var viewHeightPx by remember { mutableFloatStateOf(1920f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0) viewWidthPx = size.width.toFloat()
                if (size.height > 0) viewHeightPx = size.height.toFloat()
            }
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                activeAxis = activeRotationAxis,
                onSingleTap = { tapOffset ->
                    if (!isModelVisible) return@spatialMRGestures
                    onStopAutoRotate()
                    if (viewWidthPx > 0f && viewHeightPx > 0f) {
                        val normX = (tapOffset.x / viewWidthPx) * 2f - 1f
                        val normY = -((tapOffset.y / viewHeightPx) * 2f - 1f)
                        val halfWidthAtZ = 1.35f
                        val halfHeightAtZ = halfWidthAtZ * (viewHeightPx / viewWidthPx)
                        modelPosition = Position(
                            x = normX * halfWidthAtZ,
                            y = 0.3f + (normY * halfHeightAtZ),
                            z = 0f
                        )
                    }
                },
                onDoubleTap = {
                    onToggleMoveMode()
                },
                onTripleTap = {
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx, dy, isEdgeDrag ->
                    onStopAutoRotate()
                    val baseQuat = currentOrientation
                    if (activeRotationAxis == RotationAxis.ALL && onOrientationChange != null && baseQuat != null) {
                        var updated = baseQuat
                        if (dx != 0f) updated = updated.rotateWorldY(dx * 0.45f)
                        if (dy != 0f) updated = updated.rotateWorldX(-dy * 0.45f)
                        onOrientationChange(updated)
                    }
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
                    val baseQuat = currentOrientation
                    if (onOrientationChange != null && baseQuat != null) {
                        onOrientationChange(baseQuat.rotateWorldZ(-dAngle))
                    }
                    val newRotZ = ((rotationZ + dAngle) % 360f + 360f) % 360f
                    onRotationZChange(newRotZ)
                },
                onTwoFingerZoom = { zoomMultiplier ->
                    val newScale = (scale * zoomMultiplier).coerceIn(0.1f, 2500.0f)
                    onScaleChange(newScale)
                },
                onMove = { panX, panY ->
                    if (viewWidthPx > 0f && viewHeightPx > 0f) {
                        val halfWidthAtZ = 1.35f
                        val halfHeightAtZ = halfWidthAtZ * (viewHeightPx / viewWidthPx)
                        val deltaX = (panX / viewWidthPx) * (2f * halfWidthAtZ)
                        val deltaY = -(panY / viewHeightPx) * (2f * halfHeightAtZ)
                        modelPosition = Position(
                            x = modelPosition.x + deltaX,
                            y = modelPosition.y + deltaY,
                            z = modelPosition.z
                        )
                    }
                }
            )
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = localModelLoader,
            materialLoader = localMaterialLoader,
            environmentLoader = localEnvironmentLoader,
            environment = environment,
            renderQuality = renderQuality,
            viewNodeWindowManager = windowManager,
            cameraManipulator = null,
            autoCenterContent = false,
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 0.3f, 3.4f)
            },
            mainLightNode = rememberMainLightNode(engine),
            fillLightNode = rememberFillLightNode(engine),
            surfaceMirrorer = localSurfaceMirrorer
        ) {
            if (isModelVisible) {
                Render3DModel(
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    customModelInstance = customModelInstance,
                    pbrMaterial = pbrMaterial,
                    visorGlassMaterial = visorGlassMaterial,
                    toyCarModel = toyCarModel,
                    windowManager = windowManager,
                    scale = scale,
                    rotationY = rotationY,
                    rotationX = rotationX,
                    rotationZ = rotationZ,
                    position = modelPosition,
                    orientation = orientation,
                    disableShadows = (renderQuality == RenderQuality.Performance)
                )
            }
        }

        // Loading overlay while custom 3D model is being parsed
        if (customModelFile != null && customModelInstance == null && isModelVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading 3D model and textures...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Empty state when user clicked Clear
        if (!isModelVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scene is Empty",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Model cleared • Tap Open to load a 3D model",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                    text = "Open Models",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
