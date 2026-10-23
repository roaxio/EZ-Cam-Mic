package io.github.roaxio.ezcammic.server

import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Dedicated frame broker and egress distributor.
 * Decouples CameraX image analysis ingestion from HTTP/MJPEG network transport
 * in compliance with Three-Tier Pipeline Isolation (Rule 13).
 */
class VideoFrameDistributor {

    private class ClientSession(val outputStream: OutputStream) {
        val pendingFrame = AtomicReference<ByteArray?>(null)
        val isWriting = AtomicBoolean(false)
        @Volatile var isAlive = true
    }

    private val clientSessions = ConcurrentHashMap<OutputStream, ClientSession>()
    private val broadcastExecutor = Executors.newCachedThreadPool()
    private val crlf = "\r\n".toByteArray(Charsets.US_ASCII)

    @Volatile
    var isStopped: Boolean = false
        private set

    val maxStreamingClients: Int
        get() = DynamicResourceBudgets.computeMaxStreamingVideoClients()

    /**
     * Registers a new MJPEG video client stream.
     * Returns true if client registered within dynamic capacity, false if saturated.
     */
    fun registerClient(outputStream: OutputStream): Boolean {
        if (isStopped || clientSessions.size >= maxStreamingClients) {
            return false
        }
        val session = ClientSession(outputStream)
        clientSessions[outputStream] = session
        return true
    }

    /**
     * Unregisters an MJPEG video client stream.
     */
    fun unregisterClient(outputStream: OutputStream) {
        val session = clientSessions.remove(outputStream)
        if (session != null) {
            session.isAlive = false
            session.pendingFrame.set(null)
        }
    }

    /**
     * Non-blocking broadcast of a compressed JPEG frame to all active client sessions.
     * Implements non-blocking drop-oldest frame queues per client via AtomicReference.
     */
    fun distributeFrame(jpegBytes: ByteArray) {
        if (isStopped || clientSessions.isEmpty()) return

        for (session in clientSessions.values) {
            if (!session.isAlive) continue
            session.pendingFrame.set(jpegBytes)
            if (session.isWriting.compareAndSet(false, true)) {
                broadcastExecutor.execute {
                    sendFramesToClient(session)
                }
            }
        }
    }

    private fun sendFramesToClient(session: ClientSession) {
        try {
            while (session.isAlive && !isStopped) {
                val frame = session.pendingFrame.getAndSet(null) ?: break
                val boundaryHeader = (
                    "--frame\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${frame.size}\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)

                session.outputStream.write(boundaryHeader)
                session.outputStream.write(frame)
                session.outputStream.write(crlf)
                session.outputStream.flush()
            }
        } catch (e: Exception) {
            session.isAlive = false
            unregisterClient(session.outputStream)
            try { session.outputStream.close() } catch (ignored: Exception) {}
        } finally {
            session.isWriting.set(false)
            if (session.isAlive && session.pendingFrame.get() != null && !isStopped) {
                if (session.isWriting.compareAndSet(false, true)) {
                    broadcastExecutor.execute { sendFramesToClient(session) }
                }
            }
        }
    }

    fun isClientActive(outputStream: OutputStream): Boolean {
        return clientSessions.containsKey(outputStream)
    }

    fun getConnectedClientsCount(): Int {
        return clientSessions.size
    }

    fun clear() {
        for (session in clientSessions.values) {
            session.isAlive = false
            session.pendingFrame.set(null)
            try { session.outputStream.close() } catch (ignored: Exception) {}
        }
        clientSessions.clear()
    }

    fun reset() {
        isStopped = false
    }

    fun stop() {
        isStopped = true
        clear()
    }
}
