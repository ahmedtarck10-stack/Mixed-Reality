package com.aistudio.mixedreality.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import io.github.sceneview.utils.SurfaceMirrorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoSessionRecorder(private val context: Context) {
    companion object {
        private const val TAG = "VideoSessionRecorder"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var mirroredSurface: Surface? = null
    private var boundMirrorer: SurfaceMirrorer? = null
    private var isRecording = false

    // Frame capture fallback
    private val capturedBitmaps = mutableListOf<Bitmap>()
    private var captureJob: Job? = null

    fun startRecording(
        activity: Activity?,
        surfaceMirrorer: SurfaceMirrorer?,
        scope: CoroutineScope
    ): Boolean {
        if (isRecording) return true

        capturedBitmaps.clear()
        val tempFile = File(context.cacheDir, "temp_mr_recording_${System.currentTimeMillis()}.mp4")
        recordingFile = tempFile

        if (surfaceMirrorer != null) {
            try {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                recorder.setVideoSize(720, 1280)
                recorder.setVideoFrameRate(30)
                recorder.setVideoEncodingBitRate(4_000_000)
                recorder.setOutputFile(tempFile.absolutePath)
                recorder.prepare()

                val surface = recorder.surface
                recorder.start()
                surfaceMirrorer.startMirroring(surface)

                mediaRecorder = recorder
                mirroredSurface = surface
                boundMirrorer = surfaceMirrorer
                Log.d(TAG, "Hardware MediaRecorder started with SurfaceMirrorer")
            } catch (e: Throwable) {
                Log.w(TAG, "MediaRecorder failed to initialize: ${e.message}, falling back to frame recorder")
                mediaRecorder?.release()
                mediaRecorder = null
            }
        }

        // Periodic snapshot collector for rock-solid video generation
        if (activity != null) {
            captureJob = scope.launch(Dispatchers.Main) {
                while (isActive) {
                    MediaManager.captureWindowBitmap(activity) { bmp ->
                        try {
                            if (capturedBitmaps.size < 60) { // Capped at 60 frames to safeguard memory
                                val scaled = Bitmap.createScaledBitmap(bmp, 540, 960, true)
                                val rgb565 = if (scaled.config != Bitmap.Config.RGB_565) {
                                    val copy = scaled.copy(Bitmap.Config.RGB_565, false)
                                    if (copy != null) {
                                        scaled.recycle()
                                        copy
                                    } else scaled
                                } else scaled
                                capturedBitmaps.add(rgb565)
                            }
                        } finally {
                            bmp.recycle()
                        }
                    }
                    delay(200L) // ~5 frames per second snapshot capture
                }
            }
        }

        isRecording = true
        return true
    }

    suspend fun stopRecording(): Uri? = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext null
        isRecording = false

        captureJob?.cancel()
        captureJob = null

        var videoUri: Uri? = null

        try {
            // 1. If hardware mediaRecorder was running, finalize it
            val recorder = mediaRecorder
            val surface = mirroredSurface
            val mirrorer = boundMirrorer

            if (recorder != null) {
                try {
                    if (mirrorer != null && surface != null) {
                        mirrorer.stopMirroring(surface)
                    }
                    recorder.stop()
                    recorder.release()
                    Log.d(TAG, "MediaRecorder stopped successfully")
                } catch (e: Throwable) {
                    Log.w(TAG, "Error stopping MediaRecorder: ${e.message}")
                } finally {
                    mediaRecorder = null
                    mirroredSurface = null
                    boundMirrorer = null
                }
            }

            val targetFile = recordingFile
            if (targetFile != null && targetFile.exists() && targetFile.length() > 1024L) {
                videoUri = MediaManager.saveVideoToGallery(context, targetFile)
            }

            // 2. If hardware file was empty or missing, encode captured frames into valid MP4
            if (videoUri == null && capturedBitmaps.isNotEmpty()) {
                try {
                    val fallbackFile = File(context.cacheDir, "mr_video_fallback_${System.currentTimeMillis()}.mp4")
                    val success = encodeBitmapsToMp4(capturedBitmaps, fallbackFile, 540, 960)
                    if (success && fallbackFile.exists() && fallbackFile.length() > 0) {
                        videoUri = MediaManager.saveVideoToGallery(context, fallbackFile)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Fallback frame encoding failed", e)
                }
            }
        } finally {
            // Strictly guarantee memory cleanup even if errors occur during encoding or saving
            capturedBitmaps.forEach {
                try {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                } catch (_: Exception) {}
            }
            capturedBitmaps.clear()
        }

        videoUri
    }

    /**
     * Encodes a list of Bitmaps into an H.264 MP4 file using Android MediaCodec + MediaMuxer.
     */
    private fun encodeBitmapsToMp4(
        bitmaps: List<Bitmap>,
        outputFile: File,
        width: Int,
        height: Int
    ): Boolean {
        if (bitmaps.isEmpty()) return false

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 15)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / 15L
            var presentationTimeUs = 0L

            for (i in 0 until (bitmaps.size * 3)) { // duplicate frames to reach ~15fps
                val bmp = bitmaps[i % bitmaps.size]
                val yuv = bitmapToYuv420(bmp, width, height)

                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(yuv)
                    codec.queueInputBuffer(inputIndex, 0, yuv.size, presentationTimeUs, 0)
                    presentationTimeUs += frameDurationUs
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            // End of stream
            val eosIndex = codec.dequeueInputBuffer(10_000L)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(eosIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }

            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Error encoding frames to MP4: ${e.message}", e)
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val safeBmp = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else null
        val target = safeBmp ?: bitmap

        val argb = IntArray(width * height)
        target.getPixels(argb, 0, width, 0, 0, width, height)
        safeBmp?.recycle()
        val yuv = ByteArray(width * height * 3 / 2)

        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
