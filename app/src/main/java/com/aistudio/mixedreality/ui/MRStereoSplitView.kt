package com.aistudio.mixedreality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aistudio.mixedreality.ui.theme.*
import com.google.android.filament.Engine
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.createEnvironment
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberViewNodeManager
import java.io.File

/**
 * Dual-Viewport Stereoscopic Split View for MR (Mixed Reality) mode.
 * - Single CameraPassthroughBackground to prevent camera hardware lockup/crash.
 * - Shared single Filament Engine across both eye scenes to avoid GPU EGL context exhaustion.
 * - Two interconnected models: scaling or rotating one scales/rotates both in lockstep.
 * - AR experience anchored on the real ground surface.
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
    isAutoRotating: Boolean,
    rotationY: Float,
    isRecording: Boolean,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    modifier: Modifier = Modifier
) {
    // Shared synchronized scale and rotation across both eyes
    var sharedScaleMultiplier by remember { mutableFloatStateOf(1.0f) }
    var sharedRotationOffset by remember { mutableFloatStateOf(0f) }

    // AR Ground Anchor position in 3D world space
    var anchorGroundPosition by remember { mutableStateOf(Position(0f, -0.6f, -2.0f)) }

    val effectiveScale = scale * sharedScaleMultiplier
    val effectiveRotationY = (rotationY + sharedRotationOffset) % 360f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture handling linked to both models simultaneously
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    sharedScaleMultiplier = (sharedScaleMultiplier * zoom).coerceIn(0.25f, 3.5f)
                    sharedRotationOffset = (sharedRotationOffset + pan.x * 0.5f) % 360f
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Tap to anchor the 3D model onto the real ground surface
                    val normX = (offset.x / size.width.coerceAtLeast(1)) * 2f - 1f
                    val normY = (offset.y / size.height.coerceAtLeast(1)) * 2f - 1f
                    val targetX = normX * 0.8f
                    val targetZ = -1.6f - ((1f - normY) * 1.2f)
                    anchorGroundPosition = Position(targetX, -0.6f, targetZ)
                }
            }
    ) {
        // EXACTLY ONE single Camera Passthrough Background for the entire screen!
        // Prevents CAMERA_IN_USE / CameraAccessException crashes
        CameraPassthroughBackground(modifier = Modifier.fillMaxSize())

        // Horizontal Stereo Split Row: Left Eye | Divider | Right Eye
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT EYE VIEWPORT (Camera 1 + Model 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                StereoEyeScene(
                    isLeftEye = true,
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = color,
                    metallic = metallic,
                    roughness = roughness,
                    scale = effectiveScale,
                    rotationY = effectiveRotationY,
                    groundPosition = anchorGroundPosition,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader
                )
            }

            // CENTER STEREOSCOPIC DIVIDER
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // RIGHT EYE VIEWPORT (Camera 2 + Model 2)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                StereoEyeScene(
                    isLeftEye = false,
                    modelItem = modelItem,
                    customModelFile = customModelFile,
                    isModelVisible = isModelVisible,
                    color = color,
                    metallic = metallic,
                    roughness = roughness,
                    scale = effectiveScale,
                    rotationY = effectiveRotationY,
                    groundPosition = anchorGroundPosition,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader
                )
            }
        }
    }
}

/**
 * Individual Eye Viewport Scene rendering the 3D model anchored in AR ground space.
 * Uses shared engine and loaders to eliminate multi-engine GPU memory exhaustion and shader crashes.
 */
@Composable
private fun StereoEyeScene(
    isLeftEye: Boolean,
    modelItem: ModelItem,
    customModelFile: File?,
    isModelVisible: Boolean,
    color: Color,
    metallic: Float,
    roughness: Float,
    scale: Float,
    rotationY: Float,
    groundPosition: Position,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader
) {
    val windowManager = rememberViewNodeManager()

    val pbrMaterial = remember(materialLoader, color, metallic, roughness) {
        materialLoader.createColorInstance(
            color = color,
            metallic = metallic.coerceIn(0.0f, 0.5f),
            roughness = roughness.coerceIn(0.2f, 0.9f)
        )
    }

    val visorGlassMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF141923),
            metallic = 0.85f,
            roughness = 0.15f
        )
    }

    val floorGridMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0x3300E5FF),
            metallic = 0.2f,
            roughness = 0.8f
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

    // Interpupillary distance (IPD) horizontal offset: left eye = -0.035m, right eye = +0.035m
    val eyeOffsetX = if (isLeftEye) -0.035f else 0.035f

    SceneView(
        modifier = Modifier.fillMaxSize(),
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        environment = rememberEnvironment(environmentLoader) {
            createEnvironment(environmentLoader)
        },
        viewNodeWindowManager = windowManager,
        cameraManipulator = rememberCameraManipulator(),
        cameraNode = rememberCameraNode(engine) {
            position = Position(eyeOffsetX, 0.2f, 6.0f)
        },
        mainLightNode = rememberMainLightNode(engine),
        fillLightNode = rememberFillLightNode(engine)
    ) {
        // AR Real-world ground plane tracking surface
        CubeNode(
            size = Size(6f, 0.005f, 6f),
            position = Position(0f, -0.65f, groundPosition.z),
            materialInstance = floorGridMaterial
        )

        if (isModelVisible) {
            Render3DModel(
                modelItem = modelItem,
                customModelFile = customModelFile,
                customModelInstance = customModelInstance,
                pbrMaterial = pbrMaterial,
                visorGlassMaterial = visorGlassMaterial,
                toyCarModel = toyCarModel,
                windowManager = windowManager,
                scale = 0.7f * scale,
                rotationY = rotationY,
                position = groundPosition
            )
        }
    }
}
