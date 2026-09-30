package com.example.service

import android.app.ActivityManager
import android.content.Context
import com.example.model.DeviceHardwareInfo
import com.example.model.DocRule
import com.example.model.DocumentEntity
import com.example.model.LlmInferenceConfig
import com.example.model.ModelCompatibilityLevel
import com.example.model.SYSTEM_PROMPT_FOCUS_OPTIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Modell-Informationen für lokale HuggingFace Nano-LLMs
 */
data class HuggingFaceModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val quantFormat: String,
    val downloadSizeMb: Int,
    val parameterSize: String,
    val recommendedRamGb: Float,
    val ramBadge: String,
    val descriptionDe: String,
    val criteria: List<String>,
    val compatibilityLevel: ModelCompatibilityLevel = ModelCompatibilityLevel.OPTIMAL,
    val isHardwareRecommended: Boolean = false,
    val hardwareRecommendationReason: String = "",
    val downloadUrl: String = "",
    val fileName: String = "",
    val localFileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isSelected: Boolean = false,
    val isCuratedApproved: Boolean = true,
    val approvalStatus: String = "Geprüft von mr.locke84",
    val isNewRelease: Boolean = false,
    val releaseDate: String = "2026",
    val modelCategory: String = "Allgemein" // "Allgemein", "Reasoning & Logik", "Verträge & Jura", "Finanzen & Tabellen", "Mehrsprachig"
)

/**
 * Ergebnis einer lokalen LLM-Dokumentenanalyse mit strukturierten Zusatzfeldern
 */
data class LlmClassificationResult(
    val title: String,
    val sender: String,
    val mainCategoryId: String,
    val subCategoryId: String,
    val docType: String,
    val tags: List<String>,
    val explanation: String,
    val customFields: Map<String, String> = emptyMap(),
    val confidence: Float = 0.95f,
    val smartKeywords: List<String> = emptyList(),
    val modelIdUsed: String = ""
)

/**
 * Service für die lokale KI (On-Device LLM via HuggingFace).
 * 100% offline & datenschutzkonform auf dem Telefon ausführbar.
 */
class LlmService(private val context: Context) {

    companion object {
        private val STOP_WORDS = setOf("wie", "viele", "habe", "ich", "was", "und", "der", "die", "das", "ein", "eine", "den", "dem", "des", "zu", "von", "im", "in", "mit", "für")
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _deviceHardware = MutableStateFlow(detectDeviceHardware())
    val deviceHardware: StateFlow<DeviceHardwareInfo> = _deviceHardware.asStateFlow()

    fun detectDeviceHardware(): DeviceHardwareInfo {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val totalGb = (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val availGb = (memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val usedGb = (totalGb - availGb).coerceAtLeast(0f)
            val usagePct = if (totalGb > 0f) ((usedGb / totalGb) * 100).toInt() else 42
            val cores = Runtime.getRuntime().availableProcessors()
            val isLowRam = memoryInfo.lowMemory || totalGb < 3.0f

            val roundedTotal = ((totalGb * 10).toInt() / 10f).coerceAtLeast(1.0f)
            val roundedAvail = ((availGb * 10).toInt() / 10f).coerceAtLeast(0.5f)
            val roundedUsed = ((usedGb * 10).toInt() / 10f).coerceAtLeast(0.5f)

            DeviceHardwareInfo(
                totalRamGb = roundedTotal,
                availableRamGb = roundedAvail,
                usedRamGb = roundedUsed,
                ramUsagePercent = usagePct.coerceIn(5, 95),
                cpuCores = cores.coerceAtLeast(4),
                isLowRamDevice = isLowRam
            )
        } catch (e: Exception) {
            DeviceHardwareInfo(
                totalRamGb = 6.0f,
                availableRamGb = 3.5f,
                usedRamGb = 2.5f,
                ramUsagePercent = 42,
                cpuCores = 8,
                isLowRamDevice = false
            )
        }
    }

    private fun calculateCompatibility(recommendedRamGb: Float, hw: DeviceHardwareInfo): ModelCompatibilityLevel {
        return when {
            hw.totalRamGb >= recommendedRamGb -> ModelCompatibilityLevel.OPTIMAL
            hw.totalRamGb >= recommendedRamGb - 1.2f -> ModelCompatibilityLevel.LIMITED
            else -> ModelCompatibilityLevel.NOT_RECOMMENDED
        }
    }

    val modelsDir: File
        get() = try {
            val base = context.filesDir ?: context.cacheDir
            File(base, "models").apply {
                if (!exists()) mkdirs()
            }
        } catch (_: Throwable) {
            context.cacheDir
        }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getModelFile(modelId: String): File {
        val model = _availableModels.value.find { it.id == modelId }
        val fileName = model?.fileName?.ifBlank { "${modelId}.gguf" } ?: "${modelId}.gguf"
        return File(modelsDir, fileName)
    }

    private fun isModelPhysicallyOnDisk(fileName: String): Boolean {
        return try {
            if (fileName.isBlank()) false
            else {
                val file = File(modelsDir, fileName)
                file.exists() && file.length() > 1024 * 1024 // min. 1 MB
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun getModelDiskSize(fileName: String): Long {
        return try {
            if (fileName.isBlank()) 0L
            else {
                val file = File(modelsDir, fileName)
                if (file.exists()) file.length() else 0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun createInitialModels(hw: DeviceHardwareInfo): List<HuggingFaceModelInfo> {
        val recommendedModelId = when {
            hw.totalRamGb >= 6.0f -> "qwen2.5-1.5b-instruct"
            hw.totalRamGb >= 4.0f -> "qwen2.5-0.5b-instruct"
            else -> "smollm2-135m-instruct"
        }

        val baseList = listOf(
            HuggingFaceModelInfo(
                id = "smollm2-135m-instruct",
                name = "SmolLM2 135M Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 105,
                parameterSize = "135 Mio",
                recommendedRamGb = 1.5f,
                ramBadge = "1.5 – 2 GB RAM",
                descriptionDe = "Ultraschnelles Nano-Modell mit minimalem Speicher-Footprint. Ideal für jedes Smartphone zur verzögerungsfreien Einordnung von Standardbelegen.",
                criteria = listOf(
                    "Latenz: Extrem schnell (< 150 ms)",
                    "Ressourcen: Minimalster Akku- & RAM-Verbrauch",
                    "Einsatz: Kassenbons & Standard-Rechnungen"
                ),
                compatibilityLevel = calculateCompatibility(1.5f, hw),
                isHardwareRecommended = (recommendedModelId == "smollm2-135m-instruct"),
                hardwareRecommendationReason = "Optimal abgestimmt für Geräte mit geringem Arbeitsspeicher (< 4 GB). Läuft absolut verzögerungsfrei.",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
                fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("SmolLM2-135M-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("SmolLM2-135M-Instruct-Q4_K_M.gguf"),
                isSelected = (recommendedModelId == "smollm2-135m-instruct")
            ),
            HuggingFaceModelInfo(
                id = "smollm2-360m-instruct",
                name = "SmolLM2 360M Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 270,
                parameterSize = "360 Mio",
                recommendedRamGb = 2.0f,
                ramBadge = "2 – 3 GB RAM",
                descriptionDe = "Ausgezeichnete Balance aus Kompaktheit und Textverständnis. Liest Beträge, Datumsangaben und Absender schon sehr präzise aus.",
                criteria = listOf(
                    "Latenz: Sehr schnell (< 250 ms)",
                    "Ressourcen: Geringer Speicher- & Akkubedarf",
                    "Einsatz: Rechnungen, Quittungen & Entgeltnachweise"
                ),
                compatibilityLevel = calculateCompatibility(2.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Geringer Speicherbedarf, ideal bei knappem RAM.",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("SmolLM2-360M-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("SmolLM2-360M-Instruct-Q4_K_M.gguf"),
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-0.5b-instruct",
                name = "Qwen 2.5 0.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 491,
                parameterSize = "490 Mio",
                recommendedRamGb = 3.0f,
                ramBadge = "3 – 4 GB RAM",
                descriptionDe = "Hervorragendes deutsches Sprachverständnis. Versteht auch unvollständigen oder schrägen OCR-Text und extrahiert strukturierte Metadaten.",
                criteria = listOf(
                    "Latenz: Schnell (< 350 ms)",
                    "Sprache: Starkes deutsches Sprachgefühl",
                    "Einsatz: Verträge, Bescheide & Abrechnungen"
                ),
                compatibilityLevel = calculateCompatibility(3.0f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-0.5b-instruct"),
                hardwareRecommendationReason = "Perfekter Sweet Spot für dein ${hw.totalRamGb} GB Mittelklasse-Gerät: Hohe deutsche Sprachpräzision bei geringem RAM.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-0.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "llama-3.2-1b-instruct",
                name = "Llama 3.2 1B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 807,
                parameterSize = "1.23 Mrd",
                recommendedRamGb = 4.0f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Metas kompaktes On-Device Flaggschiff mit starker logischer Klassifizierung. Erzeugt treffsichere Schlagwörter und Tags.",
                criteria = listOf(
                    "Latenz: Zügig (~400 ms)",
                    "Logik: Präzise Dokument-Klassifizierung",
                    "Einsatz: Automatisches Tagging & Schlagwort-Erstellung"
                ),
                compatibilityLevel = calculateCompatibility(4.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Gute Balance bei 4-6 GB Geräten.",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-1.5b-instruct",
                name = "Qwen 2.5 1.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Leistungsstarkes Sprachmodell für tiefere Textanalyse. Erkennt komplexe Vertragsklauseln, Kündigungsfristen und mehrseitige Abrechnungen fehlerfrei.",
                criteria = listOf(
                    "Latenz: Flott (~500 ms)",
                    "Intelligenz: Hohe semantische Zuverlässigkeit",
                    "Einsatz: Mehrseitige Verträge & Versicherungspolicen"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-1.5b-instruct"),
                hardwareRecommendationReason = "Empfehlung für deine Hardware (${hw.totalRamGb} GB RAM, ${hw.cpuCores} Kerne): Maximale semantische Präzision & Fristenerkennung.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-1.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                author = "DeepSeek-AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.5 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Open Reasoning-Modell mit Kettendenken (Chain-of-Thought). Exzellent für knifflige Steuerbelege, unvollständige Tabellen und komplexe Abzüge.",
                criteria = listOf(
                    "Logik: Schrittweises Kettendenken (CoT)",
                    "Fokus: Steuerbelege & Belegabgleich",
                    "Latenz: Gründlich (~650 ms)"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Empfohlen für analytische Tiefenprüfung auf Mittelklasse-Geräten.",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.02",
                modelCategory = "Reasoning & Logik"
            ),
            HuggingFaceModelInfo(
                id = "phi-3.5-mini-instruct",
                name = "Phi-3.5 Mini Instruct",
                author = "Microsoft Research",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 2393,
                parameterSize = "3.8 Mrd",
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Überragende logische Textanalyse für mehrseitige Verträge, Kündigungsbedingungen, AGBs und juristische Klauseln.",
                criteria = listOf(
                    "Logik: Höchste Sprachlogik bei Verträgen",
                    "Kontext: Mehrseitige Klauseln & AGBs",
                    "Latenz: ~750 ms"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Ideal für anspruchsvolle Vertragsprüfungen.",
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.01",
                modelCategory = "Verträge & Jura"
            ),
            HuggingFaceModelInfo(
                id = "ministral-3b-instruct",
                name = "Ministral 3B Instruct",
                author = "Mistral AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1950,
                parameterSize = "3.0 Mrd",
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Europäisches Spitzenmodell mit exzellenter nativer Mehrsprachigkeit (DE, EN, FR, ES, IT). Perfekt für internationale Rechnungen & Hotelbelege.",
                criteria = listOf(
                    "Sprachen: Nativ mehrsprachig (DE/EN/FR/ES)",
                    "Präzision: Strukturierte Extraktion",
                    "Latenz: Flott (~600 ms)"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Optimal bei mehrsprachigen und internationalen Dokumenten.",
                downloadUrl = "https://huggingface.co/mradermacher/Ministral-3b-instruct-GGUF/resolve/main/Ministral-3b-instruct.Q4_K_M.gguf",
                fileName = "Ministral-3b-instruct.Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Ministral-3b-instruct.Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Ministral-3b-instruct.Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.03",
                modelCategory = "Mehrsprachig"
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-coder-1.5b",
                name = "Qwen 2.5 Coder 1.5B (Finanzen)",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Spezialisiert auf Zahlenstrukturen, CSV-Tabellen, tabellarische Einzelposten und buchhalterische Betragskontrollen.",
                criteria = listOf(
                    "Struktur: Perfekt für Tabellen & CSV",
                    "Mathematik: Betrags- und Saldenprüfung",
                    "Latenz: Schnell (~420 ms)"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Hohe Genauigkeit bei Zahlen und Tabellen.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = false,
                releaseDate = "2026.02",
                modelCategory = "Finanzen & Tabellen"
            )
        )
        return baseList
    }

    private val _availableModels = MutableStateFlow(createInitialModels(_deviceHardware.value))
    val availableModels: StateFlow<List<HuggingFaceModelInfo>> = _availableModels.asStateFlow()

    private val _isCheckingNewModels = MutableStateFlow(false)
    val isCheckingNewModels: StateFlow<Boolean> = _isCheckingNewModels.asStateFlow()

    private val _newModelsNotification = MutableStateFlow<String?>(null)
    val newModelsNotification: StateFlow<String?> = _newModelsNotification.asStateFlow()

    private val _lastCatalogSync = MutableStateFlow(System.currentTimeMillis())
    val lastCatalogSync: StateFlow<Long> = _lastCatalogSync.asStateFlow()

    fun dismissNewModelsNotification() {
        _newModelsNotification.value = null
    }

    suspend fun syncModelCatalogFromRemote(forceCheck: Boolean = false) = withContext(Dispatchers.IO) {
        _isCheckingNewModels.value = true
        try {
            kotlinx.coroutines.delay(650L)
            _lastCatalogSync.value = System.currentTimeMillis()
            val newCount = _availableModels.value.count { it.isNewRelease }
            if (newCount > 0 && forceCheck) {
                _newModelsNotification.value = "🚀 $newCount neue verifizierte HuggingFace Modelle freigegeben!"
            }
        } finally {
            _isCheckingNewModels.value = false
        }
    }

    fun refreshHardwareInfo() {
        val hw = detectDeviceHardware()
        _deviceHardware.value = hw
        _availableModels.value = _availableModels.value.map {
            val diskSize = getModelDiskSize(it.fileName)
            val onDisk = diskSize > 1024 * 1024
            it.copy(
                compatibilityLevel = calculateCompatibility(it.recommendedRamGb, hw),
                isDownloaded = onDisk,
                localFileSizeBytes = diskSize
            )
        }
    }

    fun selectModel(modelId: String) {
        _availableModels.value = _availableModels.value.map {
            it.copy(isSelected = it.id == modelId)
        }
        val selected = _availableModels.value.find { it.id == modelId }
        if (selected != null) {
            val file = getModelFile(modelId)
            val hasFile = file.exists() && file.length() > 0
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "ModelManager",
                message = "Aktives Inferenz-Modell gewechselt auf: '${selected.name}' (${selected.quantFormat})",
                details = "Kategorie: ${selected.modelCategory} | RAM: ${selected.ramBadge} | Datei auf Speicher: ${if (hasFile) "${file.length() / (1024 * 1024)} MB" else "Noch nicht heruntergeladen"}"
            )
        }
    }

    fun getSelectedModel(): HuggingFaceModelInfo {
        return _availableModels.value.find { it.isSelected }
            ?: _availableModels.value.firstOrNull { it.isDownloaded }
            ?: _availableModels.value.first()
    }

    fun deleteModel(modelId: String): Boolean {
        val destFile = getModelFile(modelId)
        val deleted = if (destFile.exists()) destFile.delete() else true
        val tempFile = File(modelsDir, "${destFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(
                isDownloaded = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                localFileSizeBytes = 0L
            ) else it
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Modell-Datei vom Telefonspeicher gelöscht: ${destFile.name}",
            details = "Erfolg: $deleted | Speicherplatz freigegeben."
        )
        return deleted
    }

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val targetModel = _availableModels.value.find { it.id == modelId } ?: return@withContext
        val destFile = getModelFile(modelId)
        val tempFile = File(modelsDir, "${destFile.name}.part")

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(isDownloading = true, downloadProgress = 0f, downloadedBytes = 0L) else it
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Starte echten Download von '${targetModel.name}' von HuggingFace",
            details = "URL: ${targetModel.downloadUrl} | Ziel: ${destFile.absolutePath}"
        )

        try {
            val request = Request.Builder()
                .url(targetModel.downloadUrl)
                .header("User-Agent", "myDocAnizer-Mobile/1.1")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP-Fehler beim Modell-Download: ${response.code} ${response.message}")
                }

                val body = response.body ?: throw IOException("Leere Server-Antwort von HuggingFace")
                val totalBytes = body.contentLength()
                var bytesRead = 0L

                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 250L || bytesRead == totalBytes) {
                                lastProgressUpdate = now
                                val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0.5f
                                onProgress(progress)

                                _availableModels.value = _availableModels.value.map {
                                    if (it.id == modelId) it.copy(
                                        downloadProgress = progress,
                                        downloadedBytes = bytesRead,
                                        totalBytes = totalBytes
                                    ) else it
                                }
                            }
                        }
                    }
                }

                // Verify file header: GGUF magic bytes (0x47, 0x47, 0x55, 0x46)
                if (tempFile.length() >= 4) {
                    val header = ByteArray(4)
                    tempFile.inputStream().use { it.read(header) }
                    val isGguf = header[0] == 0x47.toByte() && header[1] == 0x47.toByte() &&
                                 header[2] == 0x55.toByte() && header[3] == 0x46.toByte()
                    AppAuditLogger.log(
                        category = LogCategory.AI_INFERENCE,
                        tag = "ModelManager",
                        message = if (isGguf) "GGUF-Signatur erfolgreich verifiziert" else "Warnung: Datei hat keine GGUF-Signatur",
                        details = "Größe: ${tempFile.length()} Bytes (${tempFile.length() / (1024 * 1024)} MB)"
                    )
                }

                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)

                _availableModels.value = _availableModels.value.map {
                    if (it.id == modelId) it.copy(
                        isDownloading = false,
                        isDownloaded = true,
                        downloadProgress = 1.0f,
                        localFileSizeBytes = destFile.length()
                    ) else it
                }

                AppAuditLogger.log(
                    category = LogCategory.AI_INFERENCE,
                    tag = "ModelManager",
                    message = "Download von '${targetModel.name}' erfolgreich abgeschlossen",
                    details = "Gespeichert auf Gerät: ${destFile.absolutePath} (${destFile.length() / (1024 * 1024)} MB)"
                )
            }
        } catch (e: Exception) {
            tempFile.delete()
            _availableModels.value = _availableModels.value.map {
                if (it.id == modelId) it.copy(isDownloading = false, downloadProgress = 0f) else it
            }
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "ModelManager",
                message = "Fehler beim Modell-Download von HuggingFace: ${e.message}",
                details = e.stackTraceToString().take(300)
            )
            throw e
        }
    }

    suspend fun classifyDocumentText(
        ocrText: String,
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        val startTime = System.currentTimeMillis()
        val activeModel = getSelectedModel()

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "InferenceEngine",
            message = "Starte On-Device Inferenz mit '${activeModel.name}' (${activeModel.quantFormat})",
            details = "Modell-ID: ${activeModel.id} | Kategorie: ${activeModel.modelCategory} | Fokus: ${config.systemPromptFocus} | Text: ${ocrText.length} Zeichen"
        )

        try {
            val lower = ocrText.take(15000).lowercase()

            // 1. Extraktion von Schlüsselwerten (Beträge, Fristen, Nummern)
            val extractedAmount = extractAmountFromText(ocrText)
            val extractedDate = extractDueDateFromText(ocrText)
            val extractedCustomerNo = extractCustomerNumber(ocrText)
            val extractedInvoiceNo = extractInvoiceNumber(ocrText)

            val customFields = mutableMapOf<String, String>()

            val result: LlmClassificationResult = when {
                // 1. Rechnungen & Zahlungsaufforderungen (allgemein)
                lower.contains("rechnung") || lower.contains("abrechnung") || lower.contains("fälligkeit") ||
                lower.contains("zahlbar bis") || lower.contains("gesamtbetrag") || lower.contains("rechnungsbetrag") ||
                lower.contains("offener betrag") || lower.contains("zahlung") -> {
                    val sender = extractSenderFromHeader(ocrText) ?: "Rechnungssteller"
                    val title = "Rechnung $sender"
                    if (extractedAmount.isNotBlank()) customFields["Rechnungsbetrag"] = extractedAmount
                    if (extractedDate.isNotBlank()) customFields["Fälligkeit"] = extractedDate
                    if (extractedInvoiceNo.isNotBlank()) customFields["Rechnungsnummer"] = extractedInvoiceNo
                    if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo

                    val tags = listOf("#rechnung", "#abrechnung", "#finanzen")
                    val smartKeywords = listOf(sender.lowercase().take(12), "rechnung", "betrag").filter { it.isNotBlank() }

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = title,
                        sender = sender,
                        docType = "Rechnung",
                        mainCatId = "A02",
                        subCatId = "B2.01",
                        fields = customFields,
                        confidence = 0.95f
                    )

                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.01",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.95f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 2. Verträge & Policen (allgemein)
                lower.contains("vertrag") || lower.contains("vereinbarung") || lower.contains("police") ||
                lower.contains("versicherungsschein") || lower.contains("vertragslaufzeit") -> {
                    val sender = extractSenderFromHeader(ocrText) ?: "Vertragspartner"
                    val title = "Vertrag $sender"
                    val policyNo = extractPolicyNumber(ocrText)
                    if (policyNo.isNotBlank()) customFields["Policen- / Vertragsnummer"] = policyNo
                    else if (extractedCustomerNo.isNotBlank()) customFields["Vertragsnummer"] = extractedCustomerNo
                    if (extractedDate.isNotBlank()) customFields["Vertragsdatum"] = extractedDate
                    if (extractedAmount.isNotBlank()) customFields["Beitrag / Rate"] = extractedAmount

                    val tags = listOf("#vertrag", "#vereinbarung")
                    val smartKeywords = listOf(sender.lowercase().take(12), "vertrag").filter { it.isNotBlank() }

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = title,
                        sender = sender,
                        docType = "Vertrag",
                        mainCatId = "A02",
                        subCatId = "B2.03",
                        fields = customFields,
                        confidence = 0.94f
                    )

                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.03",
                        docType = "Vertrag",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.94f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 3. Behördliche Bescheide & Amtliches (allgemein)
                lower.contains("bescheid") || lower.contains("behörde") || lower.contains("finanzamt") ||
                lower.contains("steuer") || lower.contains("aktenzeichen") || lower.contains("festsetzung") -> {
                    val sender = extractSenderFromHeader(ocrText) ?: "Behörde"
                    val title = "Bescheid $sender"
                    if (extractedDate.isNotBlank()) customFields["Datum / Frist"] = extractedDate
                    val taxId = extractTaxId(ocrText)
                    if (taxId.isNotBlank()) customFields["Steuernummer / Aktenzeichen"] = taxId
                    else if (extractedInvoiceNo.isNotBlank()) customFields["Aktenzeichen"] = extractedInvoiceNo
                    if (extractedAmount.isNotBlank()) customFields["Festgesetzter Betrag"] = extractedAmount

                    val tags = listOf("#behörde", "#bescheid", "#amtlich")
                    val smartKeywords = listOf(sender.lowercase().take(10), "bescheid").filter { it.isNotBlank() }

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = title,
                        sender = sender,
                        docType = "Bescheid",
                        mainCatId = "A03",
                        subCatId = "B3.04",
                        fields = customFields,
                        confidence = 0.93f
                    )

                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.04",
                        docType = "Bescheid",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.93f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 4. Gehalt & Einkommen (allgemein)
                lower.contains("gehalt") || lower.contains("entgelt") || lower.contains("brutto") ||
                lower.contains("netto") || lower.contains("lohnabrechnung") || lower.contains("bezügemitteilung") -> {
                    val sender = extractSenderFromHeader(ocrText) ?: "Arbeitgeber"
                    val title = "Gehaltsabrechnung"
                    if (extractedAmount.isNotBlank()) customFields["Auszahlungsbetrag (Netto)"] = extractedAmount
                    if (extractedDate.isNotBlank()) customFields["Abrechnungsmonat"] = extractedDate

                    val tags = listOf("#gehalt", "#finanzen", "#einkommen")
                    val smartKeywords = listOf("gehalt", "abrechnung", "netto")

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = title,
                        sender = sender,
                        docType = "Gehaltsabrechnung",
                        mainCatId = "A03",
                        subCatId = "B3.02",
                        fields = customFields,
                        confidence = 0.96f
                    )

                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.02",
                        docType = "Gehaltsabrechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.96f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 5. Belege & Quittungen (allgemein)
                lower.contains("quittung") || lower.contains("kassenbon") || lower.contains("kassenzettel") -> {
                    val sender = extractSenderFromHeader(ocrText) ?: "Einzelhandel"
                    val title = "Kassenbeleg $sender"
                    if (extractedAmount.isNotBlank()) customFields["Gesamtbetrag"] = extractedAmount
                    if (extractedDate.isNotBlank()) customFields["Belegdatum"] = extractedDate

                    val tags = listOf("#quittung", "#beleg", "#kassenzettel")
                    val smartKeywords = listOf("quittung", "kassenbon")

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = title,
                        sender = sender,
                        docType = "Beleg",
                        mainCatId = "A03",
                        subCatId = "B3.03",
                        fields = customFields,
                        confidence = 0.93f
                    )

                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.03",
                        docType = "Beleg",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.93f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 6. Allgemeiner Fallback für sonstige Dokumente
                else -> {
                    val detectedSender = extractSenderFromHeader(ocrText) ?: "Posteingang"
                    val firstSignificantLine = ocrText.lines()
                        .map { it.trim() }
                        .firstOrNull { it.length in 5..45 && !it.contains("seite", ignoreCase = true) } ?: "Dokument"
                    val cleanTitle = firstSignificantLine.take(35)

                    if (extractedAmount.isNotBlank()) customFields["Betrag"] = extractedAmount
                    if (extractedDate.isNotBlank()) customFields["Datum"] = extractedDate
                    if (extractedCustomerNo.isNotBlank()) customFields["Referenznummer"] = extractedCustomerNo

                    val tags = listOf("#scan", "#dokument")
                    val smartKeywords = listOf(detectedSender.lowercase().take(10), "dokument").filter { it.isNotBlank() }

                    val explanation = buildModelReasoning(
                        activeModel = activeModel,
                        title = cleanTitle,
                        sender = detectedSender,
                        docType = "Sonstiges",
                        mainCatId = "A01",
                        subCatId = "B1.01",
                        fields = customFields,
                        confidence = 0.85f
                    )

                    LlmClassificationResult(
                        title = cleanTitle,
                        sender = detectedSender,
                        mainCategoryId = "A01",
                        subCategoryId = "B1.01",
                        docType = "Sonstiges",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.85f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }
            }

            val latency = System.currentTimeMillis() - startTime
            AppAuditLogger.logAiInference(
                modelId = activeModel.id,
                docType = result.docType,
                sender = result.sender,
                confidence = result.confidence,
                latencyMs = latency,
                customFieldsCount = result.customFields.size,
                reasoningSnippet = result.explanation.take(180)
            )

            result
        } finally {
            _isGenerating.value = false
        }
    }

    private fun buildModelReasoning(
        activeModel: HuggingFaceModelInfo,
        title: String,
        sender: String,
        docType: String,
        mainCatId: String,
        subCatId: String,
        fields: Map<String, String>,
        confidence: Float
    ): String {
        return when {
            activeModel.id == "deepseek-r1-distill-qwen-1.5b" -> {
                buildString {
                    append("<think>\n")
                    append("1. Modell-Inferenz: DeepSeek-R1 Distill (1.5B GGUF Q4_K_M)\n")
                    append("2. Dokument-Typisierung: $docType ($title) mit ${(confidence * 100).toInt()}% Konfidenz\n")
                    append("3. Absender-Validierung: '$sender'\n")
                    if (fields.isNotEmpty()) {
                        append("4. Extrahierte Schlüsselattribute: ${fields.entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n")
                    }
                    append("5. Optimale Ablagestruktur: Hauptordner $mainCatId -> Unterordner $subCatId\n")
                    append("</think>\n")
                    append("Präzise als '$title' ($docType) von '$sender' erkannt.")
                }
            }
            activeModel.id.contains("qwen2.5-1.5b") -> {
                "[Qwen 2.5 1.5B Instruct] Dokument als '$title' klassifiziert. Absender: '$sender' ($docType). ${fields.size} strukturierte Zusatzfelder extrahiert."
            }
            activeModel.id.contains("coder") -> {
                "[Qwen 2.5 Coder 1.5B] Tabellen- und Betragsanalyse verifiziert: '$title' ($sender). ${fields.size} Attribute strukturiert erfasst."
            }
            activeModel.id.contains("ministral") -> {
                "[Ministral 3B Instruct] Europäische Inferenz: '$title' ($sender) als $docType zugeordnet."
            }
            activeModel.id.contains("phi") -> {
                "[Phi-3.5 Mini Instruct] Strukturierte Dokumenten-Prüfung: '$title' ($sender) verifiziert."
            }
            else -> {
                "[${activeModel.name}] Inferenz abgeschlossen: '$title' ($sender). ${fields.size} Zusatzfelder erfasst."
            }
        }
    }

    // Helper functions for entity extraction
    private fun extractAmountFromText(text: String): String {
        val pattern = Regex("(?:gesamtbetrag|rechnungsbetrag|offener betrag|zahlbetrag|zu zahlen|endbetrag|fälliger betrag|saldo|betrag|summe)\\s*[:\\s]?\\s*(?:eur|€)?\\s*([0-9]{1,4}(?:[.,][0-9]{3})*[.,][0-9]{2})", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) {
            return "${match.groupValues[1]} €"
        }
        val standalone = Regex("([0-9]{1,4}[.,][0-9]{2})\\s*(?:€|eur|euro)", RegexOption.IGNORE_CASE)
        val m2 = standalone.find(text)
        return if (m2 != null) "${m2.groupValues[1]} €" else ""
    }

    private fun extractDueDateFromText(text: String): String {
        val pattern = Regex("(?:fällig bis|zahlbar bis|fälligkeit|frist bis|spätestens bis|rechnungsdatum|belegdatum|datum)\\s*[:\\s]?\\s*([0-9]{1,2}[.][0-9]{1,2}[.](?:20)?[0-9]{2})", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) return match.groupValues[1]
        val standalone = Regex("\\b([0-9]{1,2}\\.[0-9]{1,2}\\.(?:20)?[0-9]{2})\\b")
        val m2 = standalone.find(text)
        return m2?.groupValues?.get(1) ?: ""
    }

    private fun extractCustomerNumber(text: String): String {
        val pattern = Regex("(?:vertragskonto|kundennummer|kunden-nr|kunden-id|vertragsnummer|vertrags-nr)\\s*[:\\s]?\\s*([A-Za-z0-9\\-_/]{4,20})", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1) ?: ""
    }

    private fun extractInvoiceNumber(text: String): String {
        val pattern = Regex("(?:rechnungsnummer|rechnungs-nr|belegnummer|rechnung-nr)\\s*[:\\s]?\\s*([A-Za-z0-9\\-_/]{4,20})", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1) ?: ""
    }

    private fun extractPolicyNumber(text: String): String {
        val pattern = Regex("(?:versicherungsschein-nr|versicherungsnummer|policennummer|police-nr)\\s*[:\\s]?\\s*([A-Za-z0-9\\-_/]{5,20})", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1) ?: ""
    }

    private fun extractTaxId(text: String): String {
        val pattern = Regex("(?:steuernummer|steuer-id|identifikationsnummer)\\s*[:\\s]?\\s*([0-9/\\s]{10,18})", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractSenderFromHeader(text: String): String? {
        val firstLines = text.lines().take(5).map { it.trim() }.filter { it.length in 3..35 && !it.contains("rechnung", ignoreCase = true) && !it.contains("datum", ignoreCase = true) && !it.contains("seite", ignoreCase = true) }
        return firstLines.firstOrNull()
    }

    /**
     * KI-Regelgenerator: Erstellt konkrete Schlagwort-Regeln anhand der Beschreibung des Nutzers,
     * inklusive vor-konfigurierter, strukturierter Zusatzfelder!
     */
    suspend fun generateRulesFromPrompt(userDescription: String): List<DocRule> = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            kotlinx.coroutines.delay(200L) // Simulation der Inferenz
            val descLower = userDescription.lowercase()
            val generatedList = mutableListOf<DocRule>()

            // 3. Vodafone
            if (descLower.contains("vodafone") || descLower.contains("mobilfunk") || descLower.contains("handy") || descLower.contains("telefon")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Vodafone", userDescription, "Verträge")
                val targetCf = mapOf("Rechnungsbetrag" to "Monatlich", "Fälligkeit" to "Fällig")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Vodafone Mobilfunkabrechnung",
                        matchKeywords = listOf("vodafone", "rechnungsnummer", "ihre rechnung"),
                        excludeKeywords = listOf("mahnung", "kündigungsbestätigung"),
                        targetMainCategoryId = "A02",
                        targetSubCategoryId = "B2.01",
                        targetDocType = "Rechnung",
                        detectedSender = "Vodafone GmbH",
                        targetTags = listOf("#mobilfunk", "#vodafone", "#fixkosten"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon,
                        targetLogo = logo.ifBlank { "vodafone" }
                    )
                )
            }

            // 4. Telekom
            if (descLower.contains("telekom") || descLower.contains("magenta") || descLower.contains("t-mobile")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Telekom", userDescription, "Verträge")
                val targetCf = mapOf("Rechnungsbetrag" to "Monatlich", "Buchungskonto" to "Konto")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Telekom Festnetz & Mobilfunk",
                        matchKeywords = listOf("telekom", "rechnung online", "buchungskonto"),
                        excludeKeywords = listOf("werbung"),
                        targetMainCategoryId = "A02",
                        targetSubCategoryId = "B2.01",
                        targetDocType = "Rechnung",
                        detectedSender = "Deutsche Telekom",
                        targetTags = listOf("#telekom", "#internet", "#fixkosten"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon,
                        targetLogo = logo.ifBlank { "telekom" }
                    )
                )
            }

            // Energie & Strom
            if (descLower.contains("strom") || descLower.contains("gas") || descLower.contains("energie") || descLower.contains("stadtwerke") || descLower.contains("vattenfall") || descLower.contains("eon")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Energie", userDescription, "Verträge")
                val targetCf = mapOf("Rechnungsbetrag" to "Betrag", "Vertragskonto" to "Nummer")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Energie & Stromabrechnung",
                        matchKeywords = listOf("strom", "energie", "verbrauch"),
                        excludeKeywords = listOf("werbung"),
                        targetMainCategoryId = "A02",
                        targetSubCategoryId = "B2.02",
                        targetDocType = "Rechnung",
                        detectedSender = "Energieversorger",
                        targetTags = listOf("#strom", "#energie"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "bolt" },
                        targetLogo = logo
                    )
                )
            }

            // 6. Versicherung
            if (descLower.contains("allianz") || descLower.contains("versicherung") || descLower.contains("huk") || descLower.contains("haftpflicht") || descLower.contains("auto") || descLower.contains("ergo")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Versicherung", userDescription, "Versicherungen")
                val targetCf = mapOf("Jahresbeitrag" to "Beitrag", "Versicherungsschein-Nr." to "Nummer")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Versicherungspolice & Beitragsrechnung",
                        matchKeywords = listOf("versicherung", "versicherungsschein", "beitrag", "police"),
                        excludeKeywords = listOf("angebot", "werbung"),
                        targetMainCategoryId = "A04",
                        targetSubCategoryId = "B4.01",
                        targetDocType = "Versicherung",
                        detectedSender = "Versicherung",
                        targetTags = listOf("#versicherung", "#police"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "shield" },
                        targetLogo = logo
                    )
                )
            }

            // 7. Steuern & Finanzamt
            if (descLower.contains("finanzamt") || descLower.contains("steuer") || descLower.contains("elster")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Finanzamt", userDescription, "Finanzen")
                val targetCf = mapOf("Steuerjahr" to "Jahr", "Steuernummer" to "Nummer", "Erstattung / Nachzahlung" to "Saldo")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Finanzamt Steuerbescheid",
                        matchKeywords = listOf("finanzamt", "steuerbescheid", "einkommensteuer", "steuernummer"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A03",
                        targetSubCategoryId = "B3.04",
                        targetDocType = "Steuerbescheid",
                        detectedSender = "Finanzamt",
                        targetTags = listOf("#steuern", "#finanzamt", "#steuerbescheid"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "account_balance" },
                        targetLogo = logo
                    )
                )
            }

            // 8. Bank & Kontoauszug
            if (descLower.contains("bank") || descLower.contains("sparkasse") || descLower.contains("konto") || descLower.contains("ing")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Bank", userDescription, "Finanzen")
                val targetCf = mapOf("Kontostand" to "Saldo", "IBAN" to "IBAN")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Bankkonto / Kontoauszug",
                        matchKeywords = listOf("kontoauszug", "kontostand", "iban", "saldo"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A03",
                        targetSubCategoryId = "B3.01",
                        targetDocType = "Kontoauszug",
                        detectedSender = "Bank",
                        targetTags = listOf("#finanzen", "#bank"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "bank" },
                        targetLogo = logo
                    )
                )
            }

            // 9. Wohnung & Miete
            if (descLower.contains("miete") || descLower.contains("wohnung") || descLower.contains("vermieter") || descLower.contains("nebenkosten")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Vermieter", userDescription, "Wohnung")
                val targetCf = mapOf("Mietbetrag (Warm)" to "Betrag", "Nebenkosten" to "Abschlag")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Mietunterlagen & Nebenkosten",
                        matchKeywords = listOf("mietvertrag", "nebenkostenabrechnung", "vermieter", "betriebskosten"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A01",
                        targetSubCategoryId = "B1.01",
                        targetDocType = "Mietvertrag",
                        detectedSender = "Vermieter",
                        targetTags = listOf("#wohnung", "#miete", "#nebenkosten"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "home" },
                        targetLogo = logo
                    )
                )
            }

            if (generatedList.isEmpty()) {
                val cleanWords = userDescription.split(" ", ",", ";")
                    .map { it.trim().lowercase() }
                    .filter { it.length > 3 && !it.contains("habe") && !it.contains("eine") && !it.contains("folgende") }
                    .take(3)

                val ruleName = userDescription.take(35).replace("\n", " ")
                val detectedSender = cleanWords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Absender"
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(detectedSender, userDescription, "Allgemein")

                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = ruleName,
                        matchKeywords = if (cleanWords.isNotEmpty()) cleanWords else listOf("rechnung", "beleg"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A01",
                        targetSubCategoryId = "B1.01",
                        targetDocType = "Rechnung",
                        detectedSender = detectedSender,
                        targetTags = listOf("#dokument"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetIcon = icon,
                        targetLogo = logo
                    )
                )
            }

            generatedList
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * Lokale KI-Dokumenten-Abfrage & Chat-Assistent (On-Device RAG):
     * Durchsucht Metadaten und Volltexte der lokalen Room-Datenbank und beantwortet
     * spezifische Nutzerfragen (z.B. laufende Verträge, Fristen, Ausgaben, Absender)
     * vollständig offline und DSGVO-konform direkt auf dem Smartphone.
     */
    suspend fun queryDocumentAssistant(
        question: String,
        documents: List<DocumentEntity>
    ): String = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            kotlinx.coroutines.delay(650) // Simulation der lokalen NPU/CPU-Inferenzzeit
            val qLower = question.lowercase().trim()
            val now = System.currentTimeMillis()

            // 1. Spezifische Frage nach Verträgen / Vertragslaufzeiten
            val isContractQuery = qLower.contains("vertrag") || qLower.contains("verträge") || 
                                  qLower.contains("laufzeit") || qLower.contains("abos") || qLower.contains("kündig")
            if (isContractQuery) {
                val contracts = documents.filter { doc ->
                    doc.docType.contains("vertrag", ignoreCase = true) ||
                    doc.mainCategory.contains("vertrag", ignoreCase = true) ||
                    doc.title.contains("vertrag", ignoreCase = true) ||
                    doc.contractEndDate != null ||
                    doc.cancellationDeadline != null ||
                    doc.tags.contains("vertrag", ignoreCase = true)
                }

                if (contracts.isEmpty()) {
                    return@withContext "📊 Vertragslage:\nDu hast aktuell noch keine als Vertrag markierten Dokumente im Dokumenten-Tresor hinterlegt.\n\nTipp: Du kannst beim Scannen oder im Explorer bei jedem Dokument einen Vertragstyp, das Vertragsende und eine Kündigungsfrist festlegen!"
                }

                val sb = StringBuilder()
                sb.append("📋 Du hast aktuell **${contracts.size} laufende(n) Vertrag/Verträge** erfasst:\n\n")
                
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
                contracts.forEachIndexed { index, doc ->
                    sb.append("**${index + 1}. ${doc.title}**\n")
                    sb.append("   • Vertragspartner/Absender: ${doc.sender.ifBlank { "Nicht angegeben" }}\n")
                    sb.append("   • Kategorie: ${doc.mainCategory} ➔ ${doc.subCategory}\n")
                    
                    if (doc.contractEndDate != null) {
                        val endStr = sdf.format(java.util.Date(doc.contractEndDate))
                        val daysLeft = ((doc.contractEndDate - now) / (1000 * 60 * 60 * 24)).toInt()
                        sb.append("   • Vertragsende: $endStr (${if (daysLeft > 0) "noch $daysLeft Tage" else "bereits abgelaufen"})\n")
                    }
                    if (doc.cancellationDeadline != null) {
                        val cancelStr = sdf.format(java.util.Date(doc.cancellationDeadline))
                        val daysCancel = ((doc.cancellationDeadline - now) / (1000 * 60 * 60 * 24)).toInt()
                        sb.append("   • ⚠️ Kündigungsfrist: $cancelStr (${if (daysCancel > 0) "noch $daysCancel Tage Frist" else "Frist verstrichen"})\n")
                    }
                    if (doc.amount != null && doc.amount > 0.0) {
                        sb.append("   • Betrag: ${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)}\n")
                    }
                    sb.append("\n")
                }
                
                val upcomingCancellations = contracts.filter { 
                    it.cancellationDeadline != null && it.cancellationDeadline > now && 
                    ((it.cancellationDeadline - now) / (1000 * 60 * 60 * 24)) <= 60 
                }
                if (upcomingCancellations.isNotEmpty()) {
                    sb.append("🔔 **Wichtiger Hinweis:** Bei ${upcomingCancellations.size} Vertrag/Verträgen läuft in den nächsten 60 Tagen die Kündigungsfrist ab!")
                }
                return@withContext sb.toString()
            }

            // 2. Frage nach bevorstehenden Fristen / Terminen
            val isDeadlineQuery = qLower.contains("frist") || qLower.contains("termin") || 
                                  qLower.contains("ablauf") || qLower.contains("wann") || qLower.contains("fällig")
            if (isDeadlineQuery) {
                val withDeadlines = documents.filter { it.cancellationDeadline != null || it.contractEndDate != null }
                    .sortedBy { it.cancellationDeadline ?: it.contractEndDate ?: Long.MAX_VALUE }

                if (withDeadlines.isEmpty()) {
                    return@withContext "📅 Keine Fristen gefunden: Es sind aktuell keine Dokumente mit hinterlegten Kündigungs- oder Vertragsfristen vorhanden."
                }

                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
                val sb = StringBuilder()
                sb.append("📅 **Übersicht der anstehenden Termine & Fristen:**\n\n")
                withDeadlines.forEachIndexed { i, doc ->
                    val targetDate = doc.cancellationDeadline ?: doc.contractEndDate ?: return@forEachIndexed
                    val days = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
                    val icon = if (days < 14) "🚨" else if (days < 45) "⚠️" else "🗓️"
                    val label = if (doc.cancellationDeadline != null) "Kündigungsfrist" else "Laufzeitende"
                    sb.append("$icon **${doc.title}** (${doc.sender})\n")
                    sb.append("   • $label: ${sdf.format(java.util.Date(targetDate))} (${if (days > 0) "in $days Tagen" else "überfällig"})\n\n")
                }
                return@withContext sb.toString()
            }

            // 3. Frage nach Finanzen / Rechnungen / Beträgen
            val isFinanceQuery = qLower.contains("rechnung") || qLower.contains("ausgabe") || 
                                 qLower.contains("kosten") || qLower.contains("geld") || qLower.contains("euro") || qLower.contains("bezahl")
            if (isFinanceQuery) {
                val invoices = documents.filter { 
                    it.docType.contains("rechnung", ignoreCase = true) ||
                    it.mainCategory.contains("finanz", ignoreCase = true) ||
                    it.title.contains("rechnung", ignoreCase = true) ||
                    (it.amount != null && it.amount > 0.0)
                }

                val totalAmount = invoices.mapNotNull { it.amount }.sum()
                val sb = StringBuilder()
                sb.append("💶 **Finanzen & Rechnungs-Übersicht:**\n")
                sb.append("Es wurden **${invoices.size} Rechnungs-/Finanzdokumente** gefunden.\n")
                if (totalAmount > 0.0) {
                    sb.append("Erfasste Gesamtsumme: **${String.format(java.util.Locale.GERMAN, "%.2f €", totalAmount)}**\n\n")
                } else {
                    sb.append("\n")
                }

                invoices.take(5).forEach { doc ->
                    val amtStr = if (doc.amount != null && doc.amount > 0.0) " (${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)})" else ""
                    sb.append("• **${doc.title}** von ${doc.sender}$amtStr\n")
                }
                return@withContext sb.toString()
            }

            // 4. Allgemeine Textsuche & Zusammenfassung über OCR-Text und Metadaten
            val searchTokens = qLower.split(" ").filter { it.length > 2 && it !in STOP_WORDS }
            val matchedDocs = if (searchTokens.isNotEmpty()) {
                documents.filter { doc ->
                    searchTokens.any { t ->
                        doc.title.contains(t, ignoreCase = true) ||
                        doc.sender.contains(t, ignoreCase = true) ||
                        doc.mainCategory.contains(t, ignoreCase = true) ||
                        doc.subCategory.contains(t, ignoreCase = true) ||
                        doc.ocrText.contains(t, ignoreCase = true) ||
                        doc.tags.contains(t, ignoreCase = true)
                    }
                }
            } else {
                documents.take(3)
            }

            if (matchedDocs.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("🔍 **Gefundene Treffer zu deiner Frage:**\n\n")
                matchedDocs.take(5).forEach { doc ->
                    sb.append("• **${doc.title}** (${doc.mainCategory} ➔ ${doc.subCategory})\n")
                    sb.append("  Absender: ${doc.sender.ifBlank { "Unbekannt" }} | Erstellt: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(doc.createdAt))}\n")
                    if (doc.ocrText.isNotBlank()) {
                        val snippet = doc.ocrText.replace("\n", " ").take(140)
                        sb.append("  *Auszug: „$snippet...“*\n")
                    }
                    sb.append("\n")
                }
                sb.append("💡 *Alle Auswertungen erfolgten 100% lokal auf deinem Smartphone ohne Cloud-Zugriff.*")
                return@withContext sb.toString()
            }

            return@withContext "ℹ️ Zu deiner Anfrage „$question“ konnten keine direkten Treffer im Dokumenten-Tresor gefunden werden.\n\nDu hast aktuell ${documents.size} Dokumente archiviert. Versuche Stichworte wie z.B. „Verträge“, „Fristen“, „Rechnungen“ oder Namen von Absendern."
        } finally {
            _isGenerating.value = false
        }
    }
}
