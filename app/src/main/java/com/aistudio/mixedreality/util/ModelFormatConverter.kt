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
import kotlin.math.sqrt

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
        val isSuccess: Boolean = true
    )

    /**
     * Zero-boxing dynamic FloatArray builder (~6x lower RAM than ArrayList<Float>).
     */
    private class FloatArrayBuilder(initialCapacity: Int = 1024) {
        var data = FloatArray(initialCapacity.coerceAtLeast(16))
            private set
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) {
                val newCap = if (data.size < 1_048_576) data.size * 2 else data.size + (data.size ushr 1)
                data = data.copyOf(newCap.coerceAtLeast(size + 16))
            }
            data[size++] = v
        }

        fun add3(x: Float, y: Float, z: Float) {
            ensureCapacity(3)
            data[size++] = x
            data[size++] = y
            data[size++] = z
        }

        fun add2(u: Float, v: Float) {
            ensureCapacity(2)
            data[size++] = u
            data[size++] = v
        }

        private fun ensureCapacity(extra: Int) {
            val needed = size + extra
            if (needed > data.size) {
                var newCap = if (data.size < 1_048_576) data.size * 2 else data.size + (data.size ushr 1)
                if (newCap < needed) newCap = needed + 64
                data = data.copyOf(newCap)
            }
        }

        fun clearAndRelease() {
            data = FloatArray(0)
            size = 0
        }
    }

    /**
     * Zero-boxing dynamic IntArray builder.
     */
    private class IntArrayBuilder(initialCapacity: Int = 1024) {
        var data = IntArray(initialCapacity.coerceAtLeast(16))
            private set
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) {
                val newCap = if (data.size < 1_048_576) data.size * 2 else data.size + (data.size ushr 1)
                data = data.copyOf(newCap.coerceAtLeast(size + 16))
            }
            data[size++] = v
        }

        fun clearAndRelease() {
            data = IntArray(0)
            size = 0
        }
    }

    /**
     * Primitive open-addressing hash map mapping packed (vIdx, tIdx, nIdx) -> output vertex index.
     * Eliminates duplicate vertex emission for shared corners, cutting GPU VRAM and vertex load 2x-3x.
     */
    private class VertexDedupMap(initialPowerOfTwo: Int = 1024) {
        private var keys = LongArray(initialPowerOfTwo) { EMPTY_KEY }
        private var values = IntArray(initialPowerOfTwo)
        private var mask = initialPowerOfTwo - 1
        private var count = 0
        private var maxFill = (initialPowerOfTwo * 0.65f).toInt()

        fun getOrPut(key: Long, newValue: Int): Int {
            if (count >= maxFill) rehash()
            var slot = mixHash(key) and mask
            while (true) {
                val existing = keys[slot]
                if (existing == EMPTY_KEY) {
                    keys[slot] = key
                    values[slot] = newValue
                    count++
                    return newValue
                }
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
            }
        }

        private fun rehash() {
            val oldKeys = keys
            val oldValues = values
            val newCap = oldKeys.size shl 1
            keys = LongArray(newCap) { EMPTY_KEY }
            values = IntArray(newCap)
            mask = newCap - 1
            maxFill = (newCap * 0.65f).toInt()
            for (i in oldKeys.indices) {
                val k = oldKeys[i]
                if (k != EMPTY_KEY) {
                    var slot = mixHash(k) and mask
                    while (keys[slot] != EMPTY_KEY) {
                        slot = (slot + 1) and mask
                    }
                    keys[slot] = k
                    values[slot] = oldValues[i]
                }
            }
        }

        fun release() {
            keys = LongArray(0)
            values = IntArray(0)
        }

        companion object {
            private const val EMPTY_KEY = -1L

            fun mixHash(k: Long): Int {
                var h = k
                h = h xor (h ushr 33)
                h *= -0xae502812aa7333L
                h = h xor (h ushr 33)
                return h.toInt()
            }
        }
    }

    fun processIncomingModel(context: Context, sourceFile: File): ConversionResult =
        processIncomingModel(context.cacheDir, sourceFile)

    fun processIncomingModel(cacheDir: File, sourceFile: File): ConversionResult {
        val extension = sourceFile.extension.lowercase()
        return when (extension) {
            "glb" -> {
                val info = StreamingMeshProcessor.inspectGlbMetadata(sourceFile)
                val finalFile: File
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
                        outVerts = optInfo.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        outTris = optInfo.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        msg = "Dense GLB (${formatModelCount(info.triangleCount)} tris) optimized to ${formatModelCount(optInfo.triangleCount)} tris for GPU"
                    } else {
                        finalFile = sourceFile
                        outVerts = info.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        outTris = info.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        msg = "Native GLB (${formatModelCount(info.triangleCount)} tris)"
                    }
                } else {
                    finalFile = sourceFile
                    outVerts = info.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    outTris = info.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    msg = if (info.isValidGlb && info.triangleCount > 0L) {
                        "Native GLB (${formatModelCount(info.triangleCount)} tris)"
                    } else {
                        "Native GLB Model Loaded"
                    }
                }

                ConversionResult(
                    glbFile = finalFile,
                    originalFormat = "GLB",
                    triangleCount = outTris,
                    vertexCount = outVerts,
                    message = msg,
                    isSuccess = true
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
            "usda" -> {
                val proxyGlb = File(cacheDir, "${sourceFile.nameWithoutExtension}_usda.glb")
                generateUsdzSpatialProxy(proxyGlb)
                ConversionResult(
                    glbFile = proxyGlb,
                    originalFormat = "USDZ (Apple)",
                    triangleCount = 12,
                    vertexCount = 24,
                    message = "USDA text format contains no compiled binary geometry. A bounding box proxy is shown.",
                    isSuccess = false
                )
            }
            "usdc" -> {
                val proxyGlb = File(cacheDir, "${sourceFile.nameWithoutExtension}_usdc.glb")
                generateUsdzSpatialProxy(proxyGlb)
                ConversionResult(
                    glbFile = proxyGlb,
                    originalFormat = "USDZ (Apple)",
                    triangleCount = 12,
                    vertexCount = 24,
                    message = "USDC binary Crate format contains proprietary Apple geometry. A bounding box proxy is shown.",
                    isSuccess = false
                )
            }
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

    private fun formatModelCount(n: Long): String = when {
        n >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format(java.util.Locale.US, "%.0fK", n / 1_000.0)
        else -> n.toString()
    }

    fun convertObjToGlb(context: Context, objFile: File): ConversionResult =
        convertObjToGlb(context.cacheDir, objFile)

    /**
     * Converts Wavefront .obj files into compliant binary glTF 2.0 (.glb).
     * Files >= 8 MB (or dense multi-million vertex/polygon meshes up to 30M+) use disk-paged streaming + clustering.
     */
    fun convertObjToGlb(cacheDir: File, objFile: File): ConversionResult {
        if (objFile.length() >= 8L * 1024L * 1024L) {
            return StreamingMeshProcessor.convertObjToGlbStreaming(cacheDir, objFile)
        }
        val approxVertEstimate = (objFile.length() / 48L).toInt().coerceIn(512, 262_144)
        val rawPositions = FloatArrayBuilder(approxVertEstimate * 3)
        val rawNormals = FloatArrayBuilder(approxVertEstimate * 3)
        val rawUvs = FloatArrayBuilder(approxVertEstimate * 2)

        val positions = FloatArrayBuilder(approxVertEstimate * 3)
        val normals = FloatArrayBuilder(approxVertEstimate * 3)
        val texCoords = FloatArrayBuilder(approxVertEstimate * 2)
        val indices = IntArrayBuilder(approxVertEstimate * 3)
        var dedupMap: VertexDedupMap? = VertexDedupMap(minOf(Integer.highestOneBit(approxVertEstimate.coerceAtLeast(1024)) shl 1, 131_072))

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        var parsedMtlNote: String? = null

        // Scratch arrays for parsing a single polygon face without allocations
        var faceV = IntArray(16)
        var faceT = IntArray(16)
        var faceN = IntArray(16)

        try {
            BufferedReader(
                InputStreamReader(BufferedInputStream(FileInputStream(objFile), 131_072), Charsets.UTF_8),
                131_072
            ).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val len = line.length
                    var cursor = skipWhitespace(line, 0, len)
                    if (cursor >= len || line[cursor] == '#') continue

                    val cmdEnd = findWhitespace(line, cursor, len)
                    val cmdLen = cmdEnd - cursor
                    val c0 = line[cursor]

                    when {
                        cmdLen == 1 && c0 == 'v' -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            val xEnd = findWhitespace(line, cursor, len)
                            if (xEnd > cursor) {
                                val x = parseFastFloat(line, cursor, xEnd)
                                cursor = skipWhitespace(line, xEnd, len)
                                val yEnd = findWhitespace(line, cursor, len)
                                if (yEnd > cursor) {
                                    val y = parseFastFloat(line, cursor, yEnd)
                                    cursor = skipWhitespace(line, yEnd, len)
                                    val zEnd = findWhitespace(line, cursor, len)
                                    if (zEnd > cursor) {
                                        val z = parseFastFloat(line, cursor, zEnd)
                                        rawPositions.add3(x, y, z)
                                    }
                                }
                            }
                        }
                        cmdLen == 2 && c0 == 'v' && line[cursor + 1] == 'n' -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            val xEnd = findWhitespace(line, cursor, len)
                            if (xEnd > cursor) {
                                val nx = parseFastFloat(line, cursor, xEnd)
                                cursor = skipWhitespace(line, xEnd, len)
                                val yEnd = findWhitespace(line, cursor, len)
                                if (yEnd > cursor) {
                                    val ny = parseFastFloat(line, cursor, yEnd)
                                    cursor = skipWhitespace(line, yEnd, len)
                                    val zEnd = findWhitespace(line, cursor, len)
                                    if (zEnd > cursor) {
                                        val nz = parseFastFloat(line, cursor, zEnd, defaultVal = 1f)
                                        rawNormals.add3(nx, ny, nz)
                                    }
                                }
                            }
                        }
                        cmdLen == 2 && c0 == 'v' && line[cursor + 1] == 't' -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            val uEnd = findWhitespace(line, cursor, len)
                            if (uEnd > cursor) {
                                val u = parseFastFloat(line, cursor, uEnd)
                                cursor = skipWhitespace(line, uEnd, len)
                                val vEnd = findWhitespace(line, cursor, len)
                                if (vEnd > cursor) {
                                    val v = parseFastFloat(line, cursor, vEnd)
                                    rawUvs.add2(u, v)
                                }
                            }
                        }
                        cmdLen == 1 && c0 == 'f' -> {
                            val totalV = rawPositions.size / 3
                            val totalT = rawUvs.size / 2
                            val totalN = rawNormals.size / 3

                            var faceCount = 0
                            cursor = skipWhitespace(line, cmdEnd, len)
                            while (cursor < len) {
                                val tokEnd = findWhitespace(line, cursor, len)
                                if (tokEnd > cursor) {
                                    if (faceCount == faceV.size) {
                                        val newCap = faceV.size * 2
                                        faceV = faceV.copyOf(newCap)
                                        faceT = faceT.copyOf(newCap)
                                        faceN = faceN.copyOf(newCap)
                                    }
                                    parseObjFaceToken(
                                        line = line,
                                        start = cursor,
                                        end = tokEnd,
                                        totalV = totalV,
                                        totalT = totalT,
                                        totalN = totalN,
                                        outV = faceV,
                                        outT = faceT,
                                        outN = faceN,
                                        slot = faceCount
                                    )
                                    faceCount++
                                }
                                cursor = skipWhitespace(line, tokEnd, len)
                            }

                            if (faceCount >= 3) {
                                val canDedup = dedupMap != null &&
                                    totalV <= 1_048_575 && totalT <= 1_048_575 && totalN <= 1_048_575
                                if (!canDedup && dedupMap != null) {
                                    dedupMap.release()
                                    dedupMap = null
                                }

                                val v0Raw = (faceV[0] - 1) * 3
                                if (v0Raw !in 0 until (rawPositions.size - 2)) continue

                                for (i in 1 until faceCount - 1) {
                                    val v1Raw = (faceV[i] - 1) * 3
                                    val v2Raw = (faceV[i + 1] - 1) * 3
                                    if (v1Raw !in 0 until (rawPositions.size - 2) ||
                                        v2Raw !in 0 until (rawPositions.size - 2)
                                    ) {
                                        continue
                                    }

                                    // Precompute flat geometric normal once only if any corner lacks vn
                                    var faceNx = 0f
                                    var faceNy = 1f
                                    var faceNz = 0f
                                    val n0Missing = faceN[0] <= 0 || (faceN[0] - 1) * 3 !in 0 until (rawNormals.size - 2)
                                    val n1Missing = faceN[i] <= 0 || (faceN[i] - 1) * 3 !in 0 until (rawNormals.size - 2)
                                    val n2Missing = faceN[i + 1] <= 0 || (faceN[i + 1] - 1) * 3 !in 0 until (rawNormals.size - 2)
                                    if (n0Missing || n1Missing || n2Missing) {
                                        val ax = rawPositions.data[v1Raw] - rawPositions.data[v0Raw]
                                        val ay = rawPositions.data[v1Raw + 1] - rawPositions.data[v0Raw + 1]
                                        val az = rawPositions.data[v1Raw + 2] - rawPositions.data[v0Raw + 2]
                                        val bx = rawPositions.data[v2Raw] - rawPositions.data[v0Raw]
                                        val by = rawPositions.data[v2Raw + 1] - rawPositions.data[v0Raw + 1]
                                        val bz = rawPositions.data[v2Raw + 2] - rawPositions.data[v0Raw + 2]
                                        val cx = ay * bz - az * by
                                        val cy = az * bx - ax * bz
                                        val cz = ax * by - ay * bx
                                        val nLen = sqrt(cx * cx + cy * cy + cz * cz)
                                        if (nLen > 1e-6f && nLen.isFinite()) {
                                            val inv = 1f / nLen
                                            faceNx = cx * inv
                                            faceNy = cy * inv
                                            faceNz = cz * inv
                                        }
                                    }

                                    for (corner in 0 until 3) {
                                        val slot = when (corner) {
                                            0 -> 0
                                            1 -> i
                                            else -> i + 1
                                        }
                                        val vIdx1 = faceV[slot]
                                        val tIdx1 = faceT[slot]
                                        val nIdx1 = faceN[slot]

                                        val vBase = (vIdx1 - 1) * 3
                                        val px = rawPositions.data[vBase]
                                        val py = rawPositions.data[vBase + 1]
                                        val pz = rawPositions.data[vBase + 2]

                                        if (px < minX) minX = px
                                        if (py < minY) minY = py
                                        if (pz < minZ) minZ = pz
                                        if (px > maxX) maxX = px
                                        if (py > maxY) maxY = py
                                        if (pz > maxZ) maxZ = pz

                                        val hasNormal = nIdx1 > 0 && (nIdx1 - 1) * 3 in 0 until (rawNormals.size - 2)
                                        val hasUv = tIdx1 > 0 && (tIdx1 - 1) * 2 in 0 until (rawUvs.size - 1)
                                        val currentVertCount = positions.size / 3

                                        val emittedIdx = if (canDedup && hasNormal && dedupMap != null) {
                                            val packKey = (vIdx1.toLong() shl 42) or
                                                ((if (hasUv) tIdx1.toLong() else 0L) shl 21) or
                                                nIdx1.toLong()
                                            dedupMap.getOrPut(packKey, currentVertCount)
                                        } else {
                                            currentVertCount
                                        }

                                        if (emittedIdx == currentVertCount) {
                                            positions.add3(px, py, pz)
                                            if (hasNormal) {
                                                val nBase = (nIdx1 - 1) * 3
                                                normals.add3(
                                                    rawNormals.data[nBase],
                                                    rawNormals.data[nBase + 1],
                                                    rawNormals.data[nBase + 2]
                                                )
                                            } else {
                                                normals.add3(faceNx, faceNy, faceNz)
                                            }
                                            if (hasUv) {
                                                val tBase = (tIdx1 - 1) * 2
                                                texCoords.add2(rawUvs.data[tBase], rawUvs.data[tBase + 1])
                                            } else {
                                                texCoords.add2(0f, 0f)
                                            }
                                        }
                                        indices.add(emittedIdx)
                                    }
                                }
                            }
                        }
                        cmdLen == 6 && line.startsWith("mtllib", cursor) -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            if (cursor < len) {
                                val mtlName = line.substring(cursor, len).trim()
                                val mtlFile = File(objFile.parentFile, mtlName)
                                if (mtlFile.exists()) {
                                    runCatching {
                                        mtlFile.bufferedReader().useLines { mLines ->
                                            for (mLine in mLines) {
                                                if (mLine.trimStart().startsWith("Kd")) {
                                                    parsedMtlNote = "Material definition parsed ($mtlName)"
                                                    break
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    parsedMtlNote = "Companion MTL file '$mtlName' not found"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OBJ file: ${e.message}", e)
        } finally {
            // Release raw scratch arrays BEFORE serializing GLB to halve peak RAM
            rawPositions.clearAndRelease()
            rawNormals.clearAndRelease()
            rawUvs.clearAndRelease()
            dedupMap?.release()
            dedupMap = null
        }

        val hasValidGeometry = positions.size > 0 && indices.size >= 3
        if (!hasValidGeometry) {
            positions.add3(-0.5f, 0f, 0f)
            positions.add3(0.5f, 0f, 0f)
            positions.add3(0f, 1f, 0f)
            normals.add3(0f, 0f, 1f)
            normals.add3(0f, 0f, 1f)
            normals.add3(0f, 0f, 1f)
            texCoords.add2(0f, 0f)
            texCoords.add2(1f, 0f)
            texCoords.add2(0.5f, 1f)
            indices.add(0); indices.add(1); indices.add(2)
            minX = -0.5f; maxX = 0.5f; minY = 0f; maxY = 1f; minZ = 0f; maxZ = 0f
        }

        val vertCount = positions.size / 3
        val indexCount = indices.size
        val triCount = indexCount / 3

        val targetGlb = File(cacheDir, "${objFile.nameWithoutExtension}_converted.glb")
        writeBinaryGlbStreaming(
            outFile = targetGlb,
            positions = positions.data,
            positionsLen = positions.size,
            normals = normals.data,
            normalsLen = normals.size,
            texCoords = texCoords.data,
            texCoordsLen = texCoords.size,
            indices = indices.data,
            indicesLen = indexCount,
            bounds = BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
        )

        positions.clearAndRelease()
        normals.clearAndRelease()
        texCoords.clearAndRelease()
        indices.clearAndRelease()

        val baseMsg = if (hasValidGeometry) "Converted OBJ ($triCount triangles) to Native GLB" else "No valid faces found in OBJ file"
        val fullMsg = if (parsedMtlNote != null) "$baseMsg ($parsedMtlNote)" else baseMsg

        return ConversionResult(
            glbFile = targetGlb,
            originalFormat = "OBJ",
            triangleCount = triCount,
            vertexCount = vertCount,
            message = fullMsg,
            isSuccess = hasValidGeometry
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
        val extractedDir = File(cacheDir, "usdz_${System.currentTimeMillis()}")
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
                            when {
                                name.endsWith(".glb", ignoreCase = true) && extractedGlb == null -> extractedGlb = outFile
                                name.endsWith(".gltf", ignoreCase = true) && extractedGltf == null -> extractedGltf = outFile
                                name.endsWith(".obj", ignoreCase = true) && extractedObj == null -> extractedObj = outFile
                                name.endsWith(".3mf", ignoreCase = true) && extracted3mf == null -> extracted3mf = outFile
                                name.endsWith(".stl", ignoreCase = true) && extractedStl == null -> extractedStl = outFile
                                name.endsWith(".ply", ignoreCase = true) && extractedPly == null -> extractedPly = outFile
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
                isSuccess = converted.isSuccess
            )
        }

        val proxyGlb = File(cacheDir, "${usdzFile.nameWithoutExtension}_usdz.glb")
        generateUsdzSpatialProxy(proxyGlb)
        return ConversionResult(
            glbFile = proxyGlb,
            originalFormat = "USDZ (Apple)",
            triangleCount = 12,
            vertexCount = 24,
            message = "Apple USDZ contains proprietary binary USDC/USDA. A spatial bounding proxy is shown; convert to GLB for full fidelity.",
            isSuccess = false
        )
    }

    private data class BoundingBox(
        val minX: Float, val minY: Float, val minZ: Float,
        val maxX: Float, val maxY: Float, val maxZ: Float
    )

    private fun skipWhitespace(s: String, start: Int, len: Int): Int {
        var i = start
        while (i < len && s[i] <= ' ') i++
        return i
    }

    private fun findWhitespace(s: String, start: Int, len: Int): Int {
        var i = start
        while (i < len && s[i] > ' ') i++
        return i
    }

    private fun parseFastFloat(s: String, start: Int, end: Int, defaultVal: Float = 0f): Float {
        if (start >= end) return defaultVal
        var i = start
        var neg = false
        when (s[i]) {
            '-' -> { neg = true; i++ }
            '+' -> i++
        }
        if (i >= end) return defaultVal

        var intPart = 0.0
        var fracPart = 0.0
        var fracDiv = 1.0
        var hasDigits = false

        while (i < end) {
            val c = s[i]
            if (c in '0'..'9') {
                intPart = intPart * 10.0 + (c - '0')
                hasDigits = true
                i++
            } else {
                break
            }
        }
        if (i < end && s[i] == '.') {
            i++
            while (i < end) {
                val c = s[i]
                if (c in '0'..'9') {
                    fracPart = fracPart * 10.0 + (c - '0')
                    fracDiv *= 10.0
                    hasDigits = true
                    i++
                } else {
                    break
                }
            }
        }
        if (i < end && (s[i] == 'e' || s[i] == 'E')) {
            val fallback = s.substring(start, end).toFloatOrNull()
            return if (fallback != null && fallback.isFinite()) fallback else defaultVal
        }
        if (i < end || !hasDigits) {
            val fallback = s.substring(start, end).toFloatOrNull()
            return if (fallback != null && fallback.isFinite()) fallback else defaultVal
        }
        val value = (intPart + fracPart / fracDiv).toFloat()
        val signed = if (neg) -value else value
        return if (signed.isFinite()) signed else defaultVal
    }

    private fun parseFastInt(s: String, start: Int, end: Int, defaultVal: Int): Int {
        if (start >= end) return defaultVal
        var i = start
        var neg = false
        when (s[i]) {
            '-' -> { neg = true; i++ }
            '+' -> i++
        }
        if (i >= end) return defaultVal
        var acc = 0
        while (i < end) {
            val d = s[i] - '0'
            if (d !in 0..9) return defaultVal
            acc = acc * 10 + d
            i++
        }
        return if (neg) -acc else acc
    }

    private fun parseObjFaceToken(
        line: String,
        start: Int,
        end: Int,
        totalV: Int,
        totalT: Int,
        totalN: Int,
        outV: IntArray,
        outT: IntArray,
        outN: IntArray,
        slot: Int
    ) {
        val slash1 = line.indexOf('/', start)
        val rawV: Int
        val rawT: Int
        val rawN: Int
        if (slash1 < 0 || slash1 >= end) {
            rawV = parseFastInt(line, start, end, 1)
            rawT = 0
            rawN = 0
        } else {
            rawV = parseFastInt(line, start, slash1, 1)
            val slash2 = line.indexOf('/', slash1 + 1)
            if (slash2 < 0 || slash2 >= end) {
                rawT = parseFastInt(line, slash1 + 1, end, 0)
                rawN = 0
            } else {
                rawT = if (slash2 > slash1 + 1) parseFastInt(line, slash1 + 1, slash2, 0) else 0
                rawN = if (end > slash2 + 1) parseFastInt(line, slash2 + 1, end, 0) else 0
            }
        }
        outV[slot] = if (rawV < 0) (totalV + 1 + rawV).coerceAtLeast(1) else rawV
        outT[slot] = if (rawT < 0) (totalT + 1 + rawT).coerceAtLeast(0) else rawT
        outN[slot] = if (rawN < 0) (totalN + 1 + rawN).coerceAtLeast(0) else rawN
    }

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
