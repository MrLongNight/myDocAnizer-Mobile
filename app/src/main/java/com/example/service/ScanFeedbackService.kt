package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ScanFeedbackService {

    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (ignored: Exception) {}
    }

    /**
     * Spielt akustisches Feedback gemäß gewähltem Sound-Profil ab.
     */
    fun playCaptureSound(soundProfile: String) {
        val tg = toneGen ?: try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 85).also { toneGen = it }
        } catch (e: Exception) { null } ?: return

        when (soundProfile.uppercase()) {
            "CLICK" -> {
                // Kurzer, mechanischer Klick
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
                } catch (ignored: Exception) {}
            }
            "SUCCESS_BEEP", "BEEP", "GONG" -> {
                // Freundlicher Bestätigungston (Standard)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                } catch (ignored: Exception) {}
            }
            "CHIME", "SUBTLE" -> {
                // Moderner Doppel-Chime (hoch-tief)
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        tg.startTone(ToneGenerator.TONE_CDMA_HIGH_SS, 80)
                        delay(90L)
                        tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 110)
                    } catch (ignored: Exception) {}
                }
            }
            "MUTE", "OFF" -> {
                // Lautlos
            }
            else -> {
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Optionales haptisches Feedback (Standard: AUS).
     */
    fun triggerVibration(context: Context, enabled: Boolean) {
        if (!enabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(70L)
                }
            }
        } catch (ignored: Exception) {}
    }
}
