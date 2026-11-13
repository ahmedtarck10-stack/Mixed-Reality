package com.aistudio.mixedreality.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

import kotlinx.coroutines.delay

/**
 * Manages device flashlight (torch) for low-light mixed reality and 3D object inspection.
 * Supports observing external Control Center / Quick Settings torch toggles via TorchCallback.
 */
object FlashlightHelper {
    private const val TAG = "FlashlightHelper"
    @Volatile
    var isTorchOn: Boolean = false
        private set

    private fun findTorchCameraId(cameraManager: CameraManager): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun isFlashAvailable(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            findTorchCameraId(cameraManager) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Observes system Control Center / Quick Settings torch changes for the back flash camera.
     * Ignores onTorchModeUnavailable so holding the camera in AR/MR preserves the active torch state.
     * Returns an unregister callback that safely cleans up the TorchCallback.
     */
    fun observeTorchState(
        context: Context,
        onTorchChanged: (Boolean) -> Unit
    ): () -> Unit {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return {}
            val targetCameraId = findTorchCameraId(cameraManager) ?: return {}
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == targetCameraId) {
                        isTorchOn = enabled
                        onTorchChanged(enabled)
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    // Do not turn off external state when AR/MR camera hardware claims the camera.
                }
            }
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            val unregister: () -> Unit = {
                runCatching { cameraManager.unregisterTorchCallback(callback) }
            }
            unregister
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to register torch observer: ${e.message}")
            val noop: () -> Unit = {}
            noop
        }
    }

    fun setTorchMode(context: Context, enabled: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraId = findTorchCameraId(cameraManager) ?: return false
            cameraManager.setTorchMode(cameraId, enabled)
            isTorchOn = enabled
            true
        } catch (e: Throwable) {
            if (!enabled) isTorchOn = false
            Log.w(TAG, "Failed to toggle torch mode ($enabled): ${e.message}")
            false
        }
    }

    /**
     * Retries setting torch mode to handle transient CAMERA_IN_USE states right after closing
     * an ARCore or Camera2 session (~350ms asynchronous camera teardown).
     */
    suspend fun setTorchModeWithRetry(
        context: Context,
        enabled: Boolean,
        maxAttempts: Int = 8,
        retryDelayMs: Long = 120L
    ): Boolean {
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            if (setTorchMode(context, enabled)) {
                return true
            }
            if (attempt < maxAttempts - 1) {
                delay(retryDelayMs)
            }
        }
        return false
    }
}
