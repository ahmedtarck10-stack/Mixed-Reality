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
}
