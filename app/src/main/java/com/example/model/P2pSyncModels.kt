package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modell für ein gekoppeltes Zweitgerät (PC, Tablet, anderes Smartphone)
 * im lokalen Master-Master WLAN-Netzwerk.
 */
data class PairedDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int = 8765,
    val sharedSecret: String, // 256-Bit Pre-Shared Key für AES-GCM
    val isAuthorized: Boolean = false, // Erst nach Bestätigung im UI autorisiert
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "Nie synchronisiert",
    val role: String = "Master-Peer",
    val deviceType: String = "ANDROID_PEER" // "ANDROID_PEER", "DESKTOP_LIGHT_APP", "WEB_PORTAL"
) {
    val lastSyncFormatted: String
        get() = if (lastSyncTimestamp > 0L) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(lastSyncTimestamp))
        } else {
            "Noch nie"
        }
}

/**
 * Entschlüsselte Nutzdaten aus dem QR-Code der myDocAnizer-Desktop App.
 */
data class DesktopQrPairingData(
    val version: Int = 2,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "myDocAnizer-Desktop PC",
    val ip: String,
    val port: Int = 8765,
    val pubKey: String = "",
    val token: String = "",
    val fingerprint: String = ""
)

/**
 * Eingehende Autorisierungsanfrage eines fremden Geräts im lokalen WLAN.
 */
data class IncomingPairingRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val clientDeviceId: String,
    val clientDeviceName: String,
    val clientIp: String,
    val pinEntered: String,
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * Ursachen für einen nicht eindeutigen Sync-Status / Konflikt.
 */
enum class ConflictCauseType(val title: String, val badgeColorHex: Long) {
    CONCURRENT_EDIT("Gleichzeitige Bearbeitung", 0xFFD97706),
    DELETE_VS_EDIT("Löschen vs. Bearbeiten", 0xFFDC2626),
    HASH_MISMATCH("Prüfsummen-Abweichung", 0xFFE11D48),
    FILE_MISSING("PDF-Datei fehlt", 0xFF7C3AED),
    CATEGORY_DIVERGENCE("Ordner-Diskrepanz", 0xFF2563EB)
}

/**
 * Schnappschuss eines Dokuments zur visuellen Gegenüberstellung bei Konflikten.
 */
data class DocumentSummarySnapshot(
    val title: String,
    val sender: String,
    val mainCategory: String,
    val subCategory: String,
    val modifiedAt: Long,
    val fileSizeFormatted: String,
    val tags: String = "",
    val ocrSnippet: String = ""
) {
    val modifiedFormatted: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN).format(Date(modifiedAt))
}

/**
 * Erkannter Konflikt bei Master-Master-Synchronisation mit detaillierter Ursachenanalyse
 * und manueller Lösungsmöglichkeit.
 */
data class SyncConflict(
    val id: String = UUID.randomUUID().toString(),
    val docId: Long,
    val docTitle: String,
    val peerName: String,
    val causeType: ConflictCauseType,
    val diagnosticMessage: String,
    val localSummary: DocumentSummarySnapshot,
    val remoteSummary: DocumentSummarySnapshot,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val resolutionChoice: String? = null
)

/**
 * Protokolleintrag eines Sync-Vorgangs im Audit-Log.
 */
data class P2pSyncLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val peerName: String,
    val peerIp: String,
    val direction: String, // "BIDIRECTIONAL", "SEND", "RECEIVE"
    val docsSent: Int = 0,
    val docsReceived: Int = 0,
    val conflictsCount: Int = 0,
    val status: String, // "SUCCESS", "CONFLICTS", "ERROR"
    val diagnosticMessage: String
) {
    val timestampFormatted: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN).format(Date(timestamp))
}

/**
 * Fortschrittsanzeige während einer aktiven P2P-Synchronisation.
 */
data class P2pSyncProgressState(
    val isSyncing: Boolean = false,
    val peerName: String = "",
    val currentStage: String = "",
    val progressPercent: Float = 0f,
    val itemsProcessed: Int = 0,
    val totalItems: Int = 0,
    val errorOrWarning: String? = null
)

/**
 * Server-Zustand des lokalen P2P-Dienstes.
 */
data class P2pServerStatus(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val port: Int = 8765,
    val deviceName: String = "DocAnizer Mobile",
    val activeConnections: Int = 0,
    val pairedDevicesCount: Int = 0
)

/**
 * Mögliche Aktionen zur Behebung eines Sync-Konflikts.
 */
enum class ConflictResolutionAction {
    KEEP_LOCAL,          // Lokale Version behalten (Remote überschreiben)
    ACCEPT_REMOTE,       // Entfernte Version übernehmen (Lokal überschreiben)
    KEEP_BOTH_AS_COPY,   // Beide behalten (Konflikt-Kopie anlegen)
    DECIDE_LATER         // Später entscheiden
}
