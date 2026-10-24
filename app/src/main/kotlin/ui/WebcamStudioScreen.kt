package io.github.roaxio.ezcammic.ui

import android.content.res.Configuration
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.ezcammic.R
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.StreamOrientation
import io.github.roaxio.ezcammic.ui.components.BlackoutScreen
import io.github.roaxio.ezcammic.ui.components.CameraViewfinder
import io.github.roaxio.ezcammic.ui.components.ConnectedClientsDialog
import io.github.roaxio.ezcammic.ui.components.ConnectionCard
import io.github.roaxio.ezcammic.ui.components.ControlBar
import io.github.roaxio.ezcammic.ui.components.UsbSetupGuideDialog
import io.github.roaxio.ezcammic.ui.theme.CardBackground
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.SlateDark950
import io.github.roaxio.ezcammic.ui.theme.TextPrimary
import io.github.roaxio.ezcammic.ui.theme.TextSecondary
import io.github.roaxio.ezcammic.viewmodel.WebcamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebcamStudioScreen(
    viewModel: WebcamViewModel,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val config by viewModel.config.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val primaryIp by viewModel.primaryIp.collectAsState()
    val wifiIp by viewModel.wifiIp.collectAsState()
    val usbIp by viewModel.usbIp.collectAsState()
    val isBlackout by viewModel.isBlackoutMode.collectAsState()
    val showUsbDialog by viewModel.showUsbDialog.collectAsState()
    val availableSensors by viewModel.availableSensors.collectAsState()
    val selectedSettingsSensorId by viewModel.selectedSettingsSensorId.collectAsState()

    val audioLevelRms by viewModel.audioLevelRms.collectAsState()
    val showSettingsSheet by viewModel.showSettingsSheet.collectAsState()
    val showClientsDialog by viewModel.showClientsDialog.collectAsState()


    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Auto-update device orientation and switch camera profile seamlessly
    LaunchedEffect(isLandscape) {
        viewModel.updateDeviceOrientation(isLandscape, lifecycleOwner, previewViewRef?.surfaceProvider)
    }

    val activeAspectRatio = config.getActiveAspectRatio(isLandscape)
    val activeResolution = config.getActiveResolution(isLandscape)
    val currentSensor = availableSensors.find { it.cameraId == config.selectedCameraId }
    val sensorSwitchLabel = currentSensor?.let {
        if (it.facing == CameraFacing.BACK) "Rear #${it.cameraId}" else "Front #${it.cameraId}"
    } ?: if (config.cameraFacing == CameraFacing.BACK) "Rear" else "Front"

    // Auto-start streaming if permissions are granted and auto-start setting is enabled
    LaunchedEffect(hasCameraPermission, hasAudioPermission, config.autoStartStreaming, config.isCameraDisabled) {
        val permissionsOk = hasAudioPermission && (hasCameraPermission || config.isCameraDisabled)
        if (permissionsOk && !stats.isRunning && config.autoStartStreaming) {
            viewModel.startStreaming(lifecycleOwner, previewViewRef?.surfaceProvider, isLandscape)
        }
    }

    // Instantly rebind camera when resolution, aspect ratio, sensor, or orientation settings change
    LaunchedEffect(activeResolution, activeAspectRatio, config.selectedCameraId, config.lockHorizontalStream, isLandscape) {
        if (stats.isRunning) {
            viewModel.rebindActiveCamera(lifecycleOwner, previewViewRef?.surfaceProvider, isLandscape)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_icon),
                                contentDescription = "EZ-Cam-Mic",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                Text(
                                    text = "EZ-Cam-Mic",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    softWrap = true
                                )
                                Text(
                                    text = if (stats.isRunning) "Live • MJPEG & Audio" else "Offline • Ready to stream",
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = if (stats.isRunning) EmeraldLive else TextSecondary,
                                    softWrap = true,
                                    maxLines = 2
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setShowUsbDialog(true) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("help_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "PC Setup Guide",
                                tint = CyanAccent
                            )
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateDark950
                    )
                )
            },
            containerColor = SlateDark950
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight
                val isNarrow = viewportWidth < 360.dp
                val isShort = viewportHeight < 620.dp
                val contentHorizontalPadding = if (isNarrow) 8.dp else 16.dp
                val contentVerticalSpacing = if (isShort) 8.dp else 14.dp

                if (!hasAudioPermission || (!hasCameraPermission && !config.isCameraDisabled)) {
                    // Permissions Request Card
                    PermissionRequestView(onRequestPermissions = onRequestPermissions)
                } else {
                    // Orientation Mismatch Warning check based on active orientation profile
                    val isMismatch = (isLandscape && activeAspectRatio.orientation == StreamOrientation.PORTRAIT) ||
                            (!isLandscape && activeAspectRatio.orientation == StreamOrientation.LANDSCAPE)

                    // Main Dashboard Content
                    if (isLandscape) {
                        // Landscape Layout: Viewfinder on left, Controls on right
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isNarrow) 8.dp else 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1.2f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = isMismatch,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    OrientationMismatchBanner(
                                        onAdjustSettings = onNavigateToSettings
                                    )
                                }

                                CameraViewfinder(
                                    config = config,
                                    stats = stats,
                                    isLandscape = true,
                                    isBlackout = isBlackout,
                                    audioLevelRms = audioLevelRms,
                                    onOpenClientsList = { viewModel.setShowClientsDialog(true) },
                                    onPreviewViewReady = { pv ->
                                        previewViewRef = pv
                                        viewModel.updatePreviewSurfaceProvider(pv.surfaceProvider)
                                    }
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ControlBar(
                                    config = config,
                                    isStreaming = stats.isRunning,
                                    sensorLabel = sensorSwitchLabel,
                                    onToggleStreaming = {
                                        viewModel.toggleStreaming(lifecycleOwner, previewViewRef?.surfaceProvider)
                                    },
                                    onToggleCamera = { viewModel.toggleCamera(lifecycleOwner, previewViewRef?.surfaceProvider) },
                                    onSwitchCamera = { viewModel.cycleNextCamera(lifecycleOwner, previewViewRef?.surfaceProvider) },
                                    onToggleTorch = { viewModel.toggleTorch() },
                                    onToggleMute = { viewModel.toggleMuteAudio() },
                                    onToggleMirror = { viewModel.toggleMirror() },
                                    onToggleGrid = { viewModel.toggleGrid() },
                                    onToggleBlackout = { viewModel.setBlackoutMode(true) },
                                    onOpenSettings = onNavigateToSettings
                                )

                                ConnectionCard(
                                    primaryIp = primaryIp,
                                    wifiIp = wifiIp,
                                    usbIp = usbIp,
                                    port = config.port,
                                    stats = stats,
                                    config = config,
                                    isWebStreamingEnabled = config.isWebStreamingEnabled,
                                    isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                                    onToggleWebStreaming = { viewModel.setWebStreamingEnabled(it) },
                                    onToggleUsbStreaming = { viewModel.setUsbStreamingEnabled(it) },
                                    onOpenGuide = { viewModel.setShowUsbDialog(true) }
                                )
                            }
                        }
                    } else {
                        // Portrait Layout: Stacked vertically
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp)
                                .padding(horizontal = contentHorizontalPadding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(contentVerticalSpacing)
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            AnimatedVisibility(
                                visible = isMismatch,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                OrientationMismatchBanner(
                                    onAdjustSettings = onNavigateToSettings
                                )
                            }

                            CameraViewfinder(
                                config = config,
                                stats = stats,
                                isLandscape = false,
                                isBlackout = isBlackout,
                                audioLevelRms = audioLevelRms,
                                maxViewportHeight = if (isShort) minOf(viewportHeight * 0.55f, 360.dp) else null,
                                onOpenClientsList = { viewModel.setShowClientsDialog(true) },
                                onPreviewViewReady = { pv ->
                                    previewViewRef = pv
                                    viewModel.updatePreviewSurfaceProvider(pv.surfaceProvider)
                                }
                            )

                            ControlBar(
                                config = config,
                                isStreaming = stats.isRunning,
                                sensorLabel = sensorSwitchLabel,
                                onToggleStreaming = {
                                    viewModel.toggleStreaming(lifecycleOwner, previewViewRef?.surfaceProvider)
                                },
                                onToggleCamera = { viewModel.toggleCamera(lifecycleOwner, previewViewRef?.surfaceProvider) },
                                onSwitchCamera = { viewModel.cycleNextCamera(lifecycleOwner, previewViewRef?.surfaceProvider) },
                                onToggleTorch = { viewModel.toggleTorch() },
                                onToggleMute = { viewModel.toggleMuteAudio() },
                                onToggleMirror = { viewModel.toggleMirror() },
                                onToggleGrid = { viewModel.toggleGrid() },
                                onToggleBlackout = { viewModel.setBlackoutMode(true) },
                                onOpenSettings = onNavigateToSettings
                            )

                            ConnectionCard(
                                primaryIp = primaryIp,
                                wifiIp = wifiIp,
                                usbIp = usbIp,
                                port = config.port,
                                stats = stats,
                                config = config,
                                isWebStreamingEnabled = config.isWebStreamingEnabled,
                                isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                                onToggleWebStreaming = { viewModel.setWebStreamingEnabled(it) },
                                onToggleUsbStreaming = { viewModel.setUsbStreamingEnabled(it) },
                                onOpenGuide = { viewModel.setShowUsbDialog(true) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // USB / PC Setup Guide Dialog
        if (showUsbDialog) {
            UsbSetupGuideDialog(
                config = config,
                stats = stats,
                wifiIp = wifiIp,
                usbIp = usbIp,
                activeResolution = activeResolution,
                activeAspectRatio = activeAspectRatio,
                currentSensor = currentSensor,
                isLandscape = isLandscape,
                onOpenSettings = {
                    viewModel.setShowUsbDialog(false)
                    viewModel.setShowSettingsSheet(true)
                },
                onDismiss = { viewModel.setShowUsbDialog(false) }
            )
        }

        // Settings Dialog Card
        if (showSettingsSheet) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { viewModel.setShowSettingsSheet(false) }
            )
        }

        // Connected Clients Dialog
        if (showClientsDialog) {
            ConnectedClientsDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.setShowClientsDialog(false) }
            )
        }

        // Blackout / Screen Dim Overlay (Battery Saver)
        BlackoutScreen(
            isVisible = isBlackout,
            stats = stats,
            onDismiss = {
                viewModel.setBlackoutMode(false)
                previewViewRef?.surfaceProvider?.let {
                    viewModel.updatePreviewSurfaceProvider(it)
                }
            }
        )
    }
}

@Composable
private fun OrientationMismatchBanner(
    onAdjustSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF78350F).copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("orientation_mismatch_warning")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Camera Orientation Mismatch",
                        color = Color(0xFFFEF3C7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sensor ratio differs from phone orientation. Tap Adjust to align.",
                        color = Color(0xFFFDE68A),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            OutlinedButton(
                onClick = onAdjustSettings,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFEF3C7)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Adjust", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionRequestView(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(CyanAccent.copy(alpha = 0.15f))
                        .border(1.dp, CyanAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Camera & Audio Access",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "EZ-Cam-Mic transforms your Android phone into an ultra-low latency webcam and microphone for PC and Mac over Wi-Fi and USB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Permission checklist points
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionFeatureRow("Camera", "Full HD 1080p 60fps streaming & sensor control")
                    PermissionFeatureRow("Microphone", "16-bit PCM studio audio capture with gain boost")
                    PermissionFeatureRow("100% Private", "Streams directly on your local LAN / USB (no cloud)")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = SlateDark950
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_permissions_button")
                ) {
                    Text(
                        text = "Grant Camera & Mic Access",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionFeatureRow(title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EmeraldLive,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextSecondary,
                softWrap = true
            )
        }
    }
}
