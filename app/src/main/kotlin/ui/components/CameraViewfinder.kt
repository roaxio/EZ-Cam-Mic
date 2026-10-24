package io.github.roaxio.ezcammic.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.roaxio.ezcammic.model.ServerStats
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.InsetBackground
import io.github.roaxio.ezcammic.ui.theme.RoseError
import io.github.roaxio.ezcammic.ui.theme.TextPrimary
import io.github.roaxio.ezcammic.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CameraViewfinder(
    config: WebcamConfig,
    stats: ServerStats,
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    isBlackout: Boolean = false,
    maxViewportHeight: Dp? = null,
    audioLevelRms: Float = stats.audioLevelRms,
    onOpenClientsList: () -> Unit = {}
) {
    val context = LocalContext.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    LaunchedEffect(previewView) {
        onPreviewViewReady(previewView)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val activeAspectRatio = config.getActiveAspectRatio(isLandscape)
    val activeResolution = config.getActiveResolution(isLandscape)
    val currentRatio = activeAspectRatio.ratioValue

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("camera_viewfinder_container"),
        contentAlignment = Alignment.Center
    ) {
        val availWidth = maxWidth
        val availHeight = maxHeight

        // Compute dimension boundaries that preserve currentRatio without cutting off or bloating
        val containerHeight = if (availHeight != Dp.Infinity && availHeight > 0.dp) {
            availHeight
        } else if (!isLandscape) {
            // In Vertical orientation, provide generous Y-axis room without artificial constriction.
            // The screen is scrollable so preview can take full proportional vertical space.
            if (maxViewportHeight != null) {
                minOf(maxViewportHeight, availWidth / currentRatio)
            } else {
                minOf(620.dp, availWidth / currentRatio)
            }
        } else if (maxViewportHeight != null) {
            if (currentRatio < 1.0f) {
                minOf(maxViewportHeight, minOf(380.dp, availWidth * 1.35f))
            } else {
                minOf(maxViewportHeight, availWidth / currentRatio)
            }
        } else {
            if (currentRatio < 1.0f) {
                // In portrait vertical scroll, cap portrait ratio height to balanced monitor bounds
                minOf(380.dp, availWidth * 1.35f)
            } else {
                availWidth / currentRatio
            }
        }

        val calculatedWidth: Dp
        val calculatedHeight: Dp

        if (availWidth / containerHeight > currentRatio) {
            // Height is the limiting factor (e.g. landscape or wide tablet)
            calculatedHeight = containerHeight
            calculatedWidth = containerHeight * currentRatio
        } else {
            // Width is the limiting factor (e.g. narrow phone or portrait)
            calculatedWidth = availWidth
            calculatedHeight = availWidth / currentRatio
        }

        val isCompactHud = calculatedWidth < 340.dp

        Box(
            modifier = Modifier
                .width(calculatedWidth)
                .height(calculatedHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(InsetBackground)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .testTag("camera_viewfinder")
        ) {
            AndroidView(
                factory = {
                    onPreviewViewReady(previewView)
                    previewView
                },
                update = {
                    // Viewfinder surface remains stable; do not re-dispatch onPreviewViewReady on recomposition
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (config.isMirrored) -1f else 1f
                    }
            )

            if (!stats.isRunning) {
                // Standby state overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(InsetBackground),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompactHud) 48.dp else 64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Stream Offline",
                            tint = TextSecondary,
                            modifier = Modifier.size(if (isCompactHud) 24.dp else 32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Webcam Server Standby",
                        style = if (isCompactHud) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap START STREAM below to broadcast to PC",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = if (isCompactHud) 11.sp else 12.sp
                    )
                }
            } else if (isBlackout) {
                // Upstream Pipeline Teardown (Rule 22.2): Rendering sink decoupled at source
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preview surface decoupled (Battery Saver)",
                        color = Color(0xFF475569),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (config.isCameraDisabled) {
                // Audio-Only / Camera Paused Screen Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(InsetBackground)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompactHud) 52.dp else 68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(2.dp, CyanAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Audio Streaming Active",
                            tint = EmeraldLive,
                            modifier = Modifier.size(if (isCompactHud) 26.dp else 34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Microphone Stream Active",
                        style = if (isCompactHud) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Camera sensor is powered down • Zero heat & battery waste",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = if (isCompactHud) 10.sp else 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Live Waveform / Audio VU Meter Visualizer in Center
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (config.isAudioMuted) "AUDIO MUTED" else "BROADCASTING PCM AUDIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (config.isAudioMuted) RoseError else EmeraldLive,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Rule of thirds grid overlay
            if (stats.isRunning && !config.isCameraDisabled && config.isGridVisible) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val gridColor = Color(0x55FFFFFF)

                    // Vertical lines
                    drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeWidth = 1f)

                    // Horizontal lines
                    drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeWidth = 1f)
                }
            }

            // Live HUD Top Bar Overlay
            if (stats.isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isCompactHud) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live status badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = if (isCompactHud) 6.dp else 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldLive.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (config.isCameraDisabled) "AUDIO LIVE" else "LIVE",
                            fontSize = if (isCompactHud) 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLive,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.isCameraDisabled) "MIC ONLY" else "${stats.currentFps.toInt()} FPS",
                            fontSize = if (isCompactHud) 10.sp else 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (config.isCameraDisabled) CyanAccent else TextPrimary
                        )
                    }

                    // Resolution & connected clients HUD
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clients badge (clickable to view and manage connected clients)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0F172A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenClientsList)
                                .testTag("camera_viewfinder_clients_badge")
                                .padding(horizontal = if (isCompactHud) 6.dp else 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = "Clients",
                                tint = CyanAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isCompactHud) "${stats.connectedClients} CL" else if (stats.connectedClients == 1) "1 CLIENT" else "${stats.connectedClients} CLIENTS",
                                fontSize = if (isCompactHud) 10.sp else 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }

                        // Resolution badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0F172A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = if (isCompactHud) 6.dp else 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (config.isCameraDisabled) "AUDIO" else activeResolution.badge,
                                fontSize = if (isCompactHud) 10.sp else 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }

                // Bottom Audio & Bitrate HUD Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xEE070B14))
                            )
                        )
                        .padding(horizontal = if (isCompactHud) 8.dp else 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio VU Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (config.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Microphone Status",
                            tint = if (config.isAudioMuted) RoseError else CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .widthIn(min = 36.dp, max = 110.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            val vuWidth = if (config.isAudioMuted) 0f else audioLevelRms.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = vuWidth.coerceAtLeast(0.01f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(EmeraldLive, CyanAccent, RoseError)
                                        )
                                    )
                            )
                        }
                    }

                    // Bitrate
                    Text(
                        text = "${(stats.bitrateKbps / 1000.0).let { String.format(Locale.US, "%.1f", it) }} Mbps",
                        fontSize = if (isCompactHud) 10.sp else 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
