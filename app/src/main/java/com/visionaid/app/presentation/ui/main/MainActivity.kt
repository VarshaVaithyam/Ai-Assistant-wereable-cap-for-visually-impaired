package com.visionaid.app.presentation.ui.main

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.visionaid.app.presentation.navigation.NavGraph
import com.visionaid.app.presentation.theme.VisionAidTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity Compose host (spec 11).
 *
 * The screen stays on while the app is visible: a blind user cannot find
 * the power button reliably mid-walk.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            VisionAidTheme {
                NavGraph()
            }
        }
    }
}
