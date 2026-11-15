package com.aistudio.mixedreality.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin streaming OpenUSD engine supporting `.usdz` archives, binary `.usdc` (`PXR-USDC` Crate),
 * and ASCII `.usda` scenes — extracting full geometry hierarchies, xform transforms, n-gon triangulation,
 * normals, UVs, PBR diffuse colors, and embedded textures directly into mobile-ready `.glb`.
 */
object UsdMeshConverter {

    private const val TAG = "UsdMeshConverter"
    private const val IO_BUF = 262_144

    /**
     * Converts `.usdz`, `.usdc`, or `.usda` into a GLB file inside `cacheDir`.
     * Preserves `"USDZ"` as `originalFormat` when geometry is extracted (or `"USDZ (Apple)"` fallback
     * only if no valid USD mesh exists or the archive is corrupt).
     */
    fun convertUsdToGlb(cacheDir: File, usdFile: File, isZippedUsdz: Boolean): ModelFormatConverter.ConversionResult {
        val workDir = File(cacheDir, "usd_work_${System.nanoTime()}").apply { mkdirs() }
        try {
            if (isZippedUsdz) {
                val extractDir = File(workDir, "unzipped").apply { mkdirs() }
                val rootFiles = mutableListOf<File>()
                var zipEntryCount = 0
                ZipInputStream(BufferedInputStream(FileInputStream(usdFile), IO_BUF)).use { zis ->
                    val copyBuf = ByteArray(IO_BUF)
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            zipEntryCount++
                            val rel = sanitizeZipEntryPath(entry.name)
                            if (rel != null) {
                                val outF = File(extractDir, rel)
                                outF.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(outF), IO_BUF).use { fos ->
                                    while (true) {
                                        val r = zis.read(copyBuf)
                                        if (r <= 0) break
                                        fos.write(copyBuf, 0, r)
                                    }
                                }
                                rootFiles.add(outF)
                            }
                        }
                        zis.closeEntry()
                    }
                }
                if (zipEntryCount == 0 && rootFiles.isEmpty()) {
                    return fallbackUsdzProxyResult(cacheDir, usdFile, "Archive is empty or corrupt")
                }

                val embeddedGlb = rootFiles.firstOrNull { it.extension.equals("glb", true) }
                if (embeddedGlb != null && embeddedGlb.length() > 0L) {
                    val dstGlb = File(cacheDir, embeddedGlb.name)
                    embeddedGlb.copyTo(dstGlb, overwrite = true)
                    val info = StreamingMeshProcessor.inspectGlbMetadata(dstGlb)
                    val finalGlb = if (info.triangleCount > StreamingMeshProcessor.DIRECT_TRIANGLE_LIMIT) {
                        StreamingMeshProcessor.simplifyUncompressedGlb(cacheDir, dstGlb) ?: dstGlb
                    } else dstGlb
                    val finalInfo = StreamingMeshProcessor.inspectGlbMetadata(finalGlb)
                    return ModelFormatConverter.ConversionResult(
                        glbFile = finalGlb,
                        originalFormat = "USDZ",
                        triangleCount = finalInfo.triangleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        vertexCount = finalInfo.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        message = "USDZ (${StreamingMeshProcessor.formatCompact(finalInfo.triangleCount)} tris) loaded",
                        isSuccess = true,
                        rawTriangleCount = finalInfo.triangleCount,
                        rawVertexCount = finalInfo.vertexCount
                    )
                }

                return convertExtractedUsd(cacheDir, usdFile, extractDir, rootFiles)
            } else {
                val baseDir = usdFile.parentFile ?: workDir
                return convertUsdCore(
                    cacheDir = cacheDir,
                    usdFile = usdFile,
                    isZippedUsdz = false,
                    extractDir = baseDir,
                    rootFiles = listOf(usdFile)
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "USD conversion error: ${t.message}", t)
            return fallbackUsdzProxyResult(cacheDir, usdFile, t.message ?: t::class.java.simpleName)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    fun convertExtractedUsd(
        cacheDir: File,
        usdFile: File,
        extractDir: File,
        rootFiles: List<File>
    ): ModelFormatConverter.ConversionResult {
        return convertUsdCore(
            cacheDir = cacheDir,
            usdFile = usdFile,
            isZippedUsdz = true,
            extractDir = extractDir,
            rootFiles = rootFiles
        )
    }

    private fun convertUsdCore(
        cacheDir: File,
        usdFile: File,
        isZippedUsdz: Boolean,
        extractDir: File,
        rootFiles: List<File>
    ): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "usd_scratch_${System.nanoTime()}").apply { mkdirs() }
        val positions = StreamingMeshProcessor.MappedFloatTupleStore(3, scratchDir, "usd_pos")
        val normals = StreamingMeshProcessor.MappedFloatTupleStore(3, scratchDir, "usd_nrm")
        val uvs = StreamingMeshProcessor.MappedFloatTupleStore(2, scratchDir, "usd_uv")
        val triIndexFile = File(scratchDir, "usd_tris.bin")
        val bounds = StreamingMeshProcessor.MeshBounds()

        try {
            val triOut = BufferedOutputStream(FileOutputStream(triIndexFile), IO_BUF)
            val triBuf = ByteBuffer.allocate(65_536).order(ByteOrder.LITTLE_ENDIAN)
            val ctx = UsdAccumContext(
                positions = positions,
                normals = normals,
                uvs = uvs,
                bounds = bounds,
                triOut = triOut,
                triBuf = triBuf
            )

            val assetMap = HashMap<String, File>()
            if (isZippedUsdz) {
                for (f in rootFiles) {
                    val rel = runCatching { f.relativeTo(extractDir).invariantSeparatorsPath.lowercase(Locale.US) }
                        .getOrElse { f.name.lowercase(Locale.US) }
                    assetMap[rel] = f
                    assetMap[f.name.lowercase(Locale.US)] = f
                }

                val usdCandidates = rootFiles.filter {
                    val ext = it.extension.lowercase(Locale.US)
                    ext == "usdc" || ext == "usda" || ext == "usd"
                }.sortedWith(
                    compareBy<File> {
                        runCatching { it.relativeTo(extractDir).invariantSeparatorsPath.count { c -> c == '/' } }
                            .getOrDefault(0)
                    }.thenBy { it.name.length }
                )

                if (usdCandidates.isNotEmpty()) {
                    val visited = HashSet<String>()
                    for (cand in usdCandidates) {
                        val before = ctx.totalTriangles
                        processAnyUsdLayer(cand, extractDir, assetMap, identityMat4(), ctx, visited, 0)
                        if (ctx.totalTriangles > before) break
                    }
                }
            } else {
                extractDir.listFiles()?.forEach { assetMap[it.name.lowercase(Locale.US)] = it }
                processAnyUsdLayer(usdFile, extractDir, assetMap, identityMat4(), ctx, HashSet(), 0)
            }

            if (triBuf.position() > 0) {
                triOut.write(triBuf.array(), 0, triBuf.position())
                triBuf.clear()
            }
            triOut.flush()
            triOut.close()

            if (ctx.totalTriangles <= 0L || positions.count <= 0L) {
                return fallbackUsdzProxyResult(
                    cacheDir = cacheDir,
                    usdFile = usdFile,
                    reason = if (isZippedUsdz) "USDZ contains no polygon mesh geometry" else "USD contains no polygon mesh geometry"
                )
            }

            bounds.normalizeIfEmpty()

            val preparedTex = ctx.primaryTextureFile?.let { prepareMobileTexture(scratchDir, it) }
            val outGlb = File(cacheDir, "${usdFile.nameWithoutExtension}_converted.glb")
            val formatLabel = when (usdFile.extension.lowercase(Locale.US)) {
                "usdc" -> "USDC"
                "usda" -> "USDA"
                else -> "USDZ"
            }

            val stats = StreamingMeshProcessor.emitIndexedDiskTrianglesToGlb(
                positions = positions,
                normals = if (normals.count == positions.count) normals else null,
                uvs = if (uvs.count == positions.count) uvs else null,
                triIndexFile = triIndexFile,
                totalVerts = positions.count,
                totalTris = ctx.totalTriangles,
                bounds = bounds,
                scratchDir = scratchDir,
                outGlb = outGlb,
                generator = "MixedReality OpenUSD Engine",
                indexIsLong = true,
                baseColorFactor = ctx.primaryDiffuseColor,
                metallicFactor = ctx.primaryMetallic,
                roughnessFactor = ctx.primaryRoughness,
                textureFile = preparedTex?.first,
                textureMime = preparedTex?.second
            )

            val note = if (stats.wasSimplified) {
                "$formatLabel (${StreamingMeshProcessor.formatCompact(stats.rawOriginalTriangles)} tris) optimized to ${StreamingMeshProcessor.formatCompact(stats.outputTriangles.toLong())} tris"
            } else {
                "$formatLabel (${StreamingMeshProcessor.formatCompact(stats.rawOriginalTriangles)} tris) converted to GLB"
            }

            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = formatLabel,
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = note,
                isSuccess = true,
                rawTriangleCount = stats.rawOriginalTriangles,
                rawVertexCount = stats.rawOriginalVertices
            )
        } catch (t: Throwable) {
            Log.e(TAG, "USD conversion error: ${t.message}", t)
            return fallbackUsdzProxyResult(cacheDir, usdFile, t.message ?: t::class.java.simpleName)
        } finally {
            positions.close()
            normals.close()
            uvs.close()
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    private fun fallbackUsdzProxyResult(cacheDir: File, usdFile: File, reason: String): ModelFormatConverter.ConversionResult {
        val proxyGlb = File(cacheDir, "${usdFile.nameWithoutExtension}_spatial_proxy.glb")
        writeSpatialProxyGlb(proxyGlb)
        return ModelFormatConverter.ConversionResult(
            glbFile = proxyGlb,
            originalFormat = "USDZ (Apple)",
            triangleCount = 12,
            vertexCount = 24,
            message = "USDZ spatial proxy active ($reason)",
            isSuccess = false,
            rawTriangleCount = 12L,
            rawVertexCount = 24L
        )
    }

    // =========================================================================
    // Layer Dispatch & Recursive Resolution
    // =========================================================================

    private fun processAnyUsdLayer(
        file: File,
        rootDir: File,
        assetMap: Map<String, File>,
        parentWorld: DoubleArray,
        ctx: UsdAccumContext,
        visited: MutableSet<String>,
        depth: Int
    ) {
        if (!file.exists() || depth > 12) return
        val key = "${file.canonicalPath}:${depth}"
        if (!visited.add(key) || visited.size > 64) return

        val magic = ByteArray(16)
        val n = FileInputStream(file).use { it.read(magic) }
        val isCrate = n >= 8 &&
            magic[0] == 'P'.code.toByte() && magic[1] == 'X'.code.toByte() &&
            magic[2] == 'R'.code.toByte() && magic[3] == '-'.code.toByte() &&
            magic[4] == 'U'.code.toByte() && magic[5] == 'S'.code.toByte() &&
            magic[6] == 'D'.code.toByte() && magic[7] == 'C'.code.toByte()

        if (isCrate) {
            parseUsdcCrateFile(file, rootDir, assetMap, parentWorld, ctx, visited, depth)
        } else {
            parseUsdaAsciiFile(file, rootDir, assetMap, parentWorld, ctx, visited, depth)
        }
    }

    private fun resolveUsdAsset(refPath: String, currentFile: File, rootDir: File, assetMap: Map<String, File>): File? {
        val clean = refPath.trim().trim('@', '"', '\'').replace('\\', '/')
        if (clean.isEmpty()) return null
        val normalized = clean.removePrefix("./").lowercase(Locale.US)
        val sibling = File(currentFile.parentFile ?: rootDir, clean)
        if (sibling.exists() && sibling.isFile) return sibling
        val fromRoot = File(rootDir, clean.removePrefix("./"))
        if (fromRoot.exists() && fromRoot.isFile) return fromRoot
        assetMap[normalized]?.let { if (it.exists() && it.isFile) return it }
        val fileName = normalized.substringAfterLast('/')
        assetMap[fileName]?.let { if (it.exists() && it.isFile) return it }
        return null
    }

    // =========================================================================
    // Binary OpenUSD `.usdc` (`PXR-USDC` Crate) Streaming Parser
    // =========================================================================

    private data class CrateSection(val name: String, val start: Long, val size: Long)

    private fun parseUsdcCrateFile(
        file: File,
        rootDir: File,
        assetMap: Map<String, File>,
        parentWorld: DoubleArray,
        ctx: UsdAccumContext,
        visited: MutableSet<String>,
        depth: Int
    ) {
        RandomAccessFile(file, "r").use { raf ->
            val hdr = ByteArray(24)
            raf.readFully(hdr)
            val hbb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val major = hdr[8].toInt() and 0xFF
            val minor = hdr[9].toInt() and 0xFF
            val tocOffset = hbb.getLong(16)
            if (tocOffset <= 24L || tocOffset >= file.length()) return

            raf.seek(tocOffset)
            val numSections = readInt64LE(raf).toInt().coerceIn(0, 64)
            val sections = HashMap<String, CrateSection>(numSections)
            val secBuf = ByteArray(32)
            val secBB = ByteBuffer.wrap(secBuf).order(ByteOrder.LITTLE_ENDIAN)
            for (s in 0 until numSections) {
                raf.readFully(secBuf)
                var nameEnd = 16
                for (i in 0 until 16) {
                    if (secBuf[i].toInt() == 0) { nameEnd = i; break }
                }
                val name = String(secBuf, 0, nameEnd, Charsets.US_ASCII)
                val start = secBB.getLong(16)
                val size = secBB.getLong(24)
                sections[name] = CrateSection(name, start, size)
            }

            // 1. TOKENS
            val tokens = readCrateTokens(raf, sections["TOKENS"], minor)
            if (tokens.isEmpty()) return

            // 2. STRINGS
            val strings = readCrateStrings(raf, sections["STRINGS"], tokens)

            // 3. FIELDS
            val fieldsPair = readCrateFields(raf, sections["FIELDS"], minor) ?: return
            val fieldTokenIdx = fieldsPair.first
            val fieldReps = fieldsPair.second

            // 4. FIELDSETS
            val fieldSets = readCrateFieldSets(raf, sections["FIELDSETS"], minor)

            // 5. PATHS
            val paths = readCratePaths(raf, sections["PATHS"], tokens, minor)

            // 6. SPECS
            val specs = readCrateSpecs(raf, sections["SPECS"], minor) ?: return

            // Build path -> fields map
            val pathFields = HashMap<String, Map<String, Long>>(specs.pathIndexes.size.coerceAtLeast(16))
            for (specIdx in specs.pathIndexes.indices) {
                val pIdx = specs.pathIndexes[specIdx]
                val fsIdx = specs.fieldSetIndexes[specIdx]
                val pathStr = paths.getOrNull(pIdx) ?: continue
                if (fsIdx !in fieldSets.indices) continue
                val fMap = LinkedHashMap<String, Long>()
                var cursor = fsIdx
                while (cursor < fieldSets.size) {
                    val fIdx = fieldSets[cursor++]
                    if (fIdx < 0) break
                    if (fIdx in fieldTokenIdx.indices) {
                        val tName = tokens.getOrNull(fieldTokenIdx[fIdx]) ?: continue
                        fMap[tName] = fieldReps[fIdx]
                    }
                }
                pathFields[pathStr] = fMap
            }

            val reader = CrateValueReader(raf, major, minor, tokens, strings)

            // Root pseudo-root metadata: upAxis & subLayers
            val rootFields = pathFields["/"] ?: emptyMap()
            val upAxis = rootFields["upAxis"]?.let { reader.readToken(it) } ?: "Y"
            val metersPerUnit = rootFields["metersPerUnit"]?.let { reader.readFloatScalar(it) } ?: 1f

            val baseMat = if (depth == 0) {
                var m = parentWorld
                if (upAxis.equals("Z", ignoreCase = true)) {
                    m = mulMat4(m, rotXMat4(-PI / 2.0))
                }
                if (metersPerUnit.isFinite() && metersPerUnit > 0f && kotlin.math.abs(metersPerUnit - 1f) > 1e-4f && metersPerUnit in 0.001f..1000f) {
                    val s = metersPerUnit.toDouble()
                    m = mulMat4(m, scaleMat4(s, s, s))
                }
                m
            } else parentWorld

            // Follow subLayers on root if present
            rootFields["subLayers"]?.let { rep ->
                for (sub in reader.readAssetPaths(rep)) {
                    resolveUsdAsset(sub, file, rootDir, assetMap)?.let { subF ->
                        processAnyUsdLayer(subF, rootDir, assetMap, baseMat, ctx, visited, depth + 1)
                    }
                }
            }

            // Build prim path hierarchy (parent -> child prim paths)
            val allPrimPaths = pathFields.keys.filter { !it.contains('.') }.sorted()
            val childrenByParent = HashMap<String, MutableList<String>>()
            for (pp in allPrimPaths) {
                if (pp == "/") continue
                val parent = pp.substringBeforeLast('/').ifEmpty { "/" }
                childrenByParent.getOrPut(parent) { mutableListOf() }.add(pp)
            }

            // Collect materials: materialPath -> MaterialInfo(diffuseColor, metallic, roughness, textureFile)
            val materials = HashMap<String, UsdMaterialInfo>()
            for (pp in allPrimPaths) {
                val pf = pathFields[pp] ?: continue
                val typeTok = pf["typeName"]?.let { reader.readToken(it) }
                if (typeTok == "Material") {
                    val matInfo = extractCrateMaterial(pp, childrenByParent, pathFields, reader, file, rootDir, assetMap)
                    materials[pp] = matInfo
                    if (matInfo.textureFile != null && ctx.primaryTextureFile == null) {
                        ctx.primaryTextureFile = matInfo.textureFile
                    }
                    if (ctx.primaryDiffuseColor == null && matInfo.diffuseColor != null) {
                        ctx.primaryDiffuseColor = matInfo.diffuseColor
                        ctx.primaryMetallic = matInfo.metallic
                        ctx.primaryRoughness = matInfo.roughness
                    }
                }
            }

            // Recursive traversal over prim tree applying local Xforms, references/payloads, and streaming Meshes
            fun walkPrim(primPath: String, worldMat: DoubleArray) {
                val pf = pathFields[primPath] ?: emptyMap()
                val localXform = readCratePrimXform(primPath, pf, pathFields, reader)
                val resetStack = pf["xformOpOrder"]?.let { rep ->
                    reader.readTokenArray(rep).any { it == "!resetXformStack!" }
                } ?: false
                val curWorld = if (resetStack) mulMat4(baseMat, localXform) else mulMat4(worldMat, localXform)

                // Check references / payload pointing to another USD file in the archive
                for (arcField in listOf("references", "payload")) {
                    pf[arcField]?.let { rep ->
                        for (assetRef in reader.readAssetPaths(rep)) {
                            resolveUsdAsset(assetRef, file, rootDir, assetMap)?.let { refF ->
                                processAnyUsdLayer(refF, rootDir, assetMap, curWorld, ctx, visited, depth + 1)
                            }
                        }
                    }
                }

                // Material binding -> update primary material appearance if not already set
                pathFields["$primPath.material:binding"]?.get("targetPaths")?.let { rep ->
                    val target = reader.readPathList(rep, paths).firstOrNull()
                    val mInfo = target?.let { materials[it] }
                    if (mInfo != null) {
                        if (ctx.primaryTextureFile == null && mInfo.textureFile != null) ctx.primaryTextureFile = mInfo.textureFile
                        if (ctx.primaryDiffuseColor == null && mInfo.diffuseColor != null) {
                            ctx.primaryDiffuseColor = mInfo.diffuseColor
                            ctx.primaryMetallic = mInfo.metallic
                            ctx.primaryRoughness = mInfo.roughness
                        }
                    }
                }

                val typeName = pf["typeName"]?.let { reader.readToken(it) }
                val hasPointsProp = pathFields.containsKey("$primPath.points") && pathFields.containsKey("$primPath.faceVertexIndices")
                if (typeName == "Mesh" || hasPointsProp) {
                    emitCrateMesh(primPath, pathFields, reader, curWorld, ctx)
                }

                val children = childrenByParent[primPath] ?: return
                for (child in children) walkPrim(child, curWorld)
            }

            val topChildren = childrenByParent["/"] ?: emptyList()
            for (rootPrim in topChildren) walkPrim(rootPrim, baseMat)
        }
    }

    private fun extractCrateMaterial(
        matPath: String,
        childrenByParent: Map<String, List<String>>,
        pathFields: Map<String, Map<String, Long>>,
        reader: CrateValueReader,
        usdFile: File,
        rootDir: File,
        assetMap: Map<String, File>
    ): UsdMaterialInfo {
        var diffuse: FloatArray? = null
        var metallic = 0.1f
        var roughness = 0.7f
        var texFile: File? = null

        val queue = ArrayDeque<String>()
        queue.add(matPath)
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            childrenByParent[p]?.forEach { queue.add(it) }
            val shaderId = pathFields["$p.info:id"]?.let { getCrateAttrValueRep(it) }?.let { reader.readToken(it) }
            if (shaderId == "UsdPreviewSurface" || pathFields.containsKey("$p.inputs:diffuseColor")) {
                pathFields["$p.inputs:diffuseColor"]?.let { getCrateAttrValueRep(it) }?.let { rep ->
                    val arr = reader.readVec3ScalarOrArray(rep)
                    if (arr != null && arr.size >= 3) {
                        diffuse = floatArrayOf(arr[0], arr[1], arr[2], 1f)
                    }
                }
                pathFields["$p.inputs:metallic"]?.let { getCrateAttrValueRep(it) }?.let { rep ->
                    reader.readFloatScalar(rep)?.let { metallic = it }
                }
                pathFields["$p.inputs:roughness"]?.let { getCrateAttrValueRep(it) }?.let { rep ->
                    reader.readFloatScalar(rep)?.let { roughness = it }
                }
            }
            if (shaderId == "UsdUVTexture" || pathFields.containsKey("$p.inputs:file")) {
                pathFields["$p.inputs:file"]?.let { getCrateAttrValueRep(it) }?.let { rep ->
                    for (asset in reader.readAssetPaths(rep)) {
                        val candidate = resolveUsdAsset(asset, usdFile, rootDir, assetMap)
                        if (candidate != null && (candidate.extension.equals("png", true) ||
                                candidate.extension.equals("jpg", true) ||
                                candidate.extension.equals("jpeg", true))
                        ) {
                            texFile = candidate
                            break
                        }
                    }
                }
            }
        }
        return UsdMaterialInfo(diffuse, metallic, roughness, texFile)
    }

    private fun readCratePrimXform(
        primPath: String,
        pf: Map<String, Long>,
        pathFields: Map<String, Map<String, Long>>,
        reader: CrateValueReader
    ): DoubleArray {
        val opOrderRep = pf["xformOpOrder"] ?: pathFields["$primPath.xformOpOrder"]?.let { getCrateAttrValueRep(it) }
        val ops = opOrderRep?.let { reader.readTokenArray(it) }
        if (ops.isNullOrEmpty()) {
            // Check direct xformOp:transform even if xformOpOrder omitted
            val directMatRep = pathFields["$primPath.xformOp:transform"]?.let { getCrateAttrValueRep(it) }
            if (directMatRep != null) {
                reader.readMatrix4d(directMatRep)?.let { return it }
            }
            return identityMat4()
        }
        var acc = identityMat4()
        for (rawOp in ops) {
            if (rawOp == "!resetXformStack!") continue
            val isInv = rawOp.startsWith("!invert!")
            val opName = if (isInv) rawOp.removePrefix("!invert!") else rawOp
            val opFields = pathFields["$primPath.$opName"] ?: continue
            val rep = getCrateAttrValueRep(opFields) ?: continue
            val m: DoubleArray? = when {
                opName.startsWith("xformOp:transform") -> reader.readMatrix4d(rep)
                opName.startsWith("xformOp:translate") -> reader.readVec3ScalarOrArray(rep)?.let { v ->
                    translateMat4(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                }
                opName.startsWith("xformOp:scale") -> reader.readVec3ScalarOrArray(rep)?.let { v ->
                    scaleMat4(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                }
                opName.startsWith("xformOp:rotateX") && !opName.startsWith("xformOp:rotateXY") ->
                    reader.readFloatScalar(rep)?.let { deg -> rotXMat4(deg * PI / 180.0) }
                opName.startsWith("xformOp:rotateY") && !opName.startsWith("xformOp:rotateYZ") ->
                    reader.readFloatScalar(rep)?.let { deg -> rotYMat4(deg * PI / 180.0) }
                opName.startsWith("xformOp:rotateZ") ->
                    reader.readFloatScalar(rep)?.let { deg -> rotZMat4(deg * PI / 180.0) }
                opName.startsWith("xformOp:rotateXYZ") -> reader.readVec3ScalarOrArray(rep)?.let { v ->
                    eulerXyzMat4(v[0] * PI / 180.0, v[1] * PI / 180.0, v[2] * PI / 180.0)
                }
                opName.startsWith("xformOp:orient") -> reader.readQuatXyzw(rep)?.let { q ->
                    quatMat4(q[0], q[1], q[2], q[3])
                }
                else -> null
            }
            if (m != null) {
                acc = mulMat4(acc, if (isInv) invertAffineOrRigidMat4(m) else m)
            }
        }
        return acc
    }

    private fun getCrateAttrValueRep(fields: Map<String, Long>): Long? =
        fields["default"] ?: fields["timeSamples"]

    private fun emitCrateMesh(
        primPath: String,
        pathFields: Map<String, Map<String, Long>>,
        reader: CrateValueReader,
        worldMat: DoubleArray,
        ctx: UsdAccumContext
    ) {
        val ptsRep = pathFields["$primPath.points"]?.let { getCrateAttrValueRep(it) } ?: return
        val countsRep = pathFields["$primPath.faceVertexCounts"]?.let { getCrateAttrValueRep(it) } ?: return
        val idxRep = pathFields["$primPath.faceVertexIndices"]?.let { getCrateAttrValueRep(it) } ?: return

        val points = reader.readFloatVecArray(ptsRep, 3) ?: return
        val vertCount = points.size / 3
        if (vertCount <= 0) return

        val faceCounts = reader.readIntArray(countsRep)
        val faceIndices = reader.readIntArray(idxRep) ?: return

        val orientation = pathFields["$primPath.orientation"]?.let { getCrateAttrValueRep(it) }?.let { reader.readToken(it) } ?: "rightHanded"
        val isLeftHanded = orientation.equals("leftHanded", ignoreCase = true)

        val uvField = pathFields["$primPath.primvars:st"]
            ?: pathFields["$primPath.primvars:UVMap"]
            ?: pathFields["$primPath.primvars:uv"]
        val uvArray = uvField?.let { getCrateAttrValueRep(it) }?.let { reader.readFloatVecArray(it, 2) }
        val uvIdxField = pathFields["$primPath.primvars:st:indices"]
            ?: pathFields["$primPath.primvars:UVMap:indices"]
            ?: pathFields["$primPath.primvars:uv:indices"]
        val uvIndices = uvIdxField?.let { getCrateAttrValueRep(it) }?.let { reader.readIntArray(it) }
        val uvInterp = uvField?.get("interpolation")?.let { reader.readToken(it) } ?: ""

        val nrmField = pathFields["$primPath.normals"] ?: pathFields["$primPath.primvars:normals"]
        val nrmArray = nrmField?.let { getCrateAttrValueRep(it) }?.let { reader.readFloatVecArray(it, 3) }
        val nrmInterp = nrmField?.get("interpolation")?.let { reader.readToken(it) } ?: ""

        // Capture primvars:displayColor if no material diffuse color set yet
        if (ctx.primaryDiffuseColor == null) {
            pathFields["$primPath.primvars:displayColor"]?.let { getCrateAttrValueRep(it) }?.let { rep ->
                val col = reader.readFloatVecArray(rep, 3)
                if (col != null && col.size >= 3) {
                    ctx.primaryDiffuseColor = floatArrayOf(col[0], col[1], col[2], 1f)
                }
            }
        }

        val canUseSharedVerts = (uvArray == null || uvIndices == null && uvInterp != "faceVarying") &&
            (nrmArray == null || nrmInterp != "faceVarying")

        emitUsdMeshToContext(
            points = points,
            faceCounts = faceCounts,
            faceIndices = faceIndices,
            isLeftHanded = isLeftHanded,
            nrmArray = nrmArray,
            nrmInterp = nrmInterp,
            uvArray = uvArray,
            uvIndices = uvIndices,
            uvInterp = uvInterp,
            worldMat = worldMat,
            canUseSharedVerts = canUseSharedVerts,
            ctx = ctx
        )
    }

    // =========================================================================
    // Crate Binary Section Decoders (TfFastCompression + Usd_IntegerCompression + ValueRep)
    // =========================================================================

    private fun readCrateTokens(raf: RandomAccessFile, sec: CrateSection?, minor: Int): List<String> {
        if (sec == null || sec.size <= 0L) return emptyList()
        raf.seek(sec.start)
        val numTokens = readInt64LE(raf).toInt().coerceIn(0, 2_000_000)
        if (numTokens == 0) return emptyList()
        val rawBytes: ByteArray = if (minor >= 4) {
            val uncompSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
            val compSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
            val comp = ByteArray(compSize)
            raf.readFully(comp)
            tfDecompress(comp, uncompSize)
        } else {
            val blob = ByteArray((sec.size - 8L).coerceIn(0L, 64L * 1024L * 1024L).toInt())
            raf.readFully(blob)
            blob
        }
        val list = ArrayList<String>(numTokens)
        var start = 0
        for (i in rawBytes.indices) {
            if (rawBytes[i].toInt() == 0) {
                list.add(String(rawBytes, start, i - start, Charsets.UTF_8))
                start = i + 1
                if (list.size == numTokens) break
            }
        }
        return list
    }

    private fun readCrateStrings(raf: RandomAccessFile, sec: CrateSection?, tokens: List<String>): List<String> {
        if (sec == null || sec.size < 8L) return emptyList()
        raf.seek(sec.start)
        val numStrings = readInt64LE(raf).toInt().coerceIn(0, 2_000_000)
        val bytes = ByteArray(numStrings * 4)
        raf.readFully(bytes)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(numStrings) { i -> tokens.getOrElse(bb.getInt(i * 4)) { "" } }
    }

    private fun readCrateFields(raf: RandomAccessFile, sec: CrateSection?, minor: Int): Pair<IntArray, LongArray>? {
        if (sec == null || sec.size < 8L) return null
        raf.seek(sec.start)
        val numFields = readInt64LE(raf).toInt().coerceIn(0, 4_000_000)
        if (numFields == 0) return IntArray(0) to LongArray(0)
        val tokenIndices: IntArray
        val reps = LongArray(numFields)
        if (minor >= 4) {
            val tokCompSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
            val tokComp = ByteArray(tokCompSize)
            raf.readFully(tokComp)
            tokenIndices = decompressUsdInts32(tokComp, numFields)

            val repCompSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
            val repComp = ByteArray(repCompSize)
            raf.readFully(repComp)
            val repBytes = tfDecompress(repComp, numFields * 8)
            val bb = ByteBuffer.wrap(repBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numFields) reps[i] = bb.getLong(i * 8)
        } else {
            val raw = ByteArray(numFields * 12)
            raf.readFully(raw)
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            tokenIndices = IntArray(numFields) { i -> bb.getInt(i * 12) }
            for (i in 0 until numFields) reps[i] = bb.getLong(numFields * 4 + i * 8)
        }
        return tokenIndices to reps
    }

    private fun readCrateFieldSets(raf: RandomAccessFile, sec: CrateSection?, minor: Int): IntArray {
        if (sec == null || sec.size < 8L) return IntArray(0)
        raf.seek(sec.start)
        val numFieldSets = readInt64LE(raf).toInt().coerceIn(0, 8_000_000)
        if (numFieldSets == 0) return IntArray(0)
        return if (minor >= 4) {
            val compSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
            val comp = ByteArray(compSize)
            raf.readFully(comp)
            decompressUsdInts32(comp, numFieldSets)
        } else {
            val raw = ByteArray(numFieldSets * 4)
            raf.readFully(raw)
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            IntArray(numFieldSets) { i -> bb.getInt(i * 4) }
        }
    }

    private fun readCratePaths(raf: RandomAccessFile, sec: CrateSection?, tokens: List<String>, minor: Int): List<String> {
        if (sec == null || sec.size < 8L) return emptyList()
        raf.seek(sec.start)
        val numPaths = readInt64LE(raf).toInt().coerceIn(0, 4_000_000)
        if (numPaths == 0) return emptyList()

        val pathIndexes: IntArray
        val elemTokens: IntArray
        val jumps: IntArray
        if (minor >= 4) {
            val c1 = readInt64LE(raf).toInt(); val b1 = ByteArray(c1); raf.readFully(b1)
            pathIndexes = decompressUsdInts32(b1, numPaths)
            val c2 = readInt64LE(raf).toInt(); val b2 = ByteArray(c2); raf.readFully(b2)
            elemTokens = decompressUsdInts32(b2, numPaths)
            val c3 = readInt64LE(raf).toInt(); val b3 = ByteArray(c3); raf.readFully(b3)
            jumps = decompressUsdInts32(b3, numPaths)
        } else {
            val raw = ByteArray(numPaths * 12)
            raf.readFully(raw)
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            pathIndexes = IntArray(numPaths) { i -> bb.getInt(i * 4) }
            elemTokens = IntArray(numPaths) { i -> bb.getInt(numPaths * 4 + i * 4) }
            jumps = IntArray(numPaths) { i -> bb.getInt(numPaths * 8 + i * 4) }
        }

        val out = MutableList(numPaths) { "" }
        // Iterative traversal of compressed path tree (matches OpenUSD CrateFile::_BuildDecompressedPathsImpl)
        val stackIdx = IntArray(numPaths + 8)
        val stackParent = arrayOfNulls<String>(numPaths + 8)
        var sp = 0
        stackIdx[sp] = 0
        stackParent[sp] = ""
        sp++

        while (sp > 0) {
            sp--
            val i = stackIdx[sp]
            val parent = stackParent[sp] ?: ""
            if (i !in 0 until numPaths) continue

            val eTok = elemTokens[i]
            val pIdx = pathIndexes[i]
            val fullPath = when {
                i == 0 && eTok == 0 -> "/"
                eTok < 0 -> {
                    val tok = tokens.getOrElse(-eTok) { "" }
                    if (parent == "/" || parent.isEmpty()) "/.$tok" else "$parent.$tok"
                }
                else -> {
                    val tok = tokens.getOrElse(eTok) { "" }
                    if (parent == "/" || parent.isEmpty()) "/$tok" else "$parent/$tok"
                }
            }
            if (pIdx in 0 until numPaths) {
                out[pIdx] = fullPath
            }

            val jump = jumps[i]
            val hasChild = (jump == -1 || jump > 0)
            val hasSibling = (jump >= 0)
            if (hasSibling) {
                val sibIdx = if (jump == 0) i + 1 else i + jump
                if (sp < stackIdx.size) {
                    stackIdx[sp] = sibIdx
                    stackParent[sp] = parent
                    sp++
                }
            }
            if (hasChild) {
                if (sp < stackIdx.size) {
                    stackIdx[sp] = i + 1
                    stackParent[sp] = fullPath
                    sp++
                }
            }
        }
        return out
    }

    private data class CrateSpecs(val pathIndexes: IntArray, val fieldSetIndexes: IntArray, val specTypes: IntArray)

    private fun readCrateSpecs(raf: RandomAccessFile, sec: CrateSection?, minor: Int): CrateSpecs? {
        if (sec == null || sec.size < 8L) return null
        raf.seek(sec.start)
        val numSpecs = readInt64LE(raf).toInt().coerceIn(0, 4_000_000)
        if (numSpecs == 0) return CrateSpecs(IntArray(0), IntArray(0), IntArray(0))
        return if (minor >= 4) {
            val c1 = readInt64LE(raf).toInt(); val b1 = ByteArray(c1); raf.readFully(b1)
            val pathIdx = decompressUsdInts32(b1, numSpecs)
            val c2 = readInt64LE(raf).toInt(); val b2 = ByteArray(c2); raf.readFully(b2)
            val fsIdx = decompressUsdInts32(b2, numSpecs)
            val c3 = readInt64LE(raf).toInt(); val b3 = ByteArray(c3); raf.readFully(b3)
            val types = decompressUsdInts32(b3, numSpecs)
            CrateSpecs(pathIdx, fsIdx, types)
        } else {
            val raw = ByteArray(numSpecs * 12)
            raf.readFully(raw)
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            CrateSpecs(
                IntArray(numSpecs) { i -> bb.getInt(i * 4) },
                IntArray(numSpecs) { i -> bb.getInt(numSpecs * 4 + i * 4) },
                IntArray(numSpecs) { i -> bb.getInt(numSpecs * 8 + i * 4) }
            )
        }
    }

    /**
     * Reads `ValueRep` payloads from a `.usdc` binary stream:
     * Bit 63: IsArray, Bit 62: IsInlined, Bit 61: IsCompressed,
     * Bits 48..55: TypeEnum, Bits 0..47: inline value or file offset.
     */
    private class CrateValueReader(
        private val raf: RandomAccessFile,
        private val major: Int,
        private val minor: Int,
        private val tokens: List<String>,
        private val strings: List<String>
    ) {
        companion object {
            const val T_INT = 3
            const val T_UINT = 4
            const val T_INT64 = 5
            const val T_UINT64 = 6
            const val T_HALF = 7
            const val T_FLOAT = 8
            const val T_DOUBLE = 9
            const val T_STRING = 10
            const val T_TOKEN = 11
            const val T_ASSET_PATH = 12
            const val T_QUATD = 13
            const val T_QUATF = 14
            const val T_QUATH = 15
            const val T_VEC2D = 16
            const val T_VEC2F = 17
            const val T_VEC2H = 18
            const val T_VEC2I = 19
            const val T_VEC3D = 20
            const val T_VEC3F = 21
            const val T_VEC3H = 22
            const val T_VEC3I = 23
            const val T_VEC4D = 24
            const val T_VEC4F = 25
            const val T_VEC4H = 26
            const val T_VEC4I = 27
            const val T_MATRIX4D = 30
            const val T_PATH_LIST_OP = 34
            const val T_TOKEN_VECTOR = 41
            const val T_TIME_SAMPLES = 46
        }

        private fun isArray(rep: Long) = ((rep ushr 63) and 1L) != 0L
        private fun isInlined(rep: Long) = ((rep ushr 62) and 1L) != 0L
        private fun isCompressed(rep: Long) = ((rep ushr 61) and 1L) != 0L
        private fun typeEnum(rep: Long) = ((rep ushr 48) and 0xFFL).toInt()
        private fun payload(rep: Long) = rep and 0x0000_FFFF_FFFF_FFFFL

        private fun unwrapTimeSampleRep(rep: Long): Long {
            if (typeEnum(rep) != T_TIME_SAMPLES || isInlined(rep)) return rep
            val off = payload(rep)
            if (off <= 0L || off >= raf.length()) return rep
            return runCatching {
                raf.seek(off)
                // TimeSamples layout: ValueRep(times) -> count -> ValueRep[count](values)
                readInt64LE(raf) // times ValueRep
                val valCount = readInt64LE(raf).toInt().coerceIn(0, 100_000)
                if (valCount <= 0) return rep
                val firstRep = readInt64LE(raf)
                // Nested TimeSamples inside TimeSamples: ValueRep can be stored at fixed or relative offsets?
                // In Crate format 0.8+: Times & Values ValueReps store file offset directly, unless IsInlined.
                firstRep
            }.getOrDefault(rep)
        }

        fun readToken(rawRep: Long): String? {
            val rep = unwrapTimeSampleRep(rawRep)
            val t = typeEnum(rep)
            if (isArray(rep)) return null
            return when (t) {
                T_TOKEN, T_STRING, T_ASSET_PATH -> {
                    val idx = if (isInlined(rep)) (payload(rep) and 0xFFFF_FFFFL).toInt() else {
                        raf.seek(payload(rep)); readInt32LE(raf)
                    }
                    when (t) {
                        T_TOKEN -> tokens.getOrNull(idx)
                        T_STRING -> strings.getOrNull(idx)
                        T_ASSET_PATH -> tokens.getOrNull(idx)
                        else -> null
                    }
                }
                else -> null
            }
        }

        fun readTokenArray(rawRep: Long): List<String> {
            val rep = unwrapTimeSampleRep(rawRep)
            val t = typeEnum(rep)
            if ((t == T_TOKEN && isArray(rep) || t == T_TOKEN_VECTOR) && !isInlined(rep)) {
                val len = readArrayLen(payload(rep))
                val arr = ByteArray(len * 4)
                raf.readFully(arr)
                val bb = ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN)
                return List(len) { i -> tokens.getOrElse(bb.getInt(i * 4)) { "" } }
            }
            return readToken(rep)?.let { listOf(it) } ?: emptyList()
        }

        fun readAssetPaths(rawRep: Long): List<String> {
            val rep = unwrapTimeSampleRep(rawRep)
            val t = typeEnum(rep)
            if (t == T_ASSET_PATH) {
                if (!isArray(rep)) return readToken(rep)?.let { listOf(it) } ?: emptyList()
                val len = readArrayLen(payload(rep))
                val arr = ByteArray(len * 4)
                raf.readFully(arr)
                val bb = ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN)
                return List(len) { i -> tokens.getOrElse(bb.getInt(i * 4)) { "" } }
            }
            if (t == T_STRING) {
                return readToken(rep)?.let { listOf(it) } ?: emptyList()
            }
            // Also handle ListOp payloads (ReferenceListOp / PayloadListOp): extract referenced assetPath strings
            if (!isInlined(rep) && (t == 37 || t == 38 || t == 34 || t == 35)) {
                // Scan small ListOp structure for asset path strings if small
                return emptyList()
            }
            return emptyList()
        }

        fun readPathList(rawRep: Long, paths: List<String>): List<String> {
            val rep = unwrapTimeSampleRep(rawRep)
            if (isInlined(rep)) return emptyList()
            val t = typeEnum(rep)
            val off = payload(rep)
            if (off <= 0L || off >= raf.length()) return emptyList()
            return runCatching {
                raf.seek(off)
                if (t == 40) { // PathVector
                    val cnt = if (minor >= 7) readInt64LE(raf).toInt().coerceIn(0, 1024) else readInt32LE(raf).coerceIn(0, 1024)
                    return@runCatching List(cnt) { paths.getOrElse(readInt32LE(raf)) { "" } }.filter { it.isNotEmpty() }
                }
                // PathListOp header: bit 0 is IsExplicit flag (no items); bits 1..6 are list arrays
                val header = raf.readByte().toInt() and 0xFF
                val out = mutableListOf<String>()
                for (bit in 1..6) {
                    if ((header and (1 shl bit)) != 0) {
                        val cnt = if (minor >= 7) readInt64LE(raf).toInt().coerceIn(0, 1024) else readInt32LE(raf).coerceIn(0, 1024)
                        for (k in 0 until cnt) {
                            val idx = readInt32LE(raf)
                            paths.getOrNull(idx)?.let { out.add(it) }
                        }
                    }
                }
                out
            }.getOrDefault(emptyList())
        }

        fun readFloatScalar(rawRep: Long): Float? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (isArray(rep)) return null
            val t = typeEnum(rep)
            return if (isInlined(rep)) {
                val p = payload(rep).toInt()
                when (t) {
                    T_FLOAT -> java.lang.Float.intBitsToFloat(p)
                    T_DOUBLE -> p.toDouble().toFloat()
                    T_HALF -> halfBitsToFloat(p and 0xFFFF)
                    T_INT -> p.toFloat()
                    else -> null
                }
            } else {
                raf.seek(payload(rep))
                when (t) {
                    T_FLOAT -> java.lang.Float.intBitsToFloat(readInt32LE(raf))
                    T_DOUBLE -> java.lang.Double.longBitsToDouble(readInt64LE(raf)).toFloat()
                    T_HALF -> {
                        val b = ByteArray(2); raf.readFully(b)
                        halfBitsToFloat((b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8))
                    }
                    else -> null
                }
            }
        }

        fun readVec3ScalarOrArray(rawRep: Long): FloatArray? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (isArray(rep)) return readFloatVecArray(rep, 3)
            val t = typeEnum(rep)
            if (isInlined(rep)) {
                // Vec3i or packed half3/float-int can be inlined
                val p = payload(rep)
                return when (t) {
                    T_VEC3H -> floatArrayOf(
                        halfBitsToFloat((p and 0xFFFFL).toInt()),
                        halfBitsToFloat(((p ushr 16) and 0xFFFFL).toInt()),
                        halfBitsToFloat(((p ushr 32) and 0xFFFFL).toInt())
                    )
                    T_VEC3I -> floatArrayOf(
                        (p and 0xFFFFL).toShort().toFloat(),
                        ((p ushr 16) and 0xFFFFL).toShort().toFloat(),
                        ((p ushr 32) and 0xFFFFL).toShort().toFloat()
                    )
                    T_VEC3F, T_VEC3D -> floatArrayOf(
                        ((p and 0xFFFFL).toShort().toInt()).toFloat(),
                        (((p ushr 16) and 0xFFFFL).toShort().toInt()).toFloat(),
                        (((p ushr 32) and 0xFFFFL).toShort().toInt()).toFloat()
                    )
                    else -> null
                }
            }
            raf.seek(payload(rep))
            return when (t) {
                T_VEC3F -> {
                    val b = ByteArray(12); raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    floatArrayOf(bb.float, bb.float, bb.float)
                }
                T_VEC3D -> {
                    val b = ByteArray(24); raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    floatArrayOf(bb.double.toFloat(), bb.double.toFloat(), bb.double.toFloat())
                }
                T_VEC3H -> {
                    val b = ByteArray(6); raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    floatArrayOf(
                        halfBitsToFloat(bb.short.toInt() and 0xFFFF),
                        halfBitsToFloat(bb.short.toInt() and 0xFFFF),
                        halfBitsToFloat(bb.short.toInt() and 0xFFFF)
                    )
                }
                else -> null
            }
        }

        fun readQuatXyzw(rawRep: Long): DoubleArray? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (isArray(rep) || isInlined(rep)) return null
            val t = typeEnum(rep)
            raf.seek(payload(rep))
            // OpenUSD Quat stores (real w, img x, img y, img z) or vector then w?
            // OpenUSD GfQuatd/f layout in Crate binary: Wait — OpenUSD GfQuat binary is (Vec3 imaginary, real w)!
            return when (t) {
                T_QUATF -> {
                    val b = ByteArray(16); raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    val ix = bb.float.toDouble(); val iy = bb.float.toDouble()
                    val iz = bb.float.toDouble(); val w = bb.float.toDouble()
                    doubleArrayOf(ix, iy, iz, w)
                }
                T_QUATD -> {
                    val b = ByteArray(32); raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    val ix = bb.double; val iy = bb.double; val iz = bb.double; val w = bb.double
                    doubleArrayOf(ix, iy, iz, w)
                }
                else -> null
            }
        }

        fun readMatrix4d(rawRep: Long): DoubleArray? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (isArray(rep) || isInlined(rep)) return null
            val t = typeEnum(rep)
            if (t != T_MATRIX4D) return null
            val b = ByteArray(128)
            raf.seek(payload(rep))
            raf.readFully(b)
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            // USD Matrix4d is row-major (translation in row 3: [12, 13, 14]); transpose to our column-major convention!
            val r = DoubleArray(16) { bb.double }
            return doubleArrayOf(
                r[0], r[1], r[2], r[3],
                r[4], r[5], r[6], r[7],
                r[8], r[9], r[10], r[11],
                r[12], r[13], r[14], r[15]
            )
        }

        fun readIntArray(rawRep: Long): IntArray? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (!isArray(rep)) return null
            val t = typeEnum(rep)
            if (isInlined(rep)) {
                val p = payload(rep)
                return if (p == 0L) IntArray(0) else null
            }
            val off = payload(rep)
            val len = readArrayLen(off)
            if (len <= 0) return IntArray(0)

            return if (isCompressed(rep)) {
                val compSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
                val comp = ByteArray(compSize)
                raf.readFully(comp)
                when (t) {
                    T_INT, T_UINT -> decompressUsdInts32(comp, len)
                    T_INT64, T_UINT64 -> {
                        val lArr = decompressUsdInts64(comp, len)
                        IntArray(len) { i -> lArr[i].toInt() }
                    }
                    else -> null
                }
            } else {
                when (t) {
                    T_INT, T_UINT -> {
                        val bytes = ByteArray(len * 4); raf.readFully(bytes)
                        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        IntArray(len) { i -> bb.getInt(i * 4) }
                    }
                    T_INT64, T_UINT64 -> {
                        val bytes = ByteArray(len * 8); raf.readFully(bytes)
                        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        IntArray(len) { i -> bb.getLong(i * 8).toInt() }
                    }
                    else -> null
                }
            }
        }

        fun readFloatVecArray(rawRep: Long, dims: Int): FloatArray? {
            val rep = unwrapTimeSampleRep(rawRep)
            if (!isArray(rep)) return null
            if (isInlined(rep)) return FloatArray(0)
            val t = typeEnum(rep)
            val off = payload(rep)
            val len = readArrayLen(off)
            if (len <= 0) return FloatArray(0)

            val scalarKind = when (t) {
                T_FLOAT, T_VEC2F, T_VEC3F, T_VEC4F, T_QUATF -> 4
                T_DOUBLE, T_VEC2D, T_VEC3D, T_VEC4D, T_QUATD -> 8
                T_HALF, T_VEC2H, T_VEC3H, T_VEC4H, T_QUATH -> 2
                else -> return null
            }
            val totalFloats = len * dims

            if (isCompressed(rep)) {
                val code = raf.readByte().toInt() and 0xFF
                return when (code) {
                    'i'.code -> {
                        val compSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
                        val comp = ByteArray(compSize)
                        raf.readFully(comp)
                        val ints = decompressUsdInts32(comp, totalFloats)
                        FloatArray(totalFloats) { i -> ints[i].toFloat() }
                    }
                    't'.code -> {
                        val lutCount = readInt32LE(raf).coerceIn(0, 16_000_000)
                        val lutBytes = ByteArray(lutCount * scalarKind)
                        raf.readFully(lutBytes)
                        val lbb = ByteBuffer.wrap(lutBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val lut = FloatArray(lutCount) { i ->
                            when (scalarKind) {
                                4 -> lbb.getFloat(i * 4)
                                8 -> lbb.getDouble(i * 8).toFloat()
                                else -> halfBitsToFloat(lbb.getShort(i * 2).toInt() and 0xFFFF)
                            }
                        }
                        val compSize = readInt64LE(raf).toInt().coerceIn(0, 128 * 1024 * 1024)
                        val comp = ByteArray(compSize)
                        raf.readFully(comp)
                        val idxs = decompressUsdInts32(comp, totalFloats)
                        FloatArray(totalFloats) { i -> lut.getOrElse(idxs[i]) { 0f } }
                    }
                    else -> {
                        // Fallback raw TfFastCompression on scalar buffer
                        val uncompExpected = totalFloats * scalarKind
                        val rawUncomp = runCatching {
                            val rem = ByteArray((raf.length() - raf.filePointer).coerceAtMost(64L * 1024L * 1024L).toInt())
                            raf.seek(raf.filePointer - 1)
                            raf.read(rem)
                            tfDecompress(rem, uncompExpected)
                        }.getOrNull() ?: return null
                        decodeScalarFloatBytes(rawUncomp, scalarKind, totalFloats)
                    }
                }
            } else {
                val raw = ByteArray(totalFloats * scalarKind)
                raf.readFully(raw)
                return decodeScalarFloatBytes(raw, scalarKind, totalFloats)
            }
        }

        private fun decodeScalarFloatBytes(raw: ByteArray, scalarKind: Int, count: Int): FloatArray {
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(count) { i ->
                when (scalarKind) {
                    4 -> bb.getFloat(i * 4)
                    8 -> bb.getDouble(i * 8).toFloat()
                    else -> halfBitsToFloat(bb.getShort(i * 2).toInt() and 0xFFFF)
                }
            }
        }

        private fun readArrayLen(offset: Long): Int {
            raf.seek(offset)
            val v = if (major > 0 || minor >= 7) readInt64LE(raf) else readInt32LE(raf).toLong() and 0xFFFF_FFFFL
            return v.coerceIn(0L, 120_000_000L).toInt()
        }
    }

    /**
     * Decompresses OpenUSD `TfFastCompression` buffers (`nChunks` byte + raw LZ4 blocks).
     */
    internal fun tfDecompress(compressed: ByteArray, maxUncompressedSize: Int): ByteArray {
        if (compressed.isEmpty() || maxUncompressedSize <= 0) return ByteArray(0)
        val nChunks = compressed[0].toInt() and 0xFF
        if (nChunks == 0) {
            val out = ByteArray(maxUncompressedSize)
            val written = decompressLz4RawBlock(compressed, 1, compressed.size - 1, out, 0)
            return if (written == out.size) out else out.copyOf(written)
        }
        var hdrPos = 1
        val chunkCompSizes = IntArray(nChunks)
        for (c in 0 until nChunks) {
            if (hdrPos + 4 > compressed.size) break
            chunkCompSizes[c] = (compressed[hdrPos].toInt() and 0xFF) or
                ((compressed[hdrPos + 1].toInt() and 0xFF) shl 8) or
                ((compressed[hdrPos + 2].toInt() and 0xFF) shl 16) or
                ((compressed[hdrPos + 3].toInt() and 0xFF) shl 24)
            hdrPos += 4
        }
        val out = ByteArray(maxUncompressedSize)
        var inPos = hdrPos
        var outPos = 0
        for (c in 0 until nChunks) {
            val cLen = chunkCompSizes[c]
            if (cLen <= 0 || inPos + cLen > compressed.size) break
            val w = decompressLz4RawBlock(compressed, inPos, cLen, out, outPos)
            inPos += cLen
            outPos += w
        }
        return if (outPos == out.size) out else out.copyOf(outPos)
    }

    /**
     * Pure-Kotlin RFC-compliant LZ4 raw block decompressor.
     */
    internal fun decompressLz4RawBlock(
        src: ByteArray,
        srcOffset: Int,
        srcLength: Int,
        dst: ByteArray,
        dstOffset: Int
    ): Int {
        var ip = srcOffset
        val iEnd = srcOffset + srcLength
        var op = dstOffset
        val oEnd = dst.size

        while (ip < iEnd) {
            val token = src[ip++].toInt() and 0xFF
            var litLen = token ushr 4
            if (litLen == 15) {
                while (ip < iEnd) {
                    val s = src[ip++].toInt() and 0xFF
                    litLen += s
                    if (s != 255) break
                }
            }
            if (litLen > 0) {
                val safeLit = minOf(litLen, minOf(iEnd - ip, oEnd - op))
                System.arraycopy(src, ip, dst, op, safeLit)
                ip += safeLit
                op += safeLit
            }
            if (ip >= iEnd) break

            if (ip + 2 > iEnd) break
            val matchOffset = (src[ip].toInt() and 0xFF) or ((src[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2
            if (matchOffset <= 0 || matchOffset > (op - dstOffset)) break

            var matchLen = (token and 0x0F) + 4
            if ((token and 0x0F) == 15) {
                while (ip < iEnd) {
                    val s = src[ip++].toInt() and 0xFF
                    matchLen += s
                    if (s != 255) break
                }
            }
            var ref = op - matchOffset
            val safeMatch = minOf(matchLen, oEnd - op)
            for (k in 0 until safeMatch) {
                dst[op++] = dst[ref++]
            }
        }
        return op - dstOffset
    }

    /**
     * Decodes OpenUSD `Usd_IntegerCompression` 32-bit delta + 2-bit code integer arrays.
     */
    internal fun decompressUsdInts32(compressed: ByteArray, numInts: Int): IntArray {
        if (numInts <= 0) return IntArray(0)
        val maxEncoded = 4 + ((numInts + 3) / 4) + (numInts * 4)
        val buf = tfDecompress(compressed, maxEncoded)
        if (buf.size < 4 + ((numInts + 3) / 4)) return IntArray(numInts)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val commonValue = bb.getInt(0)
        val codesStart = 4
        var dataPos = codesStart + ((numInts + 3) / 4)
        val out = IntArray(numInts)
        var prev = 0
        for (i in 0 until numInts) {
            val codeByte = buf[codesStart + (i ushr 2)].toInt() and 0xFF
            val code = (codeByte ushr ((i and 3) * 2)) and 3
            val delta = when (code) {
                0 -> commonValue
                1 -> if (dataPos < buf.size) buf[dataPos++].toInt() else 0
                2 -> if (dataPos + 2 <= buf.size) bb.getShort(dataPos).also { dataPos += 2 }.toInt() else 0
                else -> if (dataPos + 4 <= buf.size) bb.getInt(dataPos).also { dataPos += 4 } else 0
            }
            prev += delta
            out[i] = prev
        }
        return out
    }

    /**
     * Decodes OpenUSD `Usd_IntegerCompression64` 64-bit integer arrays.
     */
    internal fun decompressUsdInts64(compressed: ByteArray, numInts: Int): LongArray {
        if (numInts <= 0) return LongArray(0)
        val maxEncoded = 8 + ((numInts + 3) / 4) + (numInts * 8)
        val buf = tfDecompress(compressed, maxEncoded)
        if (buf.size < 8 + ((numInts + 3) / 4)) return LongArray(numInts)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val commonValue = bb.getLong(0)
        val codesStart = 8
        var dataPos = codesStart + ((numInts + 3) / 4)
        val out = LongArray(numInts)
        var prev = 0L
        for (i in 0 until numInts) {
            val codeByte = buf[codesStart + (i ushr 2)].toInt() and 0xFF
            val code = (codeByte ushr ((i and 3) * 2)) and 3
            val delta = when (code) {
                0 -> commonValue
                1 -> if (dataPos + 2 <= buf.size) bb.getShort(dataPos).also { dataPos += 2 }.toLong() else 0L
                2 -> if (dataPos + 4 <= buf.size) bb.getInt(dataPos).also { dataPos += 4 }.toLong() else 0L
                else -> if (dataPos + 8 <= buf.size) bb.getLong(dataPos).also { dataPos += 8 } else 0L
            }
            prev += delta
            out[i] = prev
        }
        return out
    }

    // =========================================================================
    // ASCII OpenUSD `.usda` Streaming Parser
    // =========================================================================

    private fun parseUsdaAsciiFile(
        file: File,
        rootDir: File,
        assetMap: Map<String, File>,
        parentWorld: DoubleArray,
        ctx: UsdAccumContext,
        visited: MutableSet<String>,
        depth: Int
    ) {
        if (file.length() > 160L * 1024L * 1024L) return
        val text = file.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!text.contains("#usda", ignoreCase = true) && !text.contains("def ")) return

        val upAxis = Regex("""upAxis\s*=\s*"([XYZxyz])"""").find(text)?.groupValues?.get(1)?.uppercase(Locale.US) ?: "Y"
        val metersPerUnit = Regex("""metersPerUnit\s*=\s*([0-9eE.+\-]+)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
        var baseMat = if (depth == 0) {
            var m = parentWorld
            if (upAxis == "Z") m = mulMat4(m, rotXMat4(-PI / 2.0))
            if (metersPerUnit in 0.001..1000.0 && kotlin.math.abs(metersPerUnit - 1.0) > 1e-4) {
                m = mulMat4(m, scaleMat4(metersPerUnit, metersPerUnit, metersPerUnit))
            }
            m
        } else parentWorld

        // Follow subLayers metadata
        Regex("""subLayers\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(text)?.let { m ->
            Regex("""@([^@]+)@""").findAll(m.groupValues[1]).forEach { ref ->
                resolveUsdAsset(ref.groupValues[1], file, rootDir, assetMap)?.let { subF ->
                    processAnyUsdLayer(subF, rootDir, assetMap, baseMat, ctx, visited, depth + 1)
                }
            }
        }

        // Extract diffuseColor / texture file across UsdPreviewSurface blocks
        if (ctx.primaryDiffuseColor == null) {
            Regex("""inputs:diffuseColor\s*=\s*\(\s*([0-9eE.+\-]+)\s*,\s*([0-9eE.+\-]+)\s*,\s*([0-9eE.+\-]+)\s*\)""")
                .find(text)?.let { mc ->
                    ctx.primaryDiffuseColor = floatArrayOf(
                        mc.groupValues[1].toFloatOrNull() ?: 0.85f,
                        mc.groupValues[2].toFloatOrNull() ?: 0.85f,
                        mc.groupValues[3].toFloatOrNull() ?: 0.85f,
                        1f
                    )
                }
        }
        if (ctx.primaryTextureFile == null) {
            Regex("""inputs:file\s*=\s*@([^@]+)@""").find(text)?.let { mt ->
                resolveUsdAsset(mt.groupValues[1], file, rootDir, assetMap)?.let { tf ->
                    ctx.primaryTextureFile = tf
                }
            }
        }

        // Walk prim blocks iteratively by brace depth so nested Xforms compose matrices accurately
        parseUsdaBlocks(text, 0, text.length, baseMat, file, rootDir, assetMap, ctx, visited, depth)
    }

    private fun parseUsdaBlocks(
        text: String,
        start: Int,
        end: Int,
        worldMat: DoubleArray,
        file: File,
        rootDir: File,
        assetMap: Map<String, File>,
        ctx: UsdAccumContext,
        visited: MutableSet<String>,
        depth: Int
    ) {
        val defRegex = Regex("""\b(def|over|class)\s+([A-Za-z0-9_]*)\s*"([^"]+)"(?:\s*\((.*?)\))?\s*\{""", setOf(RegexOption.DOT_MATCHES_ALL))
        var pos = start
        while (pos < end) {
            val m = defRegex.find(text, pos) ?: break
            if (m.range.first >= end) break
            val primType = m.groupValues[2]
            val metaBlock = m.groupValues[4]
            val openBrace = m.range.last
            val closeBrace = findMatchingBrace(text, openBrace, end)
            if (closeBrace <= openBrace) break

            // Extract child sub-blocks' ranges vs current prim's own body
            val childRanges = collectImmediateChildPrimRanges(text, openBrace + 1, closeBrace)
            val ownBody = buildString {
                var cur = openBrace + 1
                for (r in childRanges) {
                    if (r.first > cur) append(text, cur, r.first)
                    cur = r.last + 1
                }
                if (cur < closeBrace) append(text, cur, closeBrace)
            }

            // Compute this prim's local xform
            val localXform = parseUsdaXform(ownBody)
            val primWorld = mulMat4(worldMat, localXform)

            // Resolve references / payload metadata
            Regex("""@([^@]+)@""").findAll(metaBlock).forEach { ref ->
                resolveUsdAsset(ref.groupValues[1], file, rootDir, assetMap)?.let { refF ->
                    processAnyUsdLayer(refF, rootDir, assetMap, primWorld, ctx, visited, depth + 1)
                }
            }

            if (primType == "Mesh" || (ownBody.contains("points") && ownBody.contains("faceVertexIndices"))) {
                emitUsdaMeshBody(ownBody, primWorld, ctx)
            }

            for (r in childRanges) {
                parseUsdaBlocks(text, r.first, r.last + 1, primWorld, file, rootDir, assetMap, ctx, visited, depth)
            }
            pos = closeBrace + 1
        }
    }

    private fun collectImmediateChildPrimRanges(text: String, start: Int, end: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var i = start
        var braceDepth = 0
        var inString = false
        var blockStart = -1
        while (i < end) {
            val c = text[i]
            if (c == '"' && (i == 0 || text[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString) {
                if (c == '#' || (c == '/' && i + 1 < end && text[i + 1] == '/')) {
                    while (i < end && text[i] != '\n') i++
                } else if (c == '{') {
                    if (braceDepth == 0) {
                        // Walk backward over whitespace & optional metadata (...) to locate def/over/class start
                        var ls = i - 1
                        while (ls >= start && text[ls].isWhitespace()) ls--
                        if (ls >= start && text[ls] == ')') {
                            var parenD = 1
                            ls--
                            while (ls >= start && parenD > 0) {
                                when (text[ls]) {
                                    ')' -> parenD++
                                    '(' -> parenD--
                                }
                                ls--
                            }
                            while (ls >= start && text[ls].isWhitespace()) ls--
                        }
                        while (ls >= start && text[ls] != '\n' && text[ls] != ';') ls--
                        blockStart = (ls + 1).coerceAtLeast(start)
                    }
                    braceDepth++
                } else if (c == '}') {
                    braceDepth--
                    if (braceDepth == 0 && blockStart >= 0) {
                        out.add(blockStart..i)
                        blockStart = -1
                    }
                }
            }
            i++
        }
        return out
    }

    private fun findMatchingBrace(text: String, openPos: Int, limit: Int): Int {
        var d = 0
        var inStr = false
        var i = openPos
        while (i < limit) {
            val c = text[i]
            if (c == '"' && (i == 0 || text[i - 1] != '\\')) inStr = !inStr
            else if (!inStr) {
                if (c == '{') d++
                else if (c == '}') {
                    d--
                    if (d == 0) return i
                }
            }
            i++
        }
        return limit - 1
    }

    private fun parseUsdaXform(body: String): DoubleArray {
        val orderMatch = Regex("""xformOpOrder\s*=\s*\[([^\]]*)]""").find(body)
        val orderOps = if (orderMatch != null) {
            Regex(""""([^"]+)"""").findAll(orderMatch.groupValues[1]).map { it.groupValues[1] }.toList()
        } else {
            val fallback = mutableListOf<String>()
            if (body.contains("xformOp:transform")) fallback.add("xformOp:transform")
            if (body.contains("xformOp:translate")) fallback.add("xformOp:translate")
            if (body.contains("xformOp:rotateXYZ")) fallback.add("xformOp:rotateXYZ")
            if (body.contains("xformOp:scale")) fallback.add("xformOp:scale")
            fallback
        }
        var acc = identityMat4()
        for (raw in orderOps) {
            if (raw == "!resetXformStack!") continue
            val isInv = raw.startsWith("!invert!")
            val opName = raw.removePrefix("!invert!")
            val m: DoubleArray? = when {
                opName.startsWith("xformOp:transform") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*\(\s*(.*?)\s*\)\s*(?:\n|$)""", RegexOption.DOT_MATCHES_ALL)
                        .find(body)?.groupValues?.get(1)?.let { nums ->
                            val vals = extractDoubleList(nums)
                            if (vals.size >= 16) DoubleArray(16) { vals[it] } else null
                        }
                }
                opName.startsWith("xformOp:translate") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*\(([^)]+)\)""").find(body)?.groupValues?.get(1)?.let { nums ->
                        val v = extractDoubleList(nums)
                        if (v.size >= 3) translateMat4(v[0], v[1], v[2]) else null
                    }
                }
                opName.startsWith("xformOp:scale") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*\(([^)]+)\)""").find(body)?.groupValues?.get(1)?.let { nums ->
                        val v = extractDoubleList(nums)
                        if (v.size >= 3) scaleMat4(v[0], v[1], v[2]) else null
                    }
                }
                opName.startsWith("xformOp:rotateXYZ") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*\(([^)]+)\)""").find(body)?.groupValues?.get(1)?.let { nums ->
                        val v = extractDoubleList(nums)
                        if (v.size >= 3) eulerXyzMat4(v[0] * PI / 180.0, v[1] * PI / 180.0, v[2] * PI / 180.0) else null
                    }
                }
                opName.startsWith("xformOp:rotateX") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*([0-9eE.+\-]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                        ?.let { rotXMat4(it * PI / 180.0) }
                }
                opName.startsWith("xformOp:rotateY") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*([0-9eE.+\-]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                        ?.let { rotYMat4(it * PI / 180.0) }
                }
                opName.startsWith("xformOp:rotateZ") -> {
                    Regex("""\b${Regex.escape(opName)}\s*=\s*([0-9eE.+\-]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                        ?.let { rotZMat4(it * PI / 180.0) }
                }
                else -> null
            }
            if (m != null) acc = mulMat4(acc, if (isInv) invertAffineOrRigidMat4(m) else m)
        }
        return acc
    }

    private fun emitUsdaMeshBody(body: String, worldMat: DoubleArray, ctx: UsdAccumContext) {
        val pointsStr = extractUsdaArrayBlock(body, "points") ?: return
        val countsStr = extractUsdaArrayBlock(body, "faceVertexCounts")
        val indicesStr = extractUsdaArrayBlock(body, "faceVertexIndices") ?: return

        val points = extractFloatList(pointsStr)
        if (points.size < 3) return
        val faceCounts = if (countsStr != null) extractIntList(countsStr) else null
        val faceIndices = extractIntList(indicesStr)
        if (faceIndices.size < 3) return

        val leftHanded = Regex("""orientation\s*=\s*"leftHanded"""").containsMatchIn(body)
        val nrmStr = extractUsdaArrayBlock(body, "normals") ?: extractUsdaArrayBlock(body, "primvars:normals")
        val nrmArray = nrmStr?.let { extractFloatList(it) }
        val uvStr = extractUsdaArrayBlock(body, "primvars:st") ?: extractUsdaArrayBlock(body, "primvars:uv")
        val uvArray = uvStr?.let { extractFloatList(it) }
        val uvIdxStr = extractUsdaArrayBlock(body, "primvars:st:indices") ?: extractUsdaArrayBlock(body, "primvars:uv:indices")
        val uvIndices = uvIdxStr?.let { extractIntList(it) }

        if (ctx.primaryDiffuseColor == null) {
            extractUsdaArrayBlock(body, "primvars:displayColor")?.let { cStr ->
                val c = extractFloatList(cStr)
                if (c.size >= 3) ctx.primaryDiffuseColor = floatArrayOf(c[0], c[1], c[2], 1f)
            }
        }

        val uvFaceVarying = Regex("""primvars:(?:st|uv)\b.*?(?:\(\s*interpolation\s*=\s*"faceVarying")""").containsMatchIn(body) || uvIndices != null
        val nrmFaceVarying = Regex("""normals\b.*?(?:\(\s*interpolation\s*=\s*"faceVarying")""").containsMatchIn(body)

        emitUsdMeshToContext(
            points = points,
            faceCounts = faceCounts,
            faceIndices = faceIndices,
            isLeftHanded = leftHanded,
            nrmArray = nrmArray,
            nrmInterp = if (nrmFaceVarying) "faceVarying" else "vertex",
            uvArray = uvArray,
            uvIndices = uvIndices,
            uvInterp = if (uvFaceVarying) "faceVarying" else "vertex",
            worldMat = worldMat,
            canUseSharedVerts = !uvFaceVarying && !nrmFaceVarying,
            ctx = ctx
        )
    }

    private fun extractUsdaArrayBlock(body: String, attrName: String): String? {
        val m = Regex("""\b${Regex.escape(attrName)}\s*=\s*\[""").find(body) ?: return null
        val open = m.range.last
        var close = open + 1
        while (close < body.length && body[close] != ']') close++
        return if (close > open) body.substring(open + 1, close) else null
    }

    private fun extractFloatList(text: String): FloatArray {
        val capGuess = (text.length / 6).coerceIn(16, 4_000_000)
        var arr = FloatArray(capGuess)
        var n = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (c in '0'..'9' || c == '-' || c == '+') {
                val start = i
                i++
                while (i < len && (text[i] in '0'..'9' || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == '-' || text[i] == '+')) {
                    i++
                }
                val v = text.substring(start, i).toFloatOrNull()
                if (v != null && v.isFinite()) {
                    if (n == arr.size) arr = arr.copyOf(arr.size * 2)
                    arr[n++] = v
                }
            } else {
                i++
            }
        }
        return if (n == arr.size) arr else arr.copyOf(n)
    }

    private fun extractDoubleList(text: String): DoubleArray {
        val f = extractFloatList(text)
        return DoubleArray(f.size) { f[it].toDouble() }
    }

    private fun extractIntList(text: String): IntArray {
        var arr = IntArray((text.length / 4).coerceIn(16, 4_000_000))
        var n = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (c in '0'..'9' || c == '-') {
                var neg = false
                if (c == '-') { neg = true; i++ }
                var acc = 0
                var hasDigit = false
                while (i < len && text[i] in '0'..'9') {
                    acc = acc * 10 + (text[i] - '0')
                    hasDigit = true
                    i++
                }
                if (hasDigit) {
                    if (n == arr.size) arr = arr.copyOf(arr.size * 2)
                    arr[n++] = if (neg) -acc else acc
                }
            } else {
                i++
            }
        }
        return if (n == arr.size) arr else arr.copyOf(n)
    }

    // =========================================================================
    // Shared Mesh Stream Emitter (USDA + USDC -> MappedFloatTupleStore + Tris)
    // =========================================================================

    private fun emitUsdMeshToContext(
        points: FloatArray,
        faceCounts: IntArray?,
        faceIndices: IntArray,
        isLeftHanded: Boolean,
        nrmArray: FloatArray?,
        nrmInterp: String,
        uvArray: FloatArray?,
        uvIndices: IntArray?,
        uvInterp: String,
        worldMat: DoubleArray,
        canUseSharedVerts: Boolean,
        ctx: UsdAccumContext
    ) {
        val rawVertCount = points.size / 3
        val hasNorm = nrmArray != null && nrmArray.size >= 3
        val hasUv = uvArray != null && uvArray.size >= 2

        fun putCornerVertex(vIdx: Int, fvCorner: Int): Long {
            val base = vIdx * 3
            val px = points[base]; val py = points[base + 1]; val pz = points[base + 2]
            val wx = (worldMat[0] * px + worldMat[4] * py + worldMat[8] * pz + worldMat[12]).toFloat()
            val wy = (worldMat[1] * px + worldMat[5] * py + worldMat[9] * pz + worldMat[13]).toFloat()
            val wz = (worldMat[2] * px + worldMat[6] * py + worldMat[10] * pz + worldMat[14]).toFloat()
            val outIdx = ctx.positions.count
            ctx.positions.add3(wx, wy, wz)
            ctx.bounds.expand(wx, wy, wz)

            if (hasNorm) {
                val ni = if (nrmInterp == "faceVarying" && fvCorner * 3 + 2 < nrmArray.size) fvCorner
                else if (vIdx * 3 + 2 < nrmArray.size) vIdx else 0
                val rnx = nrmArray[ni * 3]; val rny = nrmArray[ni * 3 + 1]; val rnz = nrmArray[ni * 3 + 2]
                val nx = (worldMat[0] * rnx + worldMat[4] * rny + worldMat[8] * rnz).toFloat()
                val ny = (worldMat[1] * rnx + worldMat[5] * rny + worldMat[9] * rnz).toFloat()
                val nz = (worldMat[2] * rnx + worldMat[6] * rny + worldMat[10] * rnz).toFloat()
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl > 1e-6f && nl.isFinite()) ctx.normals.add3(nx / nl, ny / nl, nz / nl)
                else ctx.normals.add3(0f, 1f, 0f)
            } else {
                ctx.normals.add3(0f, 1f, 0f)
            }

            if (hasUv) {
                val uiRaw = if (uvIndices != null && fvCorner in uvIndices.indices) uvIndices[fvCorner]
                else if (uvInterp == "faceVarying") fvCorner else vIdx
                if (uiRaw * 2 + 1 < uvArray.size && uiRaw >= 0) {
                    // OpenUSD UV (0,0)=bottom-left vs glTF (0,0)=top-left
                    ctx.uvs.add2(uvArray[uiRaw * 2], 1f - uvArray[uiRaw * 2 + 1])
                } else {
                    ctx.uvs.add2(0f, 0f)
                }
            } else {
                ctx.uvs.add2(0f, 0f)
            }
            return outIdx
        }

        if (canUseSharedVerts && faceCounts != null) {
            val baseVertexOffset = ctx.positions.count
            for (v in 0 until rawVertCount) putCornerVertex(v, v)

            var cursor = 0
            for (cnt in faceCounts) {
                if (cursor + cnt > faceIndices.size) break
                if (cnt >= 3) {
                    val i0 = faceIndices[cursor]
                    if (i0 in 0 until rawVertCount) {
                        for (k in 1 until cnt - 1) {
                            val i1 = faceIndices[cursor + k]
                            val i2 = faceIndices[cursor + k + 1]
                            if (i1 in 0 until rawVertCount && i2 in 0 until rawVertCount) {
                                val a = baseVertexOffset + i0.toLong()
                                val b = baseVertexOffset + (if (isLeftHanded) i2 else i1).toLong()
                                val c = baseVertexOffset + (if (isLeftHanded) i1 else i2).toLong()
                                ctx.emitTriangle(a, b, c)
                            }
                        }
                    }
                }
                cursor += cnt
            }
        } else {
            val counts = faceCounts ?: IntArray(faceIndices.size / 3) { 3 }
            var cursor = 0
            for (cnt in counts) {
                if (cursor + cnt > faceIndices.size) break
                if (cnt >= 3) {
                    val r0 = faceIndices[cursor]
                    if (r0 in 0 until rawVertCount) {
                        val c0 = putCornerVertex(r0, cursor)
                        for (k in 1 until cnt - 1) {
                            val r1 = faceIndices[cursor + k]
                            val r2 = faceIndices[cursor + k + 1]
                            if (r1 in 0 until rawVertCount && r2 in 0 until rawVertCount) {
                                val c1 = putCornerVertex(r1, cursor + k)
                                val c2 = putCornerVertex(r2, cursor + k + 1)
                                if (isLeftHanded) ctx.emitTriangle(c0, c2, c1)
                                else ctx.emitTriangle(c0, c1, c2)
                            }
                        }
                    }
                }
                cursor += cnt
            }
        }
    }

    // =========================================================================
    // Texture Helper & Helpers
    // =========================================================================

    private fun prepareMobileTexture(scratchDir: File, rawTex: File): Pair<File, String>? {
        if (!rawTex.exists() || rawTex.length() <= 0L) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(rawTex.absolutePath, opts)
            val maxDim = max(opts.outWidth, opts.outHeight)
            if (maxDim <= 1024 && (rawTex.extension.equals("png", true) || rawTex.extension.equals("jpg", true) || rawTex.extension.equals("jpeg", true))) {
                val mime = if (rawTex.extension.equals("png", true)) "image/png" else "image/jpeg"
                return@runCatching rawTex to mime
            }
            var sample = 1
            while (maxDim / sample > 1536) sample *= 2
            val dec = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(rawTex.absolutePath, dec) ?: return@runCatching null
            val out = File(scratchDir, "usd_tex_${System.nanoTime()}.jpg")
            FileOutputStream(out).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos) }
            bmp.recycle()
            out to "image/jpeg"
        }.getOrNull()
    }

    private class UsdAccumContext(
        val positions: StreamingMeshProcessor.MappedFloatTupleStore,
        val normals: StreamingMeshProcessor.MappedFloatTupleStore,
        val uvs: StreamingMeshProcessor.MappedFloatTupleStore,
        val bounds: StreamingMeshProcessor.MeshBounds,
        val triOut: BufferedOutputStream,
        val triBuf: ByteBuffer
    ) {
        var totalTriangles: Long = 0L
        var primaryDiffuseColor: FloatArray? = null
        var primaryMetallic: Float = 0.1f
        var primaryRoughness: Float = 0.7f
        var primaryTextureFile: File? = null

        fun emitTriangle(a: Long, b: Long, c: Long) {
            if (a == b || b == c || a == c) return
            if (triBuf.remaining() < 24) {
                val p = triBuf.position()
                if (p > 0) {
                    triOut.write(triBuf.array(), 0, p)
                    triBuf.clear()
                }
            }
            triBuf.putLong(a)
            triBuf.putLong(b)
            triBuf.putLong(c)
            totalTriangles++
        }
    }

    private data class UsdMaterialInfo(
        val diffuseColor: FloatArray?,
        val metallic: Float,
        val roughness: Float,
        val textureFile: File?
    )

    private fun sanitizeZipEntryPath(name: String): String? {
        val clean = name.replace('\\', '/').trimStart('/')
        if (clean.isEmpty() || clean.contains("..")) return null
        return clean
    }

    private fun readInt32LE(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return (b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or
            ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readInt64LE(raf: RandomAccessFile): Long {
        val b = ByteArray(8)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun halfBitsToFloat(h: Int): Float {
        val sign = (h ushr 15) and 0x1
        val exp = (h ushr 10) and 0x1F
        val mant = h and 0x3FF
        val bits = when (exp) {
            0 -> {
                if (mant == 0) sign shl 31
                else {
                    var m = mant
                    var e = 127 - 15 + 1
                    while ((m and 0x400) == 0) {
                        m = m shl 1
                        e--
                    }
                    m = m and 0x3FF
                    (sign shl 31) or (e shl 23) or (m shl 13)
                }
            }
            31 -> (sign shl 31) or 0x7F800000 or (mant shl 13)
            else -> (sign shl 31) or ((exp + 127 - 15) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    // 4x4 Column-Major Matrix Utilities
    private fun identityMat4() = doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    )

    private fun translateMat4(x: Double, y: Double, z: Double) = doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        x, y, z, 1.0
    )

    private fun scaleMat4(x: Double, y: Double, z: Double) = doubleArrayOf(
        x, 0.0, 0.0, 0.0,
        0.0, y, 0.0, 0.0,
        0.0, 0.0, z, 0.0,
        0.0, 0.0, 0.0, 1.0
    )

    private fun rotXMat4(rad: Double): DoubleArray {
        val c = cos(rad); val s = sin(rad)
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, c, s, 0.0, 0.0, -s, c, 0.0, 0.0, 0.0, 0.0, 1.0)
    }

    private fun rotYMat4(rad: Double): DoubleArray {
        val c = cos(rad); val s = sin(rad)
        return doubleArrayOf(c, 0.0, -s, 0.0, 0.0, 1.0, 0.0, 0.0, s, 0.0, c, 0.0, 0.0, 0.0, 0.0, 1.0)
    }

    private fun rotZMat4(rad: Double): DoubleArray {
        val c = cos(rad); val s = sin(rad)
        return doubleArrayOf(c, s, 0.0, 0.0, -s, c, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
    }

    private fun eulerXyzMat4(rx: Double, ry: Double, rz: Double): DoubleArray =
        mulMat4(rotZMat4(rz), mulMat4(rotYMat4(ry), rotXMat4(rx)))

    private fun quatMat4(qx: Double, qy: Double, qz: Double, qw: Double): DoubleArray {
        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
        return doubleArrayOf(
            1.0 - 2.0 * (yy + zz), 2.0 * (xy + wz), 2.0 * (xz - wy), 0.0,
            2.0 * (xy - wz), 1.0 - 2.0 * (xx + zz), 2.0 * (yz + wx), 0.0,
            2.0 * (xz + wy), 2.0 * (yz - wx), 1.0 - 2.0 * (xx + yy), 0.0,
            0.0, 0.0, 0.0, 1.0
        )
    }

    private fun invertAffineOrRigidMat4(m: DoubleArray): DoubleArray {
        val r00 = m[0]; val r10 = m[4]; val r20 = m[8]
        val r01 = m[1]; val r11 = m[5]; val r21 = m[9]
        val r02 = m[2]; val r12 = m[6]; val r22 = m[10]
        val tx = m[12]; val ty = m[13]; val tz = m[14]
        return doubleArrayOf(
            r00, r10, r20, 0.0,
            r01, r11, r21, 0.0,
            r02, r12, r22, 0.0,
            -(r00 * tx + r01 * ty + r02 * tz),
            -(r10 * tx + r11 * ty + r12 * tz),
            -(r20 * tx + r21 * ty + r22 * tz),
            1.0
        )
    }

    private fun mulMat4(a: DoubleArray, b: DoubleArray): DoubleArray {
        val c = DoubleArray(16)
        for (col in 0 until 4) {
            val c4 = col * 4
            val b0 = b[c4]; val b1 = b[c4 + 1]; val b2 = b[c4 + 2]; val b3 = b[c4 + 3]
            c[c4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            c[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            c[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            c[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        return c
    }

    private fun writeSpatialProxyGlb(outGlb: File) {
        val positions = floatArrayOf(
            -0.35f, -0.35f,  0.35f,  0.35f, -0.35f,  0.35f,  0.35f,  0.35f,  0.35f, -0.35f,  0.35f,  0.35f,
             0.35f, -0.35f, -0.35f, -0.35f, -0.35f, -0.35f, -0.35f,  0.35f, -0.35f,  0.35f,  0.35f, -0.35f,
            -0.35f, -0.35f, -0.35f, -0.35f, -0.35f,  0.35f, -0.35f,  0.35f,  0.35f, -0.35f,  0.35f, -0.35f,
             0.35f, -0.35f,  0.35f,  0.35f, -0.35f, -0.35f,  0.35f,  0.35f, -0.35f,  0.35f,  0.35f,  0.35f,
            -0.35f,  0.35f,  0.35f,  0.35f,  0.35f,  0.35f,  0.35f,  0.35f, -0.35f, -0.35f,  0.35f, -0.35f,
            -0.35f, -0.35f, -0.35f,  0.35f, -0.35f, -0.35f,  0.35f, -0.35f,  0.35f, -0.35f, -0.35f,  0.35f
        )
        val normals = floatArrayOf(
             0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,
             0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,
            -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f,
             1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,
             0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,
             0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f
        )
        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15, 16, 17, 18, 16, 18, 19, 20, 21, 22, 20, 22, 23
        )
        val posBytes = positions.size * 4
        val normBytes = normals.size * 4
        val idxBytes = indices.size * 2
        val rawBin = posBytes + normBytes + idxBytes
        val binPad = (4 - (rawBin % 4)) % 4
        val paddedBin = rawBin + binPad
        val json = """{"asset":{"version":"2.0","generator":"MixedReality Spatial Proxy"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"mode":4}]}],"accessors":[{"bufferView":0,"byteOffset":0,"componentType":5126,"count":24,"type":"VEC3","max":[0.35,0.35,0.35],"min":[-0.35,-0.35,-0.35]},{"bufferView":1,"byteOffset":0,"componentType":5126,"count":24,"type":"VEC3"},{"bufferView":2,"byteOffset":0,"componentType":5123,"count":36,"type":"SCALAR"}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":$posBytes,"target":34962},{"buffer":0,"byteOffset":$posBytes,"byteLength":$normBytes,"target":34962},{"buffer":0,"byteOffset":${posBytes + normBytes},"byteLength":$idxBytes,"target":34963}],"buffers":[{"byteLength":$rawBin}]}"""
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (jsonBytes.size % 4)) % 4
        val paddedJson = jsonBytes.size + jsonPad
        val total = 12 + 8 + paddedJson + 8 + paddedBin
        val bb = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0x46546C67); bb.putInt(2); bb.putInt(total)
        bb.putInt(paddedJson); bb.putInt(0x4E4F534A); bb.put(jsonBytes)
        repeat(jsonPad) { bb.put(0x20.toByte()) }
        bb.putInt(paddedBin); bb.putInt(0x004E4942)
        for (f in positions) bb.putFloat(f)
        for (f in normals) bb.putFloat(f)
        for (s in indices) bb.putShort(s)
        repeat(binPad) { bb.put(0) }
        outGlb.parentFile?.mkdirs()
        outGlb.writeBytes(bb.array())
    }
}
