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
            arCoreAvailability = ArCoreApk.getInstance().checkAvailability(context)
        } catch (_: Throwable) {
            arCoreAvailability = ArCoreApk.Availability.UNKNOWN_ERROR
        }
    }

    if (arCoreAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ||
        arCoreAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
    ) {
        ARCoreInstallCard(
            onInstall = {
                if (activity != null) {
                    try {
                        val installStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)
                        if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                            userRequestedInstall = false
                        }
                    } catch (_: Throwable) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.core")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.core")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
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
                onOneFingerRotate = { dx, _ ->
                    // 1 Finger: Yaw Rotation
                    onStopAutoRotate()
                    val newRot = (rotationY + dx * 0.45f) % 360f
                    onRotationYChange(if (newRot < 0f) newRot + 360f else newRot)
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
                    instantPlacementMode = Config.InstantPlacementMode.DISABLED,
                    sessionConfiguration = { _, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        config.focusMode = Config.FocusMode.AUTO
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    },
                    planeRenderer = (activeAnchor == null),
                    onGestureListener = null,
                    onSessionUpdated = { session, frame ->
                        trackingManager.onSessionUpdated(
                            session = session,
                            frame = frame,
                            isModelVisible = isModelVisible,
                            splitWidthPx = leftEyeWidthPx,
                            onPlaced = {
                                modelOffset = Position(0f, 0f, 0f)
                            }
                        )
                    }
                ) {
                    activeAnchor?.let { anchor ->
                        AnchorNode(
                            anchor = anchor,
                            updateAnchorPose = true,
                            visibleTrackingStates = setOf(TrackingState.TRACKING),
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
                            Node(
                                position = modelOffset,
                                rotation = Rotation(x = 0f, y = rotationY, z = 0f),
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

            // Real Floor Plane Scanning Guidance Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp)
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
        }
    }
}
