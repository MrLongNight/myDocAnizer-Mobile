package com.example.service

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * FIDO2 / WebAuthn Passkey Manager unter Verwendung von AndroidX CredentialManager.
 * Ermöglicht passwortloses, kryptografisch gesichertes Anmelden via biometrische Hardware-Keys.
 */
class PasskeyAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Erstellt eine standardkonforme WebAuthn JSON-Registrierungsanforderung (Registration JSON).
     */
    fun createRegistrationJson(userName: String, rpId: String = "mydocanizer.local"): String {
        val challengeBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().toByteArray())
        val userIdBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(userName.toByteArray())

        val pubKeyCredParams = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "public-key")
                put("alg", -7) // ES256
            })
            put(JSONObject().apply {
                put("type", "public-key")
                put("alg", -257) // RS256
            })
        }

        val json = JSONObject().apply {
            put("challenge", challengeBase64)
            put("rp", JSONObject().apply {
                put("name", "myDocAnizer App")
                put("id", rpId)
            })
            put("user", JSONObject().apply {
                put("id", userIdBase64)
                put("name", userName)
                put("displayName", userName)
            })
            put("pubKeyCredParams", pubKeyCredParams)
            put("timeout", 60000)
            put("attestation", "none")
            put("authenticatorSelection", JSONObject().apply {
                put("authenticatorAttachment", "platform")
                put("requireResidentKey", true)
                put("residentKey", "required")
                put("userVerification", "required")
            })
        }
        return json.toString()
    }

    /**
     * Erstellt eine standardkonforme WebAuthn JSON-Authentifizierungsanforderung (Authentication JSON).
     */
    fun createAuthenticationJson(rpId: String = "mydocanizer.local"): String {
        val challengeBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().toByteArray())
        val json = JSONObject().apply {
            put("challenge", challengeBase64)
            put("timeout", 60000)
            put("rpId", rpId)
            put("userVerification", "required")
        }
        return json.toString()
    }

    /**
     * Führt die Passkey-Erstellung auf dem Android-Gerät über den System-CredentialManager aus.
     */
    suspend fun registerPasskey(
        activityContext: Context,
        userName: String,
        rpId: String = "mydocanizer.local"
    ): Result<String> = withContext(Dispatchers.Main) {
        try {
            val requestJson = createRegistrationJson(userName, rpId)
            val createRequest = CreatePublicKeyCredentialRequest(requestJson = requestJson)
            val result = credentialManager.createCredential(activityContext, createRequest)
            Result.success("Passkey für '$userName' erfolgreich im Google Credential Manager / Hardware-Keystore registriert.")
        } catch (e: CreateCredentialCancellationException) {
            Result.failure(Exception("Passkey-Einrichtung vom Benutzer abgebrochen."))
        } catch (e: CreateCredentialException) {
            Result.failure(Exception("Passkey-Fehler: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Führt die Passkey-Authentifizierung über den System-CredentialManager aus.
     */
    suspend fun authenticatePasskey(
        activityContext: Context,
        rpId: String = "mydocanizer.local"
    ): Result<String> = withContext(Dispatchers.Main) {
        try {
            val requestJson = createAuthenticationJson(rpId)
            val getOption = GetPublicKeyCredentialOption(requestJson = requestJson)
            val getRequest = GetCredentialRequest(listOf(getOption))
            val result = credentialManager.getCredential(activityContext, getRequest)
            Result.success("Passkey-Authentifizierung erfolgreich!")
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Passkey-Anmeldung abgebrochen."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Passkey-Prüfung fehlgeschlagen: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
