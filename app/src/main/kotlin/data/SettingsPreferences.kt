package io.github.roaxio.ezcammic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.roaxio.ezcammic.camera.FpsSupport
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.SensorProfileConfig
import io.github.roaxio.ezcammic.model.StreamResolution
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.utils.KeystoreSecretsHelper

class SettingsPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ezcammic_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_CAMERA_ID = "pref_selected_camera_id"
        private const val KEY_KNOWN_SENSOR_IDS = "pref_known_sensor_ids"

        private const val KEY_LANDSCAPE_ASPECT_RATIO = "pref_landscape_aspect_ratio"
        private const val KEY_LANDSCAPE_RES_W = "pref_landscape_resolution_w"
        private const val KEY_LANDSCAPE_RES_H = "pref_landscape_resolution_h"
        private const val KEY_LANDSCAPE_RES_LABEL = "pref_landscape_resolution_label"
        private const val KEY_LANDSCAPE_RES_BADGE = "pref_landscape_resolution_badge"

        private const val KEY_PORTRAIT_ASPECT_RATIO = "pref_portrait_aspect_ratio"
        private const val KEY_PORTRAIT_RES_W = "pref_portrait_resolution_w"
        private const val KEY_PORTRAIT_RES_H = "pref_portrait_resolution_h"
        private const val KEY_PORTRAIT_RES_LABEL = "pref_portrait_resolution_label"
        private const val KEY_PORTRAIT_RES_BADGE = "pref_portrait_resolution_badge"

        private const val KEY_TARGET_FPS = "pref_target_fps"
        private const val KEY_JPEG_QUALITY = "pref_jpeg_quality"
        private const val KEY_CAMERA_FACING = "pref_camera_facing"
        private const val KEY_TORCH_ON = "pref_torch_on"
        private const val KEY_CAMERA_DISABLED = "pref_camera_disabled"
        private const val KEY_AUDIO_MUTED = "pref_audio_muted"
        private const val KEY_AUDIO_GAIN = "pref_audio_gain"
        private const val KEY_AUDIO_SAMPLE_RATE = "pref_audio_sample_rate"
        private const val KEY_MIRRORED = "pref_mirrored"
        private const val KEY_GRID_VISIBLE = "pref_grid_visible"
        private const val KEY_PORT = "pref_port"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_ZOOM_LEVEL = "pref_zoom_level"
        private const val KEY_HTTPS_ENABLED = "pref_https_enabled"
        private const val KEY_AUTH_ENABLED = "pref_auth_enabled"
        private const val KEY_AUTH_USERNAME = "pref_auth_username"
        private const val KEY_AUTH_PASSWORD = "pref_auth_password"
        private const val KEY_WEB_STREAMING = "pref_web_streaming"
        private const val KEY_USB_STREAMING = "pref_usb_streaming"
        private const val KEY_AUTO_START_STREAMING = "pref_auto_start_streaming"
        private const val KEY_LOCK_HORIZONTAL_STREAM = "pref_lock_horizontal_stream"
    }

    fun loadConfig(): WebcamConfig {
        val selectedCameraId = prefs.getString(KEY_SELECTED_CAMERA_ID, "0") ?: "0"

        val landscapeAspectId = prefs.getString(KEY_LANDSCAPE_ASPECT_RATIO, "16:9") ?: "16:9"
        val landscapeAspect = AspectRatioOption.findById(landscapeAspectId)
        val landscapeResW = prefs.getInt(KEY_LANDSCAPE_RES_W, 1920)
        val landscapeResH = prefs.getInt(KEY_LANDSCAPE_RES_H, 1080)
        val landscapeResLabel = prefs.getString(KEY_LANDSCAPE_RES_LABEL, "Full HD (1080p)") ?: "Full HD (1080p)"
        val landscapeResBadge = prefs.getString(KEY_LANDSCAPE_RES_BADGE, "1080p") ?: "1080p"
        val landscapeRes = StreamResolution(
            label = landscapeResLabel,
            width = landscapeResW,
            height = landscapeResH,
            badge = landscapeResBadge,
            aspectRatioId = landscapeAspect.id
        )

        val portraitAspectId = prefs.getString(KEY_PORTRAIT_ASPECT_RATIO, "9:16") ?: "9:16"
        val portraitAspect = AspectRatioOption.findById(portraitAspectId)
        val portraitResW = prefs.getInt(KEY_PORTRAIT_RES_W, 1080)
        val portraitResH = prefs.getInt(KEY_PORTRAIT_RES_H, 1920)
        val portraitResLabel = prefs.getString(KEY_PORTRAIT_RES_LABEL, "Full HD Portrait (1080x1920)") ?: "Full HD Portrait (1080x1920)"
        val portraitResBadge = prefs.getString(KEY_PORTRAIT_RES_BADGE, "1080p") ?: "1080p"
        val portraitRes = StreamResolution(
            label = portraitResLabel,
            width = portraitResW,
            height = portraitResH,
            badge = portraitResBadge,
            aspectRatioId = portraitAspect.id
        )

        val knownSensorIds = prefs.getStringSet(KEY_KNOWN_SENSOR_IDS, emptySet()) ?: emptySet()
        val sensorProfiles = mutableMapOf<String, SensorProfileConfig>()

        for (id in knownSensorIds) {
            val sLArId = prefs.getString("pref_sensor_${id}_landscape_ar", landscapeAspectId) ?: landscapeAspectId
            val sLAr = AspectRatioOption.findById(sLArId)
            val sLW = prefs.getInt("pref_sensor_${id}_landscape_w", 1920)
            val sLH = prefs.getInt("pref_sensor_${id}_landscape_h", 1080)
            val sLLabel = prefs.getString("pref_sensor_${id}_landscape_label", "Full HD ($sLW x $sLH)") ?: "Full HD ($sLW x $sLH)"
            val sLBadge = prefs.getString("pref_sensor_${id}_landscape_badge", "1080p") ?: "1080p"
            val sLRes = StreamResolution(sLLabel, sLW, sLH, sLBadge, sLAr.id)

            val sPArId = prefs.getString("pref_sensor_${id}_portrait_ar", portraitAspectId) ?: portraitAspectId
            val sPAr = AspectRatioOption.findById(sPArId)
            val sPW = prefs.getInt("pref_sensor_${id}_portrait_w", 1080)
            val sPH = prefs.getInt("pref_sensor_${id}_portrait_h", 1920)
            val sPLabel = prefs.getString("pref_sensor_${id}_portrait_label", "Full HD Portrait ($sPW x $sPH)") ?: "Full HD Portrait ($sPW x $sPH)"
            val sPBadge = prefs.getString("pref_sensor_${id}_portrait_badge", "1080p") ?: "1080p"
            val sPRes = StreamResolution(sPLabel, sPW, sPH, sPBadge, sPAr.id)

            sensorProfiles[id] = SensorProfileConfig(
                landscapeAspectRatio = sLAr,
                landscapeResolution = sLRes,
                portraitAspectRatio = sPAr,
                portraitResolution = sPRes
            )
        }

        // Stored as a string: "30" now, or the pre-1.2.0 enum name such as "FPS_30".
        val targetFps = FpsSupport.parseStored(prefs.getString(KEY_TARGET_FPS, null))

        val facingName = prefs.getString(KEY_CAMERA_FACING, CameraFacing.BACK.name)
        val cameraFacing = runCatching { CameraFacing.valueOf(facingName ?: "") }.getOrDefault(CameraFacing.BACK)

        return WebcamConfig(
            selectedCameraId = selectedCameraId,
            sensorProfiles = sensorProfiles,
            landscapeAspectRatio = landscapeAspect,
            landscapeResolution = landscapeRes,
            portraitAspectRatio = portraitAspect,
            portraitResolution = portraitRes,
            targetFps = targetFps,
            jpegQuality = prefs.getInt(KEY_JPEG_QUALITY, 80),
            cameraFacing = cameraFacing,
            isTorchOn = prefs.getBoolean(KEY_TORCH_ON, false),
            isCameraDisabled = prefs.getBoolean(KEY_CAMERA_DISABLED, false),
            isAudioMuted = prefs.getBoolean(KEY_AUDIO_MUTED, false),
            audioGain = prefs.getFloat(KEY_AUDIO_GAIN, 1.0f),
            audioSampleRate = prefs.getInt(KEY_AUDIO_SAMPLE_RATE, 44100),
            isMirrored = prefs.getBoolean(KEY_MIRRORED, false),
            isGridVisible = prefs.getBoolean(KEY_GRID_VISIBLE, false),
            port = prefs.getInt(KEY_PORT, 4747),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            zoomLevel = prefs.getFloat(KEY_ZOOM_LEVEL, 1.0f),
            isHttpsEnabled = prefs.getBoolean(KEY_HTTPS_ENABLED, true),
            isAuthEnabled = prefs.getBoolean(KEY_AUTH_ENABLED, false),
            authUsername = prefs.getString(KEY_AUTH_USERNAME, "admin") ?: "admin",
            authPassword = KeystoreSecretsHelper.decrypt(prefs.getString(KEY_AUTH_PASSWORD, "admin123") ?: "admin123"),
            isWebStreamingEnabled = prefs.getBoolean(KEY_WEB_STREAMING, true),
            isUsbStreamingEnabled = prefs.getBoolean(KEY_USB_STREAMING, true),
            autoStartStreaming = prefs.getBoolean(KEY_AUTO_START_STREAMING, false),
            lockHorizontalStream = prefs.getBoolean(KEY_LOCK_HORIZONTAL_STREAM, false)
        )
    }

    fun saveConfig(config: WebcamConfig) {
        prefs.edit {
            putString(KEY_SELECTED_CAMERA_ID, config.selectedCameraId)
                .putStringSet(KEY_KNOWN_SENSOR_IDS, config.sensorProfiles.keys)
                .putString(KEY_LANDSCAPE_ASPECT_RATIO, config.landscapeAspectRatio.id)
                .putInt(KEY_LANDSCAPE_RES_W, config.landscapeResolution.width)
                .putInt(KEY_LANDSCAPE_RES_H, config.landscapeResolution.height)
                .putString(KEY_LANDSCAPE_RES_LABEL, config.landscapeResolution.label)
                .putString(KEY_LANDSCAPE_RES_BADGE, config.landscapeResolution.badge)
                .putString(KEY_PORTRAIT_ASPECT_RATIO, config.portraitAspectRatio.id)
                .putInt(KEY_PORTRAIT_RES_W, config.portraitResolution.width)
                .putInt(KEY_PORTRAIT_RES_H, config.portraitResolution.height)
                .putString(KEY_PORTRAIT_RES_LABEL, config.portraitResolution.label)
                .putString(KEY_PORTRAIT_RES_BADGE, config.portraitResolution.badge)
                .putString(KEY_TARGET_FPS, config.targetFps.toString())
                .putInt(KEY_JPEG_QUALITY, config.jpegQuality)
                .putString(KEY_CAMERA_FACING, config.cameraFacing.name)
                .putBoolean(KEY_TORCH_ON, config.isTorchOn)
                .putBoolean(KEY_CAMERA_DISABLED, config.isCameraDisabled)
                .putBoolean(KEY_AUDIO_MUTED, config.isAudioMuted)
                .putFloat(KEY_AUDIO_GAIN, config.audioGain)
                .putInt(KEY_AUDIO_SAMPLE_RATE, config.audioSampleRate)
                .putBoolean(KEY_MIRRORED, config.isMirrored)
                .putBoolean(KEY_GRID_VISIBLE, config.isGridVisible)
                .putInt(KEY_PORT, config.port)
                .putBoolean(KEY_KEEP_SCREEN_ON, config.keepScreenOn)
                .putFloat(KEY_ZOOM_LEVEL, config.zoomLevel)
                .putBoolean(KEY_HTTPS_ENABLED, config.isHttpsEnabled)
                .putBoolean(KEY_AUTH_ENABLED, config.isAuthEnabled)
                .putString(KEY_AUTH_USERNAME, config.authUsername)
                .putString(KEY_AUTH_PASSWORD, KeystoreSecretsHelper.encrypt(config.authPassword))
                .putBoolean(KEY_WEB_STREAMING, config.isWebStreamingEnabled)
                .putBoolean(KEY_USB_STREAMING, config.isUsbStreamingEnabled)
                .putBoolean(KEY_AUTO_START_STREAMING, config.autoStartStreaming)
                .putBoolean(KEY_LOCK_HORIZONTAL_STREAM, config.lockHorizontalStream)

            // Save individual sensor profile configs
            for ((id, profile) in config.sensorProfiles) {
                putString("pref_sensor_${id}_landscape_ar", profile.landscapeAspectRatio.id)
                    .putInt("pref_sensor_${id}_landscape_w", profile.landscapeResolution.width)
                    .putInt("pref_sensor_${id}_landscape_h", profile.landscapeResolution.height)
                    .putString("pref_sensor_${id}_landscape_label", profile.landscapeResolution.label)
                    .putString("pref_sensor_${id}_landscape_badge", profile.landscapeResolution.badge)
                    .putString("pref_sensor_${id}_portrait_ar", profile.portraitAspectRatio.id)
                    .putInt("pref_sensor_${id}_portrait_w", profile.portraitResolution.width)
                    .putInt("pref_sensor_${id}_portrait_h", profile.portraitResolution.height)
                    .putString("pref_sensor_${id}_portrait_label", profile.portraitResolution.label)
                    .putString("pref_sensor_${id}_portrait_badge", profile.portraitResolution.badge)
            }
        }
    }
}
