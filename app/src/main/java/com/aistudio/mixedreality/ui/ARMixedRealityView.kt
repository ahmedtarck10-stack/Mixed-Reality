package com.aistudio.mixedreality.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.ArCoreApk
import java.util.concurrent.atomic.AtomicReference
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
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
import java.io.File

/**
 * Checks whether Google Play Services for AR is supported and installed on this device.
 * Never returns true on exceptions, and safely distinguishes between installed,
 * uninstalled, unsupported, and transient checking states.
 */
fun isARCoreSupportedAndInstalled(context: Context): Boolean {
    return try {
        val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(context)
        Log.d("ARCoreAvailability", "ARCore availability checked: $availability")
        availability == com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
    } catch (e: Throwable) {
        Log.w("ARCoreAvailability", "ARCore checkAvailability threw exception: ${e.message}", e)
        false
    }
}

/**
 * Live ARCore scene scanning real physical floors.
 * - One tap: Hit-tests real detected horizontal floor and anchors model.
 * - One finger: Smooth 360° yaw rotation.
 * - Two fingers: Zoom (scale 0.1x to 2500x).
 * - Double tap: Toggle Move Mode (move along floor surface).
 * - Triple tap: Toggle Full Screen (hide/show navigation bars).
 */
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
    clearTrigger: Int = 0,
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
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
        onHapticFeedback = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    )
    val activeAnchor = trackingManager.activeAnchor

    var modelOffset by remember { mutableStateOf(Position(0f, 0f, 0f)) }

    // Clear anchor on clear trigger
    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            trackingManager.clear()
            modelOffset = Position(0f, 0f, 0f)
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
                    onToggleMoveMode()
                },
                onTripleTap = {
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx, _ ->
                    onStopAutoRotate()
                    val newRot = (rotationY + dx * 0.45f) % 360f
                    onRotationYChange(if (newRot < 0f) newRot + 360f else newRot)
                },
                onTwoFingerZoom = { zoomMultiplier ->
                    val newScale = (scale * zoomMultiplier).coerceIn(0.1f, 2500.0f)
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
        // Real ARCore View with Horizontal Floor Detection
        ARSceneView(
            modifier = Modifier.fillMaxSize().testTag("ar_scene_view"),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
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
        } else {
            // Real Floor Plane Scanning Guidance HUD
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isFullScreen) 16.dp else 68.dp)
                    .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(20.dp))
                    .border(
                        1.dp,
                        if (activeAnchor != null) NeonGreen.copy(alpha = 0.7f)
                        else if (trackingManager.isPlaneDetected) NeonGreen.copy(alpha = 0.7f)
                        else NeonCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (hudText, hudColor) = trackingManager.getHudStatus()

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(hudColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hudText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
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
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
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
                    Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
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
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
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
                    Text("Install / Update ARCore", fontWeight = FontWeight.Bold)
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
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
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
                    text = if (isError) "ARCore Service Unavailable" else "ARCore Not Supported on this Device",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isError)
                        "An error occurred while checking AR services. You can continue inspecting the 3D model in Object mode."
                    else
                        "This device does not support Google ARCore physical plane tracking. You can fully explore, rotate, and scale the model in Object Mode.",
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
                    Text("Return to Object Mode", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
