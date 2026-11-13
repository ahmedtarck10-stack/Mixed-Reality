package com.aistudio.mixedreality.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-capacity streaming 3D geometry processor engineered for mobile RAM/GPU constraints.
 * Supports up to 30,000,000+ vertices and 30,000,000+ polygons for OBJ, STL, PLY, 3MF, and GLB
 * using disk-paged float buffers, zero-boxing primitives, and adaptive spatial vertex clustering.
 */
object StreamingMeshProcessor {

    private const val TAG = "StreamingMeshProcessor"
    private const val IO_BUFFER_BYTES = 524_288 // 512 KB streaming buffer
    private const val GLB_CHUNK_BYTES = 65_536

    // Lossless direct emission up to 1.8M triangles; above that, adaptive vertex clustering
    // preserves silhouette and topology within mobile ES3.0/Vulkan GPU memory bounds (~1.25M tris).
    const val DIRECT_TRIANGLE_LIMIT = 1_800_000
    const val CLUSTER_MAX_VERTICES = 1_150_000
    const val CLUSTER_HASH_TABLE_SIZE = 2_097_152 // Power of 2; ~16 MB hash keys

    data class MeshStats(
        val originalVertices: Int,
        val originalTriangles: Int,
        val outputVertices: Int,
        val outputTriangles: Int,
        val wasSimplified: Boolean
    )

    data class GlbInspection(
        val isValidGlb: Boolean,
        val jsonByteLength: Int,
        val binByteLength: Long,
        val vertexCount: Long,
        val triangleCount: Long,
        val hasDraco: Boolean,
        val hasMeshopt: Boolean,
        val hasWebp: Boolean,
        val hasUncompressedAccessors: Boolean,
        val imageCount: Int
    )

    private data class BoundingBox(
        var minX: Float = Float.MAX_VALUE,
        var minY: Float = Float.MAX_VALUE,
        var minZ: Float = Float.MAX_VALUE,
        var maxX: Float = -Float.MAX_VALUE,
        var maxY: Float = -Float.MAX_VALUE,
        var maxZ: Float = -Float.MAX_VALUE
    ) {
        fun expand(x: Float, y: Float, z: Float) {
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }

        fun normalizeIfEmpty() {
            if (minX > maxX || !minX.isFinite()) {
                minX = -0.5f; maxX = 0.5f
                minY = -0.5f; maxY = 0.5f
                minZ = -0.5f; maxZ = 0.5f
            }
        }
    }

    /**
     * Paged float-tuple storage that stays in RAM for <= 524K entries (~6 MB) and automatically
     * spills cold pages to a temp file on disk so 30,000,000 vertices only use ~18 MB Java heap.
     */
    private class PagedFloatTupleStore(
        private val tupleSize: Int,
        private val scratchDir: File,
        private val tag: String
    ) : AutoCloseable {
        private val entriesPerPage = 131_072 // 131K * 3 floats * 4B = 1.5 MB per page
        private val pageFloatCount = entriesPerPage * tupleSize
        private val pageByteCount = pageFloatCount.toLong() * 4L
        private val maxRamPages = 8 // Max ~12-18 MB cached in Java Heap

        private val ramPages = LinkedHashMap<Int, FloatArray>(16, 0.75f, true)
        private val dirtyPages = HashSet<Int>()
        private var diskFile: File? = null
        private var raf: RandomAccessFile? = null
        private var ioByteBuf: ByteBuffer? = null

        var count: Int = 0
            private set

        private var activeWritePageIdx = -1
        private var activeWritePage: FloatArray? = null

        fun add3(x: Float, y: Float, z: Float) {
            val idx = count
            val pIdx = idx / entriesPerPage
            val offset = (idx % entriesPerPage) * 3
            val page = obtainWritePage(pIdx)
            page[offset] = x
            page[offset + 1] = y
            page[offset + 2] = z
            dirtyPages.add(pIdx)
            count = idx + 1
        }

        fun add2(u: Float, v: Float) {
            val idx = count
            val pIdx = idx / entriesPerPage
            val offset = (idx % entriesPerPage) * 2
            val page = obtainWritePage(pIdx)
            page[offset] = u
            page[offset + 1] = v
            dirtyPages.add(pIdx)
            count = idx + 1
        }

        fun getX(idx: Int): Float = readComponent(idx, 0)
        fun getY(idx: Int): Float = readComponent(idx, 1)
        fun getZ(idx: Int): Float = readComponent(idx, 2)
        fun getU(idx: Int): Float = readComponent(idx, 0)
        fun getV(idx: Int): Float = readComponent(idx, 1)

        private fun readComponent(idx: Int, comp: Int): Float {
            if (idx < 0 || idx >= count) return 0f
            val pIdx = idx / entriesPerPage
            val offset = (idx % entriesPerPage) * tupleSize + comp
            val page = if (pIdx == activeWritePageIdx && activeWritePage != null) {
                activeWritePage!!
            } else {
                obtainReadPage(pIdx)
            }
            return page[offset]
        }

        private fun obtainWritePage(pIdx: Int): FloatArray {
            if (pIdx == activeWritePageIdx && activeWritePage != null) {
                return activeWritePage!!
            }
            val existing = ramPages[pIdx]
            if (existing != null) {
                activeWritePageIdx = pIdx
                activeWritePage = existing
                return existing
            }
            evictIfNeeded(reserveForNew = true)
            val fresh = FloatArray(pageFloatCount)
            ramPages[pIdx] = fresh
            activeWritePageIdx = pIdx
            activeWritePage = fresh
            return fresh
        }

        private fun obtainReadPage(pIdx: Int): FloatArray {
            val cached = ramPages[pIdx]
            if (cached != null) return cached

            evictIfNeeded(reserveForNew = true)
            val arr = FloatArray(pageFloatCount)
            val fileRaf = raf
            if (fileRaf != null) {
                val byteOffset = pIdx.toLong() * pageByteCount
                if (byteOffset < fileRaf.length()) {
                    val buf = ensureIoBuf()
                    buf.clear()
                    val available = minOf(pageByteCount, fileRaf.length() - byteOffset).toInt()
                    fileRaf.seek(byteOffset)
                    fileRaf.readFully(buf.array(), 0, available)
                    buf.limit(available)
                    buf.rewind()
                    val floatCount = available / 4
                    buf.asFloatBuffer().get(arr, 0, floatCount)
                }
            }
            ramPages[pIdx] = arr
            return arr
        }

        private fun evictIfNeeded(reserveForNew: Boolean) {
            val cap = if (reserveForNew) maxRamPages - 1 else maxRamPages
            while (ramPages.size > cap) {
                val it = ramPages.entries.iterator()
                if (!it.hasNext()) break
                var victimEntry = it.next()
                if (victimEntry.key == activeWritePageIdx && it.hasNext()) {
                    victimEntry = it.next()
                }
                val vKey = victimEntry.key
                val vArr = victimEntry.value
                if (dirtyPages.remove(vKey)) {
                    spillPageToDisk(vKey, vArr)
                }
                ramPages.remove(vKey)
                if (vKey == activeWritePageIdx) {
                    activeWritePageIdx = -1
                    activeWritePage = null
                }
            }
        }

        private fun spillPageToDisk(pIdx: Int, data: FloatArray) {
            val fileRaf = ensureRaf()
            val buf = ensureIoBuf()
            buf.clear()
            buf.asFloatBuffer().put(data)
            fileRaf.seek(pIdx.toLong() * pageByteCount)
            fileRaf.write(buf.array(), 0, pageByteCount.toInt())
        }

        private fun ensureRaf(): RandomAccessFile {
            raf?.let { return it }
            scratchDir.mkdirs()
            val file = File(scratchDir, "vtx_${tag}_${System.nanoTime()}.bin")
            diskFile = file
            return RandomAccessFile(file, "rw").also { raf = it }
        }

        private fun ensureIoBuf(): ByteBuffer {
            ioByteBuf?.let { return it }
            return ByteBuffer.allocate(pageByteCount.toInt())
                .order(ByteOrder.LITTLE_ENDIAN)
                .also { ioByteBuf = it }
        }

        override fun close() {
            ramPages.clear()
            dirtyPages.clear()
            activeWritePage = null
            activeWritePageIdx = -1
            ioByteBuf = null
            runCatching { raf?.close() }
            raf = null
            runCatching { diskFile?.delete() }
            diskFile = null
        }
    }

    /**
     * Streaming spatial vertex clustering engine that converts huge triangle streams (up to 30M+ tris)
     * into a crisp, watertight-oriented GLB mesh well within mobile GPU/VRAM budgets.
     */
    private class SpatialClusterAccumulator(
        private val bounds: BoundingBox,
        private val gridResolution: Int,
        private val maxClusters: Int = CLUSTER_MAX_VERTICES
    ) {
        private val tableMask = CLUSTER_HASH_TABLE_SIZE - 1
        private var hashKeys = LongArray(CLUSTER_HASH_TABLE_SIZE) { -1L }
        private var hashVals = IntArray(CLUSTER_HASH_TABLE_SIZE)

        private var sumX = FloatArray(maxClusters)
        private var sumY = FloatArray(maxClusters)
        private var sumZ = FloatArray(maxClusters)
        private var normX = FloatArray(maxClusters)
        private var normY = FloatArray(maxClusters)
        private var normZ = FloatArray(maxClusters)
        private var sumU = FloatArray(maxClusters)
        private var sumV = FloatArray(maxClusters)
        private var counts = IntArray(maxClusters)

        var clusterCount = 0
            private set
        var overflowed = false
            private set

        private val spanX = max(1e-6f, bounds.maxX - bounds.minX)
        private val spanY = max(1e-6f, bounds.maxY - bounds.minY)
        private val spanZ = max(1e-6f, bounds.maxZ - bounds.minZ)
        private val maxSpan = max(spanX, max(spanY, spanZ))
        private val invCellSize = (gridResolution - 1).toFloat() / maxSpan

        private var triFile: File? = null
        private var triFos: BufferedOutputStream? = null
        private val triChunk = ByteBuffer.allocate(65_536).order(ByteOrder.LITTLE_ENDIAN)
        var emittedTriangles = 0
            private set

        fun startTriangleStream(scratchDir: File) {
            scratchDir.mkdirs()
            val f = File(scratchDir, "tris_${System.nanoTime()}.bin")
            triFile = f
            triFos = BufferedOutputStream(FileOutputStream(f), IO_BUFFER_BYTES)
        }

        fun getOrAssignCluster(
            px: Float, py: Float, pz: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float
        ): Int {
            val gx = ((px - bounds.minX) * invCellSize).toInt().coerceIn(0, 1_048_575).toLong()
            val gy = ((py - bounds.minY) * invCellSize).toInt().coerceIn(0, 1_048_575).toLong()
            val gz = ((pz - bounds.minZ) * invCellSize).toInt().coerceIn(0, 1_048_575).toLong()
            val key = (gx shl 40) or (gy shl 20) or gz

            var slot = mix64(key) and tableMask
            while (true) {
                val existing = hashKeys[slot]
                if (existing == key) {
                    val cId = hashVals[slot]
                    accumulate(cId, px, py, pz, nx, ny, nz, u, v)
                    return cId
                }
                if (existing == -1L) {
                    if (clusterCount >= maxClusters) {
                        overflowed = true
                        return -1
                    }
                    val cId = clusterCount++
                    hashKeys[slot] = key
                    hashVals[slot] = cId
                    accumulate(cId, px, py, pz, nx, ny, nz, u, v)
                    return cId
                }
                slot = (slot + 1) and tableMask
            }
        }

        private fun accumulate(
            cId: Int,
            px: Float, py: Float, pz: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float
        ) {
            sumX[cId] += px
            sumY[cId] += py
            sumZ[cId] += pz
            normX[cId] += nx
            normY[cId] += ny
            normZ[cId] += nz
            sumU[cId] += u
            sumV[cId] += v
            counts[cId]++
        }

        fun addTriangle(c0: Int, c1: Int, c2: Int) {
            if (c0 < 0 || c1 < 0 || c2 < 0) return
            if (c0 == c1 || c1 == c2 || c2 == c0) return
            val out = triFos ?: return
            if (triChunk.remaining() < 12) {
                out.write(triChunk.array(), 0, triChunk.position())
                triChunk.clear()
            }
            triChunk.putInt(c0)
            triChunk.putInt(c1)
            triChunk.putInt(c2)
            emittedTriangles++
        }

        fun writeToGlb(outGlb: File): BoundingBox {
            triFos?.let { out ->
                if (triChunk.position() > 0) {
                    out.write(triChunk.array(), 0, triChunk.position())
                    triChunk.clear()
                }
                out.flush()
                out.close()
            }
            triFos = null

            val vertCount = clusterCount.coerceAtLeast(3)
            val finalBounds = BoundingBox()

            for (i in 0 until clusterCount) {
                val inv = 1f / counts[i].coerceAtLeast(1).toFloat()
                val px = sumX[i] * inv
                val py = sumY[i] * inv
                val pz = sumZ[i] * inv
                sumX[i] = px
                sumY[i] = py
                sumZ[i] = pz
                finalBounds.expand(px, py, pz)

                val nLen = sqrt(normX[i] * normX[i] + normY[i] * normY[i] + normZ[i] * normZ[i])
                if (nLen > 1e-6f && nLen.isFinite()) {
                    val nInv = 1f / nLen
                    normX[i] *= nInv
                    normY[i] *= nInv
                    normZ[i] *= nInv
                } else {
                    normX[i] = 0f; normY[i] = 1f; normZ[i] = 0f
                }

                sumU[i] *= inv
                sumV[i] *= inv
            }
            finalBounds.normalizeIfEmpty()

            // Release hash lookup arrays before GLB output
            hashKeys = LongArray(0)
            hashVals = IntArray(0)
            counts = IntArray(0)

            val indexCount = emittedTriangles * 3
            val useIntIndices = clusterCount > 65535 || indexCount > 65535
            val posBytes = clusterCount.toLong() * 3L * 4L
            val normBytes = clusterCount.toLong() * 3L * 4L
            val uvBytes = clusterCount.toLong() * 2L * 4L
            val idxBytes = if (useIntIndices) indexCount.toLong() * 4L else indexCount.toLong() * 2L
            val totalBinBytes = posBytes + normBytes + uvBytes + idxBytes
            val binPadding = ((4L - (totalBinBytes % 4L)) % 4L).toInt()
            val paddedBinBytes = totalBinBytes + binPadding

            val jsonBytes = buildStandardGlbJson(
                vertCount = clusterCount,
                indexCount = indexCount,
                useIntIndices = useIntIndices,
                posByteLength = posBytes,
                normByteLength = normBytes,
                uvByteLength = uvBytes,
                indexByteLength = idxBytes,
                totalBinaryLength = totalBinBytes,
                bounds = finalBounds,
                generator = "MixedReality Streaming Cluster Engine"
            )
            val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
            val paddedJsonLen = jsonBytes.size + jsonPadding
            val totalGlbLen = (12L + 8L + paddedJsonLen + 8L + paddedBinBytes).toInt()

            outGlb.parentFile?.mkdirs()
            val writeBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            BufferedOutputStream(FileOutputStream(outGlb), IO_BUFFER_BYTES).use { bos ->
                writeGlbHeaders(bos, writeBuf, totalGlbLen, jsonBytes, paddedJsonLen, jsonPadding, paddedBinBytes.toInt())

                for (i in 0 until clusterCount) {
                    putFloat3Checked(bos, writeBuf, sumX[i], sumY[i], sumZ[i])
                }
                for (i in 0 until clusterCount) {
                    putFloat3Checked(bos, writeBuf, normX[i], normY[i], normZ[i])
                }
                for (i in 0 until clusterCount) {
                    putFloat2Checked(bos, writeBuf, sumU[i], sumV[i])
                }

                // Stream indices cleanly from temp disk file
                val tFile = triFile
                if (tFile != null && tFile.exists() && emittedTriangles > 0) {
                    val readChunk = ByteArray(65_536 * 3) // Divisible by 12
                    val readBB = ByteBuffer.wrap(readChunk).order(ByteOrder.LITTLE_ENDIAN)
                    BufferedInputStream(FileInputStream(tFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val read = bis.read(readChunk)
                            if (read <= 0) break
                            var off = 0
                            while (off + 4 <= read) {
                                val idx = readBB.getInt(off)
                                if (useIntIndices) {
                                    if (writeBuf.remaining() < 4) flushWriteBuf(bos, writeBuf)
                                    writeBuf.putInt(idx)
                                } else {
                                    if (writeBuf.remaining() < 2) flushWriteBuf(bos, writeBuf)
                                    writeBuf.putShort(idx.toShort())
                                }
                                off += 4
                            }
                        }
                    }
                }
                for (p in 0 until binPadding) {
                    if (writeBuf.remaining() < 1) flushWriteBuf(bos, writeBuf)
                    writeBuf.put(0.toByte())
                }
                flushWriteBuf(bos, writeBuf)
            }
            cleanup()
            return finalBounds
        }

        fun cleanup() {
            runCatching { triFos?.close() }
            triFos = null
            runCatching { triFile?.delete() }
            triFile = null
        }

        private fun mix64(k: Long): Int {
            var h = k
            h = h xor (h ushr 33)
            h *= -0xae502812aa7333L
            h = h xor (h ushr 33)
            return h.toInt()
        }
    }

    /**
     * Lossless disk-backed stream writer for <= DIRECT_TRIANGLE_LIMIT meshes (< ~1.8M triangles).
     * Never allocates huge FloatArray/IntArray slabs in Java heap.
     */
    private class DirectStreamingGlbWriter(
        private val scratchDir: File
    ) : AutoCloseable {
        private val id = System.nanoTime()
        private val posFile = File(scratchDir, "dpos_$id.bin")
        private val normFile = File(scratchDir, "dnorm_$id.bin")
        private val uvFile = File(scratchDir, "duv_$id.bin")
        private val idxFile = File(scratchDir, "didx_$id.bin")

        private val posOut = BufferedOutputStream(FileOutputStream(posFile), IO_BUFFER_BYTES)
        private val normOut = BufferedOutputStream(FileOutputStream(normFile), IO_BUFFER_BYTES)
        private val uvOut = BufferedOutputStream(FileOutputStream(uvFile), IO_BUFFER_BYTES)
        private val idxOut = BufferedOutputStream(FileOutputStream(idxFile), IO_BUFFER_BYTES)

        private val posBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        private val normBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        private val uvBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        private val idxBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

        var vertexCount = 0
            private set
        var indexCount = 0
            private set
        val bounds = BoundingBox()

        fun emitVertex(
            px: Float, py: Float, pz: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float
        ): Int {
            val vIdx = vertexCount++
            bounds.expand(px, py, pz)
            putFloat3Checked(posOut, posBuf, px, py, pz)
            putFloat3Checked(normOut, normBuf, nx, ny, nz)
            putFloat2Checked(uvOut, uvBuf, u, v)
            return vIdx
        }

        fun emitTriangle(i0: Int, i1: Int, i2: Int) {
            if (idxBuf.remaining() < 12) {
                flushWriteBuf(idxOut, idxBuf)
            }
            idxBuf.putInt(i0)
            idxBuf.putInt(i1)
            idxBuf.putInt(i2)
            indexCount += 3
        }

        fun finalizeToGlb(outGlb: File, generator: String) {
            flushWriteBuf(posOut, posBuf); posOut.close()
            flushWriteBuf(normOut, normBuf); normOut.close()
            flushWriteBuf(uvOut, uvBuf); uvOut.close()
            flushWriteBuf(idxOut, idxBuf); idxOut.close()
            bounds.normalizeIfEmpty()

            val useIntIndices = vertexCount > 65535 || indexCount > 65535
            val posBytes = vertexCount.toLong() * 3L * 4L
            val normBytes = vertexCount.toLong() * 3L * 4L
            val uvBytes = vertexCount.toLong() * 2L * 4L
            val idxBytes = if (useIntIndices) indexCount.toLong() * 4L else indexCount.toLong() * 2L
            val totalBinBytes = posBytes + normBytes + uvBytes + idxBytes
            val binPadding = ((4L - (totalBinBytes % 4L)) % 4L).toInt()
            val paddedBinBytes = totalBinBytes + binPadding

            val jsonBytes = buildStandardGlbJson(
                vertCount = vertexCount,
                indexCount = indexCount,
                useIntIndices = useIntIndices,
                posByteLength = posBytes,
                normByteLength = normBytes,
                uvByteLength = uvBytes,
                indexByteLength = idxBytes,
                totalBinaryLength = totalBinBytes,
                bounds = bounds,
                generator = generator
            )
            val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
            val paddedJsonLen = jsonBytes.size + jsonPadding
            val totalGlbLen = (12L + 8L + paddedJsonLen + 8L + paddedBinBytes).toInt()

            outGlb.parentFile?.mkdirs()
            val writeBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            val copyChunk = ByteArray(IO_BUFFER_BYTES)

            BufferedOutputStream(FileOutputStream(outGlb), IO_BUFFER_BYTES).use { bos ->
                writeGlbHeaders(bos, writeBuf, totalGlbLen, jsonBytes, paddedJsonLen, jsonPadding, paddedBinBytes.toInt())
                appendFileRaw(posFile, bos, copyChunk)
                appendFileRaw(normFile, bos, copyChunk)
                appendFileRaw(uvFile, bos, copyChunk)

                if (useIntIndices) {
                    appendFileRaw(idxFile, bos, copyChunk)
                } else {
                    val readBB = ByteBuffer.wrap(copyChunk).order(ByteOrder.LITTLE_ENDIAN)
                    BufferedInputStream(FileInputStream(idxFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val read = bis.read(copyChunk)
                            if (read <= 0) break
                            var off = 0
                            while (off + 4 <= read) {
                                if (writeBuf.remaining() < 2) flushWriteBuf(bos, writeBuf)
                                writeBuf.putShort(readBB.getInt(off).toShort())
                                off += 4
                            }
                        }
                    }
                    flushWriteBuf(bos, writeBuf)
                }

                for (p in 0 until binPadding) bos.write(0)
            }
        }

        private fun appendFileRaw(src: File, dst: BufferedOutputStream, buf: ByteArray) {
            BufferedInputStream(FileInputStream(src), IO_BUFFER_BYTES).use { bis ->
                while (true) {
                    val n = bis.read(buf)
                    if (n <= 0) break
                    dst.write(buf, 0, n)
                }
            }
        }

        override fun close() {
            runCatching { posOut.close() }
            runCatching { normOut.close() }
            runCatching { uvOut.close() }
            runCatching { idxOut.close() }
            runCatching { posFile.delete() }
            runCatching { normFile.delete() }
            runCatching { uvFile.delete() }
            runCatching { idxFile.delete() }
        }
    }

    /**
     * Converts Binary or ASCII STL of any triangle count (250K up to 30,000,000+ triangles)
     * without SceneView's 32 MB / 250K hardcoded caps.
     */
    fun convertStlToGlb(cacheDir: File, stlFile: File): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "mesh_scratch_${System.nanoTime()}").apply { mkdirs() }
        try {
            val fileLen = stlFile.length()
            val header = ByteArray(minOf(fileLen, 512L).toInt())
            FileInputStream(stlFile).use { it.read(header) }
            val isAscii = isAsciiStl(header, fileLen)

            val bounds = BoundingBox()
            var totalTris = 0

            // Pass 1: exact triangle count and bounding box
            if (!isAscii && fileLen >= 84L) {
                val lenBB = ByteBuffer.wrap(header, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
                val declaredTris = lenBB.int.toLong() and 0xFFFF_FFFFL
                val maxPossible = ((fileLen - 84L) / 50L).coerceAtLeast(0L)
                val binaryCount = minOf(declaredTris, maxPossible).toInt()
                totalTris = binaryCount
                val recBuf = ByteArray(50 * 4096)
                val recBB = ByteBuffer.wrap(recBuf).order(ByteOrder.LITTLE_ENDIAN)
                BufferedInputStream(FileInputStream(stlFile), IO_BUFFER_BYTES).use { bis ->
                    skipFully(bis, 84L)
                    var remaining = binaryCount
                    while (remaining > 0) {
                        val batch = minOf(remaining, 4096)
                        readFully(bis, recBuf, batch * 50)
                        for (i in 0 until batch) {
                            val base = i * 50
                            bounds.expand(recBB.getFloat(base + 12), recBB.getFloat(base + 16), recBB.getFloat(base + 20))
                            bounds.expand(recBB.getFloat(base + 24), recBB.getFloat(base + 28), recBB.getFloat(base + 32))
                            bounds.expand(recBB.getFloat(base + 36), recBB.getFloat(base + 40), recBB.getFloat(base + 44))
                        }
                        remaining -= batch
                    }
                }
            } else {
                var vInTri = 0
                stlFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trimStart()
                        if (trimmed.startsWith("vertex", ignoreCase = true)) {
                            parseThreeFloatsAfterToken(trimmed, 6)?.let { (x, y, z) ->
                                bounds.expand(x, y, z)
                                vInTri++
                                if (vInTri == 3) {
                                    totalTris++
                                    vInTri = 0
                                }
                            }
                        }
                    }
                }
            }

            if (totalTris <= 0) {
                return ModelFormatConverter.ConversionResult(
                    glbFile = stlFile,
                    originalFormat = "STL",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "No triangles found in STL file",
                    isSuccess = false
                )
            }
            bounds.normalizeIfEmpty()

            val outGlb = File(cacheDir, "${stlFile.nameWithoutExtension}_converted.glb")
            val stats = emitStlPass2(
                stlFile = stlFile,
                isAscii = isAscii,
                totalTris = totalTris,
                bounds = bounds,
                scratchDir = scratchDir,
                outGlb = outGlb
            )

            val note = if (stats.wasSimplified) {
                "STL (${formatCompact(stats.originalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles)} tris for mobile GPU"
            } else {
                "STL (${formatCompact(stats.outputTriangles)} triangles) loaded natively"
            }
            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "STL",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = note,
                isSuccess = true
            )
        } catch (t: Throwable) {
            Log.e(TAG, "STL streaming conversion error: ${t.message}", t)
            return ModelFormatConverter.ConversionResult(
                glbFile = stlFile,
                originalFormat = "STL",
                triangleCount = 0,
                vertexCount = 0,
                message = "STL conversion error: ${t.message ?: t::class.java.simpleName}",
                isSuccess = false
            )
        } finally {
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    private fun emitStlPass2(
        stlFile: File,
        isAscii: Boolean,
        totalTris: Int,
        bounds: BoundingBox,
        scratchDir: File,
        outGlb: File
    ): MeshStats {
        // STL stores 3 unshared corners per tri; cluster even medium-large STLs (>600K tris -> >1.8M unshared verts)
        // OR use direct deduplication below threshold cleanly.
        val useCluster = totalTris > 600_000
        if (!useCluster) {
            DirectStreamingGlbWriter(scratchDir).use { writer ->
                forEachStlTriangle(stlFile, isAscii, totalTris) { nx, ny, nz, x0, y0, z0, x1, y1, z1, x2, y2, z2 ->
                    val (fnx, fny, fnz) = ensureNormal(nx, ny, nz, x0, y0, z0, x1, y1, z1, x2, y2, z2)
                    val i0 = writer.emitVertex(x0, y0, z0, fnx, fny, fnz, 0f, 0f)
                    val i1 = writer.emitVertex(x1, y1, z1, fnx, fny, fnz, 0.5f, 0f)
                    val i2 = writer.emitVertex(x2, y2, z2, fnx, fny, fnz, 0f, 0.5f)
                    writer.emitTriangle(i0, i1, i2)
                }
                writer.finalizeToGlb(outGlb, "MixedReality Streaming STL Engine")
                return MeshStats(
                    originalVertices = totalTris * 3,
                    originalTriangles = totalTris,
                    outputVertices = writer.vertexCount,
                    outputTriangles = writer.indexCount / 3,
                    wasSimplified = false
                )
            }
        }

        var gridRes = estimateInitialGridResolution(totalTris)
        while (true) {
            val acc = SpatialClusterAccumulator(bounds, gridRes)
            acc.startTriangleStream(scratchDir)
            try {
                forEachStlTriangle(stlFile, isAscii, totalTris) { nx, ny, nz, x0, y0, z0, x1, y1, z1, x2, y2, z2 ->
                    if (acc.overflowed) return@forEachStlTriangle
                    val (fnx, fny, fnz) = ensureNormal(nx, ny, nz, x0, y0, z0, x1, y1, z1, x2, y2, z2)
                    val c0 = acc.getOrAssignCluster(x0, y0, z0, fnx, fny, fnz, 0f, 0f)
                    val c1 = acc.getOrAssignCluster(x1, y1, z1, fnx, fny, fnz, 0f, 0f)
                    val c2 = acc.getOrAssignCluster(x2, y2, z2, fnx, fny, fnz, 0f, 0f)
                    acc.addTriangle(c0, c1, c2)
                }
                if (acc.overflowed && gridRes > 96) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(96)
                    continue
                }
                val outTris = acc.emittedTriangles
                val outVerts = acc.clusterCount
                acc.writeToGlb(outGlb)
                return MeshStats(
                    originalVertices = totalTris * 3,
                    originalTriangles = totalTris,
                    outputVertices = outVerts,
                    outputTriangles = outTris,
                    wasSimplified = true
                )
            } finally {
                acc.cleanup()
            }
        }
    }

    private inline fun forEachStlTriangle(
        stlFile: File,
        isAscii: Boolean,
        totalTris: Int,
        onTri: (Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit
    ) {
        if (!isAscii) {
            val recBuf = ByteArray(50 * 4096)
            val recBB = ByteBuffer.wrap(recBuf).order(ByteOrder.LITTLE_ENDIAN)
            BufferedInputStream(FileInputStream(stlFile), IO_BUFFER_BYTES).use { bis ->
                skipFully(bis, 84L)
                var remaining = totalTris
                while (remaining > 0) {
                    val batch = minOf(remaining, 4096)
                    readFully(bis, recBuf, batch * 50)
                    for (i in 0 until batch) {
                        val b = i * 50
                        onTri(
                            recBB.getFloat(b), recBB.getFloat(b + 4), recBB.getFloat(b + 8),
                            recBB.getFloat(b + 12), recBB.getFloat(b + 16), recBB.getFloat(b + 20),
                            recBB.getFloat(b + 24), recBB.getFloat(b + 28), recBB.getFloat(b + 32),
                            recBB.getFloat(b + 36), recBB.getFloat(b + 40), recBB.getFloat(b + 44)
                        )
                    }
                    remaining -= batch
                }
            }
        } else {
            var nx = 0f; var ny = 1f; var nz = 0f
            val vx = FloatArray(3); val vy = FloatArray(3); val vz = FloatArray(3)
            var vIdx = 0
            stlFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t = line.trimStart()
                    if (t.startsWith("facet normal", ignoreCase = true)) {
                        parseThreeFloatsAfterToken(t, 12)?.let { (x, y, z) -> nx = x; ny = y; nz = z }
                        vIdx = 0
                    } else if (t.startsWith("vertex", ignoreCase = true)) {
                        parseThreeFloatsAfterToken(t, 6)?.let { (x, y, z) ->
                            if (vIdx < 3) {
                                vx[vIdx] = x; vy[vIdx] = y; vz[vIdx] = z
                                vIdx++
                                if (vIdx == 3) {
                                    onTri(nx, ny, nz, vx[0], vy[0], vz[0], vx[1], vy[1], vz[1], vx[2], vy[2], vz[2])
                                    vIdx = 0
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Streaming PLY converter supporting ASCII, binary_little_endian, and binary_big_endian
     * with arbitrary property layouts and up to 30,000,000 vertices and 30,000,000 polygons.
     */
    fun convertPlyToGlb(cacheDir: File, plyFile: File): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "ply_scratch_${System.nanoTime()}").apply { mkdirs() }
        try {
            val headerInfo = parsePlyHeader(plyFile)
                ?: return ModelFormatConverter.ConversionResult(
                    glbFile = plyFile,
                    originalFormat = "PLY",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "Invalid or unsupported PLY header",
                    isSuccess = false
                )

            if (headerInfo.vertexCount <= 0 || headerInfo.faceCount <= 0) {
                return ModelFormatConverter.ConversionResult(
                    glbFile = plyFile,
                    originalFormat = "PLY",
                    triangleCount = 0,
                    vertexCount = headerInfo.vertexCount,
                    message = "PLY contains point cloud with 0 triangle faces",
                    isSuccess = false
                )
            }

            val positions = PagedFloatTupleStore(3, scratchDir, "ply_pos")
            val normals = if (headerInfo.hasNormals) PagedFloatTupleStore(3, scratchDir, "ply_nrm") else null
            val uvs = if (headerInfo.hasUvs) PagedFloatTupleStore(2, scratchDir, "ply_uv") else null

            try {
                val bounds = BoundingBox()
                val rawTris = streamPlyGeometryAndFaces(
                    plyFile = plyFile,
                    header = headerInfo,
                    positions = positions,
                    normals = normals,
                    uvs = uvs,
                    bounds = bounds,
                    scratchDir = scratchDir
                )
                bounds.normalizeIfEmpty()

                val totalVerts = positions.count
                val totalTris = rawTris.count
                val outGlb = File(cacheDir, "${plyFile.nameWithoutExtension}_converted.glb")

                val stats = emitIndexedDiskTrianglesToGlb(
                    positions = positions,
                    normals = normals,
                    uvs = uvs,
                    triIndexFile = rawTris.file,
                    totalVerts = totalVerts,
                    totalTris = totalTris,
                    bounds = bounds,
                    scratchDir = scratchDir,
                    outGlb = outGlb,
                    generator = "MixedReality Streaming PLY Engine"
                )
                rawTris.close()

                val msg = if (stats.wasSimplified) {
                    "PLY (${formatCompact(stats.originalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles)} tris for mobile GPU"
                } else {
                    "PLY (${formatCompact(stats.outputTriangles)} triangles) loaded"
                }
                return ModelFormatConverter.ConversionResult(
                    glbFile = outGlb,
                    originalFormat = "PLY",
                    triangleCount = stats.outputTriangles,
                    vertexCount = stats.outputVertices,
                    message = msg,
                    isSuccess = true
                )
            } finally {
                positions.close()
                normals?.close()
                uvs?.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PLY conversion failed: ${t.message}", t)
            return ModelFormatConverter.ConversionResult(
                glbFile = plyFile,
                originalFormat = "PLY",
                triangleCount = 0,
                vertexCount = 0,
                message = "PLY conversion error: ${t.message ?: t::class.java.simpleName}",
                isSuccess = false
            )
        } finally {
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    /**
     * Streaming Wavefront OBJ converter using disk-paged vertex pools and adaptive vertex clustering,
     * handling up to 30,000,000+ vertices & 30,000,000+ polygons inside minimal Java heap.
     */
    fun convertObjToGlbStreaming(cacheDir: File, objFile: File): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "obj_scratch_${System.nanoTime()}").apply { mkdirs() }
        val positions = PagedFloatTupleStore(3, scratchDir, "obj_pos")
        val normals = PagedFloatTupleStore(3, scratchDir, "obj_nrm")
        val uvs = PagedFloatTupleStore(2, scratchDir, "obj_uv")
        val triIndexFile = File(scratchDir, "obj_tris.bin")

        val bounds = BoundingBox()
        var triCount = 0
        var parsedMtlNote: String? = null
        var faceV = IntArray(16)
        var faceT = IntArray(16)
        var faceN = IntArray(16)

        try {
            val triOut = BufferedOutputStream(FileOutputStream(triIndexFile), IO_BUFFER_BYTES)
            val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

            BufferedInputStream(FileInputStream(objFile), IO_BUFFER_BYTES).bufferedReader(Charsets.UTF_8).use { reader ->
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
                            val x = if (xEnd > cursor) parseFastFloat(line, cursor, xEnd) else 0f
                            cursor = skipWhitespace(line, xEnd, len)
                            val yEnd = findWhitespace(line, cursor, len)
                            val y = if (yEnd > cursor) parseFastFloat(line, cursor, yEnd) else 0f
                            cursor = skipWhitespace(line, yEnd, len)
                            val zEnd = findWhitespace(line, cursor, len)
                            val z = if (zEnd > cursor) parseFastFloat(line, cursor, zEnd) else 0f
                            if (zEnd > cursor) {
                                positions.add3(x, y, z)
                                bounds.expand(x, y, z)
                            }
                        }
                        cmdLen == 2 && c0 == 'v' && line[cursor + 1] == 'n' -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            val xEnd = findWhitespace(line, cursor, len)
                            val nx = if (xEnd > cursor) parseFastFloat(line, cursor, xEnd) else 0f
                            cursor = skipWhitespace(line, xEnd, len)
                            val yEnd = findWhitespace(line, cursor, len)
                            val ny = if (yEnd > cursor) parseFastFloat(line, cursor, yEnd) else 1f
                            cursor = skipWhitespace(line, yEnd, len)
                            val zEnd = findWhitespace(line, cursor, len)
                            val nz = if (zEnd > cursor) parseFastFloat(line, cursor, zEnd, 1f) else 0f
                            if (zEnd > cursor) normals.add3(nx, ny, nz)
                        }
                        cmdLen == 2 && c0 == 'v' && line[cursor + 1] == 't' -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            val uEnd = findWhitespace(line, cursor, len)
                            val u = if (uEnd > cursor) parseFastFloat(line, cursor, uEnd) else 0f
                            cursor = skipWhitespace(line, uEnd, len)
                            val vEnd = findWhitespace(line, cursor, len)
                            val v = if (vEnd > cursor) parseFastFloat(line, cursor, vEnd) else 0f
                            if (vEnd > cursor) uvs.add2(u, v)
                        }
                        cmdLen == 1 && c0 == 'f' -> {
                            val totalV = positions.count
                            val totalT = uvs.count
                            val totalN = normals.count
                            var faceCount = 0
                            cursor = skipWhitespace(line, cmdEnd, len)
                            while (cursor < len) {
                                val tokEnd = findWhitespace(line, cursor, len)
                                if (tokEnd > cursor) {
                                    if (faceCount == faceV.size) {
                                        val nc = faceV.size * 2
                                        faceV = faceV.copyOf(nc)
                                        faceT = faceT.copyOf(nc)
                                        faceN = faceN.copyOf(nc)
                                    }
                                    parseObjFaceToken(line, cursor, tokEnd, totalV, totalT, totalN, faceV, faceT, faceN, faceCount)
                                    faceCount++
                                }
                                cursor = skipWhitespace(line, tokEnd, len)
                            }
                            if (faceCount >= 3) {
                                val v0 = faceV[0] - 1
                                if (v0 !in 0 until totalV) continue
                                val t0 = faceT[0] - 1
                                val n0 = faceN[0] - 1
                                for (i in 1 until faceCount - 1) {
                                    val v1 = faceV[i] - 1
                                    val v2 = faceV[i + 1] - 1
                                    if (v1 !in 0 until totalV || v2 !in 0 until totalV) continue
                                    if (triBuf.remaining() < 36) flushWriteBuf(triOut, triBuf)
                                    triBuf.putInt(v0); triBuf.putInt(t0); triBuf.putInt(n0)
                                    triBuf.putInt(v1); triBuf.putInt(faceT[i] - 1); triBuf.putInt(faceN[i] - 1)
                                    triBuf.putInt(v2); triBuf.putInt(faceT[i + 1] - 1); triBuf.putInt(faceN[i + 1] - 1)
                                    triCount++
                                }
                            }
                        }
                        cmdLen == 6 && line.startsWith("mtllib", cursor) -> {
                            cursor = skipWhitespace(line, cmdEnd, len)
                            if (cursor < len) {
                                val mtlName = line.substring(cursor, len).trim()
                                val mtlFile = File(objFile.parentFile, mtlName)
                                parsedMtlNote = if (mtlFile.exists()) {
                                    "Material $mtlName"
                                } else {
                                    "Missing MTL $mtlName"
                                }
                            }
                        }
                    }
                }
            }
            flushWriteBuf(triOut, triBuf)
            triOut.close()

            bounds.normalizeIfEmpty()
            val outGlb = File(cacheDir, "${objFile.nameWithoutExtension}_converted.glb")

            if (triCount <= 0 || positions.count <= 0) {
                return ModelFormatConverter.ConversionResult(
                    glbFile = objFile,
                    originalFormat = "OBJ",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "No valid faces found in OBJ file",
                    isSuccess = false
                )
            }

            val stats = emitObjDiskTrianglesToGlb(
                positions = positions,
                normals = normals,
                uvs = uvs,
                triRecordFile = triIndexFile,
                totalVerts = positions.count,
                totalTris = triCount,
                bounds = bounds,
                scratchDir = scratchDir,
                outGlb = outGlb
            )

            val baseMsg = if (stats.wasSimplified) {
                "OBJ (${formatCompact(stats.originalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles)} tris"
            } else {
                "OBJ (${formatCompact(stats.outputTriangles)} tris) loaded"
            }
            val fullMsg = if (parsedMtlNote != null) "$baseMsg ($parsedMtlNote)" else baseMsg

            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "OBJ",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = fullMsg,
                isSuccess = true
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Streaming OBJ error: ${t.message}", t)
            return ModelFormatConverter.ConversionResult(
                glbFile = objFile,
                originalFormat = "OBJ",
                triangleCount = 0,
                vertexCount = 0,
                message = "OBJ parse error: ${t.message ?: t::class.java.simpleName}",
                isSuccess = false
            )
        } finally {
            positions.close()
            normals.close()
            uvs.close()
            runCatching { triIndexFile.delete() }
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    /**
     * Streaming 3MF converter parsing XML directly inside ZipInputStream without whole-file ByteArray loads.
     */
    fun convertThreeMfToGlb(cacheDir: File, threeMfFile: File): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "3mf_scratch_${System.nanoTime()}").apply { mkdirs() }
        val positions = PagedFloatTupleStore(3, scratchDir, "3mf_pos")
        val triIndexFile = File(scratchDir, "3mf_tris.bin")
        val bounds = BoundingBox()
        var triCount = 0

        try {
            val triOut = BufferedOutputStream(FileOutputStream(triIndexFile), IO_BUFFER_BYTES)
            val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            ZipInputStream(BufferedInputStream(FileInputStream(threeMfFile), IO_BUFFER_BYTES)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".model", ignoreCase = true)) {
                        val parser = factory.newPullParser()
                        parser.setInput(zis, "UTF-8")
                        var objectVertexOffset = positions.count
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (parser.name.lowercase(Locale.US)) {
                                    "mesh" -> objectVertexOffset = positions.count
                                    "vertex" -> {
                                        val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                                        val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                                        val z = parser.getAttributeValue(null, "z")?.toFloatOrNull() ?: 0f
                                        positions.add3(x, y, z)
                                        bounds.expand(x, y, z)
                                    }
                                    "triangle" -> {
                                        val v1 = (parser.getAttributeValue(null, "v1")?.toIntOrNull() ?: -1) + objectVertexOffset
                                        val v2 = (parser.getAttributeValue(null, "v2")?.toIntOrNull() ?: -1) + objectVertexOffset
                                        val v3 = (parser.getAttributeValue(null, "v3")?.toIntOrNull() ?: -1) + objectVertexOffset
                                        if (v1 in 0 until positions.count && v2 in 0 until positions.count && v3 in 0 until positions.count) {
                                            if (triBuf.remaining() < 12) flushWriteBuf(triOut, triBuf)
                                            triBuf.putInt(v1)
                                            triBuf.putInt(v2)
                                            triBuf.putInt(v3)
                                            triCount++
                                        }
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            flushWriteBuf(triOut, triBuf)
            triOut.close()
            bounds.normalizeIfEmpty()

            if (triCount <= 0 || positions.count <= 0) {
                return ModelFormatConverter.ConversionResult(
                    glbFile = threeMfFile,
                    originalFormat = "3MF",
                    triangleCount = 0,
                    vertexCount = 0,
                    message = "No 3D mesh geometry found in 3MF archive",
                    isSuccess = false
                )
            }

            val outGlb = File(cacheDir, "${threeMfFile.nameWithoutExtension}_converted.glb")
            val stats = emitIndexedDiskTrianglesToGlb(
                positions = positions,
                normals = null,
                uvs = null,
                triIndexFile = triIndexFile,
                totalVerts = positions.count,
                totalTris = triCount,
                bounds = bounds,
                scratchDir = scratchDir,
                outGlb = outGlb,
                generator = "MixedReality Streaming 3MF Engine"
            )

            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "3MF",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = "3MF (${formatCompact(stats.outputTriangles)} tris) loaded",
                isSuccess = true
            )
        } catch (t: Throwable) {
            Log.e(TAG, "3MF conversion error: ${t.message}", t)
            return ModelFormatConverter.ConversionResult(
                glbFile = threeMfFile,
                originalFormat = "3MF",
                triangleCount = 0,
                vertexCount = 0,
                message = "3MF error: ${t.message ?: t::class.java.simpleName}",
                isSuccess = false
            )
        } finally {
            positions.close()
            runCatching { triIndexFile.delete() }
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    /**
     * Reads only the GLB JSON header chunk (handles even massive >10 MB JSON chunks) to count
     * vertices, triangles, compression extensions, and images without loading the binary chunk.
     */
    fun inspectGlbMetadata(glbFile: File): GlbInspection {
        return runCatching {
            val len = glbFile.length()
            if (len < 20L) {
                return@runCatching GlbInspection(false, 0, 0, 0, 0, false, false, false, false, 0)
            }
            RandomAccessFile(glbFile, "r").use { raf ->
                val header = ByteArray(20)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.int
                val version = bb.int
                bb.int // total length
                val jsonLen = bb.int
                val jsonType = bb.int
                if (magic != 0x46546C67 || version != 2 || jsonType != 0x4E4F534A || jsonLen <= 0) {
                    return@runCatching GlbInspection(false, 0, 0, 0, 0, false, false, false, false, 0)
                }
                val safeJsonLen = jsonLen.toLong().coerceAtMost(minOf(len - 20L, 48L * 1024L * 1024L)).toInt()
                val jsonBytes = ByteArray(safeJsonLen)
                raf.readFully(jsonBytes)
                val jsonStr = String(jsonBytes, Charsets.UTF_8)
                val binByteLength = (len - 20L - jsonLen.toLong() - 8L).coerceAtLeast(0L)

                val root = JSONObject(jsonStr)
                val accessors = root.optJSONArray("accessors") ?: JSONArray()
                val meshes = root.optJSONArray("meshes") ?: JSONArray()

                var vertTotal = 0L
                var triTotal = 0L
                for (m in 0 until meshes.length()) {
                    val prims = meshes.getJSONObject(m).optJSONArray("primitives") ?: continue
                    for (p in 0 until prims.length()) {
                        val prim = prims.getJSONObject(p)
                        val attrs = prim.optJSONObject("attributes")
                        val posAccIdx = attrs?.optInt("POSITION", -1) ?: -1
                        val primVerts = if (posAccIdx in 0 until accessors.length()) {
                            accessors.getJSONObject(posAccIdx).optLong("count", 0L)
                        } else 0L
                        vertTotal += primVerts

                        val mode = prim.optInt("mode", 4)
                        val idxAcc = prim.optInt("indices", -1)
                        val elemCount = if (idxAcc in 0 until accessors.length()) {
                            accessors.getJSONObject(idxAcc).optLong("count", primVerts)
                        } else primVerts

                        triTotal += when (mode) {
                            4 -> elemCount / 3L
                            5, 6 -> (elemCount - 2L).coerceAtLeast(0L)
                            else -> 0L
                        }
                    }
                }

                val hasDraco = jsonStr.contains("KHR_draco_mesh_compression", ignoreCase = true)
                val hasMeshopt = jsonStr.contains("EXT_meshopt_compression", ignoreCase = true)
                val hasWebp = jsonStr.contains("image/webp", ignoreCase = true) ||
                    jsonStr.contains("EXT_texture_webp", ignoreCase = true)
                val images = root.optJSONArray("images")?.length() ?: 0

                GlbInspection(
                    isValidGlb = true,
                    jsonByteLength = jsonLen,
                    binByteLength = binByteLength,
                    vertexCount = vertTotal,
                    triangleCount = triTotal,
                    hasDraco = hasDraco,
                    hasMeshopt = hasMeshopt,
                    hasWebp = hasWebp,
                    hasUncompressedAccessors = !hasDraco && !hasMeshopt,
                    imageCount = images
                )
            }
        }.getOrElse {
            GlbInspection(false, 0, 0, 0, 0, false, false, false, false, 0)
        }
    }

    /**
     * If a GLB contains huge embedded images or fails under memory pressure due to 4K/8K textures,
     * produces a mobile-safe companion GLB with textures downsampled to maxEdge (e.g. 1024px) while
     * leaving compressed/raw mesh geometry intact on disk.
     */
    fun downsampleGlbTexturesForMobile(cacheDir: File, srcGlb: File, maxEdge: Int = 1024): File? {
        return runCatching {
            val len = srcGlb.length()
            if (len < 28L) return@runCatching null
            RandomAccessFile(srcGlb, "r").use { raf ->
                val header = ByteArray(20)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                if (bb.int != 0x46546C67 || bb.int != 2) return@runCatching null
                bb.int
                val jsonLen = bb.int
                val jsonType = bb.int
                if (jsonType != 0x4E4F534A || jsonLen <= 0) return@runCatching null
                val jsonBytes = ByteArray(jsonLen)
                raf.readFully(jsonBytes)

                val binHdr = ByteArray(8)
                raf.readFully(binHdr)
                val binBB = ByteBuffer.wrap(binHdr).order(ByteOrder.LITTLE_ENDIAN)
                val binLen = binBB.int
                val binType = binBB.int
                if (binType != 0x004E4942 || binLen <= 0) return@runCatching null
                val binFileOffset = 20L + jsonLen.toLong() + 8L

                val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
                val images = root.optJSONArray("images") ?: return@runCatching null
                val bufferViews = root.optJSONArray("bufferViews") ?: return@runCatching null
                if (images.length() == 0) return@runCatching null

                val rewrittenBv = HashMap<Int, Pair<ByteArray, String>>()
                for (i in 0 until images.length()) {
                    val img = images.getJSONObject(i)
                    val bvIdx = img.optInt("bufferView", -1)
                    if (bvIdx !in 0 until bufferViews.length()) continue
                    val bv = bufferViews.getJSONObject(bvIdx)
                    val off = bv.optLong("byteOffset", 0L)
                    val bLen = bv.optInt("byteLength", 0)
                    if (bLen <= 0 || bLen > 64 * 1024 * 1024) continue

                    val imgBytes = ByteArray(bLen)
                    raf.seek(binFileOffset + off)
                    raf.readFully(imgBytes)

                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, boundsOpts)
                    val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                    if (maxDim <= maxEdge && !img.optString("mimeType").contains("webp", true)) continue

                    var sample = 1
                    while (maxDim / sample > maxEdge * 2) sample *= 2
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, decodeOpts) ?: continue
                    val outStream = java.io.ByteArrayOutputStream()
                    val format = if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val mime = if (bmp.hasAlpha()) "image/png" else "image/jpeg"
                    bmp.compress(format, 85, outStream)
                    bmp.recycle()
                    rewrittenBv[bvIdx] = outStream.toByteArray() to mime
                    img.put("mimeType", mime)
                }

                if (rewrittenBv.isEmpty()) return@runCatching null

                // Append resized images cleanly at the end of BIN chunk and point their bufferViews there
                var newBinEnd = binLen.toLong()
                for ((bvIdx, pair) in rewrittenBv) {
                    val aligned = ((newBinEnd + 3L) / 4L) * 4L
                    val bv = bufferViews.getJSONObject(bvIdx)
                    bv.put("byteOffset", aligned)
                    bv.put("byteLength", pair.first.size)
                    newBinEnd = aligned + pair.first.size
                }
                val newBinPadding = ((4L - (newBinEnd % 4L)) % 4L).toInt()
                val paddedNewBinLen = newBinEnd + newBinPadding

                val buffers = root.optJSONArray("buffers")
                if (buffers != null && buffers.length() > 0) {
                    buffers.getJSONObject(0).put("byteLength", newBinEnd)
                }

                val newJsonBytes = root.toString().toByteArray(Charsets.UTF_8)
                val newJsonPadding = (4 - (newJsonBytes.size % 4)) % 4
                val paddedNewJsonLen = newJsonBytes.size + newJsonPadding
                val totalLen = (12L + 8L + paddedNewJsonLen + 8L + paddedNewBinLen).toInt()

                val outGlb = File(cacheDir, "${srcGlb.nameWithoutExtension}_mobile_tex.glb")
                val writeBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                BufferedOutputStream(FileOutputStream(outGlb), IO_BUFFER_BYTES).use { bos ->
                    writeGlbHeaders(bos, writeBuf, totalLen, newJsonBytes, paddedNewJsonLen, newJsonPadding, paddedNewBinLen.toInt())

                    // Copy original BIN payload cleanly
                    raf.seek(binFileOffset)
                    val copyBuf = ByteArray(IO_BUFFER_BYTES)
                    var remaining = binLen.toLong()
                    while (remaining > 0L) {
                        val toRead = minOf(remaining, copyBuf.size.toLong()).toInt()
                        val read = raf.read(copyBuf, 0, toRead)
                        if (read <= 0) break
                        bos.write(copyBuf, 0, read)
                        remaining -= read
                    }

                    // Write new downsampled image blobs at aligned offsets
                    var cursorPos = binLen.toLong()
                    for ((_, pair) in rewrittenBv) {
                        val aligned = ((cursorPos + 3L) / 4L) * 4L
                        while (cursorPos < aligned) {
                            bos.write(0)
                            cursorPos++
                        }
                        bos.write(pair.first)
                        cursorPos += pair.first.size
                    }
                    for (p in 0 until newBinPadding) bos.write(0)
                }
                outGlb
            }
        }.getOrNull()
    }

    /**
     * Decimates an UNCOMPRESSED oversized `.glb` (when raw vertices/triangles exceed GPU VRAM bounds)
     * directly on disk into a mobile-safe GLB.
     */
    fun simplifyUncompressedGlb(cacheDir: File, srcGlb: File): File? {
        val info = inspectGlbMetadata(srcGlb)
        if (!info.isValidGlb || !info.hasUncompressedAccessors || info.triangleCount <= DIRECT_TRIANGLE_LIMIT) {
            return null
        }
        val scratchDir = File(cacheDir, "glb_decim_${System.nanoTime()}").apply { mkdirs() }
        return try {
            RandomAccessFile(srcGlb, "r").use { raf ->
                val jsonBytes = ByteArray(info.jsonByteLength)
                raf.seek(20L)
                raf.readFully(jsonBytes)
                val binOffset = 20L + info.jsonByteLength.toLong() + 8L

                val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
                val accessors = root.optJSONArray("accessors") ?: return@use null
                val bufferViews = root.optJSONArray("bufferViews") ?: return@use null
                val meshes = root.optJSONArray("meshes") ?: return@use null

                val positions = PagedFloatTupleStore(3, scratchDir, "glb_pos")
                val triIndexFile = File(scratchDir, "glb_tris.bin")
                val triOut = BufferedOutputStream(FileOutputStream(triIndexFile), IO_BUFFER_BYTES)
                val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                val bounds = BoundingBox()
                var totalTris = 0

                try {
                    for (m in 0 until meshes.length()) {
                        val prims = meshes.getJSONObject(m).optJSONArray("primitives") ?: continue
                        for (p in 0 until prims.length()) {
                            val prim = prims.getJSONObject(p)
                            if (prim.optInt("mode", 4) != 4) continue
                            val attrs = prim.optJSONObject("attributes") ?: continue
                            val posAccIdx = attrs.optInt("POSITION", -1)
                            if (posAccIdx !in 0 until accessors.length()) continue

                            val posAcc = accessors.getJSONObject(posAccIdx)
                            if (posAcc.optInt("componentType") != 5126 || posAcc.optString("type") != "VEC3") continue
                            val posCount = posAcc.optInt("count", 0)
                            val posBvIdx = posAcc.optInt("bufferView", -1)
                            if (posCount <= 0 || posBvIdx !in 0 until bufferViews.length()) continue
                            val posBv = bufferViews.getJSONObject(posBvIdx)
                            val posStride = posBv.optInt("byteStride", 12).coerceAtLeast(12)
                            val posBaseOffset = binOffset + posBv.optLong("byteOffset", 0L) + posAcc.optLong("byteOffset", 0L)

                            val vertStartIdx = positions.count
                            val vReadBuf = ByteArray(posStride)
                            val vReadBB = ByteBuffer.wrap(vReadBuf).order(ByteOrder.LITTLE_ENDIAN)
                            raf.seek(posBaseOffset)
                            for (vi in 0 until posCount) {
                                raf.readFully(vReadBuf)
                                val x = vReadBB.getFloat(0)
                                val y = vReadBB.getFloat(4)
                                val z = vReadBB.getFloat(8)
                                positions.add3(x, y, z)
                                bounds.expand(x, y, z)
                            }

                            val idxAccIdx = prim.optInt("indices", -1)
                            if (idxAccIdx in 0 until accessors.length()) {
                                val idxAcc = accessors.getJSONObject(idxAccIdx)
                                val compType = idxAcc.optInt("componentType")
                                val idxCount = idxAcc.optInt("count", 0)
                                val idxBvIdx = idxAcc.optInt("bufferView", -1)
                                if (idxBvIdx !in 0 until bufferViews.length()) continue
                                val idxBv = bufferViews.getJSONObject(idxBvIdx)
                                val idxOff = binOffset + idxBv.optLong("byteOffset", 0L) + idxAcc.optLong("byteOffset", 0L)
                                raf.seek(idxOff)
                                val elemBytes = when (compType) {
                                    5121 -> 1; 5123 -> 2; 5125 -> 4; else -> 0
                                }
                                if (elemBytes == 0) continue
                                val trisInPrim = idxCount / 3
                                val iReadBuf = ByteArray(elemBytes * 3)
                                val iReadBB = ByteBuffer.wrap(iReadBuf).order(ByteOrder.LITTLE_ENDIAN)
                                for (ti in 0 until trisInPrim) {
                                    raf.readFully(iReadBuf)
                                    val i0: Int; val i1: Int; val i2: Int
                                    when (elemBytes) {
                                        1 -> {
                                            i0 = iReadBuf[0].toInt() and 0xFF
                                            i1 = iReadBuf[1].toInt() and 0xFF
                                            i2 = iReadBuf[2].toInt() and 0xFF
                                        }
                                        2 -> {
                                            i0 = iReadBB.getShort(0).toInt() and 0xFFFF
                                            i1 = iReadBB.getShort(2).toInt() and 0xFFFF
                                            i2 = iReadBB.getShort(4).toInt() and 0xFFFF
                                        }
                                        else -> {
                                            i0 = iReadBB.getInt(0)
                                            i1 = iReadBB.getInt(4)
                                            i2 = iReadBB.getInt(8)
                                        }
                                    }
                                    if (triBuf.remaining() < 12) flushWriteBuf(triOut, triBuf)
                                    triBuf.putInt(vertStartIdx + i0)
                                    triBuf.putInt(vertStartIdx + i1)
                                    triBuf.putInt(vertStartIdx + i2)
                                    totalTris++
                                }
                            } else {
                                val trisInPrim = posCount / 3
                                for (ti in 0 until trisInPrim) {
                                    val b = vertStartIdx + ti * 3
                                    if (triBuf.remaining() < 12) flushWriteBuf(triOut, triBuf)
                                    triBuf.putInt(b); triBuf.putInt(b + 1); triBuf.putInt(b + 2)
                                    totalTris++
                                }
                            }
                        }
                    }

                    flushWriteBuf(triOut, triBuf)
                    triOut.close()
                    bounds.normalizeIfEmpty()
                    if (totalTris <= 0) return@use null

                    val outGlb = File(cacheDir, "${srcGlb.nameWithoutExtension}_optimized.glb")
                    emitIndexedDiskTrianglesToGlb(
                        positions = positions,
                        normals = null,
                        uvs = null,
                        triIndexFile = triIndexFile,
                        totalVerts = positions.count,
                        totalTris = totalTris,
                        bounds = bounds,
                        scratchDir = scratchDir,
                        outGlb = outGlb,
                        generator = "MixedReality GLB Optimizer"
                    )
                    outGlb
                } finally {
                    positions.close()
                    runCatching { triOut.close() }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GLB geometry optimization skipped: ${t.message}")
            null
        } finally {
            runCatching { scratchDir.deleteRecursively() }
        }
    }

    // =========================================================================
    // Internal Indexed Disk Emission + Clustering
    // =========================================================================

    private fun emitIndexedDiskTrianglesToGlb(
        positions: PagedFloatTupleStore,
        normals: PagedFloatTupleStore?,
        uvs: PagedFloatTupleStore?,
        triIndexFile: File,
        totalVerts: Int,
        totalTris: Int,
        bounds: BoundingBox,
        scratchDir: File,
        outGlb: File,
        generator: String
    ): MeshStats {
        val needsCluster = totalVerts > DIRECT_TRIANGLE_LIMIT || totalTris > DIRECT_TRIANGLE_LIMIT
        val readBuf = ByteArray(12 * 4096)
        val readBB = ByteBuffer.wrap(readBuf).order(ByteOrder.LITTLE_ENDIAN)

        if (!needsCluster) {
            // Even in lossless mode, synthesize missing flat normals per corner if normals == null
            DirectStreamingGlbWriter(scratchDir).use { writer ->
                if (normals != null && normals.count == positions.count) {
                    for (v in 0 until positions.count) {
                        val u = if (uvs != null && v < uvs.count) uvs.getU(v) else 0f
                        val tv = if (uvs != null && v < uvs.count) uvs.getV(v) else 0f
                        writer.emitVertex(
                            positions.getX(v), positions.getY(v), positions.getZ(v),
                            normals.getX(v), normals.getY(v), normals.getZ(v),
                            u, tv
                        )
                    }
                    BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val n = bis.read(readBuf)
                            if (n <= 0) break
                            var off = 0
                            while (off + 12 <= n) {
                                val i0 = readBB.getInt(off)
                                val i1 = readBB.getInt(off + 4)
                                val i2 = readBB.getInt(off + 8)
                                if (i0 in 0 until totalVerts && i1 in 0 until totalVerts && i2 in 0 until totalVerts) {
                                    writer.emitTriangle(i0, i1, i2)
                                }
                                off += 12
                            }
                        }
                    }
                } else {
                    BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val n = bis.read(readBuf)
                            if (n <= 0) break
                            var off = 0
                            while (off + 12 <= n) {
                                val i0 = readBB.getInt(off)
                                val i1 = readBB.getInt(off + 4)
                                val i2 = readBB.getInt(off + 8)
                                if (i0 in 0 until totalVerts && i1 in 0 until totalVerts && i2 in 0 until totalVerts) {
                                    val x0 = positions.getX(i0); val y0 = positions.getY(i0); val z0 = positions.getZ(i0)
                                    val x1 = positions.getX(i1); val y1 = positions.getY(i1); val z1 = positions.getZ(i1)
                                    val x2 = positions.getX(i2); val y2 = positions.getY(i2); val z2 = positions.getZ(i2)
                                    val (nx, ny, nz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
                                    val u0 = if (uvs != null && i0 < uvs.count) uvs.getU(i0) else 0f
                                    val v0 = if (uvs != null && i0 < uvs.count) uvs.getV(i0) else 0f
                                    val u1 = if (uvs != null && i1 < uvs.count) uvs.getU(i1) else 0f
                                    val v1 = if (uvs != null && i1 < uvs.count) uvs.getV(i1) else 0f
                                    val u2 = if (uvs != null && i2 < uvs.count) uvs.getU(i2) else 0f
                                    val v2 = if (uvs != null && i2 < uvs.count) uvs.getV(i2) else 0f
                                    val o0 = writer.emitVertex(x0, y0, z0, nx, ny, nz, u0, v0)
                                    val o1 = writer.emitVertex(x1, y1, z1, nx, ny, nz, u1, v1)
                                    val o2 = writer.emitVertex(x2, y2, z2, nx, ny, nz, u2, v2)
                                    writer.emitTriangle(o0, o1, o2)
                                }
                                off += 12
                            }
                        }
                    }
                }
                writer.finalizeToGlb(outGlb, generator)
                return MeshStats(totalVerts, totalTris, writer.vertexCount, writer.indexCount / 3, false)
            }
        }

        // Spatial Clustering Pass for >1.8M up to 30M+ vertices/triangles
        var gridRes = estimateInitialGridResolution(totalTris)
        while (true) {
            val acc = SpatialClusterAccumulator(bounds, gridRes)
            acc.startTriangleStream(scratchDir)
            try {
                BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                    while (!acc.overflowed) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + 12 <= n && !acc.overflowed) {
                            val i0 = readBB.getInt(off)
                            val i1 = readBB.getInt(off + 4)
                            val i2 = readBB.getInt(off + 8)
                            off += 12
                            if (i0 !in 0 until totalVerts || i1 !in 0 until totalVerts || i2 !in 0 until totalVerts) continue
                            val x0 = positions.getX(i0); val y0 = positions.getY(i0); val z0 = positions.getZ(i0)
                            val x1 = positions.getX(i1); val y1 = positions.getY(i1); val z1 = positions.getZ(i1)
                            val x2 = positions.getX(i2); val y2 = positions.getY(i2); val z2 = positions.getZ(i2)
                            val (fnx, fny, fnz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
                            val hasPerVertNorm = normals != null && normals.count == positions.count
                            val c0 = acc.getOrAssignCluster(
                                x0, y0, z0,
                                if (hasPerVertNorm) normals!!.getX(i0) else fnx,
                                if (hasPerVertNorm) normals!!.getY(i0) else fny,
                                if (hasPerVertNorm) normals!!.getZ(i0) else fnz,
                                if (uvs != null && i0 < uvs.count) uvs.getU(i0) else 0f,
                                if (uvs != null && i0 < uvs.count) uvs.getV(i0) else 0f
                            )
                            val c1 = acc.getOrAssignCluster(
                                x1, y1, z1,
                                if (hasPerVertNorm) normals!!.getX(i1) else fnx,
                                if (hasPerVertNorm) normals!!.getY(i1) else fny,
                                if (hasPerVertNorm) normals!!.getZ(i1) else fnz,
                                if (uvs != null && i1 < uvs.count) uvs.getU(i1) else 0f,
                                if (uvs != null && i1 < uvs.count) uvs.getV(i1) else 0f
                            )
                            val c2 = acc.getOrAssignCluster(
                                x2, y2, z2,
                                if (hasPerVertNorm) normals!!.getX(i2) else fnx,
                                if (hasPerVertNorm) normals!!.getY(i2) else fny,
                                if (hasPerVertNorm) normals!!.getZ(i2) else fnz,
                                if (uvs != null && i2 < uvs.count) uvs.getU(i2) else 0f,
                                if (uvs != null && i2 < uvs.count) uvs.getV(i2) else 0f
                            )
                            acc.addTriangle(c0, c1, c2)
                        }
                    }
                }
                if (acc.overflowed && gridRes > 96) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(96)
                    continue
                }
                val outVerts = acc.clusterCount
                val outTris = acc.emittedTriangles
                acc.writeToGlb(outGlb)
                return MeshStats(totalVerts, totalTris, outVerts, outTris, true)
            } finally {
                acc.cleanup()
            }
        }
    }

    private fun emitObjDiskTrianglesToGlb(
        positions: PagedFloatTupleStore,
        normals: PagedFloatTupleStore,
        uvs: PagedFloatTupleStore,
        triRecordFile: File,
        totalVerts: Int,
        totalTris: Int,
        bounds: BoundingBox,
        scratchDir: File,
        outGlb: File
    ): MeshStats {
        val totalN = normals.count
        val totalT = uvs.count
        val readBuf = ByteArray(36 * 2048) // 2048 triangles per chunk
        val readBB = ByteBuffer.wrap(readBuf).order(ByteOrder.LITTLE_ENDIAN)

        if (totalVerts <= DIRECT_TRIANGLE_LIMIT && totalTris <= DIRECT_TRIANGLE_LIMIT) {
            DirectStreamingGlbWriter(scratchDir).use { writer ->
                BufferedInputStream(FileInputStream(triRecordFile), IO_BUFFER_BYTES).use { bis ->
                    while (true) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + 36 <= n) {
                            val v0 = readBB.getInt(off); val t0 = readBB.getInt(off + 4); val n0 = readBB.getInt(off + 8)
                            val v1 = readBB.getInt(off + 12); val t1 = readBB.getInt(off + 16); val n1 = readBB.getInt(off + 20)
                            val v2 = readBB.getInt(off + 24); val t2 = readBB.getInt(off + 28); val n2 = readBB.getInt(off + 32)
                            off += 36
                            if (v0 !in 0 until totalVerts || v1 !in 0 until totalVerts || v2 !in 0 until totalVerts) continue

                            val x0 = positions.getX(v0); val y0 = positions.getY(v0); val z0 = positions.getZ(v0)
                            val x1 = positions.getX(v1); val y1 = positions.getY(v1); val z1 = positions.getZ(v1)
                            val x2 = positions.getX(v2); val y2 = positions.getY(v2); val z2 = positions.getZ(v2)
                            val (fnx, fny, fnz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)

                            val o0 = writer.emitVertex(
                                x0, y0, z0,
                                if (n0 in 0 until totalN) normals.getX(n0) else fnx,
                                if (n0 in 0 until totalN) normals.getY(n0) else fny,
                                if (n0 in 0 until totalN) normals.getZ(n0) else fnz,
                                if (t0 in 0 until totalT) uvs.getU(t0) else 0f,
                                if (t0 in 0 until totalT) uvs.getV(t0) else 0f
                            )
                            val o1 = writer.emitVertex(
                                x1, y1, z1,
                                if (n1 in 0 until totalN) normals.getX(n1) else fnx,
                                if (n1 in 0 until totalN) normals.getY(n1) else fny,
                                if (n1 in 0 until totalN) normals.getZ(n1) else fnz,
                                if (t1 in 0 until totalT) uvs.getU(t1) else 0f,
                                if (t1 in 0 until totalT) uvs.getV(t1) else 0f
                            )
                            val o2 = writer.emitVertex(
                                x2, y2, z2,
                                if (n2 in 0 until totalN) normals.getX(n2) else fnx,
                                if (n2 in 0 until totalN) normals.getY(n2) else fny,
                                if (n2 in 0 until totalN) normals.getZ(n2) else fnz,
                                if (t2 in 0 until totalT) uvs.getU(t2) else 0f,
                                if (t2 in 0 until totalT) uvs.getV(t2) else 0f
                            )
                            writer.emitTriangle(o0, o1, o2)
                        }
                    }
                }
                writer.finalizeToGlb(outGlb, "MixedReality Streaming OBJ Engine")
                return MeshStats(totalVerts, totalTris, writer.vertexCount, writer.indexCount / 3, false)
            }
        }

        // Spatial Clustering Pass for massive OBJ (up to 30M verts / 30M tris)
        var gridRes = estimateInitialGridResolution(totalTris)
        while (true) {
            val acc = SpatialClusterAccumulator(bounds, gridRes)
            acc.startTriangleStream(scratchDir)
            try {
                BufferedInputStream(FileInputStream(triRecordFile), IO_BUFFER_BYTES).use { bis ->
                    while (!acc.overflowed) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + 36 <= n && !acc.overflowed) {
                            val v0 = readBB.getInt(off); val t0 = readBB.getInt(off + 4); val n0 = readBB.getInt(off + 8)
                            val v1 = readBB.getInt(off + 12); val t1 = readBB.getInt(off + 16); val n1 = readBB.getInt(off + 20)
                            val v2 = readBB.getInt(off + 24); val t2 = readBB.getInt(off + 28); val n2 = readBB.getInt(off + 32)
                            off += 36
                            if (v0 !in 0 until totalVerts || v1 !in 0 until totalVerts || v2 !in 0 until totalVerts) continue

                            val x0 = positions.getX(v0); val y0 = positions.getY(v0); val z0 = positions.getZ(v0)
                            val x1 = positions.getX(v1); val y1 = positions.getY(v1); val z1 = positions.getZ(v1)
                            val x2 = positions.getX(v2); val y2 = positions.getY(v2); val z2 = positions.getZ(v2)
                            val (fnx, fny, fnz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)

                            val c0 = acc.getOrAssignCluster(
                                x0, y0, z0,
                                if (n0 in 0 until totalN) normals.getX(n0) else fnx,
                                if (n0 in 0 until totalN) normals.getY(n0) else fny,
                                if (n0 in 0 until totalN) normals.getZ(n0) else fnz,
                                if (t0 in 0 until totalT) uvs.getU(t0) else 0f,
                                if (t0 in 0 until totalT) uvs.getV(t0) else 0f
                            )
                            val c1 = acc.getOrAssignCluster(
                                x1, y1, z1,
                                if (n1 in 0 until totalN) normals.getX(n1) else fnx,
                                if (n1 in 0 until totalN) normals.getY(n1) else fny,
                                if (n1 in 0 until totalN) normals.getZ(n1) else fnz,
                                if (t1 in 0 until totalT) uvs.getU(t1) else 0f,
                                if (t1 in 0 until totalT) uvs.getV(t1) else 0f
                            )
                            val c2 = acc.getOrAssignCluster(
                                x2, y2, z2,
                                if (n2 in 0 until totalN) normals.getX(n2) else fnx,
                                if (n2 in 0 until totalN) normals.getY(n2) else fny,
                                if (n2 in 0 until totalN) normals.getZ(n2) else fnz,
                                if (t2 in 0 until totalT) uvs.getU(t2) else 0f,
                                if (t2 in 0 until totalT) uvs.getV(t2) else 0f
                            )
                            acc.addTriangle(c0, c1, c2)
                        }
                    }
                }
                if (acc.overflowed && gridRes > 96) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(96)
                    continue
                }
                val outVerts = acc.clusterCount
                val outTris = acc.emittedTriangles
                acc.writeToGlb(outGlb)
                return MeshStats(totalVerts, totalTris, outVerts, outTris, true)
            } finally {
                acc.cleanup()
            }
        }
    }

    // =========================================================================
    // PLY Parsing Infrastructure
    // =========================================================================

    private enum class PlyFormat { ASCII, BINARY_LE, BINARY_BE }
    private enum class PlyPropType(val byteSize: Int) {
        CHAR(1), UCHAR(1), SHORT(2), USHORT(2), INT(4), UINT(4), FLOAT(4), DOUBLE(8)
    }

    private data class PlyVertexProp(val name: String, val type: PlyPropType, val byteOffset: Int)
    private data class PlyHeader(
        val format: PlyFormat,
        val headerByteLength: Long,
        val vertexCount: Int,
        val faceCount: Int,
        val vertexStride: Int,
        val xIdx: Int, val yIdx: Int, val zIdx: Int,
        val nxIdx: Int, val nyIdx: Int, val nzIdx: Int,
        val uIdx: Int, val vIdx: Int,
        val props: List<PlyVertexProp>,
        val faceListCountType: PlyPropType,
        val faceListIndexType: PlyPropType
    ) {
        val hasNormals: Boolean get() = nxIdx >= 0 && nyIdx >= 0 && nzIdx >= 0
        val hasUvs: Boolean get() = uIdx >= 0 && vIdx >= 0
    }

    private data class DiskTriStream(val file: File, val count: Int) {
        fun close() { runCatching { file.delete() } }
    }

    private fun parsePlyHeader(file: File): PlyHeader? {
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16_384)
            var len = 0
            while (len < buf.size) {
                val b = fis.read()
                if (b < 0) break
                buf[len++] = b.toByte()
                if (len >= 10) {
                    val tail = String(buf, max(0, len - 16), min(len, 16), Charsets.US_ASCII)
                    if (tail.endsWith("end_header\n") || tail.endsWith("end_header\r\n")) break
                }
            }
            val headerStr = String(buf, 0, len, Charsets.US_ASCII)
            val lines = headerStr.lineSequence().map { it.trim() }.toList()
            if (lines.isEmpty() || !lines[0].equals("ply", ignoreCase = true)) return null

            var format = PlyFormat.ASCII
            var currentElement = ""
            var vertCount = 0
            var faceCount = 0
            val props = mutableListOf<PlyVertexProp>()
            var stride = 0
            var faceCountType = PlyPropType.UCHAR
            var faceIdxType = PlyPropType.INT

            for (line in lines) {
                if (line.isEmpty() || line.startsWith("comment") || line.startsWith("obj_info")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "format" -> {
                        if (parts.size >= 2) {
                            format = when (parts[1]) {
                                "ascii" -> PlyFormat.ASCII
                                "binary_little_endian" -> PlyFormat.BINARY_LE
                                "binary_big_endian" -> PlyFormat.BINARY_BE
                                else -> return null
                            }
                        }
                    }
                    "element" -> {
                        if (parts.size >= 3) {
                            currentElement = parts[1]
                            val c = parts[2].toIntOrNull() ?: 0
                            when (currentElement) {
                                "vertex" -> vertCount = c
                                "face" -> faceCount = c
                            }
                        }
                    }
                    "property" -> {
                        if (currentElement == "vertex" && parts.size >= 3 && parts[1] != "list") {
                            val t = parsePlyType(parts[1]) ?: return null
                            props.add(PlyVertexProp(parts.last().lowercase(Locale.US), t, stride))
                            stride += t.byteSize
                        } else if (currentElement == "face" && parts.size >= 5 && parts[1] == "list") {
                            faceCountType = parsePlyType(parts[2]) ?: PlyPropType.UCHAR
                            faceIdxType = parsePlyType(parts[3]) ?: PlyPropType.INT
                        }
                    }
                    "end_header" -> break
                }
            }

            fun findProp(vararg names: String): Int =
                props.indexOfFirst { p -> names.any { p.name == it } }

            val xIdx = findProp("x"); val yIdx = findProp("y"); val zIdx = findProp("z")
            if (xIdx < 0 || yIdx < 0 || zIdx < 0) return null
            val nxIdx = findProp("nx"); val nyIdx = findProp("ny"); val nzIdx = findProp("nz")
            val uIdx = findProp("s", "u", "texture_u"); val vIdx = findProp("t", "v", "texture_v")

            return PlyHeader(
                format = format,
                headerByteLength = len.toLong(),
                vertexCount = vertCount,
                faceCount = faceCount,
                vertexStride = stride,
                xIdx = xIdx, yIdx = yIdx, zIdx = zIdx,
                nxIdx = nxIdx, nyIdx = nyIdx, nzIdx = nzIdx,
                uIdx = uIdx, vIdx = vIdx,
                props = props,
                faceListCountType = faceCountType,
                faceListIndexType = faceIdxType
            )
        }
    }

    private fun parsePlyType(tok: String): PlyPropType? = when (tok.lowercase(Locale.US)) {
        "char", "int8" -> PlyPropType.CHAR
        "uchar", "uint8" -> PlyPropType.UCHAR
        "short", "int16" -> PlyPropType.SHORT
        "ushort", "uint16" -> PlyPropType.USHORT
        "int", "int32" -> PlyPropType.INT
        "uint", "uint32" -> PlyPropType.UINT
        "float", "float32" -> PlyPropType.FLOAT
        "double", "float64" -> PlyPropType.DOUBLE
        else -> null
    }

    private fun streamPlyGeometryAndFaces(
        plyFile: File,
        header: PlyHeader,
        positions: PagedFloatTupleStore,
        normals: PagedFloatTupleStore?,
        uvs: PagedFloatTupleStore?,
        bounds: BoundingBox,
        scratchDir: File
    ): DiskTriStream {
        val triFile = File(scratchDir, "ply_tris.bin")
        var triCount = 0
        val triOut = BufferedOutputStream(FileOutputStream(triFile), IO_BUFFER_BYTES)
        val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

        BufferedInputStream(FileInputStream(plyFile), IO_BUFFER_BYTES).use { bis ->
            skipFully(bis, header.headerByteLength)
            if (header.format == PlyFormat.ASCII) {
                val reader = bis.bufferedReader(Charsets.UTF_8)
                for (vi in 0 until header.vertexCount) {
                    val line = reader.readLine() ?: break
                    val toks = line.trim().split(Regex("\\s+"))
                    if (toks.size <= max(header.xIdx, max(header.yIdx, header.zIdx))) continue
                    val x = toks[header.xIdx].toFloatOrNull() ?: 0f
                    val y = toks[header.yIdx].toFloatOrNull() ?: 0f
                    val z = toks[header.zIdx].toFloatOrNull() ?: 0f
                    positions.add3(x, y, z)
                    bounds.expand(x, y, z)
                    if (normals != null) {
                        val nx = toks.getOrNull(header.nxIdx)?.toFloatOrNull() ?: 0f
                        val ny = toks.getOrNull(header.nyIdx)?.toFloatOrNull() ?: 1f
                        val nz = toks.getOrNull(header.nzIdx)?.toFloatOrNull() ?: 0f
                        normals.add3(nx, ny, nz)
                    }
                    if (uvs != null) {
                        val u = toks.getOrNull(header.uIdx)?.toFloatOrNull() ?: 0f
                        val v = toks.getOrNull(header.vIdx)?.toFloatOrNull() ?: 0f
                        uvs.add2(u, v)
                    }
                }
                val faceVerts = IntArray(32)
                for (fi in 0 until header.faceCount) {
                    val line = reader.readLine() ?: break
                    val toks = line.trim().split(Regex("\\s+"))
                    if (toks.isEmpty()) continue
                    val nVerts = toks[0].toIntOrNull() ?: continue
                    if (nVerts < 3 || toks.size < 1 + nVerts) continue
                    val polyLen = minOf(nVerts, faceVerts.size)
                    for (k in 0 until polyLen) faceVerts[k] = toks[1 + k].toIntOrNull() ?: 0
                    for (i in 1 until polyLen - 1) {
                        if (triBuf.remaining() < 12) flushWriteBuf(triOut, triBuf)
                        triBuf.putInt(faceVerts[0]); triBuf.putInt(faceVerts[i]); triBuf.putInt(faceVerts[i + 1])
                        triCount++
                    }
                }
            } else {
                val order = if (header.format == PlyFormat.BINARY_LE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                val vBuf = ByteArray(header.vertexStride)
                val vBB = ByteBuffer.wrap(vBuf).order(order)
                val xP = header.props[header.xIdx]; val yP = header.props[header.yIdx]; val zP = header.props[header.zIdx]
                val nxP = if (header.hasNormals) header.props[header.nxIdx] else null
                val nyP = if (header.hasNormals) header.props[header.nyIdx] else null
                val nzP = if (header.hasNormals) header.props[header.nzIdx] else null
                val uP = if (header.hasUvs) header.props[header.uIdx] else null
                val vP = if (header.hasUvs) header.props[header.vIdx] else null

                for (vi in 0 until header.vertexCount) {
                    readFully(bis, vBuf, header.vertexStride)
                    val x = readPlyFloat(vBB, xP)
                    val y = readPlyFloat(vBB, yP)
                    val z = readPlyFloat(vBB, zP)
                    positions.add3(x, y, z)
                    bounds.expand(x, y, z)
                    if (normals != null && nxP != null && nyP != null && nzP != null) {
                        normals.add3(readPlyFloat(vBB, nxP), readPlyFloat(vBB, nyP), readPlyFloat(vBB, nzP))
                    }
                    if (uvs != null && uP != null && vP != null) {
                        uvs.add2(readPlyFloat(vBB, uP), readPlyFloat(vBB, vP))
                    }
                }

                val dis = DataInputStream(bis)
                val scratch = ByteArray(8)
                val sBB = ByteBuffer.wrap(scratch).order(order)
                var poly = IntArray(16)
                for (fi in 0 until header.faceCount) {
                    val cnt = readPlyIntScalar(dis, header.faceListCountType, scratch, sBB)
                    if (cnt < 0 || cnt > 256) break
                    if (cnt > poly.size) poly = IntArray(cnt)
                    for (k in 0 until cnt) {
                        poly[k] = readPlyIntScalar(dis, header.faceListIndexType, scratch, sBB)
                    }
                    for (i in 1 until cnt - 1) {
                        if (triBuf.remaining() < 12) flushWriteBuf(triOut, triBuf)
                        triBuf.putInt(poly[0]); triBuf.putInt(poly[i]); triBuf.putInt(poly[i + 1])
                        triCount++
                    }
                }
            }
        }
        flushWriteBuf(triOut, triBuf)
        triOut.close()
        return DiskTriStream(triFile, triCount)
    }

    private fun readPlyFloat(bb: ByteBuffer, prop: PlyVertexProp): Float = when (prop.type) {
        PlyPropType.FLOAT -> bb.getFloat(prop.byteOffset)
        PlyPropType.DOUBLE -> bb.getDouble(prop.byteOffset).toFloat()
        PlyPropType.INT -> bb.getInt(prop.byteOffset).toFloat()
        PlyPropType.UINT -> (bb.getInt(prop.byteOffset).toLong() and 0xFFFF_FFFFL).toFloat()
        PlyPropType.SHORT -> bb.getShort(prop.byteOffset).toFloat()
        PlyPropType.USHORT -> (bb.getShort(prop.byteOffset).toInt() and 0xFFFF).toFloat()
        PlyPropType.CHAR -> bb.get(prop.byteOffset).toFloat()
        PlyPropType.UCHAR -> (bb.get(prop.byteOffset).toInt() and 0xFF).toFloat()
    }

    private fun readPlyIntScalar(dis: DataInputStream, type: PlyPropType, buf: ByteArray, bb: ByteBuffer): Int {
        dis.readFully(buf, 0, type.byteSize)
        return when (type) {
            PlyPropType.CHAR -> buf[0].toInt()
            PlyPropType.UCHAR -> buf[0].toInt() and 0xFF
            PlyPropType.SHORT -> bb.getShort(0).toInt()
            PlyPropType.USHORT -> bb.getShort(0).toInt() and 0xFFFF
            PlyPropType.INT, PlyPropType.UINT -> bb.getInt(0)
            PlyPropType.FLOAT -> bb.getFloat(0).toInt()
            PlyPropType.DOUBLE -> bb.getDouble(0).toInt()
        }
    }

    // =========================================================================
    // Low-Level Streaming Helpers
    // =========================================================================

    private fun estimateInitialGridResolution(totalTris: Int): Int = when {
        totalTris >= 20_000_000 -> 640
        totalTris >= 10_000_000 -> 740
        totalTris >= 5_000_000 -> 860
        else -> 960
    }

    private fun formatCompact(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.0fK", n / 1_000.0)
        else -> n.toString()
    }

    private fun buildStandardGlbJson(
        vertCount: Int,
        indexCount: Int,
        useIntIndices: Boolean,
        posByteLength: Long,
        normByteLength: Long,
        uvByteLength: Long,
        indexByteLength: Long,
        totalBinaryLength: Long,
        bounds: BoundingBox,
        generator: String
    ): ByteArray {
        val json = """
        {
          "asset": {
            "version": "2.0",
            "generator": "$generator"
          },
          "scene": 0,
          "scenes": [{ "nodes": [0] }],
          "nodes": [{ "mesh": 0 }],
          "meshes": [
            {
              "primitives": [
                {
                  "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2 },
                  "indices": 3,
                  "mode": 4
                }
              ]
            }
          ],
          "accessors": [
            {
              "bufferView": 0, "byteOffset": 0, "componentType": 5126,
              "count": $vertCount, "type": "VEC3",
              "max": [${bounds.maxX}, ${bounds.maxY}, ${bounds.maxZ}],
              "min": [${bounds.minX}, ${bounds.minY}, ${bounds.minZ}]
            },
            {
              "bufferView": 1, "byteOffset": 0, "componentType": 5126,
              "count": $vertCount, "type": "VEC3"
            },
            {
              "bufferView": 2, "byteOffset": 0, "componentType": 5126,
              "count": $vertCount, "type": "VEC2"
            },
            {
              "bufferView": 3, "byteOffset": 0,
              "componentType": ${if (useIntIndices) 5125 else 5123},
              "count": $indexCount, "type": "SCALAR"
            }
          ],
          "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": $posByteLength, "target": 34962 },
            { "buffer": 0, "byteOffset": $posByteLength, "byteLength": $normByteLength, "target": 34962 },
            { "buffer": 0, "byteOffset": ${posByteLength + normByteLength}, "byteLength": $uvByteLength, "target": 34962 },
            { "buffer": 0, "byteOffset": ${posByteLength + normByteLength + uvByteLength}, "byteLength": $indexByteLength, "target": 34963 }
          ],
          "buffers": [{ "byteLength": $totalBinaryLength }]
        }
        """.trimIndent()
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun writeGlbHeaders(
        bos: BufferedOutputStream,
        buf: ByteBuffer,
        totalGlbLen: Int,
        jsonBytes: ByteArray,
        paddedJsonLen: Int,
        jsonPadding: Int,
        paddedBinLen: Int
    ) {
        buf.clear()
        buf.putInt(0x46546C67) // "glTF"
        buf.putInt(2)
        buf.putInt(totalGlbLen)
        buf.putInt(paddedJsonLen)
        buf.putInt(0x4E4F534A) // "JSON"
        bos.write(buf.array(), 0, buf.position())
        bos.write(jsonBytes)
        for (p in 0 until jsonPadding) bos.write(0x20)

        buf.clear()
        buf.putInt(paddedBinLen)
        buf.putInt(0x004E4942) // "BIN\0"
        bos.write(buf.array(), 0, buf.position())
        buf.clear()
    }

    private fun putFloat3Checked(out: BufferedOutputStream, buf: ByteBuffer, x: Float, y: Float, z: Float) {
        if (buf.remaining() < 12) flushWriteBuf(out, buf)
        buf.putFloat(x)
        buf.putFloat(y)
        buf.putFloat(z)
    }

    private fun putFloat2Checked(out: BufferedOutputStream, buf: ByteBuffer, u: Float, v: Float) {
        if (buf.remaining() < 8) flushWriteBuf(out, buf)
        buf.putFloat(u)
        buf.putFloat(v)
    }

    private fun flushWriteBuf(out: BufferedOutputStream, buf: ByteBuffer) {
        val pos = buf.position()
        if (pos > 0) {
            out.write(buf.array(), 0, pos)
            buf.clear()
        }
    }

    private fun computeFaceNormal(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Triple<Float, Float, Float> {
        val ax = x1 - x0; val ay = y1 - y0; val az = z1 - z0
        val bx = x2 - x0; val by = y2 - y0; val bz = z2 - z0
        val cx = ay * bz - az * by
        val cy = az * bx - ax * bz
        val cz = ax * by - ay * bx
        val nLen = sqrt(cx * cx + cy * cy + cz * cz)
        return if (nLen > 1e-6f && nLen.isFinite()) {
            val inv = 1f / nLen
            Triple(cx * inv, cy * inv, cz * inv)
        } else {
            Triple(0f, 1f, 0f)
        }
    }

    private fun ensureNormal(
        nx: Float, ny: Float, nz: Float,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Triple<Float, Float, Float> {
        val lenSq = nx * nx + ny * ny + nz * nz
        if (lenSq.isFinite() && lenSq > 1e-5f) {
            val inv = 1f / sqrt(lenSq)
            return Triple(nx * inv, ny * inv, nz * inv)
        }
        return computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
    }

    private fun isAsciiStl(header: ByteArray, fileLength: Long): Boolean {
        if (header.size < 6) return false
        val str = String(header, Charsets.US_ASCII)
        if (!str.trimStart().startsWith("solid", ignoreCase = true)) return false
        // Binary STL files sometimes start their 80-byte text header with "solid "; verify size mismatch
        if (fileLength >= 84L && header.size >= 84) {
            val count = ByteBuffer.wrap(header, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL
            if (84L + count * 50L == fileLength) return false
        }
        return str.contains("facet", ignoreCase = true) || str.contains("vertex", ignoreCase = true)
    }

    private fun parseThreeFloatsAfterToken(line: String, startAt: Int): Triple<Float, Float, Float>? {
        val len = line.length
        var c = skipWhitespace(line, startAt, len)
        val e1 = findWhitespace(line, c, len); if (e1 <= c) return null
        val x = parseFastFloat(line, c, e1)
        c = skipWhitespace(line, e1, len)
        val e2 = findWhitespace(line, c, len); if (e2 <= c) return null
        val y = parseFastFloat(line, c, e2)
        c = skipWhitespace(line, e2, len)
        val e3 = findWhitespace(line, c, len); if (e3 <= c) return null
        val z = parseFastFloat(line, c, e3)
        return Triple(x, y, z)
    }

    private fun skipFully(bis: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = bis.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (bis.read() < 0) throw EOFException("Unexpected EOF while skipping $bytesToSkip bytes")
                remaining--
            }
        }
    }

    private fun readFully(bis: InputStream, dst: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val n = bis.read(dst, off, len - off)
            if (n < 0) throw EOFException("Unexpected EOF reading $len bytes")
            off += n
        }
    }

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
        while (i < end && s[i] in '0'..'9') {
            intPart = intPart * 10.0 + (s[i] - '0')
            hasDigits = true
            i++
        }
        if (i < end && s[i] == '.') {
            i++
            while (i < end && s[i] in '0'..'9') {
                fracPart = fracPart * 10.0 + (s[i] - '0')
                fracDiv *= 10.0
                hasDigits = true
                i++
            }
        }
        if (i < end || !hasDigits) {
            val fb = s.substring(start, end).toFloatOrNull()
            return if (fb != null && fb.isFinite()) fb else defaultVal
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
        line: String, start: Int, end: Int,
        totalV: Int, totalT: Int, totalN: Int,
        outV: IntArray, outT: IntArray, outN: IntArray, slot: Int
    ) {
        val slash1 = line.indexOf('/', start)
        val rawV: Int; val rawT: Int; val rawN: Int
        if (slash1 < 0 || slash1 >= end) {
            rawV = parseFastInt(line, start, end, 1); rawT = 0; rawN = 0
        } else {
            rawV = parseFastInt(line, start, slash1, 1)
            val slash2 = line.indexOf('/', slash1 + 1)
            if (slash2 < 0 || slash2 >= end) {
                rawT = parseFastInt(line, slash1 + 1, end, 0); rawN = 0
            } else {
                rawT = if (slash2 > slash1 + 1) parseFastInt(line, slash1 + 1, slash2, 0) else 0
                rawN = if (end > slash2 + 1) parseFastInt(line, slash2 + 1, end, 0) else 0
            }
        }
        outV[slot] = if (rawV < 0) (totalV + 1 + rawV).coerceAtLeast(1) else rawV
        outT[slot] = if (rawT < 0) (totalT + 1 + rawT).coerceAtLeast(0) else rawT
        outN[slot] = if (rawN < 0) (totalN + 1 + rawN).coerceAtLeast(0) else rawN
    }
}
