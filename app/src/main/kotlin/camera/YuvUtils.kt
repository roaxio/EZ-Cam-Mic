package io.github.roaxio.ezcammic.camera

import androidx.camera.core.ImageProxy
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch

object YuvUtils {

    // Thread pool for ultra-fast parallel NV21 rotation and mirroring
    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    )

    private var rowBufferY = ByteArray(8192)
    private var rowBufferU = ByteArray(8192)
    private var rowBufferV = ByteArray(8192)

    private fun ensureRowBuffers(stride: Int) {
        if (rowBufferY.size < stride) {
            rowBufferY = ByteArray(stride)
            rowBufferU = ByteArray(stride)
            rowBufferV = ByteArray(stride)
        }
    }

    /**
     * Highly optimized zero-allocation YUV_420_888 to NV21 extraction.
     */
    fun yuv420ToNv21(image: ImageProxy, outNv21: ByteArray) {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        
        ensureRowBuffers(maxOf(yRowStride, vPlane.rowStride, uPlane.rowStride))

        var pos = 0

        // 1. Copy Y plane
        if (yPixelStride == 1 && yRowStride == width) {
            yBuffer.position(0)
            val ySize = width * height
            if (yBuffer.remaining() >= ySize) {
                yBuffer.get(outNv21, 0, ySize)
                pos = ySize
            }
        } else {
            yBuffer.position(0)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                if (yPixelStride == 1) {
                    yBuffer.get(outNv21, pos, width)
                    pos += width
                } else {
                    yBuffer.get(rowBufferY, 0, minOf(yRowStride, yBuffer.remaining()))
                    var srcIdx = 0
                    for (col in 0 until width) {
                        outNv21[pos++] = rowBufferY[srcIdx]
                        srcIdx += yPixelStride
                    }
                }
            }
        }

        // 2. Copy VU planes (NV21 expects V then U interleaved)
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2

        // Fast path: if V and U buffers are already contiguous semi-planar NV21
        if (vPixelStride == 2 && uPixelStride == 2 && vRowStride == uRowStride && vRowStride == width) {
            val uvTotalSize = width * uvHeight
            vBuffer.position(0)
            if (vBuffer.remaining() >= uvTotalSize) {
                vBuffer.get(outNv21, pos, uvTotalSize)
                return
            }
        }

        // Fallback: row-by-row extraction using bulk get() to avoid JNI overhead
        for (row in 0 until uvHeight) {
            vBuffer.position(row * vRowStride)
            vBuffer.get(rowBufferV, 0, minOf(vRowStride, vBuffer.remaining()))
            
            uBuffer.position(row * uRowStride)
            uBuffer.get(rowBufferU, 0, minOf(uRowStride, uBuffer.remaining()))
            
            var vIdx = 0
            var uIdx = 0
            for (col in 0 until uvWidth) {
                outNv21[pos++] = rowBufferV[vIdx]
                outNv21[pos++] = rowBufferU[uIdx]
                vIdx += vPixelStride
                uIdx += uPixelStride
            }
        }
    }

    /**
     * Parallel 90-degree clockwise NV21 rotation using sequential destination writes.
     * Divides workload across available CPU cores to maintain 30+ FPS in portrait mode.
     * Input: (width x height) NV21. Output: (height x width) NV21.
     */
    fun rotateNv21_90(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        val numThreads = 4
        val latch = CountDownLatch(numThreads)
        val chunkWidth = width / numThreads
        val uvChunkWidth = (width / 2) / numThreads
        
        for (i in 0 until numThreads) {
            val startX = i * chunkWidth
            val endX = if (i == numThreads - 1) width else startX + chunkWidth
            
            val uvStartX = i * uvChunkWidth
            val uvEndX = if (i == numThreads - 1) width / 2 else uvStartX + uvChunkWidth
            
            threadPool.execute {
                // Y plane
                var k = startX * height
                for (x in startX until endX) {
                    var srcIdx = (height - 1) * width + x
                    for (y in 0 until height) {
                        dst[k++] = src[srcIdx]
                        srcIdx -= width
                    }
                }
                
                // UV plane
                val uvHeight = height / 2
                val uvWidth = width / 2
                val frameSize = width * height
                var uvK = frameSize + uvStartX * uvHeight * 2
                
                for (x in uvStartX until uvEndX) {
                    var srcIdx = frameSize + (uvHeight - 1) * width + (x * 2)
                    for (y in 0 until uvHeight) {
                        dst[uvK++] = src[srcIdx]
                        dst[uvK++] = src[srcIdx + 1]
                        srcIdx -= width
                    }
                }
                latch.countDown()
            }
        }
        latch.await()
    }

    /**
     * Parallel 270-degree clockwise NV21 rotation using sequential destination writes.
     * Divides workload across available CPU cores to maintain 30+ FPS in portrait mode.
     * Input: (width x height) NV21. Output: (height x width) NV21.
     */
    fun rotateNv21_270(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        val numThreads = 4
        val latch = CountDownLatch(numThreads)
        val chunkWidth = width / numThreads
        val uvChunkWidth = (width / 2) / numThreads
        
        for (i in 0 until numThreads) {
            val startOutRow = i * chunkWidth
            val endOutRow = if (i == numThreads - 1) width else startOutRow + chunkWidth
            
            val startUvOutRow = i * uvChunkWidth
            val endUvOutRow = if (i == numThreads - 1) width / 2 else startUvOutRow + uvChunkWidth
            
            threadPool.execute {
                // Y plane
                var k = startOutRow * height
                for (outRow in startOutRow until endOutRow) {
                    val x = width - 1 - outRow
                    var srcIdx = x
                    for (outCol in 0 until height) {
                        dst[k++] = src[srcIdx]
                        srcIdx += width
                    }
                }
                
                // UV plane
                val uvHeight = height / 2
                val uvWidth = width / 2
                val frameSize = width * height
                var uvK = frameSize + startUvOutRow * uvHeight * 2
                
                for (outRow in startUvOutRow until endUvOutRow) {
                    val x = uvWidth - 1 - outRow
                    var srcIdx = frameSize + (x * 2)
                    for (outCol in 0 until uvHeight) {
                        dst[uvK++] = src[srcIdx]
                        dst[uvK++] = src[srcIdx + 1]
                        srcIdx += width
                    }
                }
                latch.countDown()
            }
        }
        latch.await()
    }

    /**
     * Parallel 180-degree clockwise NV21 rotation.
     */
    fun rotateNv21_180(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        val numThreads = 4
        val latch = CountDownLatch(numThreads)
        val chunkHeight = height / numThreads
        val uvChunkHeight = (height / 2) / numThreads
        
        for (i in 0 until numThreads) {
            val startY = i * chunkHeight
            val endY = if (i == numThreads - 1) height else startY + chunkHeight
            
            val startUvY = i * uvChunkHeight
            val endUvY = if (i == numThreads - 1) height / 2 else startUvY + uvChunkHeight
            
            threadPool.execute {
                // Rotate Y
                var d = startY * width
                for (y in height - 1 - startY downTo height - endY) {
                    val row = y * width
                    for (x in width - 1 downTo 0) {
                        dst[d++] = src[row + x]
                    }
                }
                
                // Rotate UV
                val uvHeight = height / 2
                val uvWidth = width / 2
                val frameSize = width * height
                var uvD = frameSize + startUvY * uvWidth * 2
                
                for (y in uvHeight - 1 - startUvY downTo uvHeight - endUvY) {
                    val row = frameSize + y * width
                    for (x in uvWidth - 1 downTo 0) {
                        val s = row + (x * 2)
                        dst[uvD++] = src[s]
                        dst[uvD++] = src[s + 1]
                    }
                }
                latch.countDown()
            }
        }
        latch.await()
    }

    /**
     * Parallel horizontal mirroring for front cameras.
     */
    fun mirrorNv21(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        val numThreads = 4
        val latch = CountDownLatch(numThreads)
        val chunkHeight = height / numThreads
        val uvChunkHeight = (height / 2) / numThreads
        
        for (i in 0 until numThreads) {
            val startY = i * chunkHeight
            val endY = if (i == numThreads - 1) height else startY + chunkHeight
            
            val startUvY = i * uvChunkHeight
            val endUvY = if (i == numThreads - 1) height / 2 else startUvY + uvChunkHeight
            
            threadPool.execute {
                // Mirror Y
                for (y in startY until endY) {
                    val row = y * width
                    for (x in 0 until width) {
                        dst[row + x] = src[row + (width - 1 - x)]
                    }
                }
                
                // Mirror UV
                val uvHeight = height / 2
                val uvWidth = width / 2
                val frameSize = width * height
                for (y in startUvY until endUvY) {
                    val row = frameSize + y * width
                    for (x in 0 until uvWidth) {
                        val s = row + ((uvWidth - 1 - x) * 2)
                        val d = row + (x * 2)
                        dst[d] = src[s]
                        dst[d + 1] = src[s + 1]
                    }
                }
                latch.countDown()
            }
        }
        latch.await()
    }

    /**
     * Parallel bilinear/nearest-neighbor scaling for NV21 frames.
     * Guarantees that outgoing frames match user-requested resolution exactly even if camera HAL falls back.
     */
    fun scaleNv21(
        src: ByteArray,
        dst: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ) {
        val numThreads = 4
        val latch = CountDownLatch(numThreads)
        val chunkHeight = dstH / numThreads
        val uvChunkHeight = (dstH / 2) / numThreads

        for (i in 0 until numThreads) {
            val startY = i * chunkHeight
            val endY = if (i == numThreads - 1) dstH else startY + chunkHeight

            val startUvY = i * uvChunkHeight
            val endUvY = if (i == numThreads - 1) dstH / 2 else startUvY + uvChunkHeight

            threadPool.execute {
                // Scale Y plane
                for (dy in startY until endY) {
                    val sy = (dy * srcH) / dstH
                    val srcRowOffset = sy * srcW
                    val dstRowOffset = dy * dstW
                    for (dx in 0 until dstW) {
                        val sx = (dx * srcW) / dstW
                        dst[dstRowOffset + dx] = src[srcRowOffset + sx]
                    }
                }

                // Scale UV plane (interleaved V then U in NV21)
                val srcFrameSize = srcW * srcH
                val dstFrameSize = dstW * dstH
                val srcUvH = srcH / 2
                val srcUvW = srcW / 2
                val dstUvW = dstW / 2

                for (duy in startUvY until endUvY) {
                    val suy = (duy * srcUvH) / (dstH / 2)
                    val srcRowOffset = srcFrameSize + suy * srcW
                    val dstRowOffset = dstFrameSize + duy * dstW
                    for (dux in 0 until dstUvW) {
                        val sux = (dux * srcUvW) / dstUvW
                        val sIdx = srcRowOffset + sux * 2
                        val dIdx = dstRowOffset + dux * 2
                        dst[dIdx] = src[sIdx]
                        dst[dIdx + 1] = src[sIdx + 1]
                    }
                }
                latch.countDown()
            }
        }
        latch.await()
    }
}
