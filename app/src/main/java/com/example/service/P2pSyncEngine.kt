package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class P2pSyncEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val localDeviceNameProvider: () -> String
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Aktiver Fortschritt des Syncs
    private val _syncProgress = MutableStateFlow(P2pSyncProgressState())
    val syncProgress: StateFlow<P2pSyncProgressState> = _syncProgress.asStateFlow()

    // Aktive Konflikte, die manuelle Entscheidung erfordern
    private val _activeConflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val activeConflicts: StateFlow<List<SyncConflict>> = _activeConflicts.asStateFlow()

    // Sync-Historie / Audit-Log
    private val _syncHistory = MutableStateFlow<List<P2pSyncLog>>(emptyList())
    val syncHistory: StateFlow<List<P2pSyncLog>> = _syncHistory.asStateFlow()

    /**
     * Sendet eine Kopplungsanfrage an ein anderes Gerät (z.B. PC oder zweites Handy).
     */
    suspend fun requestPairingWithPeer(
        peerIp: String,
        peerPort: Int,
        pin: String
    ): Result<PairingResult> = withContext(Dispatchers.IO) {
        val clientDeviceId = UUID.randomUUID().toString()
        val clientName = localDeviceNameProvider()

        val json = JSONObject().apply {
            put("clientDeviceId", clientDeviceId)
            put("clientDeviceName", clientName)
            put("pin", pin.replace(" ", "").trim())
        }

        val url = "http://$peerIp:$peerPort/api/pair/request"
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()

        try {
            val response = httpClient.newCall(req).execute()
            val respStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = runCatching { JSONObject(respStr) }.getOrNull()
                val errMsg = errObj?.optString("error") ?: "Verbindung fehlgeschlagen (HTTP ${response.code})"
                return@withContext Result.failure(Exception(errMsg))
            }

            val respObj = JSONObject(respStr)
            val reqId = respObj.optString("requestId")

            // Kurzes Warten / Polling auf Autorisierung durch den Host
            var authorizedSecret: String? = null
            var hostDeviceName = "Verbundener PC/Partner"

            for (attempt in 1..20) {
                kotlinx.coroutines.delay(1000)
                val statusUrl = "http://$peerIp:$peerPort/api/pair/status?requestId=$reqId"
                val statusReq = Request.Builder().url(statusUrl).get().build()
                val statusResp = httpClient.newCall(statusReq).execute()
                val statusStr = statusResp.body?.string() ?: ""
                val statusObj = runCatching { JSONObject(statusStr) }.getOrNull() ?: continue

                val status = statusObj.optString("status")
                if (status == "AUTHORIZED") {
                    authorizedSecret = statusObj.getString("sharedSecret")
                    hostDeviceName = statusObj.optString("hostDeviceName", hostDeviceName)
                    break
                } else if (status == "REJECTED") {
                    return@withContext Result.failure(Exception("Verbindungsanfrage wurde vom Host-Gerät abgelehnt."))
                }
            }

            if (authorizedSecret != null) {
                val pairedDevice = PairedDevice(
                    id = UUID.randomUUID().toString(),
                    name = hostDeviceName,
                    ipAddress = peerIp,
                    port = peerPort,
                    sharedSecret = authorizedSecret,
                    isAuthorized = true,
                    lastSyncStatus = "Erfolgreich verbunden"
                )
                Result.success(PairingResult.Success(pairedDevice))
            } else {
                Result.success(PairingResult.Pending(reqId, peerIp, peerPort))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Verbindung zu $peerIp:$peerPort nicht möglich: ${e.localizedMessage ?: "Timeout"}"))
        }
    }

    /**
     * Führt eine vollständige Master-Master-Synchronisation mit einem gekoppelten Gerät durch.
     */
    suspend fun performMasterMasterSync(
        peer: PairedDevice,
        onUpdatePeerStatus: (PairedDevice) -> Unit
    ): Result<P2pSyncLog> = withContext(Dispatchers.IO) {
        _syncProgress.value = P2pSyncProgressState(
            isSyncing = true,
            peerName = peer.name,
            currentStage = "Verbindung aufbauen...",
            progressPercent = 0.05f
        )

        var docsSent = 0
        var docsReceived = 0
        val detectedConflicts = mutableListOf<SyncConflict>()

        try {
            // 1. Handshake & Status
            val statusUrl = "http://${peer.ipAddress}:${peer.port}/api/status"
            val statusReq = Request.Builder().url(statusUrl).get().build()
            val statusSuccessful = httpClient.newCall(statusReq).execute().use { it.isSuccessful }
            if (!statusSuccessful) {
                throw IOException("Peer antwortet nicht auf Status-Check")
            }

            // 2. Lokale Dokumente auslesen
            _syncProgress.value = _syncProgress.value.copy(
                currentStage = "Manifeste austauschen...",
                progressPercent = 0.15f
            )
            val localDocs = getAllLocalDocuments()
            val localDocsMap = localDocs.associateBy { it.id }
            val localByTitle = localDocs.associateBy { it.title.lowercase().trim() }

            // 3. Remote-Manifest anfordern
            val manifestUrl = "http://${peer.ipAddress}:${peer.port}/api/sync/exchange_manifest"
            val manifestReq = Request.Builder()
                .url(manifestUrl)
                .addHeader("Authorization", "Bearer ${peer.sharedSecret}")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            val manifestResp = httpClient.newCall(manifestReq).execute()
            val manifestStr = manifestResp.body?.string() ?: "{}"
            if (!manifestResp.isSuccessful) {
                throw IOException("Manifest-Abruf verweigert (HTTP ${manifestResp.code}). Autorisierung prüfen.")
            }

            val remoteObj = JSONObject(manifestStr)
            val remoteDocsArr = remoteObj.optJSONArray("documents") ?: JSONArray()
            val totalRemote = remoteDocsArr.length()

            _syncProgress.value = _syncProgress.value.copy(
                currentStage = "Änderungen abgleichen...",
                progressPercent = 0.3f,
                totalItems = totalRemote
            )

            val remoteDocIdsSeen = mutableSetOf<Long>()

            // 4. Remote-Dokumente prüfen & importieren
            for (i in 0 until totalRemote) {
                val rDoc = remoteDocsArr.getJSONObject(i)
                val remoteId = rDoc.getLong("id")
                val remoteTitle = rDoc.getString("title")
                val remoteSender = rDoc.optString("sender", "")
                val remoteMainCat = rDoc.optString("mainCategory", "Allgemein")
                val remoteSubCat = rDoc.optString("subCategory", "Diverses")
                val remoteMainCatId = rDoc.optString("mainCategoryId", "")
                val remoteSubCatId = rDoc.optString("subCategoryId", "")
                val remoteDocType = rDoc.optString("docType", "")
                val remoteTags = rDoc.optString("tags", "")
                val remoteOcr = rDoc.optString("ocrText", "")
                val remoteColor = rDoc.optString("colorMode", "BW")
                val remotePages = rDoc.optInt("pageCount", 1)
                val remoteFileSize = rDoc.optString("fileSizeFormatted", "100 KB")
                val remoteCreated = rDoc.getLong("createdAt")
                val remoteIsDeleted = rDoc.optBoolean("isDeleted", false)
                val remoteDeletedAt = if (rDoc.has("deletedAt")) rDoc.getLong("deletedAt") else null
                val remoteChecksum = rDoc.optString("fileChecksum", "")
                val remoteFileName = rDoc.optString("fileName", "doc_${remoteId}.pdf")

                remoteDocIdsSeen.add(remoteId)

                // Passendes lokales Dokument suchen (per ID oder per exaktem Titel)
                val localMatch = localDocsMap[remoteId] ?: localByTitle[remoteTitle.lowercase().trim()]

                if (localMatch == null) {
                    // Fall A: Dokument existiert lokal noch gar nicht -> Neues Dokument von Peer importieren
                    if (!remoteIsDeleted) {
                        val downloadedFile = downloadFileFromPeer(peer, remoteId, remoteFileName, remoteChecksum)
                        if (downloadedFile != null) {
                            val newDoc = DocumentEntity(
                                id = if (localDocsMap.containsKey(remoteId)) 0 else remoteId,
                                title = remoteTitle,
                                sender = remoteSender,
                                fileName = remoteFileName,
                                filePath = downloadedFile.absolutePath,
                                mainCategory = remoteMainCat,
                                subCategory = remoteSubCat,
                                mainCategoryId = remoteMainCatId,
                                subCategoryId = remoteSubCatId,
                                docType = remoteDocType,
                                tags = remoteTags,
                                ocrText = remoteOcr,
                                colorMode = remoteColor,
                                pageCount = remotePages,
                                fileSizeFormatted = remoteFileSize,
                                isSynced = true,
                                createdAt = remoteCreated,
                                isDeleted = false
                            )
                            database.documentDao().insertDocument(newDoc)
                            docsReceived++
                        } else {
                            // Checksummenfehler beim Download
                            val conflict = SyncConflict(
                                docId = remoteId,
                                docTitle = remoteTitle,
                                peerName = peer.name,
                                causeType = ConflictCauseType.HASH_MISMATCH,
                                diagnosticMessage = "Prüfsummen-Abweichung beim Herunterladen der Datei von ${peer.name}. Die übertragene Datei ist unvollständig oder beschädigt.",
                                localSummary = DocumentSummarySnapshot(
                                    title = "Nicht vorhanden",
                                    sender = "",
                                    mainCategory = "",
                                    subCategory = "",
                                    modifiedAt = 0L,
                                    fileSizeFormatted = ""
                                ),
                                remoteSummary = DocumentSummarySnapshot(
                                    title = remoteTitle,
                                    sender = remoteSender,
                                    mainCategory = remoteMainCat,
                                    subCategory = remoteSubCat,
                                    modifiedAt = remoteCreated,
                                    fileSizeFormatted = remoteFileSize,
                                    tags = remoteTags
                                )
                            )
                            detectedConflicts.add(conflict)
                        }
                    }
                } else {
                    // Fall B: Dokument existiert auf beiden Geräten -> Prüfen auf Konflikt
                    val localFile = File(localMatch.filePath)
                    val localChecksum = if (localFile.exists()) P2pSyncSecurityService.calculateFileSha256(localFile) else ""

                    if (remoteIsDeleted && !localMatch.isDeleted) {
                        // Gelöscht auf Remote vs. Lokal noch aktiv
                        val localModifiedRecently = localMatch.createdAt > peer.lastSyncTimestamp
                        if (localModifiedRecently) {
                            // Konflikt: Lokal nach letztem Sync bearbeitet, auf Remote gelöscht!
                            val conflict = SyncConflict(
                                docId = localMatch.id,
                                docTitle = localMatch.title,
                                peerName = peer.name,
                                causeType = ConflictCauseType.DELETE_VS_EDIT,
                                diagnosticMessage = "Lösch-Konflikt: Auf Gerät '${peer.name}' wurde das Dokument gelöscht, auf diesem Gerät wurde es jedoch kürzlich geändert.",
                                localSummary = DocumentSummarySnapshot(
                                    title = localMatch.title,
                                    sender = localMatch.sender,
                                    mainCategory = localMatch.mainCategory,
                                    subCategory = localMatch.subCategory,
                                    modifiedAt = localMatch.createdAt,
                                    fileSizeFormatted = localMatch.fileSizeFormatted,
                                    tags = localMatch.tags
                                ),
                                remoteSummary = DocumentSummarySnapshot(
                                    title = remoteTitle,
                                    sender = remoteSender,
                                    mainCategory = remoteMainCat,
                                    subCategory = remoteSubCat,
                                    modifiedAt = remoteDeletedAt ?: System.currentTimeMillis(),
                                    fileSizeFormatted = "Gelöscht",
                                    tags = remoteTags
                                )
                            )
                            detectedConflicts.add(conflict)
                        } else {
                            // Sauberer Tombstone-Sync: Lokal ebenfalls in den Papierkorb verschieben
                            database.documentDao().softDeleteDocument(localMatch.id, remoteDeletedAt ?: System.currentTimeMillis())
                            docsReceived++
                        }
                    } else if (!remoteIsDeleted && !localMatch.isDeleted) {
                        // Beide aktiv: Prüfen, ob Inhalte voneinander abweichen
                        val titleDiffers = localMatch.title.trim() != remoteTitle.trim()
                        val senderDiffers = localMatch.sender.trim() != remoteSender.trim()
                        val catDiffers = localMatch.mainCategory.trim() != remoteMainCat.trim() || localMatch.subCategory.trim() != remoteSubCat.trim()
                        val hashDiffers = remoteChecksum.isNotBlank() && localChecksum.isNotBlank() && !remoteChecksum.equals(localChecksum, ignoreCase = true)

                        if (titleDiffers || senderDiffers || catDiffers || hashDiffers) {
                            // Beide Seiten weichen ab!
                            val localModified = localMatch.createdAt > peer.lastSyncTimestamp
                            val remoteModified = remoteCreated > peer.lastSyncTimestamp

                            if (localModified && remoteModified) {
                                // Echter beidseitiger Bearbeitungskonflikt (Concurrent Edit)
                                val diffList = mutableListOf<String>()
                                if (titleDiffers) diffList.add("Titel ('${localMatch.title}' vs. '$remoteTitle')")
                                if (senderDiffers) diffList.add("Absender ('${localMatch.sender}' vs. '$remoteSender')")
                                if (catDiffers) diffList.add("Kategorie ('${localMatch.mainCategory}' vs. '$remoteMainCat')")
                                if (hashDiffers) diffList.add("PDF-Datei (Inhalt weicht ab)")

                                val conflict = SyncConflict(
                                    docId = localMatch.id,
                                    docTitle = localMatch.title,
                                    peerName = peer.name,
                                    causeType = ConflictCauseType.CONCURRENT_EDIT,
                                    diagnosticMessage = "Gleichzeitige Bearbeitung auf beiden Geräten. Abweichungen in: ${diffList.joinToString(", ")}. Bitte gewünschte Version auswählen.",
                                    localSummary = DocumentSummarySnapshot(
                                        title = localMatch.title,
                                        sender = localMatch.sender,
                                        mainCategory = localMatch.mainCategory,
                                        subCategory = localMatch.subCategory,
                                        modifiedAt = localMatch.createdAt,
                                        fileSizeFormatted = localMatch.fileSizeFormatted,
                                        tags = localMatch.tags
                                    ),
                                    remoteSummary = DocumentSummarySnapshot(
                                        title = remoteTitle,
                                        sender = remoteSender,
                                        mainCategory = remoteMainCat,
                                        subCategory = remoteSubCat,
                                        modifiedAt = remoteCreated,
                                        fileSizeFormatted = remoteFileSize,
                                        tags = remoteTags
                                    )
                                )
                                detectedConflicts.add(conflict)
                            } else if (remoteModified && !localModified) {
                                // Saubere Aktualisierung von Remote übernehmen
                                val downloadedFile = downloadFileFromPeer(peer, remoteId, remoteFileName, remoteChecksum)
                                val updatedDoc = localMatch.copy(
                                    title = remoteTitle,
                                    sender = remoteSender,
                                    filePath = downloadedFile?.absolutePath ?: localMatch.filePath,
                                    mainCategory = remoteMainCat,
                                    subCategory = remoteSubCat,
                                    mainCategoryId = remoteMainCatId,
                                    subCategoryId = remoteSubCatId,
                                    docType = remoteDocType,
                                    tags = remoteTags,
                                    ocrText = remoteOcr,
                                    fileSizeFormatted = remoteFileSize,
                                    isSynced = true
                                )
                                database.documentDao().updateDocument(updatedDoc)
                                docsReceived++
                            }
                        }
                    }
                }

                _syncProgress.value = _syncProgress.value.copy(
                    progressPercent = 0.3f + (0.4f * (i + 1) / totalRemote.coerceAtLeast(1)),
                    itemsProcessed = i + 1
                )
            }

            // 5. Lokale neue Dokumente an den Peer übertragen (Upload)
            _syncProgress.value = _syncProgress.value.copy(
                currentStage = "Lokale Dateien an ${peer.name} übertragen...",
                progressPercent = 0.75f
            )

            val docsToUpload = localDocs.filter { !it.isDeleted && !remoteDocIdsSeen.contains(it.id) }
            for (lDoc in docsToUpload) {
                val file = File(lDoc.filePath)
                if (file.exists()) {
                    val uploaded = uploadFileToPeer(peer, lDoc, file)
                    if (uploaded) {
                        docsSent++
                    }
                }
            }

            // 6. Konflikte im Manager speichern
            if (detectedConflicts.isNotEmpty()) {
                _activeConflicts.value = _activeConflicts.value + detectedConflicts
            }

            // 7. Status aktualisieren
            val now = System.currentTimeMillis()
            val syncStatusText = when {
                detectedConflicts.isNotEmpty() -> "${detectedConflicts.size} Konflikt(e) erfordern Prüfung"
                docsSent > 0 || docsReceived > 0 -> "Synchronisiert ($docsReceived empfangen, $docsSent gesendet)"
                else -> "Aktuell (keine Änderungen)"
            }

            val updatedPeer = peer.copy(
                lastSyncTimestamp = now,
                lastSyncStatus = syncStatusText
            )
            onUpdatePeerStatus(updatedPeer)

            val log = P2pSyncLog(
                peerName = peer.name,
                peerIp = peer.ipAddress,
                direction = "BIDIRECTIONAL",
                docsSent = docsSent,
                docsReceived = docsReceived,
                conflictsCount = detectedConflicts.size,
                status = if (detectedConflicts.isNotEmpty()) "CONFLICTS" else "SUCCESS",
                diagnosticMessage = "Master-Master Sync abgeschlossen. $docsReceived Dokumente empfangen, $docsSent gesendet. ${detectedConflicts.size} Konflikte."
            )

            _syncHistory.value = listOf(log) + _syncHistory.value

            _syncProgress.value = P2pSyncProgressState(
                isSyncing = false,
                peerName = peer.name,
                currentStage = "Fertig",
                progressPercent = 1.0f
            )

            Result.success(log)
        } catch (e: Exception) {
            val errorLog = P2pSyncLog(
                peerName = peer.name,
                peerIp = peer.ipAddress,
                direction = "BIDIRECTIONAL",
                status = "ERROR",
                diagnosticMessage = "Verbindungsfehler zu ${peer.name}: ${e.localizedMessage ?: "Unbekannter Fehler"}"
            )
            _syncHistory.value = listOf(errorLog) + _syncHistory.value
            _syncProgress.value = P2pSyncProgressState(
                isSyncing = false,
                peerName = peer.name,
                errorOrWarning = e.localizedMessage
            )
            Result.failure(e)
        }
    }

    /**
     * Manuelle Konfliktlösung durch den Benutzer.
     */
    suspend fun resolveConflict(
        conflict: SyncConflict,
        action: ConflictResolutionAction,
        peer: PairedDevice?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (action) {
                ConflictResolutionAction.KEEP_LOCAL -> {
                    // Lokales Dokument bleibt unverändert, Konflikt als gelöst markieren
                    _activeConflicts.value = _activeConflicts.value.filterNot { it.id == conflict.id }
                }

                ConflictResolutionAction.ACCEPT_REMOTE -> {
                    // Entfernte Version übernehmen
                    val localDoc = database.documentDao().getDocumentById(conflict.docId)
                    if (localDoc != null) {
                        val updated = localDoc.copy(
                            title = conflict.remoteSummary.title,
                            sender = conflict.remoteSummary.sender,
                            tags = conflict.remoteSummary.tags
                        )
                        database.documentDao().updateDocument(updated)
                    }
                    _activeConflicts.value = _activeConflicts.value.filterNot { it.id == conflict.id }
                }

                ConflictResolutionAction.KEEP_BOTH_AS_COPY -> {
                    // Zweite Datei als Konflikt-Kopie anlegen
                    val localDoc = database.documentDao().getDocumentById(conflict.docId)
                    if (localDoc != null) {
                        val copyDoc = localDoc.copy(
                            id = 0,
                            title = "${conflict.remoteSummary.title} (Konflikt-Kopie [${conflict.peerName}])",
                            sender = conflict.remoteSummary.sender,
                            createdAt = System.currentTimeMillis()
                        )
                        database.documentDao().insertDocument(copyDoc)
                    }
                    _activeConflicts.value = _activeConflicts.value.filterNot { it.id == conflict.id }
                }

                ConflictResolutionAction.DECIDE_LATER -> {
                    // Verbleibt in der Liste
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFileFromPeer(
        peer: PairedDevice,
        docId: Long,
        fileName: String,
        expectedChecksum: String
    ): File? {
        val url = "http://${peer.ipAddress}:${peer.port}/api/sync/download_file?docId=$docId"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${peer.sharedSecret}")
            .get()
            .build()

        return try {
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null

                val destDir = File(context.filesDir, "Documents").apply { mkdirs() }
                val safeFileName = File(fileName).name
                val destFile = File(destDir, safeFileName)
                if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    return null
                }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val actualChecksum = P2pSyncSecurityService.calculateFileSha256(destFile)
                if (expectedChecksum.isNotBlank() && !actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                    destFile.delete()
                    return null
                }
                destFile
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadFileToPeer(
        peer: PairedDevice,
        doc: DocumentEntity,
        file: File
    ): Boolean {
        val checksum = P2pSyncSecurityService.calculateFileSha256(file)
        val url = "http://${peer.ipAddress}:${peer.port}/api/sync/upload_file?docId=${doc.id}&fileName=${doc.fileName}"

        val reqBody = file.asRequestBody("application/octet-stream".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${peer.sharedSecret}")
            .addHeader("X-File-Checksum", checksum)
            .post(reqBody)
            .build()

        return try {
            httpClient.newCall(req).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getAllLocalDocuments(): List<DocumentEntity> {
        return try {
            database.documentDao().getAllDocumentsList()
        } catch (e: Exception) {
            Log.e("P2pSyncEngine", "Fehler beim Laden lokaler Dokumente", e)
            emptyList()
        }
    }
}

sealed class PairingResult {
    data class Success(val device: PairedDevice) : PairingResult()
    data class Pending(val requestId: String, val peerIp: String, val peerPort: Int) : PairingResult()
}
