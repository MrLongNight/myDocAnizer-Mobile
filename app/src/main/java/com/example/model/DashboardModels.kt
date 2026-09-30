package com.example.model

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Individueller Zeitraum-Scope für einzelne Dashboard Elemente
 */
enum class ElementPeriodScope(val label: String, val shortLabel: String, val description: String) {
    ALL("Gesamter Zeitraum", "Gesamt", "Alle erfassten Dokumente & Belege"),
    YEAR("Dieses Kalenderjahr", "Jahr", "Seit dem 1. Januar dieses Jahres"),
    QUARTER("Dieses Quartal", "Quartal", "Aktuelles Quartal (3 Monate)"),
    MONTH("Dieser Monat", "Monat", "Seit dem 1. des aktuellen Monats"),
    LAST_30_DAYS("Letzte 30 Tage", "30 Tage", "Rollierender 30-Tage Zeitraum"),
    LAST_7_DAYS("Letzte 7 Tage", "7 Tage", "Rollierender 7-Tage Zeitraum");

    fun filterDocuments(docs: List<DocumentEntity>, now: Long = System.currentTimeMillis()): List<DocumentEntity> {
        val cal = Calendar.getInstance()
        return when (this) {
            ALL -> docs
            YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                docs.filter { it.createdAt >= start }
            }
            QUARTER -> {
                val currentMonth = cal.get(Calendar.MONTH)
                val quarterStartMonth = (currentMonth / 3) * 3
                cal.set(Calendar.MONTH, quarterStartMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                docs.filter { it.createdAt >= start }
            }
            MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                docs.filter { it.createdAt >= start }
            }
            LAST_30_DAYS -> {
                val start = now - (30L * 24 * 60 * 60 * 1000)
                docs.filter { it.createdAt >= start }
            }
            LAST_7_DAYS -> {
                val start = now - (7L * 24 * 60 * 60 * 1000)
                docs.filter { it.createdAt >= start }
            }
        }
    }
}

enum class DashboardCategory(val title: String, val iconName: String, val description: String) {
    DOCUMENTS("Dokumente & Ablage", "folder", "Dokumente erfassen, filtern und durchsuchen"),
    FINANCE("Finanzen & Kasse", "account_balance_wallet", "Ausgaben, Bargeld und Belege im Blick"),
    DEADLINES("Fristen & Termine", "event", "Kündigungs- und Vertragsfristen überwachen"),
    SECURITY("Sicherheit & Tresor", "shield", "Tresor-Status, Verschlüsselung und Backups"),
    ACTIONS("Schnellaktionen & Notizen", "bolt", "Direktzugriffe und persönliche Notizen"),
    CUSTOM("Eigene Elemente", "dashboard_customize", "Vom Nutzer selbst erstellte individuelle Dashboard Elemente")
}

enum class CustomDashboardWidgetType(val label: String, val description: String, val defaultIcon: String) {
    FILTER_DOCUMENTS("Ordner- / Kategorie-Filter", "Zeigt Dokumente aus einem bestimmten Ordner oder einer Kategorie", "folder_open"),
    TAG_FILTER("Tag- / Schlagwort-Filter", "Zeigt Dokumente mit einem bestimmten Tag (z.B. #garantie, #steuer)", "label"),
    STAT_COUNTER("Zähler & KPI-Kachel", "Zählt Dokumente oder summiert Beträge für eine bestimmte Bedingung", "analytics"),
    NOTE_MEMO("Persönlicher Merkzettel", "Freies Textfeld für wichtige Notizen, Notizen direkt editierbar", "edit_note"),
    CHECKLIST("Checkliste / To-Do Liste", "Abhaktbare Aufgabenliste für Monatsabschluss oder Ablage", "checklist"),
    BUDGET_GOAL("Budget- & Sparziel-Wächter", "Monatliches Ausgabenlimit oder Sparziel mit Fortschrittsbalken", "savings")
}

@Immutable
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isDone: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("isDone", isDone)
    }

    companion object {
        fun fromJson(json: JSONObject): ChecklistItem {
            return ChecklistItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                isDone = json.optBoolean("isDone", false)
            )
        }
    }
}

/**
 * Individuelles (vom Nutzer erstelltes) Dashboard Element
 */
@Immutable
data class CustomDashboardWidget(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val type: CustomDashboardWidgetType = CustomDashboardWidgetType.NOTE_MEMO,
    val iconName: String = "category",
    val colorSkin: String = "BLUE", // BLUE, EMERALD, AMBER, ROSE, PURPLE, SLATE
    val periodScope: ElementPeriodScope = ElementPeriodScope.ALL, // Element-spezifischer Zeitraum!
    val targetCategory: String = "",
    val targetTag: String = "",
    val noteText: String = "",
    val checklistItems: List<ChecklistItem> = emptyList(),
    val targetAmount: Double = 0.0,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("type", type.name)
        put("iconName", iconName)
        put("colorSkin", colorSkin)
        put("periodScope", periodScope.name)
        put("targetCategory", targetCategory)
        put("targetTag", targetTag)
        put("noteText", noteText)
        val itemsArray = JSONArray()
        checklistItems.forEach { itemsArray.put(it.toJson()) }
        put("checklistItems", itemsArray)
        put("targetAmount", targetAmount)
        put("isEnabled", isEnabled)
        put("orderIndex", orderIndex)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): CustomDashboardWidget {
            val items = mutableListOf<ChecklistItem>()
            val itemsArray = json.optJSONArray("checklistItems")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.optJSONObject(i)
                    if (itemObj != null) {
                        items.add(ChecklistItem.fromJson(itemObj))
                    }
                }
            }

            val typeName = json.optString("type", CustomDashboardWidgetType.NOTE_MEMO.name)
            val type = try {
                CustomDashboardWidgetType.valueOf(typeName)
            } catch (e: Exception) {
                CustomDashboardWidgetType.NOTE_MEMO
            }

            val scopeName = json.optString("periodScope", ElementPeriodScope.ALL.name)
            val scope = try {
                ElementPeriodScope.valueOf(scopeName)
            } catch (e: Exception) {
                ElementPeriodScope.ALL
            }

            return CustomDashboardWidget(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Eigenes Element"),
                subtitle = json.optString("subtitle", ""),
                type = type,
                iconName = json.optString("iconName", "category"),
                colorSkin = json.optString("colorSkin", "BLUE"),
                periodScope = scope,
                targetCategory = json.optString("targetCategory", ""),
                targetTag = json.optString("targetTag", ""),
                noteText = json.optString("noteText", ""),
                checklistItems = items,
                targetAmount = json.optDouble("targetAmount", 0.0),
                isEnabled = json.optBoolean("isEnabled", true),
                orderIndex = json.optInt("orderIndex", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Vorlagen-Definition für fertige Dashboard Elemente
 */
data class DashboardTemplateInfo(
    val id: String,
    val category: DashboardCategory,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconName: String,
    val badge: String,
    val defaultScope: ElementPeriodScope = ElementPeriodScope.ALL,
    val isDefaultActive: Boolean
)

val STANDARD_DASHBOARD_TEMPLATES = listOf(
    // DOKUMENTE & ABLAGE
    DashboardTemplateInfo(
        id = "STANDARD_KPI",
        category = DashboardCategory.DOCUMENTS,
        title = "KPI-Zusammenfassung",
        subtitle = "Dokumente, Fristen & Betragssumme",
        description = "Kompakte Kennzahlen-Kacheln mit Schnellübersicht über den gesamten Tresor.",
        iconName = "analytics",
        badge = "Standard",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_RECENT_DOCS",
        category = DashboardCategory.DOCUMENTS,
        title = "Kürzlich hinzugefügt",
        subtitle = "Die neuesten Dokumente & Belege",
        description = "Zeigt die zuletzt gescannten oder importierten Dokumente mit Status & Betrag.",
        iconName = "history",
        badge = "Basis",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = true
    ),
    DashboardTemplateInfo(
        id = "STANDARD_QUICK_SCAN",
        category = DashboardCategory.DOCUMENTS,
        title = "Beleg Quick-Scan",
        subtitle = "Kamera & Import Schnellstart",
        description = "Schnellzugriff auf Kamera-Scan und Beleg-Import mit 1 Klick.",
        iconName = "photo_camera",
        badge = "Schnellzugriff",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_FOLDER_SHORTCUTS",
        category = DashboardCategory.DOCUMENTS,
        title = "Ordner-Schnellzugriff",
        subtitle = "Direktsprung in Hauptkategorien",
        description = "Direkte Links zu den am häufigsten genutzten Ordnern im Dokumenten Tresor.",
        iconName = "folder_special",
        badge = "Navigation",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_INBOX_DOCS",
        category = DashboardCategory.DOCUMENTS,
        title = "Posteingang & Unzugeordnet",
        subtitle = "Dokumente ohne Kategorie",
        description = "Erinnert an Belege und Scans, die noch einsortiert oder überprüft werden müssen.",
        iconName = "move_to_inbox",
        badge = "Inbox",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_CUSTOM_FIELDS",
        category = DashboardCategory.DOCUMENTS,
        title = "Zusatzfelder & Metadaten",
        subtitle = "Filter nach individuellen Feldern",
        description = "Schnellfilter nach benutzerdefinierten Feldern und KI-Metadaten.",
        iconName = "category",
        badge = "Metadaten",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),

    // FINANZEN & KASSE
    DashboardTemplateInfo(
        id = "STANDARD_FINANCE_CHART",
        category = DashboardCategory.FINANCE,
        title = "Finanz- & Ausgaben-Verlauf",
        subtitle = "Monatsbalken & Top-Kategorien",
        description = "Interaktives Balkendiagramm der monatlichen Ausgaben und Top-Kostenfaktoren.",
        iconName = "bar_chart",
        badge = "Analyse",
        defaultScope = ElementPeriodScope.YEAR,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_CASH_TRACKER",
        category = DashboardCategory.FINANCE,
        title = "Bargeld-Tracker",
        subtitle = "Barausgaben sofort erfassen",
        description = "Schnelleintrag für Barkäufe mit Schnellwahl-Buttons (-5€, -10€, -20€, -50€).",
        iconName = "account_balance_wallet",
        badge = "Kasse",
        defaultScope = ElementPeriodScope.MONTH,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_RECONCILIATION",
        category = DashboardCategory.FINANCE,
        title = "Ausgaben- & Beleg-Check",
        subtitle = "Selbstkontrolle: Bargeld & Konto",
        description = "Gleicht Bank-Abhebungen mit Bar-Belegen ab, um Lücken in den Ausgaben zu erkennen.",
        iconName = "sync_alt",
        badge = "Selbstkontrolle",
        defaultScope = ElementPeriodScope.MONTH,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_CATEGORY_PIE",
        category = DashboardCategory.FINANCE,
        title = "Kategorien-Verteilung",
        subtitle = "Prozentualer Ausgabenmix",
        description = "Farbige Verteilungsleiste aller Dokumentenkategorien und Ordneranteile.",
        iconName = "pie_chart",
        badge = "Statistik",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_BUDGET_WATCH",
        category = DashboardCategory.FINANCE,
        title = "Monats-Budget-Wächter",
        subtitle = "Ausgabenlimit mit Ampelanzeige",
        description = "Überwacht das monatliche Budget und warnt rechtzeitig bei Überschreitung.",
        iconName = "savings",
        badge = "Sparziel",
        defaultScope = ElementPeriodScope.MONTH,
        isDefaultActive = false
    ),

    // FRISTEN & TERMINE
    DashboardTemplateInfo(
        id = "STANDARD_DEADLINES_RADAR",
        category = DashboardCategory.DEADLINES,
        title = "Fristen- & Kündigungs-Radar",
        subtitle = "Verträge mit Dringlichkeits-Farbampel",
        description = "Überwacht Kündigungstermine und erinnert rechtzeitig mit 1-Klick Kalender-Export.",
        iconName = "notifications_active",
        badge = "Fristen",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),
    DashboardTemplateInfo(
        id = "STANDARD_DEADLINES_MONTH",
        category = DashboardCategory.DEADLINES,
        title = "Termine der nächsten 30 Tage",
        subtitle = "Kompakte Fristen-Checkliste",
        description = "Chronologische Vorschau aller Fälligkeiten und Termine im laufenden Monat.",
        iconName = "event",
        badge = "Kalender",
        defaultScope = ElementPeriodScope.LAST_30_DAYS,
        isDefaultActive = false
    ),

    // SICHERHEIT & TRESOR
    DashboardTemplateInfo(
        id = "STANDARD_SECURITY_SCORE",
        category = DashboardCategory.SECURITY,
        title = "3-2-1 Datensicherheits-Score",
        subtitle = "Tresor-Schutz & Backup-Status",
        description = "Zeigt den Sicherheitsgrad der lokalen AES-256 Verschlüsselung und Backup-Sync.",
        iconName = "shield",
        badge = "Tresor",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    ),

    // SCHNELLAKTIONEN & NOTIZEN
    DashboardTemplateInfo(
        id = "STANDARD_QUICK_ACTIONS",
        category = DashboardCategory.ACTIONS,
        title = "Schnellaktionen-Leiste",
        subtitle = "Scannen, Importieren & Suchen",
        description = "Schnellzugriffs-Buttons für die Kernfunktionen von myDocAnizer.",
        iconName = "bolt",
        badge = "Basis",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = true
    ),
    DashboardTemplateInfo(
        id = "STANDARD_QUICK_NOTE",
        category = DashboardCategory.ACTIONS,
        title = "Schnellnotiz / Merkzettel",
        subtitle = "Flüchtige Notizen & Memos",
        description = "Direkt auf dem Dashboard beschreibbare Notizkarte für spontane Gedanken.",
        iconName = "edit_note",
        badge = "Notizen",
        defaultScope = ElementPeriodScope.ALL,
        isDefaultActive = false
    )
)
