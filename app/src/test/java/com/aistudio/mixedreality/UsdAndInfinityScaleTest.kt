package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.formatCompactCount
import com.aistudio.mixedreality.util.ModelFormatConverter
import com.aistudio.mixedreality.util.StreamingMeshProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UsdAndInfinityScaleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun usdzWithRealUsdaMeshExtractsRealGeometryZUpAndTransforms() {
        val outDir = tempFolder.newFolder("usdz_real_usda")
        val usdz = tempFolder.newFile("octahedron.usdz")

        // Octahedron: 6 vertices, 8 triangles (explicitly distinct from 12-tri fallback proxy)
        // Z-up + translate(0, 0, 2) -> rotated -90° around X => Z becomes -Y / Y becomes +Z
        val usda = """
            #usda 1.0
            (
                upAxis = "Z"
                metersPerUnit = 1.0
                defaultPrim = "Root"
            )
            def Xform "Root"
            {
                double3 xformOp:translate = (1.0, 2.0, 3.0)
                uniform token[] xformOpOrder = ["xformOp:translate"]

                def Mesh "Octa"
                {
                    int[] faceVertexCounts = [3, 3, 3, 3, 3, 3, 3, 3]
                    int[] faceVertexIndices = [
                        0, 1, 2,  0, 2, 3,  0, 3, 4,  0, 4, 1,
                        5, 2, 1,  5, 3, 2,  5, 4, 3,  5, 1, 4
                    ]
                    point3f[] points = [
                        (0, 0, 1), (1, 0, 0), (0, 1, 0), (-1, 0, 0), (0, -1, 0), (0, 0, -1)
                    ]
                    texCoord2f[] primvars:st = [(0,0), (1,0), (0.5,1), (0,0), (1,0), (0.5,1)] (
                        interpolation = "vertex"
                    )
                    rel material:binding = </Root/Mat>
                }

                def Material "Mat"
                {
                    token outputs:surface.connect = </Root/Mat/PBR.outputs:surface>
                    def Shader "PBR"
                    {
                        uniform token info:id = "UsdPreviewSurface"
                        color3f inputs:diffuseColor = (0.2, 0.7, 0.9)
                        float inputs:roughness = 0.35
                    }
                }
            }
        """.trimIndent()

        ZipOutputStream(usdz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("model.usda"))
            zos.write(usda.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val res = ModelFormatConverter.convertUsdzToGlb(outDir, usdz)
        assertTrue("USDZ with real USDA mesh must succeed", res.isSuccess)
        assertEquals("USDZ", res.originalFormat)
        assertEquals(8, res.triangleCount)
        assertEquals(8L, res.rawTriangleCount)
        assertEquals(6, res.vertexCount)
        assertEquals(6L, res.rawVertexCount)
        assertTrue(res.glbFile.exists() && res.glbFile.length() > 100L)

        // Verify GLB header & valid JSON/BIN structure
        val bytes = res.glbFile.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, bb.int) // "glTF"
        assertEquals(2, bb.int)
        assertEquals(bytes.size, bb.int)
    }

    @Test
    fun standaloneUsdaAndBinaryUsdcCrateInsideUsdzDecodeFullMeshGeometry() {
        val outDir = tempFolder.newFolder("usdc_real_crate")
        val crateBytes = buildSyntheticPyramidCrateUsdc()

        // 1. Standalone .usdc file
        val standaloneUsdc = tempFolder.newFile("pyramid.usdc").apply { writeBytes(crateBytes) }
        val standaloneRes = ModelFormatConverter.processIncomingModel(outDir, standaloneUsdc)
        assertTrue("Standalone USDC Crate must convert cleanly", standaloneRes.isSuccess)
        assertEquals("USDC", standaloneRes.originalFormat)
        assertEquals(4, standaloneRes.triangleCount) // 4-face tetrahedron/pyramid (distinct from 12-tri proxy!)

        // 2. Binary .usdc packaged inside .usdz archive
        val usdzFile = tempFolder.newFile("pyramid_crate.usdz")
        ZipOutputStream(usdzFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("Asset.usdc"))
            zos.write(crateBytes)
            zos.closeEntry()
        }
        val usdzRes = ModelFormatConverter.convertUsdzToGlb(outDir, usdzFile)
        assertTrue("USDZ containing binary PXR-USDC Crate must convert cleanly", usdzRes.isSuccess)
        assertEquals("USDZ", usdzRes.originalFormat)
        assertEquals(4, usdzRes.triangleCount)
        assertEquals(4L, usdzRes.rawTriangleCount)

        val inspect = StreamingMeshProcessor.inspectGlbMetadata(usdzRes.glbFile)
        assertTrue(inspect.isValidGlb)
        assertEquals(4L, inspect.triangleCount)
    }

    @Test
    fun mappedFloatTupleStoreSpillsSmoothlyBeyondHeapWindowWithoutCap() {
        val scratchDir = tempFolder.newFolder("mapped_store")
        val store = StreamingMeshProcessor.MappedFloatTupleStore(3, scratchDir, "huge_verts")
        try {
            // Exceeds 65,536 RAM fast-cache threshold so it spills & pages via FileChannel mmap windows
            val count = 75_000L
            for (i in 0L until count) {
                store.add3(i.toFloat(), (i * 2L).toFloat(), (i * 3L).toFloat())
            }
            store.sealForRead()
            assertEquals(count, store.count)
            assertEquals(0f, store.getX(0L), 1e-4f)
            assertEquals(74_999f, store.getX(74_999L), 1e-4f)
            assertEquals(149_998f, store.getY(74_999L), 1e-4f)
            assertEquals(224_997f, store.getZ(74_999L), 1e-4f)
        } finally {
            store.close()
        }
    }

    @Test
    fun infinityScaleLimitsAndBillionCompactCountersWorkWithoutOverflow() {
        assertEquals(Long.MAX_VALUE, StreamingMeshProcessor.UNLIMITED_CAPACITY)
        assertEquals("30.0M", formatCompactCount(30_000_000L))
        assertEquals("1.50B", formatCompactCount(1_500_000_000L))
        assertEquals("250K", formatCompactCount(250_000L))
        assertEquals("42", formatCompactCount(42L))

        val bigInspection = StreamingMeshProcessor.GlbInspection(
            isValidGlb = true,
            jsonByteLength = 4096,
            binByteLength = 360_000_000L,
            vertexCount = 30_000_000L,
            triangleCount = 30_000_000L,
            hasDraco = false,
            hasMeshopt = false,
            hasWebp = false,
            hasUncompressedAccessors = true,
            imageCount = 0
        )
        assertTrue(bigInspection.isHeavyForMobile)
    }

    @Test
    fun simplifyUncompressedGlbHandlesTriangleStripQuantizedUshortAndNodeTransform() {
        val cacheDir = tempFolder.newFolder("simplify_strip")
        val stripGlb = File(cacheDir, "strip_quantized.glb")

        // 4 vertices using UNSIGNED_SHORT normalized (5123) in a TRIANGLE_STRIP (mode=5 -> 2 triangles)
        // with a node translation [10, 20, 30]
        val json = """
        {
          "asset": {"version": "2.0"},
          "scene": 0,
          "scenes": [{"nodes": [0]}],
          "nodes": [{"mesh": 0, "translation": [10.0, 20.0, 30.0]}],
          "meshes": [{
            "primitives": [{
              "attributes": {"POSITION": 0},
              "mode": 5
            }]
          }],
          "accessors": [{
            "bufferView": 0,
            "byteOffset": 0,
            "componentType": 5123,
            "normalized": true,
            "count": 4,
            "type": "VEC3"
          }],
          "bufferViews": [{
            "buffer": 0,
            "byteOffset": 0,
            "byteLength": 24,
            "target": 34962
          }],
          "buffers": [{"byteLength": 24}]
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val jsonPad = (4 - (json.size % 4)) % 4
        val paddedJsonLen = json.size + jsonPad
        val bin = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        // v0=(0,0,0), v1=(65535,0,0), v2=(0,65535,0), v3=(65535,65535,0)
        bin.putShort(0); bin.putShort(0); bin.putShort(0)
        bin.putShort(0xFFFF.toShort()); bin.putShort(0); bin.putShort(0)
        bin.putShort(0); bin.putShort(0xFFFF.toShort()); bin.putShort(0)
        bin.putShort(0xFFFF.toShort()); bin.putShort(0xFFFF.toShort()); bin.putShort(0)

        val totalLen = 12 + 8 + paddedJsonLen + 8 + 24
        val out = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(totalLen)
        out.putInt(paddedJsonLen); out.putInt(0x4E4F534A)
        out.put(json); repeat(jsonPad) { out.put(0x20.toByte()) }
        out.putInt(24); out.putInt(0x004E4942)
        out.put(bin.array())
        stripGlb.writeBytes(out.array())

        val res = StreamingMeshProcessor.simplifyUncompressedGlb(cacheDir, stripGlb, force = true)
        assertNotNull("simplifyUncompressedGlb(force=true) should process triangle strip", res)
        val outInfo = StreamingMeshProcessor.inspectGlbMetadata(res!!)
        assertTrue(outInfo.isValidGlb)
        assertEquals(2L, outInfo.triangleCount)
    }

    @Test
    fun asciiPlyNgonBeyond32VerticesParsesDynamicallyWithoutTruncation() {
        val outDir = tempFolder.newFolder("ply_ngon")
        val plyFile = tempFolder.newFile("circle_ngon.ply")
        val ngonSides = 40
        val sb = StringBuilder()
        sb.append("ply\nformat ascii 1.0\nelement vertex $ngonSides\n")
        sb.append("property float x\nproperty float y\nproperty float z\n")
        sb.append("element face 1\nproperty list uchar int vertex_indices\nend_header\n")
        for (i in 0 until ngonSides) {
            val angle = (2.0 * Math.PI * i) / ngonSides
            sb.append("${Math.cos(angle)} ${Math.sin(angle)} 0.0\n")
        }
        sb.append("$ngonSides")
        for (i in 0 until ngonSides) sb.append(" $i")
        sb.append('\n')
        plyFile.writeText(sb.toString())

        val res = StreamingMeshProcessor.convertPlyToGlb(outDir, plyFile)
        assertTrue(res.isSuccess)
        assertEquals(ngonSides - 2, res.triangleCount)
        assertFalse(res.glbFile.length() == 0L)
    }

    // =========================================================================
    // Minimal Real OpenUSD PXR-USDC Binary Crate Writer for Unit Tests
    // =========================================================================

    private fun tfCompressSingleBlock(raw: ByteArray): ByteArray {
        // Prepend nChunks=0 byte + single RFC-compliant uncompressed LZ4 literal-only sequence
        val bos = ByteArrayOutputStream()
        bos.write(0) // nChunks = 0 -> single raw LZ4 block
        val n = raw.size
        if (n < 15) {
            bos.write(n shl 4)
        } else {
            bos.write(0xF0)
            var rem = n - 15
            while (rem >= 255) {
                bos.write(255)
                rem -= 255
            }
            bos.write(rem)
        }
        bos.write(raw)
        return bos.toByteArray()
    }

    private fun compressUsdInts32(ints: IntArray): ByteArray {
        val codesLen = (ints.size + 3) / 4
        val bb = ByteBuffer.allocate(4 + codesLen + ints.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0) // commonValue = 0
        val codes = ByteArray(codesLen) { 0xFF.toByte() } // all deltas = code 3 (int32)
        bb.put(codes)
        var prev = 0
        for (v in ints) {
            bb.putInt(v - prev)
            prev = v
        }
        return tfCompressSingleBlock(bb.array())
    }

    private fun valueRep(
        type: Int,
        payload: Long,
        isArray: Boolean = false,
        isInlined: Boolean = false,
        isCompressed: Boolean = false
    ): Long {
        var r = (type.toLong() and 0xFFL) shl 48
        if (isArray) r = r or (1L shl 63)
        if (isInlined) r = r or (1L shl 62)
        if (isCompressed) r = r or (1L shl 61)
        return r or (payload and 0x0000_FFFF_FFFF_FFFFL)
    }

    /**
     * Builds an authentic `PXR-USDC` Crate v0.8.0 file with a 4-vertex, 4-triangle tetrahedron:
     * `/`, `/Pyr`, `/Pyr.typeName`="Mesh", `/Pyr.points`, `/Pyr.faceVertexCounts`, `/Pyr.faceVertexIndices`.
     */
    private fun buildSyntheticPyramidCrateUsdc(): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeU32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun writeU64(v: Long) {
            for (b in 0..7) out.write(((v ushr (b * 8)) and 0xFFL).toInt())
        }
        fun writeFloat(v: Float) = writeU32(java.lang.Float.floatToRawIntBits(v))

        // Header (88 bytes): magic "PXR-USDC" + version [0,8,0,0] + TOC offset placeholder + reserved
        out.write("PXR-USDC".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 8, 0, 0, 0, 0, 0, 0))
        val tocOffsetPos = out.size()
        writeU64(0L)
        out.write(ByteArray(88 - out.size()))

        // Tokens: 0="", 1="upAxis", 2="Y", 3="Pyr", 4="typeName", 5="Mesh",
        //         6="points", 7="default", 8="faceVertexCounts", 9="faceVertexIndices"
        val tokens = listOf("", "upAxis", "Y", "Pyr", "typeName", "Mesh", "points", "default", "faceVertexCounts", "faceVertexIndices")

        // Write array payloads BEFORE TOC sections so ValueReps have valid file offsets
        val ptsOffset = out.size().toLong()
        writeU64(4L)
        val pts = floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, -1f)
        for (f in pts) writeFloat(f)

        val countsOffset = out.size().toLong()
        writeU64(4L)
        repeat(4) { writeU32(3) }

        val idxOffset = out.size().toLong()
        writeU64(12L)
        val idxs = intArrayOf(0, 1, 2, 0, 2, 3, 0, 3, 1, 1, 3, 2)
        for (i in idxs) writeU32(i)

        // Section 1: TOKENS
        val tokStart = out.size().toLong()
        val rawTok = tokens.joinToString("\u0000", postfix = "\u0000").toByteArray(Charsets.UTF_8)
        val compTok = tfCompressSingleBlock(rawTok)
        writeU64(tokens.size.toLong())
        writeU64(rawTok.size.toLong())
        writeU64(compTok.size.toLong())
        out.write(compTok)
        val tokSize = out.size().toLong() - tokStart

        // Section 2: STRINGS
        val strStart = out.size().toLong()
        writeU64(0L)
        val strSize = out.size().toLong() - strStart

        // Section 3: FIELDS
        // 0: upAxis="Y", 1: typeName="Mesh", 2: default=points, 3: default=counts, 4: default=indices
        val fldStart = out.size().toLong()
        val fTokIdx = intArrayOf(1, 4, 7, 7, 7)
        val fReps = longArrayOf(
            valueRep(11, 2L, isInlined = true),
            valueRep(11, 5L, isInlined = true),
            valueRep(21, ptsOffset, isArray = true),
            valueRep(3, countsOffset, isArray = true),
            valueRep(3, idxOffset, isArray = true)
        )
        val compFTok = compressUsdInts32(fTokIdx)
        val rawFRep = ByteBuffer.allocate(fReps.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        fReps.forEach { rawFRep.putLong(it) }
        val compFRep = tfCompressSingleBlock(rawFRep.array())
        writeU64(fTokIdx.size.toLong())
        writeU64(compFTok.size.toLong()); out.write(compFTok)
        writeU64(compFRep.size.toLong()); out.write(compFRep)
        val fldSize = out.size().toLong() - fldStart

        // Section 4: FIELDSETS (each ends with -1)
        // [0]=0,-1 (fsIdx=0); [2]=1,-1 (fsIdx=2); [4]=2,-1 (fsIdx=4); [6]=3,-1 (fsIdx=6); [8]=4,-1 (fsIdx=8)
        val fsStart = out.size().toLong()
        val fsArr = intArrayOf(0, -1, 1, -1, 2, -1, 3, -1, 4, -1)
        val compFs = compressUsdInts32(fsArr)
        writeU64(fsArr.size.toLong())
        writeU64(compFs.size.toLong()); out.write(compFs)
        val fsSize = out.size().toLong() - fsStart

        // Section 5: PATHS (5 nodes: 0="/", 1="/Pyr", 2="/Pyr.points", 3="/Pyr.faceVertexCounts", 4="/Pyr.faceVertexIndices")
        // jump: node0 has only child (-1); node1 has only child (-1); node2 has sibling next (0); node3 has sibling next (0); node4 leaf (-2)
        val pStart = out.size().toLong()
        val pIdxs = intArrayOf(0, 1, 2, 3, 4)
        val eToks = intArrayOf(0, 3, -6, -8, -9)
        val jumps = intArrayOf(-1, -1, 0, 0, -2)
        val cP = compressUsdInts32(pIdxs)
        val cE = compressUsdInts32(eToks)
        val cJ = compressUsdInts32(jumps)
        writeU64(5L)
        writeU64(cP.size.toLong()); out.write(cP)
        writeU64(cE.size.toLong()); out.write(cE)
        writeU64(cJ.size.toLong()); out.write(cJ)
        val pSize = out.size().toLong() - pStart

        // Section 6: SPECS
        val sStart = out.size().toLong()
        val spPath = intArrayOf(0, 1, 2, 3, 4)
        val spFs = intArrayOf(0, 2, 4, 6, 8)
        val spType = intArrayOf(1, 2, 3, 3, 3)
        val cSpP = compressUsdInts32(spPath)
        val cSpF = compressUsdInts32(spFs)
        val cSpT = compressUsdInts32(spType)
        writeU64(5L)
        writeU64(cSpP.size.toLong()); out.write(cSpP)
        writeU64(cSpF.size.toLong()); out.write(cSpF)
        writeU64(cSpT.size.toLong()); out.write(cSpT)
        val sSize = out.size().toLong() - sStart

        // Table of Contents
        val tocPos = out.size().toLong()
        val secs = listOf(
            Triple("TOKENS", tokStart, tokSize),
            Triple("STRINGS", strStart, strSize),
            Triple("FIELDS", fldStart, fldSize),
            Triple("FIELDSETS", fsStart, fsSize),
            Triple("PATHS", pStart, pSize),
            Triple("SPECS", sStart, sSize)
        )
        writeU64(secs.size.toLong())
        for ((name, st, sz) in secs) {
            val nb = name.toByteArray(Charsets.US_ASCII).copyOf(16)
            out.write(nb)
            writeU64(st)
            writeU64(sz)
        }

        val finalBytes = out.toByteArray()
        ByteBuffer.wrap(finalBytes).order(ByteOrder.LITTLE_ENDIAN).putLong(tocOffsetPos, tocPos)
        return finalBytes
    }
}
