package io.github.roaxio.ezcammic.server

import android.util.Base64
import io.github.roaxio.ezcammic.BuildConfig
import io.github.roaxio.ezcammic.audio.AudioStreamManager
import io.github.roaxio.ezcammic.camera.CameraStreamManager
import io.github.roaxio.ezcammic.model.CameraFacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class WebcamHttpServer(
    private val cameraStreamManager: CameraStreamManager,
    private val audioStreamManager: AudioStreamManager,
    private val onRemoteControlCommand: (action: String, value: String) -> Unit
) {

    companion object {
        const val MAX_CONCURRENT_SOCKETS = 128
        const val MAX_STREAMING_VIDEO_CLIENTS = 16
        const val MAX_STREAMING_AUDIO_CLIENTS = 16
    }

    val videoFrameDistributor = VideoFrameDistributor()

    init {
        cameraStreamManager.frameDistributor = videoFrameDistributor
    }

    val maxConcurrentSockets: Int
        get() = DynamicResourceBudgets.computeMaxConcurrentSockets()

    val maxStreamingVideoClients: Int
        get() = videoFrameDistributor.maxStreamingClients

    val maxStreamingAudioClients: Int
        get() = audioStreamManager.maxStreamingClients

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val clientThreadPool = DynamicResourceBudgets.createClientThreadPool()
    private val activeClientSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    @Volatile
    var isRunning = false
        private set

    var port: Int = 4747
        private set

    var isHttps: Boolean = true
        private set

    var isAuthEnabled: Boolean = false
        private set
    @Volatile
    var wifiIp: String? = null

    @Volatile
    var usbIp: String? = null

    @Volatile
    var isWebStreamingEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) pruneConnections(dropWeb = true, dropUsb = false)
        }

    @Volatile
    var isUsbStreamingEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) pruneConnections(dropWeb = false, dropUsb = true)
        }

    private fun isUsbConnection(socket: java.net.Socket): Boolean {
        val clientAddress = socket.inetAddress?.hostAddress ?: ""
        val isLoopback = socket.inetAddress?.isLoopbackAddress == true ||
                clientAddress == "127.0.0.1" ||
                clientAddress == "::1" ||
                clientAddress.startsWith("127.")
        if (isLoopback) return true

        val localAddr = socket.localAddress?.hostAddress
        if (usbIp != null && localAddr == usbIp) return true
        if (wifiIp != null && localAddr == wifiIp) return false

        return try {
            val intf = java.net.NetworkInterface.getByInetAddress(socket.localAddress)
            val name = intf?.name?.lowercase() ?: ""
            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm")
        } catch (e: Exception) {
            false
        }
    }



    private fun pruneConnections(dropWeb: Boolean, dropUsb: Boolean) {
        val iterator = activeClientSockets.iterator()
        while (iterator.hasNext()) {
            val sock = iterator.next()
            val isUsb = isUsbConnection(sock)
            if ((dropWeb && !isUsb) || (dropUsb && isUsb)) {
                try {
                    if (!sock.isClosed) sock.close()
                } catch (e: Exception) {}
                iterator.remove()
            }
        }
    }

    private var authUsername: String = "admin"
    private var authPassword: String = "admin123"

    fun updateAuthCredentials(user: String, pass: String) {
        this.authUsername = user
        this.authPassword = pass
    }

    fun updateAuthEnabled(enabled: Boolean) {
        this.isAuthEnabled = enabled
    }

    fun getVideoClientsCount(): Int = videoFrameDistributor.getConnectedClientsCount()
    fun getAudioClientsCount(): Int = audioStreamManager.getConnectedClientsCount()
    fun getConnectedClientsCount(): Int = getVideoClientsCount() + getAudioClientsCount()

    fun start(
        portNumber: Int = 4747,
        useHttps: Boolean = true,
        isAuth: Boolean = false,
        username: String = "admin",
        password: String = "admin123",
        webEnabled: Boolean = true,
        usbEnabled: Boolean = true
    ) {
        if (isRunning) return
        this.port = portNumber
        this.isHttps = useHttps
        this.isAuthEnabled = isAuth
        this.authUsername = username
        this.authPassword = password
        this.isWebStreamingEnabled = webEnabled
        this.isUsbStreamingEnabled = usbEnabled
        videoFrameDistributor.reset()

        try {
            serverSocket = if (useHttps) {
                SslUtils.createSSLServerSocket(portNumber)
            } else {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress("0.0.0.0", portNumber))
                s
            }
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        socket.soTimeout = 4000
                        socket.tcpNoDelay = true

                        // Prune closed or disconnected sockets before admission check
                        activeClientSockets.removeIf { it.isClosed || !it.isConnected }

                        if (activeClientSockets.size >= maxConcurrentSockets) {
                            try {
                                val os = socket.getOutputStream()
                                val response = ("HTTP/1.1 503 Service Unavailable\r\n" +
                                        "Content-Type: text/plain\r\n" +
                                        "Connection: close\r\n\r\n" +
                                        "Server capacity reached: maximum concurrent clients connected.\r\n").toByteArray(Charsets.US_ASCII)
                                os.write(response)
                                os.flush()
                            } catch (e: Exception) {
                                // ignore
                            } finally {
                                try { socket.close() } catch (e: Exception) {}
                            }
                            continue
                        }

                        activeClientSockets.add(socket)
                        try {
                            clientThreadPool.execute {
                                try {
                                    handleClientSocket(socket)
                                } finally {
                                    activeClientSockets.remove(socket)
                                }
                            }
                        } catch (e: java.util.concurrent.RejectedExecutionException) {
                            activeClientSockets.remove(socket)
                            try {
                                val os = socket.getOutputStream()
                                val response = ("HTTP/1.1 503 Service Unavailable\r\n" +
                                        "Content-Type: text/plain\r\n" +
                                        "Connection: close\r\n\r\n" +
                                        "Server is busy, please try again.\r\n").toByteArray(Charsets.US_ASCII)
                                os.write(response)
                                os.flush()
                            } catch (ignored: Exception) {}
                            try { socket.close() } catch (ignored: Exception) {}
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
        }
    }

    private fun handleClientSocket(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0]
            val fullPath = parts[1]

            // Parse headers
            var headerAuthHeader: String? = null
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Authorization:", ignoreCase = true)) {
                    headerAuthHeader = line.substringAfter(":").trim()
                }
                line = reader.readLine()
            }

            // Parse path and query params
            val uriParts = fullPath.split("?", limit = 2)
            val path = uriParts[0]
            val queryString = if (uriParts.size > 1) uriParts[1] else ""

            val isUsb = isUsbConnection(socket)

            // Check if streaming interface is allowed
            if (!isWebStreamingEnabled && !isUsb) {
                serveForbiddenMode(outputStream, "Wi-Fi Web Streaming is disabled in app settings.")
                socket.close()
                return
            }
            if (!isUsbStreamingEnabled && isUsb) {
                serveForbiddenMode(outputStream, "USB Wired Streaming is disabled in app settings.")
                socket.close()
                return
            }

            // Verify authentication if enabled
            if (isAuthEnabled) {
                var isAuthorized = false

                // 1. Check HTTP Basic Authorization header
                if (headerAuthHeader != null && headerAuthHeader.startsWith("Basic ", ignoreCase = true)) {
                    val base64Creds = headerAuthHeader.substring(6).trim()
                    try {
                        val decoded = String(Base64.decode(base64Creds, Base64.DEFAULT), Charsets.UTF_8)
                        val credParts = decoded.split(":", limit = 2)
                        if (credParts.size == 2 && credParts[0] == authUsername && credParts[1] == authPassword) {
                            isAuthorized = true
                        }
                    } catch (e: Exception) {
                        // ignore parsing error
                    }
                }

                // 2. Check query params fallback (useful for OBS / VLC / direct browser video tags)
                if (!isAuthorized && queryString.isNotBlank()) {
                    val params = queryString.split("&").associate {
                        val kv = it.split("=", limit = 2)
                        if (kv.size == 2) {
                            try {
                                URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
                            } catch (e: Exception) {
                                kv[0] to kv[1]
                            }
                        } else {
                            kv[0] to ""
                        }
                    }
                    val qUser = params["user"] ?: params["username"]
                    val qPass = params["pass"] ?: params["password"]
                    if (qUser == authUsername && qPass == authPassword) {
                        isAuthorized = true
                    }
                }

                if (!isAuthorized) {
                    serveUnauthorized(outputStream)
                    socket.close()
                    return
                }
            }

            when {
                path == "/" || path == "/index.html" -> {
                    serveHtmlDashboard(outputStream, socket)
                    socket.close()
                }
                path.startsWith(StaticFonts.ROUTE_PREFIX) -> {
                    serveFont(outputStream, path)
                    socket.close()
                }
                path == "/video" || path == "/stream.mjpg" || path == "/mjpeg" -> {
                    val maxVideo = maxStreamingVideoClients
                    if (videoFrameDistributor.getConnectedClientsCount() >= maxVideo) {
                        try {
                            val response = ("HTTP/1.1 503 Service Unavailable\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "Maximum concurrent video streams reached ($maxVideo).\r\n").toByteArray(Charsets.US_ASCII)
                            outputStream.write(response)
                            outputStream.flush()
                        } catch (e: Exception) {}
                        socket.close()
                        return
                    }
                    // Hijack connection for continuous MJPEG stream
                    socket.soTimeout = 0
                    socket.keepAlive = true
                    socket.tcpNoDelay = true

                    val initialHeader = (
                            "HTTP/1.1 200 OK\r\n" +
                            "Connection: close\r\n" +
                            "Server: EZCamMic/${BuildConfig.VERSION_NAME}\r\n" +
                            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                    outputStream.write(initialHeader)
                    outputStream.flush()

                    val registered = videoFrameDistributor.registerClient(outputStream)
                    if (!registered) {
                        socket.close()
                        return
                    }

                    try {
                        val buffer = ByteArray(1024)
                        while (isRunning && !socket.isClosed && socket.isConnected && videoFrameDistributor.isClientActive(outputStream)) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break // Client disconnected (EOF)
                        }
                    } catch (e: Exception) {
                        // Client closed or socket terminated
                    } finally {
                        videoFrameDistributor.unregisterClient(outputStream)
                        try {
                            if (!socket.isClosed) socket.close()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                path == "/audio.wav" || path == "/audio" -> {
                    val maxAudio = maxStreamingAudioClients
                    if (audioStreamManager.getConnectedClientsCount() >= maxAudio) {
                        try {
                            val response = ("HTTP/1.1 503 Service Unavailable\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "Maximum concurrent audio streams reached ($maxAudio).\r\n").toByteArray(Charsets.US_ASCII)
                            outputStream.write(response)
                            outputStream.flush()
                        } catch (e: Exception) {}
                        socket.close()
                        return
                    }
                    // Hijack connection for continuous WAV audio stream
                    // Disable read timeout so continuous audio streaming never times out after 4 seconds
                    socket.soTimeout = 0
                    socket.keepAlive = true
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 8192
                    serveAudioStreamHeaders(outputStream)
                    audioStreamManager.registerAudioClient(outputStream)
                    try {
                        val buffer = ByteArray(1024)
                        while (isRunning && !socket.isClosed && socket.isConnected && audioStreamManager.isAudioClientActive(outputStream)) {
                            if (inputStream.available() > 0) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break // Client disconnected (EOF)
                            } else {
                                Thread.sleep(100)
                            }
                        }
                    } catch (e: Exception) {
                        // Client closed or socket terminated
                    } finally {
                        audioStreamManager.unregisterAudioClient(outputStream)
                        try {
                            if (!socket.isClosed) socket.close()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                path == "/shot.jpg" || path == "/snapshot" -> {
                    serveSnapshot(outputStream)
                    socket.close()
                }
                path == "/api/status" -> {
                    serveApiStatus(outputStream)
                    socket.close()
                }
                path == "/api/control" -> {
                    handleApiControl(queryString, outputStream)
                    socket.close()
                }
                else -> {
                    serveNotFound(outputStream)
                    socket.close()
                }
            }

        } catch (e: Exception) {
            // Socket connection terminated or error
        } finally {
            try {
                if (!socket.isClosed) socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun serveUnauthorized(outputStream: OutputStream) {
        val htmlBody = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>401 Authentication Required - EZ-Cam-Mic</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0a0e17; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #111827; padding: 32px; border-radius: 16px; text-align: center; border: 1px solid rgba(239, 68, 68, 0.4); max-width: 420px; box-shadow: 0 20px 40px rgba(0,0,0,0.7); }
                    h2 { color: #f87171; margin-top: 0; font-size: 20px; }
                    p { color: #94a3b8; font-size: 14px; line-height: 1.6; }
                    .tip { background: #1e293b; padding: 10px; border-radius: 8px; font-size: 12px; color: #38bdf8; margin-top: 16px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>🔒 Protected Stream</h2>
                    <p>Authentication is required to access this EZ-Cam-Mic webcam stream.</p>
                    <p>Please enter the custom username and password configured in the app settings.</p>
                    <div class="tip">Tip: For OBS & scripts, use <code>http(s)://user:password@IP:PORT/video</code></div>
                </div>
            </body>
            </html>
        """.trimIndent()
        val bodyBytes = htmlBody.toByteArray(Charsets.UTF_8)
        val header = (
                "HTTP/1.1 401 Unauthorized\r\n" +
                        "WWW-Authenticate: Basic realm=\"EZ-Cam-Mic Web Share\"\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.write(bodyBytes)
        outputStream.flush()
    }

    private fun serveForbiddenMode(outputStream: OutputStream, reason: String) {
        val htmlBody = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>403 Mode Disabled - EZ-Cam-Mic</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0a0e17; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #111827; padding: 32px; border-radius: 16px; text-align: center; border: 1px solid rgba(239, 68, 68, 0.4); max-width: 420px; box-shadow: 0 20px 40px rgba(0,0,0,0.7); }
                    h2 { color: #f87171; margin-top: 0; font-size: 20px; }
                    p { color: #94a3b8; font-size: 14px; line-height: 1.6; }
                    .tip { background: #1e293b; padding: 10px; border-radius: 8px; font-size: 12px; color: #38bdf8; margin-top: 16px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>🚫 Connection Blocked</h2>
                    <p>$reason</p>
                    <div class="tip">Enable this streaming mode on your Android phone screen to allow access.</div>
                </div>
            </body>
            </html>
        """.trimIndent()
        val bodyBytes = htmlBody.toByteArray(Charsets.UTF_8)
        val header = (
                "HTTP/1.1 403 Forbidden\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.write(bodyBytes)
        outputStream.flush()
    }

    private fun serveHtmlDashboard(outputStream: OutputStream, socket: Socket) {
        val isUsb = isUsbConnection(socket)
        val localIp = socket.localAddress?.hostAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
        val connectedIp = localIp ?: (if (isUsb) usbIp else wifiIp) ?: "192.168.1.1"
        val html = HtmlDashboard.getDashboardHtml(
            serverPort = port,
            activeConnectedIp = connectedIp,
            wifiIp = wifiIp,
            usbIp = usbIp,
            isCurrentConnectionUsb = isUsb,
            isHttps = isHttps,
            isAuthEnabled = isAuthEnabled,
            authUsername = authUsername,
            authPassword = authPassword
        )
        val htmlBytes = html.toByteArray(Charsets.UTF_8)
        val responseHeader = (
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${htmlBytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                ).toByteArray()
        outputStream.write(responseHeader)
        outputStream.write(htmlBytes)
        outputStream.flush()
    }

    private fun serveAudioStreamHeaders(outputStream: OutputStream) {
        val header = (
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: audio/wav\r\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Accept-Ranges: none\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.flush()
    }

    private fun serveSnapshot(outputStream: OutputStream) {
        val snapshot = cameraStreamManager.getLatestSnapshot()
        if (snapshot != null) {
            val responseHeader = (
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${snapshot.size}\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Connection: close\r\n" +
                            "Access-Control-Allow-Origin: *\r\n\r\n"
                    ).toByteArray()
            outputStream.write(responseHeader)
            outputStream.write(snapshot)
            outputStream.flush()
        } else {
            val notReady = "Camera snapshot not ready yet.".toByteArray()
            val header = (
                    "HTTP/1.1 503 Service Unavailable\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: ${notReady.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray()
            outputStream.write(header)
            outputStream.write(notReady)
            outputStream.flush()
        }
    }

    private fun serveApiStatus(outputStream: OutputStream) {
        val fps = cameraStreamManager.currentFps.value
        val bitrate = cameraStreamManager.bitrateKbps.value
        val clients = cameraStreamManager.getConnectedClientsCount()
        val audioClients = audioStreamManager.getConnectedClientsCount()
        val audioLevel = audioStreamManager.audioLevelRms.value
        val isTorch = cameraStreamManager.isTorchEnabled
        val isMuted = audioStreamManager.isMuted
        val facing = if (cameraStreamManager.currentFacing == CameraFacing.FRONT) "FRONT" else "BACK"
        val res = cameraStreamManager.activeResolution.value
        val resParts = res.split("x")
        val streamOrientation = if (resParts.size == 2) {
            val w = resParts[0].toIntOrNull() ?: 0
            val h = resParts[1].toIntOrNull() ?: 0
            if (w > h) "LANDSCAPE" else if (w < h) "PORTRAIT" else "SQUARE"
        } else {
            "UNKNOWN"
        }
        val rotation = cameraStreamManager.streamRotation
        val isMirrored = cameraStreamManager.isMirrored

        val json = """
            {
              "isRunning": true,
              "port": $port,
              "isHttps": $isHttps,
              "isAuthProtected": $isAuthEnabled,
              "connectedClients": $clients,
              "connectedAudioClients": $audioClients,
              "currentFps": $fps,
              "bitrateKbps": $bitrate,
              "audioLevelRms": $audioLevel,
              "isTorchOn": $isTorch,
              "isMuted": $isMuted,
              "isCameraDisabled": ${cameraStreamManager.isCameraDisabled},
              "cameraFacing": "$facing",
              "activeResolution": "$res",
              "streamOrientation": "$streamOrientation",
              "rotation": $rotation,
              "isMirrored": $isMirrored
            }
        """.trimIndent()

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val header = (
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.write(jsonBytes)
        outputStream.flush()
    }

    private fun handleApiControl(queryString: String, outputStream: OutputStream) {
        val params = mutableMapOf<String, String>()
        if (queryString.isNotBlank()) {
            queryString.split("&").forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.isNotEmpty()) {
                    val key = URLDecoder.decode(kv[0], "UTF-8")
                    val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                    params[key] = value
                }
            }
        }

        val action = params["action"] ?: ""
        val value = params["val"] ?: ""

        if (action.isNotBlank()) {
            onRemoteControlCommand(action, value)
        }

        val json = """{"status": "ok", "action": "$action", "value": "$value"}"""
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val header = (
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.write(jsonBytes)
        outputStream.flush()
    }

    private fun serveFont(outputStream: OutputStream, path: String) {
        val body = StaticFonts.load(path)
        if (body == null) {
            serveNotFound(outputStream)
            return
        }
        val header = (
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: font/woff2\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Cache-Control: public, max-age=604800\r\n" +
                        "X-Content-Type-Options: nosniff\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)
        outputStream.write(header)
        outputStream.write(body)
        outputStream.flush()
    }

    private fun serveNotFound(outputStream: OutputStream) {
        val body = "404 Not Found".toByteArray()
        val header = (
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                ).toByteArray()
        outputStream.write(header)
        outputStream.write(body)
        outputStream.flush()
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null

        // Close all in-flight client sockets to immediately terminate hijacked streaming loops
        val socketsToClose = activeClientSockets.toList()
        activeClientSockets.clear()
        for (sock in socketsToClose) {
            try {
                if (!sock.isClosed) sock.close()
            } catch (e: Exception) {
                // Ignore socket close error during teardown
            }
        }

        videoFrameDistributor.stop()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }
}

