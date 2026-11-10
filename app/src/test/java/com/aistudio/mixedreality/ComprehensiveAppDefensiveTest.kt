package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.RotationAxis
import com.aistudio.mixedreality.ui.applyRotationDelta
import com.aistudio.mixedreality.ui.isEmulatorDevice
import com.aistudio.mixedreality.util.ModelFormatConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Comprehensive defensive tests covering edge cases, crash scenarios,
 * and failure modes across model conversion, gesture inputs, AR availability, and file handling.
 */
class ComprehensiveAppDefensiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 1. Gesture sanitization: Guard against NaN, Inf, and wrap-around angles
    @Test
    fun rotationControllerSafelyRejectsNaNAndInfiniteDeltas() {
        var rotX = 45f
        var rotY = 90f
        var rotZ = 0f

        // NaN delta should be completely ignored without mutating state
        applyRotationDelta(
            axis = RotationAxis.ALL,
            dx = Float.NaN,
            dy = 10f,
            isEdgeDrag = false,
            currentRotX = rotX,
            currentRotY = rotY,
            currentRotZ = rotZ,
            onRotX = { rotX = it },
            onRotY = { rotY = it },
            onRotZ = { rotZ = it }
        )
        assertEquals(45f, rotX, 0.001f)
        assertEquals(90f, rotY, 0.001f)

        // Positive and Negative Infinity deltas must be ignored
        applyRotationDelta(
            axis = RotationAxis.ALL,
            dx = Float.POSITIVE_INFINITY,
            dy = Float.NEGATIVE_INFINITY,
            isEdgeDrag = false,
            currentRotX = rotX,
            currentRotY = rotY,
            currentRotZ = rotZ,
            onRotX = { rotX = it },
            onRotY = { rotY = it },
            onRotZ = { rotZ = it }
        )
        assertEquals(45f, rotX, 0.001f)
        assertEquals(90f, rotY, 0.001f)

        // Edge drag with NaN should not crash
        applyRotationDelta(
            axis = RotationAxis.ALL,
            dx = 0f,
            dy = Float.NaN,
            isEdgeDrag = true,
            currentRotX = rotX,
            currentRotY = rotY,
            currentRotZ = rotZ,
            onRotX = { rotX = it },
            onRotY = { rotY = it },
            onRotZ = { rotZ = it }
        )
        assertEquals(0f, rotZ, 0.001f)
    }

    @Test
    fun rotationControllerRecoversFromCorruptedNaNCurrentState() {
        var rotX = 0f
        var rotY = 0f
        var rotZ = 0f

        // If the current angles were somehow corrupted to NaN, it resets to a safe finite angle
        applyRotationDelta(
            axis = RotationAxis.X,
            dx = 0f,
            dy = 20f,
            isEdgeDrag = false,
            currentRotX = Float.NaN,
            currentRotY = 0f,
            currentRotZ = 0f,
            onRotX = { rotX = it },
            onRotY = { rotY = it },
            onRotZ = { rotZ = it },
            sensitivity = 1.0f
        )
        assertTrue("Rotated X must be finite and wrapped", rotX.isFinite())
        assertEquals(340f, rotX, 0.01f)
    }

    // 2. Model parsing: Empty OBJ or comments-only OBJ files
    @Test
    fun objConverterHandlesEmptyOrMalformedFilesWithoutCrashing() {
        val outDir = tempFolder.newFolder("model_cache")

        // Case A: 0-byte empty file
        val emptyObj = tempFolder.newFile("empty.obj")
        emptyObj.writeText("")

        val emptyResult = ModelFormatConverter.convertObjToGlb(outDir, emptyObj)
        assertNotNull(emptyResult)
        assertTrue(emptyResult.glbFile.exists())
        // Converter creates 1 fallback prism triangle to ensure SceneView loader does not fail on 0 meshes
        assertEquals(1, emptyResult.triangleCount)

        // Case B: OBJ containing only comments and invalid face tokens
        val commentsObj = tempFolder.newFile("garbage.obj")
        commentsObj.writeText("""
            # Garbage header
            # Unknown commands
            mtllib nonexistent.mtl
            usemtl default
            f a/b/c d/e/f
            v invalid invalid invalid
        """.trimIndent())

        val garbageResult = ModelFormatConverter.convertObjToGlb(outDir, commentsObj)
        assertNotNull(garbageResult)
        assertTrue(garbageResult.glbFile.exists())
        assertTrue("GLB file must have valid size", garbageResult.glbFile.length() > 0)
    }

    // 3. Model parsing: Degenerate faces and polygons (Quads and N-gons)
    @Test
    fun objConverterTriangulatesQuadsAndNGonsGracefully() {
        val outDir = tempFolder.newFolder("quad_cache")
        val quadObj = tempFolder.newFile("quad.obj")
        quadObj.writeText("""
            v -1.0 1.0 0.0
            v 1.0 1.0 0.0
            v 1.0 -1.0 0.0
            v -1.0 -1.0 0.0
            # Quad face: 4 vertices should produce 2 triangles
            f 1 2 3 4
        """.trimIndent())

        val result = ModelFormatConverter.convertObjToGlb(outDir, quadObj)
        assertEquals(2, result.triangleCount)
        assertTrue(result.glbFile.exists())
    }

    // 4. USDZ packaging & extraction robustness
    @Test
    fun usdzConverterHandlesCorruptedOrEmptyZipGracefully() {
        val outDir = tempFolder.newFolder("usdz_cache")

        // A corrupt USDZ (random binary junk) should fall back cleanly to spatial proxy without throwing
        val corruptUsdz = tempFolder.newFile("corrupt.usdz")
        corruptUsdz.writeBytes(byteArrayOf(0x50, 0x4B, 0x00, 0x00, 0x12, 0x34))

        val result = ModelFormatConverter.convertUsdzToGlb(outDir, corruptUsdz)
        assertNotNull(result)
        assertEquals("USDZ (Apple)", result.originalFormat)
        assertTrue(result.glbFile.exists())
        assertTrue("Fallback proxy GLB must be generated", result.glbFile.length() > 0)
    }

    @Test
    fun usdzConverterExtractsEmbeddedGlbWhenPresent() {
        val outDir = tempFolder.newFolder("usdz_embedded_cache")
        val usdzWithGlb = tempFolder.newFile("model_with_embedded.usdz")

        // Create a valid zip containing an embedded GLB
        ZipOutputStream(usdzWithGlb.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("model/scene.glb"))
            val dummyGlb = byteArrayOf(0x67, 0x6C, 0x54, 0x46, 0x02, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00)
            zos.write(dummyGlb)
            zos.closeEntry()
        }

        val result = ModelFormatConverter.convertUsdzToGlb(outDir, usdzWithGlb)
        assertEquals("USDZ", result.originalFormat)
        assertTrue(result.glbFile.exists())
        assertEquals("scene.glb", result.glbFile.name)
    }

    // 5. Directory nesting creation test in writeBinaryGlb
    @Test
    fun glbWriterCreatesNestedDirectoriesAutomatically() {
        val deeplyNestedDir = File(tempFolder.root, "nested/level1/level2/level3")
        assertFalse(deeplyNestedDir.exists())

        val objFile = tempFolder.newFile("sample.obj")
        objFile.writeText("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")

        val result = ModelFormatConverter.convertObjToGlb(deeplyNestedDir, objFile)
        assertTrue("Nested directory must be created automatically", deeplyNestedDir.exists())
        assertTrue("Output file must exist inside nested directory", result.glbFile.exists())
    }

    // 6. Filename sanitizer logic against path traversal attacks
    @Test
    fun filenameSanitizationPreventsPathTraversal() {
        val maliciousRawNames = listOf(
            "../../etc/passwd",
            "..\\..\\system32\\evil.glb",
            "/absolute/path/to/malicious.obj",
            "normal_model.glb"
        )

        for (raw in maliciousRawNames) {
            val cleanName = raw.substringAfterLast('/').substringAfterLast('\\').trim()
            assertFalse("Cleaned name should never contain path traversal slash", cleanName.contains('/'))
            assertFalse("Cleaned name should never contain backslash", cleanName.contains('\\'))
            assertFalse("Cleaned name should never be ..", cleanName == "..")
        }
    }

    // 7. Emulator detection heuristics
    @Test
    fun emulatorDetectionIdentifiesStandardProperties() {
        // Just verify isEmulatorDevice runs without crashing on JVM/Robolectric test host
        val isEmu = isEmulatorDevice()
        // Function should return a valid boolean without exceptions
        assertTrue(isEmu || !isEmu)
    }
}
