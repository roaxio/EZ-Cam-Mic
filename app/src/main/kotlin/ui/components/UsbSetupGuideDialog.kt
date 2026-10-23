package io.github.roaxio.ezcammic.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.CameraSensorInfo
import io.github.roaxio.ezcammic.model.ServerStats
import io.github.roaxio.ezcammic.model.StreamResolution
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.ui.theme.CardBackground
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.InsetBackground
import io.github.roaxio.ezcammic.ui.theme.SlateDark950
import io.github.roaxio.ezcammic.ui.theme.TextPrimary
import io.github.roaxio.ezcammic.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun UsbSetupGuideDialog(
    config: WebcamConfig,
    stats: ServerStats,
    wifiIp: String?,
    usbIp: String?,
    activeResolution: StreamResolution,
    activeAspectRatio: AspectRatioOption,
    currentSensor: CameraSensorInfo?,
    isLandscape: Boolean,
    onOpenSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("OBS Studio", "USB Tethering", "Linux v4l2", "Zoom & Meet")

    // Dynamic state values derived strictly from current user settings & active hardware
    val proto = if (config.isHttpsEnabled) "https" else "http"
    val port = config.port
    val fps = config.targetFps
    val hwParts = stats.activeResolution.split("x")
    val hwWidth = hwParts.getOrNull(0)?.toIntOrNull() ?: activeResolution.width
    val hwHeight = hwParts.getOrNull(1)?.toIntOrNull() ?: activeResolution.height
    // When live streaming, reflect actual hardware negotiated stream dimensions
    val width = if (stats.isRunning && hwWidth > 0) hwWidth else activeResolution.width
    val height = if (stats.isRunning && hwHeight > 0) hwHeight else activeResolution.height
    val sensorName = currentSensor?.displayName ?: "Sensor #${config.selectedCameraId}"
    val orientationName = if (isLandscape) "Landscape" else "Portrait"

    // Dynamic Wi-Fi Endpoints
    val isWifiActive = wifiIp != null
    val wifiIpStr = wifiIp ?: "0.0.0.0"
    val wifiBase = "$proto://$wifiIpStr:$port"
    val wifiVideo = "$wifiBase/video"
    val wifiVideoWithAuth = if (config.isAuthEnabled) "$proto://${config.authUsername}:${config.authPassword}@$wifiIpStr:$port/video" else wifiVideo
    val wifiAudio = "$wifiBase/audio.wav"
    val wifiAudioWithAuth = if (config.isAuthEnabled) "$proto://${config.authUsername}:${config.authPassword}@$wifiIpStr:$port/audio.wav" else wifiAudio

    // Dynamic USB (Localhost) Endpoints
    val isUsbTetheringActive = usbIp != null
    val usbIpStr = usbIp ?: "192.168.42.129"
    val usbBase = "$proto://$usbIpStr:$port"
    val usbVideo = "$usbBase/video"
    val usbVideoWithAuth = if (config.isAuthEnabled) "$proto://${config.authUsername}:${config.authPassword}@$usbIpStr:$port/video" else usbVideo
    val usbAudio = "$usbBase/audio.wav"
    val usbAudioWithAuth = if (config.isAuthEnabled) "$proto://${config.authUsername}:${config.authPassword}@$usbIpStr:$port/audio.wav" else usbAudio

    // Dynamic Commands
    
    val linuxFfmpegUsb = "ffmpeg -f mjpeg -r $fps -i $usbVideoWithAuth -vcodec rawvideo -pix_fmt yuv420p -threads 0 -f v4l2 /dev/video2"
    val linuxFfmpegWifi = "ffmpeg -f mjpeg -r $fps -i $wifiVideoWithAuth -vcodec rawvideo -pix_fmt yuv420p -threads 0 -f v4l2 /dev/video2"
    val linuxAudioCmdUsb = "ffmpeg -i $usbAudioWithAuth -f alsa default"
    val linuxAudioCmdWifi = "ffmpeg -i $wifiAudioWithAuth -f alsa default"

    fun copy(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard: $label", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.92f)
                .testTag("usb_setup_guide_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(Color(0xFF1E293B))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 1. Fixed Header Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Laptop,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PC Streaming & Setup Manual",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2,
                                lineHeight = 20.sp,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Dynamic instructions synced with App Settings",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                lineHeight = 14.sp,
                                softWrap = true
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Fixed Tab Navigation Strip (Always visible, never scrolls away!)
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = InsetBackground,
                    contentColor = CyanAccent,
                    edgePadding = 4.dp,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab, matchContentSize = false),
                            height = 3.dp,
                            color = CyanAccent
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanAccent else TextSecondary,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Scrollable Manual Body (Takes full available height)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dynamic Profile & Live Settings Dependency Banner
                    CompactSettingsBanner(
                        proto = proto,
                        port = port,
                        fps = fps,
                        width = width,
                        height = height,
                        aspectRatioLabel = activeAspectRatio.label,
                        orientationName = orientationName,
                        sensorName = sensorName,
                        isHttps = config.isHttpsEnabled,
                        isAuth = config.isAuthEnabled,
                        authUsername = config.authUsername,
                        isWifiEnabled = config.isWebStreamingEnabled,
                        isUsbEnabled = config.isUsbStreamingEnabled,
                        onOpenSettings = onOpenSettings
                    )

                    // Tab Specific Manual Content
                    when (selectedTab) {
                        0 -> ObsStudioTabContent(
                            wifiVideoUrl = wifiVideoWithAuth,
                            usbVideoUrl = usbVideoWithAuth,
                            wifiAudioUrl = wifiAudioWithAuth,
                            usbAudioUrl = usbAudioWithAuth,
                            width = width,
                            height = height,
                            fps = fps,
                            sensorName = sensorName,
                            orientationName = orientationName,
                            isAuthEnabled = config.isAuthEnabled,
                            authUsername = config.authUsername,
                            isHttpsEnabled = config.isHttpsEnabled,
                            isWifiStreamingEnabled = config.isWebStreamingEnabled,
                            isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                            isWifiActive = isWifiActive,
                            isUsbActive = isUsbTetheringActive,
                            audioSampleRate = config.audioSampleRate,
                            audioGain = config.audioGain,
                            onCopy = ::copy
                        )
                        1 -> UsbTetheringTabContent(
                            port = port,
                            proto = proto,
                            usbBase = usbBase,
                            usbVideoUrl = usbVideoWithAuth,
                            usbAudioUrl = usbAudioWithAuth,
                            isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                            isUsbActive = isUsbTetheringActive,
                            isAuthEnabled = config.isAuthEnabled,
                            authUsername = config.authUsername,
                            authPassword = config.authPassword,
                            isHttpsEnabled = config.isHttpsEnabled,
                            onCopy = ::copy
                        )
                        2 -> LinuxV4l2TabContent(
                            ffmpegUsbCmd = linuxFfmpegUsb,
                            ffmpegWifiCmd = linuxFfmpegWifi,
                            audioUsbCmd = linuxAudioCmdUsb,
                            audioWifiCmd = linuxAudioCmdWifi,
                            width = width,
                            height = height,
                            fps = fps,
                            sensorName = sensorName,
                            isWifiStreamingEnabled = config.isWebStreamingEnabled,
                            isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                            isWifiActive = isWifiActive,
                            isUsbActive = isUsbTetheringActive,
                            onCopy = ::copy
                        )
                        3 -> MeetingAppsTabContent(
                            wifiVideoUrl = wifiVideoWithAuth,
                            usbVideoUrl = usbVideoWithAuth,
                            wifiAudioUrl = wifiAudioWithAuth,
                            usbAudioUrl = usbAudioWithAuth,
                            width = width,
                            height = height,
                            aspectRatioLabel = activeAspectRatio.label,
                            fps = fps,
                            port = port,
                            isHttps = config.isHttpsEnabled,
                            isWifiStreamingEnabled = config.isWebStreamingEnabled,
                            isUsbStreamingEnabled = config.isUsbStreamingEnabled,
                            isWifiActive = isWifiActive,
                            isUsbActive = isUsbTetheringActive,
                            onCopy = ::copy
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Fixed Action Buttons Footer (Single-line, non-clipping, accessible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("guide_open_settings_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(CyanAccent)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanAccent,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("guide_got_it_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = SlateDark950
                        )
                    ) {
                        Text(
                            text = "Got It",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSettingsBanner(
    proto: String,
    port: Int,
    fps: Int,
    width: Int,
    height: Int,
    aspectRatioLabel: String,
    orientationName: String,
    sensorName: String,
    isHttps: Boolean,
    isAuth: Boolean,
    authUsername: String,
    isWifiEnabled: Boolean,
    isUsbEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InsetBackground)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE CONFIGURATION PROFILE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Edit in Settings ⚙️",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Status Badges Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickBadge(
                    icon = Icons.Default.Cameraswitch,
                    label = "Sensor & Dim",
                    value = "$sensorName • ${width}x$height ($orientationName)",
                    modifier = Modifier.weight(1f)
                )
                QuickBadge(
                    icon = if (isHttps) Icons.Default.Lock else Icons.Default.Wifi,
                    label = "Protocol & Port",
                    value = "${proto.uppercase()} • Port $port • $fps FPS",
                    modifier = Modifier.weight(1f)
                )
            }

            // Status Badges Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickBadge(
                    icon = if (isAuth) Icons.Default.Shield else Icons.Default.Info,
                    label = "Security",
                    value = if (isAuth) "Auth: $authUsername" else "Open (No Auth)",
                    modifier = Modifier.weight(1f)
                )
                QuickBadge(
                    icon = Icons.Default.Usb,
                    label = "Streaming Toggles",
                    value = "Wi-Fi: ${if (isWifiEnabled) "ON" else "OFF"} • USB: ${if (isUsbEnabled) "ON" else "OFF"}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Settings Sensitivity Warning
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF78350F).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 1.dp)
                    )
                    Text(
                        text = "Configuration Dependency: All manual instructions below are generated from your current settings. If you switch camera sensors on the fly or rotate the device, stream resolution changes immediately—update your PC client (OBS/FFmpeg/browser) to match!",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Color(0xFFFDE68A),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                softWrap = true,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ObsStudioTabContent(
    wifiVideoUrl: String,
    usbVideoUrl: String,
    wifiAudioUrl: String,
    usbAudioUrl: String,
    width: Int,
    height: Int,
    fps: Int,
    sensorName: String,
    orientationName: String,
    isAuthEnabled: Boolean,
    authUsername: String,
    isHttpsEnabled: Boolean,
    isWifiStreamingEnabled: Boolean,
    isUsbStreamingEnabled: Boolean,
    isWifiActive: Boolean,
    isUsbActive: Boolean,
    audioSampleRate: Int,
    audioGain: Float,
    onCopy: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuideStep(
            1,
            "Install OBS Studio on Windows, macOS, or Linux, then open it."
        )

        GuideStep(
            2,
            "In OBS, under 'Sources', click '+' and choose 'Browser'. Name it 'EZ-Cam-Mic'."
        )

        GuideStep(
            3,
            "Paste your active live video stream URL into the 'URL' field:"
        )

        Text(
            text = "Option A: Wi-Fi Streaming URL ${if (!isWifiStreamingEnabled) "(DISABLED on Home Screen)" else if (!isWifiActive) "(Wi-Fi disconnected)" else ""}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isWifiStreamingEnabled && isWifiActive) EmeraldLive else Color(0xFFEF4444)
        )
        if (isWifiStreamingEnabled && isWifiActive) {
            CodeSnippetBox(wifiVideoUrl) { onCopy(wifiVideoUrl, "OBS Wi-Fi URL") }
        } else {
            CodeSnippetBox("Enable Wi-Fi streaming and connect to Wi-Fi to view URL") {}
        }

        Text(
            text = "Option B: Zero-Lag USB Tethering URL ${if (!isUsbStreamingEnabled) "(DISABLED on Home Screen)" else if (!isUsbActive) "(Tethering off)" else ""}:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isUsbStreamingEnabled && isUsbActive) CyanAccent else Color(0xFFEF4444)
        )
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbVideoUrl) { onCopy(usbVideoUrl, "OBS USB URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view URL") {}
        }

        if (isAuthEnabled) {
            InfoCallout(
                text = "Authentication is active in Settings. Your credentials ($authUsername) are automatically embedded in the URLs above so OBS connects without a 401 Unauthorized error."
            )
        }

        if (isHttpsEnabled) {
            InfoCallout(
                text = "HTTPS (TLS) is active in Settings. If OBS shows a blank screen, visit the URL in your PC browser once to accept the self-signed certificate."
            )
        }

        GuideStep(
            4,
            "Set Browser Source Canvas Dimensions to match current App Settings:"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(InsetBackground)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "• Width: $width",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLive
                )
                Text(
                    text = "• Height: $height",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLive
                )
                Text(
                    text = "• FPS: $fps",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLive
                )
                Text(
                    text = "• Check: 'Shutdown source when not visible'",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        SensorSwitchWarning(
            text = "On-The-Fly Sensor Change Notice: You are currently streaming from $sensorName in $orientationName ($width x $height). If you switch to another camera sensor or rotate orientation in the app, OBS will not auto-resize—update the Width and Height in OBS to match your new sensor profile!"
        )

        GuideStep(
            5,
            "Add Phone Microphone in OBS: Add a 'Media Source' or 'Browser Source' pointing to your live WAV audio feed ($audioSampleRate Hz, ${String.format(Locale.US, "%.1f", audioGain)}x gain):"
        )
        Text(text = "Option A: Wi-Fi Audio URL:", fontSize = 11.sp, color = EmeraldLive)
        if (isWifiStreamingEnabled && isWifiActive) {
            CodeSnippetBox(wifiAudioUrl) { onCopy(wifiAudioUrl, "OBS Wi-Fi Audio URL") }
        } else {
            CodeSnippetBox("Enable Wi-Fi streaming and connect to Wi-Fi to view URL") {}
        }
        
        Text(text = "Option B: Zero-Lag USB Audio URL:", fontSize = 11.sp, color = CyanAccent)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbAudioUrl) { onCopy(usbAudioUrl, "OBS USB Audio URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view URL") {}
        }

        GuideStep(
            6,
            "Click 'Start Virtual Camera' in the bottom-right Controls dock of OBS. It will instantly appear as 'OBS Virtual Camera' in Zoom, Teams, Google Meet, and Discord!"
        )
    }
}

@Composable
private fun UsbTetheringTabContent(
    port: Int,
    proto: String,
    
    usbBase: String,
    usbVideoUrl: String,
    usbAudioUrl: String,
    isUsbStreamingEnabled: Boolean,
    isUsbActive: Boolean,
    isAuthEnabled: Boolean,
    authUsername: String,
    authPassword: String,
    isHttpsEnabled: Boolean,
    onCopy: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!isUsbStreamingEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7F1D1D).copy(alpha = 0.3f))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "'USB Wired Streaming' is currently DISABLED! Enable it on the Home Screen under the 'LIVE CONFIGURATION PROFILE' block to allow USB Tethering connections.",
                        fontSize = 11.sp,
                        color = Color(0xFFFCA5A5),
                        softWrap = true
                    )
                }
            }
        }

        GuideStep(
            1,
            "Connect your Android device to your PC with a high-quality USB cable."
        )

        GuideStep(
            2,
            "Ensure 'USB Tethering' is turned on in your Android device Settings (usually under Network & internet -> Hotspot & tethering). You can tap the empty USB URL slot on the Home Screen to open Settings quickly."
        )

        GuideStep(
            3,
            "Access your zero-latency local stream directly on your PC:"
        )

        Text(text = "• Web Studio Dashboard (Chrome / Edge / Firefox):", fontSize = 11.sp, color = TextPrimary)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbBase) { onCopy(usbBase, "USB Web Studio URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view URL") {}
        }

        Text(text = "• Direct MJPEG Video Stream (OBS / VLC / FFmpeg):", fontSize = 11.sp, color = TextPrimary)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbVideoUrl) { onCopy(usbVideoUrl, "USB Video Feed URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view URL") {}
        }

        Text(text = "• Direct Live PCM Audio Feed (WAV):", fontSize = 11.sp, color = TextPrimary)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbAudioUrl) { onCopy(usbAudioUrl, "USB Audio Feed URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view URL") {}
        }

        if (isHttpsEnabled) {
            InfoCallout(
                text = "HTTPS is active in Settings. Open $usbBase in your PC browser and click 'Advanced -> Proceed to device (unsafe)' to authorize the local self-signed certificate."
            )
        }

        if (isAuthEnabled) {
            InfoCallout(
                text = "Authentication is required: Username is '$authUsername' and Password is '$authPassword' (as configured in Settings)."
            )
        }
    }
}

@Composable
private fun LinuxV4l2TabContent(
    ffmpegUsbCmd: String,
    ffmpegWifiCmd: String,
    audioUsbCmd: String,
    audioWifiCmd: String,
    width: Int,
    height: Int,
    fps: Int,
    sensorName: String,
    isWifiStreamingEnabled: Boolean,
    isUsbStreamingEnabled: Boolean,
    isWifiActive: Boolean,
    isUsbActive: Boolean,
    onCopy: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuideStep(
            1,
            "Create a virtual webcam loopback device (/dev/video2) on Linux:"
        )
        val modprobeCmd = "sudo modprobe v4l2loopback devices=1 video_nr=2 card_label=\"EZ-Cam-Mic\" exclusive_caps=1"
        CodeSnippetBox(modprobeCmd) { onCopy(modprobeCmd, "v4l2loopback Command") }

        GuideStep(
            2,
            "Pipe the live stream into /dev/video2 using FFmpeg (configured for $width x $height @ $fps FPS):"
        )
        Text(text = "Via USB Localhost (Recommended):", fontSize = 11.sp, color = CyanAccent)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(ffmpegUsbCmd) { onCopy(ffmpegUsbCmd, "FFmpeg USB Pipe Command") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view command") {}
        }

        Text(text = "Or via Wi-Fi:", fontSize = 11.sp, color = EmeraldLive)
        if (isWifiStreamingEnabled && isWifiActive) {
            CodeSnippetBox(ffmpegWifiCmd) { onCopy(ffmpegWifiCmd, "FFmpeg Wi-Fi Pipe Command") }
        } else {
            CodeSnippetBox("Enable Wi-Fi streaming and connect to Wi-Fi to view command") {}
        }

        SensorSwitchWarning(
            text = "Resolution & Sensor Dependency: FFmpeg initializes the /dev/video2 device with $width x $height ($sensorName). If you switch camera sensors or orientation in the app, restart the FFmpeg command (Ctrl+C and re-run) so the virtual device adapts to the new camera frame size."
        )

        GuideStep(
            3,
            "Optional Microphone Pipe (PulseAudio / PipeWire ALSA loopback):"
        )
        Text(text = "Via USB Localhost (Recommended):", fontSize = 11.sp, color = CyanAccent)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(audioUsbCmd) { onCopy(audioUsbCmd, "FFmpeg USB Audio Command") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view audio command") {}
        }
        
        Text(text = "Or via Wi-Fi:", fontSize = 11.sp, color = EmeraldLive)
        if (isWifiStreamingEnabled && isWifiActive) {
            CodeSnippetBox(audioWifiCmd) { onCopy(audioWifiCmd, "FFmpeg Wi-Fi Audio Command") }
        } else {
            CodeSnippetBox("Enable Wi-Fi streaming and connect to Wi-Fi to view audio command") {}
        }

        GuideStep(
            4,
            "Select 'EZ-Cam-Mic' as your camera in Chrome, Zoom, Google Meet, Discord, or Teams natively on Linux!"
        )
    }
}

@Composable
private fun MeetingAppsTabContent(
    wifiVideoUrl: String,
    usbVideoUrl: String,
    wifiAudioUrl: String,
    usbAudioUrl: String,
    width: Int,
    height: Int,
    aspectRatioLabel: String,
    fps: Int,
    port: Int,
    isHttps: Boolean,
    isWifiStreamingEnabled: Boolean,
    isUsbStreamingEnabled: Boolean,
    isWifiActive: Boolean,
    isUsbActive: Boolean,
    onCopy: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuideStep(
            1,
            "Method 1: OBS Virtual Camera (Best Quality & Universal Support)"
        )
        Text(
            text = "Follow the 'OBS Studio' tab to add your feed, set canvas to $width x $height, and click 'Start Virtual Camera'. In Zoom, Google Meet, Microsoft Teams, or Discord, select 'OBS Virtual Camera' as your video device.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = TextSecondary,
            softWrap = true
        )

        GuideStep(
            2,
            "Method 2: Zero-Software Browser Tab Share (Instant Google Meet & Teams)"
        )
        val displayWifi = if (isWifiStreamingEnabled && isWifiActive) wifiVideoUrl else "(Wi-Fi URL inactive)"
        val displayUsb = if (isUsbStreamingEnabled && isUsbActive) usbVideoUrl else "(USB URL inactive)"
        Text(
            text = "Open $displayWifi (or $displayUsb via USB) on your PC browser. In Google Meet or Teams, click 'Share Screen' > 'Chrome Tab' > choose 'EZ-Cam-Mic' and check 'Share tab audio'.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = TextSecondary,
            softWrap = true
        )

        GuideStep(
            3,
            "Microphone Audio Feed for PC Meetings"
        )
        Text(
            text = "Your uncompressed WAV microphone stream is available at:",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Text(text = "Via Wi-Fi:", fontSize = 11.sp, color = EmeraldLive)
        if (isWifiStreamingEnabled && isWifiActive) {
            CodeSnippetBox(wifiAudioUrl) { onCopy(wifiAudioUrl, "Microphone Wi-Fi Feed URL") }
        } else {
            CodeSnippetBox("Enable Wi-Fi streaming and connect to Wi-Fi to view audio URL") {}
        }
        
        Text(text = "Via USB Localhost:", fontSize = 11.sp, color = CyanAccent)
        if (isUsbStreamingEnabled && isUsbActive) {
            CodeSnippetBox(usbAudioUrl) { onCopy(usbAudioUrl, "Microphone USB Feed URL") }
        } else {
            CodeSnippetBox("Enable USB Streaming and Tethering to view audio URL") {}
        }
        Text(
            text = "On Windows, use VB-Audio Virtual Cable (free). Open the audio URL in VLC, send VLC output to 'CABLE Input', and select 'CABLE Output' as your microphone in Zoom. On macOS, use BlackHole.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = TextSecondary,
            softWrap = true
        )

        SensorSwitchWarning(
            text = "Settings Synchronization: Your current stream configuration is ${width}x${height} ($aspectRatioLabel) @ $fps FPS on Port $port (${if (isHttps) "HTTPS" else "HTTP"}). If camera output looks unexpected after switching sensors, verify your sensor resolution profile in App Settings!"
        )
    }
}

@Composable
private fun SensorSwitchWarning(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF78350F).copy(alpha = 0.2f))
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color(0xFFFDE68A),
                softWrap = true
            )
        }
    }
}

@Composable
private fun InfoCallout(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F2942))
            .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier
                    .size(15.dp)
                    .padding(top = 1.dp)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextPrimary,
                softWrap = true
            )
        }
    }
}

@Composable
private fun GuideStep(stepNumber: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CyanAccent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            softWrap = true
        )
    }
}

@Composable
private fun CodeSnippetBox(code: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InsetBackground)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = code,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = EmeraldLive,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
            softWrap = true,
            lineHeight = 16.sp
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
