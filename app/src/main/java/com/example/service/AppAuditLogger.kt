package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogCategory(val label: String, val icon: String) {
    AI_INFERENCE("Lokale KI", "🤖"),
    OCR("Texterkennung", "👁️"),
    RULE_ENGINE("Regel-Engine", "⚡"),
    STORAGE_DMS("Dokumenten-Tresor", "📁"),
    P2P_SYNC("WLAN Sync", "🔄"),
    SYSTEM("System & App", "⚙️"),
    ERROR("Fehler", "🚨")
}

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val tag: String,
    val message: String,
    val details: String = "",
    val isError: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.GERMAN).format(Date(timestamp))

    fun toLogLine(): String {
        val errPrefix = if (isError) "[ERROR] " else ""
        val detailsStr = if (details.isNotBlank()) " | Details: $details" else ""
        return "[$formattedTime] [${category.name}] [$tag] $errPrefix$message$detailsStr"
    }
}

/**
 * 100% lokales, datenschutzkonformes Ereignis- & KI-Diagnose-Protokoll.
 * Protokolliert alle wesentlichen App-Aktionen, OCR-Ergebnisse und KI-Inferenz-Details
 * direkt auf dem Gerät, damit der Nutzer jederzeit nachvollziehen kann, wie Dokumente
 * eingestuft wurden und welche Daten lokal verarbeitet werden.
 */
object AppAuditLogger {

    private const val MAX_MEMORY_LOGS = 300
    private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 2L // 2 MB

    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logLock = Any()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        synchronized(logLock) {
            if (appContext == null) {
                appContext = appCtx
                loadInitialLogsFromFile(appCtx)
                log(LogCategory.SYSTEM, "AppAuditLogger", "Diagnose-Logger erfolgreich initialisiert (100% lokales Audit-Protokoll)")
            }
        }
    }

    fun log(
        category: LogCategory,
        tag: String,
        message: String,
        details: String = "",
        isError: Boolean = false
    ) {
        val entry = AuditLogEntry(
            category = category,
            tag = tag,
            message = message,
            details = details,
            isError = isError
        )

        // Logcat für Android-Debugging
        if (isError) {
            Log.e("myDocAnizer", "[${category.name}] [$tag] $message $details")
        } else {
            Log.i("myDocAnizer", "[${category.name}] [$tag] $message $details")
        }

        // In-Memory Liste aktualisieren
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Neueste Einträge zuerst
        if (current.size > MAX_MEMORY_LOGS) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current

        // Im Hintergrund auf die rotierende Logdatei schreiben
        val ctx = appContext
        if (ctx != null) {
            scope.launch {
                appendToFile(ctx, entry)
            }
        }
    }

    fun logAiInference(
        modelId: String,
        docType: String,
        sender: String,
        confidence: Float,
        latencyMs: Long,
        customFieldsCount: Int,
        reasoningSnippet: String
    ) {
        log(
            category = LogCategory.AI_INFERENCE,
            tag = "InferenceEngine",
            message = "Dokument als '$docType' ($sender) eingestuft",
            details = "Modell: $modelId | Latenz: ${latencyMs}ms | Konfidenz: ${(confidence * 100).toInt()}% | Zusatzfelder: $customFieldsCount | Begründung: $reasoningSnippet"
        )
    }

    fun logOcr(charCount: Int, durationMs: Long, preview: String) {
        log(
            category = LogCategory.OCR,
            tag = "MLKitOcr",
            message = "$charCount Zeichen erfolgreich offline erkannt (${durationMs}ms)",
            details = "Textprobe: \"${preview.replace("\n", " ").take(100)}\""
        )
    }

    fun logRuleMatch(ruleName: String, keywordsMatched: List<String>) {
        log(
            category = LogCategory.RULE_ENGINE,
            tag = "DocRuleRepo",
            message = "Treffer durch deterministische Regel '$ruleName'",
            details = "Schlagwörter: ${keywordsMatched.joinToString(", ")}"
        )
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let { Log.getStackTraceString(it) } ?: ""
        log(
            category = LogCategory.ERROR,
            tag = tag,
            message = message,
            details = stackTrace.take(300),
            isError = true
        )
    }

    private fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }
        return File(dir, "myDocAnizer_audit.log")
    }

    private fun appendToFile(context: Context, entry: AuditLogEntry) {
        synchronized(logLock) {
            try {
                val file = getLogFile(context)
                if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                    val oldFile = File(file.parentFile, "myDocAnizer_audit.old.log")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }
                FileOutputStream(file, true).use { fos ->
                    fos.write((entry.toLogLine() + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.w("AppAuditLogger", "Fehler beim Schreiben in Logdatei: ${e.message}")
            }
        }
    }

    private fun loadInitialLogsFromFile(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                val lines = file.readLines(Charsets.UTF_8).takeLast(MAX_MEMORY_LOGS)
                val parsedList = lines.mapNotNull { line ->
                    // Einfacher Parser für bestehende Logzeilen
                    AuditLogEntry(
                        timestamp = System.currentTimeMillis(),
                        category = when {
                            line.contains("[AI_INFERENCE]") -> LogCategory.AI_INFERENCE
                            line.contains("[OCR]") -> LogCategory.OCR
                            line.contains("[RULE_ENGINE]") -> LogCategory.RULE_ENGINE
                            line.contains("[STORAGE_DMS]") -> LogCategory.STORAGE_DMS
                            line.contains("[P2P_SYNC]") -> LogCategory.P2P_SYNC
                            line.contains("[ERROR]") -> LogCategory.ERROR
                            else -> LogCategory.SYSTEM
                        },
                        tag = "Historie",
                        message = line.substringAfter("] ").take(160),
                        details = line,
                        isError = line.contains("[ERROR]")
                    )
                }.reversed()
                _logs.value = parsedList
            }
        } catch (_: Exception) {}
    }

    /**
     * Erstellt eine saubere, exportierbare Textdatei mit allen System- & KI-Logs
     */
    fun exportLogFile(context: Context): File {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(exportDir, "myDocAnizer_Diagnose_Protokoll_$dateStr.txt")

        exportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("================================================================================\n")
            writer.write("                 myDocAnizer-Mobile - DIAGNOSE & AUDIT-PROTOKOLL\n")
            writer.write("================================================================================\n")
            writer.write("Erstellt am:   $dateStr\n")
            writer.write("Datenschutz:   100% On-Device Audit-Log (Keine Cloud-Übertragung)\n")
            writer.write("Gerät:         ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
            writer.write("================================================================================\n\n")

            val file = getLogFile(context)
            if (file.exists()) {
                file.forEachLine { line ->
                    writer.write(line)
                    writer.write("\n")
                }
            } else {
                _logs.value.reversed().forEach { entry ->
                    writer.write(entry.toLogLine())
                    writer.write("\n")
                }
            }
        }
        return exportFile
    }

    fun clearLogs(context: Context) {
        synchronized(logLock) {
            _logs.value = emptyList()
            try {
                val file = getLogFile(context)
                if (file.exists()) file.delete()
                val oldFile = File(file.parentFile, "myDocAnizer_audit.old.log")
                if (oldFile.exists()) oldFile.delete()
            } catch (_: Exception) {}
            log(LogCategory.SYSTEM, "AppAuditLogger", "Diagnose-Logdatei wurde vom Nutzer geleert.")
        }
    }
}
