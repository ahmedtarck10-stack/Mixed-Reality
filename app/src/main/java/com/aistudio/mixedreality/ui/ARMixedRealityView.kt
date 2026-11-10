package com.aistudio.mixedreality.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    environmentLoader: EnvironmentLoader,
    modifier: Modifier = Modifier
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

    val context = LocalContext.current
    val activity = context as? Activity

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
                    val resolveInfo = context.packageManager.queryIntentServices(intent, 0)
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
            isError = (
                arCoreAvailability !=
                    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
            )
        )
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
                    updateAnchorPose = true,
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

                    Node(
                        position = modelOffset,
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
                    text = "Mixed Reality AR mode requires camera access to detect physical surfaces and render 3D models in real-time.",
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
                        "Grant Camera Permission",
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
 */
@Composable
fun ARCoreUnsupportedCard(
    onBackToObject: () -> Unit,
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
                .fillMaxWidth(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF141923)
            ),
            border = BorderStroke(
                1.dp,
                Color(0xFFFF5252).copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isError) "⚠️" else "📱",
                    fontSize = 42.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isError) {
                        "ARCore Service Unavailable"
                    } else {
                        "ARCore Not Supported on this Device"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isError) {
                        "An error occurred while checking AR services. You can continue inspecting the 3D model in Object mode."
                    } else {
                        "This device does not support Google ARCore physical plane tracking. You can fully explore, rotate, and scale the model in Object Mode."
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onBackToObject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("back_to_object_button")
                ) {
                    Text(
                        "Return to Object Mode",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
