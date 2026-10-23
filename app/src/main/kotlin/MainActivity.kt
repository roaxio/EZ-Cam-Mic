package io.github.roaxio.ezcammic

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.roaxio.ezcammic.ui.WebcamStudioScreen
import io.github.roaxio.ezcammic.ui.SettingsScreen
import io.github.roaxio.ezcammic.ui.theme.EzCamMicTheme
import io.github.roaxio.ezcammic.viewmodel.WebcamViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WebcamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EzCamMicTheme {
                val navController = rememberNavController()
                val config by viewModel.config.collectAsState()
                val stats by viewModel.stats.collectAsState()

                // Keep screen on management
                LaunchedEffect(config.keepScreenOn) {
                    if (config.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Rule 15 & Rule 22.5: Dynamic Configuration Pinning during in-flight hardware capture
                // Freezes and pins the orientation during active streaming to prevent pipeline recreation and frame drops,
                // and symmetrically restores dynamic auto-rotation upon session termination via pure reactive state.
                LaunchedEffect(Unit) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }

                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                val startupPermissions = remember {
                    buildList {
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasCameraPermission = permissions[Manifest.permission.CAMERA] == true ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission || !hasAudioPermission || !hasNotificationPermission) {
                        permissionLauncher.launch(startupPermissions)
                    }
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        WebcamStudioScreen(
                            viewModel = viewModel,
                            hasCameraPermission = hasCameraPermission,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermissions = {
                                permissionLauncher.launch(startupPermissions)
                            },
                            onNavigateToSettings = { viewModel.setShowSettingsSheet(true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopStreaming()
    }
}
