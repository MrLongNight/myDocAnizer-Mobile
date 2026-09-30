package com.example.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["isDeleted", "createdAt"]),
        Index(value = ["docType"]),
        Index(value = ["mainCategory"]),
        Index(value = ["cancellationDeadline"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sender: String,
    val fileName: String,
    val filePath: String,
    val mainCategory: String, // z.B. "Stromversorger"
    val subCategory: String,  // z.B. "Rechnungen"
    val mainCategoryId: String = "", // z.B. "A01"
    val subCategoryId: String = "",  // z.B. "B1.01"
    val docType: String = "", // Optionaler Doc-Typ (ehemals Doc-Group), z.B. "Fixkosten & Verträge"
    val tags: String = "", // Kommagetrennt (z.B. "#strom, #fixkosten")
    val ocrText: String = "",
    val colorMode: String = "BW", // "BW" oder "COLOR"
    val pageCount: Int = 1,
    val fileSizeFormatted: String = "120 KB",
    val isEncrypted: Boolean = true,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // Papierkorb / Mülleimer (Wiederherstellung & automatische Löschung)
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    // Verträge, Laufzeiten & Kalender-Fristen
    val contractEndDate: Long? = null,          // z.B. Mindestvertragslaufzeit
    val cancellationDeadline: Long? = null,     // z.B. Kündigungsfrist-Stichtag
    val reminderDaysBefore: Int = 14,           // Erinnerung x Tage im Voraus
    val hasCalendarReminder: Boolean = false,   // Erinnerung aktiviert
    val calendarEventType: String = "BOTH",     // "INTERNAL", "EXTERNAL", "BOTH"
    val reminderNotes: String = "",             // Notizen zur Frist / Tarif
    val amount: Double? = null,                 // Rechnungsbetrag oder monatliche Kosten
    val customIcon: String = "",                // Optionales benutzerdefiniertes Icon
    val companyLogo: String = ""                // Optionales Firmenlogo (z.B. "telekom", "allianz", "amazon")
)

@Fts4(contentEntity = DocumentEntity::class)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    val title: String,
    val sender: String,
    val ocrText: String,
    val tags: String
)

enum class CustomFieldType(val label: String) {
    TEXT("Freitext"),
    AMOUNT("Betrag (€)"),
    DATE("Datum / Frist"),
    SELECTION("Auswahlliste (Status)"),
    BOOLEAN("Ja / Nein (Schalter)")
}

enum class CustomFieldScope(val label: String) {
    GLOBAL("Global (Alle Ordner)"),
    FOLDER_SPECIFIC("Ordner-Spezifisch")
}

@Entity(
    tableName = "custom_fields",
    indices = [
        Index(value = ["scope"]),
        Index(value = ["targetMainCategory"])
    ]
)
data class CustomFieldEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val type: CustomFieldType = CustomFieldType.TEXT,
    val options: String = "", // Kommagetrennt für SELECTION type (z. B. "Bezahlt,Offen,In Bearbeitung")
    val scope: CustomFieldScope = CustomFieldScope.GLOBAL,
    val targetMainCategory: String = "",
    val targetSubCategory: String = "",
    val defaultValue: String = "",
    val isRequired: Boolean = false,
    val iconName: String = "label", // Icon für dieses Zusatzfeld
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_custom_field_values",
    primaryKeys = ["documentId", "customFieldId"],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["customFieldId"])
    ]
)
data class DocumentCustomFieldValueEntity(
    val documentId: Long,
    val customFieldId: String,
    val fieldValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CustomFieldWithValue(
    val field: CustomFieldEntity,
    val value: String
)

enum class CashTransactionType(val label: String) {
    WITHDRAWAL("Bargeld-Abhebung (Konto)"),
    EXPENSE("Bar-Ausgabe / Quittung"),
    INCOME("Bar-Einnahme"),
    PRIVATE_WITHDRAWAL("Privatentnahme (ohne Betriebsausgabe)")
}

@Entity(
    tableName = "cash_transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class CashTransactionEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val type: CashTransactionType = CashTransactionType.EXPENSE,
    val category: String = "Allgemein",
    val date: Long = System.currentTimeMillis(),
    val relatedDocumentId: Long? = null,
    val note: String = "",
    val isMatchedWithBankStatement: Boolean = false,
    val matchReconciliationMonth: String? = null
)

@Entity(
    tableName = "bank_statement_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["isCashWithdrawal"])
    ]
)
data class BankStatementEntryEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: Long,
    val bookingText: String,
    val purpose: String,
    val amount: Double,
    val isCashWithdrawal: Boolean = false,
    val matchedCashTransactionId: String? = null,
    val monthYear: String = ""
)

enum class CustomWidgetType(val label: String, val description: String) {
    FOLDER_MONITOR("Ordner- & Kategorie-Monitor", "Überwacht Belege und Gesamtsumme eines bestimmten Ordners"),
    CUSTOM_FIELD_MONITOR("Zusatzfeld-Filter", "Überwacht Dokumente mit einem bestimmten Zusatzfeld/Wert"),
    DEADLINE_MONITOR("Fristen- & Vertrags-Radar", "Zeigt bald ablaufende Fristen einer bestimmten Kategorie"),
    BUDGET_LIMIT("Monatsbudget & Ausgabenlimit", "Verfolgt ein Ausgabenlimit für einen Monat"),
    QUICK_SHORTCUT("Schnellzugriff & Aktion", "Eigener Button zum direkten Öffnen eines Ordners oder Vorlage"),
    STICKY_NOTE("Notiz / Merkzettel", "Persönlicher Notizblock für Dokumente & Erinnerungen")
}

@Immutable
@Entity(
    tableName = "custom_dashboard_widgets",
    indices = [
        Index(value = ["position"]),
        Index(value = ["widgetType"])
    ]
)
data class CustomDashboardWidgetEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val widgetType: CustomWidgetType = CustomWidgetType.FOLDER_MONITOR,
    val targetMainCategory: String = "",
    val targetSubCategory: String = "",
    val targetCustomFieldId: String = "",
    val targetCustomFieldValue: String = "",
    val numericLimit: Double = 0.0,
    val noteContent: String = "",
    val colorHex: Long = 0xFF2563EB,
    val iconName: String = "Folder",
    val isEnabled: Boolean = true,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedDocIds: List<Long> = emptyList()
)

@Immutable
data class TemplateItem(
    val id: String,
    val mainCategory: String, // Ebene 1: Hauptkategorie (z.B. "Stromversorger")
    val subCategory: String,  // Ebene 2: Unterordner (z.B. "Rechnungen")
    val mainCategoryId: String = "", // z.B. "A01"
    val subCategoryId: String = "",  // z.B. "B1.01"
    val defaultDocType: String = "", // Optionaler Doc-Typ (ehemals Doc-Group)
    val defaultSender: String = "",   // Optionaler Standard-Absender
    val namingPattern: String = "YYYY-MM-DD_[Sender]_[Title]", // Namensmuster
    val defaultTags: List<String> = emptyList(), // Vorbelegte Schlagwörter
    val defaultColorMode: String = "BW", // "BW" oder "COLOR"
    val iconName: String = "folder"
)

enum class AppViewLevel(val title: String, val subtitle: String, val badge: String) {
    STANDARD("Standard-Ansicht", "Einsteigerfreundlich – Automatische Optimierung", "🟢 Standard"),
    ADVANCED("Erweiterte Ansicht", "Mehr Kontrolle – Flexible Profile & Zeitpläne", "🟡 Erweitert"),
    EXPERT("Experten-Ansicht", "Volle Kontrolle – Inferenz, DB-Wartung & Netzwerk", "🔴 Experte")
}

@Immutable
data class DocTypeItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val colorHex: Long = 0xFF2563EB
)

data class FolderSubCategoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val prefixId: String = "",
    val iconName: String = "",
    val companyLogo: String = "",
    val customLogoUri: String = ""
)

data class FolderCategoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val prefixId: String = "",
    val iconName: String = "",
    val companyLogo: String = "",
    val customLogoUri: String = "",
    val subFolders: List<FolderSubCategoryItem> = emptyList()
)

val PRESET_FOLDERS = listOf(
    FolderCategoryItem(
        id = "f_wohnung",
        name = "Wohnung",
        prefixId = "A01",
        subFolders = listOf(
            FolderSubCategoryItem("sf_mietvertraege", "Mietverträge", "B1.01"),
            FolderSubCategoryItem("sf_nebenkosten", "Nebenkosten & Heizung", "B1.02"),
            FolderSubCategoryItem("sf_hausrat", "Hausrat & Inventar", "B1.03")
        )
    ),
    FolderCategoryItem(
        id = "f_vertraege",
        name = "Verträge",
        prefixId = "A02",
        subFolders = listOf(
            FolderSubCategoryItem("sf_mobilfunk", "Telekommunikation", "B2.01"),
            FolderSubCategoryItem("sf_strom", "Energie & Strom", "B2.02"),
            FolderSubCategoryItem("sf_abos", "Abos & Mitgliedschaften", "B2.03")
        )
    ),
    FolderCategoryItem(
        id = "f_finanzen",
        name = "Finanzen",
        prefixId = "A03",
        subFolders = listOf(
            FolderSubCategoryItem("sf_bank", "Kontoauszüge & Bank", "B3.01"),
            FolderSubCategoryItem("sf_gehalt", "Gehalt & Lohn", "B3.02"),
            FolderSubCategoryItem("sf_belege", "Belege & Quittungen", "B3.03"),
            FolderSubCategoryItem("sf_steuern", "Steuererklärung", "B3.04")
        )
    ),
    FolderCategoryItem(
        id = "f_gesundheit",
        name = "Gesundheit",
        prefixId = "A04",
        subFolders = listOf(
            FolderSubCategoryItem("sf_krankenkasse", "Krankenkasse", "B4.01"),
            FolderSubCategoryItem("sf_arzt", "Arztberichte & Rezepte", "B4.02"),
            FolderSubCategoryItem("sf_impfungen", "Impfpass & Befunde", "B4.03")
        )
    ),
    FolderCategoryItem(
        id = "f_kfz",
        name = "KFZ & Mobilität",
        prefixId = "A05",
        subFolders = listOf(
            FolderSubCategoryItem("sf_versicherung", "KFZ-Versicherung", "B5.01"),
            FolderSubCategoryItem("sf_werkstatt", "Werkstatt & TÜV", "B5.02"),
            FolderSubCategoryItem("sf_kauf", "Fahrzeugpapiere", "B5.03")
        )
    ),
    FolderCategoryItem(
        id = "f_behoerden",
        name = "Behörden & Amt",
        prefixId = "A06",
        subFolders = listOf(
            FolderSubCategoryItem("sf_finanzamt", "Finanzamt", "B6.01"),
            FolderSubCategoryItem("sf_buergeramt", "Bürgeramt & Meldebescheinigung", "B6.02"),
            FolderSubCategoryItem("sf_renten", "Rentenversicherung", "B6.03")
        )
    )
)

data class ImportTemplateItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val mainCategory: String,
    val subCategory: String,
    val mainCategoryId: String = "",
    val subCategoryId: String = "",
    val defaultDocType: String = "",
    val defaultSender: String = "",
    val defaultTags: List<String> = emptyList(),
    val targetFormat: String = "PDF", // "PDF" oder "IMAGE"
    val runOcrByDefault: Boolean = true,
    val colorMode: String = "COLOR", // "COLOR" oder "BW"
    val iconType: String = "receipt" // "contract", "receipt", "shield", "salary", "general"
)

val PRESET_IMPORT_TEMPLATES = listOf(
    ImportTemplateItem(
        id = "tmpl_recurring_mobile",
        name = "Mobilfunkvertrag / Rechnung",
        description = "Wiederkehrender monatlicher Mobilfunkbeleg",
        mainCategory = "Verträge",
        subCategory = "Telekommunikation",
        mainCategoryId = "A02",
        subCategoryId = "B2.01",
        defaultDocType = "laufender Vertrag",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "PDF",
        runOcrByDefault = true,
        colorMode = "COLOR",
        iconType = "contract"
    ),
    ImportTemplateItem(
        id = "tmpl_recurring_contract",
        name = "Laufender Vertrag (Allgemein)",
        description = "Wohnungs-, Miet- oder Dienstleistungsvertrag",
        mainCategory = "Wohnung",
        subCategory = "Mietverträge",
        mainCategoryId = "A01",
        subCategoryId = "B1.01",
        defaultDocType = "laufender Vertrag",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "PDF",
        runOcrByDefault = true,
        colorMode = "BW",
        iconType = "contract"
    ),
    ImportTemplateItem(
        id = "tmpl_recurring_salary",
        name = "Gehaltsabrechnung",
        description = "Monatlicher Entgeltnachweis / Lohnabrechnung",
        mainCategory = "Finanzen",
        subCategory = "Gehalt",
        mainCategoryId = "A03",
        subCategoryId = "B3.02",
        defaultDocType = "Gehaltsabrechnung",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "PDF",
        runOcrByDefault = true,
        colorMode = "BW",
        iconType = "salary"
    ),
    ImportTemplateItem(
        id = "tmpl_recurring_receipt",
        name = "Kassenbelege / Quittung (Bild)",
        description = "Foto-Beleg direkt als Bild im Original archivieren",
        mainCategory = "Finanzen",
        subCategory = "Belege & Quittungen",
        mainCategoryId = "A03",
        subCategoryId = "B3.03",
        defaultDocType = "Rechnung",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "IMAGE",
        runOcrByDefault = true,
        colorMode = "COLOR",
        iconType = "receipt"
    ),
    ImportTemplateItem(
        id = "tmpl_recurring_utility",
        name = "Strom & Energieabrechnung",
        description = "Stadtwerke- & Energieversorger-Rechnungen",
        mainCategory = "Finanzen",
        subCategory = "Stadtwerke",
        mainCategoryId = "A03",
        subCategoryId = "B3.01",
        defaultDocType = "Rechnung",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "PDF",
        runOcrByDefault = true,
        colorMode = "COLOR",
        iconType = "receipt"
    ),
    ImportTemplateItem(
        id = "tmpl_recurring_insurance",
        name = "Versicherungspolice / Bescheid",
        description = "Kfz-, Haftpflicht- oder Krankenversicherungsunterlagen",
        mainCategory = "Versicherungen",
        subCategory = "Kfz-Versicherung",
        mainCategoryId = "A04",
        subCategoryId = "B4.01",
        defaultDocType = "Versicherung",
        defaultSender = "",
        defaultTags = emptyList(),
        targetFormat = "PDF",
        runOcrByDefault = true,
        colorMode = "COLOR",
        iconType = "shield"
    )
)

data class ScannerSettings(
    val scanMode: String = "AUTO", // "AUTO", "MANUAL"
    val triggerMode: String = "AUTO_DETECT", // "AUTO_DETECT", "COUNTDOWN", "MANUAL"
    val countdownSeconds: Int = 3, // 2, 3, 5 Sekunden
    val isBulkMode: Boolean = false, // Direkt speichern & bereit fürs nächste Dokument
    val enableHardwareButtons: Boolean = true, // Lautstärketasten als Auslöser (nur bei manuellem Modus wirksam)
    val defaultTemplateId: String = "", // Bevorzugte Vorlage für Auto-Scan
    val tripodAutoScan: Boolean = true, // Automatische Dokumentenerkennung & Auto-Scan
    val preScanDelaySeconds: Int = 2, // Verzögerung vor Scan nach Erkennung (1 - 60s)
    val postScanDelaySeconds: Int = 3, // Wartezeit nach Scan bis Erkennung wieder aktiv (1 - 60s)
    val autoScanDelayMs: Long = 2000L, // Backwards compatibility ms
    val toolsInfoOnly: Boolean = true, // Scan-Tools (Wasserwaage, Schärfe, etc.) nur informativ (kein Blocker)
    val flashMode: String = "OFF", // "OFF", "FLASH", "TORCH"
    val sheetChangeSensitivity: String = "MEDIUM", // "LOW", "MEDIUM", "HIGH"
    val soundProfile: String = "SUCCESS_BEEP", // "CLICK", "SUCCESS_BEEP", "CHIME", "MUTE"
    val enableVibration: Boolean = false // Optionales haptisches Feedback, standardmäßig AUS
)

data class PendingRuleSuggestion(
    val bitmap: android.graphics.Bitmap,
    val ocrText: String,
    val suggestedRule: DocRule,
    val onSingleUseOnly: () -> Unit,
    val onSaveAsPermanentRule: (DocRule) -> Unit,
    val onDismiss: () -> Unit
)
data class StorageFolderNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val children: List<StorageFolderNode> = emptyList()
)

data class OptimizationTemplate(
    val id: String,
    val name: String,
    val description: String,
    val contrastBoost: Float = 1.8f,
    val brightnessOffset: Float = -50f,
    val sharpnessLevel: Float = 1.2f,
    val removeShadows: Boolean = true,
    val autoDeskew: Boolean = true,
    val cleanBackgroundWhite: Boolean = true
)

val PRESET_OPTIMIZATION_TEMPLATES = listOf(
    OptimizationTemplate(
        id = "opt_standard_text",
        name = "Standard-Textdokument & Rechnungen",
        description = "Klares Schriftbild, weißer Hintergrund, optimale Schärfe für Druck & OCR",
        contrastBoost = 1.8f,
        brightnessOffset = -50f,
        sharpnessLevel = 1.2f,
        removeShadows = true,
        autoDeskew = true,
        cleanBackgroundWhite = true
    ),
    OptimizationTemplate(
        id = "opt_receipt_thermal",
        name = "Kassenbeleg & Thermopapier",
        description = "Extremer Kontrast für verblasste Thermodrucke, Grauschleier-Entfernung",
        contrastBoost = 2.4f,
        brightnessOffset = -70f,
        sharpnessLevel = 1.5f,
        removeShadows = true,
        autoDeskew = true,
        cleanBackgroundWhite = true
    ),
    OptimizationTemplate(
        id = "opt_colored_magazine",
        name = "Farbige Broschüre & Zeitschriften",
        description = "Lebendige Farben, reduzierte Spiegelung, sanfter Text-Boost",
        contrastBoost = 1.3f,
        brightnessOffset = -15f,
        sharpnessLevel = 1.1f,
        removeShadows = true,
        autoDeskew = false,
        cleanBackgroundWhite = false
    ),
    OptimizationTemplate(
        id = "opt_handwritten_notes",
        name = "Handschrift & Notizen",
        description = "Feine Tintenlinien verstärken, Papierhintergrund aufhellen",
        contrastBoost = 2.0f,
        brightnessOffset = -40f,
        sharpnessLevel = 1.4f,
        removeShadows = true,
        autoDeskew = true,
        cleanBackgroundWhite = true
    ),
    OptimizationTemplate(
        id = "opt_id_card",
        name = "Ausweis, Karte & Zertifikat",
        description = "Gleichmäßige Belichtung ohne Schlagschatten, hohe Detailgenauigkeit",
        contrastBoost = 1.4f,
        brightnessOffset = -20f,
        sharpnessLevel = 1.3f,
        removeShadows = true,
        autoDeskew = true,
        cleanBackgroundWhite = false
    )
)

data class PdfSettings(
    val pageSize: String = "A4",
    val dpi: Int = 300,
    val compressionLevel: String = "Standard", // "Standard", "Hoch", "Verlustfrei"
    val defaultColorMode: String = "AUTO", // "AUTO", "BW", "COLOR"
    val useIdPrefixes: Boolean = true, // Ob Ordner-Nummerierung aktiv ist
    val folderPrefixStyle: String = "AKTENPLAN_STANDARD", // "AKTENPLAN_STANDARD", "NUMERIC_DOT", "BRACKETS", "COMPACT", "FILE_NUMBER", "DECIMAL", "NONE"
    val docNamingStyle: String = "DATE_SENDER_TITLE", // "DATE_SENDER_TITLE", "DATE_TITLE", "SENDER_DATE_TITLE", "ORDER_NUMBER_DATE_TITLE", "TITLE_DATE"
    val baseStorageLocation: String = "APP_STORAGE", // "APP_STORAGE", "DOCUMENTS", "EXTERNAL_STORAGE"
    val customStoragePath: String = "", // Optional benutzerdefinierter Basispfad
    val isOptimizationEnabled: Boolean = true, // Optionale Dokumenten-Optimierung
    val activeOptimizationTemplateId: String = "opt_standard_text", // Aktives Profil
    val autoDeskewEnabled: Boolean = true, // Automatische Begradigung
    val shadowRemovalEnabled: Boolean = true, // Schattenentfernung
    val backgroundCleaningEnabled: Boolean = true // Papierweiß-Glättung
)

data class DocNamingStyleOption(
    val id: String,
    val title: String,
    val example: String,
    val description: String
)

val DOC_NAMING_STYLE_OPTIONS: List<DocNamingStyleOption> = listOf(
    DocNamingStyleOption(
        id = "DATE_SENDER_TITLE",
        title = "Datum _ Absender _ Titel",
        example = "2024-09-22_Telekom_Rechnung.pdf",
        description = "Empfohlener Standard für chronologische Übersicht nach Absender."
    ),
    DocNamingStyleOption(
        id = "DATE_TITLE",
        title = "Datum _ Titel",
        example = "2024-09-22_Rechnung.pdf",
        description = "Kompakter Dateiname sortiert nach Erstellungsdatum."
    ),
    DocNamingStyleOption(
        id = "SENDER_DATE_TITLE",
        title = "Absender _ Datum _ Titel",
        example = "Telekom_2024-09-22_Rechnung.pdf",
        description = "Gruppiert Dateien im Dateimanager direkt nach Absendernamen."
    ),
    DocNamingStyleOption(
        id = "ORDER_NUMBER_DATE_TITLE",
        title = "Ordnungsnummer _ Datum _ Titel",
        example = "01_01_2024-09-22_Rechnung.pdf",
        description = "Enthält die Ordnungsnummer des Ordners direkt im Dateinamen."
    ),
    DocNamingStyleOption(
        id = "TITLE_DATE",
        title = "Titel _ Datum",
        example = "Rechnung_2024-09-22.pdf",
        description = "Klassische Dokumentenbezeichnung mit nachgestelltem Datum."
    )
)

data class PrefixStyleOption(
    val id: String,
    val title: String,
    val shortName: String,
    val example: String,
    val description: String
)

val PREFIX_STYLE_OPTIONS: List<PrefixStyleOption> = listOf(
    PrefixStyleOption(
        id = "AKTENPLAN_STANDARD",
        title = "Aktenplan-Standard (A01_ / B1.01-)",
        shortName = "A01_ / B1.01-",
        example = "A01_Versicherung / B1.01-Policen",
        description = "Klassische 2-stufige myDocAnizer Systematik nach DIN-Aktenplan."
    ),
    PrefixStyleOption(
        id = "NUMERIC_DOT",
        title = "Numerische Registratur (01. / 01.01-)",
        shortName = "01. / 01.01-",
        example = "01._Versicherung / 01.01.-Policen",
        description = "Klassische fortlaufende Ziffern-Registratur für Behörden & Kanzleien."
    ),
    PrefixStyleOption(
        id = "BRACKETS",
        title = "Eckige Klammern ([01] / [01.01])",
        shortName = "[01] / [01.01]",
        example = "[01] Versicherung / [01.01] Policen",
        description = "Hervorragend lesbar in mobilen Dateimanagern, Windows, macOS & NAS."
    ),
    PrefixStyleOption(
        id = "COMPACT",
        title = "Kompakt-Code (1_ / 1.1-)",
        shortName = "1_ / 1.1-",
        example = "1_Versicherung / 1.1-Policen",
        description = "Schlanke, kurze Pfadnamen ohne führende Nullstellen."
    ),
    PrefixStyleOption(
        id = "FILE_NUMBER",
        title = "Aktenzeichen (Az-01_ / Az-01.01-)",
        shortName = "Az-01_ / Az-01.01-",
        example = "Az-01_Versicherung / Az-01.01-Policen",
        description = "Offizielles Aktenzeichen-Format mit vorangestelltem 'Az-'."
    ),
    PrefixStyleOption(
        id = "DECIMAL",
        title = "Dezimal-Nummerierung (01_ / 01.01-)",
        shortName = "01_ / 01.01-",
        example = "01_Versicherung / 01.01-Policen",
        description = "Reine Dezimalstruktur zur einfachen Zahlensortierung."
    ),
    PrefixStyleOption(
        id = "NONE",
        title = "Deaktiviert (Nur Klartext ohne Nummern)",
        shortName = "Keine Nummern",
        example = "Versicherung / Policen",
        description = "Rein alphabetische Ordnernamen ohne vorangestellte Strukturcodes."
    )
)

data class UsbBackupDrive(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val locationNote: String, // z. B. "Zuhause (Schreibtisch)", "Ausgelagert bei Eltern (Brandschutz)"
    val lastSyncTimestamp: Long = 0L,
    val totalSyncedDocs: Int = 0
) {
    val lastBackupDate: String?
        get() = if (lastSyncTimestamp > 0L) {
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date(lastSyncTimestamp))
        } else null
}

data class CloudSyncConfig(
    val syncTarget: String = "OFFLINE_ONLY", // Standard: Nur lokaler Speicher
    val enableGoogleDrive: Boolean = false, // Standard: immer AUS (nur lokal)
    val enableWebDavNas: Boolean = false, // Standard: immer AUS
    val enableLocalVault: Boolean = true, // Standard: immer AN (Dokumente im lokalen App-Speicher)
    val enableManualUsbExport: Boolean = false, // Stufe 1: Regelmäßiges 1-Klick Backup auf USB-Stick/SD/PC
    val googleDriveAccount: String = "mr.locke84@gmail.com",
    val googleDriveFolder: String = "myDocAnizer_Vault",
    val googleDriveConnected: Boolean = false,
    val nasProtocol: String = "SMB", // "SMB" oder "WEBDAV"
    val nasShareName: String = "myDocAnizer_Backup",
    val nasConnected: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val syncTrigger: String = "AUTO_AFTER_SCAN", // "AUTO_AFTER_SCAN", "DAILY", "MANUAL"
    val syncWifiOnly: Boolean = true,
    val autoMirrorToBackupDir: Boolean = true, // Automatisches lokales Backup-Spiegeln bei jedem Scan
    val isAirGappedStrict: Boolean = false, // Keine Netzwerk-Kommunikation erlaubt
    val usbSyncWarningOnScan: Boolean = true // Automatischer Hinweis beim Scannen, wenn USB-Sync aussteht
)

/**
 * Dynamische Auswertung der Ausfallsicherheit und Datensouveränität (3-2-1 Regel)
 */
data class BackupSafetyScore(
    val scorePercent: Int,
    val label: String,
    val badgeText: String,
    val colorHex: Long,
    val description: String,
    val privacyRating: String, // Schutz vor fremdem Zugriff
    val lossRiskRating: String, // Schutz vor Verlust bei Handyschaden
    val lossRiskStatus: String, // "Sehr sicher", "Akzeptabel", "Kritisches Risiko"
    val tips: List<String>
)

fun calculateBackupSafetyScore(config: CloudSyncConfig): BackupSafetyScore {
    val hasDrive = config.enableGoogleDrive
    val hasNas = config.enableWebDavNas
    val hasUsb = config.enableManualUsbExport

    return when {
        // Fall 1: Hybrid 3-2-1 Gold-Standard (Cloud + NAS)
        hasDrive && hasNas -> BackupSafetyScore(
            scorePercent = 100,
            label = "3-2-1 Gold-Standard (Maximaler Schutz)",
            badgeText = "Vollständig Ausfallsicher (100%)",
            colorHex = 0xFF16A34A, // Grün
            description = "Hervorragend! Du erfüllst die empfohlene 3-2-1 Backup-Strategie: Mindestens 3 Kopien (Handy, Heim-NAS, Cloud) auf 2 unterschiedlichen Medien mit 1 räumlich getrennten Sicherung. Das Risiko für Datenverlust wird damit auf ein absolutes Minimum reduziert.",
            privacyRating = "100% (AES-256 Ende-zu-Ende verschlüsselt; weder Cloud-Anbieter noch Dritte können mitlesen)",
            lossRiskRating = "Minimal (Umfassender Schutz vor Handyverlust, Gerätedefekt, Diebstahl oder Elementarschäden)",
            lossRiskStatus = "Optimal geschützt",
            tips = listOf("Dein Setup ist optimal. Nach jedem Scan wird automatisch synchronisiert.")
        )

        // Fall 2: Cloud + Manuelles USB
        hasDrive && hasUsb -> BackupSafetyScore(
            scorePercent = 95,
            label = "Hervorragender Schutz (Cloud + USB)",
            badgeText = "Sehr hoher Schutz (95%)",
            colorHex = 0xFF16A34A,
            description = "Ausgezeichnet! Deine Dokumente sind automatisiert in der verschlüsselten Cloud und zusätzlich auf externem USB-Speicher gesichert.",
            privacyRating = "100% (Vollständig verschlüsselt)",
            lossRiskRating = "Sehr gering",
            lossRiskStatus = "Sehr sicher vor Geräteverlust",
            tips = listOf("Denke daran, den USB-Stick in regelmäßigen Abständen einzustecken.")
        )

        // Fall 3: NAS + Manuelles USB
        hasNas && hasUsb -> BackupSafetyScore(
            scorePercent = 90,
            label = "Hohe Datenhoheit (Heim-NAS + USB)",
            badgeText = "Hoher Schutz (90%)",
            colorHex = 0xFF0D9488,
            description = "Starke Lösung ohne Fremd-Cloud! Deine Dokumente sind auf deinem Heimserver und auf USB-Medien gesichert.",
            privacyRating = "100% (Bleibt in deiner privaten Infrastruktur)",
            lossRiskRating = "Sehr gering",
            lossRiskStatus = "Gut vor Geräteverlust geschützt",
            tips = listOf("Tipp: Lagere den USB-Stick an einem anderen physischen Ort als das NAS (räumliche Trennung).")
        )

        // Fall 4: Nur Google Drive
        hasDrive -> BackupSafetyScore(
            scorePercent = 80,
            label = "Hoher Schutz (Google Drive Cloud-Tresor)",
            badgeText = "Cloud-Schutz aktiv (80%)",
            colorHex = 0xFF2563EB, // Blau
            description = "Sehr gut! Bei Verlust, Defekt oder Neuanschaffung eines Handys sind alle Dokumente sofort auf einem neuen Gerät aus dem Cloud-Speicher wiederherstellbar.",
            privacyRating = "100% (Zero-Knowledge: Google sieht nur unlesbare AES-256 Dateien)",
            lossRiskRating = "Gering (Sicher vor Geräteverlust & Hardwareschaden)",
            lossRiskStatus = "Sehr gut vor Geräteverlust geschützt",
            tips = listOf("Für die 3-2-1 Perfektion: Aktiviere zusätzlich ein zweites Ziel (USB oder Heim-NAS).")
        )

        // Fall 5: Nur Heim-NAS
        hasNas -> BackupSafetyScore(
            scorePercent = 80,
            label = "Hohe Datenhoheit (Heim-NAS / Nextcloud)",
            badgeText = "Heimnetz-Tresor (80%)",
            colorHex = 0xFF0D9488, // Türkis / Teal
            description = "Sehr gut! Volle Datensouveränität in deinen eigenen vier Wänden. Unabhängig von Cloud-Anbietern.",
            privacyRating = "100% (Dokumente verbleiben in deinem Heimnetzwerk)",
            lossRiskRating = "Gering (Sicher vor Geräteverlust, solange dein NAS läuft)",
            lossRiskStatus = "Gut vor Geräteverlust geschützt",
            tips = listOf("Hinweis: Für zusätzlichen Schutz vor Elementarschäden empfiehlt sich ein extern gelagertes Backup.")
        )

        // Fall 6: Nur Manuelles USB-Stick Backup
        hasUsb -> BackupSafetyScore(
            scorePercent = 50,
            label = "Basis-Schutz (Manuelles USB-Backup)",
            badgeText = "Manuelles Backup (50%)",
            colorHex = 0xFFD97706, // Bernstein / Orange
            description = "Gut für Nutzer, die keine Cloud verwenden möchten. Deine Dokumente können per Knopfdruck auf einen USB-Stick exportiert werden. Neue Dokumente sind nach dem nächsten manuellen Export geschützt.",
            privacyRating = "100% (Kein Byte berührt jemals das Internet)",
            lossRiskRating = "Mittel (Abhängig von der Regelmäßigkeit der Sicherung)",
            lossRiskStatus = "Teilweise geschützt (Manuelle Durchführung erforderlich)",
            tips = listOf("Schließe regelmäßig einen USB-Stick an und klicke auf 'Sichern'.")
        )

        // Fall 7: Standard - Nur lokaler Speicher auf dem Smartphone
        else -> BackupSafetyScore(
            scorePercent = 25,
            label = "Erhöhtes Datenverlust-Risiko",
            badgeText = "Standard: Nur lokales Gerät (25%)",
            colorHex = 0xFFDC2626, // Rot
            description = "Standard-Einstellung: Deine Dokumente liegen ausschließlich im internen Speicher dieses Smartphones. Geht das Telefon verloren, wird gestohlen oder erleidet einen Defekt, sind die Daten ohne externes Backup nicht wiederherstellbar.",
            privacyRating = "100% (Kein Byte verlässt dieses Smartphone)",
            lossRiskRating = "Erhöht (Keine Zweitkopie oder räumliche Sicherung)",
            lossRiskStatus = "Risiko bei Geräteverlust",
            tips = listOf(
                "Wähle unten mindestens eine Stufe (USB-Export, Google Drive oder Heim-NAS), um deine Dokumente vor Verlust zu schützen."
            )
        )
    }
}

enum class StorageLocationType {
    USB_DRIVE,
    GOOGLE_DRIVE,
    WEBDAV_NAS,
    LOCAL_VAULT
}

data class StorageLocationSyncStatus(
    val id: String,
    val name: String,
    val type: StorageLocationType,
    val isConfigured: Boolean,
    val isSynced: Boolean,
    val pendingDocsCount: Int,
    val lastSyncFormatted: String?,
    val warningMessage: String?
)

data class OverallBackupSyncHealth(
    val hasWarnings: Boolean,
    val totalConfiguredLocations: Int,
    val unsyncedLocationsCount: Int,
    val warnings: List<String>,
    val locationStatuses: List<StorageLocationSyncStatus>
)

fun calculateBackupSyncHealth(
    totalDocumentsCount: Int,
    config: CloudSyncConfig,
    usbDrives: List<UsbBackupDrive>,
    lastSyncTimestamp: Long
): OverallBackupSyncHealth {
    val statuses = mutableListOf<StorageLocationSyncStatus>()
    val warnings = mutableListOf<String>()

    // 1. USB-STICKS
    if (config.enableManualUsbExport || usbDrives.isNotEmpty()) {
        if (usbDrives.isEmpty()) {
            val warn = "USB-Sicherung aktiviert, aber noch kein USB-Stick registriert."
            warnings.add(warn)
            statuses.add(
                StorageLocationSyncStatus(
                    id = "usb_none",
                    name = "USB-Sicherung",
                    type = StorageLocationType.USB_DRIVE,
                    isConfigured = true,
                    isSynced = false,
                    pendingDocsCount = totalDocumentsCount,
                    lastSyncFormatted = null,
                    warningMessage = warn
                )
            )
        } else {
            for (drive in usbDrives) {
                val isSynced: Boolean
                val pending: Int
                val warn: String?

                if (totalDocumentsCount == 0) {
                    isSynced = true
                    pending = 0
                    warn = null
                } else if (drive.lastSyncTimestamp == 0L) {
                    isSynced = false
                    pending = totalDocumentsCount
                    warn = "Stick „${drive.name}“ wurde noch nie synchronisiert ($pending Dokumente ausstehend)."
                } else if (totalDocumentsCount > drive.totalSyncedDocs) {
                    val diff = totalDocumentsCount - drive.totalSyncedDocs
                    isSynced = false
                    pending = diff
                    warn = "Stick „${drive.name}“ hinkt hinterher ($diff neue Dokumente ungesichert)."
                } else {
                    isSynced = true
                    pending = 0
                    warn = null
                }

                if (warn != null) {
                    warnings.add(warn)
                }

                statuses.add(
                    StorageLocationSyncStatus(
                        id = drive.id,
                        name = "USB: ${drive.name}",
                        type = StorageLocationType.USB_DRIVE,
                        isConfigured = true,
                        isSynced = isSynced,
                        pendingDocsCount = pending,
                        lastSyncFormatted = drive.lastBackupDate,
                        warningMessage = warn
                    )
                )
            }
        }
    }

    // 2. GOOGLE DRIVE
    if (config.enableGoogleDrive) {
        val isSynced: Boolean
        val pending: Int
        val warn: String?

        if (!config.googleDriveConnected) {
            isSynced = false
            pending = totalDocumentsCount
            warn = "Google Drive: Verbindung zum Cloud-Konto noch nicht hergestellt."
        } else if (totalDocumentsCount > 0 && lastSyncTimestamp == 0L) {
            isSynced = false
            pending = totalDocumentsCount
            warn = "Google Drive: Erstsynchronisierung steht noch aus ($pending Dokumente)."
        } else {
            isSynced = true
            pending = 0
            warn = null
        }

        if (warn != null) warnings.add(warn)
        statuses.add(
            StorageLocationSyncStatus(
                id = "google_drive",
                name = "Google Drive Cloud-Tresor",
                type = StorageLocationType.GOOGLE_DRIVE,
                isConfigured = true,
                isSynced = isSynced,
                pendingDocsCount = pending,
                lastSyncFormatted = if (lastSyncTimestamp > 0L) java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date(lastSyncTimestamp)) else null,
                warningMessage = warn
            )
        )
    }

    // 3. LOKALES HEIM-NAS / WEBDAV
    if (config.enableWebDavNas) {
        val isSynced: Boolean
        val pending: Int
        val warn: String?

        if (config.webDavUrl.isBlank()) {
            isSynced = false
            pending = totalDocumentsCount
            warn = "Heim-NAS: Server-Adresse (WebDAV-URL) fehlt."
        } else if (!config.nasConnected) {
            isSynced = false
            pending = totalDocumentsCount
            warn = "Heim-NAS: Verbindung zum Server noch nicht bestätigt."
        } else {
            isSynced = true
            pending = 0
            warn = null
        }

        if (warn != null) warnings.add(warn)
        statuses.add(
            StorageLocationSyncStatus(
                id = "webdav_nas",
                name = "Lokales Heim-NAS",
                type = StorageLocationType.WEBDAV_NAS,
                isConfigured = true,
                isSynced = isSynced,
                pendingDocsCount = pending,
                lastSyncFormatted = if (lastSyncTimestamp > 0L) java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date(lastSyncTimestamp)) else null,
                warningMessage = warn
            )
        )
    }

    // Falls gar kein externes Backup eingerichtet ist:
    if (statuses.isEmpty()) {
        val warn = "Kein Backup eingerichtet! Dokumente liegen nur ungesichert auf diesem Smartphone."
        warnings.add(warn)
        statuses.add(
            StorageLocationSyncStatus(
                id = "local_only",
                name = "Lokaler Smartphone-Speicher",
                type = StorageLocationType.LOCAL_VAULT,
                isConfigured = true,
                isSynced = false,
                pendingDocsCount = totalDocumentsCount,
                lastSyncFormatted = null,
                warningMessage = warn
            )
        )
    }

    val unsyncedCount = statuses.count { !it.isSynced }

    return OverallBackupSyncHealth(
        hasWarnings = warnings.isNotEmpty(),
        totalConfiguredLocations = statuses.size,
        unsyncedLocationsCount = unsyncedCount,
        warnings = warnings,
        locationStatuses = statuses
    )
}

/**
 * Detaillierte Erklärungen für Info-Popups
 */
data class StorageOptionDetail(
    val title: String,
    val shortSubtitle: String,
    val howItWorks: String,
    val securityExplanation: String,
    val whatHappensOnLoss: String,
    val recommendation: String
)

val STORAGE_INFO_DETAILS = mapOf(
    "MANUAL_USB" to StorageOptionDetail(
        title = "Stufe 1: Manuelles 1-Klick-Backup (USB-Stick / SD / PC)",
        shortSubtitle = "Vollständig offline – Manuelle verschlüsselte Sicherungsdatei",
        howItWorks = "Du verbindest dein Smartphone mit einem USB-C-Stick, einer SD-Karte oder per USB-Kabel mit dem PC. Mit einem Klick erzeugt myDocAnizer ein AES-256-verschlüsseltes Archiv (.enc) aller deiner Dokumente.",
        securityExplanation = "100% Privatsphäre. Kein Byte wird jemals über das Internet übertragen. Die Sicherungsdatei ist mit deinem Master-Passwort geschützt.",
        whatHappensOnLoss = "Wenn dein Smartphone defekt ist: Du nimmst ein neues Gerät, installierst die App, schließt den USB-Stick an und stellst alle Dokumente mit deinem Master-Passwort wieder her.",
        recommendation = "Perfekt für Nutzer, die keine Cloud und kein NAS möchten. Wichtig: Du musst selbst diszipliniert daran denken, regelmäßig den Stick anzuschließen!"
    ),
    "GOOGLE_DRIVE" to StorageOptionDetail(
        title = "Stufe 2: Google Drive Cloud-Tresor",
        shortSubtitle = "Vollautomatischer Offsite-Schutz mit AES-256-Verschlüsselung",
        howItWorks = "Die App verschlüsselt jedes Dokument VOR dem Upload direkt auf dem Smartphone mit deinem geheimen Master-Passwort (AES-256-GCM). Google Drive empfängt ausschließlich verschlüsselte Dateien (.enc), niemals Klartext-PDFs.",
        securityExplanation = "Selbst wenn Google-Mitarbeiter oder Unbefugte Zugriff auf das Google Drive erhalten, sehen sie nur unlesbares Datenrauschen. Ohne dein persönliches Master-Passwort ist eine Entschlüsselung mathematisch unmöglich.",
        whatHappensOnLoss = "Wenn dein Smartphone gestohlen wird oder kaputtgeht: Installiere myDocAnizer-Mobile auf deinem neuen Smartphone, verbinde dein Google-Konto und gib dein Master-Passwort ein. Alle Dokumente und Aktenpläne sind in wenigen Sekunden vollständig wieder da.",
        recommendation = "Sehr empfohlen für jeden, der ohne eigenen Server ein zuverlässiges, automatisches Backup haben möchte."
    ),
    "WEBDAV_NAS" to StorageOptionDetail(
        title = "Stufe 3: Lokales Heim-NAS / Nextcloud (WebDAV)",
        shortSubtitle = "Automatische Synchronisation in dein privates Heimnetzwerk",
        howItWorks = "Die App sendet verschlüsselte Archive (.enc) direkt über dein Heim-WLAN an deinen eigenen Speicher (z. B. Synology DiskStation, QNAP, TrueNAS, Nextcloud oder FRITZ!Box NAS).",
        securityExplanation = "Absolute Datensouveränität: Deine Dokumente verlassen niemals dein privates Netzwerk über fremde Cloud-Server. Zusätzlich sind sie per AES-256 geschützt.",
        whatHappensOnLoss = "Bei Verlust des Smartphones greifst du über dein neues Gerät oder deinen PC direkt auf dein Heim-NAS zu und kannst das .enc Archiv in die App importieren.",
        recommendation = "Ideal für Nutzer mit eigener IT-Infrastruktur oder Nextcloud, die keine US-Cloud-Dienste nutzen möchten."
    ),
    "P2P_SYNC" to StorageOptionDetail(
        title = "Dual-Sync mittels myDocAnizer-Mobile auf zusätzlichen Gerät",
        shortSubtitle = "Zwei Endgeräte dauerhaft direkt & verschlüsselt verbinden",
        howItWorks = "Die App synchronisiert Dokumente, Kategorien und Metadaten direkt von Gerät zu Gerät über das lokale WLAN ohne Zwischenserver mit bidirektionalem Delta-Sync.",
        securityExplanation = "100% Ende-zu-Ende verschlüsselt mit AES-256-GCM und gegenseitiger PIN-Autorisierung.",
        whatHappensOnLoss = "Das Zweitgerät besitzt jederzeit einen vollständigen, synchronisierten Datenbestand.",
        recommendation = "Ideal für Nutzer mit Smartphone und zusätzlichem Gerät wie Tablet oder Zweithandy."
    ),
    "DESKTOP_LIGHT" to StorageOptionDetail(
        title = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor",
        shortSubtitle = "Lokaler Tresor-Spiegel & Scan-to-Android am Computer",
        howItWorks = "Die myDocAnizer-Desktop App spiegelt den Dokumentenbestand verschlüsselt auf dem PC und ermöglicht das direkte Scannen via Desktop-Scanner mit automatischer Übertragung zur KI-Verarbeitung sowie lokalen Druck.",
        securityExplanation = "Verbindung erfolgt sicher per QR-Code Scan und Ende-zu-Ende Verschlüsselung im lokalen Netzwerk.",
        whatHappensOnLoss = "Der Desktop-PC verfügt über einen verschlüsselten Vollspiegel deiner Dokumente und SQLite-Datenbank.",
        recommendation = "Perfekt für komfortables Arbeiten am großen Bildschirm mit Dokumenten-Druck und Desktop-Scannern."
    ),
    "LOCAL_VAULT" to StorageOptionDetail(
        title = "Basis: Lokaler App-Speicher auf dem Smartphone",
        shortSubtitle = "Immer aktiv – Schneller Offline-Zugriff auf diesem Gerät",
        howItWorks = "Dokumente werden im isolierten Speicher der App auf dem Telefon gespeichert. Dadurch kannst du jederzeit ohne Internetverbindung blitzschnell auf deine Dokumente zugreifen.",
        securityExplanation = "Höchste Sicherheit vor fremdem Zugriff im Internet. Durch Android-App-Sandbox und Biometrie vor fremdem Zugriff geschützt.",
        whatHappensOnLoss = "ACHTUNG: Wenn dein Telefon verloren geht, ins Wasser fällt oder gestohlen wird und KEIN externes Backup aktiv ist, sind ALLE Dokumente UNWIEDERBRINGLICH VERLOREN!",
        recommendation = "Der lokale Speicher ist unverzichtbar für die tägliche Nutzung, darf aber NIEMALS die einzige Kopie deiner wichtigen Unterlagen sein."
    ),
    "OFFLINE_PRIVACY" to StorageOptionDetail(
        title = "100% Offline-Standard & Datenschutz",
        shortSubtitle = "Dokumente bleiben strikt auf deinem Gerät",
        howItWorks = "Scans, OCR-Texterkennung, lokale Volltextsuche und KI-Kategorisierung laufen zu 100% autark und lokal auf dem Smartphone ab.",
        securityExplanation = "Es gibt keine Hintergrundverbindungen zu fremden Servern. Netzwerkzugriffe erfolgen ausschließlich dann, wenn du Google Drive oder dein eigenes Heim-NAS konfigurierst.",
        whatHappensOnLoss = "Sofern kein externes Backup (Stufe 1, 2 oder 3) eingerichtet ist, führt ein Geräteverlust zum Verlust der Daten.",
        recommendation = "Ideal für maximale Privatsphäre. Kombiniere dies am besten mit Stufe 1 (USB-Archiv) oder einem verschlüsselten Tresor."
    ),
    "RULE_321" to StorageOptionDetail(
        title = "Die bewährte 3-2-1 Backup-Regel",
        shortSubtitle = "Der weltweite Standard für Datensicherheit",
        howItWorks = "3 Kopien der Daten auf mindestens 2 unterschiedlichen Speichermedien (z. B. Smartphone + Heim-NAS) mit mindestens 1 Offsite-Kopie an einem anderen Ort (z. B. verschlüsseltes Google Drive).",
        securityExplanation = "Alle Kopien außerhalb des Smartphones werden mit AES-256-GCM verschlüsselt, sodass niemand mitlesen kann.",
        whatHappensOnLoss = "Selbst bei Brand, Hochwasser zu Hause, Handyverlust und Serverdefekt gleichzeitig sind deine Dokumente an mindestens einem Ort sicher gerettet.",
        recommendation = "Die beste und sorgenfreiste Variante für alle wichtigen Verträge, Steuerunterlagen und Zeugnisse."
    )
)

data class ColorSkinItem(
    val id: String,
    val name: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val containerHex: Long,
    val description: String
)

val PRESET_COLOR_SKINS = listOf(
    ColorSkinItem("BLUE", "Oceanic Blau", 0xFF2563EB, 0xFF0284C7, 0xFFDBEAFE, "Klassisches DMS-Businessblau"),
    ColorSkinItem("EMERALD", "Smaragd Grün", 0xFF059669, 0xFF0D9488, 0xFFD1FAE5, "Frisches, klares Produktivitäts-Grün"),
    ColorSkinItem("SLATE", "Nordic Slate", 0xFF475569, 0xFF334155, 0xFFE2E8F0, "Modernes minimalistisches Anthrazit"),
    ColorSkinItem("AMBER", "Warmes Bernstein", 0xFFD97706, 0xFFB45309, 0xFFFEF3C7, "Warme, elegante Gold- & Kupfertöne"),
    ColorSkinItem("PURPLE", "Königs-Violett", 0xFF7C3AED, 0xFF6D28D9, 0xFFEDE9FE, "Edles, fokussiertes Violett"),
    ColorSkinItem("ROSE", "Bordeaux & Rubin", 0xFFBE123C, 0xFF9F1239, 0xFFFFE4E6, "Kräftiges, markantes Rubinrot")
)

/**
 * Deterministisches Schlagwort-Regelwerk vor KI.
 * Wenn ein Dokument diese Schlagwörter im OCR-Text enthält, greift sofort
 * die deterministische Regel ohne Unsicherheit oder Wartezeit.
 */
@Immutable
data class DocRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String, // z.B. "Vodafone Mobilfunk Rechnung"
    val matchKeywords: List<String>, // z.B. ["vodafone", "rechnungsnummer", "kundennummer"]
    val excludeKeywords: List<String> = emptyList(), // z.B. ["mahnung", "kündigung"]
    val targetMainCategoryId: String = "", // z.B. "A02"
    val targetSubCategoryId: String = "",  // z.B. "B2.01"
    val targetDocType: String = "Rechnung", // z.B. "Rechnung"
    val detectedSender: String = "", // z.B. "Vodafone GmbH"
    val targetTags: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val confidenceScore: Float = 1.0f,
    val isAiGenerated: Boolean = false, // Ob die Regel von der lokalen KI vorgeschlagen wurde
    val targetCustomFields: Map<String, String> = emptyMap(), // Map von CustomField-ID zu Wert
    val targetIcon: String = "", // Zugeordnetes Dokument-Icon
    val targetLogo: String = ""  // Zugeordnetes Firmenlogo
)

/**
 * Status eines Elements im Stapel-Puffer / Post-Processing
 */
enum class BatchItemStatus {
    PENDING_PROCESS, // Neu erfasst, wartet auf Start der Stapelverarbeitung
    WAITING_OCR,     // OCR & Analyse laufen
    RULE_MATCHED,    // 100% deterministischer Treffer durch Schlagwort-Regel
    AI_SUGGESTED,    // Lokales LLM hat Vorschlag generiert
    MANUAL_REVIEW,   // Wartet auf Freigabe / Korrektur durch Nutzer
    APPROVED,        // Vom Nutzer geprüft & freigegeben
    ERROR            // Fehler bei OCR/Verarbeitung
}

/**
 * Element im Stapel-Puffer (Inbox).
 * Ermöglicht schnelles Durchscannen eines Stapels und anschließendes Prüfen/Freigeben.
 */
@Immutable
data class BatchDocumentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tempImagePaths: List<String>,
    val ocrText: String = "",
    val status: BatchItemStatus = BatchItemStatus.PENDING_PROCESS,
    val suggestedTitle: String = "",
    val suggestedSender: String = "",
    val suggestedMainCategoryId: String = "",
    val suggestedSubCategoryId: String = "",
    val suggestedDocType: String = "",
    val suggestedTags: List<String> = emptyList(),
    val matchedRuleName: String? = null,
    val aiReasoning: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Echte Hardware-Telemetrie des Android-Geräts zur KI-Kompatibilitätsprüfung
 */
data class DeviceHardwareInfo(
    val totalRamGb: Float = 6.0f,
    val availableRamGb: Float = 3.5f,
    val usedRamGb: Float = 2.5f,
    val ramUsagePercent: Int = 42,
    val cpuCores: Int = 8,
    val isLowRamDevice: Boolean = false
) {
    val freeRamGb: Float get() = availableRamGb

    val performanceTier: String
        get() = when {
            totalRamGb >= 8.0f -> "High-End (Flaggschiff)"
            totalRamGb >= 5.0f -> "Ausgewogen (Mittelklasse)"
            else -> "Kompakt (Basisklasse)"
        }

    fun getCompatibility(recommendedRamGb: Float): ModelCompatibilityLevel {
        return when {
            totalRamGb >= recommendedRamGb + 1.0f -> ModelCompatibilityLevel.OPTIMAL
            totalRamGb >= recommendedRamGb - 0.2f -> ModelCompatibilityLevel.LIMITED
            else -> ModelCompatibilityLevel.NOT_RECOMMENDED
        }
    }
}

enum class ModelCompatibilityLevel {
    OPTIMAL,          // Vollständig unterstützt, flüssige On-Device Inferenz
    LIMITED,          // Läuft, erzeugt jedoch hohe RAM-Auslastung
    NOT_RECOMMENDED   // Nicht empfohlen: Gerät hat weniger RAM als für das Modell nötig
}

data class SystemPromptFocusOption(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val promptInstruction: String
)

val SYSTEM_PROMPT_FOCUS_OPTIONS = listOf(
    SystemPromptFocusOption(
        id = "STANDARD",
        title = "Standard (Allgemein)",
        icon = "receipt_long",
        description = "Ausgewogene Erkennung für Rechnungen, Verträge, Briefe & Quittungen.",
        promptInstruction = "Analysiere das Dokument präzise hinsichtlich Absender, Titel, Kategorie und Schlagwörter."
    ),
    SystemPromptFocusOption(
        id = "FINANCIAL_STRICT",
        title = "Finanzen & Buchhaltung",
        icon = "account_balance",
        description = "Strenger Fokus auf Rechnungsbeträge, IBAN, USt-ID, Rechnungsnummer & Zahlungsziel.",
        promptInstruction = "Fokus auf buchhalterische Daten: Extrahiere exakte Brutto/Netto-Beträge, IBAN, Rechnungsnummer und Fälligkeit."
    ),
    SystemPromptFocusOption(
        id = "CONTRACT_DEADLINES",
        title = "Verträge & Kündigungsfristen",
        icon = "history_edu",
        description = "Spezialisiert auf Vertragslaufzeiten, Kündigungstermine, Policennummern & Fristen.",
        promptInstruction = "Fokus auf Vertragsklauseln: Identifiziere Mindestlaufzeiten, Kündigungsfristen, Kunden-/Vertragsnummern und Fristen."
    ),
    SystemPromptFocusOption(
        id = "TABLE_DATA",
        title = "Tabellen & Einzelposten",
        icon = "table_chart",
        description = "Strukturierte Extraktion tabellarischer Positionen (z.B. Energieverbrauchszähler, Artikellisten).",
        promptInstruction = "Fokus auf tabellarische Auflistung: Erfasse Einzelpositionen, Zählerstände, Einheiten (kWh) und Zwischensummen."
    ),
    SystemPromptFocusOption(
        id = "COMPACT_SUMMARY",
        title = "Kompakte Zusammenfassung",
        icon = "summarize",
        description = "Extrem kurze Kernfakten (1-2 Sätze) und treffsichere 3-5 Schlagwörter für schnelle Ablage.",
        promptInstruction = "Fasse den Kerninhalt in maximal 2 prägnanten Sätzen zusammen und vergebe 3 treffende Tags."
    )
)

data class LlmInferenceConfig(
    val temperature: Float = 0.1f, // 0.0 (deterministisch/exakt) bis 0.8 (kreativ)
    val maxTokens: Int = 512, // 128, 256, 512, 1024
    val topP: Float = 0.9f,
    val systemPromptFocus: String = "STANDARD", // ID aus SYSTEM_PROMPT_FOCUS_OPTIONS
    val threadCount: Int = 4, // 1, 2, 4, 8 CPU-Threads
    val contextLength: Int = 2048, // 1024, 2048, 4096
    val onlyShowCompatibleModels: Boolean = true // Hardware-Filter
) {
    val cpuThreads: Int get() = threadCount
}


