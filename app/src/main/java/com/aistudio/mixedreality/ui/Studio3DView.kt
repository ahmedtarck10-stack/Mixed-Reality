package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mixedreality.ui.theme.*
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File

import com.google.android.filament.Engine
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.createEnvironment
import io.github.sceneview.rememberEnvironment

import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*

/**
 * Single-viewport 3D inspection studio (Object Mode).
 * Supports custom gestures:
 * - 1 Tap: Place model at tapped screen location
 * - 1 Finger Drag: 360-degree rotation
 * - 2 Fingers Drag/Pinch: Scale (0.1x to 2500x) or Move depending on mode
 * - Double Tap: Toggle between Move Mode and Scale Mode (no scaling)
 * - Triple Tap: Toggle Full Screen (hide/show upper and down bars)
 */
@Composable
fun Studio3DView(
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    onScaleChange: (Float) -> Unit = {},
    rotationY: Float,
    onRotationYChange: (Float) -> Unit = {},
    rotationX: Float = 0f,
    onRotationXChange: (Float) -> Unit = {},
    isMoveMode: Boolean = false,
    onToggleMoveMode: () -> Unit = {},
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    onStopAutoRotate: () -> Unit = {},
    onOpenClick: () -> Unit,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer? = null,
    modifier: Modifier = Modifier
) {
    val windowManager = rememberViewNodeManager()
    val scope = rememberCoroutineScope()
    var modelPosition by remember { mutableStateOf(Position(0f, 0f, 0f)) }
    var showModeBadge by remember { mutableStateOf(false) }

    var isFirstLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(isMoveMode) {
        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            showModeBadge = true
            delay(2200L)
            showModeBadge = false
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
            color = Color(0xFF161A22),
            metallic = 0.35f,
            roughness = 0.45f
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

    val environment = rememberEnvironment(environmentLoader) {
        createEnvironment(environmentLoader)
    }

    var viewWidthPx by remember { mutableFloatStateOf(1080f) }
    var viewHeightPx by remember { mutableFloatStateOf(1920f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0) viewWidthPx = size.width.toFloat()
                if (size.height > 0) viewHeightPx = size.height.toFloat()
            }
            .spatialMRGestures(
                scope = scope,
                isMoveMode = isMoveMode,
                onSingleTap = { tapOffset ->
                    if (!isModelVisible) return@spatialMRGestures
                    onStopAutoRotate()
                    if (viewWidthPx > 0f && viewHeightPx > 0f) {
                        val normX = (tapOffset.x / viewWidthPx) * 2f - 1f
                        val normY = -((tapOffset.y / viewHeightPx) * 2f - 1f)
                        val halfWidthAtZ = 1.35f
                        val halfHeightAtZ = halfWidthAtZ * (viewHeightPx / viewWidthPx)
                        modelPosition = Position(
                            x = normX * halfWidthAtZ,
                            y = 0.3f + (normY * halfHeightAtZ),
                            z = 0f
                        )
                    }
                },
                onDoubleTap = {
                    onToggleMoveMode()
                },
                onTripleTap = {
                    onToggleFullScreen()
                },
                onOneFingerRotate = { dx, dy ->
                    onStopAutoRotate()
                    val newRotY = (rotationY + dx * 0.35f) % 360f
                    onRotationYChange(if (newRotY < 0f) newRotY + 360f else newRotY)

                    val newRotX = (rotationX + dy * 0.35f).coerceIn(-90f, 90f)
                    onRotationXChange(newRotX)
                },
                onTwoFingerZoom = { zoomMultiplier ->
                    val newScale = (scale * zoomMultiplier).coerceIn(0.1f, 2500.0f)
                    onScaleChange(newScale)
                },
                onMove = { panX, panY ->
                    if (viewWidthPx > 0f && viewHeightPx > 0f) {
                        val halfWidthAtZ = 1.35f
                        val halfHeightAtZ = halfWidthAtZ * (viewHeightPx / viewWidthPx)
                        val deltaX = (panX / viewWidthPx) * (2f * halfWidthAtZ)
                        val deltaY = -(panY / viewHeightPx) * (2f * halfHeightAtZ)
                        modelPosition = Position(
                            x = modelPosition.x + deltaX,
                            y = modelPosition.y + deltaY,
                            z = modelPosition.z
                        )
                    }
                }
            )
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            viewNodeWindowManager = windowManager,
            cameraManipulator = null,
            autoCenterContent = false,
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 0.3f, 3.4f)
            },
            mainLightNode = rememberMainLightNode(engine),
            fillLightNode = rememberFillLightNode(engine),
            surfaceMirrorer = surfaceMirrorer
        ) {
            if (isModelVisible) {
                Render3DModel(
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    customModelInstance = customModelInstance,
                    pbrMaterial = pbrMaterial,
                    visorGlassMaterial = visorGlassMaterial,
                    toyCarModel = toyCarModel,
                    windowManager = windowManager,
                    scale = scale,
                    rotationY = rotationY,
                    rotationX = rotationX,
                    position = modelPosition
                )
            }
        }

        // Mode switch feedback pill (Double-Tap)
        androidx.compose.animation.AnimatedVisibility(
            visible = showModeBadge,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 160.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xDD121620),
                border = BorderStroke(
                    1.5.dp,
                    if (isMoveMode) NeonCyan else Color(0xFFFFD54F)
                ),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMoveMode) "🖐️ وضع التحريك: استخدم إصبعين للتحريك" else "🔍 وضع التكبير والتصغير (0.1x – 2500x)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Loading overlay while custom 3D model is being parsed
        if (customModelFile != null && customModelInstance == null && isModelVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                        text = "جاري تحميل النموذج وقراءة الخامات...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Empty state when user clicked Clear
        if (!isModelVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "المشهد فارغ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تم مسح النموذج • اضغط Open لاختيار ملف 3D أو نموذج جديد",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                    text = "Open Models",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
