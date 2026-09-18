package com.visionaid.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visionaid.app.presentation.ui.assistant.AssistantScreen
import com.visionaid.app.presentation.ui.detection.DetectionScreen
import com.visionaid.app.presentation.ui.emergency.EmergencyScreen
import com.visionaid.app.presentation.ui.main.MainScreen
import com.visionaid.app.presentation.ui.settings.SettingsScreen
import com.visionaid.app.presentation.ui.setup.SetupScreen

/** Route names. */
object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val DETECTION = "detection"
    const val ASSISTANT = "assistant"
    const val EMERGENCY = "emergency"
    const val SETTINGS = "settings"
}

/**
 * App navigation graph. The start destination depends on whether the
 * first-time setup has been completed.
 */
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val viewModel: NavViewModel = hiltViewModel()
    val settings by viewModel.preferences.settingsFlow
        .collectAsStateWithLifecycle(initialValue = null)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = settings) {
            // First frame: wait for DataStore before deciding where to go.
            null -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
            else -> NavHost(
                navController = navController,
                startDestination = if (s.setupCompleted) Routes.MAIN else Routes.SETUP
            ) {
                composable(Routes.SETUP) {
                    SetupScreen(
                        onFinished = {
                            navController.navigate(Routes.MAIN) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.MAIN) {
                    MainScreen(
                        onOpenDetection = { navController.navigate(Routes.DETECTION) },
                        onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                        onOpenEmergency = { navController.navigate(Routes.EMERGENCY) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.DETECTION) {
                    DetectionScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ASSISTANT) {
                    AssistantScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.EMERGENCY) {
                    EmergencyScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
