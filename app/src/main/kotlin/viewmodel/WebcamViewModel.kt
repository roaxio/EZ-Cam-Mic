package io.github.roaxio.ezcammic.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import io.github.roaxio.ezcammic.audio.AudioStreamManager
import io.github.roaxio.ezcammic.camera.CameraSensorDiscovery
import io.github.roaxio.ezcammic.camera.CameraStreamManager
import io.github.roaxio.ezcammic.camera.FpsSupport
import io.github.roaxio.ezcammic.data.SettingsPreferences
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.CameraSensorInfo
import io.github.roaxio.ezcammic.model.ConnectedClientInfo
import io.github.roaxio.ezcammic.model.ServerStats
import io.github.roaxio.ezcammic.model.StreamResolution
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.server.WebcamHttpServer
import io.github.roaxio.ezcammic.utils.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WebcamViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    val cameraManager = CameraStreamManager(application)
    val audioManager = AudioStreamManager()

    val audioLevelRms: StateFlow<Float> = audioManager.audioLevelRms

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkIps()
        }
        override fun onLost(network: Network) {
            refreshNetworkIps()
        }
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            refreshNetworkIps()
        }
    }

    private val _availableSensors = MutableStateFlow<List<CameraSensorInfo>>(CameraSensorDiscovery.getAvailableSensors(application))
    val availableSensors: StateFlow<List<CameraSensorInfo>> = _availableSensors.asStateFlow()

    // Supported FPS options per sensor and resolution: sensorId -> FpsSupport.sizeKey -> options.
    private val _supportedFpsMap = MutableStateFlow(CameraSensorDiscovery.getSupportedFpsMap(application))
    val supportedFpsMap: StateFlow<Map<String, Map<String, List<Int>>>> = _supportedFpsMap.asStateFlow()

    private val _config = MutableStateFlow(preferences.loadConfig())
    val config: StateFlow<WebcamConfig> = _config.asStateFlow()

    private val _selectedSettingsSensorId = MutableStateFlow(
        _config.value.selectedCameraId.ifEmpty {
            _availableSensors.value.firstOrNull()?.cameraId ?: "0"
        }
    )
    val selectedSettingsSensorId: StateFlow<String> = _selectedSettingsSensorId.asStateFlow()

    private val _stats = MutableStateFlow(
        ServerStats(
            port = _config.value.port,
            isHttps = _config.value.isHttpsEnabled,
            isAuthProtected = _config.value.isAuthEnabled,
            isWebStreamingEnabled = _config.value.isWebStreamingEnabled,
            isUsbStreamingEnabled = _config.value.isUsbStreamingEnabled,
            isCameraStreaming = !_config.value.isCameraDisabled
        )
    )
    val stats: StateFlow<ServerStats> = _stats.asStateFlow()


    private val _ipList = MutableStateFlow<List<NetworkUtils.NetworkIpInfo>>(emptyList())
    val ipList: StateFlow<List<NetworkUtils.NetworkIpInfo>> = _ipList.asStateFlow()

    private val _primaryIp = MutableStateFlow("127.0.0.1")
    val primaryIp: StateFlow<String> = _primaryIp.asStateFlow()

    private val _wifiIp = MutableStateFlow<String?>(null)
    val wifiIp: StateFlow<String?> = _wifiIp.asStateFlow()

    private val _usbIp = MutableStateFlow<String?>(null)
    val usbIp: StateFlow<String?> = _usbIp.asStateFlow()

    private val _isBlackoutMode = MutableStateFlow(false)
    val isBlackoutMode: StateFlow<Boolean> = _isBlackoutMode.asStateFlow()

    private val _showSettingsSheet = MutableStateFlow(false)
    val showSettingsSheet: StateFlow<Boolean> = _showSettingsSheet.asStateFlow()

    private val _showUsbDialog = MutableStateFlow(false)
    val showUsbDialog: StateFlow<Boolean> = _showUsbDialog.asStateFlow()

    private val _showClientsDialog = MutableStateFlow(false)
    val showClientsDialog: StateFlow<Boolean> = _showClientsDialog.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClientInfo>> = _connectedClients.asStateFlow()

    private val _blockedIps = MutableStateFlow<Set<String>>(emptySet())
    val blockedIps: StateFlow<Set<String>> = _blockedIps.asStateFlow()

    fun setShowClientsDialog(show: Boolean) {
        _showClientsDialog.value = show
        if (show) {
            refreshConnectedClients()
        }
    }

    fun disconnectClient(clientIdentifier: String, blockForSession: Boolean = false) {
        httpServer.disconnectClient(clientIdentifier, blockForSession)
        refreshConnectedClients()
        syncSmartNotification(force = true)
    }

    fun unblockClient(ipAddress: String) {
        httpServer.unblockClient(ipAddress)
        refreshConnectedClients()
    }

    fun refreshConnectedClients() {
        if (httpServer.isRunning) {
            _connectedClients.value = httpServer.getConnectedClients()
            _blockedIps.value = httpServer.getBlockedClients()
        } else {
            _connectedClients.value = emptyList()
            _blockedIps.value = emptySet()
        }
    }

    private var statsJob: Job? = null
    private var streamUptimeSeconds = 0L
    private val _showStreamRestartPrompt = MutableStateFlow(false)
    val showStreamRestartPrompt: StateFlow<Boolean> = _showStreamRestartPrompt.asStateFlow()

    fun setShowStreamRestartPrompt(show: Boolean) {
        _showStreamRestartPrompt.value = show
    }

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null

    private val httpServer = WebcamHttpServer(
        cameraStreamManager = cameraManager,
        audioStreamManager = audioManager,
        onRemoteControlCommand = { action, value ->
            handleRemoteCommand(action, value)
        }
    )

    init {
        val initialCfg = _config.value
        val sensors = _availableSensors.value
        // Ensure active sensor ID is valid
        if (sensors.isNotEmpty() && sensors.none { it.cameraId == initialCfg.selectedCameraId }) {
            val first = sensors.first()
            _config.update { it.copy(selectedCameraId = first.cameraId, cameraFacing = first.facing) }
            _selectedSettingsSensorId.value = first.cameraId
        }

        cameraManager.jpegQuality = initialCfg.jpegQuality
        cameraManager.isMirrored = initialCfg.isMirrored
        cameraManager.targetFps = initialCfg.targetFps
        // _isDeviceLandscape is declared below init (not yet initialized here); it starts as false.
        clampTargetFps(_config.value.selectedCameraId, _config.value.getActiveResolution(false))
        cameraManager.isCameraDisabled = initialCfg.isCameraDisabled
        audioManager.gain = initialCfg.audioGain
        audioManager.isMuted = initialCfg.isAudioMuted
        httpServer.isWebStreamingEnabled = initialCfg.isWebStreamingEnabled
        httpServer.isUsbStreamingEnabled = initialCfg.isUsbStreamingEnabled

        io.github.roaxio.ezcammic.CameraService.onToggleMuteAction = {
            toggleMuteAudio()
        }
        io.github.roaxio.ezcammic.CameraService.onToggleCameraAction = {
            toggleCamera(currentLifecycleOwner, currentSurfaceProvider)
        }
        io.github.roaxio.ezcammic.CameraService.onStopStreamAction = {
            stopStreaming()
        }

        try {
            connectivityManager?.registerNetworkCallback(android.net.NetworkRequest.Builder().build(), networkCallback)
        } catch (e: Exception) {
            // Ignored in environments where NetworkCallback is restricted
        }

        refreshNetworkIps()

        // Poll for network interfaces constantly to ensure UI reflects tethering state in real time
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                refreshNetworkIps()
            }
        }
    }

    private fun updateConfig(transform: (WebcamConfig) -> WebcamConfig) {
        _config.update { prev ->
            val updated = transform(prev)
            preferences.saveConfig(updated)
            updated
        }
    }
    fun refreshNetworkIps() {
        val list = NetworkUtils.getAllIpAddresses(getApplication())
        val newPrimaryIp = NetworkUtils.getPrimaryIpAddress(getApplication())
        val newWifiIp = list.firstOrNull { it.type == io.github.roaxio.ezcammic.utils.NetworkUtils.NetworkType.WIFI }?.ipAddress
        val newUsbIp = list.firstOrNull { it.type == io.github.roaxio.ezcammic.utils.NetworkUtils.NetworkType.USB_TETHERING }?.ipAddress
        
        _ipList.value = list
        _primaryIp.value = newPrimaryIp
        _wifiIp.value = newWifiIp
        _usbIp.value = newUsbIp
        
        httpServer.wifiIp = newWifiIp
        httpServer.usbIp = newUsbIp
        syncSmartNotification()
    }

    private var lastNotificationStateKey: String = ""

    private fun syncSmartNotification(force: Boolean = false) {
        if (!force && !_stats.value.isRunning && !httpServer.isRunning) return
        val cfg = _config.value
        val protocol = if (cfg.isHttpsEnabled) "https" else "http"
        val ip = _primaryIp.value
        val endpoint = "$protocol://$ip:${cfg.port}"
        val clients = httpServer.getConnectedClientsCount()
        val isAudioMuted = cfg.isAudioMuted
        val isCameraDisabled = cfg.isCameraDisabled
        val activeResBadge = cfg.getActiveResolution(_isDeviceLandscape.value).badge
        val stateKey = "$endpoint|$clients|$isAudioMuted|$isCameraDisabled|$activeResBadge"
        if (!force && stateKey == lastNotificationStateKey) return
        lastNotificationStateKey = stateKey
        io.github.roaxio.ezcammic.CameraService.start(
            context = getApplication(),
            endpoint = endpoint,
            connectedClients = clients,
            isAudioMuted = isAudioMuted,
            isCameraDisabled = isCameraDisabled,
            resolution = activeResBadge
        )
    }

    private val _isDeviceLandscape = MutableStateFlow(false)
    val isDeviceLandscape: StateFlow<Boolean> = _isDeviceLandscape.asStateFlow()

    private var orientationDebounceJob: Job? = null

    fun updateDeviceOrientation(
        isLandscape: Boolean,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        val previous = _isDeviceLandscape.value
        _isDeviceLandscape.value = isLandscape

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (previous != isLandscape && _stats.value.isRunning && owner != null) {
            orientationDebounceJob?.cancel()
            orientationDebounceJob = viewModelScope.launch {
                delay(300) // Debounce rotational jitter to allow orientation transition to settle
                if (isActive && _stats.value.isRunning && _isDeviceLandscape.value == isLandscape) {
                    val activeAspect = _config.value.getActiveAspectRatio(isLandscape)
                    val activeRes = _config.value.getActiveResolution(isLandscape)
                    cameraManager.bindCamera(
                        lifecycleOwner = owner,
                        cameraId = _config.value.selectedCameraId,
                        aspectRatioOption = activeAspect,
                        resolution = activeRes,
                        targetFpsSetting = _config.value.targetFps,
                        facing = _config.value.cameraFacing,
                        previewSurfaceProvider = provider,
                        isLandscape = isLandscape
                    )
                }
            }
        }
    }

    fun startStreaming(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        isLandscape: Boolean = _isDeviceLandscape.value
    ) {
        _isDeviceLandscape.value = isLandscape
        currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) {
            currentSurfaceProvider = surfaceProvider
        }
        val providerToUse = surfaceProvider ?: currentSurfaceProvider
        val currentCfg = _config.value
        val activeAspect = currentCfg.getActiveAspectRatio(isLandscape)
        val activeRes = currentCfg.getActiveResolution(isLandscape)
        refreshNetworkIps()
        syncSmartNotification(force = true)

        // 1. Initialize Camera with current orientation's active profile and selected camera sensor
        cameraManager.isCameraDisabled = currentCfg.isCameraDisabled
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            cameraId = currentCfg.selectedCameraId,
            aspectRatioOption = activeAspect,
            resolution = activeRes,
            targetFpsSetting = currentCfg.targetFps,
            facing = currentCfg.cameraFacing,
            previewSurfaceProvider = providerToUse,
            isLandscape = isLandscape
        )
        cameraManager.jpegQuality = currentCfg.jpegQuality
        cameraManager.isMirrored = currentCfg.isMirrored
        cameraManager.lockHorizontalStream = currentCfg.lockHorizontalStream
        cameraManager.setZoom(currentCfg.zoomLevel)
        cameraManager.setTorch(currentCfg.isTorchOn)

        // 2. Initialize Audio
        audioManager.isMuted = currentCfg.isAudioMuted
        audioManager.gain = currentCfg.audioGain
        audioManager.start(currentCfg.audioSampleRate)

        // 3. Start HTTP Server
        httpServer.start(
            portNumber = currentCfg.port,
            useHttps = currentCfg.isHttpsEnabled,
            isAuth = currentCfg.isAuthEnabled,
            username = currentCfg.authUsername,
            password = currentCfg.authPassword,
            webEnabled = currentCfg.isWebStreamingEnabled,
            usbEnabled = currentCfg.isUsbStreamingEnabled
        )

        streamUptimeSeconds = 0L
        _stats.update {
            it.copy(
                isRunning = true,
                port = currentCfg.port,
                isHttps = currentCfg.isHttpsEnabled,
                isAuthProtected = currentCfg.isAuthEnabled,
                isWebStreamingEnabled = currentCfg.isWebStreamingEnabled,
                isUsbStreamingEnabled = currentCfg.isUsbStreamingEnabled,
                isCameraStreaming = !currentCfg.isCameraDisabled
            )
        }

        // 4. Start periodic stats monitor
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive && httpServer.isRunning) {
                delay(1000)
                streamUptimeSeconds++
                _stats.update {
                    it.copy(
                        isRunning = true,
                        port = currentCfg.port,
                        isHttps = currentCfg.isHttpsEnabled,
                        isAuthProtected = currentCfg.isAuthEnabled,
                        isWebStreamingEnabled = _config.value.isWebStreamingEnabled,
                        isUsbStreamingEnabled = _config.value.isUsbStreamingEnabled,
                        isCameraStreaming = !_config.value.isCameraDisabled,
                        connectedClients = httpServer.getConnectedClientsCount(),
                        currentFps = cameraManager.currentFps.value,
                        bitrateKbps = cameraManager.bitrateKbps.value,
                        audioLevelRms = audioManager.audioLevelRms.value,
                        uptimeSeconds = streamUptimeSeconds,
                        activeResolution = cameraManager.activeResolution.value
                    )
                }
                syncSmartNotification()
                if (_showClientsDialog.value) {
                    refreshConnectedClients()
                }
            }
        }
    }

    fun restartStreaming(
        lifecycleOwner: LifecycleOwner? = currentLifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = currentSurfaceProvider
    ) {
        val owner = lifecycleOwner ?: currentLifecycleOwner ?: return
        val provider = surfaceProvider ?: currentSurfaceProvider
        stopStreaming()
        startStreaming(owner, provider, _isDeviceLandscape.value)
    }

    fun stopStreaming() {
        lastNotificationStateKey = ""
        io.github.roaxio.ezcammic.CameraService.stop(getApplication())
        statsJob?.cancel()
        statsJob = null
        httpServer.stop()
        audioManager.stop()
        cameraManager.unbind()
        currentLifecycleOwner = null
        _connectedClients.value = emptyList()
        _blockedIps.value = emptySet()
        _showClientsDialog.value = false
        _stats.update { it.copy(isRunning = false, connectedClients = 0, currentFps = 0.0, bitrateKbps = 0.0) }
    }

    fun toggleStreaming(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider? = null) {
        if (httpServer.isRunning) {
            stopStreaming()
        } else {
            startStreaming(lifecycleOwner, surfaceProvider)
        }
    }

    fun selectCameraSensor(
        sensorId: String,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        val sensor = _availableSensors.value.find { it.cameraId == sensorId } ?: return
        updateConfig {
            it.copy(
                selectedCameraId = sensor.cameraId,
                cameraFacing = sensor.facing
            )
        }
        _selectedSettingsSensorId.value = sensorId
        clampTargetFps(sensor.cameraId, _config.value.getActiveResolution(_isDeviceLandscape.value, sensor.cameraId))

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null) {
            val isLand = _isDeviceLandscape.value
            val activeAspect = _config.value.getActiveAspectRatio(isLand, sensor.cameraId)
            val activeRes = _config.value.getActiveResolution(isLand, sensor.cameraId)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = sensor.cameraId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = sensor.facing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun cycleNextCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider? = null) {
        val sensors = _availableSensors.value
        if (sensors.isEmpty()) return

        val currentIndex = sensors.indexOfFirst { it.cameraId == _config.value.selectedCameraId }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % sensors.size else 0
        val nextSensor = sensors[nextIndex]

        selectCameraSensor(nextSensor.cameraId, lifecycleOwner, surfaceProvider)
    }

    fun setSettingsSelectedSensor(sensorId: String) {
        _selectedSettingsSensorId.value = sensorId
    }

    fun setSensorLandscapeAspectRatio(
        sensorId: String,
        aspectRatio: AspectRatioOption,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        if (_stats.value.isRunning) {
            _showStreamRestartPrompt.value = true
        }
        val resolutions = CameraSensorDiscovery.getResolutionsForSensorAndAspectRatio(
            getApplication(),
            sensorId,
            aspectRatio
        )
        val defaultRes = resolutions.find { it.badge == "1080p" } ?: resolutions.firstOrNull() ?: StreamResolution.RES_1080P

        updateConfig { prev ->
            val prevProfile = prev.getProfileForSensor(sensorId)
            val updatedProfile = prevProfile.copy(
                landscapeAspectRatio = aspectRatio,
                landscapeResolution = defaultRes
            )
            val updatedProfiles = prev.sensorProfiles.toMutableMap()
            updatedProfiles[sensorId] = updatedProfile
            if (prev.selectedCameraId == sensorId) {
                prev.copy(
                    sensorProfiles = updatedProfiles,
                    landscapeAspectRatio = aspectRatio,
                    landscapeResolution = defaultRes
                )
            } else {
                prev.copy(sensorProfiles = updatedProfiles)
            }
        }
        clampTargetFps(sensorId, defaultRes)

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null && _config.value.selectedCameraId == sensorId) {
            val isLand = _isDeviceLandscape.value
            val activeAspect = _config.value.getActiveAspectRatio(isLand, sensorId)
            val activeRes = _config.value.getActiveResolution(isLand, sensorId)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = sensorId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun setSensorLandscapeResolution(
        sensorId: String,
        resolution: StreamResolution,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        if (_stats.value.isRunning) {
            _showStreamRestartPrompt.value = true
        }
        if (lifecycleOwner != null) currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) currentSurfaceProvider = surfaceProvider

        updateConfig { prev ->
            val prevProfile = prev.getProfileForSensor(sensorId)
            val updatedProfile = prevProfile.copy(landscapeResolution = resolution)
            val updatedProfiles = prev.sensorProfiles.toMutableMap()
            updatedProfiles[sensorId] = updatedProfile
            if (prev.selectedCameraId == sensorId) {
                prev.copy(sensorProfiles = updatedProfiles, landscapeResolution = resolution)
            } else {
                prev.copy(sensorProfiles = updatedProfiles)
            }
        }
        clampTargetFps(sensorId, resolution)

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null && _config.value.selectedCameraId == sensorId) {
            val isLand = _isDeviceLandscape.value
            val isHoriz = isLand || _config.value.lockHorizontalStream
            val activeAspect = _config.value.getActiveAspectRatio(isLand, sensorId)
            val activeRes = if (isHoriz) resolution else _config.value.getActiveResolution(isLand, sensorId)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = sensorId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun rebindActiveCamera(
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null,
        isLandscape: Boolean = _isDeviceLandscape.value
    ) {
        if (!_stats.value.isRunning) return
        if (lifecycleOwner != null) currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) currentSurfaceProvider = surfaceProvider

        val owner = lifecycleOwner ?: currentLifecycleOwner ?: return
        val provider = surfaceProvider ?: currentSurfaceProvider
        val sensorId = _config.value.selectedCameraId
        val activeAspect = _config.value.getActiveAspectRatio(isLandscape, sensorId)
        val activeRes = _config.value.getActiveResolution(isLandscape, sensorId)
        cameraManager.bindCamera(
            lifecycleOwner = owner,
            cameraId = sensorId,
            aspectRatioOption = activeAspect,
            resolution = activeRes,
            targetFpsSetting = _config.value.targetFps,
            facing = _config.value.cameraFacing,
            previewSurfaceProvider = provider,
            isLandscape = isLandscape
        )
    }

    fun setSensorPortraitAspectRatio(
        sensorId: String,
        aspectRatio: AspectRatioOption,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        if (_stats.value.isRunning) {
            _showStreamRestartPrompt.value = true
        }
        if (lifecycleOwner != null) currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) currentSurfaceProvider = surfaceProvider

        val resolutions = CameraSensorDiscovery.getResolutionsForSensorAndAspectRatio(
            getApplication(),
            sensorId,
            aspectRatio
        )
        val defaultRes = resolutions.find { it.badge == "1080p" } ?: resolutions.firstOrNull() ?: StreamResolution.RES_1080P_9_16

        updateConfig { prev ->
            val prevProfile = prev.getProfileForSensor(sensorId)
            val updatedProfile = prevProfile.copy(
                portraitAspectRatio = aspectRatio,
                portraitResolution = defaultRes
            )
            val updatedProfiles = prev.sensorProfiles.toMutableMap()
            updatedProfiles[sensorId] = updatedProfile
            if (prev.selectedCameraId == sensorId) {
                prev.copy(
                    sensorProfiles = updatedProfiles,
                    portraitAspectRatio = aspectRatio,
                    portraitResolution = defaultRes
                )
            } else {
                prev.copy(sensorProfiles = updatedProfiles)
            }
        }
        clampTargetFps(sensorId, defaultRes)

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null && _config.value.selectedCameraId == sensorId) {
            val isLand = _isDeviceLandscape.value
            val activeAspect = _config.value.getActiveAspectRatio(isLand, sensorId)
            val activeRes = _config.value.getActiveResolution(isLand, sensorId)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = sensorId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun setSensorPortraitResolution(
        sensorId: String,
        resolution: StreamResolution,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        if (_stats.value.isRunning) {
            _showStreamRestartPrompt.value = true
        }
        if (lifecycleOwner != null) currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) currentSurfaceProvider = surfaceProvider

        updateConfig { prev ->
            val prevProfile = prev.getProfileForSensor(sensorId)
            val updatedProfile = prevProfile.copy(portraitResolution = resolution)
            val updatedProfiles = prev.sensorProfiles.toMutableMap()
            updatedProfiles[sensorId] = updatedProfile
            if (prev.selectedCameraId == sensorId) {
                prev.copy(sensorProfiles = updatedProfiles, portraitResolution = resolution)
            } else {
                prev.copy(sensorProfiles = updatedProfiles)
            }
        }
        clampTargetFps(sensorId, resolution)

        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null && _config.value.selectedCameraId == sensorId) {
            val isLand = _isDeviceLandscape.value
            val isHoriz = isLand || _config.value.lockHorizontalStream
            val activeAspect = _config.value.getActiveAspectRatio(isLand, sensorId)
            val activeRes = if (!isHoriz) resolution else _config.value.getActiveResolution(isLand, sensorId)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = sensorId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun setLandscapeAspectRatio(
        aspectRatio: AspectRatioOption,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        setSensorLandscapeAspectRatio(_config.value.selectedCameraId, aspectRatio, lifecycleOwner, surfaceProvider)
    }

    fun setLandscapeResolution(
        resolution: StreamResolution,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        setSensorLandscapeResolution(_config.value.selectedCameraId, resolution, lifecycleOwner, surfaceProvider)
    }

    fun setPortraitAspectRatio(
        aspectRatio: AspectRatioOption,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        setSensorPortraitAspectRatio(_config.value.selectedCameraId, aspectRatio, lifecycleOwner, surfaceProvider)
    }

    fun setPortraitResolution(
        resolution: StreamResolution,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        setSensorPortraitResolution(_config.value.selectedCameraId, resolution, lifecycleOwner, surfaceProvider)
    }

    fun setTargetFps(fps: Int) {
        val changed = cameraManager.targetFps != fps
        updateConfig { it.copy(targetFps = fps) }
        cameraManager.targetFps = fps
        // The camera FPS range is part of the bind (like resolution), so re-bind a live stream to apply it.
        if (changed) rebindActiveCamera()
    }

    /** FPS options the given sensor supports at the given resolution. */
    fun fpsOptionsFor(sensorId: String, resolution: StreamResolution): List<Int> =
        FpsSupport.lookup(_supportedFpsMap.value, sensorId, resolution.width, resolution.height)

    /**
     * Moves the saved FPS to the nearest value supported by [sensorId] at [resolution] if it is not
     * supported. Only updates the setting; callers that change sensor/resolution re-bind afterwards.
     */
    private fun clampTargetFps(sensorId: String, resolution: StreamResolution) {
        val options = fpsOptionsFor(sensorId, resolution)
        val current = _config.value.targetFps
        if (current in options) return
        val clamped = FpsSupport.nearest(current, options)
        updateConfig { it.copy(targetFps = clamped) }
        cameraManager.targetFps = clamped
    }

    fun setJpegQuality(quality: Int) {
        val clamped = quality.coerceIn(30, 95)
        updateConfig { it.copy(jpegQuality = clamped) }
        cameraManager.jpegQuality = clamped
    }

    fun setAudioGain(gain: Float) {
        updateConfig { it.copy(audioGain = gain) }
        audioManager.gain = gain
    }

    fun setAudioSampleRate(sampleRate: Int) {
        updateConfig { it.copy(audioSampleRate = sampleRate) }
        audioManager.setSamplingRate(sampleRate)
    }

    fun setPort(newPort: Int, lifecycleOwner: LifecycleOwner? = null, surfaceProvider: Preview.SurfaceProvider? = null) {
        if (newPort in 1024..65535 && newPort != _config.value.port) {
            updateConfig { it.copy(port = newPort) }
            val owner = lifecycleOwner ?: currentLifecycleOwner
            val provider = surfaceProvider ?: currentSurfaceProvider
            if (_stats.value.isRunning && owner != null) {
                restartStreaming(owner, provider)
            }
        }
    }

    fun setHttpsEnabled(enabled: Boolean, lifecycleOwner: LifecycleOwner? = null, surfaceProvider: Preview.SurfaceProvider? = null) {
        if (_config.value.isHttpsEnabled != enabled) {
            updateConfig { it.copy(isHttpsEnabled = enabled) }
            val owner = lifecycleOwner ?: currentLifecycleOwner
            val provider = surfaceProvider ?: currentSurfaceProvider
            if (_stats.value.isRunning && owner != null) {
                restartStreaming(owner, provider)
            }
        }
    }

    fun setAuthEnabled(enabled: Boolean, lifecycleOwner: LifecycleOwner? = null, surfaceProvider: Preview.SurfaceProvider? = null) {
        if (_config.value.isAuthEnabled != enabled) {
            updateConfig { it.copy(isAuthEnabled = enabled) }
            httpServer.updateAuthEnabled(enabled)
            _stats.update { it.copy(isAuthProtected = enabled) }
        }
    }

    fun setAuthCredentials(user: String, pass: String, lifecycleOwner: LifecycleOwner? = null, surfaceProvider: Preview.SurfaceProvider? = null) {
        val cleanUser = user.trim().ifEmpty { "admin" }
        val cleanPass = pass.ifEmpty { "admin123" }
        updateConfig { it.copy(authUsername = cleanUser, authPassword = cleanPass) }
        httpServer.updateAuthCredentials(cleanUser, cleanPass)
    }

    fun setWebStreamingEnabled(enabled: Boolean) {
        updateConfig { it.copy(isWebStreamingEnabled = enabled) }
        httpServer.isWebStreamingEnabled = enabled
        _stats.update { it.copy(isWebStreamingEnabled = enabled) }
    }

    fun setUsbStreamingEnabled(enabled: Boolean) {
        updateConfig { it.copy(isUsbStreamingEnabled = enabled) }
        httpServer.isUsbStreamingEnabled = enabled
        _stats.update { it.copy(isUsbStreamingEnabled = enabled) }
    }

    fun setAutoStartStreaming(enabled: Boolean) {
        updateConfig { it.copy(autoStartStreaming = enabled) }
    }

    fun setLockHorizontalStream(
        locked: Boolean,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        updateConfig { it.copy(lockHorizontalStream = locked) }
        cameraManager.lockHorizontalStream = locked
        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null) {
            val isLand = _isDeviceLandscape.value
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = _config.value.selectedCameraId,
                aspectRatioOption = _config.value.getActiveAspectRatio(isLand),
                resolution = _config.value.getActiveResolution(isLand),
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
    }

    fun toggleTorch() {
        val newState = !_config.value.isTorchOn
        updateConfig { it.copy(isTorchOn = newState) }
        cameraManager.setTorch(newState)
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner? = null, surfaceProvider: Preview.SurfaceProvider? = null) {
        val newState = !_config.value.isCameraDisabled
        setCameraDisabled(newState, lifecycleOwner, surfaceProvider)
    }

    fun setCameraDisabled(
        disabled: Boolean,
        lifecycleOwner: LifecycleOwner? = null,
        surfaceProvider: Preview.SurfaceProvider? = null
    ) {
        updateConfig { it.copy(isCameraDisabled = disabled) }
        cameraManager.isCameraDisabled = disabled
        _stats.update { it.copy(isCameraStreaming = !disabled) }
        val owner = lifecycleOwner ?: currentLifecycleOwner
        val provider = surfaceProvider ?: currentSurfaceProvider
        if (_stats.value.isRunning && owner != null && !disabled) {
            val isLand = _isDeviceLandscape.value
            val activeAspect = _config.value.getActiveAspectRatio(isLand)
            val activeRes = _config.value.getActiveResolution(isLand)
            cameraManager.bindCamera(
                lifecycleOwner = owner,
                cameraId = _config.value.selectedCameraId,
                aspectRatioOption = activeAspect,
                resolution = activeRes,
                targetFpsSetting = _config.value.targetFps,
                facing = _config.value.cameraFacing,
                previewSurfaceProvider = provider,
                isLandscape = isLand
            )
        }
        syncSmartNotification()
    }

    fun toggleMuteAudio() {
        val newState = !_config.value.isAudioMuted
        updateConfig { it.copy(isAudioMuted = newState) }
        audioManager.isMuted = newState
        syncSmartNotification()
    }

    fun toggleMirror() {
        val newState = !_config.value.isMirrored
        updateConfig { it.copy(isMirrored = newState) }
        cameraManager.isMirrored = newState
    }

    fun toggleGrid() {
        val newState = !_config.value.isGridVisible
        updateConfig { it.copy(isGridVisible = newState) }
    }

    fun setZoomLevel(zoom: Float) {
        val clamped = zoom.coerceIn(1.0f, 5.0f)
        updateConfig { it.copy(zoomLevel = clamped) }
        cameraManager.setZoom(clamped)
    }

    fun setKeepScreenOn(keep: Boolean) {
        updateConfig { it.copy(keepScreenOn = keep) }
    }

    fun setBlackoutMode(enabled: Boolean) {
        _isBlackoutMode.value = enabled
        if (enabled) {
            // Rule 22.2: Decouple rendering sink at the source during blackout/dim mode to stop GPU shaders
            cameraManager.setPreviewSurfaceProvider(null)
        } else {
            currentSurfaceProvider?.let { cameraManager.setPreviewSurfaceProvider(it) }
        }
    }

    fun updatePreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        if (provider != null) {
            currentSurfaceProvider = provider
        }
        cameraManager.setPreviewSurfaceProvider(provider)
    }

    fun setShowSettingsSheet(show: Boolean) {
        _showSettingsSheet.value = show
    }

    fun setShowUsbDialog(show: Boolean) {
        _showUsbDialog.value = show
    }

    private fun handleRemoteCommand(action: String, value: String) {
        viewModelScope.launch {
            when (action.lowercase()) {
                "torch" -> {
                    val enable = if (value.isBlank() || value == "toggle") !(_config.value.isTorchOn) else (value == "true" || value == "1" || value == "on")
                    updateConfig { it.copy(isTorchOn = enable) }
                    cameraManager.setTorch(enable)
                }
                "camera" -> {
                    currentLifecycleOwner?.let { owner ->
                        cycleNextCamera(owner, currentSurfaceProvider)
                    }
                }
                "cam" -> {
                    val disable = if (value.isBlank() || value == "toggle") !(_config.value.isCameraDisabled) else (value == "false" || value == "0" || value == "off")
                    setCameraDisabled(disable, currentLifecycleOwner, currentSurfaceProvider)
                }
                "mute" -> {
                    val mute = if (value.isBlank() || value == "toggle") !(_config.value.isAudioMuted) else (value == "true" || value == "1" || value == "on")
                    updateConfig { it.copy(isAudioMuted = mute) }
                    audioManager.isMuted = mute
                    syncSmartNotification()
                }
                "quality" -> {
                    value.toIntOrNull()?.let { setJpegQuality(it) }
                }
                "fps" -> {
                    // Clamp to the nearest value the active sensor + resolution supports.
                    value.toIntOrNull()?.takeIf { it > 0 }?.let { requested ->
                        val cfg = _config.value
                        val options = fpsOptionsFor(cfg.selectedCameraId, cfg.getActiveResolution(_isDeviceLandscape.value))
                        setTargetFps(FpsSupport.nearest(requested, options))
                    }
                }
                "zoom" -> {
                    value.toFloatOrNull()?.let { setZoomLevel(it) }
                }
            }
        }
    }

    override fun onCleared() {
        io.github.roaxio.ezcammic.CameraService.onToggleMuteAction = null
        io.github.roaxio.ezcammic.CameraService.onToggleCameraAction = null
        io.github.roaxio.ezcammic.CameraService.onStopStreamAction = null
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignored
        }
        stopStreaming()
        cameraManager.release()
        audioManager.release()
    }
}
