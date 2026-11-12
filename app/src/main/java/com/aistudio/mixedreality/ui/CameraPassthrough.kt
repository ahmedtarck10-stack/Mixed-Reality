package com.aistudio.mixedreality.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private const val TAG = "CameraPassthrough"

/**
 * High-performance, zero-latency Camera2 passthrough background for Mixed Reality mode.
 * Streams the device's live back-facing camera feed onto a hardware-accelerated TextureView.
 * Gracefully falls back to a simulated ambient spatial background if camera is not available.
 */
@Composable
fun CameraPassthroughBackground(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }

    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = (event == Lifecycle.Event.ON_RESUME)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Fallback spatial background gradient in case camera is initializing or running in emulator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F141C),
                            Color(0xFF07090D),
                            Color(0xFF020305)
                        )
                    )
                )
        )

        if (hasCameraPermission && isResumed) {
            AndroidView(
                factory = { ctx ->
                    CameraPassthroughTextureView(ctx)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Hardware TextureView running a direct Camera2 preview stream.
 */
class CameraPassthroughTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: android.os.HandlerThread? = null
    private var backgroundHandler: Handler? = null

    init {
        surfaceTextureListener = this
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = android.os.HandlerThread("CameraPassthroughBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join(400)
        } catch (_: InterruptedException) {
        } finally {
            backgroundThread = null
            backgroundHandler = null
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startBackgroundThread()
        openBackCamera(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        stopBackgroundThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCamera()
        stopBackgroundThread()
    }

    @SuppressLint("MissingPermission")
    private fun openBackCamera(surfaceTexture: SurfaceTexture) {
        try {
            stopCamera()
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return

            val handler = backgroundHandler ?: Handler(Looper.getMainLooper())

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreviewSession(camera, surfaceTexture)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "Camera device error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, handler)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open camera for passthrough: ${e.message}")
        }
    }

    private fun startPreviewSession(camera: CameraDevice, surfaceTexture: SurfaceTexture) {
        try {
            surfaceTexture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(surfaceTexture)
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            val handler = backgroundHandler ?: Handler(Looper.getMainLooper())

            camera.createCaptureSession(
                listOf(previewSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed repeating request: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.w(TAG, "Camera capture session configuration failed")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start preview session: ${e.message}")
        }
    }

    private fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing camera: ${e.message}")
        }
    }
}
