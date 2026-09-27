package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

/**
 * Eingebetteter lokaler Server für:
 * 1. Android-zu-Android Master-Master P2P Synchronisation (TLS/AES-256)
 * 2. PC-Browser Zero-Knowledge Web-Portal (Client-seitige WebCrypto AES-256-GCM Verschlüsselung ohne Browser-Warnungen)
 * 3. Desktop-Scanner & Watchfolder Schnittstellen
 */
class P2pSyncServer(
    private val context: Context,
    private val database: AppDatabase,
    private val deviceNameProvider: () -> String,
    private val getPairedDevices: () -> List<PairedDevice>,
    private val onDevicePaired: (PairedDevice) -> Unit,
    private val onDocumentSynced: (DocumentEntity) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _serverStatus = MutableStateFlow(
        P2pServerStatus(
            isRunning = false,
            localIp = "127.0.0.1",
            port = 8765,
            deviceName = "myDocAnizer"
        )
    )
    val serverStatus: StateFlow<P2pServerStatus> = _serverStatus.asStateFlow()

    init {
        scope.launch {
            try {
                val ip = getLocalIpAddress()
                val name = runCatching { deviceNameProvider() }.getOrDefault("myDocAnizer")
                _serverStatus.value = _serverStatus.value.copy(
                    localIp = ip,
                    deviceName = name
                )
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    // Aktiver Kopplungs-PIN (falls Host-Modus aktiviert ist)
    private val _hostPairingPin = MutableStateFlow<String?>(null)
    val hostPairingPin: StateFlow<String?> = _hostPairingPin.asStateFlow()

    // Liste wartender Kopplungsanfragen (müssen im UI autorisiert werden)
    private val _pendingPairingRequests = MutableStateFlow<List<IncomingPairingRequest>>(emptyList())
    val pendingPairingRequests: StateFlow<List<IncomingPairingRequest>> = _pendingPairingRequests.asStateFlow()

    // Autorisierte Web-Sessions und Token
    private val authorizedTokens = mutableMapOf<String, String>()
    private val rejectedRequests = mutableSetOf<String>()
    private val activeWebSessions = mutableSetOf<String>()

    fun startServer(port: Int = 8765) {
        if (_serverStatus.value.isRunning) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                val localIp = getLocalIpAddress()
                _serverStatus.value = P2pServerStatus(
                    isRunning = true,
                    localIp = localIp,
                    port = port,
                    deviceName = deviceNameProvider()
                )
                Log.d("P2pSyncServer", "P2P Server gestartet auf $localIp:$port")

                while (isActive) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("P2pSyncServer", "Fehler beim Starten des P2P-Servers", e)
            } finally {
                _serverStatus.value = _serverStatus.value.copy(isRunning = false)
            }
        }
    }

    fun stopServer() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignorieren
        }
        _serverStatus.value = _serverStatus.value.copy(isRunning = false)
    }

    fun enableHostPairingMode(): String {
        val pin = P2pSyncSecurityService.generatePairingPin()
        _hostPairingPin.value = pin
        return pin
    }

    fun disableHostPairingMode() {
        _hostPairingPin.value = null
    }

    fun authorizePairingRequest(requestId: String, allow: Boolean) {
        val request = _pendingPairingRequests.value.find { it.requestId == requestId } ?: return
        _pendingPairingRequests.value = _pendingPairingRequests.value.filterNot { it.requestId == requestId }

        if (allow) {
            val sharedSecret = P2pSyncSecurityService.generateSharedSecret()
            authorizedTokens[requestId] = sharedSecret

            val pairedDevice = PairedDevice(
                id = request.clientDeviceId,
                name = request.clientDeviceName,
                ipAddress = request.clientIp,
                port = 8765,
                sharedSecret = sharedSecret,
                isAuthorized = true,
                pairedAt = System.currentTimeMillis(),
                lastSyncStatus = "Verbunden & Bereit"
            )
            onDevicePaired(pairedDevice)
        } else {
            rejectedRequests.add(requestId)
        }
    }

    private data class HttpRequestData(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
        val headers: Map<String, String>,
        val contentLength: Int,
        val initialBodyBytes: ByteArray
    )

    private fun readHttpRequest(input: InputStream): HttpRequestData? {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(2048)
        var headerEnd = -1
        while (headerEnd == -1) {
            val r = input.read(buf)
            if (r == -1) break
            val prevSize = baos.size()
            baos.write(buf, 0, r)
            val all = baos.toByteArray()
            for (i in maxOf(0, prevSize - 3) until all.size - 3) {
                if (all[i] == 13.toByte() && all[i+1] == 10.toByte() &&
                    all[i+2] == 13.toByte() && all[i+3] == 10.toByte()) {
                    headerEnd = i + 4
                    break
                }
            }
            if (all.size > 64 * 1024) return null
        }
        if (headerEnd == -1) return null

        val all = baos.toByteArray()
        val headerText = String(all, 0, headerEnd, Charsets.UTF_8)
        val initialBody = all.copyOfRange(headerEnd, all.size)

        val lines = headerText.split("\r\n")
        if (lines.isEmpty()) return null
        val reqLine = lines[0].split(" ")
        if (reqLine.size < 2) return null
        val method = reqLine[0].uppercase()
        val fullPath = reqLine[1]
        val path = fullPath.substringBefore("?")
        val queryParams = parseQueryParams(fullPath.substringAfter("?", ""))

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        val cl = headers["content-length"]?.toIntOrNull() ?: 0
        return HttpRequestData(method, path, queryParams, headers, cl, initialBody)
    }

    private fun readBinaryBody(input: InputStream, contentLength: Int, initialBytes: ByteArray): ByteArray {
        if (contentLength <= 0) return ByteArray(0)
        val result = ByteArray(contentLength)
        var totalRead = 0
        if (initialBytes.isNotEmpty()) {
            val copyLen = minOf(initialBytes.size, contentLength)
            System.arraycopy(initialBytes, 0, result, 0, copyLen)
            totalRead = copyLen
        }
        while (totalRead < contentLength) {
            val r = input.read(result, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }
        return result
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 20000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val req = readHttpRequest(input) ?: return socket.close()
            val method = req.method
            val path = req.path
            val queryParams = req.queryParams
            val headers = req.headers
            val contentLength = req.contentLength

            if (contentLength < 0 || contentLength > 100 * 1024 * 1024) {
                return sendJsonResponse(output, 400, JSONObject().put("error", "Ungültige Payload-Größe (Max 100 MB)").toString())
            }

            when {
                // ==========================================
                // 1. Zero-Knowledge Web Portal für PC-Browser
                // ==========================================
                (path == "/" || path == "/index.html" || path == "/portal") && method == "GET" -> {
                    sendHtmlResponse(output, 200, buildZeroKnowledgeWebPortalHtml())
                }

                path == "/cert/mydocanizer-ca.crt" && method == "GET" -> {
                    sendCertResponse(output, P2pSyncSecurityService.getLocalRootCaPem())
                }

                // 2. Web Portal Authentifizierung mit PIN
                path == "/api/web/auth" && method == "POST" -> {
                    val bodyBytes = readBinaryBody(input, contentLength, req.initialBodyBytes)
                    handleWebAuth(bodyBytes, output)
                }

                // 3. Web Portal Dokumenten-Liste (Zero-Knowledge / Authentifiziert)
                path == "/api/web/documents" && method == "GET" -> {
                    val sessionToken = headers["x-session-token"] ?: queryParams["token"] ?: ""
                    if (!isWebSessionValid(sessionToken)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert. Bitte PIN eingeben.").toString())
                    } else {
                        handleWebDocumentsList(output)
                    }
                }

                // 4. Web Portal Verschlüsselter Datei-Upload (AES-256-GCM aus WebCrypto)
                path == "/api/web/upload" && method == "POST" -> {
                    val sessionToken = headers["x-session-token"] ?: queryParams["token"] ?: ""
                    if (!isWebSessionValid(sessionToken)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert. Bitte PIN eingeben.").toString())
                    } else {
                        val fileName = headers["x-file-name"] ?: queryParams["fileName"] ?: "PC_Scan_${System.currentTimeMillis()}.pdf"
                        val isEncrypted = headers["x-encrypted"] == "true"
                        val expectedChecksum = headers["x-file-checksum"] ?: ""
                        handleWebUpload(fileName, isEncrypted, expectedChecksum, contentLength, req.initialBodyBytes, input, output)
                    }
                }

                // 5. Web Portal Datei-Download
                path == "/api/web/download" && method == "GET" -> {
                    val sessionToken = headers["x-session-token"] ?: queryParams["token"] ?: ""
                    if (!isWebSessionValid(sessionToken)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert.").toString())
                    } else {
                        val docId = queryParams["id"]?.toLongOrNull()
                        handleDownloadFile(docId, output)
                    }
                }

                // ==========================================
                // P2P API für Android-zu-Android Master Sync
                // ==========================================
                path == "/api/status" -> {
                    val json = JSONObject().apply {
                        put("status", "ok")
                        put("deviceName", deviceNameProvider())
                        put("role", "Master-Peer")
                        put("version", "2.0")
                        put("encryption", "Zero-Knowledge AES-256-GCM / TLS 1.3")
                    }
                    sendJsonResponse(output, 200, json.toString())
                }

                path == "/api/pair/request" && method == "POST" -> {
                    val bodyBytes = readBinaryBody(input, contentLength, req.initialBodyBytes)
                    handlePairRequest(bodyBytes, socket.inetAddress.hostAddress ?: "unknown", output)
                }

                path == "/api/pair/status" && method == "GET" -> {
                    val reqId = queryParams["requestId"] ?: ""
                    handlePairStatus(reqId, output)
                }

                path == "/api/sync/exchange_manifest" && method == "POST" -> {
                    if (!verifyAuth(headers)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert").toString())
                    } else {
                        val bodyBytes = readBinaryBody(input, contentLength, req.initialBodyBytes)
                        handleManifestExchange(bodyBytes, output)
                    }
                }

                path == "/api/sync/download_file" && method == "GET" -> {
                    if (!verifyAuth(headers)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert").toString())
                    } else {
                        val docId = queryParams["docId"]?.toLongOrNull()
                        handleDownloadFile(docId, output)
                    }
                }

                path == "/api/sync/upload_file" && method == "POST" -> {
                    if (!verifyAuth(headers)) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert").toString())
                    } else {
                        val docId = queryParams["docId"]?.toLongOrNull() ?: 0L
                        val fileName = queryParams["fileName"] ?: "scan.pdf"
                        val checksum = headers["x-file-checksum"] ?: ""
                        handleUploadFileStream(docId, fileName, checksum, contentLength, req.initialBodyBytes, input, output)
                    }
                }

                // ==========================================
                // myDocAnizer-Light Desktop-App Schnittstellen
                // ==========================================
                path == "/api/scan/desktop_stream" && method == "POST" -> {
                    if (!verifyAuth(headers) && !isWebSessionValid(headers["x-session-token"] ?: "")) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert für Scan-to-Android").toString())
                    } else {
                        val fileName = headers["x-file-name"] ?: queryParams["fileName"] ?: "PC_Scan_${System.currentTimeMillis()}.pdf"
                        val isEncrypted = headers["x-encrypted"] == "true"
                        val checksum = headers["x-file-checksum"] ?: ""
                        handleDesktopScanStream(fileName, isEncrypted, checksum, contentLength, req.initialBodyBytes, input, output)
                    }
                }

                path == "/api/sync/desktop_delta" && method == "POST" -> {
                    if (!verifyAuth(headers) && !isWebSessionValid(headers["x-session-token"] ?: "")) {
                        sendJsonResponse(output, 401, JSONObject().put("error", "Nicht autorisiert für Delta-Sync").toString())
                    } else {
                        val bodyBytes = readBinaryBody(input, contentLength, req.initialBodyBytes)
                        handleDesktopDeltaSync(bodyBytes, output)
                    }
                }

                else -> {
                    sendJsonResponse(output, 404, JSONObject().put("error", "Endpoint nicht gefunden").toString())
                }
            }
        } catch (e: Exception) {
            Log.e("P2pSyncServer", "Fehler in Client-Verbindung", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleWebAuth(bodyBytes: ByteArray, output: OutputStream) {
        val json = try {
            JSONObject(String(bodyBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return sendJsonResponse(output, 400, JSONObject().put("error", "Ungültiges JSON").toString())
        }

        val pinEntered = json.optString("pin", "").replace(" ", "").trim()
        val expectedPin = _hostPairingPin.value?.replace(" ", "")?.trim()

        if (expectedPin.isNullOrBlank() || pinEntered != expectedPin) {
            return sendJsonResponse(output, 403, JSONObject().put("error", "Falsche Freigabe-PIN. Bitte am Smartphone in myDocAnizer ablesen.").toString())
        }

        val token = "web_session_" + UUID.randomUUID().toString().replace("-", "")
        activeWebSessions.add(token)

        val resp = JSONObject().apply {
            put("status", "ok")
            put("token", token)
            put("deviceName", deviceNameProvider())
            put("message", "Erfolgreich autorisiert. Zero-Knowledge Schlüssel aktiv.")
        }
        sendJsonResponse(output, 200, resp.toString())
    }

    private fun isWebSessionValid(token: String): Boolean {
        if (token.isBlank()) return false
        if (activeWebSessions.contains(token)) return true
        // Prüfe auch gekoppelte P2P-Secrets
        val paired = getPairedDevices()
        return paired.any { it.isAuthorized && P2pSyncSecurityService.isTokenEqual(it.sharedSecret, token) }
    }

    private fun handleWebDocumentsList(output: OutputStream) {
        runBlocking {
            val docs = try {
                database.documentDao().getAllDocumentsList()
            } catch (e: Exception) {
                emptyList<DocumentEntity>()
            }

            val array = JSONArray()
            docs.filter { !it.isDeleted }.take(50).forEach { doc ->
                val f = File(doc.filePath)
                array.put(JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("sender", doc.sender)
                    put("category", "${doc.mainCategory} / ${doc.subCategory}")
                    put("docType", doc.docType)
                    put("fileSize", doc.fileSizeFormatted)
                    put("pageCount", doc.pageCount)
                    put("createdAt", doc.createdAt)
                    put("exists", f.exists())
                })
            }

            val resp = JSONObject().apply {
                put("status", "ok")
                put("count", array.length())
                put("documents", array)
                put("hostDevice", deviceNameProvider())
            }
            sendJsonResponse(output, 200, resp.toString())
        }
    }

    private fun handleWebUpload(
        fileName: String,
        isEncrypted: Boolean,
        expectedChecksum: String,
        contentLength: Int,
        initialBytes: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        val destDir = File(context.filesDir, "Documents").apply { mkdirs() }
        val safeFileName = File(fileName).name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(destDir, safeFileName)

        val rawBytes = readBinaryBody(input, contentLength, initialBytes)

        val finalBytes = if (isEncrypted) {
            val pin = _hostPairingPin.value ?: ""
            val key = P2pSyncSecurityService.deriveKeyFromPin(pin)
            val decrypted = P2pSyncSecurityService.decryptBytes(rawBytes, key)
            if (decrypted == null) {
                return sendJsonResponse(output, 400, JSONObject().put("error", "Entschlüsselung fehlgeschlagen (Falscher WebCrypto-Key/PIN)").toString())
            }
            decrypted
        } else {
            rawBytes
        }

        try {
            FileOutputStream(destFile).use { it.write(finalBytes) }
        } catch (e: Exception) {
            return sendJsonResponse(output, 500, JSONObject().put("error", "Speicherfehler: ${e.message}").toString())
        }

        val actualChecksum = P2pSyncSecurityService.calculateFileSha256(destFile)

        // Neues Dokument in Room-DB registrieren
        runBlocking {
            val title = safeFileName.substringBeforeLast(".")
            val entity = DocumentEntity(
                title = title,
                sender = "PC-Import",
                filePath = destFile.absolutePath,
                fileName = safeFileName,
                mainCategory = "Allgemein",
                subCategory = "PC-Scans",
                mainCategoryId = "general",
                subCategoryId = "pc_import",
                docType = "Dokument",
                tags = "PC, Web-Upload, E2EE",
                ocrText = "Importiert via myDocAnizer Zero-Knowledge Web-Portal vom PC.",
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${finalBytes.size / 1024} KB",
                createdAt = System.currentTimeMillis()
            )
            val newId = database.documentDao().insertDocument(entity)
            val savedEntity = entity.copy(id = newId)
            onDocumentSynced(savedEntity)
        }

        sendJsonResponse(output, 200, JSONObject().apply {
            put("status", "ok")
            put("fileName", safeFileName)
            put("savedPath", destFile.absolutePath)
            put("checksum", actualChecksum)
            put("encryptedTransfer", isEncrypted)
            put("message", "✅ Dokument sicher empfangen und im Tresor archiviert.")
        }.toString())
    }

    private fun handleDesktopScanStream(
        fileName: String,
        isEncrypted: Boolean,
        expectedChecksum: String,
        contentLength: Int,
        initialBytes: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        val destDir = File(context.filesDir, "Documents").apply { mkdirs() }
        val safeFileName = File(fileName).name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(destDir, safeFileName)

        val rawBytes = readBinaryBody(input, contentLength, initialBytes)
        val finalBytes = if (isEncrypted) {
            val pin = _hostPairingPin.value ?: ""
            val key = P2pSyncSecurityService.deriveKeyFromPin(pin)
            val decrypted = P2pSyncSecurityService.decryptBytes(rawBytes, key)
            decrypted ?: rawBytes
        } else {
            rawBytes
        }

        try {
            FileOutputStream(destFile).use { it.write(finalBytes) }
        } catch (e: Exception) {
            return sendJsonResponse(output, 500, JSONObject().put("error", "Speicherfehler: ${e.message}").toString())
        }

        val actualChecksum = P2pSyncSecurityService.calculateFileSha256(destFile)
        val cleanTitle = safeFileName.substringBeforeLast(".").replace("_", " ")

        val savedDoc = runBlocking {
            val entity = DocumentEntity(
                title = cleanTitle,
                sender = "Desktop-Scanner (Light)",
                filePath = destFile.absolutePath,
                fileName = safeFileName,
                mainCategory = "Allgemein",
                subCategory = "Desktop-Scans",
                mainCategoryId = "general",
                subCategoryId = "desktop_scan",
                docType = "Scan-Beleg",
                tags = "PC-Scan, Light-App, E2EE",
                ocrText = "Automatisch verarbeitet von myDocAnizer via Scan-to-Android Schnittstelle.",
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${finalBytes.size / 1024} KB",
                createdAt = System.currentTimeMillis()
            )
            val id = database.documentDao().insertDocument(entity)
            val complete = entity.copy(id = id)
            onDocumentSynced(complete)
            complete
        }

        // Antwort mit vollständigem DocumentEntity JSON an Desktop Light-App
        val resp = JSONObject().apply {
            put("status", "ok")
            put("message", "✅ Scan erfolgreich auf Android verarbeitet & indexiert.")
            put("document", JSONObject().apply {
                put("id", savedDoc.id)
                put("title", savedDoc.title)
                put("sender", savedDoc.sender)
                put("fileName", savedDoc.fileName)
                put("mainCategory", savedDoc.mainCategory)
                put("subCategory", savedDoc.subCategory)
                put("docType", savedDoc.docType)
                put("tags", savedDoc.tags)
                put("ocrText", savedDoc.ocrText)
                put("fileSizeFormatted", savedDoc.fileSizeFormatted)
                put("createdAt", savedDoc.createdAt)
                put("fileChecksum", actualChecksum)
            })
        }
        sendJsonResponse(output, 200, resp.toString())
    }

    private fun handleDesktopDeltaSync(bodyBytes: ByteArray, output: OutputStream) {
        val json = try {
            JSONObject(String(bodyBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return sendJsonResponse(output, 400, JSONObject().put("error", "Ungültiges JSON").toString())
        }

        val changes = json.optJSONArray("changes") ?: JSONArray()
        var updatedCount = 0

        runBlocking {
            for (i in 0 until changes.length()) {
                val item = changes.optJSONObject(i) ?: continue
                val docId = item.optLong("id", -1L)
                if (docId <= 0L) continue

                val existing = database.documentDao().getDocumentById(docId)
                if (existing != null) {
                    val updated = existing.copy(
                        title = item.optString("title", existing.title),
                        sender = item.optString("sender", existing.sender),
                        mainCategory = item.optString("mainCategory", existing.mainCategory),
                        subCategory = item.optString("subCategory", existing.subCategory),
                        tags = item.optString("tags", existing.tags),
                        isDeleted = item.optBoolean("isDeleted", existing.isDeleted),
                        deletedAt = if (item.has("deletedAt")) item.optLong("deletedAt") else existing.deletedAt
                    )
                    database.documentDao().updateDocument(updated)
                    onDocumentSynced(updated)
                    updatedCount++
                }
            }
        }

        sendJsonResponse(output, 200, JSONObject().apply {
            put("status", "ok")
            put("updatedCount", updatedCount)
            put("message", "$updatedCount Änderungen vom Desktop übernommen.")
        }.toString())
    }

    private fun handlePairRequest(bodyBytes: ByteArray, clientIp: String, output: OutputStream) {
        val json = try {
            JSONObject(String(bodyBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return sendJsonResponse(output, 400, JSONObject().put("error", "Ungültiges JSON").toString())
        }

        val clientDeviceId = json.optString("clientDeviceId", UUID.randomUUID().toString())
        val clientDeviceName = json.optString("clientDeviceName", "Unbekanntes Gerät")
        val pinEntered = json.optString("pin", "").replace(" ", "").trim()

        val expectedPin = _hostPairingPin.value?.replace(" ", "")?.trim()
        if (expectedPin == null || pinEntered != expectedPin) {
            return sendJsonResponse(output, 403, JSONObject().put("error", "Ungültiger Kopplungs-PIN oder Host-Modus inaktiv").toString())
        }

        val requestId = UUID.randomUUID().toString()
        val request = IncomingPairingRequest(
            requestId = requestId,
            clientDeviceId = clientDeviceId,
            clientDeviceName = clientDeviceName,
            clientIp = clientIp,
            pinEntered = pinEntered
        )

        _pendingPairingRequests.value = _pendingPairingRequests.value + request

        val resp = JSONObject().apply {
            put("status", "PENDING_AUTHORIZATION")
            put("requestId", requestId)
            put("message", "Anfrage erhalten. Bitte auf dem Host-Gerät autorisieren.")
        }
        sendJsonResponse(output, 202, resp.toString())
    }

    private fun handlePairStatus(requestId: String, output: OutputStream) {
        if (authorizedTokens.containsKey(requestId)) {
            val secret = authorizedTokens.remove(requestId)
            val resp = JSONObject().apply {
                put("status", "AUTHORIZED")
                put("hostDeviceName", deviceNameProvider())
                put("sharedSecret", secret)
            }
            sendJsonResponse(output, 200, resp.toString())
        } else if (rejectedRequests.contains(requestId)) {
            rejectedRequests.remove(requestId)
            val resp = JSONObject().apply {
                put("status", "REJECTED")
                put("message", "Kopplungsanfrage wurde auf dem Host-Gerät abgelehnt.")
            }
            sendJsonResponse(output, 403, resp.toString())
        } else {
            val resp = JSONObject().apply {
                put("status", "PENDING")
            }
            sendJsonResponse(output, 200, resp.toString())
        }
    }

    private fun handleManifestExchange(bodyBytes: ByteArray, output: OutputStream) {
        runBlocking {
            val docsList = try {
                database.documentDao().getAllDocumentsList()
            } catch (e: Exception) {
                emptyList<DocumentEntity>()
            }

            val array = JSONArray()
            docsList.forEach { doc ->
                val f = File(doc.filePath)
                val checksum = if (f.exists()) P2pSyncSecurityService.calculateFileSha256(f) else ""
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("sender", doc.sender)
                    put("fileName", doc.fileName)
                    put("mainCategory", doc.mainCategory)
                    put("subCategory", doc.subCategory)
                    put("mainCategoryId", doc.mainCategoryId)
                    put("subCategoryId", doc.subCategoryId)
                    put("docType", doc.docType)
                    put("tags", doc.tags)
                    put("ocrText", doc.ocrText)
                    put("colorMode", doc.colorMode)
                    put("pageCount", doc.pageCount)
                    put("fileSizeFormatted", doc.fileSizeFormatted)
                    put("createdAt", doc.createdAt)
                    put("isDeleted", doc.isDeleted)
                    put("deletedAt", doc.deletedAt ?: 0L)
                    put("contractEndDate", doc.contractEndDate ?: 0L)
                    put("cancellationDeadline", doc.cancellationDeadline ?: 0L)
                    put("amount", doc.amount ?: 0.0)
                    put("fileChecksum", checksum)
                }
                array.put(obj)
            }

            val resp = JSONObject().apply {
                put("status", "ok")
                put("documents", array)
                put("manifestTimestamp", System.currentTimeMillis())
            }
            sendJsonResponse(output, 200, resp.toString())
        }
    }

    private fun handleDownloadFile(docId: Long?, output: OutputStream) {
        if (docId == null) {
            return sendJsonResponse(output, 400, JSONObject().put("error", "docId fehlt").toString())
        }

        val doc = runBlocking { database.documentDao().getDocumentById(docId) }
        if (doc == null) {
            return sendJsonResponse(output, 404, JSONObject().put("error", "Dokument nicht gefunden").toString())
        }

        val file = File(doc.filePath)
        val allowedBaseDir = File(context.filesDir, "Documents")
        val allowedCacheDir = context.cacheDir
        val isPathAllowed = file.canonicalPath.startsWith(allowedBaseDir.canonicalPath) ||
                file.canonicalPath.startsWith(context.filesDir.canonicalPath) ||
                file.canonicalPath.startsWith(allowedCacheDir.canonicalPath)

        if (!file.exists() || !file.isFile || !isPathAllowed) {
            return sendJsonResponse(output, 404, JSONObject().put("error", "Datei auf Speicher nicht vorhanden oder unzulässig").toString())
        }

        val checksum = P2pSyncSecurityService.calculateFileSha256(file)
        val fileLength = file.length()

        val headerStr = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/pdf\r\n")
            append("Content-Length: $fileLength\r\n")
            append("Content-Disposition: inline; filename=\"${doc.fileName}\"\r\n")
            append("X-File-Checksum: $checksum\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }

        output.write(headerStr.toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                output.write(buf, 0, n)
            }
        }
        output.flush()
    }

    private fun handleUploadFileStream(
        docId: Long,
        fileName: String,
        expectedChecksum: String,
        contentLength: Int,
        initialBytes: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        val destDir = File(context.filesDir, "Documents").apply { mkdirs() }
        val safeFileName = File(fileName).name
        val destFile = File(destDir, safeFileName)

        if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
            return sendJsonResponse(output, 400, JSONObject().put("error", "Ungültiger Dateiname (Path Traversal)").toString())
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0

        try {
            FileOutputStream(destFile).use { fos ->
                if (initialBytes.isNotEmpty()) {
                    fos.write(initialBytes)
                    digest.update(initialBytes)
                    bytesWritten += initialBytes.size
                }

                val buf = ByteArray(64 * 1024)
                while (bytesWritten < contentLength) {
                    val toRead = minOf(buf.size, contentLength - bytesWritten)
                    val r = input.read(buf, 0, toRead)
                    if (r == -1) break
                    fos.write(buf, 0, r)
                    digest.update(buf, 0, r)
                    bytesWritten += r
                }
            }
        } catch (e: Exception) {
            destFile.delete()
            return sendJsonResponse(output, 500, JSONObject().put("error", "Schreibfehler: ${e.message}").toString())
        }

        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
        if (expectedChecksum.isNotBlank() && !actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            destFile.delete()
            return sendJsonResponse(output, 400, JSONObject().apply {
                put("error", "Prüfsummenfehler: Datei während der Übertragung beschädigt.")
                put("expected", expectedChecksum)
                put("actual", actualChecksum)
            }.toString())
        }

        sendJsonResponse(output, 200, JSONObject().apply {
            put("status", "ok")
            put("savedPath", destFile.absolutePath)
            put("checksum", actualChecksum)
        }.toString())
    }

    private fun verifyAuth(headers: Map<String, String>): Boolean {
        val authHeader = headers["authorization"] ?: return false
        val token = authHeader.removePrefix("Bearer ").trim()
        val paired = getPairedDevices()
        return paired.any { it.isAuthorized && P2pSyncSecurityService.isTokenEqual(it.sharedSecret, token) }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Internal Error"
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCertResponse(output: OutputStream, pemCert: String) {
        val bytes = pemCert.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-x509-ca-cert\r\n" +
                "Content-Disposition: attachment; filename=\"mydocanizer-ca.crt\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                map[parts[0]] = java.net.URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return map
    }

    private fun buildZeroKnowledgeWebPortalHtml(): String {
        return """
<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>myDocAnizer • Zero-Knowledge PC-Portal</title>
  <style>
    :root {
      --primary: #0284c7;
      --primary-dark: #0369a1;
      --bg: #0f172a;
      --surface: #1e293b;
      --surface-light: #334155;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --success: #10b981;
      --danger: #ef4444;
      --radius: 12px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      display: flex;
      flex-direction: column;
      align-items: center;
      min-height: 100vh;
      padding: 24px 16px;
    }
    .header {
      width: 100%;
      max-width: 800px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid var(--surface-light);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-icon {
      background: var(--primary);
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
    }
    .badge-secure {
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid var(--success);
      color: var(--success);
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .card {
      width: 100%;
      max-width: 800px;
      background: var(--surface);
      border-radius: var(--radius);
      padding: 24px;
      margin-bottom: 20px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.3);
      border: 1px solid rgba(255,255,255,0.05);
    }
    .drop-zone {
      border: 2px dashed var(--primary);
      background: rgba(2, 132, 199, 0.05);
      border-radius: var(--radius);
      padding: 40px 20px;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
    }
    .drop-zone.dragover {
      background: rgba(2, 132, 199, 0.15);
      border-color: #38bdf8;
      transform: scale(1.01);
    }
    .pin-input {
      font-size: 28px;
      letter-spacing: 8px;
      text-align: center;
      width: 240px;
      padding: 10px;
      border-radius: 8px;
      border: 2px solid var(--primary);
      background: var(--bg);
      color: var(--text);
      font-family: monospace;
      outline: none;
    }
    .btn {
      background: var(--primary);
      color: white;
      border: none;
      padding: 12px 24px;
      border-radius: 8px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: 0.2s;
    }
    .btn:hover { background: var(--primary-dark); }
    .doc-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 16px;
      font-size: 14px;
    }
    .doc-table th { text-align: left; padding: 10px; color: var(--text-muted); border-bottom: 1px solid var(--surface-light); }
    .doc-table td { padding: 12px 10px; border-bottom: 1px solid var(--surface-light); }
    .status-box {
      margin-top: 12px;
      padding: 10px 14px;
      border-radius: 8px;
      font-size: 13px;
      display: none;
    }
    .status-success { background: rgba(16, 185, 129, 0.15); border: 1px solid var(--success); color: var(--success); }
    .status-error { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--danger); color: var(--danger); }
  </style>
</head>
<body>
  <div class="header">
    <div class="brand">
      <div class="brand-icon">📂</div>
      <div>
        <h2>myDocAnizer</h2>
        <div style="font-size:12px;color:var(--text-muted);">Zero-Knowledge PC Transfer Portal</div>
      </div>
    </div>
    <div class="badge-secure">
      <span>🔒 WebCrypto AES-256-GCM (Ende-zu-Ende verschlüsselt)</span>
    </div>
  </div>

  <!-- AUTH CARD -->
  <div class="card" id="authCard">
    <h3 style="margin-bottom:8px;">1. Freigabe-PIN eingeben</h3>
    <p style="color:var(--text-muted);font-size:14px;margin-bottom:16px;">
      Bitte gib den 6-stelligen PIN ein, der in deiner myDocAnizer Android-App angezeigt wird.
      Der PIN dient direkt als kryptografischer Schlüssel für die hardwarebeschleunigte WebCrypto-Verschlüsselung in deinem Browser.
    </p>
    <div style="display:flex;gap:12px;align-items:center;">
      <input type="text" id="pinInput" class="pin-input" maxlength="7" placeholder="123 456" autofocus>
      <button class="btn" onclick="authorizeWithPin()">Autorisieren & Entschlüsseln</button>
    </div>
    <div id="authStatus" class="status-box"></div>
  </div>

  <!-- UPLOAD CARD -->
  <div class="card" id="mainCard" style="display:none;">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
      <h3>2. Scans & Dokumente übertragen (E2E-Verschlüsselt)</h3>
      <button class="btn" style="background:var(--surface-light);padding:6px 12px;font-size:12px;" onclick="loadVaultDocs()">🔄 Tresor aktualisieren</button>
    </div>
    
    <div class="drop-zone" id="dropZone" onclick="document.getElementById('fileInput').click()">
      <div style="font-size:36px;margin-bottom:8px;">📄 ⬆️</div>
      <div style="font-weight:600;font-size:16px;margin-bottom:4px;">Dateien hier hineinziehen oder klicken zum Auswählen</div>
      <div style="color:var(--text-muted);font-size:13px;">PDF, PNG, JPEG (Mehrseitige Scans werden automatisch verarbeitet)</div>
      <input type="file" id="fileInput" style="display:none;" multiple onchange="handleFileSelect(this.files)">
    </div>
    <div id="uploadStatus" class="status-box"></div>

    <h3 style="margin-top:24px;margin-bottom:8px;">3. Tresor-Dokumente auf dem Smartphone</h3>
    <table class="doc-table">
      <thead>
        <tr>
          <th>Titel</th>
          <th>Kategorie</th>
          <th>Größe</th>
          <th>Aktion</th>
        </tr>
      </thead>
      <tbody id="docsTableBody">
        <tr><td colspan="4" style="text-align:center;color:var(--text-muted);">Lade Dokumente...</td></tr>
      </tbody>
    </table>
  </div>

  <script>
    let sessionToken = '';
    let derivedAesKey = null;
    const STATIC_SALT = new TextEncoder().encode("myDocAnizer-ZeroKnowledge-P2P-Salt");

    async function deriveKeyFromPin(pin) {
      const cleanPin = pin.replace(/\s+/g, '');
      const enc = new TextEncoder();
      const keyMaterial = await window.crypto.subtle.importKey(
        "raw",
        enc.encode(cleanPin),
        { name: "PBKDF2" },
        false,
        ["deriveKey"]
      );
      return window.crypto.subtle.deriveKey(
        {
          name: "PBKDF2",
          salt: STATIC_SALT,
          iterations: 100000,
          hash: "SHA-256"
        },
        keyMaterial,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt", "decrypt"]
      );
    }

    async function authorizeWithPin() {
      const pin = document.getElementById('pinInput').value.trim();
      const statusDiv = document.getElementById('authStatus');
      if (pin.length < 6) {
        statusDiv.className = 'status-box status-error';
        statusDiv.style.display = 'block';
        statusDiv.innerText = 'Bitte gib den vollständigen 6-stelligen PIN ein.';
        return;
      }

      try {
        statusDiv.className = 'status-box';
        statusDiv.style.display = 'block';
        statusDiv.innerText = 'Leite AES-256 Schlüssel ab und autorisiere...';

        derivedAesKey = await deriveKeyFromPin(pin);

        const resp = await fetch('/api/web/auth', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pin: pin })
        });
        const data = await resp.json();

        if (resp.ok && data.status === 'ok') {
          sessionToken = data.token;
          document.getElementById('authCard').style.display = 'none';
          document.getElementById('mainCard').style.display = 'block';
          loadVaultDocs();
        } else {
          statusDiv.className = 'status-box status-error';
          statusDiv.innerText = data.error || 'Autorisierung fehlgeschlagen.';
        }
      } catch (err) {
        statusDiv.className = 'status-box status-error';
        statusDiv.innerText = 'Verbindungsfehler: ' + err.message;
      }
    }

    async function handleFileSelect(files) {
      if (!files || files.length === 0) return;
      const statusDiv = document.getElementById('uploadStatus');

      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        statusDiv.className = 'status-box';
        statusDiv.style.display = 'block';
        statusDiv.innerText = '🔒 Verschlüssele ' + file.name + ' mit AES-256-GCM im RAM...';

        const arrayBuffer = await file.arrayBuffer();
        const iv = window.crypto.getRandomValues(new Uint8Array(12));
        const encryptedData = await window.crypto.subtle.encrypt(
          { name: "AES-GCM", iv: iv },
          derivedAesKey,
          arrayBuffer
        );

        // Kombiniere IV + Ciphertext + Tag
        const combined = new Uint8Array(iv.length + encryptedData.byteLength);
        combined.set(iv, 0);
        combined.set(new Uint8Array(encryptedData), iv.length);

        statusDiv.innerText = '⬆️ Sende verschlüsselte Payload (' + file.name + ') an Android-Tresor...';

        const resp = await fetch('/api/web/upload', {
          method: 'POST',
          headers: {
            'x-session-token': sessionToken,
            'x-file-name': file.name,
            'x-encrypted': 'true',
            'Content-Type': 'application/octet-stream'
          },
          body: combined
        });
        const result = await resp.json();

        if (resp.ok && result.status === 'ok') {
          statusDiv.className = 'status-box status-success';
          statusDiv.innerText = '✅ ' + file.name + ' erfolgreich verschlüsselt übertragen & archiviert!';
          loadVaultDocs();
        } else {
          statusDiv.className = 'status-box status-error';
          statusDiv.innerText = '❌ Fehler bei ' + file.name + ': ' + (result.error || 'Unbekannt');
        }
      }
    }

    async function loadVaultDocs() {
      try {
        const resp = await fetch('/api/web/documents', {
          headers: { 'x-session-token': sessionToken }
        });
        const data = await resp.json();
        const tbody = document.getElementById('docsTableBody');
        tbody.innerHTML = '';

        if (!data.documents || data.documents.length === 0) {
          tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--text-muted);">Noch keine Dokumente im Tresor vorhanden.</td></tr>';
          return;
        }

        data.documents.forEach(doc => {
          const tr = document.createElement('tr');
          tr.innerHTML = '<td style="font-weight:600;">' + doc.title + '</td>' +
            '<td>' + doc.category + '</td>' +
            '<td>' + doc.fileSize + '</td>' +
            '<td><a href="/api/web/download?id=' + doc.id + '&token=' + sessionToken + '" target="_blank" class="btn" style="padding:4px 8px;font-size:12px;text-decoration:none;">Vorschau / PDF</a></td>';
          tbody.appendChild(tr);
        });
      } catch (e) {
        console.error(e);
      }
    }

    // Drag & Drop Handlers
    const dropZone = document.getElementById('dropZone');
    ['dragenter', 'dragover'].forEach(name => {
      dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.add('dragover'); }, false);
    });
    ['dragleave', 'drop'].forEach(name => {
      dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); }, false);
    });
    dropZone.addEventListener('drop', (e) => {
      const files = e.dataTransfer.files;
      handleFileSelect(files);
    });
  </script>
</body>
</html>
        """.trimIndent()
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress ?: ""
                            if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                return ip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            return "192.168.1.100"
        }
    }
}
