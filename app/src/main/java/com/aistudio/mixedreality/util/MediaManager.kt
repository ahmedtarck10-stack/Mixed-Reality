package com.aistudio.mixedreality.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaManager {
    private const val TAG = "MediaManager"

    /**
     * Saves a snapshot Bitmap to Android MediaStore Images (Gallery).
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "MR_Photo_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MixedReality")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.toString()),
                arrayOf("image/jpeg"),
                null
            )
            Log.d(TAG, "Saved photo to gallery: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving bitmap to gallery", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Captures the current activity window decorView to a Bitmap via PixelCopy,
     * falling back to Canvas draw if PixelCopy is unavailable.
     */
    fun captureWindowBitmap(activity: Activity, onCaptured: (Bitmap) -> Unit) {
        val window = activity.window
        val decorView = window.decorView
        val width = decorView.width.coerceAtLeast(1)
        val height = decorView.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        decorView.getLocationInWindow(location)

        try {
            PixelCopy.request(
                window,
                Rect(location[0], location[1], location[0] + width, location[1] + height),
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        onCaptured(bitmap)
                    } else {
                        // Fallback to Canvas draw
                        val fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(fallback)
                        decorView.draw(canvas)
                        onCaptured(fallback)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Throwable) {
            Log.w(TAG, "PixelCopy failed, using canvas fallback: ${e.message}")
            val fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fallback)
            decorView.draw(canvas)
            onCaptured(fallback)
        }
    }

    /**
     * Saves an MP4 video file to Android MediaStore Videos (Gallery / Movies).
     */
    fun saveVideoToGallery(context: Context, videoFile: File): Uri? {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "Video file does not exist or is empty: ${videoFile.absolutePath}")
            return null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "MR_Record_$timeStamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MixedReality")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                videoFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.toString()),
                arrayOf("video/mp4"),
                null
            )
            Log.d(TAG, "Saved video to gallery: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving video to gallery", e)
            resolver.delete(uri, null, null)
            null
        }
    }
}
