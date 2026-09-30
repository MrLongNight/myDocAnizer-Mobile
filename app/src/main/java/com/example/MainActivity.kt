package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppStartSplashAnimation
import com.example.ui.components.SplashSoundSynthesizer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.views.MainScreen

class MainActivity : FragmentActivity() {
    private val viewModel: DocAnizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.service.AppAuditLogger.init(this)
        SplashSoundSynthesizer.precompute(this) // Pre-generate the premium audio to prevent initialization latency
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val colorSkin by viewModel.colorSkin.collectAsStateWithLifecycle()
            val highContrastMode by viewModel.highContrastMode.collectAsStateWithLifecycle()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val targetTab = intent.getStringExtra("EXTRA_START_TAB")
        if (!targetTab.isNullOrBlank()) {
            viewModel.setTargetNavigationTab(targetTab)
        }
        val action = intent.getStringExtra("EXTRA_OPEN_ACTION")
        if (action == "CASH_EXPENSE") {
            viewModel.setDirectOpenCashTracker(true)
        }
        if (intent.getBooleanExtra("EXTRA_OPEN_RECONCILIATION", false)) {
            viewModel.setDirectOpenReconciliation(true)
        }
    }
}
