package com.aistudio.mixedreality.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

import kotlinx.coroutines.delay

/**
 * Manages device flashlight (torch) for low-light mixed reality and 3D object inspection.
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
