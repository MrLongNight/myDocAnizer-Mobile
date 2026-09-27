package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppStartSplashAnimation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.views.MainScreen

class MainActivity : FragmentActivity() {
    private val viewModel: DocAnizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val colorSkin by viewModel.colorSkin.collectAsState()
            val highContrastMode by viewModel.highContrastMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            var showSplash by rememberSaveable { mutableStateOf(true) }

            MyApplicationTheme(
                darkTheme = isDark,
                colorSkin = colorSkin,
                highContrast = highContrastMode
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen(viewModel = viewModel)

                        if (showSplash) {
                            AppStartSplashAnimation(
                                onAnimationFinished = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val settings = viewModel.scannerSettings.value
            if (settings.scanMode == "MANUAL" && settings.enableHardwareButtons) {
                viewModel.triggerHardwareCapture()
                return true // Konsumiert den Tastendruck für die Kamera-Auslösung nur im manuellen Modus
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
