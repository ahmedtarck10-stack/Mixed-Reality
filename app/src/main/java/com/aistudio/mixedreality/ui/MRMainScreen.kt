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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.mixedreality.ui.theme.*
import com.aistudio.mixedreality.util.MediaManager
import com.aistudio.mixedreality.util.VideoSessionRecorder
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberSurfaceMirrorer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MRMainScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Shared single Filament Engine & loaders across entire screen lifecycle
    // Prevents multi-engine EGL context crashes, shader errors, and GPU memory exhaustion
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

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
    var scaleFactor by remember { mutableFloatStateOf(1.0f) }
    var isAutoRotating by remember { mutableStateOf(true) }
    var rotationY by remember { mutableFloatStateOf(0f) }

    // Bottom sheet for model tuning / presets
    var showTuneDrawer by remember { mutableStateOf(false) }

    // Shutter flash animation
    var showFlash by remember { mutableStateOf(false) }

    // Video Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val videoRecorder = remember(context) { VideoSessionRecorder(context) }
    val surfaceMirrorer = rememberSurfaceMirrorer()

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
                val copiedFile = copyUriToLocalCache(context, uri)
                if (copiedFile != null && copiedFile.exists() && copiedFile.length() > 0) {
                    customModelFile = copiedFile
                    isModelVisible = true
                    val fileName = copiedFile.name
                    Toast.makeText(context, "تم تحميل النموذج بنجاح: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تعذر قراءة ملف النموذج المختار", Toast.LENGTH_SHORT).show()
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
                    scale = scaleFactor,
                    rotationY = rotationY,
                    onOpenClick = {
                        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*"))
                    },
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader,
                    surfaceMirrorer = surfaceMirrorer
                )
            }
            ViewportMode.AR -> {
                if (isARCoreAvailable) {
                    if (hasCameraPermission) {
                        ARMixedRealityView(
                            modelItem = selectedModel,
                            customModelFile = customModelFile,
                            isModelVisible = isModelVisible,
                            color = modelColor,
                            scale = scaleFactor,
                            clearTrigger = clearTrigger,
                            engine = engine,
                            modelLoader = modelLoader,
                            materialLoader = materialLoader
                        )
                    } else {
                        PermissionRequestPlaceholder(onGrant = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        })
                    }
                } else {
                    SpatialSimulationARView(
                        modelItem = selectedModel,
                        customModelFile = customModelFile,
                        isModelVisible = isModelVisible,
                        color = modelColor,
                        scale = scaleFactor,
                        clearTrigger = clearTrigger,
                        isRecording = isRecording,
                        engine = engine,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        environmentLoader = environmentLoader
                    )
                }
            }
            ViewportMode.MR -> {
                // Dual stereoscopic viewports with synchronized gestures and live camera passthrough
                MRStereoSplitView(
                    modelItem = selectedModel,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = modelColor,
                    metallic = metallic,
                    roughness = roughness,
                    scale = scaleFactor,
                    isAutoRotating = isAutoRotating,
                    rotationY = rotationY,
                    isRecording = isRecording,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader,
                    surfaceMirrorer = surfaceMirrorer,
                    isARCoreAvailable = isARCoreAvailable,
                    clearTrigger = clearTrigger,
                    isMoveMode = isMoveMode,
                    onToggleMoveMode = { isMoveMode = !isMoveMode },
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { isFullScreen = !isFullScreen }
                )
            }
        }

        // --- 2. TOP FLOATING SEGMENTED PILL (MR | AR | Object) ---
        // Hidden in Full Screen mode (Triple-Tap toggles)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isFullScreen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            TopModeSegmentedPill(
                currentMode = selectedMode,
                onModeSelected = { mode ->
                    if (mode == ViewportMode.AR || mode == ViewportMode.MR) {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    selectedMode = mode
                }
            )
        }

        // Full Screen Hint Overlay (Fades out automatically after 2.8s)
        if (isFullScreen && showFullScreenHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 18.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "وضع ملء الشاشة 🥽 • انقر 3 مرات لإظهار القوائم",
                    color = Color.White,
                    fontSize = 12.sp,
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
                                    Toast.makeText(context, "تم حفظ الصورة في المعرض 📸", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تم التقاط الصورة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "تم التقاط الصورة", Toast.LENGTH_SHORT).show()
                    }
                },
                onRecClick = {
                    // Requirement 2: Start / Stop video recording and save to Gallery
                    if (!isRecording) {
                        isRecording = true
                        videoRecorder.startRecording(activity, surfaceMirrorer, scope)
                        Toast.makeText(context, "بدأ تسجيل الفيديو 🎥", Toast.LENGTH_SHORT).show()
                    } else {
                        isRecording = false
                        scope.launch {
                            Toast.makeText(context, "جاري معالجة وحفظ الفيديو...", Toast.LENGTH_SHORT).show()
                            val uri = videoRecorder.stopRecording()
                            if (uri != null) {
                                Toast.makeText(context, "تم حفظ الفيديو في المعرض بنجاح 🎥", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "تم إنهاء التسجيل", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onOpenClick = {
                    // Requirement 4: Clicking Open goes to Files to pick a 3D model (.glb / .gltf)
                    filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*"))
                },
                onClearClick = {
                    // Requirement 3: Clear button deletes / removes the model
                    isModelVisible = false
                    customModelFile = null
                    clearTrigger++
                    Toast.makeText(context, "تم مسح النموذج من المشهد 🗑️", Toast.LENGTH_SHORT).show()
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
                        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream", "model/*"))
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
                    scale = scaleFactor,
                    onScaleChange = { scaleFactor = it },
                    isAutoRotating = isAutoRotating,
                    onToggleAutoRotate = { isAutoRotating = !isAutoRotating },
                    onClose = { showTuneDrawer = false }
                )
            }
        }
    }
}

/**
 * Copies a selected Content Uri to app's private cache directory.
 */
fun copyUriToLocalCache(context: Context, uri: Uri): File? {
    return try {
        var rawName = queryDisplayName(context, uri) ?: "model_${System.currentTimeMillis()}.glb"
        // Ensure proper 3D extension for Filament model loader
        val isGlbOrGltf = rawName.endsWith(".glb", ignoreCase = true) || rawName.endsWith(".gltf", ignoreCase = true)
        val finalName = if (!isGlbOrGltf) {
            if (rawName.contains('.')) "${rawName.substringBeforeLast('.')}.glb" else "$rawName.glb"
        } else {
            rawName
        }
        val cacheFile = File(context.cacheDir, finalName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        cacheFile
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
 */
@Composable
fun TopModeSegmentedPill(
    currentMode: ViewportMode,
    onModeSelected: (ViewportMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = PillContainerColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .wrapContentSize()
            .height(44.dp)
            .testTag("top_mode_segmented_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            ViewportMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) PillSelectedColor else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(mode) }
                        .padding(horizontal = 22.dp)
                        .testTag("mode_tab_${mode.name.lowercase(Locale.ROOT)}")
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = if (isSelected) PillTextColor else PillUnselectedTextColor
                    )
                }
            }
        }
    }
}

/**
 * Bottom action control capsule: PHOTO | ● REC | Open | Clear
 */
@Composable
fun BottomControlBar(
    isRecording: Boolean,
    onPhotoClick: () -> Unit,
    onRecClick: () -> Unit,
    onOpenClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = PillContainerColor,
        shadowElevation = 10.dp,
        modifier = Modifier
            .wrapContentSize()
            .height(56.dp)
            .testTag("bottom_control_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // PHOTO
            TextButton(
                onClick = onPhotoClick,
                colors = ButtonDefaults.textButtonColors(contentColor = PillTextColor),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("photo_button")
            ) {
                Text(
                    text = "PHOTO",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // ● REC Button
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

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = RecButtonColor,
                modifier = Modifier
                    .scale(recScale)
                    .height(42.dp)
                    .clickable(onClick = onRecClick)
                    .testTag("rec_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "STOP" else "REC",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Open
            TextButton(
                onClick = onOpenClick,
                colors = ButtonDefaults.textButtonColors(contentColor = PillTextColor),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("open_model_picker_button")
            ) {
                Text(
                    text = "Open",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            // Clear
            TextButton(
                onClick = onClearClick,
                colors = ButtonDefaults.textButtonColors(contentColor = PillTextColor),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("clear_scene_button")
            ) {
                Text(
                    text = "Clear",
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
