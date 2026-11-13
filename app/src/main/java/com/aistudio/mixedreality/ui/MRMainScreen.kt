package com.aistudio.mixedreality.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Tune
import com.aistudio.mixedreality.util.FlashlightHelper
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aistudio.mixedreality.ui.theme.*
import com.aistudio.mixedreality.util.MediaManager
import com.aistudio.mixedreality.util.ModelFormatConverter
import com.aistudio.mixedreality.util.VideoSessionRecorder
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberSurfaceMirrorer
import io.github.sceneview.utils.SurfaceMirrorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // Prevents multi-engine EGL context crashes, shader errors, and GPU memory exhaustion
    val engine = rememberEngine()

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
    var activeRotationAxis by remember { mutableStateOf(RotationAxis.ALL) }

    // Cloud 3D Library Sheet (Google Drive Models - Long press on Open)
    var showCloudLibrary by remember { mutableStateOf(false) }

    // Flashlight / Torch toggle for low-light environments
    val hasFlashHardware = remember(context) { FlashlightHelper.isFlashAvailable(context) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isAppResumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    isAppResumed = false
                    FlashlightHelper.setTorchMode(context, false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            FlashlightHelper.setTorchMode(context, false)
        }
    }

    LaunchedEffect(isFlashlightOn, selectedMode, isAppResumed) {
        if (!isAppResumed) {
            FlashlightHelper.setTorchMode(context, false)
        } else if (selectedMode == ViewportMode.OBJECT) {
            if (isFlashlightOn) {
                FlashlightHelper.setTorchModeWithRetry(context, true)
            } else {
                FlashlightHelper.setTorchMode(context, false)
            }
        } else {
            // AR/MR owns the camera hardware via ARSession flashMode or CameraPassthrough;
            // ensure CameraManager torch is released cleanly before ARCore acquires it.
            if (FlashlightHelper.isTorchOn) {
                FlashlightHelper.setTorchMode(context, false)
            }
        }
    }

    // Handle incoming 3D model files from external apps / file manager on background thread
    LaunchedEffect(incomingModelUri) {
        val uri = incomingModelUri ?: return@LaunchedEffect
        val copiedFile = withContext(Dispatchers.IO) { copyUriToLocalCache(context, uri) }
        if (copiedFile != null && copiedFile.exists() && copiedFile.length() > 0) {
            customModelFile = copiedFile
            isModelVisible = true
            selectedMode = ViewportMode.OBJECT
            Toast.makeText(context, "Opened 3D model: ${copiedFile.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to load 3D model from file", Toast.LENGTH_SHORT).show()
        }
        onModelHandled()
    }

    // Handle App Shortcuts and Quick Settings tile launches
    LaunchedEffect(shortcutAction) {
        val action = shortcutAction ?: return@LaunchedEffect
        when (action) {
            "cloud_library" -> {
                showCloudLibrary = true
            }
            "launch_ar" -> {
                selectedMode = ViewportMode.AR
            }
        }
        onShortcutHandled()
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

    // Move Mode (Double-Tap toggles moving mode)
    var isMoveMode by remember { mutableStateOf(false) }

    // Temporary hint banner when entering full screen
    var showFullScreenHint by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            showFullScreenHint = true
            delay(2800L)
            showFullScreenHint = false
        }
    }

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

    // Requirement 4: System Document / File Picker for 3D models (.glb, .gltf)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val copiedFile = withContext(Dispatchers.IO) { copyUriToLocalCache(context, uri) }
                if (copiedFile != null && copiedFile.exists() && copiedFile.length() > 0) {
                    customModelFile = copiedFile
                    isModelVisible = true
                    val fileName = copiedFile.name
                    Toast.makeText(context, "Model loaded successfully: $fileName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Unable to read 3D model file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-orbit animation loop (smooth 30fps rotation loop to eliminate lighting thrashing and flicker)
    LaunchedEffect(isAutoRotating) {
        if (isAutoRotating) {
            while (isAutoRotating) {
                delay(33L)
                rotationY = (rotationY + 0.5f) % 360f
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
                    onSurfaceMirrorerChanged = { activeSurfaceMirrorer = it }
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
                        clearTrigger = clearTrigger,
                        isMoveMode = isMoveMode,
                        onToggleMoveMode = { isMoveMode = !isMoveMode },
                        activeRotationAxis = activeRotationAxis,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { isFullScreen = !isFullScreen },
                        isFlashlightOn = isFlashlightOn,
                        onStopAutoRotate = { isAutoRotating = false },
                        onFallbackToObject = { selectedMode = ViewportMode.OBJECT },
                        engine = engine
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
                        isRecording = isRecording,
                        engine = engine,
                        onSurfaceMirrorerChanged = { activeSurfaceMirrorer = it },
                        isARCoreAvailable = isARCoreAvailable,
                        clearTrigger = clearTrigger,
                        isMoveMode = isMoveMode,
                        onToggleMoveMode = { isMoveMode = !isMoveMode },
                        activeRotationAxis = activeRotationAxis,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { isFullScreen = !isFullScreen },
                        isFlashlightOn = isFlashlightOn,
                        onStopAutoRotate = { isAutoRotating = false },
                        onFallbackToObject = { selectedMode = ViewportMode.OBJECT }
                    )
                } else {
                    PermissionRequestPlaceholder(onGrant = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }
            }
        }

        // --- 2. TOP FLOATING SEGMENTED PILL (EXACT CENTER) & FLASHLIGHT (LEFT) ---
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
                FlashlightToggleButton(
                    isOn = isFlashlightOn,
                    onToggle = {
                        if (!hasFlashHardware) {
                            isFlashlightOn = false
                            Toast.makeText(context, "لا يوجد فلاش في هذا الجهاز", Toast.LENGTH_SHORT).show()
                        } else {
                            isFlashlightOn = !isFlashlightOn
                            val msg = if (isFlashlightOn) "كشاف الإضاءة: يعمل 💡" else "كشاف الإضاءة: متوقف"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                TopModeSegmentedPill(
                    currentMode = selectedMode,
                    onModeSelected = { mode ->
                        if (mode != ViewportMode.OBJECT && FlashlightHelper.isTorchOn) {
                            FlashlightHelper.setTorchMode(context, false)
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

        // Full Screen Hint Overlay (Fades out automatically after 2.8s)
        if (isFullScreen && showFullScreenHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 18.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "شاشة كاملة 🥽 • اضغط 3 مرات (Triple-tap) للرجوع",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Floating button to exit full screen mode
        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .clickable { isFullScreen = false }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("exit_fullscreen_button")
            ) {
                Text(
                    text = "✕ إظهار الأشرطة",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
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



        // --- 4. INDEPENDENT X/Y/Z 360° ROTATION CONTROLLER ---
        // Hidden in Full Screen mode or when scene is cleared
        androidx.compose.animation.AnimatedVisibility(
            visible = !isFullScreen && isModelVisible,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp, start = 12.dp, end = 12.dp)
        ) {
            SpatialRotationController(
                rotationX = rotationX,
                onRotationXChange = { rotationX = it },
                rotationY = rotationY,
                onRotationYChange = { rotationY = it },
                rotationZ = rotationZ,
                onRotationZChange = { rotationZ = it },
                activeAxis = activeRotationAxis,
                onActiveAxisChange = { activeRotationAxis = it },
                onStopAutoRotate = { isAutoRotating = false }
            )
        }

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
                    // Requirement 3: Clear button deletes / removes the model
                    isModelVisible = false
                    customModelFile = null
                    clearTrigger++
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
                    onRotationXChange = { rotationX = it },
                    rotationY = rotationY,
                    onRotationYChange = { rotationY = it },
                    rotationZ = rotationZ,
                    onRotationZChange = { rotationZ = it },
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
                        val converted = ModelFormatConverter.processIncomingModel(context, downloadedFile)
                        customModelFile = converted.glbFile
                        isModelVisible = true
                        selectedMode = ViewportMode.OBJECT
                        Toast.makeText(context, "Loaded from Cloud: $title", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }


    }
}

/**
 * Copies a selected Content Uri to app's private cache directory and processes USDZ/OBJ/GLB formats.
 */
fun copyUriToLocalCache(context: Context, uri: Uri): File? {
    return try {
        val rawName = queryDisplayName(context, uri) ?: "model_${System.currentTimeMillis()}.glb"
        val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        val extension = cleanName.substringAfterLast('.', "").lowercase()
        val isRecognized3D = extension in setOf("glb", "gltf", "usdz", "usda", "usdc", "obj")
        val baseName = if (cleanName.contains('.')) cleanName.substringBeforeLast('.') else cleanName
        val safeExt = if (isRecognized3D) ".$extension" else ".glb"
        val modelFolder = File(context.cacheDir, "model_${System.currentTimeMillis()}").apply { mkdirs() }
        val cacheFile = File(modelFolder, "$baseName$safeExt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return null
        }

        val conversion = ModelFormatConverter.processIncomingModel(modelFolder, cacheFile)
        conversion.glbFile
    } catch (e: Exception) {
        null
    }
}

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

/**
 * Quick toggle button for camera flashlight / torch in low-light environments.
 */
@Composable
fun FlashlightToggleButton(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .testTag("flashlight_toggle_button")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isOn) {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFEA00),
                                Color(0xFFFFB300),
                                Color(0xFFFF8F00)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x73202532),
                                Color(0x8C10141D),
                                Color(0xA60A0C13)
                            )
                        )
                    }
                )
                .border(
                    width = 1.2.dp,
                    brush = if (isOn) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F))
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x73FFFFFF),
                                Color(0x2BFFFFFF),
                                Color(0x1AFFFFFF),
                                Color(0x4DFFFFFF)
                            )
                        )
                    },
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (isOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Flashlight",
                tint = if (isOn) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

