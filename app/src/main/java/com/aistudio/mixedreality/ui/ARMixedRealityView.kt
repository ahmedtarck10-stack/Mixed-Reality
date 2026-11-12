package com.aistudio.mixedreality.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.R
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
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
import java.io.File

/**
 * Checks whether Google Play Services for AR is supported and installed on this device.
 */
fun isARCoreSupportedAndInstalled(context: Context): Boolean {
    return try {
        val packageInstalled = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    "com.google.ar.core",
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("com.google.ar.core", 0)
            }
            true
        }.getOrDefault(false)

        if (!packageInstalled) {
            // When com.google.ar.core is not installed, ARCore is definitely not installed.
            // Bypasses checkAvailability to avoid Play Store service bind failures on emulators.
            return false
        }

        val availability = ArCoreApk.getInstance().checkAvailability(context)
        Log.d("ARCoreAvailability", "ARCore availability checked: $availability")
        availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
    } catch (e: Throwable) {
        Log.w(
            "ARCoreAvailability",
            "ARCore checkAvailability threw exception: ${e.message}",
            e
        )
        false
    }
}

internal fun isEmulatorDevice(): Boolean {
    val fingerprint = android.os.Build.FINGERPRINT.orEmpty()
    val model = android.os.Build.MODEL.orEmpty()
    val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
    val hardware = android.os.Build.HARDWARE.orEmpty()
    val product = android.os.Build.PRODUCT.orEmpty()
    val board = android.os.Build.BOARD.orEmpty().lowercase(java.util.Locale.US)

    return (fingerprint.startsWith("generic")
            || fingerprint.startsWith("unknown")
            || model.contains("google_sdk")
            || model.contains("Emulator")
            || model.contains("Android SDK built for x86")
            || manufacturer.contains("Genymotion")
            || hardware.contains("goldfish")
            || hardware.contains("ranchu")
            || product.contains("sdk_gphone")
            || product.contains("google_sdk")
            || product.contains("sdk")
            || product.contains("vbox86p")
            || product.contains("emulator")
            || board.contains("goldfish"))
}

@Composable
fun ARMixedRealityView(
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
    onStopAutoRotate: () -> Unit = {},
    onFallbackToObject: () -> Unit = {},
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val trackingManager = rememberARTrackingManager(
        modeName = "AR",
        onHapticFeedback = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )
    val activeAnchor = trackingManager.activeAnchor

    var isAnchorLocked by remember { mutableStateOf(false) }

    var modelOffset by remember {
        mutableStateOf(Position(0f, 0f, 0f))
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

    // Lazy conditional loading: only fetch 3D model if it is actively selected
    val toyCarModel = if (customModelFile == null && modelItem == ModelItem.TOY_CAR) {
        rememberModelInstance(
            modelLoader = modelLoader,
            fileLocation = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb"
        )
    } else {
        null
    }

    val customModelInstance = key(customModelFile?.absolutePath) {
        customModelFile?.let { file ->
            rememberModelInstance(
                modelLoader = modelLoader,
                fileLocation = "file://${file.absolutePath}"
            )
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity

    var isTestSimulationMode by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    if (isTestSimulationMode) {
        ARTrackingSimulationView(
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
            clearTrigger = clearTrigger,
            isMoveMode = isMoveMode,
            onToggleMoveMode = onToggleMoveMode,
            activeRotationAxis = activeRotationAxis,
            isFullScreen = isFullScreen,
            onToggleFullScreen = onToggleFullScreen,
            onStopAutoRotate = onStopAutoRotate,
            onFallbackToObject = onFallbackToObject,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            modifier = modifier
        )
        return
    }

    var arCoreAvailability by remember {
        mutableStateOf<ArCoreApk.Availability?>(null)
    }
    var userRequestedInstall by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(Unit) {
        try {
            val packageInstalled = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        "com.google.ar.core",
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo("com.google.ar.core", 0)
                }
                true
            }.getOrDefault(false)

            if (packageInstalled) {
                arCoreAvailability =
                    ArCoreApk.getInstance().checkAvailability(context)
            } else {
                val hasPlayStore = runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            "com.android.vending",
                            android.content.pm.PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo("com.android.vending", 0)
                    }
                    true
                }.getOrDefault(false)

                val canBindPlayStore = runCatching {
                    val intent = Intent("com.google.android.play.core.install.BIND_INSTALL_SERVICE")
                        .setPackage("com.android.vending")
                    val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.queryIntentServices(
                            intent,
                            android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.queryIntentServices(intent, 0)
                    }
                    resolveInfo.isNotEmpty()
                }.getOrDefault(false)

                arCoreAvailability = if (canBindPlayStore && !isEmulatorDevice()) {
                    ArCoreApk.getInstance().checkAvailability(context)
                } else if (hasPlayStore && !isEmulatorDevice()) {
                    ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
                } else {
                    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
                }
            }
        } catch (_: Throwable) {
            arCoreAvailability = ArCoreApk.Availability.UNKNOWN_ERROR
        }
    }

    if (
        arCoreAvailability == null ||
        arCoreAvailability == ArCoreApk.Availability.UNKNOWN_CHECKING
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NeonCyan)
        }
        return
    }

    if (
        arCoreAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ||
        arCoreAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
    ) {
        ARCoreInstallCard(
            onInstall = {
                val canBindPlayStore = runCatching {
                    val intent = Intent("com.google.android.play.core.install.BIND_INSTALL_SERVICE")
                        .setPackage("com.android.vending")
                    val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.queryIntentServices(
                            intent,
                            android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.queryIntentServices(intent, 0)
                    }
                    resolveInfo.isNotEmpty()
                }.getOrDefault(false)

                if (canBindPlayStore && !isEmulatorDevice() && activity != null) {
                    try {
                        val installStatus =
                            ArCoreApk.getInstance().requestInstall(
                                activity,
                                userRequestedInstall
                            )

                        if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                            userRequestedInstall = false
                        }
                    } catch (_: Throwable) {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://play.google.com/store/apps/details?id=com.google.ar.core"
                                )
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                } else {
                    runCatching {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://play.google.com/store/apps/details?id=com.google.ar.core"
                            )
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        )
        return
    }

    if (
        arCoreAvailability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ||
        arCoreAvailability == ArCoreApk.Availability.UNKNOWN_ERROR ||
        arCoreAvailability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT
    ) {
        ARCoreUnsupportedCard(
            onBackToObject = onFallbackToObject,
            onStartTestMode = { isTestSimulationMode = true },
            onOpenDiagnostics = { showDiagnosticsDialog = true },
            isError = (
                arCoreAvailability !=
                    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
            )
        )
        if (showDiagnosticsDialog) {
            ARTrackingDiagnosticsDialog(
                trackingManager = trackingManager,
                isRealARCoreAvailable = false,
                onDismiss = { showDiagnosticsDialog = false },
                onLaunchTestMode = { isTestSimulationMode = true }
            )
        }
        return
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
                    trackingManager.onSingleTap(
                        tapOffset,
                        isModelVisible
                    )
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
                onTwoFingerRotateZ = { dAngle ->
                    val newRotZ =
                        ((rotationZ + dAngle) % 360f + 360f) % 360f
                    onRotationZChange(newRotZ)
                },
                onTwoFingerZoom = { zoomMultiplier ->
                    val newScale =
                        (scale * zoomMultiplier).coerceIn(0.1f, 2500.0f)
                    onScaleChange(newScale)
                },
                onMove = { panX, panY ->
                    modelOffset = Position(
                        x = modelOffset.x + (panX * 0.0025f),
                        y = modelOffset.y,
                        z = modelOffset.z + (panY * 0.0025f)
                    )
                }
            )
    ) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ar_scene_view"),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP,
            // FocusMode.FIXED locks hyperfocal depth to prevent autofocus hunting
            // from inducing feature-point micro-drift and plane instability during continuous 6DoF tracking.
            focusMode = Config.FocusMode.FIXED,
            sessionConfiguration = { _, config ->
                config.planeFindingMode =
                    Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.FIXED
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.instantPlacementMode =
                    Config.InstantPlacementMode.LOCAL_Y_UP
                config.lightEstimationMode =
                    Config.LightEstimationMode.DISABLED
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
                    onPlaced = {
                        modelOffset = Position(0f, 0f, 0f)
                        isAnchorLocked = true
                    }
                )
            }
        ) {
            activeAnchor?.let { anchor ->
                AnchorNode(
                    anchor = anchor,
                    updateAnchorPose = !isAnchorLocked,
                    visibleTrackingStates = setOf(
                        TrackingState.TRACKING,
                        TrackingState.PAUSED
                    ),
                    onTrackingStateChanged = { newState ->
                        trackingManager.onAnchorTrackingStateChanged(newState)
                    }
                ) {
                    val safeScale =
                        if (scale.isFinite() && scale > 0f) {
                            scale.coerceIn(0.1f, 2500.0f)
                        } else {
                            1.0f
                        }

                    val safeRotX =
                        if (rotationX.isFinite()) {
                            ((rotationX % 360f) + 360f) % 360f
                        } else {
                            0f
                        }

                    val safeRotY =
                        if (rotationY.isFinite()) {
                            ((rotationY % 360f) + 360f) % 360f
                        } else {
                            0f
                        }

                    val safeRotZ =
                        if (rotationZ.isFinite()) {
                            ((rotationZ % 360f) + 360f) % 360f
                        } else {
                            0f
                        }

                    val effectivePosition = Position(
                        x = modelOffset.x + trackingManager.smoothedOffset.x,
                        y = modelOffset.y + trackingManager.smoothedOffset.y,
                        z = modelOffset.z + trackingManager.smoothedOffset.z
                    )

                    Node(
                        position = effectivePosition,
                        rotation = Rotation(
                            x = safeRotX,
                            y = safeRotY,
                            z = safeRotZ
                        ),
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

        // --- OVERLAYS ---

        // 1. Move Mode Active Banner
        if (isMoveMode) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NeonCyan,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✋", fontSize = 13.sp)
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
            // 2. Real-Time HUD Status Badge
            val (hudText, hudColor) = trackingManager.getHudStatus()

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            1.dp,
                            hudColor.copy(alpha = 0.7f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("ar_tracking_hud_badge")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
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
                        Surface(
                            onClick = {
                                isAnchorLocked = !isAnchorLocked
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isAnchorLocked) Color(0x99000000) else Color(0xCC00E5FF),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = if (isAnchorLocked) "🔒 Anchor Locked" else "🔓 Anchor Unlocked",
                                color = if (isAnchorLocked) NeonCyan else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                trackingManager.clear()
                                modelOffset = Position(0f, 0f, 0f)
                                isAnchorLocked = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x99000000),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "↺ Reset Anchor",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Diagnostics & Test Mode Button (Top-left floating chip)
        Surface(
            onClick = { showDiagnosticsDialog = true },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xCC141E2D),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = if (isFullScreen) 16.dp else 68.dp, start = 16.dp)
                .testTag("ar_test_diagnostics_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧪", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AR Test",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Diagnostics Dialog
        if (showDiagnosticsDialog) {
            ARTrackingDiagnosticsDialog(
                trackingManager = trackingManager,
                isRealARCoreAvailable = true,
                onDismiss = { showDiagnosticsDialog = false }
            )
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF141923)
            ),
            border = BorderStroke(
                1.dp,
                NeonCyan.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Permission",
                    tint = NeonCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.camera_permission_required),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("grant_camera_button")
                ) {
                    Text(
                        stringResource(R.string.grant_permission),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * ARCore installation request card when Google Play Services for AR is missing or outdated.
 */
@Composable
fun ARCoreInstallCard(onInstall: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF141923)
            ),
            border = BorderStroke(
                1.dp,
                NeonCyan.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🌐",
                    fontSize = 42.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Install Google Play Services for AR",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Real-world surface and plane tracking requires the official Google ARCore package to be installed or updated.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("install_arcore_button")
                ) {
                    Text(
                        "Install / Update ARCore",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Clean fallback card displayed when the device hardware or emulator does not support ARCore.
 * Allows running interactive AR Tracking Test Mode and comprehensive diagnostics.
 */
@Composable
fun ARCoreUnsupportedCard(
    onBackToObject: () -> Unit,
    onStartTestMode: (() -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    isError: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF141923)
            ),
            border = BorderStroke(
                1.dp,
                if (isError) Color(0xFFFF5252).copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isError) "⚠️" else "🥽",
                    fontSize = 42.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isError) {
                        "ARCore Service Unavailable"
                    } else {
                        "ARCore Not Supported / Emulator"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isError) {
                        "An error occurred while checking AR services. You can continue testing AR tracking in simulation mode."
                    } else {
                        "Physical plane tracking requires hardware ARCore. You can run the interactive AR Tracking Test Mode with simulated plane detection, damping & tap placement."
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (onStartTestMode != null) {
                    Button(
                        onClick = onStartTestMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_ar_test_mode_button")
                    ) {
                        Text(
                            "🧪 Start AR Tracking Test Mode (اختبار تتبع AR)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (onOpenDiagnostics != null) {
                    OutlinedButton(
                        onClick = onOpenDiagnostics,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan
                        ),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_ar_diagnostics_button")
                    ) {
                        Text(
                            "🔬 Run AR Tracking Self-Test (فحص شامل)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                TextButton(
                    onClick = onBackToObject,
                    modifier = Modifier.testTag("back_to_object_button")
                ) {
                    Text(
                        "Return to Object Mode",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
