package com.aistudio.mixedreality

import com.aistudio.mixedreality.util.VideoSessionRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class VideoSessionRecorderTest {

    @Test
    fun alignedDimensionsPreservePortraitAndLandscapeAndMacroblockAlignment() {
        // Portrait Object / AR phone viewport
        val (pw, ph) = VideoSessionRecorder.computeAlignedVideoDimensions(1080, 2400)
        assertTrue("Portrait width must be < height", pw < ph)
        assertEquals(0, pw % 16)
        assertEquals(0, ph % 16)
        assertEquals(576, pw)
        assertEquals(1280, ph)

        // Landscape MR stereo split viewport
        val (lw, lh) = VideoSessionRecorder.computeAlignedVideoDimensions(2400, 1080)
        assertTrue("Landscape width must be > height", lw > lh)
        assertEquals(0, lw % 16)
        assertEquals(0, lh % 16)
        assertEquals(1280, lw)
        assertEquals(576, lh)

        // Standard 1080p vertical & horizontal
        assertEquals(720 to 1280, VideoSessionRecorder.computeAlignedVideoDimensions(1080, 1920))
        assertEquals(1280 to 720, VideoSessionRecorder.computeAlignedVideoDimensions(1920, 1080))
    }

    @Test
    fun alignedDimensionsHandleInvalidAndOddViewBoundsSafely() {
        val (dw, dh) = VideoSessionRecorder.computeAlignedVideoDimensions(0, 0)
        assertEquals(720, dw)
        assertEquals(1280, dh)
        assertEquals(0, dw % 16)
        assertEquals(0, dh % 16)

        val (ow, oh) = VideoSessionRecorder.computeAlignedVideoDimensions(1081, 2399)
        assertTrue(ow in 256..1280 && oh in 256..1280)
        assertEquals(0, ow % 16)
        assertEquals(0, oh % 16)
        assertTrue(ow < oh)

        val (sw, sh) = VideoSessionRecorder.computeAlignedVideoDimensions(410, 730)
        assertTrue(sw <= 416 && sh <= 736)
        assertEquals(0, sw % 16)
        assertEquals(0, sh % 16)
    }

    @Test
    fun writeArgbToYuvPlanesSupportsStridedPlanarAndSemiPlanarLayouts() {
        val w = 16
        val h = 16
        val argb = IntArray(w * h)
        // Top half pure red (FF FF0000), bottom half pure blue (FF 0000FF)
        for (row in 0 until h) {
            val pixel = if (row < h / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
            for (col in 0 until w) {
                argb[row * w + col] = pixel
            }
        }

        // Case 1: Planar I420 with padded rowStride = 24
        val yRowStride = 24
        val uvRowStride = 12
        val yBuf = ByteBuffer.allocate(yRowStride * h)
        val uBuf = ByteBuffer.allocate(uvRowStride * (h / 2))
        val vBuf = ByteBuffer.allocate(uvRowStride * (h / 2))

        VideoSessionRecorder.writeArgbToYuvPlanes(
            argb = argb,
            width = w,
            height = h,
            yBuffer = yBuf,
            yRowStride = yRowStride,
            yPixelStride = 1,
            uBuffer = uBuf,
            vBuffer = vBuf,
            uvRowStride = uvRowStride,
            uvPixelStride = 1
        )

        // Red has BT.601 Y ~= 81, U ~= 90, V ~= 240
        val redY = yBuf.get(0).toInt() and 0xFF
        val redU = uBuf.get(0).toInt() and 0xFF
        val redV = vBuf.get(0).toInt() and 0xFF
        assertTrue("Expected red Y around 81, got $redY", redY in 75..88)
        assertTrue("Expected red U < 110, got $redU", redU < 110)
        assertTrue("Expected red V > 220, got $redV", redV > 220)

        // Blue has BT.601 Y ~= 41, U ~= 240, V ~= 110
        val blueRowOffset = (h / 2) * yRowStride
        val blueY = yBuf.get(blueRowOffset).toInt() and 0xFF
        val blueUvOffset = (h / 4) * uvRowStride
        val blueU = uBuf.get(blueUvOffset).toInt() and 0xFF
        val blueV = vBuf.get(blueUvOffset).toInt() and 0xFF
        assertTrue("Expected blue Y around 41, got $blueY", blueY in 35..48)
        assertTrue("Expected blue U > 220, got $blueU", blueU > 220)
        assertTrue("Expected blue V < 120, got $blueV", blueV < 120)

        // Case 2: Semi-planar interleaved NV12 (uvPixelStride = 2, single interleaved backing array)
        val nv12UvBacking = ByteArray(w * (h / 2))
        val nv12U = ByteBuffer.wrap(nv12UvBacking, 0, nv12UvBacking.size).slice()
        val nv12V = ByteBuffer.wrap(nv12UvBacking, 1, nv12UvBacking.size - 1).slice()
        val yBuf2 = ByteBuffer.allocate(w * h)

        VideoSessionRecorder.writeArgbToYuvPlanes(
            argb = argb,
            width = w,
            height = h,
            yBuffer = yBuf2,
            yRowStride = w,
            yPixelStride = 1,
            uBuffer = nv12U,
            vBuffer = nv12V,
            uvRowStride = w,
            uvPixelStride = 2
        )

        val interleavedU0 = nv12UvBacking[0].toInt() and 0xFF
        val interleavedV0 = nv12UvBacking[1].toInt() and 0xFF
        assertTrue("NV12 U0 should be red U (<110), got $interleavedU0", interleavedU0 < 110)
        assertTrue("NV12 V0 should be red V (>220), got $interleavedV0", interleavedV0 > 220)
    }
}
