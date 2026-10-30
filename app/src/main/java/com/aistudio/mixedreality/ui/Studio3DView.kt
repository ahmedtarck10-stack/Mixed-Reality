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

/**
 * Single-viewport 3D inspection studio (Object Mode).
 * Supports orbit camera manipulator, custom imported models, and empty scene state when cleared.
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
    rotationY: Float,
    onOpenClick: () -> Unit,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    surfaceMirrorer: SurfaceMirrorer? = null,
    modifier: Modifier = Modifier
) {
    val windowManager = rememberViewNodeManager()

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

    val environment = rememberEnvironment(environmentLoader) {
        createEnvironment(environmentLoader)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            viewNodeWindowManager = windowManager,
            cameraManipulator = rememberCameraManipulator(),
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
                    position = Position(0f, 0f, 0f)
                )
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
