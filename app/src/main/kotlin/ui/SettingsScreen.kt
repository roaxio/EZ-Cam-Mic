package io.github.roaxio.ezcammic.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.roaxio.ezcammic.camera.CameraSensorDiscovery
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.CameraSensorInfo
import io.github.roaxio.ezcammic.ui.theme.AmberWarning
import io.github.roaxio.ezcammic.ui.theme.CardBackground
import io.github.roaxio.ezcammic.ui.theme.CyanAccent
import io.github.roaxio.ezcammic.ui.theme.EmeraldLive
import io.github.roaxio.ezcammic.ui.theme.InsetBackground
import io.github.roaxio.ezcammic.ui.theme.TextPrimary
import io.github.roaxio.ezcammic.ui.theme.TextSecondary
import io.github.roaxio.ezcammic.viewmodel.WebcamViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WebcamViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val availableSensors by viewModel.availableSensors.collectAsState()
    val selectedSensorId by viewModel.selectedSettingsSensorId.collectAsState()
    val showRestartPrompt by viewModel.showStreamRestartPrompt.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isCurrentOrientationLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var portText by remember { mutableStateOf(config.port.toString()) }
    var usernameText by remember { mutableStateOf(config.authUsername) }
    var passwordText by remember { mutableStateOf(config.authPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Top-Level: Selected camera sensor in Settings to configure
    var activeSettingsSensorId by remember(selectedSensorId, availableSensors) {
        mutableStateOf(
            if (availableSensors.any { it.cameraId == selectedSensorId }) selectedSensorId
            else availableSensors.firstOrNull()?.cameraId ?: "0"
        )
    }

    val activeSensor = availableSensors.find { it.cameraId == activeSettingsSensorId }
        ?: availableSensors.firstOrNull()
        ?: CameraSensorInfo("0", CameraFacing.BACK, "Rear Main Camera")

    // Current profile for the selected camera sensor
    val currentSensorProfile = config.getProfileForSensor(activeSettingsSensorId)

    // Selected tab in settings: true for Horizontal (Landscape), false for Vertical (Portrait)
    var selectedProfileIsLandscape by remember { mutableStateOf(isCurrentOrientationLandscape) }

    // Discover sensor-supported aspect ratios & resolutions for the selected sensor
    val (landscapeRatios, portraitRatios) = remember(activeSettingsSensorId) {
        CameraSensorDiscovery.getSupportedAspectRatiosForSensor(context, activeSettingsSensorId)
    }

    val supportedFpsMap by viewModel.supportedFpsMap.collectAsState()

    val availableLandscapeResolutions = remember(activeSettingsSensorId, currentSensorProfile.landscapeAspectRatio) {
        CameraSensorDiscovery.getResolutionsForSensorAndAspectRatio(
            context,
            activeSettingsSensorId,
            currentSensorProfile.landscapeAspectRatio
        )
    }

    val availablePortraitResolutions = remember(activeSettingsSensorId, currentSensorProfile.portraitAspectRatio) {
        CameraSensorDiscovery.getResolutionsForSensorAndAspectRatio(
            context,
            activeSettingsSensorId,
            currentSensorProfile.portraitAspectRatio
        )
    }

    Dialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.92f)
                .testTag("settings_dialog_card"),
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
                // Fixed Header Section (matching UsbSetupGuideDialog pattern)
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
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Settings & Profiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sensor profiles & camera hardware controls",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("settings_screen_content")
                ) {
            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // TOP LEVEL LAYER: CAMERA SENSOR SELECTION (AVAILABLE PHONE SENSORS)
            // =========================================================================

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "CAMERA SENSORS & PROFILES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select any available phone camera sensor to configure its aspect ratios and resolutions for both orientations.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sensor Selector Cards (Horizontal scrollable row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSensors.forEach { sensor ->
                    val isSelectedInSettings = sensor.cameraId == activeSettingsSensorId
                    val isLiveOnCamera = sensor.cameraId == config.selectedCameraId

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelectedInSettings) CyanAccent.copy(alpha = 0.16f) else InsetBackground)
                            .border(
                                width = if (isSelectedInSettings) 1.5.dp else 1.dp,
                                color = if (isSelectedInSettings) CyanAccent else Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { activeSettingsSensorId = sensor.cameraId }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (sensor.facing == CameraFacing.FRONT) Icons.Default.Cameraswitch else Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (isSelectedInSettings) CyanAccent else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = sensor.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelectedInSettings) TextPrimary else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ID: ${sensor.cameraId}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                if (isLiveOnCamera) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(EmeraldLive.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "● LIVE ON PHONE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldLive
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick live switch button if the active setting sensor is not live
            if (activeSensor.cameraId != config.selectedCameraId) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.selectCameraSensor(activeSensor.cameraId, lifecycleOwner)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Switch live camera to ${activeSensor.displayName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            softWrap = true
                        )
                    }
                    Text(
                        text = "SWITCH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // DUAL ORIENTATION PROFILES: HORIZONTAL & VERTICAL CONCURRENT CONFIGURATION
            // =========================================================================

            Text(
                text = "ORIENTATION PROFILES (${activeSensor.displayName.uppercase()})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Independent aspect ratios & resolutions configured for this sensor. Automatically switches when phone orientation changes.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Orientation Profile Switcher Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InsetBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Horizontal / Landscape Tab
                val isLandTab = selectedProfileIsLandscape
                val isSensorLive = activeSensor.cameraId == config.selectedCameraId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLandTab) CyanAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            width = if (isLandTab) 1.5.dp else 0.dp,
                            color = if (isLandTab) CyanAccent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedProfileIsLandscape = true }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropLandscape,
                                contentDescription = null,
                                tint = if (isLandTab) CyanAccent else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Horizontal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLandTab) TextPrimary else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentSensorProfile.landscapeAspectRatio.id} • ${currentSensorProfile.landscapeResolution.badge}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isLandTab) CyanAccent else Color(0xFF64748B)
                        )
                        if (isCurrentOrientationLandscape && isSensorLive) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "● LIVE ON PHONE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLive,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Vertical / Portrait Tab
                val isPortTab = !selectedProfileIsLandscape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPortTab) CyanAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            width = if (isPortTab) 1.5.dp else 0.dp,
                            color = if (isPortTab) CyanAccent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedProfileIsLandscape = false }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropPortrait,
                                contentDescription = null,
                                tint = if (isPortTab) CyanAccent else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Vertical",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPortTab) TextPrimary else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentSensorProfile.portraitAspectRatio.id} • ${currentSensorProfile.portraitResolution.badge}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isPortTab) CyanAccent else Color(0xFF64748B)
                        )
                        if (!isCurrentOrientationLandscape && isSensorLive) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "● LIVE ON PHONE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLive,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // SELECTED PROFILE CASCADING CONFIGURATION
            // ==========================================
            if (selectedProfileIsLandscape) {
                // LANDSCAPE CONFIGURATION
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "HORIZONTAL ASPECT RATIO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    landscapeRatios.forEach { ratio ->
                        val isSelected = currentSensorProfile.landscapeAspectRatio.id == ratio.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent.copy(alpha = 0.16f) else InsetBackground)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CyanAccent else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setSensorLandscapeAspectRatio(activeSensor.cameraId, ratio, lifecycleOwner)
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CropLandscape,
                                    contentDescription = null,
                                    tint = if (isSelected) CyanAccent else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ratio.id,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = if (ratio.id == "16:9") "Widescreen" else if (ratio.id == "4:3") "Standard" else "Square",
                                    fontSize = 10.sp,
                                    color = if (isSelected) CyanAccent else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Landscape Resolutions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HORIZONTAL RESOLUTION (${currentSensorProfile.landscapeAspectRatio.id})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${availableLandscapeResolutions.size} matching",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableLandscapeResolutions.forEach { res ->
                        val isSelected = currentSensorProfile.landscapeResolution.width == res.width &&
                                currentSensorProfile.landscapeResolution.height == res.height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent.copy(alpha = 0.12f) else InsetBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CyanAccent else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setSensorLandscapeResolution(activeSensor.cameraId, res, lifecycleOwner)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = res.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    softWrap = true
                                )
                                Text(
                                    text = "${res.width} x ${res.height} px • ${res.badge}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) CyanAccent else Color(0xFF64748B),
                                    softWrap = true
                                )
                            }
                            if (isSelected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SELECTED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // PORTRAIT CONFIGURATION
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "VERTICAL ASPECT RATIO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    portraitRatios.forEach { ratio ->
                        val isSelected = currentSensorProfile.portraitAspectRatio.id == ratio.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent.copy(alpha = 0.16f) else InsetBackground)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CyanAccent else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setSensorPortraitAspectRatio(activeSensor.cameraId, ratio, lifecycleOwner)
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CropPortrait,
                                    contentDescription = null,
                                    tint = if (isSelected) CyanAccent else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ratio.id,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = if (ratio.id == "9:16") "Story/Reel" else if (ratio.id == "3:4") "Standard" else "Square",
                                    fontSize = 10.sp,
                                    color = if (isSelected) CyanAccent else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Portrait Resolutions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERTICAL RESOLUTION (${currentSensorProfile.portraitAspectRatio.id})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${availablePortraitResolutions.size} matching",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availablePortraitResolutions.forEach { res ->
                        val isSelected = currentSensorProfile.portraitResolution.width == res.width &&
                                currentSensorProfile.portraitResolution.height == res.height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent.copy(alpha = 0.12f) else InsetBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CyanAccent else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setSensorPortraitResolution(activeSensor.cameraId, res, lifecycleOwner)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = res.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    softWrap = true
                                )
                                Text(
                                    text = "${res.width} x ${res.height} px • ${res.badge}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) CyanAccent else Color(0xFF64748B),
                                    softWrap = true
                                )
                            }
                            if (isSelected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SELECTED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Target FPS
            Text(
                text = "TARGET FRAMERATE (FPS)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Only the FPS values this sensor supports at the resolution of the profile shown above.
            val fpsResolution = if (selectedProfileIsLandscape) currentSensorProfile.landscapeResolution
                else currentSensorProfile.portraitResolution
            val fpsOptions = remember(supportedFpsMap, activeSettingsSensorId, fpsResolution) {
                viewModel.fpsOptionsFor(activeSettingsSensorId, fpsResolution)
            }
            // Up to 4 chips per row (wraps on narrow screens); a single row of <= 4 looks as before.
            val fpsChipsPerRow = fpsOptions.size.coerceIn(1, 4)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fpsOptions.chunked(fpsChipsPerRow).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { fpsOption ->
                            val isSelected = config.targetFps == fpsOption
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CyanAccent.copy(alpha = 0.15f) else InsetBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyanAccent else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setTargetFps(fpsOption) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$fpsOption FPS",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanAccent else TextSecondary
                                )
                            }
                        }
                        // Keep chip widths equal in a shorter last row.
                        repeat(fpsChipsPerRow - rowOptions.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stream Orientation Lock Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .border(
                        1.dp,
                        if (config.lockHorizontalStream) CyanAccent.copy(alpha = 0.4f) else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lock Stream to Horizontal (Landscape)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Forces stream to stay horizontal 16:9 regardless of phone rotation",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = config.lockHorizontalStream,
                    onCheckedChange = { viewModel.setLockHorizontalStream(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rule-of-Thirds Grid Overlay Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InsetBackground)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Rule-of-Thirds Grid Overlay",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Displays 3x3 composition framing guidelines on the camera preview",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = config.isGridVisible,
                    onCheckedChange = { viewModel.toggleGrid() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. JPEG Quality Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JPEG COMPRESSION QUALITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${config.jpegQuality}%",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
            }
            Slider(
                value = config.jpegQuality.toFloat(),
                onValueChange = { viewModel.setJpegQuality(it.toInt()) },
                valueRange = 30f..95f,
                steps = 12,
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = InsetBackground
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Digital Zoom Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIGITAL ZOOM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = String.format(Locale.US, "%.1fx", config.zoomLevel),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
            }
            Slider(
                value = config.zoomLevel,
                onValueChange = { viewModel.setZoomLevel(it) },
                valueRange = 1.0f..5.0f,
                steps = 40,
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = InsetBackground
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Audio Settings
            Text(
                text = "AUDIO & MICROPHONE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Mic Gain Boost", fontSize = 13.sp, color = TextPrimary)
                Text(
                    text = String.format(Locale.US, "%.1fx", config.audioGain),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLive
                )
            }
            Slider(
                value = config.audioGain,
                onValueChange = { viewModel.setAudioGain(it) },
                valueRange = 0.5f..3.0f,
                steps = 25,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldLive,
                    activeTrackColor = EmeraldLive,
                    inactiveTrackColor = InsetBackground
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sampling rate selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(16000 to "16 kHz", 44100 to "44.1 kHz", 48000 to "48 kHz").forEach { (rate, label) ->
                    val isSelected = config.audioSampleRate == rate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EmeraldLive.copy(alpha = 0.15f) else InsetBackground)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldLive else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setAudioSampleRate(rate) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EmeraldLive else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Streaming Channels & Interfaces
            Text(
                text = "STREAMING CHANNELS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Wi-Fi Web Streaming Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .border(
                        1.dp,
                        if (config.isWebStreamingEnabled) EmeraldLive.copy(alpha = 0.4f) else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (config.isWebStreamingEnabled) EmeraldLive else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Wi-Fi Web & OBS Streaming",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            softWrap = true
                        )
                        Text(
                            text = "Broadcasts stream across local Wi-Fi network",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            softWrap = true
                        )
                    }
                }
                Switch(
                    checked = config.isWebStreamingEnabled,
                    onCheckedChange = { viewModel.setWebStreamingEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldLive,
                        checkedTrackColor = EmeraldLive.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // USB Wired Streaming Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .border(
                        1.dp,
                        if (config.isUsbStreamingEnabled) CyanAccent.copy(alpha = 0.4f) else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (config.isUsbStreamingEnabled) CyanAccent else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "USB Wired Streaming",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            softWrap = true
                        )
                        Text(
                            text = "Permits high-speed wired stream over USB Tethering cable",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            softWrap = true
                        )
                    }
                }
                Switch(
                    checked = config.isUsbStreamingEnabled,
                    onCheckedChange = { viewModel.setUsbStreamingEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Security & Protocol (HTTPS & Authentication)
            Text(
                text = "SECURITY & PROTOCOL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // HTTPS Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .border(
                        1.dp,
                        if (config.isHttpsEnabled) EmeraldLive.copy(alpha = 0.4f) else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (config.isHttpsEnabled) EmeraldLive else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "HTTPS Protocol (SSL/TLS)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            softWrap = true
                        )
                        Text(
                            text = "Enables secure TLS encrypted local web share",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            softWrap = true
                        )
                    }
                }
                Switch(
                    checked = config.isHttpsEnabled,
                    onCheckedChange = { viewModel.setHttpsEnabled(it, lifecycleOwner) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldLive,
                        checkedTrackColor = EmeraldLive.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Authentication Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .border(
                        1.dp,
                        if (config.isAuthEnabled) CyanAccent.copy(alpha = 0.4f) else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (config.isAuthEnabled) CyanAccent else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Custom User Authentication",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            softWrap = true
                        )
                        Text(
                            text = "Require username & password for web & feeds",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            softWrap = true
                        )
                    }
                }
                Switch(
                    checked = config.isAuthEnabled,
                    onCheckedChange = { viewModel.setAuthEnabled(it, lifecycleOwner) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            AnimatedVisibility(visible = config.isAuthEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(InsetBackground)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!config.isHttpsEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Cleartext HTTP Credential Warning",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "HTTP Basic Auth sends credentials unencrypted. If streaming over shared or public Wi-Fi, enable HTTPS (SSL/TLS) above to encrypt credentials and feeds.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "CUSTOM CREDENTIALS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )

                    OutlinedTextField(
                        value = usernameText,
                        onValueChange = {
                            usernameText = it
                            viewModel.setAuthCredentials(it, passwordText, lifecycleOwner)
                        },
                        label = { Text("Web Share Username") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            viewModel.setAuthCredentials(usernameText, it, lifecycleOwner)
                        },
                        label = { Text("Web Share Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide Password" else "Show Password",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Server Port & Screen Awake
            Text(
                text = "NETWORK & SYSTEM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it
                    it.toIntOrNull()?.let { p ->
                        if (p in 1024..65535) viewModel.setPort(p, lifecycleOwner)
                    }
                },
                label = { Text("Server Port (HTTP/HTTPS)") },
                placeholder = { Text("4747") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedLabelColor = CyanAccent,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = InsetBackground,
                    unfocusedContainerColor = InsetBackground
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Keep Screen Awake", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(text = "Prevents device sleep while streaming", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = config.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InsetBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Auto-Start Streaming on Launch", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(text = "Instantly start broadcasting when app opens", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = config.autoStartStreaming,
                    onCheckedChange = { viewModel.setAutoStartStreaming(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldLive,
                        checkedTrackColor = EmeraldLive.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
        }
    }

    if (showRestartPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowStreamRestartPrompt(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Stream Restart Required",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "You changed the aspect ratio or resolution while streaming is live. Please stop and start the stream manually from the main screen for the new hardware mode to take effect.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            confirmButton = {
                Button(
                    onClick = { viewModel.setShowStreamRestartPrompt(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it", color = Color(0xFF0A0E17), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
