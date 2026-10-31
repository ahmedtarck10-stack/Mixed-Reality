package com.aistudio.mixedreality.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.createEnvironment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberViewNodeManager
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Stereoscopic Mixed Reality (MR) Screen.
 * - One tap: Anchor / place model onto the real floor.
 * - One finger: Smooth 360° rotation (yaw).
 * - Two fingers: Zoom (scale minify / magnify).
 * - Double tap: Toggle Move mode (drag model across space).
 * - Triple tap: Toggle Full Screen mode (hides/shows upper and lower UI pills).
 */
@Composable
fun MRStereoSplitView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    isAutoRotating: Boolean,
    rotationY: Float,
    isRecording: Boolean,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer,
    isARCoreAvailable: Boolean = true,
    clearTrigger: Int = 0,
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isARCoreAvailable) {
        MRStereoARCoreView(
            modelItem = modelItem,
            customModelFile = customModelFile,
            isModelVisible = isModelVisible,
            color = color,
            metallic = metallic,
            roughness = roughness,
            scale = scale,
            rotationY = rotationY,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            surfaceMirrorer = surfaceMirrorer,
            clearTrigger = clearTrigger,
            isMoveMode = isMoveMode,
            onToggleMoveMode = onToggleMoveMode,
            isFullScreen = isFullScreen,
            onToggleFullScreen = onToggleFullScreen,
            modifier = modifier
        )
    } else {
        MRStereoSimulationView(
            modelItem = modelItem,
            customModelFile = customModelFile,
            isModelVisible = isModelVisible,
            color = color,
            metallic = metallic,
            roughness = roughness,
            scale = scale,
            rotationY = rotationY,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            clearTrigger = clearTrigger,
            isMoveMode = isMoveMode,
            onToggleMoveMode = onToggleMoveMode,
            isFullScreen = isFullScreen,
            onToggleFullScreen = onToggleFullScreen,
            modifier = modifier
        )
    }
}

/**
 * Genuine ARCore stereoscopic experience using ARSceneView for Left Eye and SurfaceMirrorer for Right Eye.
 */
@Composable
private fun MRStereoARCoreView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    rotationY: Float,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer,
    clearTrigger: Int,
    isMoveMode: Boolean,
    onToggleMoveMode: () -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val windowManager = rememberViewNodeManager()

    var activeAnchor by remember { mutableStateOf<Anchor?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var isPlaneDetected by remember { mutableStateOf(false) }

    var leftEyeWidthPx by remember { mutableFloatStateOf(540f) }
    var leftEyeHeightPx by remember { mutableFloatStateOf(1080f) }

    var userScaleMultiplier by remember { mutableFloatStateOf(1.0f) }
    var userRotationOffset by remember { mutableFloatStateOf(0f) }
    var modelOffset by remember { mutableStateOf(Position(0f, 0f, 0f)) }

    val activeRightSurfaceRef = remember { AtomicReference<Surface?>(null) }

    fun stopRightMirrorSafely() {
        val surf = activeRightSurfaceRef.getAndSet(null)
        if (surf != null) {
            runCatching { surfaceMirrorer.stopMirroring(surf) }
            runCatching { surf.release() }
        }
    }

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            runCatching { activeAnchor?.detach() }
            activeAnchor = null
            userScaleMultiplier = 1.0f
            userRotationOffset = 0f
            modelOffset = Position(0f, 0f, 0f)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRightMirrorSafely()
            runCatching { activeAnchor?.detach() }
        }
    }

    val pbrMaterial = remember(materialLoader, color, metallic, roughness) {
        materialLoader.createColorInstance(
            color = color,
            metallic = metallic.coerceIn(0.0f, 0.5f),
            roughness = roughness.coerceIn(0.2f, 0.85f)
        )
    }

    val visorGlassMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF141923),
            metallic = 0.85f,
            roughness = 0.15f
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

    val effectiveRotation = (rotationY + userRotationOffset) % 360f
    val effectiveScale = scale * userScaleMultiplier

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                onSingleTap = { tapOffset ->
                    // 1 Tap: Anchor model at tap location
                    if (!isModelVisible) return@spatialMRGestures
                    val frame = latestFrame ?: return@spatialMRGestures
                    val hits = frame.hitTest(tapOffset.x, tapOffset.y)
                    val floorHit = hits.firstOrNull { hit ->
                        val t = hit.trackable
                        t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            t.isPoseInPolygon(hit.hitPose)
                    } ?: hits.firstOrNull { hit ->
                        val t = hit.trackable
                        t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                    }
                    if (floorHit != null) {
                        runCatching { activeAnchor?.detach() }
                        activeAnchor = floorHit.createAnchor()
                        modelOffset = Position(0f, 0f, 0f)
                    }
                },
                onDoubleTap = {
                    // Double Tap: Switch to Moving mode
                    onToggleMoveMode()
                },
                onTripleTap = {
                    // Triple Tap: Toggle Full Screen mode
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx ->
                    // 1 Finger: Yaw Rotation
                    userRotationOffset = (userRotationOffset + dx * 0.6f) % 360f
                },
                onTwoFingerZoom = { zoomFactor ->
                    // 2 Fingers: Zoom (Scale Minify / Magnify)
                    userScaleMultiplier = (userScaleMultiplier * zoomFactor).coerceIn(0.2f, 5.0f)
                },
                onMove = { panX, panY ->
                    // Moving Mode: Move model across floor / space
                    modelOffset = Position(
                        x = modelOffset.x + (panX * 0.0025f),
                        y = modelOffset.y,
                        z = modelOffset.z + (panY * 0.0025f)
                    )
                }
            )
    ) {
        // Dual Stereo Split Row: Left Eye | Divider | Right Eye
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. LEFT EYE: ARSceneView (Live ARCore Camera + Ground Lock Anchor)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            leftEyeWidthPx = size.width.toFloat()
                            leftEyeHeightPx = size.height.toFloat()
                        }
                    }
                    .testTag("mr_left_eye")
            ) {
                ARSceneView(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader,
                    surfaceMirrorer = surfaceMirrorer,
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
                    instantPlacementMode = Config.InstantPlacementMode.DISABLED,
                    planeRenderer = (activeAnchor == null),
                    onSessionUpdated = { session, frame ->
                        latestFrame = frame
                        val planes = session.getAllTrackables(Plane::class.java)
                        val hasTrackedPlane = planes.any {
                            it.trackingState == TrackingState.TRACKING &&
                                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                        }
                        if (hasTrackedPlane != isPlaneDetected) {
                            isPlaneDetected = hasTrackedPlane
                        }

                        // Auto-anchor on the first tracked floor surface if not placed yet
                        if (activeAnchor == null && isModelVisible && hasTrackedPlane) {
                            val hits = frame.hitTest(leftEyeWidthPx / 2f, leftEyeHeightPx / 2f)
                            val floorHit = hits.firstOrNull { hit ->
                                val t = hit.trackable
                                t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                    hit.trackable.trackingState == TrackingState.TRACKING
                            }
                            if (floorHit != null) {
                                activeAnchor = floorHit.createAnchor()
                            }
                        }
                    }
                ) {
                    activeAnchor?.let { anchor ->
                        AnchorNode(anchor = anchor) {
                            Node(
                                position = modelOffset,
                                rotation = Rotation(x = 0f, y = effectiveRotation, z = 0f),
                                scale = Scale(effectiveScale)
                            ) {
                                if (isModelVisible) {
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
                                        Render3DModel(
                                            modelItem = modelItem,
                                            customModelFile = null,
                                            customModelInstance = null,
                                            pbrMaterial = pbrMaterial,
                                            visorGlassMaterial = visorGlassMaterial,
                                            toyCarModel = toyCarModel,
                                            windowManager = windowManager,
                                            scale = 0.35f,
                                            rotationY = 0f,
                                            position = Position(0f, 0f, 0f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. CENTER STEREOSCOPIC DIVIDER
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // 3. RIGHT EYE: Hardware SurfaceMirrorer (Duplicated Live Camera + Model in real-time)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("mr_right_eye")
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            isOpaque = true
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                    stopRightMirrorSafely()
                                    val surf = Surface(st)
                                    activeRightSurfaceRef.set(surf)
                                    runCatching {
                                        surfaceMirrorer.startMirroring(
                                            surf,
                                            width.coerceAtLeast(1),
                                            height.coerceAtLeast(1)
                                        )
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                    val surf = activeRightSurfaceRef.get()
                                    if (surf != null) {
                                        runCatching { surfaceMirrorer.stopMirroring(surf) }
                                        runCatching {
                                            surfaceMirrorer.startMirroring(
                                                surf,
                                                width.coerceAtLeast(1),
                                                height.coerceAtLeast(1)
                                            )
                                        }
                                    }
                                }

                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    stopRightMirrorSafely()
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    onRelease = {
                        stopRightMirrorSafely()
                    }
                )
            }
        }

        // Move Mode Active Indicator
        if (isMoveMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 70.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✋", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "وضع التحريك مفعّل • انقر نقراً مزدوجاً للعودة للتدوير",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (activeAnchor == null && isModelVisible) {
            // Top Surface Guidance Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPlaneDetected) NeonGreen else NeonCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaneDetected) "تم رصد الأرضية • انقر لتثبيت النموذج" else "وجّه الكاميرا نحو الأرضية لرصد السطح",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Fallback spatial stereo simulation for emulators or devices without ARCore.
 */
@Composable
private fun MRStereoSimulationView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    rotationY: Float,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    clearTrigger: Int,
    isMoveMode: Boolean,
    onToggleMoveMode: () -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sharedScaleMultiplier by remember { mutableFloatStateOf(1.0f) }
    var sharedRotationOffset by remember { mutableFloatStateOf(0f) }
    var anchorGroundPosition by remember { mutableStateOf(Position(0f, -0.6f, -2.0f)) }

    val effectiveScale = scale * sharedScaleMultiplier
    val effectiveRotationY = (rotationY + sharedRotationOffset) % 360f

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            sharedScaleMultiplier = 1.0f
            sharedRotationOffset = 0f
            anchorGroundPosition = Position(0f, -0.6f, -2.0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                onSingleTap = { tapOffset ->
                    val normX = (tapOffset.x / 1080f).coerceIn(0f, 1f) * 2f - 1f
                    val normY = (tapOffset.y / 1920f).coerceIn(0f, 1f) * 2f - 1f
                    anchorGroundPosition = Position(normX * 0.8f, -0.6f, -1.6f - ((1f - normY) * 1.2f))
                },
                onDoubleTap = onToggleMoveMode,
                onTripleTap = onToggleFullScreen,
                onOneFingerRotate = { dx ->
                    sharedRotationOffset = (sharedRotationOffset + dx * 0.6f) % 360f
                },
                onTwoFingerZoom = { zoomFactor ->
                    sharedScaleMultiplier = (sharedScaleMultiplier * zoomFactor).coerceIn(0.2f, 5.0f)
                },
                onMove = { panX, panY ->
                    anchorGroundPosition = Position(
                        x = anchorGroundPosition.x + panX * 0.003f,
                        y = -0.6f,
                        z = anchorGroundPosition.z + panY * 0.003f
                    )
                }
            )
    ) {
        CameraPassthroughBackground(modifier = Modifier.fillMaxSize())

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                StereoEyeSceneFallback(
                    isLeftEye = true,
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = color,
                    metallic = metallic,
                    roughness = roughness,
                    scale = effectiveScale,
                    rotationY = effectiveRotationY,
                    groundPosition = anchorGroundPosition,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.25f))
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                StereoEyeSceneFallback(
                    isLeftEye = false,
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = color,
                    metallic = metallic,
                    roughness = roughness,
                    scale = effectiveScale,
                    rotationY = effectiveRotationY,
                    groundPosition = anchorGroundPosition,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader
                )
            }
        }

        if (isMoveMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 70.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✋", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "وضع التحريك مفعّل • انقر نقراً مزدوجاً للعودة للتدوير",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StereoEyeSceneFallback(
    isLeftEye: Boolean,
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    rotationY: Float,
    groundPosition: Position,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader
) {
    val windowManager = rememberViewNodeManager()

    val pbrMaterial = remember(materialLoader, color, metallic, roughness) {
        materialLoader.createColorInstance(
            color = color,
            metallic = metallic.coerceIn(0.0f, 0.5f),
            roughness = roughness.coerceIn(0.2f, 0.9f)
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
            metallic = 0.2f,
            roughness = 0.8f
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

    val eyeOffsetX = if (isLeftEye) -0.035f else 0.035f

    SceneView(
        modifier = Modifier.fillMaxSize(),
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        environment = rememberEnvironment(environmentLoader) {
            createEnvironment(environmentLoader)
        },
        viewNodeWindowManager = windowManager,
        cameraManipulator = rememberCameraManipulator(),
        cameraNode = rememberCameraNode(engine) {
            position = Position(eyeOffsetX, 0.2f, 6.0f)
        },
        mainLightNode = rememberMainLightNode(engine),
        fillLightNode = rememberFillLightNode(engine)
    ) {
        CubeNode(
            size = Size(6f, 0.005f, 6f),
            position = Position(0f, -0.65f, groundPosition.z),
            materialInstance = floorGridMaterial
        )

        if (isModelVisible) {
            Render3DModel(
                modelItem = modelItem,
                customModelFile = customModelFile,
                customModelInstance = customModelInstance,
                pbrMaterial = pbrMaterial,
                visorGlassMaterial = visorGlassMaterial,
                toyCarModel = toyCarModel,
                windowManager = windowManager,
                scale = 0.7f * scale,
                rotationY = rotationY,
                position = groundPosition
            )
        }
    }
}
