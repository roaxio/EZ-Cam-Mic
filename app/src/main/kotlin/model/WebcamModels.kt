package io.github.roaxio.ezcammic.model

enum class CameraFacing(val label: String) {
    BACK("Back Camera"),
    FRONT("Front Camera (Selfie)")
}

enum class StreamOrientation(val label: String) {
    LANDSCAPE("Horizontal / Landscape"),
    PORTRAIT("Vertical / Portrait")
}

data class AspectRatioOption(
    val id: String, // e.g. "16:9", "4:3", "1:1", "9:16", "3:4"
    val label: String,
    val orientation: StreamOrientation,
    val aspectX: Int,
    val aspectY: Int,
    val ratioValue: Float
) {
    companion object {
        val RATIO_16_9 = AspectRatioOption("16:9", "16:9 (Widescreen)", StreamOrientation.LANDSCAPE, 16, 9, 16f / 9f)
        val RATIO_4_3 = AspectRatioOption("4:3", "4:3 (Standard Photo)", StreamOrientation.LANDSCAPE, 4, 3, 4f / 3f)
        val RATIO_1_1 = AspectRatioOption("1:1", "1:1 (Square)", StreamOrientation.LANDSCAPE, 1, 1, 1f)

        val RATIO_9_16 = AspectRatioOption("9:16", "9:16 (Portrait / Reel)", StreamOrientation.PORTRAIT, 9, 16, 9f / 16f)
        val RATIO_3_4 = AspectRatioOption("3:4", "3:4 (Portrait Standard)", StreamOrientation.PORTRAIT, 3, 4, 3f / 4f)

        val DEFAULT_LANDSCAPE = listOf(RATIO_16_9, RATIO_4_3, RATIO_1_1)
        val DEFAULT_PORTRAIT = listOf(RATIO_9_16, RATIO_3_4)
        val ALL_DEFAULTS = DEFAULT_LANDSCAPE + DEFAULT_PORTRAIT

        fun findById(id: String): AspectRatioOption {
            return ALL_DEFAULTS.find { it.id.equals(id, ignoreCase = true) } ?: RATIO_16_9
        }
    }
}

data class StreamResolution(
    val label: String,
    val width: Int,
    val height: Int,
    val badge: String,
    val aspectRatioId: String = "16:9"
) {
    val name: String
        get() = "${aspectRatioId}_${width}x${height}"

    companion object {
        // Landscape 16:9
        val RES_4K_16_9 = StreamResolution("4K Ultra HD (2160p)", 3840, 2160, "4K", "16:9")
        val RES_1440P_16_9 = StreamResolution("2K Quad HD (1440p)", 2560, 1440, "1440p", "16:9")
        val RES_1080P = StreamResolution("Full HD (1080p)", 1920, 1080, "1080p", "16:9")
        val RES_720P = StreamResolution("HD (720p)", 1280, 720, "720p", "16:9")
        val RES_480P = StreamResolution("SD (480p)", 854, 480, "480p", "16:9")

        // Landscape 4:3
        val RES_4000x3000_4_3 = StreamResolution("12MP Ultra (3000p)", 4000, 3000, "12MP", "4:3")
        val RES_1920x1440_4_3 = StreamResolution("Full Standard (1440p)", 1920, 1440, "1440p", "4:3")
        val RES_1440x1080_4_3 = StreamResolution("HD Standard (1080p)", 1440, 1080, "1080p", "4:3")
        val RES_1280x960_4_3 = StreamResolution("HD (960p)", 1280, 960, "960p", "4:3")
        val RES_640x480_4_3 = StreamResolution("VGA (480p)", 640, 480, "480p", "4:3")

        // Landscape 1:1
        val RES_1080x1080_1_1 = StreamResolution("Square HD (1080p)", 1080, 1080, "1080p", "1:1")
        val RES_720x720_1_1 = StreamResolution("Square (720p)", 720, 720, "720p", "1:1")
        val RES_480x480_1_1 = StreamResolution("Square (480p)", 480, 480, "480p", "1:1")

        // Portrait 9:16
        val RES_4K_9_16 = StreamResolution("4K Portrait (2160x3840)", 2160, 3840, "4K", "9:16")
        val RES_1440P_9_16 = StreamResolution("2K Portrait (1440x2560)", 1440, 2560, "1440p", "9:16")
        val RES_1080P_9_16 = StreamResolution("Full HD Portrait (1080x1920)", 1080, 1920, "1080p", "9:16")
        val RES_720P_9_16 = StreamResolution("HD Portrait (720x1280)", 720, 1280, "720p", "9:16")
        val RES_480P_9_16 = StreamResolution("SD Portrait (480x854)", 480, 854, "480p", "9:16")

        // Portrait 3:4
        val RES_3000x4000_3_4 = StreamResolution("12MP Portrait (3000x4000)", 3000, 4000, "12MP", "3:4")
        val RES_1440x1920_3_4 = StreamResolution("Full Standard Portrait (1440x1920)", 1440, 1920, "1440p", "3:4")
        val RES_1080x1440_3_4 = StreamResolution("HD Standard Portrait (1080x1440)", 1080, 1440, "1080p", "3:4")
        val RES_960x1280_3_4 = StreamResolution("HD Portrait (960x1280)", 960, 1280, "960p", "3:4")
        val RES_480x640_3_4 = StreamResolution("VGA Portrait (480x640)", 480, 640, "480p", "3:4")

        val DEFAULT_PRESETS = listOf(
            RES_4K_16_9, RES_1440P_16_9, RES_1080P, RES_720P, RES_480P,
            RES_4000x3000_4_3, RES_1920x1440_4_3, RES_1440x1080_4_3, RES_1280x960_4_3, RES_640x480_4_3,
            RES_1080x1080_1_1, RES_720x720_1_1, RES_480x480_1_1,
            RES_4K_9_16, RES_1440P_9_16, RES_1080P_9_16, RES_720P_9_16, RES_480P_9_16,
            RES_3000x4000_3_4, RES_1440x1920_3_4, RES_1080x1440_3_4, RES_960x1280_3_4, RES_480x640_3_4
        )

        fun fromDimensions(w: Int, h: Int, aspectRatioId: String = "16:9"): StreamResolution {
            return DEFAULT_PRESETS.find { it.width == w && it.height == h }
                ?: StreamResolution("${w}x${h}", w, h, "${minOf(w, h)}p", aspectRatioId)
        }
    }
}

data class ServerStats(
    val isRunning: Boolean = false,
    val port: Int = 4747,
    val isHttps: Boolean = true,
    val isAuthProtected: Boolean = false,
    val isWebStreamingEnabled: Boolean = true,
    val isUsbStreamingEnabled: Boolean = true,
    val connectedClients: Int = 0,
    val currentFps: Double = 0.0,
    val bitrateKbps: Double = 0.0,
    val totalBytesSent: Long = 0L,
    val audioLevelRms: Float = 0f,
    val uptimeSeconds: Long = 0L,
    val activeResolution: String = "1920x1080",
    val isCameraStreaming: Boolean = true
)

data class CameraSensorInfo(
    val cameraId: String,
    val facing: CameraFacing,
    val name: String,
    val focalLengthMm: Float = 0f,
    val maxResolutionBadge: String = "1080p",
    val sensorOrientation: Int = 90
) {
    val displayName: String
        get() = name
}

data class SensorProfileConfig(
    val landscapeAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val landscapeResolution: StreamResolution = StreamResolution.RES_1080P,
    val portraitAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_9_16,
    val portraitResolution: StreamResolution = StreamResolution.RES_1080P_9_16
)

data class WebcamConfig(
    val selectedCameraId: String = "0",
    val sensorProfiles: Map<String, SensorProfileConfig> = emptyMap(),
    val landscapeAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val landscapeResolution: StreamResolution = StreamResolution.RES_1080P,
    val portraitAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_9_16,
    val portraitResolution: StreamResolution = StreamResolution.RES_1080P_9_16,
    val targetFps: Int = 30, // max FPS requested from the camera; see camera/FpsSupport
    val jpegQuality: Int = 80,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val isTorchOn: Boolean = false,
    val isCameraDisabled: Boolean = false,
    val isAudioMuted: Boolean = false,
    val audioGain: Float = 1.0f,
    val audioSampleRate: Int = 44100,
    val isMirrored: Boolean = false,
    val isGridVisible: Boolean = false,
    val port: Int = 4747,
    val keepScreenOn: Boolean = true,
    val zoomLevel: Float = 1.0f,
    val isHttpsEnabled: Boolean = true,
    val isAuthEnabled: Boolean = false,
    val authUsername: String = "admin",
    val authPassword: String = "admin123",
    val isWebStreamingEnabled: Boolean = true,
    val isUsbStreamingEnabled: Boolean = true,
    val autoStartStreaming: Boolean = false,
    val lockHorizontalStream: Boolean = false
) {
    fun getProfileForSensor(cameraId: String): SensorProfileConfig {
        return sensorProfiles[cameraId] ?: SensorProfileConfig(
            landscapeAspectRatio = landscapeAspectRatio,
            landscapeResolution = landscapeResolution,
            portraitAspectRatio = portraitAspectRatio,
            portraitResolution = portraitResolution
        )
    }

    fun getActiveAspectRatio(isLandscape: Boolean, cameraId: String = selectedCameraId): AspectRatioOption {
        val profile = getProfileForSensor(cameraId)
        return if (lockHorizontalStream || isLandscape) profile.landscapeAspectRatio else profile.portraitAspectRatio
    }

    fun getActiveResolution(isLandscape: Boolean, cameraId: String = selectedCameraId): StreamResolution {
        val profile = getProfileForSensor(cameraId)
        return if (lockHorizontalStream || isLandscape) profile.landscapeResolution else profile.portraitResolution
    }

    // Compatibility accessors
    val aspectRatio: AspectRatioOption
        get() = getProfileForSensor(selectedCameraId).landscapeAspectRatio

    val resolution: StreamResolution
        get() = getProfileForSensor(selectedCameraId).landscapeResolution
}

data class ConnectedClientInfo(
    val id: String,
    val ipAddress: String,
    val port: Int,
    val isUsb: Boolean,
    val userAgent: String?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val connectedSinceEpochMs: Long
)


