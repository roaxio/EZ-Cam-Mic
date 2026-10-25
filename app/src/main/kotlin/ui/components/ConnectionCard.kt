package io.github.roaxio.ezcammic.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.roaxio.ezcammic.model.ServerStats
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.ui.theme.CardBackground
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.InsetBackground
import io.github.roaxio.ezcammic.ui.theme.TextPrimary
import io.github.roaxio.ezcammic.ui.theme.TextSecondary
import io.github.roaxio.ezcammic.utils.NetworkUtils

@Composable
fun ConnectionCard(
    primaryIp: String,
    wifiIp: String?,
    usbIp: String?,
    port: Int,
    stats: ServerStats,
    modifier: Modifier = Modifier,
    config: WebcamConfig? = null,
    isWebStreamingEnabled: Boolean = true,
    isUsbStreamingEnabled: Boolean = true,
    onToggleWebStreaming: (Boolean) -> Unit = {},
    onToggleUsbStreaming: (Boolean) -> Unit = {},
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    var showAdvancedFeeds by remember { mutableStateOf(false) }
    var isWifiExpanded by remember { mutableStateOf(false) }
    var isUsbExpanded by remember { mutableStateOf(false) }

    val isHttps = config?.isHttpsEnabled ?: stats.isHttps
    val isAuth = config?.isAuthEnabled ?: stats.isAuthProtected
    val activePort = config?.port ?: port
    val isWebActive = config?.isWebStreamingEnabled ?: (stats.isWebStreamingEnabled && isWebStreamingEnabled)
    val isUsbActive = config?.isUsbStreamingEnabled ?: (stats.isUsbStreamingEnabled && isUsbStreamingEnabled)

    val displayWifiIp = wifiIp ?: if (primaryIp != usbIp) primaryIp else "0.0.0.0"
    val wifiUrl = if (displayWifiIp != "0.0.0.0") NetworkUtils.buildWebDashboardUrl(displayWifiIp, activePort, isHttps) else "Wi-Fi Not Connected" 
    
    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: PC Connection URL Title + PC SETUP button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PC Connection URLs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenGuide() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Setup Guide",
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PC Setup",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            }

            // Row 2: Status tags (HTTPS, AUTH) in adjacent row below
            if (isHttps || isAuth) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isHttps) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2210B981))
                                .border(1.dp, EmeraldLive.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("HTTPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLive)
                        }
                    }
                    if (isAuth) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2238BDF8))
                                .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AUTH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Wi-Fi Web Connection Header & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. Wi-Fi Web & OBS Studio URL:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWebStreamingEnabled) TextSecondary else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    softWrap = true,
                    lineHeight = 16.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isWebStreamingEnabled) "Enabled" else "Disabled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWebStreamingEnabled) EmeraldLive else Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isWebStreamingEnabled,
                        onCheckedChange = onToggleWebStreaming,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldLive,
                            checkedTrackColor = EmeraldLive.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Wi-Fi Connection Box (Tap to toggle wrap / expand full text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isWebStreamingEnabled) InsetBackground else InsetBackground.copy(alpha = 0.5f))
                    .border(
                        1.dp,
                        if (isWebStreamingEnabled) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .then(
                        if (!isWebStreamingEnabled) Modifier.clickable { onToggleWebStreaming(true) }
                        else Modifier
                    )
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isWebStreamingEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isWifiExpanded) Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                else Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(end = 4.dp)
                            )
                            .clickable { isWifiExpanded = !isWifiExpanded }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = EmeraldLive,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = wifiUrl,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanAccent,
                            softWrap = isWifiExpanded,
                            lineHeight = 18.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isWifiExpanded = !isWifiExpanded },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = if (isWifiExpanded) "COLLAPSE" else "WRAP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = { copyToClipboard(wifiUrl, "Wi-Fi URL") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Wi-Fi URL",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                try {
                                    if (displayWifiIp == "0.0.0.0") return@IconButton
                                    val intent = Intent(Intent.ACTION_VIEW, wifiUrl.toUri())
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open Browser",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi Streaming Disabled (Tap to enable)",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            softWrap = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. USB Tethering Connection Header & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Wired USB Tethering Connection:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUsbStreamingEnabled) TextSecondary else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    softWrap = true,
                    lineHeight = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isUsbStreamingEnabled) "Enabled" else "Disabled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUsbStreamingEnabled) CyanAccent else Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isUsbStreamingEnabled,
                        onCheckedChange = onToggleUsbStreaming,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanAccent,
                            checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isUsbStreamingEnabled) {
                val isUsbTetheringActive = usbIp != null
                val currentUsbUrl = if (usbIp != null) NetworkUtils.buildWebDashboardUrl(usbIp, activePort, isHttps) else ""
                
                if (isUsbTetheringActive) {
                    // USB Tethering Active Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InsetBackground)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isUsbExpanded) Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                    else Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(end = 4.dp)
                                )
                                .clickable { isUsbExpanded = !isUsbExpanded }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentUsbUrl,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                softWrap = isUsbExpanded,
                                lineHeight = 17.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isUsbExpanded = !isUsbExpanded },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text(
                                    text = if (isUsbExpanded) "COLLAPSE" else "WRAP",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard(currentUsbUrl, "USB Tethering URL") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy USB URL",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, currentUsbUrl.toUri())
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open Browser",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }


                } else {
                    // USB Tethering inactive prompt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InsetBackground)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .clickable {
                                try {
                                    val intent = Intent().apply {
                                        setClassName("com.android.settings", "com.android.settings.TetherSettings")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Enable USB Tethering",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "Tap here to open Settings and turn on USB Tethering to stream via USB.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InsetBackground.copy(alpha = 0.5f))
                        .border(1.dp, Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { onToggleUsbStreaming(true) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UsbOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "USB Wired Mode Disabled (Tap to enable)",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            softWrap = true
                        )
                    }
                }
            }

            // Expandable direct feed links
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAdvancedFeeds = !showAdvancedFeeds }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvancedFeeds) "▼ Hide Direct Stream Endpoints" else "▶ Show Direct Stream Endpoints (/video, /audio)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyanAccent
                )
            }

            AnimatedVisibility(visible = showAdvancedFeeds) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    var showAuthUrls by remember { mutableStateOf(false) }
                    
                    if (isAuth) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showAuthUrls = !showAuthUrls }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (showAuthUrls) Icons.Default.Lock else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showAuthUrls) "Hide Auth in URLs" else "Show Auth in URLs",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            }
                        }
                    }

                    val authPrefix = if (isAuth && showAuthUrls && config != null) "${config.authUsername}:${config.authPassword}@" else ""

                    if (isWebActive && displayWifiIp != "0.0.0.0") {
                        Text(text = "Via Wi-Fi:", fontSize = 11.sp, color = EmeraldLive, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, top = 4.dp))
                        
                        val videoFeedWifi = NetworkUtils.buildVideoFeedUrl(displayWifiIp, activePort, isHttps, authPrefix)
                        val audioFeedWifi = NetworkUtils.buildAudioFeedUrl(displayWifiIp, activePort, isHttps, authPrefix)
                        val snapshotUrlWifi = NetworkUtils.buildSnapshotUrl(displayWifiIp, activePort, isHttps, authPrefix)

                        StreamFeedItem(label = "MJPEG Video Stream (/video)", url = videoFeedWifi, onCopy = { copyToClipboard(videoFeedWifi, "Video Feed (Wi-Fi)") })
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamFeedItem(label = "Live Audio Stream (/audio.wav)", url = audioFeedWifi, onCopy = { copyToClipboard(audioFeedWifi, "Audio Feed (Wi-Fi)") })
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamFeedItem(label = "Still Snapshot Frame (/shot.jpg)", url = snapshotUrlWifi, onCopy = { copyToClipboard(snapshotUrlWifi, "Snapshot Frame (Wi-Fi)") })
                        
                        if (isUsbActive && usbIp != null) Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (isUsbActive && usbIp != null) {
                        Text(text = "Via USB Localhost:", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, top = 4.dp))
                        
                        val videoFeedUsb = NetworkUtils.buildVideoFeedUrl(usbIp, activePort, isHttps, authPrefix)
                        val audioFeedUsb = NetworkUtils.buildAudioFeedUrl(usbIp, activePort, isHttps, authPrefix)
                        val snapshotUrlUsb = NetworkUtils.buildSnapshotUrl(usbIp, activePort, isHttps, authPrefix)

                        StreamFeedItem(label = "MJPEG Video Stream (/video)", url = videoFeedUsb, onCopy = { copyToClipboard(videoFeedUsb, "Video Feed (USB)") })
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamFeedItem(label = "Live Audio Stream (/audio.wav)", url = audioFeedUsb, onCopy = { copyToClipboard(audioFeedUsb, "Audio Feed (USB)") })
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamFeedItem(label = "Still Snapshot Frame (/shot.jpg)", url = snapshotUrlUsb, onCopy = { copyToClipboard(snapshotUrlUsb, "Snapshot Frame (USB)") })
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamFeedItem(
    label: String,
    url: String,
    onCopy: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InsetBackground)
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isExpanded) "• Tap to scroll" else "• Tap to wrap",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }
            if (isExpanded) {
                Text(
                    text = url,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent,
                    softWrap = true,
                    lineHeight = 15.sp
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = url,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyanAccent,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
