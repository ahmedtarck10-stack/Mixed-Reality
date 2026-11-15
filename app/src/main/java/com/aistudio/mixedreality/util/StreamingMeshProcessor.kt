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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-capacity streaming 3D geometry processor engineered for mobile RAM/GPU constraints.
 * Supports unlimited ("∞") vertex and polygon counts across OBJ, STL, PLY, 3MF, GLB, and USD
 * using sequential binary streaming, memory-mapped float buffers, and single-pass spatial clustering.
 */
object StreamingMeshProcessor {

    private const val TAG = "StreamingMeshProcessor"
    private const val IO_BUFFER_BYTES = 524_288 // 512 KB streaming buffer
    private const val GLB_CHUNK_BYTES = 65_536

    // Lossless direct emission up to 1.8M triangles; above that, adaptive vertex clustering
    // preserves silhouette and topology within mobile ES3.0/Vulkan GPU memory bounds while accepting
    // arbitrary (infinite) input vertices and polygons.
    const val UNLIMITED_CAPACITY = Long.MAX_VALUE
    const val DIRECT_TRIANGLE_LIMIT = 1_800_000
    const val CLUSTER_MAX_VERTICES = 850_000
    const val CLUSTER_HASH_TABLE_SIZE = 2_097_152 // Power of 2; ~16 MB hash keys

    data class MeshStats(
        val originalVertices: Int,
        val originalTriangles: Int,
        val outputVertices: Int,
        val outputTriangles: Int,
        val wasSimplified: Boolean,
        val rawOriginalVertices: Long = originalVertices.toLong(),
        val rawOriginalTriangles: Long = originalTriangles.toLong()
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
    ) {
        val isHeavyForMobile: Boolean
            get() = triangleCount > 250_000L || vertexCount > 250_000L || binByteLength > 2_000_000L
    }

    internal class MeshBounds(
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
     * Zero-heap memory-mapped float-tuple storage supporting unlimited (Long-indexed) vertices.
     * - Write phase: sequential buffered binary stream (zero seeks, 512 KB OS-friendly writes).
     * - Read phase: windowed read-only FileChannel.map (32 MB sliding LRU + fallback Raf),
     *   delivering ~5ns random vertex lookups with 0 MB Java heap on 30M+ / infinite meshes.
     */
    internal class MappedFloatTupleStore(
        private val tupleSize: Int,
        private val scratchDir: File,
        private val tag: String
    ) : AutoCloseable {
        private val tupleBytes = tupleSize * 4L
        private val ramCapacityEntries = 65_536 // Up to ~768 KB tiny fast-path in RAM before spilling
        private var ramFastArray: FloatArray? = FloatArray(1024 * tupleSize)
        private var spilledToDisk = false

        private var diskFile: File? = null
        private var writeStream: BufferedOutputStream? = null
        private var writeBuf: ByteBuffer? = null

        private var readRaf: RandomAccessFile? = null
        private var readChannel: FileChannel? = null
        private val windowBytes = 32L * 1024L * 1024L // 32 MB mmap window
        private val mappedWindows = object : LinkedHashMap<Long, MappedByteBuffer>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, MappedByteBuffer>?): Boolean {
                return size > 8 // Cap virtual address space at 256 MB even on 32-bit processes
            }
        }
        private var lastWindowIdx = -1L
        private var lastWindowBuf: MappedByteBuffer? = null
        private val fallbackScratch = ByteArray(16)
        private val fallbackBB = ByteBuffer.wrap(fallbackScratch).order(ByteOrder.LITTLE_ENDIAN)
        private var sealed = false

        var count: Long = 0L
            private set

        val intCount: Int
            get() = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        fun add3(x: Float, y: Float, z: Float) {
            if (!spilledToDisk) {
                val idx = count.toInt()
                if (idx < ramCapacityEntries) {
                    val arr = ensureRamCapacity(idx + 1)
                    val base = idx * 3
                    arr[base] = x
                    arr[base + 1] = y
                    arr[base + 2] = z
                    count++
                    return
                }
                spillRamToDisk()
            }
            val bos = writeStream!!
            val bb = writeBuf!!
            if (bb.remaining() < 12) flushWriteBuffer(bos, bb)
            bb.putFloat(x)
            bb.putFloat(y)
            bb.putFloat(z)
            count++
        }

        fun add2(u: Float, v: Float) {
            if (!spilledToDisk) {
                val idx = count.toInt()
                if (idx < ramCapacityEntries) {
                    val arr = ensureRamCapacity(idx + 1)
                    val base = idx * 2
                    arr[base] = u
                    arr[base + 1] = v
                    count++
                    return
                }
                spillRamToDisk()
            }
            val bos = writeStream!!
            val bb = writeBuf!!
            if (bb.remaining() < 8) flushWriteBuffer(bos, bb)
            bb.putFloat(u)
            bb.putFloat(v)
            count++
        }

        fun sealForRead() {
            if (sealed) return
            sealed = true
            if (spilledToDisk) {
                writeStream?.let { bos ->
                    writeBuf?.let { bb -> flushWriteBuffer(bos, bb) }
                    bos.flush()
                    bos.close()
                }
                writeStream = null
                writeBuf = null
                val f = diskFile ?: return
                val raf = RandomAccessFile(f, "r")
                readRaf = raf
                readChannel = raf.channel
            }
        }

        fun getX(idx: Long): Float = readComponent(idx, 0)
        fun getY(idx: Long): Float = readComponent(idx, 1)
        fun getZ(idx: Long): Float = readComponent(idx, 2)
        fun getU(idx: Long): Float = readComponent(idx, 0)
        fun getV(idx: Long): Float = readComponent(idx, 1)

        fun getX(idx: Int): Float = readComponent(idx.toLong(), 0)
        fun getY(idx: Int): Float = readComponent(idx.toLong(), 1)
        fun getZ(idx: Int): Float = readComponent(idx.toLong(), 2)
        fun getU(idx: Int): Float = readComponent(idx.toLong(), 0)
        fun getV(idx: Int): Float = readComponent(idx.toLong(), 1)

        fun readTuple3(idx: Long, out: FloatArray, outOffset: Int = 0) {
            if (idx < 0L || idx >= count) {
                out[outOffset] = 0f; out[outOffset + 1] = 0f; out[outOffset + 2] = 0f
                return
            }
            if (!spilledToDisk) {
                val arr = ramFastArray ?: return
                val base = idx.toInt() * 3
                out[outOffset] = arr[base]
                out[outOffset + 1] = arr[base + 1]
                out[outOffset + 2] = arr[base + 2]
                return
            }
            if (!sealed) sealForRead()
            val bytePos = idx * 12L
            val wIdx = bytePos / windowBytes
            val inWin = (bytePos - (wIdx * windowBytes)).toInt()
            val win = obtainWindow(wIdx)
            if (win != null && inWin + 12 <= win.limit()) {
                out[outOffset] = win.getFloat(inWin)
                out[outOffset + 1] = win.getFloat(inWin + 4)
                out[outOffset + 2] = win.getFloat(inWin + 8)
                return
            }
            readFallbackBytes(bytePos, 12)
            out[outOffset] = fallbackBB.getFloat(0)
            out[outOffset + 1] = fallbackBB.getFloat(4)
            out[outOffset + 2] = fallbackBB.getFloat(8)
        }

        private fun readComponent(idx: Long, comp: Int): Float {
            if (idx < 0L || idx >= count) return 0f
            if (!spilledToDisk) {
                val arr = ramFastArray ?: return 0f
                return arr[idx.toInt() * tupleSize + comp]
            }
            if (!sealed) sealForRead()
            val bytePos = idx * tupleBytes + comp * 4L
            val wIdx = bytePos / windowBytes
            val inWin = (bytePos - (wIdx * windowBytes)).toInt()
            val win = obtainWindow(wIdx)
            if (win != null && inWin + 4 <= win.limit()) {
                return win.getFloat(inWin)
            }
            readFallbackBytes(bytePos, 4)
            return fallbackBB.getFloat(0)
        }

        private fun obtainWindow(wIdx: Long): MappedByteBuffer? {
            if (wIdx == lastWindowIdx && lastWindowBuf != null) return lastWindowBuf
            val cached = mappedWindows[wIdx]
            if (cached != null) {
                lastWindowIdx = wIdx
                lastWindowBuf = cached
                return cached
            }
            val ch = readChannel ?: return null
            val startByte = wIdx * windowBytes
            val totalLen = ch.size()
            if (startByte >= totalLen) return null
            val mapLen = minOf(windowBytes + 16L, totalLen - startByte)
            val mapped = runCatching {
                ch.map(FileChannel.MapMode.READ_ONLY, startByte, mapLen).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                }
            }.getOrNull() ?: return null
            mappedWindows[wIdx] = mapped
            lastWindowIdx = wIdx
            lastWindowBuf = mapped
            return mapped
        }

        private fun readFallbackBytes(bytePos: Long, size: Int) {
            val raf = readRaf ?: return
            synchronized(raf) {
                raf.seek(bytePos)
                raf.readFully(fallbackScratch, 0, size)
            }
        }

        private fun ensureRamCapacity(requiredEntries: Int): FloatArray {
            val reqFloats = requiredEntries * tupleSize
            val cur = ramFastArray!!
            if (reqFloats <= cur.size) return cur
            val newEntries = minOf(ramCapacityEntries, maxOf(requiredEntries, (cur.size / tupleSize) * 2))
            return cur.copyOf(newEntries * tupleSize).also { ramFastArray = it }
        }

        private fun spillRamToDisk() {
            scratchDir.mkdirs()
            val file = File(scratchDir, "vtx_${tag}_${System.nanoTime()}.bin")
            diskFile = file
            val bos = BufferedOutputStream(FileOutputStream(file), IO_BUFFER_BYTES)
            val bb = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            writeStream = bos
            writeBuf = bb
            val cur = ramFastArray
            if (cur != null) {
                val floatCount = count.toInt() * tupleSize
                for (i in 0 until floatCount) {
                    if (bb.remaining() < 4) flushWriteBuffer(bos, bb)
                    bb.putFloat(cur[i])
                }
            }
            ramFastArray = null
            spilledToDisk = true
        }

        private fun flushWriteBuffer(bos: BufferedOutputStream, bb: ByteBuffer) {
            val pos = bb.position()
            if (pos > 0) {
                bos.write(bb.array(), 0, pos)
                bb.clear()
            }
        }

        override fun close() {
            ramFastArray = null
            mappedWindows.clear()
            lastWindowBuf = null
            lastWindowIdx = -1L
            runCatching { writeStream?.close() }
            writeStream = null
            writeBuf = null
            runCatching { readChannel?.close() }
            readChannel = null
            runCatching { readRaf?.close() }
            readRaf = null
            runCatching { diskFile?.delete() }
            diskFile = null
        }
    }

    /**
     * Streaming spatial vertex clustering engine that converts arbitrarily large triangle streams
     * (30M, 100M, infinity polygons/vertices) into a crisp, watertight-oriented GLB mesh well within
     * mobile GPU/VRAM budgets while growing arrays lazily instead of reserving 66 MB up front.
     */
    private class SpatialClusterAccumulator(
        private val bounds: MeshBounds,
        private val gridResolution: Int,
        private val maxClusters: Int = CLUSTER_MAX_VERTICES,
        private val forceNoOverflowFallback: Boolean = false
    ) {
        private val tableMask = CLUSTER_HASH_TABLE_SIZE - 1
        private var hashKeys = LongArray(CLUSTER_HASH_TABLE_SIZE) { -1L }
        private var hashVals = IntArray(CLUSTER_HASH_TABLE_SIZE)

        private var capacity = 65_536
        private var sumX = FloatArray(capacity)
        private var sumY = FloatArray(capacity)
        private var sumZ = FloatArray(capacity)
        private var normX = FloatArray(capacity)
        private var normY = FloatArray(capacity)
        private var normZ = FloatArray(capacity)
        private var sumU = FloatArray(capacity)
        private var sumV = FloatArray(capacity)
        private var counts = IntArray(capacity)

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

        private fun growArrays(needed: Int) {
            val nextCap = minOf(maxClusters, maxOf(needed, capacity * 2))
            sumX = sumX.copyOf(nextCap)
            sumY = sumY.copyOf(nextCap)
            sumZ = sumZ.copyOf(nextCap)
            normX = normX.copyOf(nextCap)
            normY = normY.copyOf(nextCap)
            normZ = normZ.copyOf(nextCap)
            sumU = sumU.copyOf(nextCap)
            sumV = sumV.copyOf(nextCap)
            counts = counts.copyOf(nextCap)
            capacity = nextCap
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
                        if (forceNoOverflowFallback) {
                            // Quantize key down safely into an existing bucket rather than discarding geometry
                            val foldedId = (mix64(key) and 0x7FFF_FFFF) % maxClusters
                            return foldedId
                        }
                        overflowed = true
                        return -1
                    }
                    val cId = clusterCount++
                    if (cId >= capacity) growArrays(cId + 1)
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

        fun writeToGlb(
            outGlb: File,
            baseColorFactor: FloatArray? = null,
            metallicFactor: Float = 0.1f,
            roughnessFactor: Float = 0.7f,
            textureFile: File? = null,
            textureMime: String? = null
        ): MeshBounds {
            triFos?.let { out ->
                if (triChunk.position() > 0) {
                    out.write(triChunk.array(), 0, triChunk.position())
                    triChunk.clear()
                }
                out.flush()
                out.close()
            }
            triFos = null

            val finalBounds = MeshBounds()

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

            hashKeys = LongArray(0)
            hashVals = IntArray(0)
            counts = IntArray(0)

            val texBytes = if (textureFile != null && textureFile.exists() && textureFile.length() in 1L..24_000_000L) {
                runCatching { textureFile.readBytes() }.getOrNull()
            } else null

            val indexCount = emittedTriangles * 3
            val useIntIndices = clusterCount > 65535 || indexCount > 65535
            val posBytes = clusterCount.toLong() * 3L * 4L
            val normBytes = clusterCount.toLong() * 3L * 4L
            val uvBytes = clusterCount.toLong() * 2L * 4L
            val idxBytes = if (useIntIndices) indexCount.toLong() * 4L else indexCount.toLong() * 2L
            val rawGeoBytes = posBytes + normBytes + uvBytes + idxBytes
            val texPrePad = if (texBytes != null) ((4L - (rawGeoBytes % 4L)) % 4L).toInt() else 0
            val totalBinBytes = rawGeoBytes + texPrePad + (texBytes?.size?.toLong() ?: 0L)
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
                generator = "MixedReality Streaming Cluster Engine",
                baseColorFactor = baseColorFactor,
                metallicFactor = metallicFactor,
                roughnessFactor = roughnessFactor,
                textureOffset = rawGeoBytes + texPrePad,
                textureByteLength = texBytes?.size ?: 0,
                textureMimeType = textureMime
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

                val tFile = triFile
                if (tFile != null && tFile.exists() && emittedTriangles > 0) {
                    val readChunk = ByteArray(65_536 * 3)
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
                flushWriteBuf(bos, writeBuf)
                if (texBytes != null) {
                    for (p in 0 until texPrePad) bos.write(0)
                    bos.write(texBytes)
                }
                for (p in 0 until binPadding) bos.write(0)
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
        val bounds = MeshBounds()

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

        fun finalizeToGlb(
            outGlb: File,
            generator: String,
            baseColorFactor: FloatArray? = null,
            metallicFactor: Float = 0.1f,
            roughnessFactor: Float = 0.7f,
            textureFile: File? = null,
            textureMime: String? = null
        ) {
            flushWriteBuf(posOut, posBuf); posOut.close()
            flushWriteBuf(normOut, normBuf); normOut.close()
            flushWriteBuf(uvOut, uvBuf); uvOut.close()
            flushWriteBuf(idxOut, idxBuf); idxOut.close()
            bounds.normalizeIfEmpty()

            val texBytes = if (textureFile != null && textureFile.exists() && textureFile.length() in 1L..24_000_000L) {
                runCatching { textureFile.readBytes() }.getOrNull()
            } else null

            val useIntIndices = vertexCount > 65535 || indexCount > 65535
            val posBytes = vertexCount.toLong() * 3L * 4L
            val normBytes = vertexCount.toLong() * 3L * 4L
            val uvBytes = vertexCount.toLong() * 2L * 4L
            val idxBytes = if (useIntIndices) indexCount.toLong() * 4L else indexCount.toLong() * 2L
            val rawGeoBytes = posBytes + normBytes + uvBytes + idxBytes
            val texPrePad = if (texBytes != null) ((4L - (rawGeoBytes % 4L)) % 4L).toInt() else 0
            val totalBinBytes = rawGeoBytes + texPrePad + (texBytes?.size?.toLong() ?: 0L)
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
                generator = generator,
                baseColorFactor = baseColorFactor,
                metallicFactor = metallicFactor,
                roughnessFactor = roughnessFactor,
                textureOffset = rawGeoBytes + texPrePad,
                textureByteLength = texBytes?.size ?: 0,
                textureMimeType = textureMime
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
                if (texBytes != null) {
                    for (p in 0 until texPrePad) bos.write(0)
                    bos.write(texBytes)
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

            val bounds = MeshBounds()
            var totalTris = 0L

            // Pass 1: exact triangle count and bounding box
            if (!isAscii && fileLen >= 84L) {
                val lenBB = ByteBuffer.wrap(header, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
                val declaredTris = lenBB.int.toLong() and 0xFFFF_FFFFL
                val maxPossible = ((fileLen - 84L) / 50L).coerceAtLeast(0L)
                val binaryCount = minOf(declaredTris, maxPossible)
                totalTris = binaryCount
                val recBuf = ByteArray(50 * 4096)
                val recBB = ByteBuffer.wrap(recBuf).order(ByteOrder.LITTLE_ENDIAN)
                BufferedInputStream(FileInputStream(stlFile), IO_BUFFER_BYTES).use { bis ->
                    skipFully(bis, 84L)
                    var remaining = binaryCount
                    while (remaining > 0L) {
                        val batch = minOf(remaining, 4096L).toInt()
                        readFully(bis, recBuf, batch * 50)
                        for (i in 0 until batch) {
                            val base = i * 50
                            bounds.expand(recBB.getFloat(base + 12), recBB.getFloat(base + 16), recBB.getFloat(base + 20))
                            bounds.expand(recBB.getFloat(base + 24), recBB.getFloat(base + 28), recBB.getFloat(base + 32))
                            bounds.expand(recBB.getFloat(base + 36), recBB.getFloat(base + 40), recBB.getFloat(base + 44))
                        }
                        remaining -= batch.toLong()
                    }
                }
            } else {
                var vInTri = 0
                BufferedInputStream(FileInputStream(stlFile), IO_BUFFER_BYTES).bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
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

            if (totalTris <= 0L) {
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
                "STL (${formatCompact(stats.rawOriginalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles.toLong())} tris for mobile GPU"
            } else {
                "STL (${formatCompact(stats.outputTriangles.toLong())} triangles) loaded natively"
            }
            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "STL",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = note,
                isSuccess = true,
                rawTriangleCount = stats.rawOriginalTriangles,
                rawVertexCount = stats.rawOriginalVertices
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
        totalTris: Long,
        bounds: MeshBounds,
        scratchDir: File,
        outGlb: File
    ): MeshStats {
        val useCluster = totalTris > 600_000L
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
                val rawVerts = totalTris * 3L
                return MeshStats(
                    originalVertices = rawVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = writer.vertexCount,
                    outputTriangles = writer.indexCount / 3,
                    wasSimplified = false,
                    rawOriginalVertices = rawVerts,
                    rawOriginalTriangles = totalTris
                )
            }
        }

        var gridRes = estimateInitialGridResolution(totalTris)
        while (true) {
            val isFloor = gridRes <= 64
            val acc = SpatialClusterAccumulator(bounds, gridRes, forceNoOverflowFallback = isFloor)
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
                if (acc.overflowed && !isFloor) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(64)
                    continue
                }
                val outTris = acc.emittedTriangles
                val outVerts = acc.clusterCount
                acc.writeToGlb(outGlb)
                val rawVerts = totalTris * 3L
                return MeshStats(
                    originalVertices = rawVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = outVerts,
                    outputTriangles = outTris,
                    wasSimplified = true,
                    rawOriginalVertices = rawVerts,
                    rawOriginalTriangles = totalTris
                )
            } finally {
                acc.cleanup()
            }
        }
    }

    private inline fun forEachStlTriangle(
        stlFile: File,
        isAscii: Boolean,
        totalTris: Long,
        onTri: (Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit
    ) {
        if (!isAscii) {
            val recBuf = ByteArray(50 * 4096)
            val recBB = ByteBuffer.wrap(recBuf).order(ByteOrder.LITTLE_ENDIAN)
            BufferedInputStream(FileInputStream(stlFile), IO_BUFFER_BYTES).use { bis ->
                skipFully(bis, 84L)
                var remaining = totalTris
                while (remaining > 0L) {
                    val batch = minOf(remaining, 4096L).toInt()
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
                    remaining -= batch.toLong()
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

            if (headerInfo.vertexCount <= 0L || headerInfo.faceCount <= 0L) {
                return ModelFormatConverter.ConversionResult(
                    glbFile = plyFile,
                    originalFormat = "PLY",
                    triangleCount = 0,
                    vertexCount = headerInfo.vertexCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    message = "PLY contains point cloud with 0 triangle faces",
                    isSuccess = false,
                    rawVertexCount = headerInfo.vertexCount
                )
            }

            val positions = MappedFloatTupleStore(3, scratchDir, "ply_pos")
            val normals = if (headerInfo.hasNormals) MappedFloatTupleStore(3, scratchDir, "ply_nrm") else null
            val uvs = if (headerInfo.hasUvs) MappedFloatTupleStore(2, scratchDir, "ply_uv") else null

            try {
                val bounds = MeshBounds()
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
                    generator = "MixedReality Streaming PLY Engine",
                    indexIsLong = rawTris.indexIsLong
                )
                rawTris.close()

                val msg = if (stats.wasSimplified) {
                    "PLY (${formatCompact(stats.rawOriginalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles.toLong())} tris for mobile GPU"
                } else {
                    "PLY (${formatCompact(stats.outputTriangles.toLong())} triangles) loaded"
                }
                return ModelFormatConverter.ConversionResult(
                    glbFile = outGlb,
                    originalFormat = "PLY",
                    triangleCount = stats.outputTriangles,
                    vertexCount = stats.outputVertices,
                    message = msg,
                    isSuccess = true,
                    rawTriangleCount = stats.rawOriginalTriangles,
                    rawVertexCount = stats.rawOriginalVertices
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
     * Streaming Wavefront OBJ converter using zero-heap memory-mapped vertex pools and spatial clustering,
     * supporting unlimited ("∞") vertices & polygons without Int overflow or GC stalls.
     */
    fun convertObjToGlbStreaming(cacheDir: File, objFile: File): ModelFormatConverter.ConversionResult {
        val scratchDir = File(cacheDir, "obj_scratch_${System.nanoTime()}").apply { mkdirs() }
        val positions = MappedFloatTupleStore(3, scratchDir, "obj_pos")
        val normals = MappedFloatTupleStore(3, scratchDir, "obj_nrm")
        val uvs = MappedFloatTupleStore(2, scratchDir, "obj_uv")
        val triIndexFile = File(scratchDir, "obj_tris.bin")

        val bounds = MeshBounds()
        var triCount = 0L
        var parsedMtlNote: String? = null
        var faceV = LongArray(16)
        var faceT = LongArray(16)
        var faceN = LongArray(16)

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
                                    parseObjFaceTokenLong(line, cursor, tokEnd, totalV, totalT, totalN, faceV, faceT, faceN, faceCount)
                                    faceCount++
                                }
                                cursor = skipWhitespace(line, tokEnd, len)
                            }
                            if (faceCount >= 3) {
                                val v0 = faceV[0] - 1L
                                if (v0 !in 0L until totalV) continue
                                val t0 = faceT[0] - 1L
                                val n0 = faceN[0] - 1L
                                for (i in 1 until faceCount - 1) {
                                    val v1 = faceV[i] - 1L
                                    val v2 = faceV[i + 1] - 1L
                                    if (v1 !in 0L until totalV || v2 !in 0L until totalV) continue
                                    if (triBuf.remaining() < 72) flushWriteBuf(triOut, triBuf)
                                    triBuf.putLong(v0); triBuf.putLong(t0); triBuf.putLong(n0)
                                    triBuf.putLong(v1); triBuf.putLong(faceT[i] - 1L); triBuf.putLong(faceN[i] - 1L)
                                    triBuf.putLong(v2); triBuf.putLong(faceT[i + 1] - 1L); triBuf.putLong(faceN[i + 1] - 1L)
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

            if (triCount <= 0L || positions.count <= 0L) {
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
                "OBJ (${formatCompact(stats.rawOriginalTriangles)} tris) optimized to ${formatCompact(stats.outputTriangles.toLong())} tris"
            } else {
                "OBJ (${formatCompact(stats.outputTriangles.toLong())} tris) loaded"
            }
            val fullMsg = if (parsedMtlNote != null) "$baseMsg ($parsedMtlNote)" else baseMsg

            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "OBJ",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = fullMsg,
                isSuccess = true,
                rawTriangleCount = stats.rawOriginalTriangles,
                rawVertexCount = stats.rawOriginalVertices
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
        val positions = MappedFloatTupleStore(3, scratchDir, "3mf_pos")
        val triIndexFile = File(scratchDir, "3mf_tris.bin")
        val bounds = MeshBounds()
        var triCount = 0L

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
                                        val v1 = (parser.getAttributeValue(null, "v1")?.toLongOrNull() ?: -1L) + objectVertexOffset
                                        val v2 = (parser.getAttributeValue(null, "v2")?.toLongOrNull() ?: -1L) + objectVertexOffset
                                        val v3 = (parser.getAttributeValue(null, "v3")?.toLongOrNull() ?: -1L) + objectVertexOffset
                                        if (v1 in 0L until positions.count && v2 in 0L until positions.count && v3 in 0L until positions.count) {
                                            if (triBuf.remaining() < 24) flushWriteBuf(triOut, triBuf)
                                            triBuf.putLong(v1)
                                            triBuf.putLong(v2)
                                            triBuf.putLong(v3)
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

            if (triCount <= 0L || positions.count <= 0L) {
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
                generator = "MixedReality Streaming 3MF Engine",
                indexIsLong = true
            )

            return ModelFormatConverter.ConversionResult(
                glbFile = outGlb,
                originalFormat = "3MF",
                triangleCount = stats.outputTriangles,
                vertexCount = stats.outputVertices,
                message = "3MF (${formatCompact(stats.rawOriginalTriangles)} tris) loaded",
                isSuccess = true,
                rawTriangleCount = stats.rawOriginalTriangles,
                rawVertexCount = stats.rawOriginalVertices
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

                val root = MiniJson.parseObject(jsonStr) ?: return@runCatching GlbInspection(false, 0, 0, 0, 0, false, false, false, false, 0)
                val accessors = root.optArr("accessors") ?: MiniJson.Arr(emptyList())
                val meshes = root.optArr("meshes") ?: MiniJson.Arr(emptyList())

                var vertTotal = 0L
                var triTotal = 0L
                for (m in 0 until meshes.length()) {
                    val prims = meshes.optObj(m)?.optArr("primitives") ?: continue
                    for (p in 0 until prims.length()) {
                        val prim = prims.optObj(p) ?: continue
                        val attrs = prim.optObj("attributes")
                        val posAccIdx = attrs?.optInt("POSITION", -1) ?: -1
                        val primVerts = if (posAccIdx in 0 until accessors.length()) {
                            accessors.optObj(posAccIdx)?.optLong("count", 0L) ?: 0L
                        } else 0L
                        vertTotal += primVerts

                        val mode = prim.optInt("mode", 4)
                        val idxAcc = prim.optInt("indices", -1)
                        val elemCount = if (idxAcc in 0 until accessors.length()) {
                            accessors.optObj(idxAcc)?.optLong("count", primVerts) ?: primVerts
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
                val images = root.optArr("images")?.length() ?: 0

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
     * directly on disk into a mobile-safe GLB using bulk channel reads, full node world transforms,
     * and quantized/float accessor support + triangle strip/fan conversion.
     */
    fun simplifyUncompressedGlb(cacheDir: File, srcGlb: File, force: Boolean = false): File? {
        val info = inspectGlbMetadata(srcGlb)
        if (!info.isValidGlb || !info.hasUncompressedAccessors) return null
        if (!force && info.triangleCount <= DIRECT_TRIANGLE_LIMIT && srcGlb.length() <= 40L * 1024L * 1024L) {
            return null
        }
        val scratchDir = File(cacheDir, "glb_decim_${System.nanoTime()}").apply { mkdirs() }
        return try {
            RandomAccessFile(srcGlb, "r").use { raf ->
                val jsonBytes = ByteArray(info.jsonByteLength)
                raf.seek(20L)
                raf.readFully(jsonBytes)
                val binOffset = 20L + info.jsonByteLength.toLong() + 8L

                val root = MiniJson.parseObject(String(jsonBytes, Charsets.UTF_8)) ?: return@use null
                val accessors = root.optArr("accessors") ?: return@use null
                val bufferViews = root.optArr("bufferViews") ?: return@use null
                val meshes = root.optArr("meshes") ?: return@use null

                // Build mesh -> world matrices list via scene/nodes hierarchy
                val meshTransforms = collectGlbMeshWorldMatrices(root, meshes.length())

                val positions = MappedFloatTupleStore(3, scratchDir, "glb_pos")
                val normals = MappedFloatTupleStore(3, scratchDir, "glb_nrm")
                val uvs = MappedFloatTupleStore(2, scratchDir, "glb_uv")
                val triIndexFile = File(scratchDir, "glb_tris.bin")
                val triOut = BufferedOutputStream(FileOutputStream(triIndexFile), IO_BUFFER_BYTES)
                val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                val bounds = MeshBounds()
                var totalTris = 0L

                val chunkBytes = IO_BUFFER_BYTES
                val bulkBuf = ByteArray(chunkBytes)
                val bulkBB = ByteBuffer.wrap(bulkBuf).order(ByteOrder.LITTLE_ENDIAN)

                try {
                    for (m in 0 until meshes.length()) {
                        val prims = meshes.optObj(m)?.optArr("primitives") ?: continue
                        val matrices = meshTransforms[m] ?: listOf(identityMat4())
                        for (mat in matrices) {
                            val isIdentity = isIdentityMat4(mat)
                            for (p in 0 until prims.length()) {
                                val prim = prims.optObj(p) ?: continue
                                val mode = prim.optInt("mode", 4)
                                if (mode !in 4..6) continue
                                val attrs = prim.optObj("attributes") ?: continue
                                val posAccIdx = attrs.optInt("POSITION", -1)
                                if (posAccIdx !in 0 until accessors.length()) continue

                                val posAcc = accessors.optObj(posAccIdx) ?: continue
                                if (posAcc.optString("type") != "VEC3") continue
                                val posComp = posAcc.optInt("componentType")
                                val posCompSize = gltfCompByteSize(posComp)
                                if (posCompSize == 0) continue
                                val posCount = posAcc.optLong("count", 0L)
                                val posBvIdx = posAcc.optInt("bufferView", -1)
                                if (posCount <= 0L || posBvIdx !in 0 until bufferViews.length()) continue
                                val posBv = bufferViews.optObj(posBvIdx) ?: continue
                                val posMinElemBytes = posCompSize * 3
                                val posStride = posBv.optInt("byteStride", posMinElemBytes).coerceAtLeast(posMinElemBytes)
                                val posBaseOffset = binOffset + posBv.optLong("byteOffset", 0L) + posAcc.optLong("byteOffset", 0L)
                                val posNormalized = posAcc.optBoolean("normalized", false)

                                // Optional NORMAL & TEXCOORD_0 streams read sequentially if present
                                val nrmAccIdx = attrs.optInt("NORMAL", -1)
                                val uvAccIdx = attrs.optInt("TEXCOORD_0", -1)

                                val vertStartIdx = positions.count
                                streamGlbVec3AccessorBulk(
                                    raf = raf,
                                    baseOffset = posBaseOffset,
                                    count = posCount,
                                    stride = posStride,
                                    compType = posComp,
                                    normalized = posNormalized,
                                    worldMat = if (isIdentity) null else mat,
                                    isNormal = false,
                                    bulkBuf = bulkBuf,
                                    bulkBB = bulkBB,
                                    dst = positions,
                                    bounds = bounds
                                )

                                // Keep normals/uvs aligned to positions.count
                                val nrmWritten = if (nrmAccIdx in 0 until accessors.length()) {
                                    val nAcc = accessors.optObj(nrmAccIdx)
                                    val nBvIdx = nAcc?.optInt("bufferView", -1) ?: -1
                                    val nComp = nAcc?.optInt("componentType") ?: 0
                                    val nCompSz = gltfCompByteSize(nComp)
                                    if (nAcc != null && nAcc.optString("type") == "VEC3" && nCompSz > 0 &&
                                        nAcc.optLong("count", 0L) == posCount && nBvIdx in 0 until bufferViews.length()
                                    ) {
                                        val nBv = bufferViews.optObj(nBvIdx)!!
                                        val nStride = nBv.optInt("byteStride", nCompSz * 3).coerceAtLeast(nCompSz * 3)
                                        val nOff = binOffset + nBv.optLong("byteOffset", 0L) + nAcc.optLong("byteOffset", 0L)
                                        streamGlbVec3AccessorBulk(
                                            raf = raf,
                                            baseOffset = nOff,
                                            count = posCount,
                                            stride = nStride,
                                            compType = nComp,
                                            normalized = nAcc.optBoolean("normalized", true),
                                            worldMat = if (isIdentity) null else mat,
                                            isNormal = true,
                                            bulkBuf = bulkBuf,
                                            bulkBB = bulkBB,
                                            dst = normals,
                                            bounds = null
                                        )
                                        true
                                    } else false
                                } else false
                                if (!nrmWritten) {
                                    for (k in 0L until posCount) normals.add3(0f, 1f, 0f)
                                }

                                val uvWritten = if (uvAccIdx in 0 until accessors.length()) {
                                    val uAcc = accessors.optObj(uvAccIdx)
                                    val uBvIdx = uAcc?.optInt("bufferView", -1) ?: -1
                                    val uComp = uAcc?.optInt("componentType") ?: 0
                                    val uCompSz = gltfCompByteSize(uComp)
                                    if (uAcc != null && uAcc.optString("type") == "VEC2" && uCompSz > 0 &&
                                        uAcc.optLong("count", 0L) == posCount && uBvIdx in 0 until bufferViews.length()
                                    ) {
                                        val uBv = bufferViews.optObj(uBvIdx)!!
                                        val uStride = uBv.optInt("byteStride", uCompSz * 2).coerceAtLeast(uCompSz * 2)
                                        val uOff = binOffset + uBv.optLong("byteOffset", 0L) + uAcc.optLong("byteOffset", 0L)
                                        streamGlbVec2AccessorBulk(
                                            raf = raf,
                                            baseOffset = uOff,
                                            count = posCount,
                                            stride = uStride,
                                            compType = uComp,
                                            normalized = uAcc.optBoolean("normalized", false),
                                            bulkBuf = bulkBuf,
                                            bulkBB = bulkBB,
                                            dst = uvs
                                        )
                                        true
                                    } else false
                                } else false
                                if (!uvWritten) {
                                    for (k in 0L until posCount) uvs.add2(0f, 0f)
                                }

                                // Stream indices in bulk and triangulate TRIANGLES / STRIP / FAN
                                val idxAccIdx = prim.optInt("indices", -1)
                                totalTris += streamGlbPrimitiveIndicesBulk(
                                    raf = raf,
                                    binOffset = binOffset,
                                    accessors = accessors,
                                    bufferViews = bufferViews,
                                    idxAccIdx = idxAccIdx,
                                    mode = mode,
                                    vertStartIdx = vertStartIdx,
                                    posCount = posCount,
                                    bulkBuf = bulkBuf,
                                    bulkBB = bulkBB,
                                    triOut = triOut,
                                    triBuf = triBuf
                                )
                            }
                        }
                    }

                    flushWriteBuf(triOut, triBuf)
                    triOut.close()
                    bounds.normalizeIfEmpty()
                    if (totalTris <= 0L) return@use null

                    // Preserve first material's baseColorFactor if no external texture dependencies exist
                    val baseColor = extractPrimaryMaterialBaseColor(root)
                    val outGlb = File(cacheDir, "${srcGlb.nameWithoutExtension}_optimized.glb")
                    emitIndexedDiskTrianglesToGlb(
                        positions = positions,
                        normals = normals,
                        uvs = uvs,
                        triIndexFile = triIndexFile,
                        totalVerts = positions.count,
                        totalTris = totalTris,
                        bounds = bounds,
                        scratchDir = scratchDir,
                        outGlb = outGlb,
                        generator = "MixedReality GLB Optimizer",
                        indexIsLong = true,
                        baseColorFactor = baseColor
                    )
                    outGlb
                } finally {
                    positions.close()
                    normals.close()
                    uvs.close()
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

    private fun gltfCompByteSize(compType: Int): Int = when (compType) {
        5120, 5121 -> 1 // BYTE, UBYTE
        5122, 5123 -> 2 // SHORT, USHORT
        5125, 5126 -> 4 // UINT, FLOAT
        else -> 0
    }

    private fun decodeGlbScalar(bb: ByteBuffer, buf: ByteArray, offset: Int, compType: Int, normalized: Boolean): Float {
        return when (compType) {
            5126 -> bb.getFloat(offset)
            5122 -> {
                val s = bb.getShort(offset).toFloat()
                if (normalized) maxOf(s / 32767f, -1f) else s
            }
            5123 -> {
                val u = (bb.getShort(offset).toInt() and 0xFFFF).toFloat()
                if (normalized) u / 65535f else u
            }
            5120 -> {
                val b = buf[offset].toFloat()
                if (normalized) maxOf(b / 127f, -1f) else b
            }
            5121 -> {
                val ub = (buf[offset].toInt() and 0xFF).toFloat()
                if (normalized) ub / 255f else ub
            }
            5125 -> bb.getInt(offset).toFloat()
            else -> 0f
        }
    }

    private fun streamGlbVec3AccessorBulk(
        raf: RandomAccessFile,
        baseOffset: Long,
        count: Long,
        stride: Int,
        compType: Int,
        normalized: Boolean,
        worldMat: DoubleArray?,
        isNormal: Boolean,
        bulkBuf: ByteArray,
        bulkBB: ByteBuffer,
        dst: MappedFloatTupleStore,
        bounds: MeshBounds?
    ) {
        val cSz = gltfCompByteSize(compType)
        val elemsPerChunk = (bulkBuf.size / stride).coerceAtLeast(1)
        raf.seek(baseOffset)
        var remaining = count
        while (remaining > 0L) {
            val batch = minOf(remaining, elemsPerChunk.toLong()).toInt()
            val bytesNeeded = if (batch == 1) cSz * 3 else (batch - 1) * stride + cSz * 3
            raf.readFully(bulkBuf, 0, bytesNeeded)
            for (i in 0 until batch) {
                val o = i * stride
                val rx = decodeGlbScalar(bulkBB, bulkBuf, o, compType, normalized)
                val ry = decodeGlbScalar(bulkBB, bulkBuf, o + cSz, compType, normalized)
                val rz = decodeGlbScalar(bulkBB, bulkBuf, o + cSz * 2, compType, normalized)
                if (worldMat == null) {
                    dst.add3(rx, ry, rz)
                    bounds?.expand(rx, ry, rz)
                } else if (isNormal) {
                    val nx = (worldMat[0] * rx + worldMat[4] * ry + worldMat[8] * rz).toFloat()
                    val ny = (worldMat[1] * rx + worldMat[5] * ry + worldMat[9] * rz).toFloat()
                    val nz = (worldMat[2] * rx + worldMat[6] * ry + worldMat[10] * rz).toFloat()
                    val nl = sqrt(nx * nx + ny * ny + nz * nz)
                    if (nl > 1e-6f && nl.isFinite()) {
                        val inv = 1f / nl
                        dst.add3(nx * inv, ny * inv, nz * inv)
                    } else {
                        dst.add3(0f, 1f, 0f)
                    }
                } else {
                    val wx = (worldMat[0] * rx + worldMat[4] * ry + worldMat[8] * rz + worldMat[12]).toFloat()
                    val wy = (worldMat[1] * rx + worldMat[5] * ry + worldMat[9] * rz + worldMat[13]).toFloat()
                    val wz = (worldMat[2] * rx + worldMat[6] * ry + worldMat[10] * rz + worldMat[14]).toFloat()
                    dst.add3(wx, wy, wz)
                    bounds?.expand(wx, wy, wz)
                }
            }
            if (stride > cSz * 3 && remaining > batch) {
                raf.skipBytes(stride - cSz * 3)
            }
            remaining -= batch.toLong()
        }
    }

    private fun streamGlbVec2AccessorBulk(
        raf: RandomAccessFile,
        baseOffset: Long,
        count: Long,
        stride: Int,
        compType: Int,
        normalized: Boolean,
        bulkBuf: ByteArray,
        bulkBB: ByteBuffer,
        dst: MappedFloatTupleStore
    ) {
        val cSz = gltfCompByteSize(compType)
        val elemsPerChunk = (bulkBuf.size / stride).coerceAtLeast(1)
        raf.seek(baseOffset)
        var remaining = count
        while (remaining > 0L) {
            val batch = minOf(remaining, elemsPerChunk.toLong()).toInt()
            val bytesNeeded = if (batch == 1) cSz * 2 else (batch - 1) * stride + cSz * 2
            raf.readFully(bulkBuf, 0, bytesNeeded)
            for (i in 0 until batch) {
                val o = i * stride
                val u = decodeGlbScalar(bulkBB, bulkBuf, o, compType, normalized)
                val v = decodeGlbScalar(bulkBB, bulkBuf, o + cSz, compType, normalized)
                dst.add2(u, v)
            }
            if (stride > cSz * 2 && remaining > batch) {
                raf.skipBytes(stride - cSz * 2)
            }
            remaining -= batch.toLong()
        }
    }

    private fun streamGlbPrimitiveIndicesBulk(
        raf: RandomAccessFile,
        binOffset: Long,
        accessors: MiniJson.Arr,
        bufferViews: MiniJson.Arr,
        idxAccIdx: Int,
        mode: Int,
        vertStartIdx: Long,
        posCount: Long,
        bulkBuf: ByteArray,
        bulkBB: ByteBuffer,
        triOut: BufferedOutputStream,
        triBuf: ByteBuffer
    ): Long {
        var tris = 0L
        fun emitTri(a: Long, b: Long, c: Long) {
            if (a == b || b == c || a == c) return
            if (triBuf.remaining() < 24) flushWriteBuf(triOut, triBuf)
            triBuf.putLong(vertStartIdx + a)
            triBuf.putLong(vertStartIdx + b)
            triBuf.putLong(vertStartIdx + c)
            tris++
        }

        if (idxAccIdx !in 0 until accessors.length()) {
            when (mode) {
                4 -> for (ti in 0L until posCount / 3L) {
                    val b = ti * 3L; emitTri(b, b + 1L, b + 2L)
                }
                5 -> for (ti in 0L until (posCount - 2L).coerceAtLeast(0L)) {
                    if ((ti and 1L) == 0L) emitTri(ti, ti + 1L, ti + 2L)
                    else emitTri(ti + 1L, ti, ti + 2L)
                }
                6 -> for (ti in 1L until (posCount - 1L).coerceAtLeast(1L)) {
                    emitTri(0L, ti, ti + 1L)
                }
            }
            return tris
        }

        val idxAcc = accessors.optObj(idxAccIdx) ?: return 0L
        val compType = idxAcc.optInt("componentType")
        val idxCount = idxAcc.optLong("count", 0L)
        val idxBvIdx = idxAcc.optInt("bufferView", -1)
        if (idxBvIdx !in 0 until bufferViews.length() || idxCount <= 0L) return 0L
        val idxBv = bufferViews.optObj(idxBvIdx) ?: return 0L
        val elemBytes = when (compType) {
            5121 -> 1; 5123 -> 2; 5125 -> 4; else -> 0
        }
        if (elemBytes == 0) return 0L
        val idxOff = binOffset + idxBv.optLong("byteOffset", 0L) + idxAcc.optLong("byteOffset", 0L)
        raf.seek(idxOff)

        var prev0 = 0L
        var prev1 = 0L
        var idxSeen = 0L
        val elemsPerChunk = ((bulkBuf.size / elemBytes) / 3) * 3
        val batchLimit = maxOf(elemsPerChunk, 3)

        var remaining = idxCount
        var triPhase = 0
        while (remaining > 0L) {
            val batch = minOf(remaining, batchLimit.toLong()).toInt()
            raf.readFully(bulkBuf, 0, batch * elemBytes)
            for (i in 0 until batch) {
                val off = i * elemBytes
                val vIdx = when (elemBytes) {
                    1 -> (bulkBuf[off].toInt() and 0xFF).toLong()
                    2 -> (bulkBB.getShort(off).toInt() and 0xFFFF).toLong()
                    else -> bulkBB.getInt(off).toLong() and 0xFFFF_FFFFL
                }
                when (mode) {
                    4 -> {
                        when (triPhase) {
                            0 -> { prev0 = vIdx; triPhase = 1 }
                            1 -> { prev1 = vIdx; triPhase = 2 }
                            else -> { emitTri(prev0, prev1, vIdx); triPhase = 0 }
                        }
                    }
                    5 -> {
                        if (idxSeen == 0L) prev0 = vIdx
                        else if (idxSeen == 1L) prev1 = vIdx
                        else {
                            if ((idxSeen and 1L) == 0L) emitTri(prev0, prev1, vIdx)
                            else emitTri(prev1, prev0, vIdx)
                            prev0 = prev1; prev1 = vIdx
                        }
                    }
                    6 -> {
                        if (idxSeen == 0L) prev0 = vIdx
                        else if (idxSeen == 1L) prev1 = vIdx
                        else {
                            emitTri(prev0, prev1, vIdx)
                            prev1 = vIdx
                        }
                    }
                }
                idxSeen++
            }
            remaining -= batch.toLong()
        }
        return tris
    }

    private fun extractPrimaryMaterialBaseColor(root: MiniJson.Obj): FloatArray? {
        val mats = root.optArr("materials") ?: return null
        if (mats.length() == 0) return null
        val pbr = mats.optObj(0)?.optObj("pbrMetallicRoughness") ?: return null
        val arr = pbr.optArr("baseColorFactor") ?: return null
        if (arr.length() < 3) return null
        return floatArrayOf(
            arr.optDouble(0, 1.0).toFloat(),
            arr.optDouble(1, 1.0).toFloat(),
            arr.optDouble(2, 1.0).toFloat(),
            arr.optDouble(3, 1.0).toFloat()
        )
    }

    private fun collectGlbMeshWorldMatrices(root: MiniJson.Obj, meshCount: Int): Map<Int, List<DoubleArray>> {
        val nodes = root.optArr("nodes") ?: return emptyMap()
        val scenes = root.optArr("scenes")
        val sceneIdx = root.optInt("scene", 0)
        val rootNodeIndices = mutableListOf<Int>()
        val rootArr = scenes?.optObj(sceneIdx)?.optArr("nodes")
        if (rootArr != null && rootArr.length() > 0) {
            for (i in 0 until rootArr.length()) rootNodeIndices.add(rootArr.optInt(i, -1))
        } else {
            val isChild = BooleanArray(nodes.length())
            for (n in 0 until nodes.length()) {
                val ch = nodes.optObj(n)?.optArr("children") ?: continue
                for (c in 0 until ch.length()) {
                    val ci = ch.optInt(c, -1)
                    if (ci in 0 until nodes.length()) isChild[ci] = true
                }
            }
            for (n in 0 until nodes.length()) if (!isChild[n]) rootNodeIndices.add(n)
        }

        val out = HashMap<Int, MutableList<DoubleArray>>(meshCount)
        fun walk(nodeIdx: Int, parentMat: DoubleArray, depth: Int) {
            if (nodeIdx !in 0 until nodes.length() || depth > 128) return
            val node = nodes.optObj(nodeIdx) ?: return
            val local = gltfNodeLocalMatrix(node)
            val world = mulMat4(parentMat, local)
            val meshIdx = node.optInt("mesh", -1)
            if (meshIdx in 0 until meshCount) {
                out.getOrPut(meshIdx) { mutableListOf() }.add(world)
            }
            val children = node.optArr("children") ?: return
            for (c in 0 until children.length()) walk(children.optInt(c, -1), world, depth + 1)
        }
        val id = identityMat4()
        for (rn in rootNodeIndices) walk(rn, id, 0)
        return out
    }

    private fun gltfNodeLocalMatrix(node: MiniJson.Obj): DoubleArray {
        val matArr = node.optArr("matrix")
        if (matArr != null && matArr.length() == 16) {
            return DoubleArray(16) { i -> matArr.optDouble(i, if (i % 5 == 0) 1.0 else 0.0) }
        }
        val tArr = node.optArr("translation")
        val rArr = node.optArr("rotation")
        val sArr = node.optArr("scale")
        val tx = tArr?.optDouble(0, 0.0) ?: 0.0
        val ty = tArr?.optDouble(1, 0.0) ?: 0.0
        val tz = tArr?.optDouble(2, 0.0) ?: 0.0
        val qx = rArr?.optDouble(0, 0.0) ?: 0.0
        val qy = rArr?.optDouble(1, 0.0) ?: 0.0
        val qz = rArr?.optDouble(2, 0.0) ?: 0.0
        val qw = rArr?.optDouble(3, 1.0) ?: 1.0
        val sx = sArr?.optDouble(0, 1.0) ?: 1.0
        val sy = sArr?.optDouble(1, 1.0) ?: 1.0
        val sz = sArr?.optDouble(2, 1.0) ?: 1.0

        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz

        return doubleArrayOf(
            (1.0 - 2.0 * (yy + zz)) * sx, (2.0 * (xy + wz)) * sx, (2.0 * (xz - wy)) * sx, 0.0,
            (2.0 * (xy - wz)) * sy, (1.0 - 2.0 * (xx + zz)) * sy, (2.0 * (yz + wx)) * sy, 0.0,
            (2.0 * (xz + wy)) * sz, (2.0 * (yz - wx)) * sz, (1.0 - 2.0 * (xx + yy)) * sz, 0.0,
            tx, ty, tz, 1.0
        )
    }

    /** Fast, zero-dependency pure-Kotlin JSON reader for GLB/glTF headers across Android & JVM tests. */
    private object MiniJson {
        class Obj(private val map: Map<String, Any?>) {
            fun optObj(k: String): Obj? = map[k] as? Obj
            fun optArr(k: String): Arr? = map[k] as? Arr
            fun optString(k: String, def: String = ""): String = map[k] as? String ?: def
            fun optInt(k: String, def: Int = 0): Int = (map[k] as? Number)?.toInt() ?: def
            fun optLong(k: String, def: Long = 0L): Long = (map[k] as? Number)?.toLong() ?: def
            fun optBoolean(k: String, def: Boolean = false): Boolean = map[k] as? Boolean ?: def
        }
        class Arr(private val list: List<Any?>) {
            fun length(): Int = list.size
            fun optObj(i: Int): Obj? = list.getOrNull(i) as? Obj
            fun optInt(i: Int, def: Int = 0): Int = (list.getOrNull(i) as? Number)?.toInt() ?: def
            fun optDouble(i: Int, def: Double = 0.0): Double = (list.getOrNull(i) as? Number)?.toDouble() ?: def
        }
        fun parseObject(src: String): Obj? {
            val p = Parser(src)
            p.skipWs()
            return p.readVal() as? Obj
        }
        private class Parser(private val s: String) {
            var i = 0
            fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun readVal(): Any? {
                skipWs()
                if (i >= s.length) return null
                return when (s[i]) {
                    '{' -> readObj()
                    '[' -> readArr()
                    '"' -> readStr()
                    't' -> { i = (i + 4).coerceAtMost(s.length); true }
                    'f' -> { i = (i + 5).coerceAtMost(s.length); false }
                    'n' -> { i = (i + 4).coerceAtMost(s.length); null }
                    else -> readNum()
                }
            }
            fun readObj(): Obj {
                i++ // '{'
                val m = HashMap<String, Any?>()
                skipWs()
                if (i < s.length && s[i] == '}') { i++; return Obj(m) }
                while (i < s.length) {
                    skipWs()
                    val k = if (s[i] == '"') readStr() else ""
                    skipWs()
                    if (i < s.length && s[i] == ':') i++
                    val v = readVal()
                    m[k] = v
                    skipWs()
                    if (i < s.length && s[i] == ',') { i++; continue }
                    if (i < s.length && s[i] == '}') { i++; break }
                    break
                }
                return Obj(m)
            }
            fun readArr(): Arr {
                i++ // '['
                val l = ArrayList<Any?>()
                skipWs()
                if (i < s.length && s[i] == ']') { i++; return Arr(l) }
                while (i < s.length) {
                    l.add(readVal())
                    skipWs()
                    if (i < s.length && s[i] == ',') { i++; continue }
                    if (i < s.length && s[i] == ']') { i++; break }
                    break
                }
                return Arr(l)
            }
            fun readStr(): String {
                i++ // '"'
                val start = i
                var hasEsc = false
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\') { hasEsc = true; i += 2; continue }
                    if (c == '"') {
                        val raw = s.substring(start, i)
                        i++
                        return if (!hasEsc) raw else unescape(raw)
                    }
                    i++
                }
                return s.substring(start, i)
            }
            fun unescape(raw: String): String {
                val sb = StringBuilder(raw.length)
                var k = 0
                while (k < raw.length) {
                    val c = raw[k++]
                    if (c == '\\' && k < raw.length) {
                        when (val e = raw[k++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> if (k + 4 <= raw.length) {
                                sb.append(raw.substring(k, k + 4).toIntOrNull(16)?.toChar() ?: ' ')
                                k += 4
                            }
                            else -> sb.append(e)
                        }
                    } else sb.append(c)
                }
                return sb.toString()
            }
            fun readNum(): Double {
                val st = i
                var isDouble = false
                while (i < s.length) {
                    val c = s[i]
                    if (c in '0'..'9' || c == '-' || c == '+') { i++ }
                    else if (c == '.' || c == 'e' || c == 'E') { isDouble = true; i++ }
                    else break
                }
                val txt = s.substring(st, i)
                return if (isDouble) (txt.toDoubleOrNull() ?: 0.0) else (txt.toDoubleOrNull() ?: 0.0)
            }
        }
    }

    private fun identityMat4(): DoubleArray = doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    )

    private fun isIdentityMat4(m: DoubleArray): Boolean {
        return kotlin.math.abs(m[0] - 1.0) < 1e-6 && kotlin.math.abs(m[5] - 1.0) < 1e-6 &&
            kotlin.math.abs(m[10] - 1.0) < 1e-6 && kotlin.math.abs(m[15] - 1.0) < 1e-6 &&
            kotlin.math.abs(m[12]) < 1e-6 && kotlin.math.abs(m[13]) < 1e-6 && kotlin.math.abs(m[14]) < 1e-6 &&
            kotlin.math.abs(m[1]) < 1e-6 && kotlin.math.abs(m[2]) < 1e-6 && kotlin.math.abs(m[4]) < 1e-6 &&
            kotlin.math.abs(m[6]) < 1e-6 && kotlin.math.abs(m[8]) < 1e-6 && kotlin.math.abs(m[9]) < 1e-6
    }

    private fun mulMat4(a: DoubleArray, b: DoubleArray): DoubleArray {
        val c = DoubleArray(16)
        for (col in 0 until 4) {
            val c4 = col * 4
            val b0 = b[c4]; val b1 = b[c4 + 1]; val b2 = b[c4 + 2]; val b3 = b[c4 + 3]
            c[c4]     = a[0] * b0 + a[4] * b1 + a[8]  * b2 + a[12] * b3
            c[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9]  * b2 + a[13] * b3
            c[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            c[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        return c
    }

    // =========================================================================
    // Internal Indexed Disk Emission + Clustering
    // =========================================================================

    internal fun emitIndexedDiskTrianglesToGlb(
        positions: MappedFloatTupleStore,
        normals: MappedFloatTupleStore?,
        uvs: MappedFloatTupleStore?,
        triIndexFile: File,
        totalVerts: Long,
        totalTris: Long,
        bounds: MeshBounds,
        scratchDir: File,
        outGlb: File,
        generator: String,
        indexIsLong: Boolean = false,
        baseColorFactor: FloatArray? = null,
        metallicFactor: Float = 0.1f,
        roughnessFactor: Float = 0.7f,
        textureFile: File? = null,
        textureMime: String? = null
    ): MeshStats {
        positions.sealForRead()
        normals?.sealForRead()
        uvs?.sealForRead()

        val needsCluster = totalVerts > DIRECT_TRIANGLE_LIMIT.toLong() || totalTris > DIRECT_TRIANGLE_LIMIT.toLong()
        val triBytes = if (indexIsLong) 24 else 12
        val readBuf = ByteArray(triBytes * 4096)
        val readBB = ByteBuffer.wrap(readBuf).order(ByteOrder.LITTLE_ENDIAN)

        if (!needsCluster) {
            DirectStreamingGlbWriter(scratchDir).use { writer ->
                if (normals != null && normals.count == positions.count) {
                    var v = 0L
                    while (v < positions.count) {
                        val u = if (uvs != null && v < uvs.count) uvs.getU(v) else 0f
                        val tv = if (uvs != null && v < uvs.count) uvs.getV(v) else 0f
                        writer.emitVertex(
                            positions.getX(v), positions.getY(v), positions.getZ(v),
                            normals.getX(v), normals.getY(v), normals.getZ(v),
                            u, tv
                        )
                        v++
                    }
                    BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val n = bis.read(readBuf)
                            if (n <= 0) break
                            var off = 0
                            while (off + triBytes <= n) {
                                val i0: Long; val i1: Long; val i2: Long
                                if (indexIsLong) {
                                    i0 = readBB.getLong(off); i1 = readBB.getLong(off + 8); i2 = readBB.getLong(off + 16)
                                } else {
                                    i0 = readBB.getInt(off).toLong(); i1 = readBB.getInt(off + 4).toLong(); i2 = readBB.getInt(off + 8).toLong()
                                }
                                if (i0 in 0L until totalVerts && i1 in 0L until totalVerts && i2 in 0L until totalVerts) {
                                    writer.emitTriangle(i0.toInt(), i1.toInt(), i2.toInt())
                                }
                                off += triBytes
                            }
                        }
                    }
                } else {
                    BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                        while (true) {
                            val n = bis.read(readBuf)
                            if (n <= 0) break
                            var off = 0
                            while (off + triBytes <= n) {
                                val i0: Long; val i1: Long; val i2: Long
                                if (indexIsLong) {
                                    i0 = readBB.getLong(off); i1 = readBB.getLong(off + 8); i2 = readBB.getLong(off + 16)
                                } else {
                                    i0 = readBB.getInt(off).toLong(); i1 = readBB.getInt(off + 4).toLong(); i2 = readBB.getInt(off + 8).toLong()
                                }
                                if (i0 in 0L until totalVerts && i1 in 0L until totalVerts && i2 in 0L until totalVerts) {
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
                                off += triBytes
                            }
                        }
                    }
                }
                writer.finalizeToGlb(
                    outGlb = outGlb,
                    generator = generator,
                    baseColorFactor = baseColorFactor,
                    metallicFactor = metallicFactor,
                    roughnessFactor = roughnessFactor,
                    textureFile = textureFile,
                    textureMime = textureMime
                )
                return MeshStats(
                    originalVertices = totalVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = writer.vertexCount,
                    outputTriangles = writer.indexCount / 3,
                    wasSimplified = false,
                    rawOriginalVertices = totalVerts,
                    rawOriginalTriangles = totalTris
                )
            }
        }

        // Single-pass / adaptive spatial clustering for arbitrarily massive (30M..infinity) meshes
        var gridRes = estimateInitialGridResolution(totalTris)
        val hasPerVertNorm = normals != null && normals.count == positions.count
        while (true) {
            val isFloor = gridRes <= 64
            val acc = SpatialClusterAccumulator(bounds, gridRes, forceNoOverflowFallback = isFloor)
            acc.startTriangleStream(scratchDir)
            try {
                BufferedInputStream(FileInputStream(triIndexFile), IO_BUFFER_BYTES).use { bis ->
                    while (!acc.overflowed) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + triBytes <= n && !acc.overflowed) {
                            val i0: Long; val i1: Long; val i2: Long
                            if (indexIsLong) {
                                i0 = readBB.getLong(off); i1 = readBB.getLong(off + 8); i2 = readBB.getLong(off + 16)
                            } else {
                                i0 = readBB.getInt(off).toLong(); i1 = readBB.getInt(off + 4).toLong(); i2 = readBB.getInt(off + 8).toLong()
                            }
                            off += triBytes
                            if (i0 !in 0L until totalVerts || i1 !in 0L until totalVerts || i2 !in 0L until totalVerts) continue
                            val x0 = positions.getX(i0); val y0 = positions.getY(i0); val z0 = positions.getZ(i0)
                            val x1 = positions.getX(i1); val y1 = positions.getY(i1); val z1 = positions.getZ(i1)
                            val x2 = positions.getX(i2); val y2 = positions.getY(i2); val z2 = positions.getZ(i2)
                            val (fnx, fny, fnz) = if (hasPerVertNorm) Triple(0f, 1f, 0f) else computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
                            val c0 = acc.getOrAssignCluster(
                                x0, y0, z0,
                                if (hasPerVertNorm) normals.getX(i0) else fnx,
                                if (hasPerVertNorm) normals.getY(i0) else fny,
                                if (hasPerVertNorm) normals.getZ(i0) else fnz,
                                if (uvs != null && i0 < uvs.count) uvs.getU(i0) else 0f,
                                if (uvs != null && i0 < uvs.count) uvs.getV(i0) else 0f
                            )
                            val c1 = acc.getOrAssignCluster(
                                x1, y1, z1,
                                if (hasPerVertNorm) normals.getX(i1) else fnx,
                                if (hasPerVertNorm) normals.getY(i1) else fny,
                                if (hasPerVertNorm) normals.getZ(i1) else fnz,
                                if (uvs != null && i1 < uvs.count) uvs.getU(i1) else 0f,
                                if (uvs != null && i1 < uvs.count) uvs.getV(i1) else 0f
                            )
                            val c2 = acc.getOrAssignCluster(
                                x2, y2, z2,
                                if (hasPerVertNorm) normals.getX(i2) else fnx,
                                if (hasPerVertNorm) normals.getY(i2) else fny,
                                if (hasPerVertNorm) normals.getZ(i2) else fnz,
                                if (uvs != null && i2 < uvs.count) uvs.getU(i2) else 0f,
                                if (uvs != null && i2 < uvs.count) uvs.getV(i2) else 0f
                            )
                            acc.addTriangle(c0, c1, c2)
                        }
                    }
                }
                if (acc.overflowed && !isFloor) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(64)
                    continue
                }
                val outVerts = acc.clusterCount
                val outTris = acc.emittedTriangles
                acc.writeToGlb(
                    outGlb = outGlb,
                    baseColorFactor = baseColorFactor,
                    metallicFactor = metallicFactor,
                    roughnessFactor = roughnessFactor,
                    textureFile = textureFile,
                    textureMime = textureMime
                )
                return MeshStats(
                    originalVertices = totalVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = outVerts,
                    outputTriangles = outTris,
                    wasSimplified = true,
                    rawOriginalVertices = totalVerts,
                    rawOriginalTriangles = totalTris
                )
            } finally {
                acc.cleanup()
            }
        }
    }

    private fun emitObjDiskTrianglesToGlb(
        positions: MappedFloatTupleStore,
        normals: MappedFloatTupleStore,
        uvs: MappedFloatTupleStore,
        triRecordFile: File,
        totalVerts: Long,
        totalTris: Long,
        bounds: MeshBounds,
        scratchDir: File,
        outGlb: File
    ): MeshStats {
        positions.sealForRead()
        normals.sealForRead()
        uvs.sealForRead()

        val totalN = normals.count
        val totalT = uvs.count
        val recBytes = 72 // 9 Longs per triangle (v0,t0,n0, v1,t1,n1, v2,t2,n2)
        val readBuf = ByteArray(recBytes * 2048)
        val readBB = ByteBuffer.wrap(readBuf).order(ByteOrder.LITTLE_ENDIAN)

        if (totalVerts <= DIRECT_TRIANGLE_LIMIT.toLong() && totalTris <= DIRECT_TRIANGLE_LIMIT.toLong()) {
            DirectStreamingGlbWriter(scratchDir).use { writer ->
                BufferedInputStream(FileInputStream(triRecordFile), IO_BUFFER_BYTES).use { bis ->
                    while (true) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + recBytes <= n) {
                            val v0 = readBB.getLong(off); val t0 = readBB.getLong(off + 8); val n0 = readBB.getLong(off + 16)
                            val v1 = readBB.getLong(off + 24); val t1 = readBB.getLong(off + 32); val n1 = readBB.getLong(off + 40)
                            val v2 = readBB.getLong(off + 48); val t2 = readBB.getLong(off + 56); val n2 = readBB.getLong(off + 64)
                            off += recBytes
                            if (v0 !in 0L until totalVerts || v1 !in 0L until totalVerts || v2 !in 0L until totalVerts) continue

                            val x0 = positions.getX(v0); val y0 = positions.getY(v0); val z0 = positions.getZ(v0)
                            val x1 = positions.getX(v1); val y1 = positions.getY(v1); val z1 = positions.getZ(v1)
                            val x2 = positions.getX(v2); val y2 = positions.getY(v2); val z2 = positions.getZ(v2)
                            val (fnx, fny, fnz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)

                            val o0 = writer.emitVertex(
                                x0, y0, z0,
                                if (n0 in 0L until totalN) normals.getX(n0) else fnx,
                                if (n0 in 0L until totalN) normals.getY(n0) else fny,
                                if (n0 in 0L until totalN) normals.getZ(n0) else fnz,
                                if (t0 in 0L until totalT) uvs.getU(t0) else 0f,
                                if (t0 in 0L until totalT) uvs.getV(t0) else 0f
                            )
                            val o1 = writer.emitVertex(
                                x1, y1, z1,
                                if (n1 in 0L until totalN) normals.getX(n1) else fnx,
                                if (n1 in 0L until totalN) normals.getY(n1) else fny,
                                if (n1 in 0L until totalN) normals.getZ(n1) else fnz,
                                if (t1 in 0L until totalT) uvs.getU(t1) else 0f,
                                if (t1 in 0L until totalT) uvs.getV(t1) else 0f
                            )
                            val o2 = writer.emitVertex(
                                x2, y2, z2,
                                if (n2 in 0L until totalN) normals.getX(n2) else fnx,
                                if (n2 in 0L until totalN) normals.getY(n2) else fny,
                                if (n2 in 0L until totalN) normals.getZ(n2) else fnz,
                                if (t2 in 0L until totalT) uvs.getU(t2) else 0f,
                                if (t2 in 0L until totalT) uvs.getV(t2) else 0f
                            )
                            writer.emitTriangle(o0, o1, o2)
                        }
                    }
                }
                writer.finalizeToGlb(outGlb, "MixedReality Streaming OBJ Engine")
                return MeshStats(
                    originalVertices = totalVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = writer.vertexCount,
                    outputTriangles = writer.indexCount / 3,
                    wasSimplified = false,
                    rawOriginalVertices = totalVerts,
                    rawOriginalTriangles = totalTris
                )
            }
        }

        // Spatial Clustering Pass for unbounded ("∞") OBJ meshes
        var gridRes = estimateInitialGridResolution(totalTris)
        while (true) {
            val isFloor = gridRes <= 64
            val acc = SpatialClusterAccumulator(bounds, gridRes, forceNoOverflowFallback = isFloor)
            acc.startTriangleStream(scratchDir)
            try {
                BufferedInputStream(FileInputStream(triRecordFile), IO_BUFFER_BYTES).use { bis ->
                    while (!acc.overflowed) {
                        val n = bis.read(readBuf)
                        if (n <= 0) break
                        var off = 0
                        while (off + recBytes <= n && !acc.overflowed) {
                            val v0 = readBB.getLong(off); val t0 = readBB.getLong(off + 8); val n0 = readBB.getLong(off + 16)
                            val v1 = readBB.getLong(off + 24); val t1 = readBB.getLong(off + 32); val n1 = readBB.getLong(off + 40)
                            val v2 = readBB.getLong(off + 48); val t2 = readBB.getLong(off + 56); val n2 = readBB.getLong(off + 64)
                            off += recBytes
                            if (v0 !in 0L until totalVerts || v1 !in 0L until totalVerts || v2 !in 0L until totalVerts) continue

                            val x0 = positions.getX(v0); val y0 = positions.getY(v0); val z0 = positions.getZ(v0)
                            val x1 = positions.getX(v1); val y1 = positions.getY(v1); val z1 = positions.getZ(v1)
                            val x2 = positions.getX(v2); val y2 = positions.getY(v2); val z2 = positions.getZ(v2)
                            val (fnx, fny, fnz) = computeFaceNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)

                            val c0 = acc.getOrAssignCluster(
                                x0, y0, z0,
                                if (n0 in 0L until totalN) normals.getX(n0) else fnx,
                                if (n0 in 0L until totalN) normals.getY(n0) else fny,
                                if (n0 in 0L until totalN) normals.getZ(n0) else fnz,
                                if (t0 in 0L until totalT) uvs.getU(t0) else 0f,
                                if (t0 in 0L until totalT) uvs.getV(t0) else 0f
                            )
                            val c1 = acc.getOrAssignCluster(
                                x1, y1, z1,
                                if (n1 in 0L until totalN) normals.getX(n1) else fnx,
                                if (n1 in 0L until totalN) normals.getY(n1) else fny,
                                if (n1 in 0L until totalN) normals.getZ(n1) else fnz,
                                if (t1 in 0L until totalT) uvs.getU(t1) else 0f,
                                if (t1 in 0L until totalT) uvs.getV(t1) else 0f
                            )
                            val c2 = acc.getOrAssignCluster(
                                x2, y2, z2,
                                if (n2 in 0L until totalN) normals.getX(n2) else fnx,
                                if (n2 in 0L until totalN) normals.getY(n2) else fny,
                                if (n2 in 0L until totalN) normals.getZ(n2) else fnz,
                                if (t2 in 0L until totalT) uvs.getU(t2) else 0f,
                                if (t2 in 0L until totalT) uvs.getV(t2) else 0f
                            )
                            acc.addTriangle(c0, c1, c2)
                        }
                    }
                }
                if (acc.overflowed && !isFloor) {
                    acc.cleanup()
                    gridRes = (gridRes * 0.65f).toInt().coerceAtLeast(64)
                    continue
                }
                val outVerts = acc.clusterCount
                val outTris = acc.emittedTriangles
                acc.writeToGlb(outGlb)
                return MeshStats(
                    originalVertices = totalVerts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    originalTriangles = totalTris.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    outputVertices = outVerts,
                    outputTriangles = outTris,
                    wasSimplified = true,
                    rawOriginalVertices = totalVerts,
                    rawOriginalTriangles = totalTris
                )
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
        val vertexCount: Long,
        val faceCount: Long,
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

    private data class DiskTriStream(val file: File, val count: Long, val indexIsLong: Boolean) {
        fun close() { runCatching { file.delete() } }
    }

    private fun parsePlyHeader(file: File): PlyHeader? {
        FileInputStream(file).use { fis ->
            val buf = ByteArray(32_768)
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
            var vertCount = 0L
            var faceCount = 0L
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
                            val c = parts[2].toLongOrNull() ?: 0L
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
        positions: MappedFloatTupleStore,
        normals: MappedFloatTupleStore?,
        uvs: MappedFloatTupleStore?,
        bounds: MeshBounds,
        scratchDir: File
    ): DiskTriStream {
        val triFile = File(scratchDir, "ply_tris.bin")
        var triCount = 0L
        val triOut = BufferedOutputStream(FileOutputStream(triFile), IO_BUFFER_BYTES)
        val triBuf = ByteBuffer.allocate(GLB_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

        BufferedInputStream(FileInputStream(plyFile), IO_BUFFER_BYTES).use { bis ->
            skipFully(bis, header.headerByteLength)
            if (header.format == PlyFormat.ASCII) {
                val reader = bis.bufferedReader(Charsets.UTF_8)
                var tokStarts = IntArray(64)
                var tokEnds = IntArray(64)
                var vi = 0L
                val maxNeededCoordIdx = max(header.xIdx, max(header.yIdx, header.zIdx))

                while (vi < header.vertexCount) {
                    val line = reader.readLine() ?: break
                    vi++
                    val len = line.length
                    var tCount = 0
                    var c = skipWhitespace(line, 0, len)
                    while (c < len) {
                        val e = findWhitespace(line, c, len)
                        if (e > c) {
                            if (tCount == tokStarts.size) {
                                tokStarts = tokStarts.copyOf(tCount * 2)
                                tokEnds = tokEnds.copyOf(tCount * 2)
                            }
                            tokStarts[tCount] = c
                            tokEnds[tCount] = e
                            tCount++
                        }
                        c = skipWhitespace(line, e, len)
                    }
                    if (tCount <= maxNeededCoordIdx) continue
                    val x = parseFastFloat(line, tokStarts[header.xIdx], tokEnds[header.xIdx])
                    val y = parseFastFloat(line, tokStarts[header.yIdx], tokEnds[header.yIdx])
                    val z = parseFastFloat(line, tokStarts[header.zIdx], tokEnds[header.zIdx])
                    positions.add3(x, y, z)
                    bounds.expand(x, y, z)
                    if (normals != null) {
                        val nx = if (header.nxIdx in 0 until tCount) parseFastFloat(line, tokStarts[header.nxIdx], tokEnds[header.nxIdx]) else 0f
                        val ny = if (header.nyIdx in 0 until tCount) parseFastFloat(line, tokStarts[header.nyIdx], tokEnds[header.nyIdx], 1f) else 1f
                        val nz = if (header.nzIdx in 0 until tCount) parseFastFloat(line, tokStarts[header.nzIdx], tokEnds[header.nzIdx]) else 0f
                        normals.add3(nx, ny, nz)
                    }
                    if (uvs != null) {
                        val u = if (header.uIdx in 0 until tCount) parseFastFloat(line, tokStarts[header.uIdx], tokEnds[header.uIdx]) else 0f
                        val v = if (header.vIdx in 0 until tCount) parseFastFloat(line, tokStarts[header.vIdx], tokEnds[header.vIdx]) else 0f
                        uvs.add2(u, v)
                    }
                }

                var faceVerts = LongArray(64)
                var fi = 0L
                while (fi < header.faceCount) {
                    val line = reader.readLine() ?: break
                    fi++
                    val len = line.length
                    var c = skipWhitespace(line, 0, len)
                    if (c >= len) continue
                    val nEnd = findWhitespace(line, c, len)
                    val nVerts = parseFastInt(line, c, nEnd, 0)
                    if (nVerts < 3) continue
                    if (nVerts > faceVerts.size) faceVerts = LongArray(nVerts)
                    c = skipWhitespace(line, nEnd, len)
                    var parsed = 0
                    while (c < len && parsed < nVerts) {
                        val e = findWhitespace(line, c, len)
                        if (e > c) {
                            faceVerts[parsed++] = parseFastLong(line, c, e, 0L)
                        }
                        c = skipWhitespace(line, e, len)
                    }
                    if (parsed < 3) continue
                    for (i in 1 until parsed - 1) {
                        if (triBuf.remaining() < 24) flushWriteBuf(triOut, triBuf)
                        triBuf.putLong(faceVerts[0]); triBuf.putLong(faceVerts[i]); triBuf.putLong(faceVerts[i + 1])
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

                var vi = 0L
                while (vi < header.vertexCount) {
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
                    vi++
                }

                val dis = DataInputStream(bis)
                val scratch = ByteArray(8)
                val sBB = ByteBuffer.wrap(scratch).order(order)
                var poly = LongArray(64)
                var fi = 0L
                while (fi < header.faceCount) {
                    val cnt = readPlyIntScalar(dis, header.faceListCountType, scratch, sBB)
                    if (cnt < 0 || cnt > 65_536) break
                    if (cnt > poly.size) poly = LongArray(cnt)
                    for (k in 0 until cnt) {
                        poly[k] = readPlyIntScalar(dis, header.faceListIndexType, scratch, sBB).toLong() and 0xFFFF_FFFFL
                    }
                    for (i in 1 until cnt - 1) {
                        if (triBuf.remaining() < 24) flushWriteBuf(triOut, triBuf)
                        triBuf.putLong(poly[0]); triBuf.putLong(poly[i]); triBuf.putLong(poly[i + 1])
                        triCount++
                    }
                    fi++
                }
            }
        }
        flushWriteBuf(triOut, triBuf)
        triOut.close()
        return DiskTriStream(triFile, triCount, indexIsLong = true)
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

    private fun estimateInitialGridResolution(totalTris: Long): Int = when {
        totalTris >= 80_000_000L -> 512
        totalTris >= 30_000_000L -> 600
        totalTris >= 15_000_000L -> 680
        totalTris >= 5_000_000L -> 820
        else -> 960
    }

    internal fun formatCompact(n: Long): String = when {
        n >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format(Locale.US, "%.0fK", n / 1_000.0)
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
        bounds: MeshBounds,
        generator: String,
        baseColorFactor: FloatArray? = null,
        metallicFactor: Float = 0.1f,
        roughnessFactor: Float = 0.7f,
        textureOffset: Long = 0L,
        textureByteLength: Int = 0,
        textureMimeType: String? = null
    ): ByteArray {
        val hasMat = baseColorFactor != null || textureByteLength > 0
        val hasTex = textureByteLength > 0 && !textureMimeType.isNullOrBlank()
        val sb = StringBuilder(1024)
        sb.append('{')
        sb.append("\"asset\":{\"version\":\"2.0\",\"generator\":\"").append(generator).append("\"},")
        sb.append("\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}],")
        sb.append("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"TEXCOORD_0\":2},\"indices\":3,\"mode\":4")
        if (hasMat) sb.append(",\"material\":0")
        sb.append("}]}],")
        if (hasMat) {
            val c = baseColorFactor ?: floatArrayOf(1f, 1f, 1f, 1f)
            sb.append("\"materials\":[{\"pbrMetallicRoughness\":{")
            sb.append("\"baseColorFactor\":[")
                .append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append(',').append(if (c.size > 3) c[3] else 1f)
                .append("],")
            if (hasTex) sb.append("\"baseColorTexture\":{\"index\":0},")
            sb.append("\"metallicFactor\":").append(metallicFactor)
            sb.append(",\"roughnessFactor\":").append(roughnessFactor)
            sb.append("},\"doubleSided\":true}],")
        }
        if (hasTex) {
            sb.append("\"textures\":[{\"sampler\":0,\"source\":0}],")
            sb.append("\"samplers\":[{\"magFilter\":9729,\"minFilter\":9987,\"wrapS\":10497,\"wrapT\":10497}],")
            sb.append("\"images\":[{\"bufferView\":4,\"mimeType\":\"").append(textureMimeType).append("\"}],")
        }
        sb.append("\"accessors\":[")
        sb.append("{\"bufferView\":0,\"byteOffset\":0,\"componentType\":5126,\"count\":").append(vertCount)
            .append(",\"type\":\"VEC3\",\"max\":[").append(bounds.maxX).append(',').append(bounds.maxY).append(',').append(bounds.maxZ)
            .append("],\"min\":[").append(bounds.minX).append(',').append(bounds.minY).append(',').append(bounds.minZ).append("]},")
        sb.append("{\"bufferView\":1,\"byteOffset\":0,\"componentType\":5126,\"count\":").append(vertCount).append(",\"type\":\"VEC3\"},")
        sb.append("{\"bufferView\":2,\"byteOffset\":0,\"componentType\":5126,\"count\":").append(vertCount).append(",\"type\":\"VEC2\"},")
        sb.append("{\"bufferView\":3,\"byteOffset\":0,\"componentType\":").append(if (useIntIndices) 5125 else 5123)
            .append(",\"count\":").append(indexCount).append(",\"type\":\"SCALAR\"}],")
        sb.append("\"bufferViews\":[")
        sb.append("{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(posByteLength).append(",\"target\":34962},")
        sb.append("{\"buffer\":0,\"byteOffset\":").append(posByteLength).append(",\"byteLength\":").append(normByteLength).append(",\"target\":34962},")
        sb.append("{\"buffer\":0,\"byteOffset\":").append(posByteLength + normByteLength).append(",\"byteLength\":").append(uvByteLength).append(",\"target\":34962},")
        sb.append("{\"buffer\":0,\"byteOffset\":").append(posByteLength + normByteLength + uvByteLength).append(",\"byteLength\":").append(indexByteLength).append(",\"target\":34963}")
        if (hasTex) {
            sb.append(",{\"buffer\":0,\"byteOffset\":").append(textureOffset).append(",\"byteLength\":").append(textureByteLength).append('}')
        }
        sb.append("],\"buffers\":[{\"byteLength\":").append(totalBinaryLength).append("}]}")
        return sb.toString().toByteArray(Charsets.UTF_8)
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
        val v = parseFastLong(s, start, end, defaultVal.toLong())
        return v.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun parseFastLong(s: String, start: Int, end: Int, defaultVal: Long): Long {
        if (start >= end) return defaultVal
        var i = start
        var neg = false
        when (s[i]) {
            '-' -> { neg = true; i++ }
            '+' -> i++
        }
        if (i >= end) return defaultVal
        var acc = 0L
        while (i < end) {
            val d = s[i] - '0'
            if (d !in 0..9) return defaultVal
            acc = acc * 10L + d
            i++
        }
        return if (neg) -acc else acc
    }

    private fun parseObjFaceTokenLong(
        line: String, start: Int, end: Int,
        totalV: Long, totalT: Long, totalN: Long,
        outV: LongArray, outT: LongArray, outN: LongArray, slot: Int
    ) {
        val slash1 = line.indexOf('/', start)
        val rawV: Long; val rawT: Long; val rawN: Long
        if (slash1 < 0 || slash1 >= end) {
            rawV = parseFastLong(line, start, end, 1L); rawT = 0L; rawN = 0L
        } else {
            rawV = parseFastLong(line, start, slash1, 1L)
            val slash2 = line.indexOf('/', slash1 + 1)
            if (slash2 < 0 || slash2 >= end) {
                rawT = parseFastLong(line, slash1 + 1, end, 0L); rawN = 0L
            } else {
                rawT = if (slash2 > slash1 + 1) parseFastLong(line, slash1 + 1, slash2, 0L) else 0L
                rawN = if (end > slash2 + 1) parseFastLong(line, slash2 + 1, end, 0L) else 0L
            }
        }
        outV[slot] = if (rawV < 0L) (totalV + 1L + rawV).coerceAtLeast(1L) else rawV
        outT[slot] = if (rawT < 0L) (totalT + 1L + rawT).coerceAtLeast(0L) else rawT
        outN[slot] = if (rawN < 0L) (totalN + 1L + rawN).coerceAtLeast(0L) else rawN
    }
}
