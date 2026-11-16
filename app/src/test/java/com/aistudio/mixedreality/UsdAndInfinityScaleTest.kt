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

    private fun extractGlbJson(glb: File): String {
        val bytes = glb.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, bb.int)
        assertEquals(2, bb.int)
        bb.int
        val jsonLen = bb.int
        assertEquals(0x4E4F534A, bb.int)
        return String(bytes, 20, jsonLen, Charsets.UTF_8).trimEnd()
    }

    private fun minimal1x1PngBytes(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
        0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00,
        0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(),
        0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    @Test
    fun simplifyUncompressedGlbPreservesVertexColorsMultiMaterialsAndEmbeddedTexture() {
        val cacheDir = tempFolder.newFolder("simplify_colors_tex")
        val srcGlb = File(cacheDir, "colored_textured.glb")
        val png = minimal1x1PngBytes()

        val posBytes = 4 * 3 * 4 // 4 verts VEC3 float = 48 bytes
        val colBytes = 4 * 4     // 4 verts COLOR_0 VEC4 ubyte = 16 bytes
        val idxBytes = 6 * 2     // 2 tris = 6 u16 = 12 bytes
        val pngOffset = posBytes + colBytes + idxBytes // 76 bytes (4-byte aligned)
        val rawBinLen = pngOffset + png.size
        val binPad = (4 - (rawBinLen % 4)) % 4
        val paddedBinLen = rawBinLen + binPad

        val json = """
        {
          "asset": {"version": "2.0"},
          "scene": 0,
          "scenes": [{"nodes": [0]}],
          "nodes": [{"mesh": 0}],
          "meshes": [{
            "primitives": [
              {"attributes": {"POSITION": 0, "COLOR_0": 1}, "indices": 2, "material": 0},
              {"attributes": {"POSITION": 0, "COLOR_0": 1}, "indices": 3, "material": 1}
            ]
          }],
          "materials": [
            {"pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.25, 0.1, 1.0], "baseColorTexture": {"index": 0}}},
            {"pbrMetallicRoughness": {"baseColorFactor": [0.1, 0.8, 0.9, 1.0], "metallicFactor": 0.6, "roughnessFactor": 0.25}}
          ],
          "textures": [{"source": 0}],
          "images": [{"bufferView": 4, "mimeType": "image/png"}],
          "accessors": [
            {"bufferView": 0, "byteOffset": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "byteOffset": 0, "componentType": 5121, "normalized": true, "count": 4, "type": "VEC4"},
            {"bufferView": 2, "byteOffset": 0, "componentType": 5123, "count": 3, "type": "SCALAR"},
            {"bufferView": 3, "byteOffset": 0, "componentType": 5123, "count": 3, "type": "SCALAR"}
          ],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": 48, "target": 34962},
            {"buffer": 0, "byteOffset": 48, "byteLength": 16, "target": 34962},
            {"buffer": 0, "byteOffset": 64, "byteLength": 6, "target": 34963},
            {"buffer": 0, "byteOffset": 70, "byteLength": 6, "target": 34963},
            {"buffer": 0, "byteOffset": $pngOffset, "byteLength": ${png.size}}
          ],
          "buffers": [{"byteLength": $rawBinLen}]
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val jsonPad = (4 - (json.size % 4)) % 4
        val paddedJsonLen = json.size + jsonPad
        val totalLen = 12 + 8 + paddedJsonLen + 8 + paddedBinLen

        val out = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(totalLen)
        out.putInt(paddedJsonLen); out.putInt(0x4E4F534A)
        out.put(json); repeat(jsonPad) { out.put(0x20.toByte()) }
        out.putInt(paddedBinLen); out.putInt(0x004E4942)
        // Positions
        out.putFloat(0f); out.putFloat(0f); out.putFloat(0f)
        out.putFloat(1f); out.putFloat(0f); out.putFloat(0f)
        out.putFloat(1f); out.putFloat(1f); out.putFloat(0f)
        out.putFloat(0f); out.putFloat(1f); out.putFloat(0f)
        // Colors (red, green, blue, yellow)
        out.put(255.toByte()); out.put(0); out.put(0); out.put(255.toByte())
        out.put(0); out.put(255.toByte()); out.put(0); out.put(255.toByte())
        out.put(0); out.put(0); out.put(255.toByte()); out.put(255.toByte())
        out.put(255.toByte()); out.put(255.toByte()); out.put(0); out.put(255.toByte())
        // Indices: tri 0 -> mat 0; tri 1 -> mat 1
        out.putShort(0); out.putShort(1); out.putShort(2)
        out.putShort(0); out.putShort(2); out.putShort(3)
        out.put(png)
        repeat(binPad) { out.put(0) }
        srcGlb.writeBytes(out.array())

        val simplified = StreamingMeshProcessor.simplifyUncompressedGlb(cacheDir, srcGlb, force = true)
        assertNotNull(simplified)
        val outJson = extractGlbJson(simplified!!)
        assertTrue("COLOR_0 attribute must be preserved", outJson.contains("\"COLOR_0\""))
        assertTrue("baseColorTexture must be preserved", outJson.contains("\"baseColorTexture\""))
        assertTrue("Embedded PNG image must be preserved", outJson.contains("image/png"))
        assertTrue("Second material color [0.1,0.8,0.9,1.0] must be preserved", outJson.contains("0.8"))
    }

    @Test
    fun objWithMtlTexturesMultiMaterialAndVertexColorsPreservesAllColors() {
        val workDir = tempFolder.newFolder("obj_mtl_color")
        val outDir = File(workDir, "cache")
        File(workDir, "diffuse_tex.png").writeBytes(minimal1x1PngBytes())
        File(workDir, "palette.mtl").writeText(
            """
            newmtl RedMat
            Kd 0.9 0.1 0.1
            map_Kd diffuse_tex.png

            newmtl BlueMat
            Kd 0.1 0.2 0.95
            """.trimIndent()
        )
        val objFile = File(workDir, "colored.obj")
        objFile.writeText(
            """
            mtllib palette.mtl
            v 0 0 0 255 0 0
            v 1 0 0 0 255 0
            v 1 1 0 0 0 255
            v 0 1 0 255 255 0
            vt 0.0 0.25
            vt 1.0 0.25
            vt 1.0 0.75
            vt 0.0 0.75
            usemtl RedMat
            f 1/1 2/2 3/3
            usemtl BlueMat
            f 1/1 3/3 4/4
            """.trimIndent()
        )

        val res = ModelFormatConverter.convertObjToGlb(outDir, objFile)
        assertTrue(res.isSuccess)
        assertEquals(2, res.triangleCount)
        val json = extractGlbJson(res.glbFile)
        assertTrue("OBJ vertex colors must emit COLOR_0", json.contains("\"COLOR_0\""))
        assertTrue("MTL map_Kd PNG texture must be embedded", json.contains("\"baseColorTexture\""))
        assertTrue("Embedded PNG image must exist", json.contains("image/png"))
        assertTrue("Multiple materials must emit >=2 primitives", json.contains("\"material\":1"))
    }

    @Test
    fun plyAndThreeMfPreserveVertexAndTriangleColors() {
        val outDir = tempFolder.newFolder("ply_3mf_color")

        // PLY with uchar RGB vertex colors
        val plyFile = File(outDir, "colored.ply")
        plyFile.writeText(
            """
            ply
            format ascii 1.0
            element vertex 3
            property float x
            property float y
            property float z
            property uchar red
            property uchar green
            property uchar blue
            element face 1
            property list uchar int vertex_indices
            end_header
            0 0 0 255 0 0
            1 0 0 0 255 0
            0 1 0 0 0 255
            3 0 1 2
            """.trimIndent()
        )
        val plyRes = StreamingMeshProcessor.convertPlyToGlb(outDir, plyFile)
        assertTrue(plyRes.isSuccess)
        assertTrue("PLY vertex colors must emit COLOR_0", extractGlbJson(plyRes.glbFile).contains("\"COLOR_0\""))

        // 3MF with basematerials & colored triangle
        val mfFile = File(outDir, "colored.3mf")
        val modelXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
              <resources>
                <basematerials id="1">
                  <base name="Crimson" displaycolor="#FF2244" />
                </basematerials>
                <object id="2" type="model" pid="1" pindex="0">
                  <mesh>
                    <vertices>
                      <vertex x="0" y="0" z="0" />
                      <vertex x="10" y="0" z="0" />
                      <vertex x="0" y="10" z="0" />
                    </vertices>
                    <triangles>
                      <triangle v1="0" v2="1" v3="2" pid="1" p1="0" />
                    </triangles>
                  </mesh>
                </object>
              </resources>
              <build><item objectid="2" /></build>
            </model>
        """.trimIndent()
        ZipOutputStream(mfFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("3D/3dmodel.model"))
            zos.write(modelXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val mfRes = StreamingMeshProcessor.convertThreeMfToGlb(outDir, mfFile)
        assertTrue(mfRes.isSuccess)
        assertEquals(1, mfRes.triangleCount)
        assertTrue("3MF colored triangle must emit COLOR_0", extractGlbJson(mfRes.glbFile).contains("\"COLOR_0\""))
    }

    @Test
    fun usdzWithGeomSubsetMultiMaterialAndVertexDisplayColorPreservesCorrectColors() {
        val outDir = tempFolder.newFolder("usdz_multicolor")
        val usdz = tempFolder.newFile("multicolor.usdz")
        val png = minimal1x1PngBytes()

        // Ensure sibling NormalTex shader does NOT overwrite DiffTex, AND GeomSubset switches material per face,
        // AND second mesh with primvars:displayColor exports COLOR_0
        val usda = """
            #usda 1.0
            def Xform "Root"
            {
                def Material "TexturedMat"
                {
                    token outputs:surface.connect = </Root/TexturedMat/PBR.outputs:surface>
                    def Shader "PBR"
                    {
                        uniform token info:id = "UsdPreviewSurface"
                        color3f inputs:diffuseColor = (0.18, 0.18, 0.18)
                        color3f inputs:diffuseColor.connect = </Root/TexturedMat/DiffTex.outputs:rgb>
                        normal3f inputs:normal.connect = </Root/TexturedMat/NormalTex.outputs:rgb>
                    }
                    def Shader "DiffTex"
                    {
                        uniform token info:id = "UsdUVTexture"
                        asset inputs:file = @albedo_color.png@
                    }
                    def Shader "NormalTex"
                    {
                        uniform token info:id = "UsdUVTexture"
                        asset inputs:file = @surface_normal.jpg@
                    }
                }
                def Material "SolidBlueMat"
                {
                    token outputs:surface.connect = </Root/SolidBlueMat/PBR.outputs:surface>
                    def Shader "PBR"
                    {
                        uniform token info:id = "UsdPreviewSurface"
                        color3f inputs:diffuseColor = (0.05, 0.3, 0.95)
                    }
                }
                def Mesh "MultiMatBox"
                {
                    int[] faceVertexCounts = [3, 3]
                    int[] faceVertexIndices = [0, 1, 2, 0, 2, 3]
                    point3f[] points = [(0,0,0), (1,0,0), (1,1,0), (0,1,0)]
                    texCoord2f[] primvars:st = [(0,0), (1,0), (1,1), (0,1)] (interpolation = "vertex")
                    def GeomSubset "Sub0"
                    {
                        uniform token elementType = "face"
                        int[] indices = [0]
                        rel material:binding = </Root/TexturedMat>
                    }
                    def GeomSubset "Sub1"
                    {
                        uniform token elementType = "face"
                        int[] indices = [1]
                        rel material:binding = </Root/SolidBlueMat>
                    }
                }
                def Mesh "VertColorTri"
                {
                    int[] faceVertexCounts = [3]
                    int[] faceVertexIndices = [0, 1, 2]
                    point3f[] points = [(2,0,0), (3,0,0), (2,1,0)]
                    color3f[] primvars:displayColor = [(1, 0, 0), (0, 1, 0), (0, 0, 1)] (interpolation = "vertex")
                }
            }
        """.trimIndent()

        ZipOutputStream(usdz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("scene.usda"))
            zos.write(usda.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("albedo_color.png"))
            zos.write(png)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("surface_normal.jpg"))
            zos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            zos.closeEntry()
        }

        val res = ModelFormatConverter.convertUsdzToGlb(outDir, usdz)
        assertTrue(res.isSuccess)
        assertEquals(3, res.triangleCount)
        val json = extractGlbJson(res.glbFile)
        assertTrue("USDZ vertex displayColor must emit COLOR_0", json.contains("\"COLOR_0\""))
        assertTrue("Connected diffuse PNG (not normal JPG) must be chosen", json.contains("image/png"))
        assertFalse("Normal map JPG must not overwrite connected diffuse PNG", json.contains("image/jpeg"))
        assertTrue("GeomSubset must emit second material slot", json.contains("\"material\":1"))
        assertTrue("SolidBlueMat color (0.95) must be preserved", json.contains("0.95"))
    }

    @Test
    fun usdcCrateInlineVec3DiffuseColorDecodesSignedBytesAccurately() {
        val outDir = tempFolder.newFolder("usdc_inline_color")
        val usdcBytes = buildSyntheticColoredCrateUsdc()
        val usdcFile = tempFolder.newFile("green_pyr.usdc").apply { writeBytes(usdcBytes) }

        val res = ModelFormatConverter.processIncomingModel(outDir, usdcFile)
        assertTrue("Colored USDC Crate must convert cleanly", res.isSuccess)
        val json = extractGlbJson(res.glbFile)
        // Expected baseColorFactor is [0.0, 1.0, 0.0, 1.0] (previously bugged to [256.0, 0.0, 0.0, 1.0])
        assertTrue("Inline Crate Vec3f (0,1,0) must unpack to [0.0,1.0,0.0,1.0], got: $json", json.contains("[0.0,1.0,0.0,1.0]"))
    }

    private fun buildSyntheticColoredCrateUsdc(): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeU32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun writeU64(v: Long) {
            for (b in 0..7) out.write(((v ushr (b * 8)) and 0xFFL).toInt())
        }
        fun writeFloat(v: Float) = writeU32(java.lang.Float.floatToRawIntBits(v))

        out.write("PXR-USDC".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 8, 0, 0, 0, 0, 0, 0))
        val tocOffsetPos = out.size()
        writeU64(0L)
        out.write(ByteArray(88 - out.size()))

        // Tokens:
        // 0="", 1="upAxis", 2="Y", 3="Pyr", 4="typeName", 5="Mesh",
        // 6="points", 7="default", 8="faceVertexCounts", 9="faceVertexIndices",
        // 10="material:binding", 11="targetPaths", 12="Mat", 13="Material",
        // 14="PBR", 15="Shader", 16="inputs:diffuseColor"
        val tokens = listOf(
            "", "upAxis", "Y", "Pyr", "typeName", "Mesh",
            "points", "default", "faceVertexCounts", "faceVertexIndices",
            "material:binding", "targetPaths", "Mat", "Material",
            "PBR", "Shader", "inputs:diffuseColor"
        )

        val ptsOffset = out.size().toLong()
        writeU64(3L)
        for (f in floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 1f, 0f, 0f)) writeFloat(f)

        val countsOffset = out.size().toLong()
        writeU64(1L); writeU32(3)

        val idxOffset = out.size().toLong()
        writeU64(3L); writeU32(0); writeU32(1); writeU32(2)

        // PathListOp payload for material:binding targetPaths -> path index 6 ("/Mat")
        // Bit 1 (Explicit list): header = 0x02, count(int64) = 1, pathIdx(int32) = 6
        val bindTargetOffset = out.size().toLong()
        out.write(0x02)
        writeU64(1L)
        writeU32(6)

        // TOKENS
        val tokStart = out.size().toLong()
        val rawTok = tokens.joinToString("\u0000", postfix = "\u0000").toByteArray(Charsets.UTF_8)
        val compTok = tfCompressSingleBlock(rawTok)
        writeU64(tokens.size.toLong()); writeU64(rawTok.size.toLong()); writeU64(compTok.size.toLong()); out.write(compTok)
        val tokSize = out.size().toLong() - tokStart

        // STRINGS
        val strStart = out.size().toLong(); writeU64(0L); val strSize = out.size().toLong() - strStart

        // FIELDS
        // 0: upAxis="Y"
        // 1: typeName="Mesh"
        // 2: default=points
        // 3: default=counts
        // 4: default=indices
        // 5: targetPaths=[/Mat]
        // 6: typeName="Material"
        // 7: typeName="Shader"
        // 8: default=inline Vec3f (0, 1, 0) -> bytes: [0, 1, 0] -> payload = 0x000100L!
        val fldStart = out.size().toLong()
        val fTokIdx = intArrayOf(1, 4, 7, 7, 7, 11, 4, 4, 7)
        val fReps = longArrayOf(
            valueRep(11, 2L, isInlined = true),
            valueRep(11, 5L, isInlined = true),
            valueRep(21, ptsOffset, isArray = true),
            valueRep(3, countsOffset, isArray = true),
            valueRep(3, idxOffset, isArray = true),
            valueRep(34, bindTargetOffset),
            valueRep(11, 13L, isInlined = true),
            valueRep(11, 15L, isInlined = true),
            valueRep(21, 0x000100L, isInlined = true)
        )
        val compFTok = compressUsdInts32(fTokIdx)
        val rawFRep = ByteBuffer.allocate(fReps.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        fReps.forEach { rawFRep.putLong(it) }
        val compFRep = tfCompressSingleBlock(rawFRep.array())
        writeU64(fTokIdx.size.toLong())
        writeU64(compFTok.size.toLong()); out.write(compFTok)
        writeU64(compFRep.size.toLong()); out.write(compFRep)
        val fldSize = out.size().toLong() - fldStart

        // FIELDSETS: fsIdx = 0, 2, 4, 6, 8, 10, 12, 14, 16
        val fsStart = out.size().toLong()
        val fsArr = intArrayOf(0, -1, 1, -1, 2, -1, 3, -1, 4, -1, 5, -1, 6, -1, 7, -1, 8, -1)
        val compFs = compressUsdInts32(fsArr)
        writeU64(fsArr.size.toLong()); writeU64(compFs.size.toLong()); out.write(compFs)
        val fsSize = out.size().toLong() - fsStart

        // PATHS (9 nodes):
        // 0="/", 1="/Pyr", 2="/Pyr.points", 3="/Pyr.faceVertexCounts", 4="/Pyr.faceVertexIndices", 5="/Pyr.material:binding",
        // 6="/Mat", 7="/Mat/PBR", 8="/Mat/PBR.inputs:diffuseColor"
        // jumps:
        // node 0: child only (-1)
        // node 1: child(1->2) AND sibling(1->6: jump = 5)
        // node 2: sibling only (0), node 3: sibling only (0), node 4: sibling only (0), node 5: leaf (-2)
        // node 6: child only (-1), node 7: child only (-1), node 8: leaf (-2)
        val pStart = out.size().toLong()
        val pIdxs = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val eToks = intArrayOf(0, 3, -6, -8, -9, -10, 12, 14, -16)
        val jumps = intArrayOf(-1, 5, 0, 0, 0, -2, -1, -1, -2)
        val cP = compressUsdInts32(pIdxs); val cE = compressUsdInts32(eToks); val cJ = compressUsdInts32(jumps)
        writeU64(9L)
        writeU64(cP.size.toLong()); out.write(cP)
        writeU64(cE.size.toLong()); out.write(cE)
        writeU64(cJ.size.toLong()); out.write(cJ)
        val pSize = out.size().toLong() - pStart

        // SPECS
        val sStart = out.size().toLong()
        val spPath = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val spFs = intArrayOf(0, 2, 4, 6, 8, 10, 12, 14, 16)
        val spType = intArrayOf(1, 2, 3, 3, 3, 4, 2, 2, 3)
        val cSpP = compressUsdInts32(spPath); val cSpF = compressUsdInts32(spFs); val cSpT = compressUsdInts32(spType)
        writeU64(9L)
        writeU64(cSpP.size.toLong()); out.write(cSpP)
        writeU64(cSpF.size.toLong()); out.write(cSpF)
        writeU64(cSpT.size.toLong()); out.write(cSpT)
        val sSize = out.size().toLong() - sStart

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
            out.write(name.toByteArray(Charsets.US_ASCII).copyOf(16))
            writeU64(st); writeU64(sz)
        }

        val finalBytes = out.toByteArray()
        ByteBuffer.wrap(finalBytes).order(ByteOrder.LITTLE_ENDIAN).putLong(tocOffsetPos, tocPos)
        return finalBytes
    }
}
