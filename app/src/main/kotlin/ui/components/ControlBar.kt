package io.github.roaxio.ezcammic.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.WebcamConfig
import io.github.roaxio.ezcammic.ui.theme.AmberWarning
import io.github.roaxio.ezcammic.ui.theme.CardBackground
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.InsetBackground
import io.github.roaxio.ezcammic.ui.theme.RoseError
import io.github.roaxio.ezcammic.ui.theme.SlateDark950
import io.github.roaxio.ezcammic.ui.theme.TextSecondary

@Composable
fun ControlBar(
    config: WebcamConfig,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    sensorLabel: String = "",
    onToggleStreaming: () -> Unit,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleBlackout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("control_bar")
    ) {
        val screenWidth = maxWidth

        // Intelligently derive all geometric dimensions and typography from the container/screen width
        val sectionSpacing = (screenWidth * 0.024f).coerceIn(8.dp, 12.dp)
        val cornerRadius = (screenWidth * 0.035f).coerceIn(10.dp, 16.dp)

        // Primary Row (Row 1): Start Stream button + Quick Toggles (Cam & Mic)
        val row1Height = (screenWidth * 0.148f).coerceIn(52.dp, 66.dp)
        val row1Spacing = (screenWidth * 0.022f).coerceIn(6.dp, 10.dp)
        val streamIconSize = (row1Height * 0.42f).coerceIn(20.dp, 26.dp)
        val streamTextSize = (screenWidth.value * 0.037f).coerceIn(13f, 16f).sp
        val streamSpacer = (row1Height * 0.14f).coerceIn(6.dp, 10.dp)

        // Quick Toggles metrics (Cam On/Off, Mic On/Off):
        // Scaled to match the vertical centering and pleasant proportions of the Torch button
        val quickBadgeSize = (row1Height * 0.50f).coerceIn(26.dp, 35.dp)
        val quickIconSize = (quickBadgeSize * 0.55f).coerceIn(14.dp, 20.dp)
        val quickTextSize = (screenWidth.value * 0.024f).coerceIn(8.5f, 11f).sp
        val quickSpacing = (row1Height * 0.045f).coerceIn(2.dp, 4.dp)

        // Secondary Row (Row 2): 5 action buttons (Switch, Torch, Mirror, Grid, Dim)
        val row2Height = (screenWidth * 0.158f).coerceIn(54.dp, 70.dp)
        val row2BadgeSize = (row2Height * 0.50f).coerceIn(28.dp, 36.dp)
        val row2IconSize = (row2BadgeSize * 0.52f).coerceIn(15.dp, 19.dp)
        val row2TextSize = (screenWidth.value * 0.024f).coerceIn(8.5f, 10.5f).sp
        val row2Spacing = (row2Height * 0.045f).coerceIn(2.dp, 4.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Row 1: Main Stream Launch Action on the left, Quick Toggles (Cam, Mic) on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(row1Height),
                horizontalArrangement = Arrangement.spacedBy(row1Spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Start / Stop Stream Action Button
                Button(
                    onClick = onToggleStreaming,
                    modifier = Modifier
                        .weight(1.85f)
                        .fillMaxHeight()
                        .testTag("toggle_stream_button"),
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) RoseError else EmeraldLive,
                        contentColor = SlateDark950
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(streamIconSize)
                        )
                        Spacer(modifier = Modifier.width(streamSpacer))
                        Text(
                            text = if (isStreaming) "STOP STREAMING" else if (config.isCameraDisabled) "START MIC STREAM" else "START STREAM",
                            fontSize = streamTextSize,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick Toggles Dock Container (Cam On/Off and Mic On/Off to the right of Start Stream button)
                // Centered with text along the Y axis, matching the visual geometry and styling of Torch
                Row(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(CardBackground)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(cornerRadius))
                        .padding(
                            horizontal = (screenWidth * 0.012f).coerceIn(3.dp, 6.dp),
                            vertical = (row1Height * 0.04f).coerceIn(2.dp, 5.dp)
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Camera On/Off Toggle Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        QuickToggleIconButton(
                            icon = if (config.isCameraDisabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            label = if (config.isCameraDisabled) "Cam Off" else "Cam On",
                            contentDescription = if (config.isCameraDisabled) "Enable Camera" else "Disable Camera",
                            isActive = !config.isCameraDisabled,
                            activeColor = EmeraldLive,
                            inactiveColor = RoseError,
                            onClick = onToggleCamera,
                            testTag = "toggle_camera_button",
                            badgeSize = quickBadgeSize,
                            iconSize = quickIconSize,
                            textSize = quickTextSize,
                            spacing = quickSpacing
                        )
                    }

                    // Subtle vertical separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(row1Height * 0.45f)
                            .background(Color(0xFF1E293B))
                    )

                    // Quick Microphone On/Off Toggle Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        QuickToggleIconButton(
                            icon = if (config.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (config.isAudioMuted) "Muted" else "Mic On",
                            contentDescription = if (config.isAudioMuted) "Unmute Microphone" else "Mute Microphone",
                            isActive = !config.isAudioMuted,
                            activeColor = EmeraldLive,
                            inactiveColor = RoseError,
                            onClick = onToggleMute,
                            testTag = "toggle_mic_button",
                            badgeSize = quickBadgeSize,
                            iconSize = quickIconSize,
                            textSize = quickTextSize,
                            spacing = quickSpacing
                        )
                    }
                }
            }

            // Row 2: Action Buttons Row (Camera Switch, Torch, Mirror, Grid, Dim Screen)
            // Non-scrollable with 5 evenly spaced, dynamic-sized buttons matching screen width
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(row2Height)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(CardBackground)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(cornerRadius))
                    .padding(
                        horizontal = (screenWidth * 0.012f).coerceIn(3.dp, 6.dp),
                        vertical = (row2Height * 0.04f).coerceIn(2.dp, 5.dp)
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch Camera (cycles through all available camera sensors)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ControlButton(
                        icon = Icons.Default.Cameraswitch,
                        label = sensorLabel.ifEmpty { if (config.cameraFacing == CameraFacing.BACK) "Rear" else "Front" },
                        isActive = config.cameraFacing == CameraFacing.FRONT,
                        onClick = onSwitchCamera,
                        testTag = "switch_camera_button",
                        badgeSize = row2BadgeSize,
                        iconSize = row2IconSize,
                        textSize = row2TextSize,
                        spacing = row2Spacing
                    )
                }

                // Flashlight / Torch (available on rear camera)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ControlButton(
                        icon = if (config.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        label = if (config.isTorchOn) "Torch ON" else "Torch",
                        isActive = config.isTorchOn,
                        activeColor = AmberWarning,
                        onClick = onToggleTorch,
                        testTag = "toggle_torch_button",
                        badgeSize = row2BadgeSize,
                        iconSize = row2IconSize,
                        textSize = row2TextSize,
                        spacing = row2Spacing
                    )
                }

                // Mirror Flip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ControlButton(
                        icon = Icons.Default.Flip,
                        label = "Mirror",
                        isActive = config.isMirrored,
                        onClick = onToggleMirror,
                        testTag = "toggle_mirror_button",
                        badgeSize = row2BadgeSize,
                        iconSize = row2IconSize,
                        textSize = row2TextSize,
                        spacing = row2Spacing
                    )
                }

                // Rule-of-Thirds Composition Grid Overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ControlButton(
                        icon = Icons.Default.GridOn,
                        label = if (config.isGridVisible) "Grid ON" else "Grid",
                        isActive = config.isGridVisible,
                        activeColor = CyanAccent,
                        onClick = onToggleGrid,
                        testTag = "toggle_grid_button",
                        badgeSize = row2BadgeSize,
                        iconSize = row2IconSize,
                        textSize = row2TextSize,
                        spacing = row2Spacing
                    )
                }

                // Battery Saver Blackout Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ControlButton(
                        icon = Icons.Default.DarkMode,
                        label = "Dim",
                        isActive = false,
                        onClick = onToggleBlackout,
                        testTag = "dim_screen_button",
                        badgeSize = row2BadgeSize,
                        iconSize = row2IconSize,
                        textSize = row2TextSize,
                        spacing = row2Spacing
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToggleIconButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeSize: Dp,
    iconSize: Dp,
    textSize: TextUnit,
    spacing: Dp,
    activeColor: Color = EmeraldLive,
    inactiveColor: Color = RoseError
) {
    val tintColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        label = "quickToggleTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.4f) else inactiveColor.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.height(spacing))
        Text(
            text = label,
            fontSize = textSize,
            lineHeight = textSize * 1.15f,
            fontWeight = FontWeight.Bold,
            color = tintColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeSize: Dp,
    iconSize: Dp,
    textSize: TextUnit,
    spacing: Dp,
    activeColor: Color = CyanAccent,
    inactiveColor: Color = TextSecondary
) {
    val tintColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        label = "tintColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.15f) else InsetBackground)
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.4f) else Color(0xFF1E293B),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.height(spacing))
        Text(
            text = label,
            fontSize = textSize,
            lineHeight = textSize * 1.15f,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = tintColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true
        )
    }
}
