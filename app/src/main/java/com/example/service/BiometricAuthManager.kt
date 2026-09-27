package com.example.service

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthManager(private val context: Context) {

    enum class BiometricHardwareStatus {
        AVAILABLE,
        NOT_ENROLLED,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        UNKNOWN
    }

    // Android BiometricPrompt verbietet die Kombination von BIOMETRIC_WEAK mit DEVICE_CREDENTIAL
    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /**
     * Prüft, ob Biometrie oder Geräte-PIN/Muster auf dem Gerät unterstützt werden.
     */
    fun checkBiometricAvailability(): BiometricHardwareStatus {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(allowedAuthenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricHardwareStatus.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricHardwareStatus.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricHardwareStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricHardwareStatus.HARDWARE_UNAVAILABLE
                else -> BiometricHardwareStatus.UNKNOWN
            }
        } catch (e: Exception) {
            BiometricHardwareStatus.UNKNOWN
        }
    }

    /**
     * Benutzerfreundliche Statustext-Beschreibung für den Setup-Wizard und die Einstellungen.
     */
    fun getHardwareStatusLabel(): String {
        return when (checkBiometricAvailability()) {
            BiometricHardwareStatus.AVAILABLE -> "Biometrie bereit (Fingerabdruck / Gesicht / PIN)"
            BiometricHardwareStatus.NOT_ENROLLED -> "Hardware vorhanden, aber noch kein Fingerabdruck/Gesicht im System registriert"
            BiometricHardwareStatus.NO_HARDWARE -> "Keine biometrische Hardware gefunden (Geräte-PIN nutzbar)"
            BiometricHardwareStatus.HARDWARE_UNAVAILABLE -> "Biometrische Sensoren momentan nicht verfügbar"
            BiometricHardwareStatus.UNKNOWN -> "Status konnte nicht ermittelt werden"
        }
    }

    /**
     * Startet den nativen Android BiometricPrompt Dialog mit Hardware-Biometrie und PIN/Muster-Fallback.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "myDocAnizer autorisieren",
        subtitle: String = "Entsperre die App mit deinem Fingerabdruck, Gesicht oder deiner Geräte-PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("Aktivität wurde beendet")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // errorCode 10 / 13 sind Benutzerabbrüche (User canceled / Back)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Einzelner Fehlversuch (z. B. Finger nicht erkannt), BiometricPrompt bleibt i.d.R. offen
                }
            }
        )

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("Fehler beim Starten der Authentifizierung: ${e.localizedMessage}")
        }
    }
}
