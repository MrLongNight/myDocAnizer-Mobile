package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.AppDatabase
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class P2pSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("p2p_sync_prefs", Context.MODE_PRIVATE)

    // Eigener Gerätename (z.B. "Pixel 8 Pro (MrLocke)")
    private val _localDeviceName = MutableStateFlow(
        prefs.getString("p2p_device_name", "myDocAnizer Smartphone") ?: "myDocAnizer Smartphone"
    )
    val localDeviceName: StateFlow<String> = _localDeviceName.asStateFlow()

    fun setLocalDeviceName(name: String) {
        prefs.edit().putString("p2p_device_name", name.trim()).apply()
        _localDeviceName.value = name.trim()
    }

    // Liste gekoppelter Geräte
    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(loadPairedDevices())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    // Eingebetteter P2P Server
    val server = P2pSyncServer(
        context = context,
        database = database,
        deviceNameProvider = { _localDeviceName.value },
        getPairedDevices = { _pairedDevices.value },
        onDevicePaired = { newDevice ->
            addPairedDevice(newDevice)
        },
        onDocumentSynced = {
            // Document inserted/updated
        }
    )

    // Master-Master Sync Engine
    val engine = P2pSyncEngine(
        context = context,
        database = database,
        localDeviceNameProvider = { _localDeviceName.value }
    )

    val serverStatus: StateFlow<P2pServerStatus> = server.serverStatus
    val hostPairingPin: StateFlow<String?> = server.hostPairingPin
    val pendingPairingRequests: StateFlow<List<IncomingPairingRequest>> = server.pendingPairingRequests
    val syncProgress: StateFlow<P2pSyncProgressState> = engine.syncProgress
    val activeConflicts: StateFlow<List<SyncConflict>> = engine.activeConflicts
    val syncHistory: StateFlow<List<P2pSyncLog>> = engine.syncHistory

    init {
        // P2P-Server nur starten, wenn explizit vom Nutzer aktiviert
        val autoStart = prefs.getBoolean("p2p_autostart_server", false)
        if (autoStart) {
            scope.launch(Dispatchers.IO) {
                try {
                    server.startServer()
                } catch (e: Throwable) {
                    // Safe guard
                }
            }
        }
    }

    fun startServer() {
        prefs.edit().putBoolean("p2p_autostart_server", true).apply()
        server.startServer()
    }

    fun stopServer() {
        prefs.edit().putBoolean("p2p_autostart_server", false).apply()
        server.stopServer()
    }

    fun startHostPairingMode(): String {
        return server.enableHostPairingMode()
    }

    fun stopHostPairingMode() {
        server.disableHostPairingMode()
    }

    fun authorizePairingRequest(requestId: String, allow: Boolean) {
        server.authorizePairingRequest(requestId, allow)
    }

    suspend fun pairWithRemoteDevice(ip: String, port: Int, pin: String): Result<PairingResult> {
        val res = engine.requestPairingWithPeer(ip, port, pin)
        res.onSuccess { result ->
            if (result is PairingResult.Success) {
                addPairedDevice(result.device)
            }
        }
        return res
    }

    /**
     * Koppelt die Android-App mit der myDocAnizer-Desktop App anhand des gescannten QR-Codes.
     */
    suspend fun pairWithDesktopQrData(qrJson: String): Result<PairedDevice> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val json = JSONObject(qrJson.trim())
            val id = json.optString("id", java.util.UUID.randomUUID().toString())
            val name = json.optString("name", "myDocAnizer-Desktop PC")
            val ip = json.optString("ip", "")
            val port = json.optInt("port", 8765)
            val token = json.optString("token", "")

            if (ip.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Ungültige IP-Adresse im Desktop-QR-Code."))
            }

            val sharedSecret = P2pSyncSecurityService.generateSharedSecret()
            val device = PairedDevice(
                id = id,
                name = name,
                ipAddress = ip,
                port = port,
                sharedSecret = sharedSecret,
                isAuthorized = true,
                pairedAt = System.currentTimeMillis(),
                lastSyncStatus = "Erfolgreich verbunden",
                role = "Desktop-Light-Spiegel",
                deviceType = "DESKTOP_LIGHT_APP"
            )

            // Optional Handshake-Aufruf an den Desktop
            try {
                val url = java.net.URL("http://$ip:$port/api/pair/confirm")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().apply {
                    put("clientDeviceId", java.util.UUID.randomUUID().toString())
                    put("clientDeviceName", _localDeviceName.value)
                    put("sharedSecret", sharedSecret)
                    put("token", token)
                }.toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            } catch (e: Exception) {
                // Lokale Kopplung bleibt auch bei kurzem Netzwerk-Timeout gültig
            }

            addPairedDevice(device)
            Result.success(device)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncWithDevice(device: PairedDevice): Result<P2pSyncLog> {
        return engine.performMasterMasterSync(device) { updated ->
            updatePairedDevice(updated)
        }
    }

    fun syncAllPairedDevices(onComplete: (Int, Int) -> Unit = { _, _ -> }) {
        scope.launch {
            var successCount = 0
            var conflictCount = 0
            val devices = _pairedDevices.value.filter { it.isAuthorized }
            for (dev in devices) {
                val res = syncWithDevice(dev)
                res.onSuccess { log ->
                    if (log.conflictsCount > 0) conflictCount += log.conflictsCount
                    successCount++
                }
            }
            onComplete(successCount, conflictCount)
        }
    }

    suspend fun resolveConflict(conflict: SyncConflict, action: ConflictResolutionAction) {
        val peer = _pairedDevices.value.find { it.name == conflict.peerName }
        engine.resolveConflict(conflict, action, peer)
    }

    fun addPairedDevice(device: PairedDevice) {
        val existing = _pairedDevices.value.filterNot { it.id == device.id || it.ipAddress == device.ipAddress }
        val updated = existing + device
        _pairedDevices.value = updated
        savePairedDevices(updated)
    }

    fun updatePairedDevice(device: PairedDevice) {
        val updated = _pairedDevices.value.map { if (it.id == device.id) device else it }
        _pairedDevices.value = updated
        savePairedDevices(updated)
    }

    fun removePairedDevice(deviceId: String) {
        val updated = _pairedDevices.value.filterNot { it.id == deviceId }
        _pairedDevices.value = updated
        savePairedDevices(updated)
    }

    private fun loadPairedDevices(): List<PairedDevice> {
        val json = prefs.getString("paired_devices_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<PairedDevice>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PairedDevice(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        ipAddress = obj.getString("ipAddress"),
                        port = obj.optInt("port", 8765),
                        sharedSecret = obj.getString("sharedSecret"),
                        isAuthorized = obj.optBoolean("isAuthorized", true),
                        pairedAt = obj.optLong("pairedAt", System.currentTimeMillis()),
                        lastSyncTimestamp = obj.optLong("lastSyncTimestamp", 0L),
                        lastSyncStatus = obj.optString("lastSyncStatus", "Bereit")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePairedDevices(list: List<PairedDevice>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("ipAddress", item.ipAddress)
                put("port", item.port)
                put("sharedSecret", item.sharedSecret)
                put("isAuthorized", item.isAuthorized)
                put("pairedAt", item.pairedAt)
                put("lastSyncTimestamp", item.lastSyncTimestamp)
                put("lastSyncStatus", item.lastSyncStatus)
            }
            array.put(obj)
        }
        prefs.edit().putString("paired_devices_json", array.toString()).apply()
    }
}
