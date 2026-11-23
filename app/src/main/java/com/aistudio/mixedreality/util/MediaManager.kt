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
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open image MediaStore stream")
            stream.use { outputStream ->
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

        val location = IntArray(2)
        decorView.getLocationInWindow(location)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    window,
                    Rect(location[0], location[1], location[0] + width, location[1] + height),
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onCaptured(bitmap)
                        } else {
                            if (!bitmap.isRecycled) {
                                bitmap.recycle()
                            }
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
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                val fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(fallback)
                decorView.draw(canvas)
                onCaptured(fallback)
            }
        } else {
            val fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fallback)
            decorView.draw(canvas)
            onCaptured(fallback)
        }
    }

    /**
     * Captures the composited activity window (SurfaceView/ARSceneView/PreviewView/TextureView/UI)
     * directly into a caller-owned [destBitmap], letting PixelCopy perform hardware scaling on GPU.
     */
    fun captureWindowIntoBitmap(
        activity: Activity,
        destBitmap: Bitmap,
        callbackHandler: Handler,
        onFinished: (Boolean) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed || destBitmap.isRecycled) {
            onFinished(false)
            return
        }

        val window = activity.window
        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            onFinished(false)
            return
        }

        val srcRect = Rect(0, 0, width, height)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PixelCopy.request(
                    window,
                    srcRect,
                    destBitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onFinished(true)
                        } else {
                            drawScaledDecorViewFallback(decorView, destBitmap, callbackHandler, onFinished)
                        }
                    },
                    callbackHandler
                )
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Window PixelCopy request failed: ${e.message}")
            }
        }

        drawScaledDecorViewFallback(decorView, destBitmap, callbackHandler, onFinished)
    }

    private fun drawScaledDecorViewFallback(
        decorView: android.view.View,
        destBitmap: Bitmap,
        callbackHandler: Handler,
        onFinished: (Boolean) -> Unit
    ) {
        decorView.post {
            val drawn = try {
                if (destBitmap.isRecycled || decorView.width <= 0 || decorView.height <= 0) {
                    false
                } else {
                    val canvas = Canvas(destBitmap)
                    val sx = destBitmap.width.toFloat() / decorView.width.toFloat()
                    val sy = destBitmap.height.toFloat() / decorView.height.toFloat()
                    canvas.scale(sx, sy)
                    decorView.draw(canvas)
                    true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Canvas fallback draw failed: ${e.message}")
                false
            }
            callbackHandler.post { onFinished(drawn) }
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

        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs))
        val displayName = "MR_Record_$timeStamp.mp4"
        val title = displayName.substringBeforeLast('.')
        val durationMs = extractVideoDurationMs(videoFile)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.TITLE, title)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, nowSec)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
            if (durationMs > 0L) {
                put(MediaStore.Video.Media.DURATION, durationMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MixedReality")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open video MediaStore stream")
            stream.use { outputStream ->
                videoFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                // Legacy API 24-28: scan the resolved filesystem path (never raw content:// URI)
                val dataPath = runCatching {
                    resolver.query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
                if (!dataPath.isNullOrBlank()) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(dataPath),
                        arrayOf("video/mp4"),
                        null
                    )
                }
            }
            Log.d(TAG, "Saved playable video to gallery (${videoFile.length()} bytes, ${durationMs}ms): $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving video to gallery", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun extractVideoDurationMs(videoFile: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
