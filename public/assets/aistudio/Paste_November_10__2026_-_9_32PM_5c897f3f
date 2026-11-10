@file:JvmName("ARSceneKt")

package io.github.sceneview.ar

import android.content.Context.WINDOW_SERVICE
import android.util.Size
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.WindowManager as AndroidWindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.utils.readBuffer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.View
import com.google.ar.core.Anchor
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.PlaybackFailedException
import io.github.sceneview.EngineRenderInvalidators
import io.github.sceneview.FrameRateGate
import io.github.sceneview.RenderInvalidator
import io.github.sceneview.SceneFrameActivity
import io.github.sceneview.SceneFrameDispatch
import io.github.sceneview.SceneNodeManager
import io.github.sceneview.SceneRenderInvalidators
import io.github.sceneview.SceneRenderer
import io.github.sceneview.utils.SurfaceMirrorer
import io.github.sceneview.RenderQuality
import io.github.sceneview.SurfaceType
import io.github.sceneview.applyRenderQuality
import io.github.sceneview.ar.arcore.configure
import io.github.sceneview.ar.arcore.isTracking
import io.github.sceneview.ar.camera.ARCameraStream
import io.github.sceneview.ar.light.LightEstimator
import io.github.sceneview.ar.node.ARCameraNode
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.DepthMeshNode
import io.github.sceneview.ar.node.PointCloudNode
import io.github.sceneview.ar.node.PoseNode
import io.github.sceneview.ar.node.TrackableNode
import io.github.sceneview.ar.scene.PlaneRenderer
import io.github.sceneview.ar.scene.PlaneRendererBase
import io.github.sceneview.ar.scene.SceneUnderstanding
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.collision.HitResult
import io.github.sceneview.environment.Environment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.gesture.replayHandedBackDown
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode
import io.github.sceneview.node.ViewNode.WindowManager
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberARView
import io.github.sceneview.safeDestroyEnvironment
import io.github.sceneview.sortTransformsIfUnsorted
import io.github.sceneview.safeDestroyIndirectLight
import kotlinx.coroutines.delay
import java.io.File
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal val PLAYBACK_DATASET_URI_ALLOWED_SCHEMES: Set<String> = setOf("content", "file")

internal fun isAllowedPlaybackDatasetUri(uri: android.net.Uri?): Boolean =
    uri == null || uri.scheme in PLAYBACK_DATASET_URI_ALLOWED_SCHEMES

internal class ChangeGate<T>(initial: T) {
    private var last: T = initial

    fun shouldApply(next: T): Boolean {
        if (last == next) return false
        last = next
        return true
    }
}

@Suppress("DEPRECATION", "UNUSED_PARAMETER", "UnusedParameter")
@Composable
fun ARSceneView(
    modifier: Modifier = Modifier,
    surfaceType: SurfaceType = SurfaceType.Surface,
    engine: Engine = rememberEngine(),
    modelLoader: ModelLoader = rememberModelLoader(engine),
    materialLoader: MaterialLoader = rememberMaterialLoader(engine),
    environmentLoader: EnvironmentLoader = rememberEnvironmentLoader(engine),
    sessionFeatures: Set<Session.Feature> = setOf(),
    playbackDataset: File? = null,
    playbackDatasetUri: android.net.Uri? = null,
    // تم التعديل لعدم فرض أقصى دقة 4K التي تسبب اختناق المستشعرات على الأجهزة الحديثة
    sessionCameraConfig: ((Session) -> CameraConfig)? = null,
    flashMode: Config.FlashMode = Config.FlashMode.OFF,
    planeFindingMode: Config.PlaneFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
    depthMode: Config.DepthMode = Config.DepthMode.DISABLED,
    instantPlacementMode: Config.InstantPlacementMode = Config.InstantPlacementMode.DISABLED,
    geospatialMode: Config.GeospatialMode = Config.GeospatialMode.DISABLED,
    streetscapeGeometryMode: Config.StreetscapeGeometryMode = Config.StreetscapeGeometryMode.DISABLED,
    cloudAnchorMode: Config.CloudAnchorMode = Config.CloudAnchorMode.DISABLED,
    augmentedFaceMode: Config.AugmentedFaceMode = Config.AugmentedFaceMode.DISABLED,
    imageStabilizationMode: Config.ImageStabilizationMode = Config.ImageStabilizationMode.OFF,
    semanticMode: Config.SemanticMode = Config.SemanticMode.DISABLED,
    updateMode: Config.UpdateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
    // تم الضبط افتراضياً إلى FIXED لمنع التغير البؤري الذي يسبب اهتزاز المجسمات
    focusMode: Config.FocusMode = Config.FocusMode.FIXED,
    sessionConfiguration: ((session: Session, Config) -> Unit)? = null,
    planeRenderer: Boolean = true,
    planeRendererVersion: PlaneRendererBase.Version = PlaneRendererBase.Version.V2,
    sceneUnderstanding: SceneUnderstanding? = null,
    cameraStream: ARCameraStream? = rememberARCameraStream(materialLoader),
    view: View = rememberARView(engine),
    renderQuality: RenderQuality? = null,
    isOpaque: Boolean = true,
    renderer: Renderer = rememberRenderer(engine),
    scene: Scene = rememberScene(engine),
    environment: Environment = rememberAREnvironment(engine),
    mainLightNode: LightNode? = rememberMainLightNode(engine),
    fillLightNode: LightNode? = rememberFillLightNode(engine),
    cameraNode: ARCameraNode = rememberARCameraNode(engine),
    cameraExposure: Float? = null,
    collisionSystem: CollisionSystem = rememberCollisionSystem(view),
    viewNodeWindowManager: WindowManager? = null,
    onSessionCreated: ((session: Session) -> Unit)? = null,
    onSessionResumed: ((session: Session) -> Unit)? = null,
    onSessionPaused: ((session: Session) -> Unit)? = null,
    onSessionFailed: ((exception: Exception) -> Unit)? = null,
    onSessionFailure: ((failure: ARSessionFailure) -> Unit)? = null,
    onPlaybackFailed: ((exception: Exception) -> Unit)? = null,
    onConfigDowngraded: ((downgrade: ARConfigDowngrade) -> Unit)? = null,
    onSessionUpdated: ((session: Session, frame: Frame) -> Unit)? = null,
    onTrackingFailureChanged: ((trackingFailureReason: TrackingFailureReason?) -> Unit)? = null,
    surfaceMirrorer: SurfaceMirrorer? = null,
    onGestureListener: GestureDetector.OnGestureListener? = rememberOnGestureListener(),
    onTouchEvent: ((e: MotionEvent, hitResult: HitResult?) -> Boolean)? = null,
    permissionHandler: ARPermissionHandler? = rememberActivityARPermissionHandler(),
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    cameraPermissionOverlay: (@Composable BoxScope.(ARCameraPermissionState) -> Unit)? = {
        ARCameraPermissionOverlay(it)
    },
    arCoreAvailabilityOverlay: (@Composable BoxScope.(ARCoreAvailabilityState) -> Unit)? = {
        ARCoreAvailabilityOverlay(it)
    },
    onARCoreAvailability: ((availability: ARCoreAvailability?) -> Unit)? = null,
    onCameraPermissionStateChanged: ((state: ARCameraPermissionState?) -> Unit)? = null,
    content: (@Composable ARSceneScope.() -> Unit)? = null
) {
    if (LocalInspectionMode.current) {
        ARScenePreview(modifier)
        return
    }

    val context = LocalContext.current

    val hostView = LocalView.current
    DisposableEffect(hostView) {
        KeepScreenOnLeases.acquire(hostView)
        onDispose { KeepScreenOnLeases.release(hostView) }
    }

    val arPlaneRenderer = remember(engine, materialLoader, scene) {
        PlaneRenderer(engine, materialLoader, scene)
    }
    val lightEstimator = remember(engine, environmentLoader) {
        LightEstimator(engine, environmentLoader.iblPrefilter)
    }
    DisposableEffect(arPlaneRenderer, lightEstimator) {
        onDispose {
            arPlaneRenderer.destroy()
            lightEstimator.destroy()
        }
    }

    val onSessionCreatedRef = remember { AtomicReference(onSessionCreated) }
    val onSessionResumedRef = remember { AtomicReference(onSessionResumed) }
    val onSessionPausedRef = remember { AtomicReference(onSessionPaused) }
    val onSessionFailedRef = remember { AtomicReference(onSessionFailed) }
    val onSessionFailureRef = remember { AtomicReference(onSessionFailure) }
    val onPlaybackFailedRef = remember { AtomicReference(onPlaybackFailed) }
    val onConfigDowngradedRef = remember { AtomicReference(onConfigDowngraded) }
    val onSessionUpdatedRef = remember { AtomicReference(onSessionUpdated) }
    val onTrackingFailureChangedRef = remember { AtomicReference(onTrackingFailureChanged) }
    val sessionConfigurationRef = remember { AtomicReference(sessionConfiguration) }
    val sessionCameraConfigRef = remember { AtomicReference(sessionCameraConfig) }
    val flashModeRef = remember { AtomicReference(flashMode) }

    val planeFindingModeRef = remember { AtomicReference(planeFindingMode) }
    val depthModeRef = remember { AtomicReference(depthMode) }
    val instantPlacementModeRef = remember { AtomicReference(instantPlacementMode) }
    val geospatialModeRef = remember { AtomicReference(geospatialMode) }
    val streetscapeGeometryModeRef = remember { AtomicReference(streetscapeGeometryMode) }
    val cloudAnchorModeRef = remember { AtomicReference(cloudAnchorMode) }
    val augmentedFaceModeRef = remember { AtomicReference(augmentedFaceMode) }
    val imageStabilizationModeRef = remember { AtomicReference(imageStabilizationMode) }
    val semanticModeRef = remember { AtomicReference(semanticMode) }
    val updateModeRef = remember { AtomicReference(updateMode) }
    val focusModeRef = remember { AtomicReference(focusMode) }

    SideEffect {
        onSessionCreatedRef.set(onSessionCreated)
        onSessionResumedRef.set(onSessionResumed)
        onSessionPausedRef.set(onSessionPaused)
        onSessionFailedRef.set(onSessionFailed)
        onSessionFailureRef.set(onSessionFailure)
        onPlaybackFailedRef.set(onPlaybackFailed)
        onConfigDowngradedRef.set(onConfigDowngraded)
        onSessionUpdatedRef.set(onSessionUpdated)
        onTrackingFailureChangedRef.set(onTrackingFailureChanged)
        sessionConfigurationRef.set(sessionConfiguration)
        sessionCameraConfigRef.set(sessionCameraConfig)
        flashModeRef.set(flashMode)
        planeFindingModeRef.set(planeFindingMode)
        depthModeRef.set(depthMode)
        instantPlacementModeRef.set(instantPlacementMode)
        geospatialModeRef.set(geospatialMode)
        streetscapeGeometryModeRef.set(streetscapeGeometryMode)
        cloudAnchorModeRef.set(cloudAnchorMode)
        augmentedFaceModeRef.set(augmentedFaceMode)
        imageStabilizationModeRef.set(imageStabilizationMode)
        semanticModeRef.set(semanticMode)
        updateModeRef.set(updateMode)
        focusModeRef.set(focusMode)
    }

    LaunchedEffect(view, renderQuality) {
        renderQuality?.let { view.applyRenderQuality(it) }
    }

    val prevTrackingFailureRef = remember { AtomicReference<TrackingFailureReason?>(null) }
    val isFrontFaceWindingInvertedRef = remember { AtomicBoolean(false) }

    val builtIndirectLightRef = remember { AtomicReference<IndirectLight?>(null) }

    DisposableEffect(engine, builtIndirectLightRef, scene, environment) {
        onDispose {
            scene.indirectLight = null
            builtIndirectLightRef.getAndSet(null)?.let {
                engine.safeDestroyIndirectLight(it)
            }
        }
    }

    val baselineMainLightColorRef = remember(mainLightNode) {
        AtomicReference<io.github.sceneview.math.Color?>(null)
    }
    val baselineMainLightIntensityRef = remember(mainLightNode) {
        AtomicReference<Float?>(null)
    }

    require(playbackDataset == null || playbackDatasetUri == null) {
        "ARSceneView: pass either playbackDataset (File) OR playbackDatasetUri (Uri), not both."
    }
    require(isAllowedPlaybackDatasetUri(playbackDatasetUri)) {
        "ARSceneView: playbackDatasetUri scheme '${playbackDatasetUri?.scheme}' is not " +
            "allowed — expected one of $PLAYBACK_DATASET_URI_ALLOWED_SCHEMES."
    }

    var cameraPermissionState by remember { mutableStateOf<ARCameraPermissionState?>(null) }
    var arCoreAvailability by remember { mutableStateOf<ARCoreAvailability?>(null) }
    val arCore = remember {
        val initialPlaybackDataset = playbackDataset
        val initialPlaybackDatasetUri = playbackDatasetUri
        ARCore(
            onSessionCreated = { session ->
                cameraPermissionState = null
                arCoreAvailability = null
                cameraStream?.let { session.setCameraTextureNames(it.cameraTextureIds) }
                
                val dispatchPlaybackFailure: (Exception) -> Unit = { e ->
                    if (onPlaybackFailedRef.get() != null) {
                        onPlaybackFailedRef.get()?.invoke(e)
                    } else {
                        onSessionFailedRef.get()?.invoke(e)
                        onSessionFailureRef.get()?.invoke(ARSessionFailure.from(e))
                    }
                }
                val bindFile: () -> Unit = {
                    initialPlaybackDataset?.let { file ->
                        try {
                            session.setPlaybackDataset(file.absolutePath)
                        } catch (e: PlaybackFailedException) {
                            dispatchPlaybackFailure(e)
                        } catch (e: Exception) {
                            dispatchPlaybackFailure(e)
                        }
                    }
                }
                val bindUri: () -> Unit = {
                    initialPlaybackDatasetUri?.let { uri ->
                        try {
                            session.setPlaybackDatasetUri(uri)
                        } catch (e: PlaybackFailedException) {
                            dispatchPlaybackFailure(e)
                        } catch (e: Exception) {
                            dispatchPlaybackFailure(e)
                        }
                    }
                }
                bindFile()
                bindUri()

                val requestedCameraConfig = sessionCameraConfigRef.get()?.invoke(session)
                requestedCameraConfig?.let { session.cameraConfig = it }

                var effectiveRequestedDepthMode: Config.DepthMode? = depthModeRef.get()
                session.configure { config ->
                    config.updateMode = updateModeRef.get() ?: Config.UpdateMode.LATEST_CAMERA_IMAGE
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.flashMode = flashModeRef.get() ?: Config.FlashMode.OFF
                    config.planeFindingMode = planeFindingModeRef.get()
                        ?: Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.depthMode = effectiveRequestedDepthMode ?: Config.DepthMode.DISABLED
                    config.instantPlacementMode = instantPlacementModeRef.get()
                        ?: Config.InstantPlacementMode.DISABLED
                    config.geospatialMode = geospatialModeRef.get() ?: Config.GeospatialMode.DISABLED
                    config.streetscapeGeometryMode = streetscapeGeometryModeRef.get()
                        ?: Config.StreetscapeGeometryMode.DISABLED
                    config.cloudAnchorMode = cloudAnchorModeRef.get() ?: Config.CloudAnchorMode.DISABLED
                    config.augmentedFaceMode = augmentedFaceModeRef.get()
                        ?: Config.AugmentedFaceMode.DISABLED
                    config.imageStabilizationMode = imageStabilizationModeRef.get()
                        ?: Config.ImageStabilizationMode.OFF
                    config.semanticMode = semanticModeRef.get() ?: Config.SemanticMode.DISABLED
                    
                    // فرض FIXED كخيار افتراضي لمنع الانجراف الناتج عن تغير التركيز
                    config.focusMode = focusModeRef.get() ?: Config.FocusMode.FIXED
                    
                    sessionConfigurationRef.get()?.invoke(session, config)
                    
                    effectiveRequestedDepthMode = config.depthMode.takeIf {
                        it != Config.DepthMode.DISABLED
                    }
                }

                onConfigDowngradedRef.get()?.let { onDowngraded ->
                    detectConfigDowngrades(
                        requestedDepthMode = effectiveRequestedDepthMode,
                        effectiveDepthMode = session.config.depthMode,
                        requestedCameraConfig = requestedCameraConfig,
                        effectiveCameraConfig = session.cameraConfig
                    ).forEach(onDowngraded)
                }

                cameraStream?.let {
                    scene.addEntity(it.entity)
                    it.attachedScene = scene
                }
                onSessionCreatedRef.get()?.invoke(session)
            },
            onSessionResumed = { session ->
                onSessionResumedRef.get()?.invoke(session)
            },
            onSessionPaused = { session ->
                onSessionPausedRef.get()?.invoke(session)
            },
            onArSessionFailed = { exception ->
                onSessionFailedRef.get()?.invoke(exception)
                onSessionFailureRef.get()?.invoke(ARSessionFailure.from(exception))
            },
            onSessionConfigChanged = { session, _ ->
                isFrontFaceWindingInvertedRef.set(
                    session.cameraConfig.facingDirection == CameraConfig.FacingDirection.FRONT
                )
            }
        )
    }

    val currentPermissionHandler by rememberUpdatedState(permissionHandler)
    arCore.onCameraPermissionDenied = { permanently ->
        val state = ARCameraPermissionState(
            permanentlyDenied = permanently,
            request = { arCore.retryCameraPermission(currentPermissionHandler) },
            openSettings = { arCore.openAppSettings(currentPermissionHandler) },
        )
        cameraPermissionState = state
        onCameraPermissionStateChanged?.invoke(state)
    }

    arCore.onCameraPermissionGranted = {
        cameraPermissionState = null
        onCameraPermissionStateChanged?.invoke(null)
    }

    arCore.onARCoreAvailability = { availability ->
        arCoreAvailability = availability
        onARCoreAvailability?.invoke(availability)
    }

    DisposableEffect(lifecycle) {
        arCore.create(context, permissionHandler, sessionFeatures)

        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { arCore.resume(context, permissionHandler) }
            override fun onPause(owner: LifecycleOwner) { arCore.pause() }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            arCore.detachHost()
            arCore.destroy()
        }
    }

    val flashModeGate = remember { ChangeGate(flashMode) }
    LaunchedEffect(flashMode) {
        if (!flashModeGate.shouldApply(flashMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.flashMode != flashMode) {
            session.configure { config -> config.flashMode = flashMode }
        }
    }

    val planeFindingModeGate = remember { ChangeGate(planeFindingMode) }
    LaunchedEffect(planeFindingMode) {
        if (!planeFindingModeGate.shouldApply(planeFindingMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.planeFindingMode != planeFindingMode) {
            session.configure { config -> config.planeFindingMode = planeFindingMode }
        }
    }
    val depthModeGate = remember { ChangeGate(depthMode) }
    LaunchedEffect(depthMode) {
        if (!depthModeGate.shouldApply(depthMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.depthMode != depthMode) {
            session.configure { config -> config.depthMode = depthMode }
        }
    }
    val instantPlacementModeGate = remember { ChangeGate(instantPlacementMode) }
    LaunchedEffect(instantPlacementMode) {
        if (!instantPlacementModeGate.shouldApply(instantPlacementMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.instantPlacementMode != instantPlacementMode) {
            session.configure { config -> config.instantPlacementMode = instantPlacementMode }
        }
    }
    val geospatialModeGate = remember { ChangeGate(geospatialMode) }
    LaunchedEffect(geospatialMode) {
        if (!geospatialModeGate.shouldApply(geospatialMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.geospatialMode != geospatialMode) {
            session.configure { config -> config.geospatialMode = geospatialMode }
        }
    }
    val streetscapeGeometryModeGate = remember { ChangeGate(streetscapeGeometryMode) }
    LaunchedEffect(streetscapeGeometryMode) {
        if (!streetscapeGeometryModeGate.shouldApply(streetscapeGeometryMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.streetscapeGeometryMode != streetscapeGeometryMode) {
            session.configure { config -> config.streetscapeGeometryMode = streetscapeGeometryMode }
        }
    }
    val cloudAnchorModeGate = remember { ChangeGate(cloudAnchorMode) }
    LaunchedEffect(cloudAnchorMode) {
        if (!cloudAnchorModeGate.shouldApply(cloudAnchorMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.cloudAnchorMode != cloudAnchorMode) {
            session.configure { config -> config.cloudAnchorMode = cloudAnchorMode }
        }
    }
    val augmentedFaceModeGate = remember { ChangeGate(augmentedFaceMode) }
    LaunchedEffect(augmentedFaceMode) {
        if (!augmentedFaceModeGate.shouldApply(augmentedFaceMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.augmentedFaceMode != augmentedFaceMode) {
            session.configure { config -> config.augmentedFaceMode = augmentedFaceMode }
        }
    }
    val imageStabilizationModeGate = remember { ChangeGate(imageStabilizationMode) }
    LaunchedEffect(imageStabilizationMode) {
        if (!imageStabilizationModeGate.shouldApply(imageStabilizationMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.imageStabilizationMode != imageStabilizationMode) {
            session.configure { config -> config.imageStabilizationMode = imageStabilizationMode }
        }
    }
    val semanticModeGate = remember { ChangeGate(semanticMode) }
    LaunchedEffect(semanticMode) {
        if (!semanticModeGate.shouldApply(semanticMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.semanticMode != semanticMode) {
            session.configure { config -> config.semanticMode = semanticMode }
        }
    }
    val updateModeGate = remember { ChangeGate(updateMode) }
    LaunchedEffect(updateMode) {
        if (!updateModeGate.shouldApply(updateMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.updateMode != updateMode) {
            session.configure { config -> config.updateMode = updateMode }
        }
    }
    val focusModeGate = remember { ChangeGate(focusMode) }
    LaunchedEffect(focusMode) {
        if (!focusModeGate.shouldApply(focusMode)) return@LaunchedEffect
        val session = arCore.session ?: return@LaunchedEffect
        if (session.config.focusMode != focusMode) {
            session.configure { config -> config.focusMode = focusMode }
        }
    }

    val nodeManager = remember(scene, collisionSystem) { SceneNodeManager(scene, collisionSystem) }

    DisposableEffect(environment, scene) {
        scene.indirectLight = environment.indirectLight
        scene.skybox = environment.skybox
        onDispose {
            if (scene.skybox === environment.skybox) scene.skybox = null
            if (scene.indirectLight === environment.indirectLight) scene.indirectLight = null
        }
    }

    SideEffect {
        view.scene = scene
        view.camera = cameraNode.camera
        cameraNode.collisionSystem = collisionSystem
        cameraNode.setView(view)
        cameraExposure?.let { cameraNode.setExposure(it) }
    }

    val prevMainLightRef = remember { AtomicReference<LightNode?>(null) }
    SideEffect {
        val prev = prevMainLightRef.get()
        if (prev != mainLightNode) {
            prev?.let { nodeManager.removeNode(it) }
            mainLightNode?.let { nodeManager.addNode(it) }
            prevMainLightRef.set(mainLightNode)
        }
    }

    DisposableEffect(fillLightNode) {
        fillLightNode?.let { nodeManager.addNode(it) }
        onDispose {
            fillLightNode?.let { nodeManager.removeNode(it) }
        }
    }

    val scopeChildNodes: SnapshotStateList<Node> = remember { mutableStateListOf() }
    val childNodesRef = remember { AtomicReference(emptyList<Node>()) }
    val frameActivity = remember { SceneFrameActivity() }
    DisposableEffect(frameActivity) { onDispose { frameActivity.clear() } }
    val frameDispatch = remember { SceneFrameDispatch() }
    DisposableEffect(frameDispatch) { onDispose { frameDispatch.clear() } }

    LaunchedEffect(nodeManager) {
        var prevNodes = emptyList<Node>()
        snapshotFlow { scopeChildNodes.toList() }.collect { newNodes ->
            (prevNodes - newNodes.toSet()).forEach { nodeManager.removeNode(it) }
            (newNodes - prevNodes.toSet()).forEach { nodeManager.addNode(it) }
            prevNodes = newNodes
            childNodesRef.set(newNodes)
            frameActivity.setRoots(newNodes)
        }
    }

    val cameraStreamRef = remember { AtomicReference<ARCameraStream?>(cameraStream) }
    val prevCameraStreamRef = remember { AtomicReference<ARCameraStream?>(null) }
    SideEffect {
        cameraStreamRef.set(cameraStream)
        val prev = prevCameraStreamRef.get()
        if (prev != cameraStream) {
            prev?.let {
                scene.removeEntity(it.entity)
                it.attachedScene = null
            }
            cameraStream?.let { stream ->
                arCore.session?.let {
                    it.setCameraTextureNames(stream.cameraTextureIds)
                    scene.addEntity(stream.entity)
                    stream.attachedScene = scene
                }
            }
            prevCameraStreamRef.set(cameraStream)
        }
    }

    SideEffect {
        arPlaneRenderer.isEnabled = planeRenderer
    }

    SideEffect {
        sceneUnderstanding?.let { su ->
            arPlaneRenderer.isEnabled = su.planeVisualization
            lightEstimator.isEnabled = su.lighting
            cameraStream?.isDepthOcclusionEnabled = su.occlusion
        }
    }

    val isResumed = remember {
        AtomicBoolean(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { isResumed.set(true) }
            override fun onPause(owner: LifecycleOwner) { isResumed.set(false) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val gestureDetector = remember(context) { GestureDetector(context = context, listener = null) }
    val cameraGestureDetectorRef = remember { AtomicReference<CameraGestureDetector?>(null) }

    val capturedTouchNodeRef = remember { AtomicReference<Node?>(null) }

    val safeGestureListener = remember(onGestureListener) { onGestureListener?.arSessionSafe() }
    SideEffect { gestureDetector.listener = safeGestureListener }

    val touchDispatcher: (MotionEvent) -> Unit = { event ->
        val hitResult = collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }
        val hitNode = hitResult?.node
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            capturedTouchNodeRef.getAndSet(null)?.takeIf { it !== hitNode }?.onCapturedTouchEvent(event)
        }
        val capturedNode = capturedTouchNodeRef.get()?.takeIf { it !== hitNode }
        var consumedByNode = false
        if (onTouchEvent?.invoke(event, hitResult) != true) {
            consumedByNode = capturedNode?.onCapturedTouchEvent(event) == true ||
                    (hitResult != null && hitNode?.onTouchEvent(event, hitResult) == true)
            if (!consumedByNode) {
                replayHandedBackDown(
                    event, capturedTouchNodeRef.get(), hitResult, gestureDetector,
                    cameraGestureDetectorRef.get(),
                )
                gestureDetector.onTouchEvent(event, hitResult)
                cameraGestureDetectorRef.get()?.onTouchEvent(event)
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> capturedTouchNodeRef.set(hitNode.takeIf { consumedByNode })
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> capturedTouchNodeRef.set(null)
        }
    }

    @Suppress("DEPRECATION")
    val display = remember(context) {
        (context.getSystemService(WINDOW_SERVICE) as AndroidWindowManager).defaultDisplay
    }

    val sceneRenderer = remember(engine, view, renderer) {
        SceneRenderer(engine, view, renderer)
    }

    val lastArFrameTimestamp = remember { AtomicLong(0L) }
    val trackingStateLog = remember { ARTrackingStateLog() }
    val arFramesOwed = remember { AtomicInteger(1) }

    val frameRateGate = remember { FrameRateGate() }
    val sceneInvalidator = remember { RenderInvalidator() }
    DisposableEffect(frameRateGate, sceneInvalidator) {
        sceneInvalidator.attach(frameRateGate)
        onDispose { sceneInvalidator.detach(frameRateGate) }
    }
    DisposableEffect(scene, sceneInvalidator) {
        SceneRenderInvalidators.register(scene, sceneInvalidator)
        EngineRenderInvalidators.register(engine, sceneInvalidator)
        onDispose {
            EngineRenderInvalidators.unregister(engine, sceneInvalidator)
            SceneRenderInvalidators.unregister(scene, sceneInvalidator)
        }
    }

    SideEffect {
        sceneRenderer.surfaceMirrorer = surfaceMirrorer
        sceneRenderer.onSurfaceResized = { width, height ->
            cameraNode.updateProjection()
            arCore.session?.setDisplayGeometry(display.rotation, width, height)
            @Suppress("DEPRECATION")
            arPlaneRenderer.viewSize = Size(width, height)
            arFramesOwed.set(1)
        }
        sceneRenderer.onSurfaceReady = { viewHeight ->
            if (cameraGestureDetectorRef.get() == null) {
                cameraGestureDetectorRef.set(
                    CameraGestureDetector(
                        viewHeight = viewHeight,
                        cameraManipulator = null
                    )
                )
            }
        }
        sceneRenderer.onSurfaceDestroyed = {
            cameraGestureDetectorRef.set(null)
        }
    }

    DisposableEffect(sceneRenderer) {
        onDispose { sceneRenderer.destroy() }
    }

    LaunchedEffect(renderer, isOpaque) {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, if (isOpaque) 1.0 else 0.0)
        }
    }

    LaunchedEffect(engine, renderer, view, scene) {
        var lastFrameTimeNanos = 0L
        while (true) {
            if (!isResumed.get()) {
                lastFrameTimeNanos = 0L
                delay(16)
                continue
            }
            withFrameNanos { frameTimeNanos ->
                var hasNewArFrame = false
                var sceneChanged = false
                val presented = sceneRenderer.renderFrame(
                    frameTimeNanos,
                    shouldPresent = {
                        hasNewArFrame || sceneChanged || arFramesOwed.get() > 0
                    }
                ) {
                    view.isFrontFaceWindingInverted = isFrontFaceWindingInvertedRef.get()

                    val childNodes = childNodesRef.get()

                    arCore.session?.let { session ->
                        try {
                            session.updateOrNull()?.let { frame ->
                                hasNewArFrame =
                                    lastArFrameTimestamp.getAndSet(frame.timestamp) != frame.timestamp
                                val trackingFailureReason = readTrackingFailureReason(
                                    trackingStateLog, session, frame, frameTimeNanos
                                )
                                onARFrame(
                                    engine = engine,
                                    scene = scene,
                                    view = view,
                                    cameraNode = cameraNode,
                                    cameraStream = cameraStreamRef.get(),
                                    lightEstimator = lightEstimator,
                                    mainLightNode = mainLightNode,
                                    environment = environment,
                                    arPlaneRenderer = arPlaneRenderer,
                                    childNodes = childNodes,
                                    baselineMainLightColorRef = baselineMainLightColorRef,
                                    baselineMainLightIntensityRef = baselineMainLightIntensityRef,
                                    builtIndirectLightRef = builtIndirectLightRef,
                                    session = session,
                                    frame = frame
                                )
                                notifyHostOfFrame(
                                    prevTrackingFailureRef = prevTrackingFailureRef,
                                    onTrackingFailureChangedRef = onTrackingFailureChangedRef,
                                    onSessionUpdatedRef = onSessionUpdatedRef,
                                    session = session,
                                    frame = frame,
                                    trackingFailureReason = trackingFailureReason
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "SceneView",
                                "ARCore session update failed",
                                e
                            )
                        }
                    }

                    engine.sortTransformsIfUnsorted()
                    modelLoader.updateLoad()
                    frameDispatch.dispatch(childNodes, frameTimeNanos)

                    val inertiaDelta = if (lastFrameTimeNanos == 0L) {
                        NOMINAL_INERTIA_FRAME_SECONDS
                    } else {
                        ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                            .coerceIn(0f, MAX_INERTIA_FRAME_SECONDS)
                    }
                    lastFrameTimeNanos = frameTimeNanos
                    gestureDetector.advanceInertia(inertiaDelta)

                    sceneChanged = frameRateGate.shouldRender(
                        frameTimeNanos = frameTimeNanos,
                        active = frameActivity.hasActiveNode ||
                                gestureDetector.isCoasting ||
                                modelLoader.isLoading
                    )
                }

                if (presented) {
                    frameRateGate.didRender(frameTimeNanos)
                    if (arFramesOwed.get() > 0) {
                        arFramesOwed.decrementAndGet()
                    }
                }
            }
        }
    }

    Box(modifier = if (isOpaque) modifier.background(Color.Black) else modifier) {
        when (surfaceType) {
            SurfaceType.Surface -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).also { sv ->
                        sceneRenderer.attachToSurfaceView(sv, isOpaque, ctx, display, touchDispatcher)
                    }
                },
                update = {}
            )

            SurfaceType.TextureSurface -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        sceneRenderer.attachToTextureView(tv, isOpaque, ctx, display, touchDispatcher)
                    }
                },
                update = {}
            )
        }

        val denial = cameraPermissionState
        if (denial != null && cameraPermissionOverlay != null) cameraPermissionOverlay(denial)

        val availability = arCoreAvailability
        if (denial == null && availability != null && arCoreAvailabilityOverlay != null) {
            val state = remember(availability, permissionHandler) {
                ARCoreAvailabilityState(
                    availability = availability,
                    retry = { arCore.retryARCoreAvailability(permissionHandler) },
                )
            }
            arCoreAvailabilityOverlay(state)
        }
    }

    if (content != null) {
        val scope = remember(engine, modelLoader, materialLoader, environmentLoader, nodeManager) {
            ARSceneScope(
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                environmentLoader = environmentLoader,
                _nodes = scopeChildNodes,
                nodeRemover = nodeManager::removeNode
            )
        }
        scope.content()
    }
}

private const val NOMINAL_INERTIA_FRAME_SECONDS = 1f / 60f
private const val MAX_INERTIA_FRAME_SECONDS = 0.05f

private fun onARFrame(
    engine: Engine,
    scene: Scene,
    view: View,
    cameraNode: ARCameraNode,
    cameraStream: ARCameraStream?,
    lightEstimator: LightEstimator?,
    mainLightNode: LightNode?,
    environment: Environment,
    arPlaneRenderer: PlaneRenderer,
    childNodes: List<Node>,
    baselineMainLightColorRef: AtomicReference<io.github.sceneview.math.Color?>,
    baselineMainLightIntensityRef: AtomicReference<Float?>,
    builtIndirectLightRef: AtomicReference<IndirectLight?>,
    session: Session,
    frame: Frame
) {
    cameraStream?.update(session, frame)
    cameraNode.update(session, frame)

    lightEstimator?.update(session, frame, cameraNode.camera)?.let { estimation ->
        mainLightNode?.let { light ->
            baselineMainLightColorRef.compareAndSet(null, light.color)
            baselineMainLightIntensityRef.compareAndSet(null, light.intensity)
            val baselineColor = baselineMainLightColorRef.get() ?: light.color
            val baselineIntensity = baselineMainLightIntensityRef.get() ?: light.intensity
            estimation.mainLightColor?.let { light.color = baselineColor * it }
            estimation.mainLightIntensity?.let { light.intensity = baselineIntensity * it }
            estimation.mainLightDirection?.let { light.lightDirection = it }
        }
        val indirectLight = environment.indirectLight
        if (shouldRebuildIndirectLight(estimation, indirectLight)) {
            val sources = pickIndirectLightSources(estimation, indirectLight)
            val newIbl = IndirectLight.Builder().apply {
                if (sources.useEstimationIrradiance) {
                    estimation.irradiance?.let { irradiance(3, it) }
                } else {
                    indirectLight?.irradianceTexture?.let { irradiance(it) }
                }
                if (sources.useEstimationReflections) {
                    estimation.reflections?.let { reflections(it) }
                } else {
                    indirectLight?.reflectionsTexture?.let { reflections(it) }
                }
                indirectLight?.intensity?.let { intensity(it) }
                indirectLight?.getRotation(null)?.let { rotation(it) }
            }.build(engine)
            scene.indirectLight = newIbl
            builtIndirectLightRef.getAndSet(newIbl)?.let { previousBuiltIbl ->
                engine.safeDestroyIndirectLight(previousBuiltIbl)
            }
        }
    }

    arPlaneRenderer.update(session, frame)

    val (updatedTrackables, updatedAnchors) = buildFrameUpdatedSets(frame, childNodes)

    for (n in childNodes) when (n) {
        is PoseNode -> {
            n.frameUpdatedTrackables = updatedTrackables
            n.frameUpdatedAnchors = updatedAnchors
            n.update(session, frame)
            n.frameUpdatedTrackables = null
            n.frameUpdatedAnchors = null
        }
        is DepthMeshNode -> n.update(session, frame)
        is PointCloudNode -> n.update(session, frame)
    }
}

private fun notifyHostOfFrame(
    prevTrackingFailureRef: AtomicReference<TrackingFailureReason?>,
    onTrackingFailureChangedRef: AtomicReference<((TrackingFailureReason?) -> Unit)?>,
    onSessionUpdatedRef: AtomicReference<((Session, Frame) -> Unit)?>,
    session: Session,
    frame: Frame,
    trackingFailureReason: TrackingFailureReason
) {
    dispatchHostFrameCallbacks(
        newTrackingFailure = trackingFailureReason.takeIf { it != TrackingFailureReason.NONE },
        prevTrackingFailureRef = prevTrackingFailureRef,
        onTrackingFailureChanged = { onTrackingFailureChangedRef.get()?.invoke(it) },
        onSessionUpdated = { onSessionUpdatedRef.get()?.invoke(session, frame) },
        onThrow = { name, exception -> android.util.Log.e("SceneView", "$name threw", exception) }
    )
}

private fun readTrackingFailureReason(
    log: ARTrackingStateLog,
    session: Session,
    frame: Frame,
    nowNanos: Long
): TrackingFailureReason {
    val camera = frame.camera
    val state = camera.trackingState
    val reason = if (state == TrackingState.TRACKING) {
        TrackingFailureReason.NONE
    } else {
        camera.trackingFailureReason
    }
    log.transition(state, reason, nowNanos, System.identityHashCode(session))
        ?.let { android.util.Log.i("SceneView", it) }
    return reason
}

private fun buildFrameUpdatedSets(
    frame: Frame,
    childNodes: List<Node>
): Pair<Set<Trackable>?, Set<Anchor>?> {
    var updatedTrackables: Set<Trackable>? = null
    var updatedAnchors: Set<Anchor>? = null
    for (n in childNodes) {
        when (n) {
            is AnchorNode ->
                if (updatedAnchors == null) updatedAnchors = frame.updatedAnchors.toHashSet()
            is TrackableNode<*> ->
                if (updatedTrackables == null) {
                    updatedTrackables = frame.getUpdatedTrackables(Trackable::class.java).toHashSet()
                }
        }
        if (updatedTrackables != null && updatedAnchors != null) break
    }
    return updatedTrackables to updatedAnchors
}

internal fun pickIndirectLightSources(
    estimation: LightEstimator.Estimation,
    baseIndirectLight: IndirectLight?
): IndirectLightSources {
    val hasIrradiance = estimation.irradiance != null
    val hasReflections = estimation.reflections != null
    return IndirectLightSources(
        useEstimationIrradiance = hasIrradiance,
        useEstimationReflections = hasReflections,
        hasBaseIndirectLight = baseIndirectLight != null
    )
}

internal fun shouldRebuildIndirectLight(
    estimation: LightEstimator.Estimation,
    baseIndirectLight: IndirectLight?
): Boolean {
    val irradianceAvailable = estimation.irradiance != null ||
        baseIndirectLight?.irradianceTexture != null
    val reflectionsAvailable = estimation.reflections != null ||
        baseIndirectLight?.reflectionsTexture != null
    val anyFresh = estimation.irradiance != null || estimation.reflections != null
    return anyFresh && irradianceAvailable && reflectionsAvailable
}

internal data class IndirectLightSources(
    val useEstimationIrradiance: Boolean,
    val useEstimationReflections: Boolean,
    val hasBaseIndirectLight: Boolean
)

fun highestResolutionCameraConfig(session: Session): CameraConfig =
    runCatching {
        val filter = CameraConfigFilter(session)
            .setFacingDirection(CameraConfig.FacingDirection.BACK)
            .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
        session.getSupportedCameraConfigs(filter).maxByOrNull {
            it.imageSize.width.toLong() * it.imageSize.height.toLong()
        }
    }.getOrNull() ?: session.cameraConfig

fun frontCameraConfig(session: Session): CameraConfig =
    runCatching {
        val filter = CameraConfigFilter(session)
            .setFacingDirection(CameraConfig.FacingDirection.FRONT)
        session.getSupportedCameraConfigs(filter).maxByOrNull {
            it.imageSize.width.toLong() * it.imageSize.height.toLong()
        }
    }.getOrNull() ?: session.cameraConfig

@Composable
fun rememberARCameraNode(
    engine: Engine,
    creator: () -> ARCameraNode = {
        createARCameraNode(engine)
    }
) = rememberNode(creator)

@Composable
fun rememberARCameraStream(
    materialLoader: MaterialLoader,
    creator: () -> ARCameraStream = {
        createARCameraStream(materialLoader)
    }
) = remember(materialLoader) { creator() }.also { cameraStream ->
    DisposableEffect(cameraStream) {
        onDispose {
            cameraStream.destroy()
        }
    }
}

@Composable
fun rememberAREnvironment(
    engine: Engine,
    apply: Environment.() -> Unit = {}
): Environment {
    val context = LocalContext.current
    val iblBuffer = remember(context) {
        runCatching {
            context.assets.readBuffer("environments/neutral/neutral_ibl.ktx")
        }.getOrNull()
    }
    val environment = remember(engine, iblBuffer) {
        createAREnvironment(engine, iblBuffer).apply(apply)
    }
    DisposableEffect(environment) {
        onDispose { engine.safeDestroyEnvironment(environment) }
    }
    return environment
}

@Composable
private fun ARScenePreview(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF1E3A8A),
                        androidx.compose.ui.graphics.Color(0xFF0F172A),
                    ),
                ),
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "📷  ARSceneView preview",
                style = androidx.compose.ui.text.TextStyle(
                    color = androidx.compose.ui.graphics.Color(0xFFDBEAFE),
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
            )
            androidx.compose.foundation.text.BasicText(
                text = "AR rendering needs ARCore + Filament JNI, neither loaded by AS LayoutLib.\n" +
                    "Use Android Studio Live Edit on an ARCore-supported device for the live session.",
                style = androidx.compose.ui.text.TextStyle(
                    color = androidx.compose.ui.graphics.Color(0xFF93C5FD),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 16.sp,
                ),
            )
        }
    }
}

@Deprecated("Use ARSceneView instead", ReplaceWith("ARSceneView(modifier, surfaceType, engine, modelLoader, materialLoader, environmentLoader, sessionFeatures, playbackDataset, sessionCameraConfig, flashMode, sessionConfiguration, planeRenderer, cameraStream, view, isOpaque, renderer, scene, environment, mainLightNode, fillLightNode, cameraNode, cameraExposure, collisionSystem, viewNodeWindowManager, onSessionCreated, onSessionResumed, onSessionPaused, onSessionFailed, onPlaybackFailed, onSessionUpdated, onTrackingFailureChanged, onGestureListener, onTouchEvent, permissionHandler, lifecycle, content)"))
@Suppress("DEPRECATION")
@Composable
fun ARScene(
    modifier: Modifier = Modifier,
    surfaceType: SurfaceType = SurfaceType.Surface,
    engine: Engine = rememberEngine(),
    modelLoader: ModelLoader = rememberModelLoader(engine),
    materialLoader: MaterialLoader = rememberMaterialLoader(engine),
    environmentLoader: EnvironmentLoader = rememberEnvironmentLoader(engine),
    sessionFeatures: Set<Session.Feature> = setOf(),
    playbackDataset: File? = null,
    sessionCameraConfig: ((Session) -> CameraConfig)? = ::highestResolutionCameraConfig,
    flashMode: Config.FlashMode = Config.FlashMode.OFF,
    sessionConfiguration: ((session: Session, Config) -> Unit)? = null,
    planeRenderer: Boolean = true,
    planeRendererVersion: PlaneRendererBase.Version = PlaneRendererBase.Version.V2,
    cameraStream: ARCameraStream? = rememberARCameraStream(materialLoader),
    view: View = rememberARView(engine),
    isOpaque: Boolean = true,
    renderer: Renderer = rememberRenderer(engine),
    scene: Scene = rememberScene(engine),
    environment: Environment = rememberAREnvironment(engine),
    mainLightNode: LightNode? = rememberMainLightNode(engine),
    fillLightNode: LightNode? = rememberFillLightNode(engine),
    cameraNode: ARCameraNode = rememberARCameraNode(engine),
    cameraExposure: Float? = null,
    collisionSystem: CollisionSystem = rememberCollisionSystem(view),
    viewNodeWindowManager: ViewNode.WindowManager? = null,
    onSessionCreated: ((session: Session) -> Unit)? = null,
    onSessionResumed: ((session: Session) -> Unit)? = null,
    onSessionPaused: ((session: Session) -> Unit)? = null,
    onSessionFailed: ((exception: Exception) -> Unit)? = null,
    onPlaybackFailed: ((exception: Exception) -> Unit)? = null,
    onSessionUpdated: ((session: Session, Frame) -> Unit)? = null,
    onTrackingFailureChanged: ((trackingFailureReason: TrackingFailureReason?) -> Unit)? = null,
    onGestureListener: GestureDetector.OnGestureListener? = rememberOnGestureListener(),
    onTouchEvent: ((e: MotionEvent, hitResult: HitResult?) -> Boolean)? = null,
    permissionHandler: ARPermissionHandler? = rememberActivityARPermissionHandler(),
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    content: (@Composable ARSceneScope.() -> Unit)? = null
) = ARSceneView(
    modifier = modifier,
    surfaceType = surfaceType,
    engine = engine,
    modelLoader = modelLoader,
    materialLoader = materialLoader,
    environmentLoader = environmentLoader,
    sessionFeatures = sessionFeatures,
    playbackDataset = playbackDataset,
    sessionCameraConfig = sessionCameraConfig,
    flashMode = flashMode,
    sessionConfiguration = sessionConfiguration,
    planeRenderer = planeRenderer,
    planeRendererVersion = planeRendererVersion,
    cameraStream = cameraStream,
    view = view,
    isOpaque = isOpaque,
    renderer = renderer,
    scene = scene,
    environment = environment,
    mainLightNode = mainLightNode,
    fillLightNode = fillLightNode,
    cameraNode = cameraNode,
    cameraExposure = cameraExposure,
    collisionSystem = collisionSystem,
    viewNodeWindowManager = viewNodeWindowManager,
    onSessionCreated = onSessionCreated,
    onSessionResumed = onSessionResumed,
    onSessionPaused = onSessionPaused,
    onSessionFailed = onSessionFailed,
    onPlaybackFailed = onPlaybackFailed,
    onSessionUpdated = onSessionUpdated,
    onTrackingFailureChanged = onTrackingFailureChanged,
    onGestureListener = onGestureListener,
    onTouchEvent = onTouchEvent,
    permissionHandler = permissionHandler,
    lifecycle = lifecycle,
    content = content
)

@Deprecated(
    "Binary-compatibility overload. Use the ARSceneView overload that takes `onCameraPermissionStateChanged`.",
    level = DeprecationLevel.HIDDEN,
)
@Suppress("LongParameterList", "DEPRECATION")
@Composable
fun ARSceneView(
    modifier: Modifier = Modifier,
    surfaceType: SurfaceType = SurfaceType.Surface,
    engine: Engine = rememberEngine(),
    modelLoader: ModelLoader = rememberModelLoader(engine),
    materialLoader: MaterialLoader = rememberMaterialLoader(engine),
    environmentLoader: EnvironmentLoader = rememberEnvironmentLoader(engine),
    sessionFeatures: Set<Session.Feature> = setOf(),
    playbackDataset: File? = null,
    playbackDatasetUri: android.net.Uri? = null,
    sessionCameraConfig: ((Session) -> CameraConfig)? = ::highestResolutionCameraConfig,
    flashMode: Config.FlashMode = Config.FlashMode.OFF,
    planeFindingMode: Config.PlaneFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
    depthMode: Config.DepthMode = Config.DepthMode.DISABLED,
    instantPlacementMode: Config.InstantPlacementMode = Config.InstantPlacementMode.DISABLED,
    geospatialMode: Config.GeospatialMode = Config.GeospatialMode.DISABLED,
    streetscapeGeometryMode: Config.StreetscapeGeometryMode = Config.StreetscapeGeometryMode.DISABLED,
    cloudAnchorMode: Config.CloudAnchorMode = Config.CloudAnchorMode.DISABLED,
    augmentedFaceMode: Config.AugmentedFaceMode = Config.AugmentedFaceMode.DISABLED,
    imageStabilizationMode: Config.ImageStabilizationMode = Config.ImageStabilizationMode.OFF,
    semanticMode: Config.SemanticMode = Config.SemanticMode.DISABLED,
    updateMode: Config.UpdateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
    focusMode: Config.FocusMode = Config.FocusMode.AUTO,
    sessionConfiguration: ((session: Session, Config) -> Unit)? = null,
    planeRenderer: Boolean = true,
    planeRendererVersion: PlaneRendererBase.Version = PlaneRendererBase.Version.V1,
    sceneUnderstanding: SceneUnderstanding? = null,
    cameraStream: ARCameraStream? = rememberARCameraStream(materialLoader),
    view: View = rememberARView(engine),
    renderQuality: RenderQuality? = null,
    isOpaque: Boolean = true,
    renderer: Renderer = rememberRenderer(engine),
    scene: Scene = rememberScene(engine),
    environment: Environment = rememberAREnvironment(engine),
    mainLightNode: LightNode? = rememberMainLightNode(engine),
    fillLightNode: LightNode? = rememberFillLightNode(engine),
    cameraNode: ARCameraNode = rememberARCameraNode(engine),
    cameraExposure: Float? = null,
    collisionSystem: CollisionSystem = rememberCollisionSystem(view),
    viewNodeWindowManager: WindowManager? = null,
    onSessionCreated: ((session: Session) -> Unit)? = null,
    onSessionResumed: ((session: Session) -> Unit)? = null,
    onSessionPaused: ((session: Session) -> Unit)? = null,
    onSessionFailed: ((exception: Exception) -> Unit)? = null,
    onSessionFailure: ((failure: ARSessionFailure) -> Unit)? = null,
    onPlaybackFailed: ((exception: Exception) -> Unit)? = null,
    onConfigDowngraded: ((downgrade: ARConfigDowngrade) -> Unit)? = null,
    onSessionUpdated: ((session: Session, Frame) -> Unit)? = null,
    onTrackingFailureChanged: ((trackingFailureReason: TrackingFailureReason?) -> Unit)? = null,
    surfaceMirrorer: SurfaceMirrorer? = null,
    onGestureListener: GestureDetector.OnGestureListener? = rememberOnGestureListener(),
    onTouchEvent: ((e: MotionEvent, hitResult: HitResult?) -> Boolean)? = null,
    permissionHandler: ARPermissionHandler? = rememberActivityARPermissionHandler(),
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    cameraPermissionOverlay: (@Composable BoxScope.(ARCameraPermissionState) -> Unit)? = {
        ARCameraPermissionOverlay(it)
    },
    arCoreAvailabilityOverlay: (@Composable BoxScope.(ARCoreAvailabilityState) -> Unit)? = {
        ARCoreAvailabilityOverlay(it)
    },
    onARCoreAvailability: ((availability: ARCoreAvailability?) -> Unit)? = null,
    content: (@Composable ARSceneScope.() -> Unit)? = null
) = ARSceneView(
    modifier = modifier,
    surfaceType = surfaceType,
    engine = engine,
    modelLoader = modelLoader,
    materialLoader = materialLoader,
    environmentLoader = environmentLoader,
    sessionFeatures = sessionFeatures,
    playbackDataset = playbackDataset,
    playbackDatasetUri = playbackDatasetUri,
    sessionCameraConfig = sessionCameraConfig,
    flashMode = flashMode,
    planeFindingMode = planeFindingMode,
    depthMode = depthMode,
    instantPlacementMode = instantPlacementMode,
    geospatialMode = geospatialMode,
    streetscapeGeometryMode = streetscapeGeometryMode,
    cloudAnchorMode = cloudAnchorMode,
    augmentedFaceMode = augmentedFaceMode,
    imageStabilizationMode = imageStabilizationMode,
    semanticMode = semanticMode,
    updateMode = updateMode,
    focusMode = focusMode,
    sessionConfiguration = sessionConfiguration,
    planeRenderer = planeRenderer,
    planeRendererVersion = planeRendererVersion,
    sceneUnderstanding = sceneUnderstanding,
    cameraStream = cameraStream,
    view = view,
    renderQuality = renderQuality,
    isOpaque = isOpaque,
    renderer = renderer,
    scene = scene,
    environment = environment,
    mainLightNode = mainLightNode,
    fillLightNode = fillLightNode,
    cameraNode = cameraNode,
    cameraExposure = cameraExposure,
    collisionSystem = collisionSystem,
    viewNodeWindowManager = viewNodeWindowManager,
    onSessionCreated = onSessionCreated,
    onSessionResumed = onSessionResumed,
    onSessionPaused = onSessionPaused,
    onSessionFailed = onSessionFailed,
    onSessionFailure = onSessionFailure,
    onPlaybackFailed = onPlaybackFailed,
    onConfigDowngraded = onConfigDowngraded,
    onSessionUpdated = onSessionUpdated,
    onTrackingFailureChanged = onTrackingFailureChanged,
    surfaceMirrorer = surfaceMirrorer,
    onGestureListener = onGestureListener,
    onTouchEvent = onTouchEvent,
    permissionHandler = permissionHandler,
    lifecycle = lifecycle,
    cameraPermissionOverlay = cameraPermissionOverlay,
    arCoreAvailabilityOverlay = arCoreAvailabilityOverlay,
    onARCoreAvailability = onARCoreAvailability,
    onCameraPermissionStateChanged = null,
    content = content,
)
