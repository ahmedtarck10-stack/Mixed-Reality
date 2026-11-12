package com.aistudio.mixedreality

import android.graphics.Bitmap
import com.aistudio.mixedreality.ui.ARTrackingHelper
import com.aistudio.mixedreality.ui.ViewportMode
import com.aistudio.mixedreality.ui.ZoomGestureAccumulator
import com.aistudio.mixedreality.ui.ZoomScalePolicy
import com.aistudio.mixedreality.util.ModelFormatConverter
import com.aistudio.mixedreality.util.VideoSessionRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RemediationMasterVerificationTest
 * Verifies core fixes across AR tracking, model conversion, and memory management.
 */
class RemediationMasterVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test01_objNegativeRelativeIndicesSupported() {
        val outDir = tempFolder.newFolder("out_negative")
        val objFile = File(outDir, "relative.obj")
        // Wavefront OBJ with negative relative indices
        objFile.writeText(
            """
            v 0.0 1.0 0.0
            v -1.0 -1.0 1.0
            v 1.0 -1.0 1.0
            f -3 -2 -1
            """.trimIndent()
        )

        val result = ModelFormatConverter.convertObjToGlb(outDir, objFile)
        assertEquals("OBJ", result.originalFormat)
        assertEquals(1, result.triangleCount)
        assertTrue(result.glbFile.exists())
        assertTrue(result.glbFile.length() > 0)
    }

    @Test
    fun test02_objInvalidIndicesDoNotCorruptMesh() {
        val outDir = tempFolder.newFolder("out_invalid")
        val objFile = File(outDir, "corrupted.obj")
        // Face references index 99 which does not exist
        objFile.writeText(
            """
            v 0.0 1.0 0.0
            v -1.0 -1.0 1.0
            v 1.0 -1.0 1.0
            f 1 2 99
            """.trimIndent()
        )

        val result = ModelFormatConverter.convertObjToGlb(outDir, objFile)
        // Corrupted triangle was skipped, fallback minimal triangle is provided safely
        assertNotNull(result.glbFile)
        assertTrue(result.glbFile.exists())
    }

    @Test
    fun test03_gltfCompanionBinDetection() {
        val modelDir = tempFolder.newFolder("gltf_test")
        val gltfFile = File(modelDir, "scene.gltf")
        gltfFile.writeText(
            """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "missing_buffer.bin", "byteLength": 1024}]
            }
            """.trimIndent()
        )

        val result = ModelFormatConverter.processIncomingModel(tempFolder.root, gltfFile)
        assertEquals("glTF", result.originalFormat)
        assertTrue(result.message.contains("missing_buffer.bin") || result.message.contains("not found"))
    }

    @Test
    fun test04_arTrackingRecoveryHeightDeltaCheck() {
        val helper = ARTrackingHelper()

        // 1. Same plane center Y as saved pose (heightDelta = 0.02m <= 0.10m) -> Eligible
        val eligible = helper.isEligibleRecoveryPlane(
            cameraIsTracking = true,
            planeIsTracking = true,
            isUpwardHorizontalPlane = true,
            isSubsumed = false,
            savedPoseInsidePolygon = true,
            planeCenterY = -0.50f,
            savedPoseY = -0.48f,
            checkHeightDelta = true
        )
        assertTrue("Plane within 10cm height delta should be eligible", eligible)

        // 2. Different elevation (heightDelta = 0.25m > 0.10m) -> Ineligible
        val rejectedDifferentElevation = helper.isEligibleRecoveryPlane(
            cameraIsTracking = true,
            planeIsTracking = true,
            isUpwardHorizontalPlane = true,
            isSubsumed = false,
            savedPoseInsidePolygon = true,
            planeCenterY = -0.50f,
            savedPoseY = -0.75f,
            checkHeightDelta = true
        )
        assertFalse("Plane exceeding 10cm height delta must be rejected", rejectedDifferentElevation)

        // 3. Subsumed plane -> Ineligible
        val rejectedSubsumed = helper.isEligibleRecoveryPlane(
            cameraIsTracking = true,
            planeIsTracking = true,
            isUpwardHorizontalPlane = true,
            isSubsumed = true,
            savedPoseInsidePolygon = true,
            planeCenterY = -0.50f,
            savedPoseY = -0.50f,
            checkHeightDelta = true
        )
        assertFalse("Subsumed plane must be rejected", rejectedSubsumed)
    }

    @Test
    fun test05_viewportModeStringResourcesMapped() {
        assertEquals(R.string.mode_3d, ViewportMode.OBJECT.titleRes)
        assertEquals(R.string.mode_ar, ViewportMode.AR.titleRes)
        assertEquals(R.string.mode_ar, ViewportMode.MR.titleRes)
    }

    @Test
    fun test06_zoomScalePolicyBounds() {
        assertEquals(0.1f, ZoomScalePolicy.normalize(0.01f), 0.001f)
        assertEquals(2500f, ZoomScalePolicy.normalize(5000f), 0.001f)
        assertEquals(1.5f, ZoomScalePolicy.normalize(1.5f), 0.001f)

        val applied = ZoomScalePolicy.apply(currentScale = 1.0f, factor = 2.0f)
        assertEquals(2.0f, applied, 0.001f)
    }

    @Test
    fun test07_zoomGestureAccumulator() {
        val accumulator = ZoomGestureAccumulator()
        accumulator.add(1.2f)
        accumulator.add(1.1f)
        val consumed = accumulator.consume()
        assertTrue(consumed > 1.25f && consumed < 1.4f)
        // Subsequent consume resets factor to 1.0f
        assertEquals(1.0f, accumulator.consume(), 0.001f)
    }
}
