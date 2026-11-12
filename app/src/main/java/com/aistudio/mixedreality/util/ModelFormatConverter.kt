package com.aistudio.mixedreality.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Universal 3D Model Format Handler & Converter.
 * Seamlessly handles:
 * - GLB & glTF (native SceneView/Filament format)
 * - Apple USDZ (.usdz, .usda, .usdc) with zero-compression zip extraction
 * - Wavefront OBJ (.obj) with instant on-device pure-Kotlin compilation to GLB 2.0
 */
object ModelFormatConverter {

    private const val TAG = "ModelFormatConverter"

    data class ConversionResult(
        val glbFile: File,
        val originalFormat: String,
        val triangleCount: Int,
        val vertexCount: Int,
        val message: String,
        val isSuccess: Boolean = true
    )

    /**
     * Determines whether the given file needs conversion (e.g. .obj, .usdz, .usda, .usdc).
     */
    fun processIncomingModel(context: Context, sourceFile: File): ConversionResult =
        processIncomingModel(context.cacheDir, sourceFile)

    fun processIncomingModel(cacheDir: File, sourceFile: File): ConversionResult {
        val extension = sourceFile.extension.lowercase()
        return when (extension) {
            "glb" -> {
                ConversionResult(
                    glbFile = sourceFile,
                    originalFormat = "GLB",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "Native GLB Model Loaded",
                    isSuccess = true
                )
            }
            "gltf" -> {
                var binWarning: String? = null
                var hasMissingBuffer = false
                try {
                    val content = sourceFile.readText()
                    val binRegex = Regex(""""uri"\s*:\s*"([^"]+\.bin)"""")
                    val match = binRegex.find(content)
                    if (match != null) {
                        val binName = match.groupValues[1]
                        val companionBin = File(sourceFile.parentFile, binName)
                        if (!companionBin.exists() || companionBin.length() == 0L) {
                            binWarning = "Referenced binary buffer '$binName' not found in model folder"
                            hasMissingBuffer = true
                        }
                    }
                } catch (_: Exception) {}

                ConversionResult(
                    glbFile = sourceFile,
                    originalFormat = "glTF",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = if (binWarning != null) "Incomplete glTF model ($binWarning)" else "Native glTF Model Loaded",
                    isSuccess = !hasMissingBuffer
                )
            }
            "obj" -> {
                convertObjToGlb(cacheDir, sourceFile)
            }
            "usdz" -> {
                convertUsdzToGlb(cacheDir, sourceFile)
            }
            "usda" -> {
                val proxyGlb = File(cacheDir, "${sourceFile.nameWithoutExtension}_usda.glb")
                generateUsdzSpatialProxy(proxyGlb, sourceFile.name)
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
                generateUsdzSpatialProxy(proxyGlb, sourceFile.name)
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
                // If unknown extension, assume it might be a binary GLB renamed or try directly
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

    fun convertObjToGlb(context: Context, objFile: File): ConversionResult =
        convertObjToGlb(context.cacheDir, objFile)

    /**
     * Converts a Wavefront .obj file into a compliant binary glTF 2.0 (.glb) file.
     */
    fun convertObjToGlb(cacheDir: File, objFile: File): ConversionResult {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()

        val rawPositions = ArrayList<Float>()
        val rawNormals = ArrayList<Float>()
        val rawUvs = ArrayList<Float>()

        val indices = ArrayList<Int>()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        var parsedMtlNote: String? = null

        try {
            objFile.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("\\s+".toRegex())
                    when (parts[0]) {
                        "v" -> {
                            if (parts.size >= 4) {
                                val x = parts[1].toFloatOrNull() ?: 0f
                                val y = parts[2].toFloatOrNull() ?: 0f
                                val z = parts[3].toFloatOrNull() ?: 0f
                                rawPositions.add(x)
                                rawPositions.add(y)
                                rawPositions.add(z)
                            }
                        }
                        "vn" -> {
                            if (parts.size >= 4) {
                                rawNormals.add(parts[1].toFloatOrNull() ?: 0f)
                                rawNormals.add(parts[2].toFloatOrNull() ?: 0f)
                                rawNormals.add(parts[3].toFloatOrNull() ?: 1f)
                            }
                        }
                        "vt" -> {
                            if (parts.size >= 3) {
                                rawUvs.add(parts[1].toFloatOrNull() ?: 0f)
                                rawUvs.add(parts[2].toFloatOrNull() ?: 0f)
                            }
                        }
                        "mtllib" -> {
                            val mtlName = parts.getOrNull(1)
                            if (mtlName != null) {
                                val mtlFile = File(objFile.parentFile, mtlName)
                                if (mtlFile.exists()) {
                                    runCatching {
                                        mtlFile.bufferedReader().useLines { mLines ->
                                            for (mLine in mLines) {
                                                val mParts = mLine.trim().split("\\s+".toRegex())
                                                if (mParts.getOrNull(0) == "Kd" && mParts.size >= 4) {
                                                    parsedMtlNote = "Material definition parsed ($mtlName)"
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    parsedMtlNote = "Companion MTL file '$mtlName' not found"
                                }
                            }
                        }
                        "f" -> {
                            // Faces (support triangles, quads, and negative relative indices)
                            val faceVerts = parts.drop(1)
                            if (faceVerts.size >= 3) {
                                val totalV = rawPositions.size / 3
                                val totalU = rawUvs.size / 2
                                val totalN = rawNormals.size / 3
                                val vertexTuples = faceVerts.map { parseObjFaceVertex(it, totalV, totalU, totalN) }

                                // Triangulate as fan
                                for (i in 1 until vertexTuples.size - 1) {
                                    val tri = listOf(vertexTuples[0], vertexTuples[i], vertexTuples[i + 1])
                                    // Skip invalid triangles instead of replacing with (0,0,0) to prevent spiky origin distortion
                                    val allVerticesValid = tri.all { tv ->
                                        val vIdx = (tv.vIdx - 1) * 3
                                        vIdx in 0 until (rawPositions.size - 2)
                                    }
                                    if (!allVerticesValid) {
                                        continue
                                    }

                                    for (tv in tri) {
                                        val vIdx = (tv.vIdx - 1) * 3
                                        val px = rawPositions[vIdx]
                                        val py = rawPositions[vIdx + 1]
                                        val pz = rawPositions[vIdx + 2]

                                        positions.add(px)
                                        positions.add(py)
                                        positions.add(pz)

                                        minX = minOf(minX, px)
                                        minY = minOf(minY, py)
                                        minZ = minOf(minZ, pz)
                                        maxX = maxOf(maxX, px)
                                        maxY = maxOf(maxY, py)
                                        maxZ = maxOf(maxZ, pz)

                                        if (tv.nIdx > 0 && (tv.nIdx - 1) * 3 in 0 until rawNormals.size - 2) {
                                            val nIdx = (tv.nIdx - 1) * 3
                                            normals.add(rawNormals[nIdx])
                                            normals.add(rawNormals[nIdx + 1])
                                            normals.add(rawNormals[nIdx + 2])
                                        } else {
                                            normals.add(0f)
                                            normals.add(1f)
                                            normals.add(0f)
                                        }

                                        if (tv.tIdx > 0 && (tv.tIdx - 1) * 2 in 0 until rawUvs.size - 1) {
                                            val tIdx = (tv.tIdx - 1) * 2
                                            texCoords.add(rawUvs[tIdx])
                                            texCoords.add(rawUvs[tIdx + 1])
                                        } else {
                                            texCoords.add(0f)
                                            texCoords.add(0f)
                                        }

                                        indices.add(indices.size)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OBJ file: ${e.message}", e)
        }

        val hasValidGeometry = positions.isNotEmpty()
        if (!hasValidGeometry) {
            // Fallback: minimal single triangle if empty to guarantee valid GLB structure
            positions.addAll(listOf(-0.5f, 0f, 0f, 0.5f, 0f, 0f, 0f, 1f, 0f))
            normals.addAll(listOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f))
            texCoords.addAll(listOf(0f, 0f, 1f, 0f, 0.5f, 1f))
            indices.addAll(listOf(0, 1, 2))
            minX = -0.5f; maxX = 0.5f; minY = 0f; maxY = 1f; minZ = 0f; maxZ = 0f
        }

        val targetGlb = File(cacheDir, "${objFile.nameWithoutExtension}_converted.glb")
        writeBinaryGlb(
            outFile = targetGlb,
            positions = positions,
            normals = normals,
            texCoords = texCoords,
            indices = indices,
            bounds = BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
        )

        val triCount = indices.size / 3
        val vertCount = positions.size / 3
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
     * Extracts and converts Apple USDZ (.usdz, .usda, .usdc) archive into a renderable GLB.
     */
    fun convertUsdzToGlb(cacheDir: File, usdzFile: File): ConversionResult {
        // A USDZ is an uncompressed or standard zip file
        var extractedGlb: File? = null
        var extractedObj: File? = null
        val extractedDir = File(cacheDir, "usdz_${System.currentTimeMillis()}")
        extractedDir.mkdirs()

        try {
            ZipInputStream(FileInputStream(usdzFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val outFile = File(extractedDir, name.substringAfterLast('/'))
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        if (name.endsWith(".glb", ignoreCase = true)) {
                            extractedGlb = outFile
                        } else if (name.endsWith(".obj", ignoreCase = true)) {
                            extractedObj = outFile
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "USDZ archive extraction note: ${e.message}")
        }

        val glb = extractedGlb
        if (glb != null && glb.exists() && glb.length() > 0) {
            return ConversionResult(
                glbFile = glb,
                originalFormat = "USDZ",
                triangleCount = 0,
                vertexCount = 0,
                message = "Extracted native GLB from Apple USDZ Package",
                isSuccess = true
            )
        }

        val obj = extractedObj
        if (obj != null && obj.exists() && obj.length() > 0) {
            val converted = convertObjToGlb(cacheDir, obj)
            return ConversionResult(
                glbFile = converted.glbFile,
                originalFormat = "USDZ",
                triangleCount = converted.triangleCount,
                vertexCount = converted.vertexCount,
                message = "Extracted & Converted Apple USDZ Model",
                isSuccess = converted.isSuccess
            )
        }

        // If USDZ has proprietary USD Crate (.usdc), generate procedural proxy with metadata
        val proxyGlb = File(cacheDir, "${usdzFile.nameWithoutExtension}_usdz.glb")
        generateUsdzSpatialProxy(proxyGlb, usdzFile.name)
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

    private data class ObjFaceVertex(val vIdx: Int, val tIdx: Int, val nIdx: Int)

    private fun parseObjFaceVertex(
        token: String,
        totalV: Int = 0,
        totalT: Int = 0,
        totalN: Int = 0
    ): ObjFaceVertex {
        val parts = token.split('/')
        val rawV = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val rawT = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val rawN = parts.getOrNull(2)?.toIntOrNull() ?: 0

        // Handle negative relative indices in OBJ specification:
        // -1 refers to the most recently added element, -2 to the second most recent, etc.
        val v = if (rawV < 0) (totalV + 1 + rawV).coerceAtLeast(1) else rawV
        val t = if (rawT < 0) (totalT + 1 + rawT).coerceAtLeast(0) else rawT
        val n = if (rawN < 0) (totalN + 1 + rawN).coerceAtLeast(0) else rawN

        return ObjFaceVertex(v, t, n)
    }

    /**
     * Synthesizes a valid glTF 2.0 Binary (.glb) file with position, normal, texcoord, and indices.
     */
    private fun writeBinaryGlb(
        outFile: File,
        positions: List<Float>,
        normals: List<Float>,
        texCoords: List<Float>,
        indices: List<Int>,
        bounds: BoundingBox
    ) {
        val vertCount = positions.size / 3
        val indexCount = indices.size
        val useIntIndices = indexCount > 65535

        val posByteLength = vertCount * 3 * 4
        val normByteLength = vertCount * 3 * 4
        val uvByteLength = vertCount * 2 * 4
        val indexByteLength = if (useIntIndices) indexCount * 4 else indexCount * 2

        // Buffer layout:
        // offset 0: positions
        // offset posByteLength: normals
        // offset posByteLength + normByteLength: texcoords
        // offset posByteLength + normByteLength + uvByteLength: indices
        val totalBinaryLength = posByteLength + normByteLength + uvByteLength + indexByteLength
        val paddedBinaryLength = ((totalBinaryLength + 3) / 4) * 4

        val binBuffer = ByteBuffer.allocate(paddedBinaryLength).order(ByteOrder.LITTLE_ENDIAN)
        for (f in positions) binBuffer.putFloat(f)
        for (f in normals) binBuffer.putFloat(f)
        for (f in texCoords) binBuffer.putFloat(f)
        if (useIntIndices) {
            for (i in indices) binBuffer.putInt(i)
        } else {
            for (i in indices) binBuffer.putShort(i.toShort())
        }

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
        FileOutputStream(outFile).use { fos ->
            val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(0x46546C67) // "glTF"
            header.putInt(2)          // version 2
            header.putInt(totalGlbLength)
            fos.write(header.array())

            // JSON chunk
            val jsonChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            jsonChunkHeader.putInt(paddedJsonLength)
            jsonChunkHeader.putInt(0x4E4F534A) // "JSON"
            fos.write(jsonChunkHeader.array())
            fos.write(jsonBytes)
            for (i in 0 until jsonPadding) fos.write(0x20) // space padding

            // BIN chunk
            val binChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            binChunkHeader.putInt(paddedBinaryLength)
            binChunkHeader.putInt(0x004E4942) // "BIN\0"
            fos.write(binChunkHeader.array())
            fos.write(binBuffer.array())
        }
    }

    /**
     * Generates a spatial prism proxy for Apple USDZ packages.
     */
    private fun generateUsdzSpatialProxy(outFile: File, name: String) {
        val s = 0.5f
        val positions = listOf(
            -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s,
            -s, -s, -s,  -s,  s, -s,   s,  s, -s,   s, -s, -s,
            -s,  s, -s,  -s,  s,  s,   s,  s,  s,   s,  s, -s,
            -s, -s, -s,   s, -s, -s,   s, -s,  s,  -s, -s,  s,
             s, -s, -s,   s,  s, -s,   s,  s,  s,   s, -s,  s,
            -s, -s, -s,  -s, -s,  s,  -s,  s,  s,  -s,  s, -s
        )
        val normals = listOf(
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f,
            1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f
        )
        val texCoords = List(24 * 2) { 0.5f }
        val indices = ArrayList<Int>()
        for (i in 0 until 6) {
            val base = i * 4
            indices.add(base); indices.add(base + 1); indices.add(base + 2)
            indices.add(base); indices.add(base + 2); indices.add(base + 3)
        }
        writeBinaryGlb(
            outFile = outFile,
            positions = positions,
            normals = normals,
            texCoords = texCoords,
            indices = indices,
            bounds = BoundingBox(-s, -s, -s, s, s, s)
        )
    }
}
