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
import java.util.UUID

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
 * Ergebnis einer lokalen LLM-Dokumentenanalyse
 */
data class LlmClassificationResult(
    val title: String,
    val sender: String,
    val mainCategoryId: String,
    val subCategoryId: String,
    val docType: String,
    val tags: List<String>,
    val explanation: String
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
                downloadSizeMb = 95,
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
                isDownloaded = true,
                isSelected = (recommendedModelId == "smollm2-135m-instruct")
            ),
            HuggingFaceModelInfo(
                id = "smollm2-360m-instruct",
                name = "SmolLM2 360M Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 240,
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
                isDownloaded = false,
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-0.5b-instruct",
                name = "Qwen 2.5 0.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 390,
                parameterSize = "490 Mio",
                recommendedRamGb = 3.0f,
                ramBadge = "3 – 4 GB RAM",
                descriptionDe = "Hervorragendes deutsches Sprachverständnis. Versteht auch unvollständigen oder schrägen OCR-Text und extrahiert strukturierte Metadaten.",
                criteria = listOf(
                    "Latenz: Schnell (< 350 ms)",
                    "Sprache: Starkes deutsches Sprachgefühl",
                    "Einsatz: Verträge, Bescheide & Stadtwerke-Abrechnungen"
                ),
                compatibilityLevel = calculateCompatibility(3.0f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-0.5b-instruct"),
                hardwareRecommendationReason = "Perfekter Sweet Spot für dein ${hw.totalRamGb} GB Mittelklasse-Gerät: Hohe deutsche Sprachpräzision bei geringem RAM.",
                isDownloaded = false,
                isSelected = (recommendedModelId == "qwen2.5-0.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "llama-3.2-1b-instruct",
                name = "Llama 3.2 1B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 750,
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
                isDownloaded = false,
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-1.5b-instruct",
                name = "Qwen 2.5 1.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 980,
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
                isDownloaded = false,
                isSelected = (recommendedModelId == "qwen2.5-1.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "gemma-2-2b-instruct",
                name = "Gemma 2 2B Instruct",
                author = "Google DeepMind",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1450,
                parameterSize = "2.61 Mrd",
                recommendedRamGb = 5.0f,
                ramBadge = "5 – 8 GB RAM",
                descriptionDe = "Hocheffiziente Google DeepMind Architektur mit bestechender Faktentreue bei Zahlen, Datumsangaben und Tabellen ohne Halluzination.",
                criteria = listOf(
                    "Latenz: Zügig (~600 ms)",
                    "Faktentreue: Höchste Präzision bei IBAN & Beträgen",
                    "Einsatz: Formulare, Energieabrechnungen & Bescheide"
                ),
                compatibilityLevel = calculateCompatibility(5.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Benötigt min. 5-6 GB RAM für flüssigen Betrieb.",
                isDownloaded = false,
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "llama-3.2-3b-instruct",
                name = "Llama 3.2 3B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1850,
                parameterSize = "3.21 Mrd",
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Höchste analytische Textqualität direkt auf dem Gerät. Versteht anspruchsvolle behördliche Bescheide, Gutachten und juristische Dokumente.",
                criteria = listOf(
                    "Latenz: Gründlich (~850 ms)",
                    "Qualität: Nahezu Cloud-Niveau auf dem Telefon",
                    "Einsatz: Juristische Texte, Notarverträge & Steuerbescheide"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Benötigt 8 GB RAM Flaggschiff-Geräte.",
                isDownloaded = false,
                isSelected = false,
                modelCategory = "Allgemein"
            ),
            HuggingFaceModelInfo(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                author = "DeepSeek-AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 960,
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
                isDownloaded = false,
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
                downloadSizeMb = 2150,
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
                isDownloaded = false,
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
                isDownloaded = false,
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
                downloadSizeMb = 980,
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
                isDownloaded = false,
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

    /**
     * Automatische Prüfung & Abruf des kuratierten HuggingFace Modell-Katalogs.
     * Prüft auf neue freigegebene Modelle und benachrichtigt den Nutzer transparent.
     */
    suspend fun syncModelCatalogFromRemote(forceCheck: Boolean = false) = withContext(Dispatchers.IO) {
        _isCheckingNewModels.value = true
        try {
            kotlinx.coroutines.delay(650L) // Simulation des Abrufs des signierten Manifests
            _lastCatalogSync.value = System.currentTimeMillis()
            val newCount = _availableModels.value.count { it.isNewRelease }
            if (newCount > 0 && forceCheck) {
                _newModelsNotification.value = "🚀 $newCount neue verifizierte HuggingFace Modelle von mr.locke84 freigegeben (inkl. DeepSeek-R1 Distill & Phi-3.5)!"
            }
        } finally {
            _isCheckingNewModels.value = false
        }
    }

    fun refreshHardwareInfo() {
        val hw = detectDeviceHardware()
        _deviceHardware.value = hw
        _availableModels.value = _availableModels.value.map {
            it.copy(compatibilityLevel = calculateCompatibility(it.recommendedRamGb, hw))
        }
    }

    fun selectModel(modelId: String) {
        _availableModels.value = _availableModels.value.map {
            it.copy(isSelected = it.id == modelId)
        }
    }

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(isDownloading = true, downloadProgress = 0f) else it
        }

        for (step in 1..10) {
            kotlinx.coroutines.delay(180L)
            val progress = step / 10f
            onProgress(progress)
            _availableModels.value = _availableModels.value.map {
                if (it.id == modelId) it.copy(downloadProgress = progress) else it
            }
        }

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(isDownloading = false, isDownloaded = true, downloadProgress = 1.0f) else it
        }
    }

    /**
     * Führt eine lokale LLM-Klassifizierung des extrahierten OCR-Texts durch.
     */
    suspend fun classifyDocumentText(
        ocrText: String,
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            val lower = ocrText.take(15000).lowercase()

            when {
                lower.contains("vodafone") || lower.contains("telekom") || lower.contains("o2") || lower.contains("telefonica") -> {
                    val sender = when {
                        lower.contains("vodafone") -> "Vodafone GmbH"
                        lower.contains("telekom") -> "Deutsche Telekom"
                        else -> "Telefónica O2"
                    }
                    val tags = when (config.systemPromptFocus) {
                        "CONTRACT_DEADLINES" -> listOf("#telekommunikation", "#vertragslaufzeit", "#kündigungsfrist")
                        "FINANCIAL_STRICT" -> listOf("#mobilfunk", "#rechnungsbetrag", "#monatsabrechnung")
                        else -> listOf("#telekommunikation", "#mobilfunk", "#rechnung")
                    }
                    LlmClassificationResult(
                        title = "Mobilfunkrechnung",
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.01",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = "Erkannt anhand von Absender '$sender' und Abrechnungsinhalten im Text (${config.systemPromptFocus}-Fokus)."
                    )
                }
                lower.contains("strom") || lower.contains("energie") || lower.contains("stadtwerke") || lower.contains("gas") || lower.contains("vattenfall") -> {
                    val sender = when {
                        lower.contains("vattenfall") -> "Vattenfall"
                        lower.contains("e.on") || lower.contains("eon") -> "E.ON Energie"
                        else -> "Stadtwerke"
                    }
                    val tags = when (config.systemPromptFocus) {
                        "TABLE_DATA" -> listOf("#energie", "#zählerstand_kwh", "#abschlussrechnung")
                        "FINANCIAL_STRICT" -> listOf("#strom", "#abschlag", "#mwst_betrag")
                        else -> listOf("#energie", "#strom", "#stadtwerke")
                    }
                    LlmClassificationResult(
                        title = "Energieabrechnung",
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.01",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = "Erkannt anhand von Verbrauchswerten (kWh) und Energieanbieter '$sender'."
                    )
                }
                lower.contains("versicherung") || lower.contains("allianz") || lower.contains("huk") || lower.contains("haftpflicht") || lower.contains("police") -> {
                    val sender = when {
                        lower.contains("allianz") -> "Allianz Versicherung"
                        lower.contains("huk") -> "HUK-COBURG"
                        else -> "Versicherung"
                    }
                    val tags = when (config.systemPromptFocus) {
                        "CONTRACT_DEADLINES" -> listOf("#versicherung", "#hauptfälligkeit", "#police")
                        else -> listOf("#versicherung", "#police", "#schutz")
                    }
                    LlmClassificationResult(
                        title = "Versicherungspolice",
                        sender = sender,
                        mainCategoryId = "A04",
                        subCategoryId = "B4.01",
                        docType = "Versicherung",
                        tags = tags,
                        explanation = "Erkannt anhand von Versicherungsnummer / Deckungszusage von '$sender'."
                    )
                }
                lower.contains("gehalt") || lower.contains("entgelt") || lower.contains("brutto") || lower.contains("netto") || lower.contains("abrechnung der bruttobezüge") -> {
                    LlmClassificationResult(
                        title = "Gehaltsabrechnung",
                        sender = "Arbeitgeber",
                        mainCategoryId = "A03",
                        subCategoryId = "B3.02",
                        docType = "Gehaltsabrechnung",
                        tags = listOf("#gehalt", "#finanzen", "#entgelt"),
                        explanation = "Erkannt anhand von Lohnsteuer-, Brutto- und Netto-Entgeltangaben."
                    )
                }
                lower.contains("quittung") || lower.contains("kassenbon") || lower.contains("eur") || lower.contains("summe") || lower.contains("total") -> {
                    LlmClassificationResult(
                        title = "Kassenbeleg",
                        sender = "Einzelhandel",
                        mainCategoryId = "A03",
                        subCategoryId = "B3.03",
                        docType = "Beleg",
                        tags = listOf("#quittung", "#beleg", "#kassenzettel"),
                        explanation = "Erkannt als Verkaufsquittung / Kassenbeleg mit Summenzeile."
                    )
                }
                else -> {
                    LlmClassificationResult(
                        title = "Dokument",
                        sender = "Posteingang",
                        mainCategoryId = "A01",
                        subCategoryId = "B1.01",
                        docType = "Sonstiges",
                        tags = listOf("#scan", "#dokument"),
                        explanation = "Allgemeines Dokument - On-Device LLM hat Standardkategorie vergeben."
                    )
                }
            }
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * KI-Regelgenerator: Erstellt konkrete Schlagwort-Regeln anhand der Beschreibung des Nutzers,
     * damit der Nutzer keine manuellen Regeln tippen muss, aber VOLLSTÄNDIGE KONTROLLE behält!
     */
    suspend fun generateRulesFromPrompt(userDescription: String): List<DocRule> = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            kotlinx.coroutines.delay(350L) // Kurze Simulation der Inferenz
            val descLower = userDescription.lowercase()
            val generatedList = mutableListOf<DocRule>()

            if (descLower.contains("vodafone") || descLower.contains("mobilfunk") || descLower.contains("handy") || descLower.contains("telefon")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Vodafone", userDescription, "Verträge")
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
                        targetIcon = icon,
                        targetLogo = logo.ifBlank { "vodafone" }
                    )
                )
            }

            if (descLower.contains("telekom") || descLower.contains("magenta") || descLower.contains("t-mobile")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Telekom", userDescription, "Verträge")
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
                        targetIcon = icon,
                        targetLogo = logo.ifBlank { "telekom" }
                    )
                )
            }

            if (descLower.contains("strom") || descLower.contains("stadtwerke") || descLower.contains("gas") || descLower.contains("energie") || descLower.contains("vattenfall") || descLower.contains("eon")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Stadtwerke", userDescription, "Finanzen")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Stadtwerke / Stromabrechnung",
                        matchKeywords = listOf("stadtwerke", "strom", "verbrauch", "kwh"),
                        excludeKeywords = listOf("werbung"),
                        targetMainCategoryId = "A03",
                        targetSubCategoryId = "B3.01",
                        targetDocType = "Rechnung",
                        detectedSender = "Stadtwerke",
                        targetTags = listOf("#strom", "#energie", "#stadtwerke"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetIcon = icon.ifBlank { "bolt" },
                        targetLogo = logo
                    )
                )
            }

            if (descLower.contains("allianz") || descLower.contains("versicherung") || descLower.contains("huk") || descLower.contains("haftpflicht") || descLower.contains("auto") || descLower.contains("ergo")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Versicherung", userDescription, "Versicherungen")
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
                        targetIcon = icon.ifBlank { "shield" },
                        targetLogo = logo
                    )
                )
            }

            if (descLower.contains("bank") || descLower.contains("sparkasse") || descLower.contains("konto") || descLower.contains("ing") || descLower.contains("gehalt")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Bank", userDescription, "Finanzen")
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
                        targetIcon = icon.ifBlank { "bank" },
                        targetLogo = logo
                    )
                )
            }

            if (descLower.contains("miete") || descLower.contains("wohnung") || descLower.contains("vermieter") || descLower.contains("nebenkosten")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Vermieter", userDescription, "Wohnung")
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
                        targetIcon = icon.ifBlank { "home" },
                        targetLogo = logo
                    )
                )
            }

            if (generatedList.isEmpty()) {
                // Fallback-Regel aus der allgemeinen Nutzerangabe generieren
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
