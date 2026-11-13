package com.aistudio.mixedreality.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.aistudio.mixedreality.util.FlashlightHelper
import com.aistudio.mixedreality.util.MRNotificationManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.mixedreality.ui.theme.*
import com.aistudio.mixedreality.util.MediaManager
import com.aistudio.mixedreality.util.ModelFormatConverter
import com.aistudio.mixedreality.util.VideoSessionRecorder
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberSurfaceMirrorer
import io.github.sceneview.utils.SurfaceMirrorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MRMainScreen(
    incomingModelUri: Uri? = null,
    onModelHandled: () -> Unit = {},
    shortcutAction: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Shared single Filament Engine & loaders across entire screen lifecycle
    // Prevents multi-engine EGL context crashes, redundant model reloads, and GPU memory exhaustion
    val engine = rememberEngine()
    val sharedModelLoader = rememberModelLoader(engine)
    val sharedMaterialLoader = rememberMaterialLoader(engine)
    val sharedEnvironmentLoader = rememberEnvironmentLoader(engine)

    // ARCore device support detection
    val isARCoreAvailable = remember(context) {
        isARCoreSupportedAndInstalled(context)
    }

    // Viewport Mode (MR, AR, Object)
    var selectedMode by remember { mutableStateOf(ViewportMode.OBJECT) }

    // Model selection & visibility
    var selectedModel by remember { mutableStateOf(ModelItem.VISION_HEADSET) }
    var customModelFile by remember { mutableStateOf<File?>(null) }
    var isModelVisible by remember { mutableStateOf(true) }
    var isPreparingModelFile by remember { mutableStateOf(false) }
    var preparingStatusMessage by remember { mutableStateOf("Streaming & optimizing 3D file...") }
    var modelStatsSummary by remember { mutableStateOf<String?>(null) }

    // Visual attributes: realistic default PBR values for natural color representation
    var modelColor by remember { mutableStateOf(Color(0xFF00E5FF)) }
    var metallic by remember { mutableFloatStateOf(0.25f) }
    var roughness by remember { mutableFloatStateOf(0.45f) }
    // Keep Studio Object scale and AR/MR world scale independent so zooming in Object mode
    // never blows up a mesh around the physical AR camera on mode switch.
    var objectScaleFactor by remember { mutableFloatStateOf(1.0f) }
    var arScaleFactor by remember { mutableFloatStateOf(1.0f) }
    var isAutoRotating by remember { mutableStateOf(false) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    var rotationX by remember { mutableFloatStateOf(0f) }
    var rotationZ by remember { mutableFloatStateOf(0f) }
    var modelQuaternion by remember { mutableStateOf(SpatialQuaternion.IDENTITY) }
    var activeRotationAxis by remember { mutableStateOf(RotationAxis.ALL) }

    // Cloud 3D Library Sheet (Google Drive Models - Long press on Open)
    var showCloudLibrary by remember { mutableStateOf(false) }

    // Notification permission & Android system shade sync
    var hasNotificationPermission by remember {
        mutableStateOf(MRNotificationManager.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Flashlight starts OFF and follows the user's external Control Center / Quick Settings toggle.
    // The app never turns the torch on or off automatically on launch, pause, or dispose.
    var isExternalTorchOn by remember { mutableStateOf(false) }
    val ignoreTorchCallbacksUntilMs = remember { mutableLongStateOf(0L) }
    val currentModeState by rememberUpdatedState(selectedMode)
    var previousMode by remember { mutableStateOf(ViewportMode.OBJECT) }

    DisposableEffect(context) {
        val unregisterTorchObserver = FlashlightHelper.observeTorchState(context) { enabled ->
            if (currentModeState == ViewportMode.OBJECT &&
                SystemClock.elapsedRealtime() >= ignoreTorchCallbacksUntilMs.longValue
            ) {
                isExternalTorchOn = enabled
            }
        }
        onDispose {
            unregisterTorchObserver()
        }
    }

    LaunchedEffect(selectedMode) {
        val fromMode = previousMode
        previousMode = selectedMode
        if (selectedMode == ViewportMode.OBJECT && fromMode != ViewportMode.OBJECT && isExternalTorchOn) {
            // ARCore turns hardware LED off while tearing down ARSession; re-engage only if user had it ON externally.
            ignoreTorchCallbacksUntilMs.longValue = SystemClock.elapsedRealtime() + 1400L
            delay(180L)
            FlashlightHelper.setTorchModeWithRetry(context, true)
        }
    }

    // Bottom sheet for model tuning / presets
    var showTuneDrawer by remember { mutableStateOf(false) }

    // Shutter flash animation
    var showFlash by remember { mutableStateOf(false) }

    // Video Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val videoRecorder = remember(context) { VideoSessionRecorder(context) }
    val fallbackSurfaceMirrorer = rememberSurfaceMirrorer()
    var activeSurfaceMirrorer by remember { mutableStateOf<SurfaceMirrorer?>(null) }

    // Clear trigger counter for AR anchors
    var clearTrigger by remember { mutableIntStateOf(0) }

    // Full Screen Mode (Triple-Tap toggles upper part & lower part)
    var isFullScreen by remember { mutableStateOf(false) }

    // Move Mode (Double-Tap switches between 2-finger Move and 2-finger Scale)
    var isMoveMode by remember { mutableStateOf(false) }

    // Camera permission launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted && isARCoreAvailable) {
            Toast.makeText(context, "Camera permission needed for AR/MR mode", Toast.LENGTH_SHORT).show()
            selectedMode = ViewportMode.OBJECT
        }
    }

    fun applyImportedModelOutcome(outcome: ModelImportOutcome, resetModeToObject: Boolean) {
        val copiedFile = outcome.file
        if (copiedFile != null && copiedFile.exists() && copiedFile.length() > 0L) {
            customModelFile = copiedFile
            isModelVisible = true
            if (resetModeToObject) {
                selectedMode = ViewportMode.OBJECT
            }
            modelQuaternion = SpatialQuaternion.IDENTITY
            rotationX = 0f
            rotationY = 0f
            rotationZ = 0f
            modelStatsSummary = outcome.statsLabel
            Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
            MRNotificationManager.showEventNotification(
                context = context,
                title = "3D Model Ready • ${copiedFile.name}",
                message = outcome.message,
                isError = false
            )
        } else {
            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            MRNotificationManager.showEventNotification(
                context = context,
                title = "3D Model Failed to Load",
                message = outcome.message,
                isError = true
            )
        }
    }

    // Handle incoming 3D model files from external apps / file manager on background thread
    LaunchedEffect(incomingModelUri) {
        val uri = incomingModelUri ?: return@LaunchedEffect
        isPreparingModelFile = true
        preparingStatusMessage = "Streaming & optimizing 3D file..."
        val outcome = withContext(Dispatchers.IO) { copyUriAndConvertModel(context, uri) }
        isPreparingModelFile = false
        applyImportedModelOutcome(outcome, resetModeToObject = true)
        onModelHandled()
    }

    // System Document / File Picker for 3D models (.glb, .gltf, .usdz, .obj, .3mf, .stl, .ply)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isPreparingModelFile = true
                preparingStatusMessage = "Streaming & optimizing 3D file..."
                val outcome = withContext(Dispatchers.IO) { copyUriAndConvertModel(context, uri) }
                isPreparingModelFile = false
                applyImportedModelOutcome(outcome, resetModeToObject = false)
            }
        }
    }

    // Handle App Shortcuts, Quick Settings tile, and System Notification quick actions
    LaunchedEffect(shortcutAction) {
        val action = shortcutAction ?: return@LaunchedEffect
        when (action) {
            MRNotificationManager.ACTION_CLOUD_LIBRARY -> {
                showCloudLibrary = true
            }
            MRNotificationManager.ACTION_LAUNCH_AR -> {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                selectedMode = ViewportMode.AR
            }
            MRNotificationManager.ACTION_OPEN_PICKER -> {
                filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*", "model/vnd.usdz+zip", "model/obj"))
            }
        }
        onShortcutHandled()
    }

    // Keep persistent notification in Android's top pull-down shade / control center synced
    LaunchedEffect(
        hasNotificationPermission,
        selectedMode,
        customModelFile?.absolutePath,
        selectedModel,
        isModelVisible,
        modelStatsSummary,
        isRecording
    ) {
        if (hasNotificationPermission) {
            val activeModelName = when {
                !isModelVisible -> "Empty Scene"
                customModelFile != null -> customModelFile!!.name
                else -> selectedModel.title
            }
            MRNotificationManager.showSessionNotification(
                context = context,
                modeLabel = selectedMode.title,
                modelLabel = activeModelName,
                statsSummary = if (isModelVisible) modelStatsSummary else null,
                isRecording = isRecording
            )
        }
    }

    // Auto-orbit animation loop (smooth 30fps rotation loop to eliminate lighting thrashing and flicker)
    LaunchedEffect(isAutoRotating) {
        if (isAutoRotating) {
            while (isAutoRotating) {
                delay(33L)
                rotationY = (rotationY + 0.5f) % 360f
                modelQuaternion = modelQuaternion.rotateWorldY(0.5f)
            }
        }
    }

    // Recording seconds timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    // Photo flash timer
    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(120L)
            showFlash = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 1. MAIN VIEWPORT (Object / AR / MR Stereo Split) ---
        when (selectedMode) {
            ViewportMode.OBJECT -> {
                Studio3DView(
                    modelItem = selectedModel,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = modelColor,
                    metallic = metallic,
                    roughness = roughness,
                    scale = objectScaleFactor,
                    onScaleChange = { objectScaleFactor = it },
                    rotationY = rotationY,
                    onRotationYChange = { rotationY = it },
                    rotationX = rotationX,
                    onRotationXChange = { rotationX = it },
                    rotationZ = rotationZ,
                    onRotationZChange = { rotationZ = it },
                    orientation = modelQuaternion,
                    onOrientationChange = { modelQuaternion = it },
                    isMoveMode = isMoveMode,
                    onToggleMoveMode = { isMoveMode = !isMoveMode },
                    activeRotationAxis = activeRotationAxis,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { isFullScreen = !isFullScreen },
                    onStopAutoRotate = { isAutoRotating = false },
                    onOpenClick = {
                        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*"))
                    },
                    engine = engine,
                    modelLoader = sharedModelLoader,
                    materialLoader = sharedMaterialLoader,
                    environmentLoader = sharedEnvironmentLoader,
                    onSurfaceMirrorerChanged = { activeSurfaceMirrorer = it },
                    onModelError = { reason ->
                        MRNotificationManager.showEventNotification(
                            context = context,
                            title = "3D Render Issue",
                            message = reason,
                            isError = true
                        )
                    }
                )
            }
            ViewportMode.AR -> {
                if (hasCameraPermission) {
                    ARMixedRealityView(
                        modelItem = selectedModel,
                        customModelFile = customModelFile,
                        isModelVisible = isModelVisible,
                        color = modelColor,
                        metallic = metallic,
                        roughness = roughness,
                        scale = arScaleFactor,
                        onScaleChange = { arScaleFactor = it },
                        rotationY = rotationY,
                        onRotationYChange = { rotationY = it },
                        rotationX = rotationX,
                        onRotationXChange = { rotationX = it },
                        rotationZ = rotationZ,
                        onRotationZChange = { rotationZ = it },
                        orientation = modelQuaternion,
                        onOrientationChange = { modelQuaternion = it },
                        clearTrigger = clearTrigger,
                        isMoveMode = isMoveMode,
                        onToggleMoveMode = { isMoveMode = !isMoveMode },
                        activeRotationAxis = activeRotationAxis,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { isFullScreen = !isFullScreen },
                        isFlashlightOn = isExternalTorchOn,
                        onStopAutoRotate = { isAutoRotating = false },
                        onFallbackToObject = { selectedMode = ViewportMode.OBJECT },
                        engine = engine,
                        modelLoader = sharedModelLoader,
                        materialLoader = sharedMaterialLoader,
                        environmentLoader = sharedEnvironmentLoader,
                        onModelError = { reason ->
                            MRNotificationManager.showEventNotification(
                                context = context,
                                title = "AR Model Render Issue",
                                message = reason,
                                isError = true
                            )
                        }
                    )
                } else {
                    PermissionRequestPlaceholder(onGrant = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }
            }
            ViewportMode.MR -> {
                if (hasCameraPermission) {
                    // Dual stereoscopic viewports with synchronized gestures and live camera passthrough
                    MRStereoSplitView(
                        modelItem = selectedModel,
                        customModelFile = customModelFile,
                        isModelVisible = isModelVisible,
                        color = modelColor,
                        metallic = metallic,
                        roughness = roughness,
                        scale = arScaleFactor,
                        onScaleChange = { arScaleFactor = it },
                        isAutoRotating = isAutoRotating,
                        rotationY = rotationY,
                        onRotationYChange = { rotationY = it },
                        rotationX = rotationX,
                        onRotationXChange = { rotationX = it },
                        rotationZ = rotationZ,
                        onRotationZChange = { rotationZ = it },
                        orientation = modelQuaternion,
                        onOrientationChange = { modelQuaternion = it },
                        isRecording = isRecording,
                        engine = engine,
                        modelLoader = sharedModelLoader,
                        materialLoader = sharedMaterialLoader,
                        environmentLoader = sharedEnvironmentLoader,
                        onSurfaceMirrorerChanged = { activeSurfaceMirrorer = it },
                        isARCoreAvailable = isARCoreAvailable,
                        clearTrigger = clearTrigger,
                        isMoveMode = isMoveMode,
                        onToggleMoveMode = { isMoveMode = !isMoveMode },
                        activeRotationAxis = activeRotationAxis,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { isFullScreen = !isFullScreen },
                        isFlashlightOn = isExternalTorchOn,
                        onStopAutoRotate = { isAutoRotating = false },
                        onFallbackToObject = { selectedMode = ViewportMode.OBJECT },
                        onModelError = { reason ->
                            MRNotificationManager.showEventNotification(
                                context = context,
                                title = "MR Model Render Issue",
                                message = reason,
                                isError = true
                            )
                        }
                    )
                } else {
                    PermissionRequestPlaceholder(onGrant = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }
            }
        }

        // --- 2. TOP FLOATING SEGMENTED PILL (EXACT CENTER) ---
        // Hidden in Full Screen mode (Triple-Tap toggles)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isFullScreen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                TopModeSegmentedPill(
                    currentMode = selectedMode,
                    onModeSelected = { mode ->
                        if (mode != ViewportMode.OBJECT && selectedMode == ViewportMode.OBJECT) {
                            ignoreTorchCallbacksUntilMs.longValue = SystemClock.elapsedRealtime() + 1500L
                            if (FlashlightHelper.isTorchOn) {
                                // Hand off hardware camera torch cleanly to ARCore's TORCH config
                                FlashlightHelper.setTorchMode(context, false)
                            }
                        }
                        if (mode == ViewportMode.AR || mode == ViewportMode.MR) {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        selectedMode = mode
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // --- 3. ACTIVE RECORDING BADGE (Top-right indicator) ---
        if (isRecording) {
            RecordingStatusBadge(
                seconds = recordingSeconds,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 22.dp, end = 20.dp)
            )
        }



        // Rotation bar hidden; 1-finger drag handles full 360° X, Y, and Z rotation directly

        // --- 5. BOTTOM FLOATING CONTROL PILL (PHOTO | ● REC | Open | Clear) ---
        // Hidden in Full Screen mode (Triple-Tap toggles)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isFullScreen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            BottomControlBar(
                isRecording = isRecording,
                onPhotoClick = {
                    // Requirement 1: Capture snapshot and save to Gallery
                    showFlash = true
                    if (activity != null) {
                        MediaManager.captureWindowBitmap(activity) { bitmap ->
                            scope.launch {
                                val uri = MediaManager.saveBitmapToGallery(context, bitmap)
                                if (uri != null) {
                                    Toast.makeText(context, "Photo saved to Gallery 📸", Toast.LENGTH_SHORT).show()
                                    MRNotificationManager.showEventNotification(
                                        context = context,
                                        title = "Snapshot Saved",
                                        message = "3D / AR screenshot saved to your Gallery."
                                    )
                                } else {
                                    Toast.makeText(context, "Photo captured", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Photo captured", Toast.LENGTH_SHORT).show()
                    }
                },
                onRecClick = {
                    // Requirement 2: Start / Stop video recording and save to Gallery
                    if (!isRecording) {
                        isRecording = true
                        videoRecorder.startRecording(
                            activity,
                            activeSurfaceMirrorer ?: fallbackSurfaceMirrorer,
                            scope
                        )
                        Toast.makeText(context, "Video recording started 🎥", Toast.LENGTH_SHORT).show()
                    } else {
                        isRecording = false
                        scope.launch {
                            Toast.makeText(context, "Saving video to Gallery...", Toast.LENGTH_SHORT).show()
                            val uri = videoRecorder.stopRecording()
                            if (uri != null) {
                                Toast.makeText(context, "Video saved to Gallery 🎥", Toast.LENGTH_LONG).show()
                                MRNotificationManager.showEventNotification(
                                    context = context,
                                    title = "Video Recording Saved",
                                    message = "Mixed Reality video clip saved to your Gallery."
                                )
                            } else {
                                Toast.makeText(context, "Recording finished", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onOpenClick = {
                    // Requirement 4: Clicking Open goes to Files to pick a 3D model (.glb / .gltf / .usdz / .obj)
                    filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*", "model/vnd.usdz+zip", "model/obj"))
                },
                onOpenLongClick = {
                    showCloudLibrary = true
                },
                onOpenCloudLibrary = {
                    showCloudLibrary = true
                },
                onClearClick = {
                    // Remove model & prune old cached model folders to keep RAM & storage clean
                    isModelVisible = false
                    customModelFile = null
                    modelStatsSummary = null
                    modelQuaternion = SpatialQuaternion.IDENTITY
                    rotationX = 0f
                    rotationY = 0f
                    rotationZ = 0f
                    clearTrigger++
                    scope.launch(Dispatchers.IO) {
                        pruneStaleModelCache(context.cacheDir)
                    }
                    Toast.makeText(context, "Model removed from scene 🗑️", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- 6. CAMERA SHUTTER FLASH OVERLAY ---
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // --- 6B. LARGE FILE STREAMING / CONVERSION INDICATOR ---
        if (isPreparingModelFile) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
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
                        text = preparingStatusMessage,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 7. MODEL CUSTOMIZATION & PRESETS SHEET (TUNE / PRESETS) ---
        if (showTuneDrawer) {
            ModalBottomSheet(
                onDismissRequest = { showTuneDrawer = false },
                containerColor = CardNavy,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                ModelPickerDrawerContent(
                    customModelFile = customModelFile,
                    onPickFileClick = {
                        showTuneDrawer = false
                        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*", "model/vnd.usdz+zip", "model/obj"))
                    },
                    selectedModel = selectedModel,
                    onSelectModel = {
                        selectedModel = it
                        customModelFile = null
                        isModelVisible = true
                    },
                    selectedColor = modelColor,
                    onSelectColor = { modelColor = it },
                    metallic = metallic,
                    onMetallicChange = { metallic = it },
                    roughness = roughness,
                    onRoughnessChange = { roughness = it },
                    scale = if (selectedMode == ViewportMode.OBJECT) objectScaleFactor else arScaleFactor,
                    onScaleChange = { newScale ->
                        if (selectedMode == ViewportMode.OBJECT) {
                            objectScaleFactor = newScale
                        } else {
                            arScaleFactor = newScale
                        }
                    },
                    rotationX = rotationX,
                    onRotationXChange = { newX ->
                        rotationX = newX
                        modelQuaternion = SpatialQuaternion.fromEulerZYX(newX, rotationY, rotationZ)
                    },
                    rotationY = rotationY,
                    onRotationYChange = { newY ->
                        rotationY = newY
                        modelQuaternion = SpatialQuaternion.fromEulerZYX(rotationX, newY, rotationZ)
                    },
                    rotationZ = rotationZ,
                    onRotationZChange = { newZ ->
                        rotationZ = newZ
                        modelQuaternion = SpatialQuaternion.fromEulerZYX(rotationX, rotationY, newZ)
                    },
                    isAutoRotating = isAutoRotating,
                    onToggleAutoRotate = { isAutoRotating = !isAutoRotating },
                    onClose = { showTuneDrawer = false }
                )
            }
        }

        // --- 9. CLOUD 3D MODEL LIBRARY SHEET ---
        if (showCloudLibrary) {
            CloudLibrarySheet(
                onDismiss = { showCloudLibrary = false },
                onModelDownloaded = { downloadedFile, title ->
                    showCloudLibrary = false
                    scope.launch {
                        isPreparingModelFile = true
                        preparingStatusMessage = "Processing $title..."
                        val converted = withContext(Dispatchers.IO) {
                            pruneStaleModelCache(context.cacheDir, keepFile = downloadedFile)
                            ModelFormatConverter.processIncomingModel(context, downloadedFile)
                        }
                        isPreparingModelFile = false
                        val statsLabel = if (converted.triangleCount > 0 || converted.vertexCount > 0) {
                            "${formatCompactCount(converted.vertexCount)} verts • ${formatCompactCount(converted.triangleCount)} tris"
                        } else null
                        applyImportedModelOutcome(
                            ModelImportOutcome(
                                file = converted.glbFile,
                                message = "Loaded from Cloud: $title (${converted.message})",
                                statsLabel = statsLabel
                            ),
                            resetModeToObject = true
                        )
                    }
                }
            )
        }
    }
}

data class ModelImportOutcome(
    val file: File?,
    val message: String,
    val statsLabel: String? = null
)

internal fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.0fK", count / 1_000.0)
    else -> count.toString()
}

internal fun pruneStaleModelCache(cacheDir: File, keepFile: File? = null) {
    val keepPath = runCatching { keepFile?.canonicalPath }.getOrNull()
    cacheDir.listFiles()?.forEach { child ->
        val name = child.name
        val isModelArtifact = name.startsWith("model_") ||
            name.startsWith("usdz_") ||
            name.startsWith("Cloud_Model_") ||
            (child.isFile && child.extension.lowercase(Locale.US) in setOf(
                "glb", "gltf", "obj", "usdz", "usda", "usdc", "3mf", "stl", "ply", "zip"
            ))
        if (isModelArtifact) {
            val childPath = runCatching { child.canonicalPath }.getOrNull()
            if (keepPath == null || childPath != keepPath) {
                runCatching {
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
        }
    }
}

private fun sniffModelExtensionFromHeader(header: ByteArray, length: Int): String? {
    if (length >= 4) {
        // Binary glTF ('glTF')
        if (header[0] == 'g'.code.toByte() && header[1] == 'l'.code.toByte() &&
            header[2] == 'T'.code.toByte() && header[3] == 'F'.code.toByte()
        ) {
            return "glb"
        }
        // ZIP container (USDZ, 3MF, or zipped glTF/OBJ)
        if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        ) {
            return "zip"
        }
    }
    val text = String(header, 0, length.coerceAtMost(256), Charsets.UTF_8).trimStart()
    return when {
        text.startsWith("{") -> "gltf"
        text.startsWith("ply\n", ignoreCase = true) || text.startsWith("ply\r", ignoreCase = true) -> "ply"
        text.startsWith("solid ", ignoreCase = true) -> "stl"
        text.startsWith("v ") || text.startsWith("#") || text.startsWith("mtllib") || text.startsWith("o ") -> "obj"
        else -> null
    }
}

/**
 * Streams a selected Content Uri to app's private cache directory using 512 KB buffered IO,
 * sniffs extensionless magic bytes, delegates to StreamingMeshProcessor / ModelFormatConverter,
 * and returns explicit success stats or exact failure diagnostics.
 */
fun copyUriAndConvertModel(context: Context, uri: Uri): ModelImportOutcome {
    return try {
        pruneStaleModelCache(context.cacheDir)

        val rawName = queryDisplayName(context, uri) ?: "model_${System.currentTimeMillis()}.glb"
        val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
            .ifEmpty { "model_${System.currentTimeMillis()}.glb" }
        val extension = cleanName.substringAfterLast('.', "").lowercase(Locale.US)
        val isRecognized3D = extension in setOf("glb", "gltf", "usdz", "usda", "usdc", "obj", "3mf", "stl", "ply", "zip")
        val baseName = (if (cleanName.contains('.')) cleanName.substringBeforeLast('.') else cleanName)
            .ifEmpty { "model" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")

        val modelFolder = File(context.cacheDir, "model_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempOutFile = File(modelFolder, "$baseName.${if (isRecognized3D) extension else "tmp"}")

        val headerPeek = ByteArray(256)
        var headerLen = 0
        val streamBufferBytes = 524_288

        context.contentResolver.openInputStream(uri)?.use { rawInput ->
            BufferedInputStream(rawInput, streamBufferBytes).use { bufferedIn ->
                BufferedOutputStream(tempOutFile.outputStream(), streamBufferBytes).use { bufferedOut ->
                    val chunk = ByteArray(streamBufferBytes)
                    var firstChunk = true
                    while (true) {
                        val read = bufferedIn.read(chunk)
                        if (read <= 0) break
                        if (firstChunk) {
                            headerLen = read.coerceAtMost(headerPeek.size)
                            System.arraycopy(chunk, 0, headerPeek, 0, headerLen)
                            firstChunk = false
                        }
                        bufferedOut.write(chunk, 0, read)
                    }
                    bufferedOut.flush()
                }
            }
        } ?: return ModelImportOutcome(null, "Storage provider returned no data stream for $cleanName")

        if (!tempOutFile.exists() || tempOutFile.length() == 0L) {
            tempOutFile.delete()
            return ModelImportOutcome(null, "Selected file is empty: $cleanName")
        }

        val finalExt = if (isRecognized3D) {
            extension
        } else {
            sniffModelExtensionFromHeader(headerPeek, headerLen) ?: "glb"
        }

        val cacheFile = if (tempOutFile.extension.equals(finalExt, ignoreCase = true)) {
            tempOutFile
        } else {
            File(modelFolder, "$baseName.$finalExt").also { target ->
                if (!tempOutFile.renameTo(target)) {
                    tempOutFile.copyTo(target, overwrite = true)
                    tempOutFile.delete()
                }
            }
        }

        val conversion = ModelFormatConverter.processIncomingModel(modelFolder, cacheFile)
        val statsLabel = if (conversion.triangleCount > 0 || conversion.vertexCount > 0) {
            "${formatCompactCount(conversion.vertexCount)} verts • ${formatCompactCount(conversion.triangleCount)} tris"
        } else {
            "${(conversion.glbFile.length() / (1024 * 1024).coerceAtLeast(1)).coerceAtLeast(1)} MB"
        }
        ModelImportOutcome(
            file = conversion.glbFile,
            message = conversion.message,
            statsLabel = statsLabel
        )
    } catch (oom: OutOfMemoryError) {
        System.gc()
        ModelImportOutcome(null, "Not enough memory for model file: ${oom.message ?: "Out of memory"}")
    } catch (t: Throwable) {
        ModelImportOutcome(null, "Could not process 3D file: ${t.message ?: t::class.java.simpleName}")
    }
}

fun copyUriToLocalCache(context: Context, uri: Uri): File? =
    copyUriAndConvertModel(context, uri).file

/**
 * Extracts display filename from a Content Uri.
 */
fun queryDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}

/**
 * Top floating segmented control pill: MR | AR | Object
 * Replicates the liquid-glass capsule with glossy highlight from user reference.
 */
@Composable
fun TopModeSegmentedPill(
    currentMode: ViewportMode,
    onModeSelected: (ViewportMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x73202532),
                        Color(0x8C10141D),
                        Color(0xA60A0C13)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x73FFFFFF),
                        Color(0x2BFFFFFF),
                        Color(0x1AFFFFFF),
                        Color(0x4DFFFFFF)
                    )
                ),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("top_mode_segmented_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ViewportMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0x404B576D),
                                                Color(0x2B333E50)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color(0xCCFFFFFF),
                                                Color(0x66FFFFFF),
                                                Color(0x99FFFFFF)
                                            )
                                        ),
                                        shape = RoundedCornerShape(percent = 50)
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(mode) }
                        .padding(horizontal = 16.dp)
                        .testTag("mode_tab_${mode.name.lowercase(Locale.ROOT)}")
                ) {
                    Text(
                        text = mode.title,
                        color = if (isSelected) Color.White else Color(0xB3CAD5E2),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Bottom action control capsule: PHOTO | ● REC | Open | Clear
 * Replicates the liquid-glass capsule with glowing circular REC button from user reference.
 */
@Composable
fun BottomControlBar(
    isRecording: Boolean,
    onPhotoClick: () -> Unit,
    onRecClick: () -> Unit,
    onOpenClick: () -> Unit,
    onOpenLongClick: () -> Unit = {},
    onOpenCloudLibrary: () -> Unit = {},
    onClearClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x73202532),
                        Color(0x8C10141D),
                        Color(0xA60A0C13)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x73FFFFFF),
                        Color(0x2BFFFFFF),
                        Color(0x1AFFFFFF),
                        Color(0x4DFFFFFF)
                    )
                ),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .testTag("bottom_control_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. PHOTO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onPhotoClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("photo_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PHOTO",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // 2. Glowing Red Circular REC Button
            val recTransition = rememberInfiniteTransition(label = "rec_pulse")
            val recScale by recTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isRecording) 1.08f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rec_scale"
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(recScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF334B),
                                Color(0xFFE50920),
                                Color(0xFFB30415)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color(0x99FFFFFF), Color(0x33FFFFFF))
                        ),
                        shape = CircleShape
                    )
                    .clickable(onClick = onRecClick)
                    .testTag("rec_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp))
                        Text(
                            text = "STOP",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp))
                        Text(
                            text = "REC",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            // 3. Open (Short Tap: System Local File Picker | Long Press: Open / Cloud Chooser)
            var isHoldingOpen by remember { mutableStateOf(false) }
            var holdProgress by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            isHoldingOpen = true
                            var triggeredLongPress = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val elapsed = System.currentTimeMillis() - downTime
                                holdProgress = (elapsed / 450f).coerceIn(0f, 1f)

                                if (!triggeredLongPress && elapsed >= 450L) {
                                    triggeredLongPress = true
                                    isHoldingOpen = false
                                    holdProgress = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenLongClick()
                                    break
                                }

                                if (event.changes.all { !it.pressed }) {
                                    if (!triggeredLongPress) {
                                        onOpenClick()
                                    }
                                    isHoldingOpen = false
                                    holdProgress = 0f
                                    break
                                }
                            }
                        }
                    }
                    .then(
                        if (isHoldingOpen && holdProgress > 0.05f) {
                            Modifier.border(
                                width = 1.2.dp,
                                color = NeonCyan.copy(alpha = 0.3f + (holdProgress * 0.7f)),
                                shape = RoundedCornerShape(percent = 50)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("open_model_picker_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isHoldingOpen && holdProgress > 0.30f) "Cloud ☁️" else "Open",
                    color = if (isHoldingOpen) NeonCyan else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            // 4. Clear
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClearClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("clear_scene_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Clear",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Recording badge displayed at top right when recording is in progress.
 */
@Composable
fun RecordingStatusBadge(
    seconds: Int,
    modifier: Modifier = Modifier
) {
    val minutes = seconds / 60
    val secs = seconds % 60
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color(0xCC000000),
        border = androidx.compose.foundation.BorderStroke(1.dp, RecButtonColor),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RecButtonColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, secs),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

