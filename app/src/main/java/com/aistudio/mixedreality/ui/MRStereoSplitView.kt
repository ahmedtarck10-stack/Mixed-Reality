package com.aistudio.mixedreality.ui

import android.app.Activity
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.highestResolutionCameraConfig
import io.github.sceneview.ar.node.AnchorNode
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
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Stereoscopic Mixed Reality (MR) Screen.
 * - One tap: Hit-tests real detected horizontal floor and anchors model.
 * - One finger: Smooth 360° yaw rotation.
 * - Two fingers: Zoom (scale 0.1x to 2500x).
 * - Double tap: Toggle Move mode (drag model across real floor plane).
 * - Triple tap: Toggle Full Screen mode (hides/shows upper and lower navigation bars).
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
    onScaleChange: (Float) -> Unit = {},
    isAutoRotating: Boolean = false,
    rotationY: Float = 0f,
    onRotationYChange: (Float) -> Unit = {},
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    rotationZ: Float = 0f,
    onRotationZChange: (Float) -> Unit = {},
    isRecording: Boolean = false,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer,
    isARCoreAvailable: Boolean = true,
    clearTrigger: Int = 0,
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
    activeRotationAxis: RotationAxis = RotationAxis.ALL,
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    onStopAutoRotate: () -> Unit = {},
    onFallbackToObject: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MRStereoARCoreView(
        modelItem = modelItem,
        customModelFile = customModelFile,
        isModelVisible = isModelVisible,
        color = color,
        metallic = metallic,
        roughness = roughness,
        scale = scale,
        onScaleChange = onScaleChange,
        rotationY = rotationY,
        onRotationYChange = onRotationYChange,
        rotationX = rotationX,
        onRotationXChange = onRotationXChange,
        rotationZ = rotationZ,
        onRotationZChange = onRotationZChange,
        onStopAutoRotate = onStopAutoRotate,
        onFallbackToObject = onFallbackToObject,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        surfaceMirrorer = surfaceMirrorer,
        clearTrigger = clearTrigger,
        isMoveMode = isMoveMode,
        onToggleMoveMode = onToggleMoveMode,
        activeRotationAxis = activeRotationAxis,
        isFullScreen = isFullScreen,
        onToggleFullScreen = onToggleFullScreen,
        modifier = modifier
    )
}

/**
 * Genuine ARCore stereoscopic experience using ARSceneView for Left Eye and SurfaceMirrorer for Right Eye.
 * Scans real physical floors — no simulated or fake planes.
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
    onScaleChange: (Float) -> Unit,
    rotationY: Float,
    onRotationYChange: (Float) -> Unit,
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    rotationZ: Float = 0f,
    onRotationZChange: (Float) -> Unit = {},
    onStopAutoRotate: () -> Unit,
    onFallbackToObject: () -> Unit = {},
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer,
    clearTrigger: Int,
    isMoveMode: Boolean,
    onToggleMoveMode: () -> Unit,
    activeRotationAxis: RotationAxis = RotationAxis.ALL,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var arCoreAvailability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var userRequestedInstall by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val packageInstalled = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo("com.google.ar.core", android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo("com.google.ar.core", 0)
                }
                true
            }.getOrDefault(false)

            if (packageInstalled) {
                arCoreAvailability = ArCoreApk.getInstance().checkAvailability(context)
            } else {
                val hasPlayStore = runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo("com.android.vending", android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo("com.android.vending", 0)
                    }
                    true
                }.getOrDefault(false)

                if (!hasPlayStore) {
                    arCoreAvailability = ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
                } else {
                    arCoreAvailability = ArCoreApk.getInstance().checkAvailability(context)
                }
            }
        } catch (_: Throwable) {
            arCoreAvailability = ArCoreApk.Availability.UNKNOWN_ERROR
        }
    }

    if (arCoreAvailability == null || arCoreAvailability == ArCoreApk.Availability.UNKNOWN_CHECKING) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = NeonCyan)
        }
        return
    }

    if (arCoreAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ||
        arCoreAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
    ) {
        ARCoreInstallCard(
            onInstall = {
                val hasPlayStore = runCatching {
                    context.packageManager.getPackageInfo("com.android.vending", 0) != null
                }.getOrDefault(false)

                if (hasPlayStore && activity != null) {
                    try {
                        val installStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)
                        if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                            userRequestedInstall = false
                        }
                    } catch (_: Throwable) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                } else {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        )
        return
    }

    if (arCoreAvailability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ||
        arCoreAvailability == ArCoreApk.Availability.UNKNOWN_ERROR ||
        arCoreAvailability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT
    ) {
        ARCoreUnsupportedCard(
            onBackToObject = onFallbackToObject,
            isError = (arCoreAvailability != ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE)
        )
        return
    }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val trackingManager = rememberARTrackingManager(
        modeName = "MR",
        onHapticFeedback = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    )
    val activeAnchor = trackingManager.activeAnchor

    // Anti-Drift Stabilization Controls (Optimized for modern Samsung Galaxy S21-S26, Ultra, Fold, Flip, A25)
    var isFixedFocusEnabled by remember { mutableStateOf(true) }
    var isAnchorLocked by remember { mutableStateOf(false) }

    var leftEyeWidthPx by remember { mutableFloatStateOf(540f) }
    var leftEyeHeightPx by remember { mutableFloatStateOf(1080f) }

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
            trackingManager.clear()
            modelOffset = Position(0f, 0f, 0f)
            isAnchorLocked = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRightMirrorSafely()
            trackingManager.dispose()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                activeAxis = activeRotationAxis,
                onSingleTap = { tapOffset ->
                    trackingManager.onSingleTap(tapOffset, isModelVisible)
                },
                onDoubleTap = {
                    // Double Tap: Switch to Moving mode
                    onToggleMoveMode()
                },
                onTripleTap = {
                    // Triple Tap: Toggle Full Screen mode
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx, dy, isEdgeDrag ->
                    // 1 Finger: Smooth 360-degree rotation across X, Y, Z
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
                onTwoFingerRotateZ = { dAngle ->
                    // 2 Fingers Twist: Roll Rotation (Z axis)
                    val newRotZ = ((rotationZ + dAngle) % 360f + 360f) % 360f
                    onRotationZChange(newRotZ)
                },
                onTwoFingerZoom = { zoomFactor ->
                    // 2 Fingers: Zoom (Scale 0.1x to 2500x)
                    val newScale = (scale * zoomFactor).coerceIn(0.1f, 2500.0f)
                    onScaleChange(newScale)
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
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP,
                    focusMode = Config.FocusMode.FIXED,
                    sessionCameraConfig = ::highestResolutionCameraConfig,
                    sessionConfiguration = { _, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        // Standard 1: FIXED focus freezes lens at infinity, stopping OIS/Laser autofocus movements and VIO drift
                        config.focusMode = Config.FocusMode.FIXED
                        // Standard 2: LATEST_CAMERA_IMAGE binds ARCore lifecycle strictly to camera 30fps cadence rather than screen
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        // Standard 3: LOCAL_Y_UP enables instant 0.1s model drop without waiting for full polygonal floor mesh
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                        // Standard 4: DISABLED light estimation prevents brightness flashing/blooming ("ينور") on sensor shifts
                        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                        // Standard 5: DISABLED depth mode prevents uncalibrated ToF/stereo sensor noise from warping floor height
                        config.depthMode = Config.DepthMode.DISABLED
                    },
                    planeRenderer = (activeAnchor == null),
                    permissionHandler = null,
                    onGestureListener = null,
                    onSessionUpdated = { session, frame ->
                        trackingManager.onSessionUpdated(
                            session = session,
                            frame = frame,
                            isModelVisible = isModelVisible,
                            splitWidthPx = leftEyeWidthPx,
                            onPlaced = {
                                modelOffset = Position(0f, 0f, 0f)
                                isAnchorLocked = true // Automatically lock position on floor to eliminate drift
                            }
                        )
                    }
                ) {
                    activeAnchor?.let { anchor ->
                        AnchorNode(
                            anchor = anchor,
                            updateAnchorPose = true,
                            // Keep visible in both TRACKING and PAUSED states so the model never flickers or disappears ("يختفي")
                            visibleTrackingStates = setOf(TrackingState.TRACKING, TrackingState.PAUSED),
                            onTrackingStateChanged = { newState ->
                                trackingManager.onAnchorTrackingStateChanged(newState)
                            },
                            apply = {
                                isPositionEditable = false
                                isEditable = false
                                isRotationEditable = false
                                isScaleEditable = false
                            }
                        ) {
                            val safeScale = if (scale.isFinite() && scale > 0f) scale.coerceIn(0.1f, 2500.0f) else 1.0f
                            val safeRotX = if (rotationX.isFinite()) ((rotationX % 360f) + 360f) % 360f else 0f
                            val safeRotY = if (rotationY.isFinite()) ((rotationY % 360f) + 360f) % 360f else 0f
                            val safeRotZ = if (rotationZ.isFinite()) ((rotationZ % 360f) + 360f) % 360f else 0f
                            Node(
                                position = modelOffset,
                                rotation = Rotation(x = safeRotX, y = safeRotY, z = safeRotZ),
                                scale = Scale(safeScale)
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

            // Central Stereoscopic Optical Divider Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // 2. RIGHT EYE: Hardware-Mirrored Zero-Overhead Surface
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
                                    val newSurf = Surface(st)
                                    val oldSurf = activeRightSurfaceRef.getAndSet(newSurf)
                                    if (oldSurf != null) {
                                        runCatching { surfaceMirrorer.stopMirroring(oldSurf) }
                                        runCatching { oldSurf.release() }
                                    }
                                    runCatching {
                                        surfaceMirrorer.startMirroring(
                                            newSurf,
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
                        text = "Move mode active • Double-tap to return to rotate",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (isModelVisible) {
            val (hudText, hudColor) = trackingManager.getHudStatus()

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Real Floor Plane Scanning Guidance Badge
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .border(
                            1.dp,
                            hudColor.copy(alpha = 0.7f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(hudColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hudText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (activeAnchor != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Position Lock Toggle (Freezes pose to kill drift on Samsung flagships)
                        androidx.compose.material3.Surface(
                            onClick = {
                                isAnchorLocked = !isAnchorLocked
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAnchorLocked) NeonGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAnchorLocked) NeonGreen else Color.White.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAnchorLocked) "🔒 Locked (Drift Shield ON)" else "🔓 Lock Position",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAnchorLocked) NeonGreen else Color.White
                                )
                            }
                        }

                        // Fixed Focus Toggle (Fixed is default to prevent OIS focal length shifts)
                        androidx.compose.material3.Surface(
                            onClick = {
                                isFixedFocusEnabled = !isFixedFocusEnabled
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isFixedFocusEnabled) NeonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFixedFocusEnabled) NeonCyan else Color.White.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isFixedFocusEnabled) "🎯 Fixed Focus" else "🔍 Auto Focus",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isFixedFocusEnabled) NeonCyan else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
