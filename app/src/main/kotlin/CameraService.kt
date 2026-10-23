package io.github.roaxio.ezcammic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CameraService : Service() {

    @Volatile
    private var isForegroundStarted = false

    private var currentEndpoint: String = ""
    private var currentClients: Int = 0
    private var currentAudioMuted: Boolean = false
    private var currentCameraDisabled: Boolean = false
    private var currentResolution: String = "1080p"

    companion object {
        const val ACTION_START = "io.github.roaxio.ezcammic.action.START_STREAMING"
        const val ACTION_STOP = "io.github.roaxio.ezcammic.action.STOP_STREAMING"
        const val ACTION_USER_STOP = "io.github.roaxio.ezcammic.action.USER_STOP_STREAMING"
        const val ACTION_TOGGLE_MUTE = "io.github.roaxio.ezcammic.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_CAMERA = "io.github.roaxio.ezcammic.action.TOGGLE_CAMERA"

        const val EXTRA_ENDPOINT = "extra_endpoint"
        const val EXTRA_CLIENTS = "extra_clients"
        const val EXTRA_AUDIO_MUTED = "extra_audio_muted"
        const val EXTRA_CAMERA_DISABLED = "extra_camera_disabled"
        const val EXTRA_RESOLUTION = "extra_resolution"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "camera_service_channel"

        private const val REQ_CONTENT = 0
        private const val REQ_TOGGLE_MUTE = 1
        private const val REQ_TOGGLE_CAMERA = 2
        private const val REQ_USER_STOP = 3

        @Volatile
        private var activeInstance: CameraService? = null

        @Volatile
        var onToggleMuteAction: (() -> Unit)? = null

        @Volatile
        var onToggleCameraAction: (() -> Unit)? = null

        @Volatile
        var onStopStreamAction: (() -> Unit)? = null

        fun start(
            context: Context,
            endpoint: String = "",
            connectedClients: Int = 0,
            isAudioMuted: Boolean = false,
            isCameraDisabled: Boolean = false,
            resolution: String = "1080p"
        ) {
            val active = activeInstance
            if (active != null && active.isForegroundStarted) {
                active.updateNotificationState(
                    endpoint = endpoint,
                    connectedClients = connectedClients,
                    isAudioMuted = isAudioMuted,
                    isCameraDisabled = isCameraDisabled,
                    resolution = resolution
                )
                return
            }

            val intent = Intent(context, CameraService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ENDPOINT, endpoint)
                putExtra(EXTRA_CLIENTS, connectedClients)
                putExtra(EXTRA_AUDIO_MUTED, isAudioMuted)
                putExtra(EXTRA_CAMERA_DISABLED, isCameraDisabled)
                putExtra(EXTRA_RESOLUTION, resolution)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Graceful fallback in restricted/test environments
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CameraService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                activeInstance?.stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        when (intent?.action) {
            ACTION_STOP -> {
                isForegroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_USER_STOP -> {
                val callback = onStopStreamAction
                if (callback != null) {
                    callback.invoke()
                } else {
                    isForegroundStarted = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                onToggleMuteAction?.invoke()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_CAMERA -> {
                onToggleCameraAction?.invoke()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (intent != null) {
                    currentEndpoint = intent.getStringExtra(EXTRA_ENDPOINT) ?: currentEndpoint
                    currentClients = intent.getIntExtra(EXTRA_CLIENTS, currentClients)
                    currentAudioMuted = intent.getBooleanExtra(EXTRA_AUDIO_MUTED, currentAudioMuted)
                    currentCameraDisabled = intent.getBooleanExtra(EXTRA_CAMERA_DISABLED, currentCameraDisabled)
                    currentResolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: currentResolution
                }
                startForegroundNotification()
            }
        }
        return START_NOT_STICKY
    }

    internal fun updateNotificationState(
        endpoint: String,
        connectedClients: Int,
        isAudioMuted: Boolean,
        isCameraDisabled: Boolean,
        resolution: String
    ) {
        currentEndpoint = endpoint
        currentClients = connectedClients
        currentAudioMuted = isAudioMuted
        currentCameraDisabled = isCameraDisabled
        currentResolution = resolution

        if (!isForegroundStarted) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(NOTIFICATION_ID, buildSmartNotification())
        } catch (_: Exception) {
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notification = buildSmartNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        } catch (_: Exception) {
        }
    }

    internal fun buildSmartNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = PendingIntent.getService(
            this,
            REQ_TOGGLE_MUTE,
            Intent(this, CameraService::class.java).apply { action = ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cameraIntent = PendingIntent.getService(
            this,
            REQ_TOGGLE_CAMERA,
            Intent(this, CameraService::class.java).apply { action = ACTION_TOGGLE_CAMERA },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            REQ_USER_STOP,
            Intent(this, CameraService::class.java).apply { action = ACTION_USER_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = if (currentCameraDisabled) "Audio Only" else "Live"
        val clientLabel = if (currentClients == 1) "1 client" else "$currentClients clients"
        val title = "EZ-Cam-Mic • $modeLabel ($clientLabel)"

        val camStatus = if (currentCameraDisabled) "Cam Off" else "Cam $currentResolution"
        val micStatus = if (currentAudioMuted) "Mic Muted" else "Mic Active"
        val shortText = if (currentEndpoint.isNotBlank()) {
            "$currentEndpoint • $camStatus • $micStatus"
        } else {
            "$camStatus • $micStatus"
        }

        val expandedText = buildString {
            if (currentEndpoint.isNotBlank()) {
                append("Endpoint: ").append(currentEndpoint).append("\n")
            }
            append("Video: ")
            append(if (currentCameraDisabled) "Paused (Mic-Only Standby)" else "Streaming ($currentResolution)")
            append(" • Audio: ")
            append(if (currentAudioMuted) "Muted" else "Active")
        }

        val muteActionLabel = if (currentAudioMuted) "Unmute Mic" else "Mute Mic"
        val camActionLabel = if (currentCameraDisabled) "Cam On" else "Cam Off"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, muteActionLabel, muteIntent)
            .addAction(0, camActionLabel, cameraIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        isForegroundStarted = false
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "EZ-Cam-Mic Active Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live stream endpoint, client count, and quick controls while streaming."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
