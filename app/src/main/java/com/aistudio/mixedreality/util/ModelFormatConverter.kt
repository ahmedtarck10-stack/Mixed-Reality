package com.aistudio.mixedreality.util

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * High-performance Universal 3D Model Format Handler & Converter.
 * Optimized for large meshes using zero-boxing primitive buffers, zero-Regex parsing,
 * open-addressing vertex deduplication, and chunk-streamed GLB serialization.
 */
object ModelFormatConverter {

    private const val TAG = "ModelFormatConverter"
    private const val IO_BUFFER_BYTES = 262_144 // 256 KB streaming I/O buffer
    private const val GLB_STREAM_CHUNK_BYTES = 65_536

    data class ConversionResult(
        val glbFile: File,
        val originalFormat: String,
        val triangleCount: Int,
        val vertexCount: Int,
        val message: String,
        val isSuccess: Boolean = true,
        val rawTriangleCount: Long = triangleCount.toLong(),
        val rawVertexCount: Long = vertexCount.toLong()
    )

    fun processIncomingModel(context: Context, sourceFile: File): ConversionResult =
        processIncomingModel(context.cacheDir, sourceFile)

    fun processIncomingModel(cacheDir: File, sourceFile: File): ConversionResult {
        val extension = sourceFile.extension.lowercase()
        return when (extension) {
            "glb" -> {
                val info = StreamingMeshProcessor.inspectGlbMetadata(sourceFile)
                val finalFile: File
                val rawVerts: Long
                val rawTris: Long
                val outVerts: Int
                val outTris: Int
                val msg: String

                if (info.isValidGlb && info.hasUncompressedAccessors &&
                    info.triangleCount > StreamingMeshProcessor.DIRECT_TRIANGLE_LIMIT.toLong()
                ) {
                    val optimized = StreamingMeshProcessor.simplifyUncompressedGlb(cacheDir, sourceFile)
                    if (optimized != null && optimized.exists() && optimized.length() > 0L) {
                        val optInfo = StreamingMeshProcessor.inspectGlbMetadata(optimized)
                        finalFile = optimized
                        rawVerts = info.vertexCount
                        rawTris = info.triangleCount
                        outVerts = optInfo.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        outTris = optInfo.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        msg = "Dense GLB (${formatModelCount(info.triangleCount)} tris) optimized to ${formatModelCount(optInfo.triangleCount)} tris for GPU"
                    } else {
                        finalFile = sourceFile
                        rawVerts = info.vertexCount
                        rawTris = info.triangleCount
                        outVerts = info.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        outTris = info.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        msg = "Native GLB (${formatModelCount(info.triangleCount)} tris)"
                    }
                } else if (info.isValidGlb && info.hasWebp) {
                    val webpSafe = StreamingMeshProcessor.downsampleGlbTexturesForMobile(cacheDir, sourceFile, maxEdge = 2048)
                    val chosen = if (webpSafe != null && webpSafe.exists() && webpSafe.length() > 0L) webpSafe else sourceFile
                    finalFile = chosen
                    rawVerts = info.vertexCount
                    rawTris = info.triangleCount
                    outVerts = info.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    outTris = info.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val texNote = if (chosen != sourceFile) " [WebP->PNG/JPG]" else ""
                    msg = if (info.triangleCount > 0L) {
                        "Native GLB (${formatModelCount(info.triangleCount)} tris)$texNote"
                    } else {
                        "Native GLB Model Loaded$texNote"
                    }
                } else {
                    finalFile = sourceFile
                    rawVerts = info.vertexCount
                    rawTris = info.triangleCount
                    outVerts = info.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    outTris = info.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val compressionNote = when {
                        info.hasDraco && info.hasMeshopt -> " [Draco+Meshopt]"
                        info.hasDraco -> " [Draco]"
                        info.hasMeshopt -> " [Meshopt]"
                        else -> ""
                    }
                    msg = if (info.isValidGlb && info.triangleCount > 0L) {
                        "Native GLB (${formatModelCount(info.triangleCount)} tris)$compressionNote"
                    } else {
                        "Native GLB Model Loaded$compressionNote"
                    }
                }

                ConversionResult(
                    glbFile = finalFile,
                    originalFormat = "GLB",
                    triangleCount = outTris,
                    vertexCount = outVerts,
                    message = msg,
                    isSuccess = true,
                    rawTriangleCount = rawTris,
                    rawVertexCount = rawVerts
                )
            }
            "stl" -> StreamingMeshProcessor.convertStlToGlb(cacheDir, sourceFile)
            "ply" -> StreamingMeshProcessor.convertPlyToGlb(cacheDir, sourceFile)
            "3mf" -> StreamingMeshProcessor.convertThreeMfToGlb(cacheDir, sourceFile)
            "gltf" -> {
                var binWarning: String? = null
                var hasMissingBuffer = false
                val missingResources = mutableListOf<String>()
                try {
                    val uriKeyPattern = "\"uri\""
                    BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(sourceFile), 65_536), Charsets.UTF_8), 65_536).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            var searchPos = 0
                            while (true) {
                                val idx = line.indexOf(uriKeyPattern, searchPos)
                                if (idx < 0) break
                                val colonIdx = line.indexOf(':', idx + uriKeyPattern.length)
                                if (colonIdx < 0) break
                                val quoteStart = line.indexOf('"', colonIdx + 1)
                                if (quoteStart < 0) break
                                val quoteEnd = line.indexOf('"', quoteStart + 1)
                                if (quoteEnd < 0) break
                                val uriStr = line.substring(quoteStart + 1, quoteEnd)
                                searchPos = quoteEnd + 1
                                if (uriStr.startsWith("data:", ignoreCase = true)) {
                                    continue
                                }
                                val companion = File(sourceFile.parentFile, uriStr)
                                if (!companion.exists() || companion.length() == 0L) {
                                    missingResources.add(uriStr)
                                    hasMissingBuffer = true
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                    if (missingResources.isNotEmpty()) {
                        binWarning = "Referenced external buffers/textures not found: ${missingResources.joinToString(", ")}"
                    }
                } catch (_: Exception) {
                }

                val finalFile = if (hasMissingBuffer) {
                    val proxyGlb = File(cacheDir, "${sourceFile.nameWithoutExtension}_gltf_fallback.glb")
                    generateUsdzSpatialProxy(proxyGlb)
                    proxyGlb
                } else {
                    sourceFile
                }

                ConversionResult(
                    glbFile = finalFile,
                    originalFormat = "glTF",
                    triangleCount = if (hasMissingBuffer) 12 else 0,
                    vertexCount = if (hasMissingBuffer) 24 else 0,
                    message = if (binWarning != null) "Incomplete glTF model ($binWarning)" else "Native glTF Model Loaded",
                    isSuccess = !hasMissingBuffer
                )
            }
            "obj" -> convertObjToGlb(cacheDir, sourceFile)
            "usdz", "zip" -> convertUsdzToGlb(cacheDir, sourceFile)
            "usda" -> UsdMeshConverter.convertUsdToGlb(cacheDir, sourceFile, isZippedUsdz = false)
            "usdc", "usd" -> UsdMeshConverter.convertUsdToGlb(cacheDir, sourceFile, isZippedUsdz = false)
            else -> {
                ConversionResult(
                    glbFile = sourceFile,
                    originalFormat = extension.uppercase(),
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "3D Model Ready",
                    isSuccess = true
                )
            }
        }
    }

    private fun formatModelCount(n: Long): String = StreamingMeshProcessor.formatCompact(n)

    fun convertObjToGlb(context: Context, objFile: File): ConversionResult =
        convertObjToGlb(context.cacheDir, objFile)

    /**
     * Converts Wavefront .obj files into compliant binary glTF 2.0 (.glb) with full per-vertex RGB[A] colors,
     * `.mtl` PBR material colors & textures, multi-material primitives, and glTF V-flipped UVs.
     */
    fun convertObjToGlb(cacheDir: File, objFile: File): ConversionResult {
        cacheDir.mkdirs()
        val streamed = StreamingMeshProcessor.convertObjToGlbStreaming(cacheDir, objFile)
        if (streamed.isSuccess && streamed.triangleCount > 0 && streamed.glbFile.exists() && streamed.glbFile.length() > 0L) {
            return streamed
        }

        // Fallback single-triangle minimal GLB for empty/corrupt OBJ inputs
        val targetGlb = File(cacheDir, "${objFile.nameWithoutExtension}_converted.glb")
        writeBinaryGlbStreaming(
            outFile = targetGlb,
            positions = floatArrayOf(-0.5f, 0f, 0f, 0.5f, 0f, 0f, 0f, 1f, 0f),
            positionsLen = 9,
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            normalsLen = 9,
            texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f),
            texCoordsLen = 6,
            indices = intArrayOf(0, 1, 2),
            indicesLen = 3,
            bounds = BoundingBox(-0.5f, 0f, 0f, 0.5f, 1f, 0f)
        )
        return ConversionResult(
            glbFile = targetGlb,
            originalFormat = "OBJ",
            triangleCount = 1,
            vertexCount = 3,
            message = streamed.message.ifBlank { "No valid faces found in OBJ file" },
            isSuccess = false
        )
    }

    fun convertUsdzToGlb(context: Context, usdzFile: File): ConversionResult =
        convertUsdzToGlb(context.cacheDir, usdzFile)

    /**
     * Extracts and converts Apple USDZ (.usdz) or Zipped 3D archives (.zip) into a renderable GLB/glTF.
     */
    fun convertUsdzToGlb(cacheDir: File, usdzFile: File): ConversionResult {
        var extractedGlb: File? = null
        var extractedGltf: File? = null
        var extractedObj: File? = null
        var extracted3mf: File? = null
        var extractedStl: File? = null
        var extractedPly: File? = null
        var hasUsdFiles = false
        val allExtractedFiles = mutableListOf<File>()
        val extractedDir = File(cacheDir, "usdz_${System.currentTimeMillis()}_${System.nanoTime()}")
        extractedDir.mkdirs()
        val canonicalRoot = extractedDir.canonicalPath + File.separator

        try {
            val copyBuffer = ByteArray(IO_BUFFER_BYTES)
            ZipInputStream(BufferedInputStream(FileInputStream(usdzFile), IO_BUFFER_BYTES)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !name.startsWith("__MACOSX")) {
                        val outFile = File(extractedDir, name)
                        if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile), IO_BUFFER_BYTES).use { bos ->
                                var read = zis.read(copyBuffer)
                                while (read != -1) {
                                    bos.write(copyBuffer, 0, read)
                                    read = zis.read(copyBuffer)
                                }
                            }
                            allExtractedFiles.add(outFile)
                            when {
                                name.endsWith(".glb", ignoreCase = true) && extractedGlb == null -> extractedGlb = outFile
                                name.endsWith(".gltf", ignoreCase = true) && extractedGltf == null -> extractedGltf = outFile
                                name.endsWith(".obj", ignoreCase = true) && extractedObj == null -> extractedObj = outFile
                                name.endsWith(".3mf", ignoreCase = true) && extracted3mf == null -> extracted3mf = outFile
                                name.endsWith(".stl", ignoreCase = true) && extractedStl == null -> extractedStl = outFile
                                name.endsWith(".ply", ignoreCase = true) && extractedPly == null -> extractedPly = outFile
                                name.endsWith(".usdc", ignoreCase = true) ||
                                    name.endsWith(".usda", ignoreCase = true) ||
                                    name.endsWith(".usd", ignoreCase = true) -> hasUsdFiles = true
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "USDZ/ZIP archive extraction note: ${e.message}")
        }

        val glb = extractedGlb
        if (glb != null && glb.exists() && glb.length() > 0) {
            return processIncomingModel(extractedDir, glb).copy(originalFormat = "USDZ")
        }

        if (hasUsdFiles) {
            val usdResult = UsdMeshConverter.convertExtractedUsd(
                cacheDir = cacheDir,
                usdFile = usdzFile,
                extractDir = extractedDir,
                rootFiles = allExtractedFiles
            )
            if (usdResult.isSuccess) {
                runCatching { extractedDir.deleteRecursively() }
                return usdResult
            }
        }

        val gltf = extractedGltf
        if (gltf != null && gltf.exists() && gltf.length() > 0) {
            return processIncomingModel(extractedDir, gltf).copy(originalFormat = "USDZ")
        }

        val threeMf = extracted3mf
        if (threeMf != null && threeMf.exists() && threeMf.length() > 0) {
            return processIncomingModel(extractedDir, threeMf).copy(originalFormat = "USDZ")
        }

        val stl = extractedStl
        if (stl != null && stl.exists() && stl.length() > 0) {
            return processIncomingModel(extractedDir, stl).copy(originalFormat = "USDZ")
        }

        val ply = extractedPly
        if (ply != null && ply.exists() && ply.length() > 0) {
            return processIncomingModel(extractedDir, ply).copy(originalFormat = "USDZ")
        }

        val obj = extractedObj
        if (obj != null && obj.exists() && obj.length() > 0) {
            val converted = convertObjToGlb(cacheDir, obj)
            return ConversionResult(
                glbFile = converted.glbFile,
                originalFormat = "USDZ",
                triangleCount = converted.triangleCount,
                vertexCount = converted.vertexCount,
                message = "Extracted & Converted Archive OBJ Model",
                isSuccess = converted.isSuccess,
                rawTriangleCount = converted.rawTriangleCount,
                rawVertexCount = converted.rawVertexCount
            )
        }

        runCatching { extractedDir.deleteRecursively() }
        val proxyGlb = File(cacheDir, "${usdzFile.nameWithoutExtension}_usdz.glb")
        generateUsdzSpatialProxy(proxyGlb)
        return ConversionResult(
            glbFile = proxyGlb,
            originalFormat = "USDZ (Apple)",
            triangleCount = 12,
            vertexCount = 24,
            message = "USDZ archive is corrupted or contains no 3D mesh geometry.",
            isSuccess = false,
            rawTriangleCount = 12L,
            rawVertexCount = 24L
        )
    }

    private data class BoundingBox(
        val minX: Float, val minY: Float, val minZ: Float,
        val maxX: Float, val maxY: Float, val maxZ: Float
    )

    /**
     * Streams glTF 2.0 Binary (.glb) directly to disk using a tiny fixed 64 KB staging buffer,
     * eliminating duplicate full-model ByteBuffer heap allocations.
     */
    private fun writeBinaryGlbStreaming(
        outFile: File,
        positions: FloatArray,
        positionsLen: Int,
        normals: FloatArray,
        normalsLen: Int,
        texCoords: FloatArray,
        texCoordsLen: Int,
        indices: IntArray,
        indicesLen: Int,
        bounds: BoundingBox
    ) {
        val vertCount = positionsLen / 3
        val indexCount = indicesLen
        val useIntIndices = vertCount > 65535 || indexCount > 65535

        val posByteLength = vertCount.toLong() * 3L * 4L
        val normByteLength = vertCount.toLong() * 3L * 4L
        val uvByteLength = vertCount.toLong() * 2L * 4L
        val indexByteLength = if (useIntIndices) indexCount.toLong() * 4L else indexCount.toLong() * 2L

        val totalBinaryLength = posByteLength + normByteLength + uvByteLength + indexByteLength
        val binPadding = ((4L - (totalBinaryLength % 4L)) % 4L).toInt()
        val paddedBinaryLength = (totalBinaryLength + binPadding).toInt()

        val jsonString = """
        {
          "asset": {
            "version": "2.0",
            "generator": "MixedReality USDZ/OBJ Engine"
          },
          "scene": 0,
          "scenes": [
            {
              "nodes": [0]
            }
          ],
          "nodes": [
            {
              "mesh": 0
            }
          ],
          "meshes": [
            {
              "primitives": [
                {
                  "attributes": {
                    "POSITION": 0,
                    "NORMAL": 1,
                    "TEXCOORD_0": 2
                  },
                  "indices": 3,
                  "mode": 4
                }
              ]
            }
          ],
          "accessors": [
            {
              "bufferView": 0,
              "byteOffset": 0,
              "componentType": 5126,
              "count": $vertCount,
              "type": "VEC3",
              "max": [${bounds.maxX}, ${bounds.maxY}, ${bounds.maxZ}],
              "min": [${bounds.minX}, ${bounds.minY}, ${bounds.minZ}]
            },
            {
              "bufferView": 1,
              "byteOffset": 0,
              "componentType": 5126,
              "count": $vertCount,
              "type": "VEC3"
            },
            {
              "bufferView": 2,
              "byteOffset": 0,
              "componentType": 5126,
              "count": $vertCount,
              "type": "VEC2"
            },
            {
              "bufferView": 3,
              "byteOffset": 0,
              "componentType": ${if (useIntIndices) 5125 else 5123},
              "count": $indexCount,
              "type": "SCALAR"
            }
          ],
          "bufferViews": [
            {
              "buffer": 0,
              "byteOffset": 0,
              "byteLength": $posByteLength,
              "target": 34962
            },
            {
              "buffer": 0,
              "byteOffset": $posByteLength,
              "byteLength": $normByteLength,
              "target": 34962
            },
            {
              "buffer": 0,
              "byteOffset": ${posByteLength + normByteLength},
              "byteLength": $uvByteLength,
              "target": 34962
            },
            {
              "buffer": 0,
              "byteOffset": ${posByteLength + normByteLength + uvByteLength},
              "byteLength": $indexByteLength,
              "target": 34963
            }
          ],
          "buffers": [
            {
              "byteLength": $totalBinaryLength
            }
          ]
        }
        """.trimIndent()

        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        val paddedJsonLength = jsonBytes.size + jsonPadding
        val totalGlbLength = 12 + 8 + paddedJsonLength + 8 + paddedBinaryLength

        outFile.parentFile?.mkdirs()
        val chunkBuf = ByteBuffer.allocate(GLB_STREAM_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val chunkBytes = chunkBuf.array()

        BufferedOutputStream(FileOutputStream(outFile), IO_BUFFER_BYTES).use { bos ->
            chunkBuf.clear()
            chunkBuf.putInt(0x46546C67) // "glTF"
            chunkBuf.putInt(2)
            chunkBuf.putInt(totalGlbLength)
            chunkBuf.putInt(paddedJsonLength)
            chunkBuf.putInt(0x4E4F534A) // "JSON"
            bos.write(chunkBytes, 0, chunkBuf.position())
            bos.write(jsonBytes)
            for (p in 0 until jsonPadding) bos.write(0x20)

            chunkBuf.clear()
            chunkBuf.putInt(paddedBinaryLength)
            chunkBuf.putInt(0x004E4942) // "BIN\0"
            bos.write(chunkBytes, 0, chunkBuf.position())

            chunkBuf.clear()
            fun flushChunk() {
                val pos = chunkBuf.position()
                if (pos > 0) {
                    bos.write(chunkBytes, 0, pos)
                    chunkBuf.clear()
                }
            }

            fun writeFloats(arr: FloatArray, len: Int) {
                for (i in 0 until len) {
                    if (chunkBuf.remaining() < 4) flushChunk()
                    chunkBuf.putFloat(arr[i])
                }
            }

            writeFloats(positions, positionsLen)
            writeFloats(normals, normalsLen)
            writeFloats(texCoords, texCoordsLen)

            if (useIntIndices) {
                for (i in 0 until indexCount) {
                    if (chunkBuf.remaining() < 4) flushChunk()
                    chunkBuf.putInt(indices[i])
                }
            } else {
                for (i in 0 until indexCount) {
                    if (chunkBuf.remaining() < 2) flushChunk()
                    chunkBuf.putShort(indices[i].toShort())
                }
            }
            for (p in 0 until binPadding) {
                if (chunkBuf.remaining() < 1) flushChunk()
                chunkBuf.put(0.toByte())
            }
            flushChunk()
        }
    }

    /**
     * Generates a spatial prism proxy for Apple USDZ packages.
     */
    private fun generateUsdzSpatialProxy(outFile: File) {
        val s = 0.5f
        val positions = floatArrayOf(
            -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s,
            -s, -s, -s,  -s,  s, -s,   s,  s, -s,   s, -s, -s,
            -s,  s, -s,  -s,  s,  s,   s,  s,  s,   s,  s, -s,
            -s, -s, -s,   s, -s, -s,   s, -s,  s,  -s, -s,  s,
             s, -s, -s,   s,  s, -s,   s,  s,  s,   s, -s,  s,
            -s, -s, -s,  -s, -s,  s,  -s,  s,  s,  -s,  s, -s
        )
        val normals = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f,
            1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f
        )
        val texCoords = FloatArray(24 * 2) { 0.5f }
        val indices = IntArray(36)
        var ptr = 0
        for (i in 0 until 6) {
            val base = i * 4
            indices[ptr++] = base; indices[ptr++] = base + 1; indices[ptr++] = base + 2
            indices[ptr++] = base; indices[ptr++] = base + 2; indices[ptr++] = base + 3
        }
        writeBinaryGlbStreaming(
            outFile = outFile,
            positions = positions,
            positionsLen = positions.size,
            normals = normals,
            normalsLen = normals.size,
            texCoords = texCoords,
            texCoordsLen = texCoords.size,
            indices = indices,
            indicesLen = indices.size,
            bounds = BoundingBox(-s, -s, -s, s, s, s)
        )
    }
}
