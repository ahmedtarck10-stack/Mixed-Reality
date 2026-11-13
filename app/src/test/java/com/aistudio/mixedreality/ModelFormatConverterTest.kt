package com.aistudio.mixedreality

import com.aistudio.mixedreality.util.ModelFormatConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelFormatConverterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun objFileIsConvertedToValidBinaryGlb() {
        val objContent = """
            # Sample OBJ pyramid
            v 0.0 1.0 0.0
            v -1.0 -1.0 1.0
            v 1.0 -1.0 1.0
            v 0.0 -1.0 -1.0
            vn 0.0 1.0 0.0
            vt 0.5 1.0
            vt 0.0 0.0
            vt 1.0 0.0
            f 1/1/1 2/2/1 3/3/1
            f 1/1/1 3/3/1 4/2/1
            f 1/1/1 4/2/1 2/2/1
            f 2/2/1 4/2/1 3/3/1
        """.trimIndent()

        val objFile = tempFolder.newFile("pyramid.obj")
        objFile.writeText(objContent)

        val outDir = tempFolder.newFolder("out")
        val glbTarget = File(outDir, "pyramid_converted.glb")

        // Parse OBJ using converter
        val mockContext = object {
            val cacheDir: File = outDir
        }

        // Test directly using the converter's conversion logic
        val result = ModelFormatConverter.convertObjToGlb(
            cacheDir = outDir,
            objFile = objFile
        )

        assertEquals("OBJ", result.originalFormat)
        assertTrue("Triangle count should be positive", result.triangleCount > 0)
        assertTrue("GLB file should exist and have content", result.glbFile.exists() && result.glbFile.length() > 0)

        // Verify glTF 2.0 Magic bytes ("glTF" -> 0x46546C67)
        val bytes = result.glbFile.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        val version = buffer.int
        val length = buffer.int

        assertEquals(0x46546C67, magic)
        assertEquals(2, version)
        assertEquals(bytes.size, length)
    }

    @Test
    fun usdzPackagingIdentifiesAppleFormat() {
        val usdzFile = tempFolder.newFile("sample.usdz")
        usdzFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        val result = ModelFormatConverter.processIncomingModel(
            cacheDir = tempFolder.root,
            sourceFile = usdzFile
        )

        assertTrue(result.originalFormat.contains("USDZ"))
        assertTrue(result.glbFile.exists())
    }

    @Test
    fun binaryStlExceedingOldSceneViewCapConvertsCleanlyToValidGlb() {
        val outDir = tempFolder.newFolder("stl_out")
        val stlFile = tempFolder.newFile("dense_part.stl")
        // Write a binary STL with 260,000 triangles (> old 250,000 SceneView StlLoader limit!)
        val triCount = 260_000
        stlFile.outputStream().buffered(262_144).use { os ->
            os.write(ByteArray(80)) // header
            val hdr = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(triCount).array()
            os.write(hdr)
            val rec = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until triCount) {
                rec.clear()
                rec.putFloat(0f); rec.putFloat(0f); rec.putFloat(1f)
                val x = (i % 500).toFloat()
                val y = (i / 500).toFloat()
                rec.putFloat(x); rec.putFloat(y); rec.putFloat(0f)
                rec.putFloat(x + 1f); rec.putFloat(y); rec.putFloat(0f)
                rec.putFloat(x); rec.putFloat(y + 1f); rec.putFloat(0f)
                rec.putShort(0)
                os.write(rec.array())
            }
        }

        val result = com.aistudio.mixedreality.util.StreamingMeshProcessor.convertStlToGlb(outDir, stlFile)
        assertTrue("STL conversion should succeed", result.isSuccess)
        assertEquals("STL", result.originalFormat)
        assertEquals(triCount, result.triangleCount)
        assertTrue("GLB file should exist", result.glbFile.exists() && result.glbFile.length() > 100)

        val headerBytes = ByteArray(12)
        result.glbFile.inputStream().use { it.read(headerBytes) }
        val bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, bb.int)
        assertEquals(2, bb.int)
        assertEquals(result.glbFile.length().toInt(), bb.int)
    }

    @Test
    fun asciiPlyAndStreamingObjConvertToValidGlb() {
        val outDir = tempFolder.newFolder("ply_obj_out")

        // 1. ASCII PLY cube face
        val plyFile = tempFolder.newFile("mesh.ply")
        plyFile.writeText(
            """
            ply
            format ascii 1.0
            element vertex 4
            property float x
            property float y
            property float z
            element face 1
            property list uchar int vertex_indices
            end_header
            0.0 0.0 0.0
            1.0 0.0 0.0
            1.0 1.0 0.0
            0.0 1.0 0.0
            4 0 1 2 3
            """.trimIndent()
        )
        val plyResult = com.aistudio.mixedreality.util.StreamingMeshProcessor.convertPlyToGlb(outDir, plyFile)
        assertTrue("PLY conversion should succeed", plyResult.isSuccess)
        assertEquals("PLY", plyResult.originalFormat)
        assertEquals(2, plyResult.triangleCount)
        assertTrue(plyResult.glbFile.exists())

        // 2. Two-pass disk-paged streaming OBJ
        val objFile = tempFolder.newFile("streamed.obj")
        objFile.writeText(
            """
            v 0.0 0.0 0.0
            v 2.0 0.0 0.0
            v 0.0 2.0 0.0
            f 1 2 3
            """.trimIndent()
        )
        val objResult = com.aistudio.mixedreality.util.StreamingMeshProcessor.convertObjToGlbStreaming(outDir, objFile)
        assertTrue("Streaming OBJ conversion should succeed", objResult.isSuccess)
        assertEquals("OBJ", objResult.originalFormat)
        assertEquals(1, objResult.triangleCount)
        assertTrue(objResult.glbFile.exists())
    }

    @Test
    fun notificationSessionSummaryFormatsModeModelStatsAndRecordingState() {
        val (idleTitle, idleBody) = com.aistudio.mixedreality.util.MRNotificationManager.buildSessionSummaryText(
            modeLabel = "AR",
            modelLabel = "scan.glb",
            statsSummary = "1.2M verts • 2.4M tris",
            isRecording = false
        )
        assertEquals("Mixed Reality • AR Mode", idleTitle)
        assertEquals("scan.glb • 1.2M verts • 2.4M tris", idleBody)

        val (recTitle, recBody) = com.aistudio.mixedreality.util.MRNotificationManager.buildSessionSummaryText(
            modeLabel = "MR",
            modelLabel = "engine.3mf",
            statsSummary = null,
            isRecording = true
        )
        assertEquals("REC Active • MR Mode", recTitle)
        assertEquals("engine.3mf • Recording screen", recBody)
    }
}
