package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Vollwertiger, nativer WebDAV-Client für Nextcloud, ownCloud, Synology NAS, Fritz!NAS und universelle WebDAV-Server.
 * Führt echte HTTP-WebDAV-Anfragen (PROPFIND, MKCOL, PUT, GET, DELETE) mit Authentifizierung aus.
 */
object WebDavClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Testet die Erreichbarkeit und Authentifizierung eines WebDAV-Servers per echtem HTTP PROPFIND / HEAD.
     */
    suspend fun testConnection(url: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(url)
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("WebDAV-Server-URL darf nicht leer sein."))
            }

            val credential = if (user.isNotBlank() || pass.isNotBlank()) Credentials.basic(user, pass) else null

            // Versuche PROPFIND (WebDAV-Standard), mit Fallback auf GET
            val propfindXml = """<?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop><D:displayname/><D:resourcetype/></D:prop>
                </D:propfind>
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url(cleanUrl)
                .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
                .header("Depth", "0")

            if (credential != null) {
                requestBuilder.header("Authorization", credential)
            }

            val request = requestBuilder.build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                // Fallback auf HEAD / GET, falls der Server die Methode PROPFIND ablehnt
                val headRequest = Request.Builder().url(cleanUrl).apply {
                    if (credential != null) header("Authorization", credential)
                }.build()
                httpClient.newCall(headRequest).execute()
            }

            response.use { resp ->
                when (resp.code) {
                    200, 207, 204 -> {
                        Result.success("WebDAV-Verbindung erfolgreich hergestellt! (HTTP ${resp.code} ${resp.message})")
                    }
                    401, 403 -> {
                        Result.failure(IOException("Authentifizierung fehlgeschlagen (HTTP ${resp.code}): Benutzername oder Passwort ungültig."))
                    }
                    404 -> {
                        Result.failure(IOException("WebDAV-Pfad nicht gefunden (HTTP 404). Bitte Zielordner prüfen."))
                    }
                    else -> {
                        Result.failure(IOException("WebDAV-Server meldet Status: HTTP ${resp.code} (${resp.message})"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("Netzwerkfehler beim WebDAV-Handshake: ${e.localizedMessage ?: e.message}", e))
        }
    }

    /**
     * Erstellt einen Ordner auf dem WebDAV-Server via HTTP MKCOL.
     */
    suspend fun createFolder(url: String, user: String, pass: String, folderName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(url)
            val folderUrl = if (cleanUrl.endsWith("/")) "$cleanUrl$folderName/" else "$cleanUrl/$folderName/"
            val credential = if (user.isNotBlank() || pass.isNotBlank()) Credentials.basic(user, pass) else null

            val request = Request.Builder()
                .url(folderUrl)
                .method("MKCOL", "".toRequestBody(null))
                .apply {
                    if (credential != null) header("Authorization", credential)
                }
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 405 || resp.code == 201) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("MKCOL fehlgeschlagen: HTTP ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lädt eine Datei per HTTP PUT auf das NAS/WebDAV hoch.
     */
    suspend fun uploadFile(
        url: String,
        user: String,
        pass: String,
        remoteFileName: String,
        localFile: File,
        mimeType: String = "application/octet-stream"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!localFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Lokale Datei existiert nicht: ${localFile.name}"))
            }

            val cleanUrl = sanitizeUrl(url)
            val targetUrl = if (cleanUrl.endsWith("/")) "$cleanUrl$remoteFileName" else "$cleanUrl/$remoteFileName"
            val credential = if (user.isNotBlank() || pass.isNotBlank()) Credentials.basic(user, pass) else null

            val body = localFile.asRequestBody(mimeType.toMediaTypeOrNull())

            val request = Request.Builder()
                .url(targetUrl)
                .put(body)
                .apply {
                    if (credential != null) header("Authorization", credential)
                }
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code in 200..204) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("WebDAV PUT Upload fehlgeschlagen: HTTP ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lädt eine Datei per HTTP GET vom WebDAV-Server herunter.
     */
    suspend fun downloadFile(
        url: String,
        user: String,
        pass: String,
        remoteFileName: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(url)
            val targetUrl = if (cleanUrl.endsWith("/")) "$cleanUrl$remoteFileName" else "$cleanUrl/$remoteFileName"
            val credential = if (user.isNotBlank() || pass.isNotBlank()) Credentials.basic(user, pass) else null

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .apply {
                    if (credential != null) header("Authorization", credential)
                }
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("WebDAV GET Download fehlgeschlagen: HTTP ${resp.code}"))
                }
                val bodyStream = resp.body?.byteStream() ?: return@withContext Result.failure(IOException("Leere Server-Antwort"))
                FileOutputStream(destinationFile).use { out ->
                    bodyStream.copyTo(out)
                }
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isBlank()) return ""
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }
}
