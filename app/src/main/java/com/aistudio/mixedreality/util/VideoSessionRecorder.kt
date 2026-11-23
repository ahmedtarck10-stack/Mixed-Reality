package com.aistudio.mixedreality.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import io.github.sceneview.utils.SurfaceMirrorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Records the live composited viewport across Object, AR, and MR (dual stereo) modes
 * using hardware-scaled window capture into a streaming H.264 MediaCodec + MediaMuxer pipeline.
 *
 * Unlike SurfaceMirrorer-based recording, window-level PixelCopy matches the exact visual output
 * of Photo capture (camera passthrough + AR anchors + 3D models + stereo split) without conflicting
 * with MRStereoSplitView's Left->Right eye SurfaceMirrorer.
 */
class VideoSessionRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VideoSessionRecorder"
        private const val MAX_VIDEO_EDGE_PX = 1280
        private const val TARGET_FRAME_RATE = 24
        private const val CAPTURE_INTERVAL_MS = 45L // ~22 fps smoothed pacing
        private const val VIDEO_BITRATE = 4_000_000
        private const val MIN_VALID_MP4_BYTES = 1024L

        /**
         * Computes 16-pixel macroblock-aligned encoder dimensions preserving the input aspect ratio
         * and orientation (portrait for Object/AR, landscape for MR stereo split or rotated devices).
         */
        fun computeAlignedVideoDimensions(
            viewWidth: Int,
            viewHeight: Int,
            maxEdge: Int = MAX_VIDEO_EDGE_PX
        ): Pair<Int, Int> {
            val alignedMax = ((maxEdge / 16) * 16).coerceAtLeast(256)
            if (viewWidth <= 0 || viewHeight <= 0) {
                return 720 to 1280.coerceAtMost(alignedMax)
            }

            val longest = max(viewWidth, viewHeight)
            val scale = if (longest > alignedMax) alignedMax.toFloat() / longest.toFloat() else 1f
            val rawW = (viewWidth * scale).roundToInt()
            val rawH = (viewHeight * scale).roundToInt()

            var alignedW = (((rawW + 8) / 16) * 16).coerceIn(256, alignedMax)
            var alignedH = (((rawH + 8) / 16) * 16).coerceIn(256, alignedMax)

            // Preserve dominant orientation explicitly on extreme ratios
            if (viewWidth > viewHeight && alignedW < alignedH) {
                val tmp = alignedW
                alignedW = alignedH
                alignedH = tmp
            } else if (viewHeight > viewWidth && alignedH < alignedW) {
                val tmp = alignedW
                alignedW = alignedH
                alignedH = tmp
            }
            return alignedW to alignedH
        }

        /**
         * Encodes ARGB pixels into planar/semi-planar YUV420 buffers honoring exact Android
         * Image.Plane rowStride and pixelStride values (supports I420, NV12, NV21).
         */
        fun writeArgbToYuvPlanes(
            argb: IntArray,
            width: Int,
            height: Int,
            yBuffer: ByteBuffer,
            yRowStride: Int,
            yPixelStride: Int,
            uBuffer: ByteBuffer,
            vBuffer: ByteBuffer,
            uvRowStride: Int,
            uvPixelStride: Int
        ) {
            val expectedPixels = width * height
            if (argb.size < expectedPixels) return

            for (row in 0 until height) {
                val yRowBase = row * yRowStride
                val argbRowBase = row * width
                for (col in 0 until width) {
                    val c = argb[argbRowBase + col]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF

                    val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)
                    val yOffset = yRowBase + col * yPixelStride
                    if (yOffset < yBuffer.limit()) {
                        yBuffer.put(yOffset, yVal.toByte())
                    }

                    if ((row and 1) == 0 && (col and 1) == 0) {
                        val uvRow = row ushr 1
                        val uvCol = col ushr 1
                        val uvOffset = uvRow * uvRowStride + uvCol * uvPixelStride

                        val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240)
                        val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240)

                        if (uvOffset < uBuffer.limit()) {
                            uBuffer.put(uvOffset, uVal.toByte())
                        }
                        if (uvOffset < vBuffer.limit()) {
                            vBuffer.put(uvOffset, vVal.toByte())
                        }
                    }
                }
            }
        }

        /**
         * Verifies that [file] is a finalized ISO BMFF / MP4 container containing both
         * an 'ftyp' header box, non-empty 'mdat' media payload, and a 'moov' index atom.
         * Without 'moov' (written only by MediaMuxer.stop()), Gallery players cannot open the video.
         */
        fun hasPlayableMp4Structure(file: File): Boolean {
            if (!file.exists() || file.length() < 32L) return false
            var hasFtyp = false
            var hasMoov = false
            var hasMdatData = false
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    val fileLen = raf.length()
                    val header = ByteArray(8)
                    val extSizeBytes = ByteArray(8)
                    var offset = 0L
                    var boxCount = 0
                    while (offset + 8L <= fileLen && boxCount < 256) {
                        raf.seek(offset)
                        if (raf.read(header) != 8) break
                        val boxSize32 = ((header[0].toLong() and 0xFFL) shl 24) or
                            ((header[1].toLong() and 0xFFL) shl 16) or
                            ((header[2].toLong() and 0xFFL) shl 8) or
                            (header[3].toLong() and 0xFFL)
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        var headerLen = 8L
                        var boxSize = when {
                            boxSize32 == 0L -> fileLen - offset
                            boxSize32 == 1L -> {
                                if (offset + 16L > fileLen || raf.read(extSizeBytes) != 8) break
                                headerLen = 16L
                                ((extSizeBytes[0].toLong() and 0xFFL) shl 56) or
                                    ((extSizeBytes[1].toLong() and 0xFFL) shl 48) or
                                    ((extSizeBytes[2].toLong() and 0xFFL) shl 40) or
                                    ((extSizeBytes[3].toLong() and 0xFFL) shl 32) or
                                    ((extSizeBytes[4].toLong() and 0xFFL) shl 24) or
                                    ((extSizeBytes[5].toLong() and 0xFFL) shl 16) or
                                    ((extSizeBytes[6].toLong() and 0xFFL) shl 8) or
                                    (extSizeBytes[7].toLong() and 0xFFL)
                            }
                            else -> boxSize32
                        }
                        if (boxSize < headerLen || offset + boxSize > fileLen) break
                        when (boxType) {
                            "ftyp" -> hasFtyp = true
                            "moov" -> if (boxSize > headerLen + 8L) hasMoov = true
                            "mdat" -> if (boxSize > headerLen + 16L) hasMdatData = true
                        }
                        offset += boxSize
                        boxCount++
                    }
                }
                hasFtyp && hasMoov && hasMdatData
            } catch (_: Throwable) {
                false
            }
        }
    }

    private val stateLock = Any()
    private val stopMutex = Mutex()
    private val isActiveCapture = AtomicBoolean(false)

    @Volatile
    private var activeSession: StreamingSession? = null

    fun isRecording(): Boolean = isActiveCapture.get()

    @Suppress("UNUSED_PARAMETER")
    fun startRecording(
        activity: Activity?,
        surfaceMirrorer: SurfaceMirrorer? = null,
        scope: CoroutineScope? = null
    ): Boolean {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot start recording without a live Activity")
            return false
        }

        synchronized(stateLock) {
            if (isActiveCapture.get() || activeSession != null) {
                return true
            }

            val decorView = activity.window?.decorView
            val rawW = decorView?.width?.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.widthPixels
            val rawH = decorView?.height?.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.heightPixels

            val (videoWidth, videoHeight) = computeAlignedVideoDimensions(rawW, rawH)
            val tempFile = File(context.cacheDir, "temp_mr_recording_${System.currentTimeMillis()}.mp4")

            val session = createStreamingSession(activity, tempFile, videoWidth, videoHeight)
            if (session == null) {
                tempFile.delete()
                return false
            }

            activeSession = session
            isActiveCapture.set(true)
            session.start()
            Log.d(TAG, "Started window video recording (${videoWidth}x${videoHeight}) -> ${tempFile.name}")
            return true
        }
    }

    suspend fun stopRecording(): Uri? = stopMutex.withLock {
        val session: StreamingSession?
        synchronized(stateLock) {
            if (!isActiveCapture.getAndSet(false) && activeSession == null) {
                return@withLock null
            }
            session = activeSession
            activeSession = null
        }

        if (session == null) return@withLock null

        return@withLock withContext(Dispatchers.IO) {
            val outputFile = session.outputFile
            var savedUri: Uri? = null
            try {
                val validMp4 = session.stopAndFinalize()
                if (validMp4 && outputFile.exists() && outputFile.length() > MIN_VALID_MP4_BYTES) {
                    savedUri = MediaManager.saveVideoToGallery(context, outputFile)
                } else {
                    Log.w(
                        TAG,
                        "Finalized recording had insufficient data (valid=$validMp4, bytes=${outputFile.length()})"
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed finalizing recording", t)
            } finally {
                session.releaseResources()
                try {
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                } catch (_: Throwable) {}
            }
            savedUri
        }
    }

    /**
     * Immediately aborts and cleans up any active recording session without saving to Gallery.
     * Used during lifecycle disposal if the user leaves the screen while recording.
     */
    fun cancelAndRelease() {
        val session: StreamingSession?
        synchronized(stateLock) {
            isActiveCapture.set(false)
            session = activeSession
            activeSession = null
        }
        session?.abort()
    }

    private fun createStreamingSession(
        activity: Activity,
        outputFile: File,
        width: Int,
        height: Int
    ): StreamingSession? {
        // Primary path: Hardware-accelerated Surface encoder
        try {
            return StreamingSession(
                activity = activity,
                outputFile = outputFile,
                width = width,
                height = height,
                useInputSurface = true,
                isActiveCapture = isActiveCapture
            )
        } catch (surfaceErr: Throwable) {
            Log.w(TAG, "Surface MediaCodec unavailable (${surfaceErr.message}); trying YUV420Flexible")
        }

        // Secondary fallback path: YUV420Flexible streaming Image-plane encoder
        return try {
            StreamingSession(
                activity = activity,
                outputFile = outputFile,
                width = width,
                height = height,
                useInputSurface = false,
                isActiveCapture = isActiveCapture
            )
        } catch (yuvErr: Throwable) {
            Log.e(TAG, "Failed creating H.264 MediaCodec session", yuvErr)
            null
        }
    }

    private class StreamingSession(
        private val activity: Activity,
        val outputFile: File,
        private val width: Int,
        private val height: Int,
        private val useInputSurface: Boolean,
        private val isActiveCapture: AtomicBoolean
    ) {
        private val codec: MediaCodec
        private val muxer: MediaMuxer
        private var inputSurface: Surface? = null

        private val captureThread = HandlerThread("MRVideoCapture").apply { start() }
        private val captureHandler = Handler(captureThread.looper)

        private val frameBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val targetRect = Rect(0, 0, width, height)
        private val argbScratch: IntArray? = if (useInputSurface) null else IntArray(width * height)

        private val submittedFrames = AtomicInteger(0)
        private val writtenSamples = AtomicInteger(0)
        private val inFlightCapture = AtomicBoolean(false)
        private val encoderStopping = AtomicBoolean(false)
        private val abortRequested = AtomicBoolean(false)
        private val resourcesReleased = AtomicBoolean(false)
        private val muxerStopped = AtomicBoolean(false)
        private val drainDoneLatch = CountDownLatch(1)

        @Volatile
        private var muxerStarted = false

        @Volatile
        private var trackIndex = -1

        @Volatile
        private var stopRequestedNs = 0L

        private var recordingStartNs = 0L
        private var lastSubmittedPtsUs = -1L
        private var hasCapturedAnyFrame = false

        private val drainThread: Thread

        init {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    if (useInputSurface) {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    } else {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    }
                )
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            var createdCodec: MediaCodec? = null
            var createdMuxer: MediaMuxer? = null
            var createdSurface: Surface? = null
            try {
                createdCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                createdCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                if (useInputSurface) {
                    createdSurface = createdCodec.createInputSurface()
                }
                createdCodec.start()
                createdMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (t: Throwable) {
                try {
                    createdSurface?.release()
                } catch (_: Throwable) {}
                try {
                    createdCodec?.release()
                } catch (_: Throwable) {}
                try {
                    createdMuxer?.release()
                } catch (_: Throwable) {}
                if (!frameBitmap.isRecycled) frameBitmap.recycle()
                captureThread.quitSafely()
                throw t
            }

            codec = createdCodec
            muxer = createdMuxer
            inputSurface = createdSurface
            drainThread = Thread(::drainEncoderLoop, "MRVideoMuxerDrain")
        }

        fun start() {
            recordingStartNs = System.nanoTime()
            drainThread.start()
            captureHandler.post(::captureNextFrame)
        }

        private fun captureNextFrame() {
            if (!isActiveCapture.get() || encoderStopping.get()) return
            if (!inFlightCapture.compareAndSet(false, true)) return

            val captureStartMs = android.os.SystemClock.elapsedRealtime()
            MediaManager.captureWindowIntoBitmap(activity, frameBitmap, captureHandler) { success ->
                try {
                    if (success && isActiveCapture.get() && !encoderStopping.get()) {
                        hasCapturedAnyFrame = true
                        submitCurrentBitmapFrame()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Frame submit error: ${t.message}")
                } finally {
                    inFlightCapture.set(false)
                }

                if (isActiveCapture.get() && !encoderStopping.get()) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - captureStartMs
                    val nextDelay = (CAPTURE_INTERVAL_MS - elapsed).coerceAtLeast(4L)
                    captureHandler.postDelayed(::captureNextFrame, nextDelay)
                }
            }
        }

        private fun submitCurrentBitmapFrame() {
            if (frameBitmap.isRecycled) return
            val elapsedUs = ((System.nanoTime() - recordingStartNs) / 1_000L).coerceAtLeast(0L)
            val ptsUs = max(elapsedUs, lastSubmittedPtsUs + 1_000L)
            lastSubmittedPtsUs = ptsUs

            if (useInputSurface) {
                val surface = inputSurface ?: return
                val canvas = lockBestCanvas(surface)
                try {
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(frameBitmap, null, targetRect, paint)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                submittedFrames.incrementAndGet()
            } else {
                queueYuvInputFrame(ptsUs, 0)
            }
        }

        private fun lockBestCanvas(surface: Surface): android.graphics.Canvas {
            // Use standard CPU-backed Surface lock for MediaCodec.createInputSurface():
            // HWUI lockHardwareCanvas conflicts with GRALLOC_USAGE_HW_VIDEO_ENCODER on several OEM GPUs
            // and can emit blank or corrupted frames.
            return surface.lockCanvas(null)
        }

        private fun queueYuvInputFrame(ptsUs: Long, flags: Int): Boolean {
            val inputIndex = codec.dequeueInputBuffer(15_000L)
            if (inputIndex < 0) return false

            if ((flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, flags)
                return true
            }

            val scratch = argbScratch ?: return false
            frameBitmap.getPixels(scratch, 0, width, 0, 0, width, height)

            val image = try {
                codec.getInputImage(inputIndex)
            } catch (_: Throwable) {
                null
            }

            val rawInputCap = try {
                codec.getInputBuffer(inputIndex)?.capacity()
            } catch (_: Throwable) {
                null
            }

            if (image != null && image.planes.size >= 3) {
                try {
                    val yPlane = image.planes[0]
                    val uPlane = image.planes[1]
                    val vPlane = image.planes[2]
                    writeArgbToYuvPlanes(
                        argb = scratch,
                        width = width,
                        height = height,
                        yBuffer = yPlane.buffer,
                        yRowStride = yPlane.rowStride,
                        yPixelStride = yPlane.pixelStride,
                        uBuffer = uPlane.buffer,
                        vBuffer = vPlane.buffer,
                        uvRowStride = uPlane.rowStride,
                        uvPixelStride = uPlane.pixelStride
                    )
                    val expectedStridedSize = (yPlane.rowStride * height) +
                        (uPlane.rowStride * (height / 2)) * if (uPlane.pixelStride == 1) 2 else 1
                    val bufferSize = rawInputCap ?: expectedStridedSize.coerceAtLeast(width * height * 3 / 2)
                    codec.queueInputBuffer(inputIndex, 0, bufferSize, ptsUs, 0)
                    submittedFrames.incrementAndGet()
                    return true
                } finally {
                    image.close()
                }
            } else {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return false
                inputBuffer.clear()
                val nv12 = encodePackedNv12(scratch, width, height)
                val bytesToWrite = nv12.size.coerceAtMost(inputBuffer.remaining())
                inputBuffer.put(nv12, 0, bytesToWrite)
                codec.queueInputBuffer(inputIndex, 0, bytesToWrite, ptsUs, 0)
                submittedFrames.incrementAndGet()
                return true
            }
        }

        private fun encodePackedNv12(argb: IntArray, w: Int, h: Int): ByteArray {
            val out = ByteArray(w * h * 3 / 2)
            var yIdx = 0
            var uvIdx = w * h
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val c = argb[row * w + col]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)
                    out[yIdx++] = y.toByte()
                    if ((row and 1) == 0 && (col and 1) == 0) {
                        val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240)
                        val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240)
                        if (uvIdx + 1 < out.size) {
                            out[uvIdx++] = u.toByte()
                            out[uvIdx++] = v.toByte()
                        }
                    }
                }
            }
            return out
        }

        private fun drainEncoderLoop() {
            val bufferInfo = MediaCodec.BufferInfo()
            var firstSrcPtsUs = -1L
            var lastMuxerPtsUs = -1L
            try {
                while (!abortRequested.get()) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 20_000L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (abortRequested.get()) break
                            if (encoderStopping.get() &&
                                stopRequestedNs > 0L &&
                                (System.nanoTime() - stopRequestedNs) > 3_500_000_000L
                            ) {
                                break
                            }
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outputIndex >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outputIndex)
                            if (encodedData != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                if (!muxerStarted) {
                                    trackIndex = muxer.addTrack(codec.outputFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
                                if (firstSrcPtsUs < 0L) {
                                    firstSrcPtsUs = bufferInfo.presentationTimeUs
                                }
                                var normalizedPtsUs = (bufferInfo.presentationTimeUs - firstSrcPtsUs).coerceAtLeast(0L)
                                if (normalizedPtsUs <= lastMuxerPtsUs) {
                                    normalizedPtsUs = lastMuxerPtsUs + 33_333L
                                }
                                lastMuxerPtsUs = normalizedPtsUs
                                bufferInfo.presentationTimeUs = normalizedPtsUs

                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                writtenSamples.incrementAndGet()
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (!encoderStopping.get() && !abortRequested.get()) {
                    Log.e(TAG, "Muxer drain failed", t)
                }
            } finally {
                drainDoneLatch.countDown()
            }
        }

        fun stopAndFinalize(): Boolean {
            captureHandler.removeCallbacksAndMessages(null)

            // Wait briefly if an asynchronous PixelCopy callback is mid-execution
            val waitDeadline = android.os.SystemClock.elapsedRealtime() + 500L
            while (inFlightCapture.get() && android.os.SystemClock.elapsedRealtime() < waitDeadline) {
                Thread.sleep(10L)
            }

            // Ensure short taps still yield >= 3 frames so MP4 track has positive playable duration
            val flushLatch = CountDownLatch(1)
            captureHandler.post {
                try {
                    if (hasCapturedAnyFrame) {
                        while (submittedFrames.get() < 3) {
                            Thread.sleep(36L)
                            submitCurrentBitmapFrame()
                        }
                    }
                    stopRequestedNs = System.nanoTime()
                    encoderStopping.set(true)
                    if (useInputSurface) {
                        codec.signalEndOfInputStream()
                    } else {
                        val eosPts = max(
                            ((System.nanoTime() - recordingStartNs) / 1_000L).coerceAtLeast(0L),
                            lastSubmittedPtsUs + 33_333L
                        )
                        queueYuvInputFrame(eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "EOS signal failed: ${t.message}")
                } finally {
                    flushLatch.countDown()
                }
            }
            flushLatch.await(1_500L, TimeUnit.MILLISECONDS)

            drainDoneLatch.await(4_000L, TimeUnit.MILLISECONDS)

            // Finalize MediaCodec and MediaMuxer BEFORE copying the MP4 to MediaStore!
            // MediaMuxer only flushes the 'moov' atom during muxer.stop(); copying earlier produces an unplayable MP4.
            releaseResources()
            return muxerStarted && writtenSamples.get() > 0 && hasPlayableMp4Structure(outputFile)
        }

        fun abort() {
            abortRequested.set(true)
            stopRequestedNs = System.nanoTime()
            encoderStopping.set(true)
            captureHandler.removeCallbacksAndMessages(null)
            try {
                if (useInputSurface) codec.signalEndOfInputStream()
            } catch (_: Throwable) {}
            releaseResources()
            try {
                if (outputFile.exists()) outputFile.delete()
            } catch (_: Throwable) {}
        }

        fun releaseResources() {
            if (!resourcesReleased.compareAndSet(false, true)) return
            stopRequestedNs = if (stopRequestedNs == 0L) System.nanoTime() else stopRequestedNs
            encoderStopping.set(true)
            abortRequested.set(true)
            captureHandler.removeCallbacksAndMessages(null)
            try {
                drainThread.join(2_000L)
            } catch (_: Throwable) {}
            try {
                codec.stop()
            } catch (_: Throwable) {}
            try {
                codec.release()
            } catch (_: Throwable) {}
            try {
                inputSurface?.release()
            } catch (_: Throwable) {}
            inputSurface = null
            if (muxerStopped.compareAndSet(false, true)) {
                try {
                    if (muxerStarted) {
                        muxer.stop()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaMuxer stop failed: ${t.message}")
                }
                try {
                    muxer.release()
                } catch (_: Throwable) {}
            }
            try {
                captureThread.quitSafely()
            } catch (_: Throwable) {}
            try {
                if (!frameBitmap.isRecycled) {
                    frameBitmap.recycle()
                }
            } catch (_: Throwable) {}
        }
    }
}
