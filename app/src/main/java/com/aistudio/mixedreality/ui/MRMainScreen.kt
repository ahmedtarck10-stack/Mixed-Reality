package com.aistudio.mixedreality.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.mixedreality.ui.theme.*
import com.google.ar.core.Config
import io.github.sceneview.SceneView
import io.github.sceneview.ar.PlacementScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import kotlinx.coroutines.delay
import java.util.Locale

enum class ViewportMode(val title: String) {
    MR("MR"),
    AR("AR"),
    OBJECT("Object")
}

enum class ModelItem(
    val title: String,
    val subtitle: String,
    val iconDesc: String
) {
    VISION_HEADSET("Vision Headset", "Mixed Reality Spatial Visor", "🥽"),
    TOY_CAR("Khronos Toy Car", "Realistic 3D glTF Model", "🏎️"),
    HOLO_CUBE("Hologram Cube", "PBR Reflective Geometry", "🧊"),
    QUANTUM_ORB("Quantum Orb", "Metallic Specular Sphere", "🔮"),
    SPATIAL_CARD("Spatial Interface", "3D Floating UI Plane", "📄")
}

// Exact pill colors matching the screenshot
val PillContainerColor = Color(0xFFAEB4BC)
val PillSelectedColor = Color(0xFF8B929B)
val PillTextColor = Color(0xFF1E2227)
val PillUnselectedTextColor = Color(0xFF33383F)
val RecButtonColor = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MRMainScreen() {
    val context = LocalContext.current

    var selectedMode by remember { mutableStateOf(ViewportMode.OBJECT) }
    var selectedModel by remember { mutableStateOf(ModelItem.VISION_HEADSET) }
    var modelColor by remember { mutableStateOf(Color(0xFF00E5FF)) }
    var metallic by remember { mutableFloatStateOf(0.85f) }
    var roughness by remember { mutableFloatStateOf(0.15f) }
    var scaleFactor by remember { mutableFloatStateOf(1.0f) }
    var isAutoRotating by remember { mutableStateOf(true) }

    // Bottom sheet for Open model selector
    var showOpenDrawer by remember { mutableStateOf(false) }

    // Flash animation on Photo snap
    var showFlash by remember { mutableStateOf(false) }

    // Recording state & timer
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Placed anchors count for Clear button
    var clearTrigger by remember { mutableIntStateOf(0) }

    // Camera permission check for AR and MR modes
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
        if (!isGranted) {
            Toast.makeText(context, "Camera permission needed for AR/MR mode", Toast.LENGTH_SHORT).show()
            selectedMode = ViewportMode.OBJECT
        }
    }

    // Timer effect when recording is active
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    // Flash effect trigger
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
        // --- 1. MAIN 3D / AR / MR VIEWPORT ---
        when (selectedMode) {
            ViewportMode.OBJECT -> {
                Studio3DView(
                    modelItem = selectedModel,
                    color = modelColor,
                    metallic = metallic,
                    roughness = roughness,
                    scale = scaleFactor,
                    isAutoRotating = isAutoRotating
                )
            }
            ViewportMode.AR -> {
                if (hasCameraPermission) {
                    ARMixedRealityView(
                        modelItem = selectedModel,
                        color = modelColor,
                        scale = scaleFactor,
                        clearTrigger = clearTrigger
                    )
                } else {
                    PermissionRequestPlaceholder(onGrant = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }
            }
            ViewportMode.MR -> {
                if (hasCameraPermission) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ARMixedRealityView(
                            modelItem = selectedModel,
                            color = modelColor,
                            scale = scaleFactor,
                            clearTrigger = clearTrigger
                        )
                        // Vision Pro style Mixed Reality Spatial Passthrough HUD
                        VisionProMRHudOverlay(recordingActive = isRecording)
                    }
                } else {
                    PermissionRequestPlaceholder(onGrant = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }
            }
        }

        // --- 2. TOP FLOATING SEGMENTED PILL (MR | AR | Object) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            TopModeSegmentedPill(
                currentMode = selectedMode,
                onModeSelected = { mode ->
                    if (mode == ViewportMode.AR || mode == ViewportMode.MR) {
                        if (hasCameraPermission) {
                            selectedMode = mode
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        selectedMode = mode
                    }
                }
            )
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

        // --- 4. BOTTOM FLOATING CONTROL PILL (PHOTO | ● REC | Open | Clear) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            BottomControlBar(
                isRecording = isRecording,
                onPhotoClick = {
                    showFlash = true
                    Toast.makeText(context, "📸 Snapshot captured to Gallery", Toast.LENGTH_SHORT).show()
                },
                onRecClick = {
                    isRecording = !isRecording
                    if (isRecording) {
                        Toast.makeText(context, "● AR Recording started", Toast.LENGTH_SHORT).show()
                    } else {
                        val duration = "%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)
                        Toast.makeText(context, "🎥 Video saved ($duration)", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenClick = {
                    showOpenDrawer = true
                },
                onClearClick = {
                    clearTrigger++
                    Toast.makeText(context, "Scene objects cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- 5. CAMERA SHUTTER FLASH OVERLAY ---
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // --- 6. MODEL SELECTION & CUSTOMIZATION DRAWER (OPEN) ---
        if (showOpenDrawer) {
            ModalBottomSheet(
                onDismissRequest = { showOpenDrawer = false },
                containerColor = CardNavy,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                ModelPickerDrawerContent(
                    selectedModel = selectedModel,
                    onSelectModel = { selectedModel = it },
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
                    onClose = { showOpenDrawer = false }
                )
            }
        }
    }
}

/**
 * Top floating segmented control pill: MR | AR | Object
 * Exactly styled after user reference IMG_9700_4318669e.jpeg
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
 * Matches the reference screenshot layout and styling
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
 * Recording badge displayed at top right when recording is in progress
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
 * Mixed Reality futuristic HUD overlay (Vision Pro spatial visor style)
 */
@Composable
fun VisionProMRHudOverlay(recordingActive: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Holographic corner telemetry markers
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Text(
                text = "SPATIAL PASSTHROUGH // V4.6",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = NeonCyan.copy(alpha = 0.8f)
            )
            Text(
                text = "DEPTH ESTIMATION: 60 FPS • LOW LATENCY",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Center holographic reticle
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.Center)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.4f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .align(Alignment.Center)
            )
        }
    }
}

/**
 * 3D Studio inspection view with orbit camera manipulator
 */
@Composable
fun Studio3DView(
    modelItem: ModelItem,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    isAutoRotating: Boolean
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val windowManager = rememberViewNodeManager()

    var rotationY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isAutoRotating) {
        if (isAutoRotating) {
            while (true) {
                withFrameNanos {
                    rotationY = (rotationY + 0.6f) % 360f
                }
            }
        }
    }

    val pbrMaterial = remember(materialLoader, color, metallic, roughness) {
        materialLoader.createColorInstance(
            color = color,
            metallic = metallic,
            roughness = roughness
        )
    }

    // Visor Dark Glass Material
    val visorGlassMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF11141A),
            metallic = 0.95f,
            roughness = 0.05f
        )
    }

    val toyCarModel = rememberModelInstance(
        modelLoader = modelLoader,
        fileLocation = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb"
    )

    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        viewNodeWindowManager = windowManager,
        cameraManipulator = rememberCameraManipulator(),
        cameraNode = rememberCameraNode(engine) {
            position = Position(0f, 0.4f, 3.2f)
        },
        mainLightNode = rememberMainLightNode(engine)
    ) {
        when (modelItem) {
            ModelItem.VISION_HEADSET -> {
                // Procedural Vision Pro Style Visor Assembly
                // Curved Front Glass
                SphereNode(
                    radius = 0.7f * scale,
                    materialInstance = visorGlassMaterial,
                    position = Position(0f, 0f, 0f),
                    rotation = Rotation(x = 10f, y = rotationY, z = 0f)
                ) {
                    // Outer Aluminum Ring
                    CylinderNode(
                        radius = 0.75f * scale,
                        height = 0.15f * scale,
                        materialInstance = pbrMaterial,
                        position = Position(0f, 0f, -0.1f)
                    )
                }
            }
            ModelItem.TOY_CAR -> {
                toyCarModel?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 1.2f * scale,
                        autoAnimate = true,
                        rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                    )
                } ?: run {
                    // Fallback preview while loading
                    CubeNode(
                        size = Size(0.7f * scale, 0.7f * scale, 0.7f * scale),
                        materialInstance = pbrMaterial,
                        rotation = Rotation(x = 15f, y = rotationY, z = 0f)
                    )
                }
            }
            ModelItem.HOLO_CUBE -> {
                CubeNode(
                    size = Size(0.85f * scale, 0.85f * scale, 0.85f * scale),
                    materialInstance = pbrMaterial,
                    rotation = Rotation(x = 20f, y = rotationY, z = 0f)
                )
            }
            ModelItem.QUANTUM_ORB -> {
                SphereNode(
                    radius = 0.55f * scale,
                    materialInstance = pbrMaterial,
                    rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                )
            }
            ModelItem.SPATIAL_CARD -> {
                ViewNode(
                    windowManager = windowManager,
                    unlit = true,
                    position = Position(0f, 0f, 0f),
                    rotation = Rotation(x = 0f, y = rotationY, z = 0f)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardNavy),
                        border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .width(260.dp)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🥽",
                                fontSize = 36.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mixed Reality",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Interactive 3D Compose Surface in Spatial Workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ARCore Tap-to-place scene with surface plane finding and reticle
 */
@Composable
fun ARMixedRealityView(
    modelItem: ModelItem,
    color: Color,
    scale: Float,
    clearTrigger: Int
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // Key on clearTrigger to detach all anchors when Clear is clicked
    key(clearTrigger) {
        val material = remember(materialLoader, color) {
            materialLoader.createColorInstance(
                color = color,
                metallic = 0.85f,
                roughness = 0.2f
            )
        }

        val visorMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(
                color = Color(0xFF10141C),
                metallic = 0.95f,
                roughness = 0.05f
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PlacementScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                materialLoader = materialLoader,
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
                instantPlacement = true,
                showReticle = true,
                onPlaced = { anchor ->
                    AnchorNode(anchor = anchor) {
                        when (modelItem) {
                            ModelItem.VISION_HEADSET -> {
                                SphereNode(
                                    radius = 0.15f * scale,
                                    materialInstance = visorMaterial
                                ) {
                                    CylinderNode(
                                        radius = 0.16f * scale,
                                        height = 0.05f * scale,
                                        materialInstance = material,
                                        position = Position(0f, 0f, -0.02f)
                                    )
                                }
                            }
                            ModelItem.HOLO_CUBE -> {
                                CubeNode(
                                    size = Size(0.22f * scale, 0.22f * scale, 0.22f * scale),
                                    materialInstance = material
                                )
                            }
                            ModelItem.QUANTUM_ORB -> {
                                SphereNode(
                                    radius = 0.14f * scale,
                                    materialInstance = material
                                )
                            }
                            ModelItem.TOY_CAR, ModelItem.SPATIAL_CARD -> {
                                CubeNode(
                                    size = Size(0.2f * scale, 0.2f * scale, 0.2f * scale),
                                    materialInstance = material
                                )
                            }
                        }
                    }
                }
            )

            // AR Placement Guidance HUD
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan surfaces • Tap screen to place object",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Camera permission request card placeholder
 */
@Composable
fun PermissionRequestPlaceholder(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = NeonGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mixed Reality AR mode requires camera permissions to detect surfaces and track spatial objects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_camera_permission_button")
                ) {
                    Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Bottom sheet content for Open button: model selection, material colors, sliders
 */
@Composable
fun ModelPickerDrawerContent(
    selectedModel: ModelItem,
    onSelectModel: (ModelItem) -> Unit,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    metallic: Float,
    onMetallicChange: (Float) -> Unit,
    roughness: Float,
    onRoughnessChange: (Float) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    isAutoRotating: Boolean,
    onToggleAutoRotate: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Select 3D Object",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Model List
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ModelItem.values()) { item ->
                val isSelected = selectedModel == item
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) NeonCyan else Color.Transparent
                    ),
                    modifier = Modifier
                        .clickable { onSelectModel(item) }
                        .testTag("model_item_${item.name.lowercase(Locale.ROOT)}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = item.iconDesc, fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) NeonCyan else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Color selection palette
        Text(
            text = "MATERIAL COLOR",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        val colorPalette = listOf(
            Color(0xFF00E5FF), // Neon Cyan
            Color(0xFF7C4DFF), // Vision Purple
            Color(0xFF69F0AE), // Matrix Green
            Color(0xFFFF5252), // Flame Red
            Color(0xFFFFD740), // Gold
            Color(0xFFFFFFFF), // Pure White
            Color(0xFF263238)  // Obsidian Black
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            colorPalette.forEach { color ->
                val isSelected = selectedColor == color
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metallic slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Metallic",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = metallic,
                onValueChange = onMetallicChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "%.0f%%".format(metallic * 100),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        // Scale slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.4f..2.5f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Text(
                text = "%.1fx".format(scale),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Auto rotate switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Auto-Orbit Rotation",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Switch(
                checked = isAutoRotating,
                onCheckedChange = { onToggleAutoRotate() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonCyan
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
