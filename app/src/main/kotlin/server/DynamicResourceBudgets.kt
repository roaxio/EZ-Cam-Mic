package io.github.roaxio.ezcammic.server

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Derives operational limits dynamically from runtime environment capacity
 * (available CPU cores and max JVM heap memory) in accordance with
 * System Invariant & Dynamic Capacity (Rule 10).
 */
object DynamicResourceBudgets {

    val availableProcessors: Int
        get() = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val maxMemoryMb: Long
        get() = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    /**
     * Dynamically computes maximum concurrent streaming video clients.
     * Scale clamped between floor of 4 and ceiling of 32 based on memory and core capacity.
     */
    fun computeMaxStreamingVideoClients(): Int {
        val memoryQuota = (maxMemoryMb / 64).toInt() // 64MB per potential heavy stream buffer pool
        val coreQuota = availableProcessors * 2
        return minOf(memoryQuota, coreQuota).coerceIn(4, 32)
    }

    /**
     * Dynamically computes maximum concurrent streaming audio clients.
     * Scale clamped between floor of 8 and ceiling of 32.
     */
    fun computeMaxStreamingAudioClients(): Int {
        return (availableProcessors * 4).coerceIn(8, 32)
    }

    /**
     * Dynamically computes maximum concurrent client TCP sockets (HTTP requests, APIs, video, audio).
     */
    fun computeMaxConcurrentSockets(): Int {
        val videoClients = computeMaxStreamingVideoClients()
        return (videoClients * 4).coerceIn(16, 128)
    }

    /**
     * Creates a bounded ThreadPoolExecutor dynamically sized for the host device's socket capacity.
     */
    fun createClientThreadPool(): ThreadPoolExecutor {
        val maxThreads = computeMaxConcurrentSockets()
        return ThreadPoolExecutor(
            0,
            maxThreads,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue()
        )
    }
}
