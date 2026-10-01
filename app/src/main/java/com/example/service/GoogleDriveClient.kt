package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Nativer Client für die Google Drive REST API v3.
 * Überträgt verschlüsselte Dokument-Archive (.enc) und Disaster-Recovery-Indizes
 * direkt an den Google Drive Cloud-Speicher.
 */
object GoogleDriveClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Prüft die Gültigkeit eines Google OAuth-Tokens und ruft Account-Informationen ab.
     */
    suspend fun checkAccountInfo(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Kein Google OAuth-Token hinterlegt."))
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val user = json.optJSONObject("user")
                    val email = user?.optString("emailAddress") ?: "Google-Konto"
                    val displayName = user?.optString("displayName") ?: ""
                    Result.success("Verbunden mit $displayName ($email)")
                } else {
                    Result.failure(IOException("Google Drive API Fehler (HTTP ${resp.code}): ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lädt eine verschlüsselte Datei (.enc) per Google Drive v3 Multipart-Upload hoch.
     */
    suspend fun uploadEncryptedFile(
        accessToken: String,
        file: File,
        remoteName: String = file.name,
        folderName: String = "myDocAnizer_Vault"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Google Drive Synchronisation erfordert ein gültiges OAuth-Token."))
            }
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Lokale Datei existiert nicht: ${file.name}"))
            }

            // Metadaten JSON
            val metadataJson = JSONObject().apply {
                put("name", remoteName)
                put("description", "myDocAnizer Zero-Knowledge Encrypted Backup")
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    "metadata.json",
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
                )
                .addFormDataPart(
                    "file",
                    remoteName,
                    file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "{}")
                    val fileId = respJson.optString("id", "")
                    Result.success(fileId)
                } else {
                    Result.failure(IOException("Google Drive Upload fehlgeschlagen: HTTP ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
