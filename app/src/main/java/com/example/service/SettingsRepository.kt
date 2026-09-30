package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppViewLevel
import com.example.model.ChecklistItem
import com.example.model.CustomDashboardWidget
import com.example.model.DocTypeItem
import com.example.model.ElementPeriodScope
import com.example.model.ImportTemplateItem
import com.example.model.PRESET_IMPORT_TEMPLATES
import com.example.model.PdfSettings
import com.example.model.STANDARD_DASHBOARD_TEMPLATES
import com.example.model.ScannerSettings
import com.example.model.TemplateItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mydocanizer_prefs", Context.MODE_PRIVATE)

    // Ein-&Ausgaben Erfassung (ehemals Haushaltsbuch)
    private val _enableIncomeExpenseTracking = MutableStateFlow(
        prefs.getBoolean("enable_income_expense_tracking", prefs.getBoolean("enable_household_book", false))
    )
    val enableIncomeExpenseTracking: StateFlow<Boolean> = _enableIncomeExpenseTracking.asStateFlow()
    val enableHouseholdBook: StateFlow<Boolean> = _enableIncomeExpenseTracking.asStateFlow()

    fun setEnableIncomeExpenseTracking(enabled: Boolean) {
        prefs.edit()
            .putBoolean("enable_income_expense_tracking", enabled)
            .putBoolean("enable_household_book", enabled)
            .apply()
        _enableIncomeExpenseTracking.value = enabled
    }

    fun setEnableHouseholdBook(enabled: Boolean) = setEnableIncomeExpenseTracking(enabled)

    // Modul 1: Bargeld Tracker (Geldbörse & Barkasse)
    private val _enableCashTracker = MutableStateFlow(prefs.getBoolean("enable_cash_tracker", false))
    val enableCashTracker: StateFlow<Boolean> = _enableCashTracker.asStateFlow()

    fun setEnableCashTracker(enabled: Boolean) {
        prefs.edit().putBoolean("enable_cash_tracker", enabled).apply()
        _enableCashTracker.value = enabled
    }

    // Modul 2: Ein-/Ausgaben & Beleg Erfassung (Kassenbons, Rechnungen, OCR)
    private val _enableReceiptExpenses = MutableStateFlow(prefs.getBoolean("enable_receipt_expenses", false))
    val enableReceiptExpenses: StateFlow<Boolean> = _enableReceiptExpenses.asStateFlow()

    fun setEnableReceiptExpenses(enabled: Boolean) {
        prefs.edit().putBoolean("enable_receipt_expenses", enabled).apply()
        _enableReceiptExpenses.value = enabled
    }

    // Modul 3: Kontoauszüge importieren (PDF/CSV-Bankumsätze)
    private val _enableBankStatementImport = MutableStateFlow(prefs.getBoolean("enable_bank_statement_import", false))
    val enableBankStatementImport: StateFlow<Boolean> = _enableBankStatementImport.asStateFlow()

    fun setEnableBankStatementImport(enabled: Boolean) {
        prefs.edit().putBoolean("enable_bank_statement_import", enabled).apply()
        _enableBankStatementImport.value = enabled
    }

    // Automatischer monatlicher Datenabgleich (Bargeld- & Beleg-Check / Selbstkontrolle)
    // Gleicht Bargeld-Abhebungen vom Konto mit manuellen Bar-Ausgaben und Belegen ab
    private val _enableMonthlyReconciliation = MutableStateFlow(prefs.getBoolean("enable_monthly_reconciliation", false))
    val enableMonthlyReconciliation: StateFlow<Boolean> = _enableMonthlyReconciliation.asStateFlow()

    fun setEnableMonthlyReconciliation(enabled: Boolean) {
        prefs.edit().putBoolean("enable_monthly_reconciliation", enabled).apply()
        _enableMonthlyReconciliation.value = enabled
    }

    private val _notifyReconciliationDiscrepancies = MutableStateFlow(prefs.getBoolean("notify_reconciliation_discrepancies", true))
    val notifyReconciliationDiscrepancies: StateFlow<Boolean> = _notifyReconciliationDiscrepancies.asStateFlow()

    fun setNotifyReconciliationDiscrepancies(enabled: Boolean) {
        prefs.edit().putBoolean("notify_reconciliation_discrepancies", enabled).apply()
        _notifyReconciliationDiscrepancies.value = enabled
    }

    // Dashboard anpassbare Widgets (Standardmäßig nur Grundfunktionen ohne Aktivierungsaufwand)
    private val _showBelegQuickScanWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_quick_scan", false))
    val showBelegQuickScanWidget: StateFlow<Boolean> = _showBelegQuickScanWidget.asStateFlow()
    fun setShowBelegQuickScanWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_quick_scan", show).apply()
        _showBelegQuickScanWidget.value = show
    }

    private val _showCashTrackerWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_cash_tracker", false))
    val showCashTrackerWidget: StateFlow<Boolean> = _showCashTrackerWidget.asStateFlow()
    fun setShowCashTrackerWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_cash_tracker", show).apply()
        _showCashTrackerWidget.value = show
    }

    private val _showKpiWidgets = MutableStateFlow(prefs.getBoolean("dashboard_show_kpi", false))
    val showKpiWidgets: StateFlow<Boolean> = _showKpiWidgets.asStateFlow()
    fun setShowKpiWidgets(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_kpi", show).apply()
        _showKpiWidgets.value = show
    }

    private val _showDeadlinesWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_deadlines", false))
    val showDeadlinesWidget: StateFlow<Boolean> = _showDeadlinesWidget.asStateFlow()
    fun setShowDeadlinesWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_deadlines", show).apply()
        _showDeadlinesWidget.value = show
    }

    private val _showFinanceWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_finance", false))
    val showFinanceWidget: StateFlow<Boolean> = _showFinanceWidget.asStateFlow()
    fun setShowFinanceWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_finance", show).apply()
        _showFinanceWidget.value = show
    }

    private val _showReconciliationWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_reconciliation", false))
    val showReconciliationWidget: StateFlow<Boolean> = _showReconciliationWidget.asStateFlow()
    fun setShowReconciliationWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_reconciliation", show).apply()
        _showReconciliationWidget.value = show
    }

    private val _showCategoryDistributionWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_category_distribution", false))
    val showCategoryDistributionWidget: StateFlow<Boolean> = _showCategoryDistributionWidget.asStateFlow()
    fun setShowCategoryDistributionWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_category_distribution", show).apply()
        _showCategoryDistributionWidget.value = show
    }

    private val _showSecurityScoreWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_security_score", false))
    val showSecurityScoreWidget: StateFlow<Boolean> = _showSecurityScoreWidget.asStateFlow()
    fun setShowSecurityScoreWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_security_score", show).apply()
        _showSecurityScoreWidget.value = show
    }

    private val _showCustomFieldsWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_custom_fields", false))
    val showCustomFieldsWidget: StateFlow<Boolean> = _showCustomFieldsWidget.asStateFlow()
    fun setShowCustomFieldsWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_custom_fields", show).apply()
        _showCustomFieldsWidget.value = show
    }

    // Zusätzliche Standard-Vorlagen Sichtbarkeiten
    private val _showRecentDocsWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_recent_docs", true))
    val showRecentDocsWidget: StateFlow<Boolean> = _showRecentDocsWidget.asStateFlow()
    fun setShowRecentDocsWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_recent_docs", show).apply()
        _showRecentDocsWidget.value = show
    }

    private val _showFolderShortcutsWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_folder_shortcuts", false))
    val showFolderShortcutsWidget: StateFlow<Boolean> = _showFolderShortcutsWidget.asStateFlow()
    fun setShowFolderShortcutsWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_folder_shortcuts", show).apply()
        _showFolderShortcutsWidget.value = show
    }

    private val _showInboxDocsWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_inbox_docs", false))
    val showInboxDocsWidget: StateFlow<Boolean> = _showInboxDocsWidget.asStateFlow()
    fun setShowInboxDocsWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_inbox_docs", show).apply()
        _showInboxDocsWidget.value = show
    }

    private val _showBudgetWatchWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_budget_watch", false))
    val showBudgetWatchWidget: StateFlow<Boolean> = _showBudgetWatchWidget.asStateFlow()
    fun setShowBudgetWatchWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_budget_watch", show).apply()
        _showBudgetWatchWidget.value = show
    }

    private val _showQuickActionsWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_quick_actions", true))
    val showQuickActionsWidget: StateFlow<Boolean> = _showQuickActionsWidget.asStateFlow()
    fun setShowQuickActionsWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_quick_actions", show).apply()
        _showQuickActionsWidget.value = show
    }

    private val _showQuickNoteWidget = MutableStateFlow(prefs.getBoolean("dashboard_show_quick_note", false))
    val showQuickNoteWidget: StateFlow<Boolean> = _showQuickNoteWidget.asStateFlow()
    fun setShowQuickNoteWidget(show: Boolean) {
        prefs.edit().putBoolean("dashboard_show_quick_note", show).apply()
        _showQuickNoteWidget.value = show
    }

    private val _quickNoteText = MutableStateFlow(prefs.getString("dashboard_quick_note_text", "") ?: "")
    val quickNoteText: StateFlow<String> = _quickNoteText.asStateFlow()
    fun setQuickNoteText(text: String) {
        prefs.edit().putString("dashboard_quick_note_text", text).apply()
        _quickNoteText.value = text
    }

    // Individuelle, vom Nutzer erstellte Dashboard-Elemente (Custom Dashboard Widgets)
    private val _customDashboardWidgets = MutableStateFlow<List<CustomDashboardWidget>>(loadCustomDashboardWidgets())
    val customDashboardWidgets: StateFlow<List<CustomDashboardWidget>> = _customDashboardWidgets.asStateFlow()

    private fun loadCustomDashboardWidgets(): List<CustomDashboardWidget> {
        val jsonStr = prefs.getString("custom_dashboard_widgets_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CustomDashboardWidget>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(CustomDashboardWidget.fromJson(obj))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomDashboardWidgets(list: List<CustomDashboardWidget>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString("custom_dashboard_widgets_json", array.toString()).apply()
        _customDashboardWidgets.value = list
    }

    fun addCustomWidget(widget: CustomDashboardWidget) {
        val current = _customDashboardWidgets.value.toMutableList()
        current.add(widget)
        saveCustomDashboardWidgets(current)

        // Automatisch an die Widget-Reihenfolge anhängen
        val order = _dashboardWidgetOrder.value.toMutableList()
        if (!order.contains(widget.id)) {
            order.add(widget.id)
            setDashboardWidgetOrder(order)
        }
    }

    fun updateCustomWidget(widget: CustomDashboardWidget) {
        val current = _customDashboardWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widget.id }
        if (index != -1) {
            current[index] = widget
            saveCustomDashboardWidgets(current)
        }
    }

    fun deleteCustomWidget(id: String) {
        val current = _customDashboardWidgets.value.filter { it.id != id }
        saveCustomDashboardWidgets(current)

        val order = _dashboardWidgetOrder.value.filter { it != id }
        setDashboardWidgetOrder(order)
    }

    fun toggleCustomWidget(id: String, enabled: Boolean) {
        val current = _customDashboardWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(isEnabled = enabled)
            saveCustomDashboardWidgets(current)
        }
    }

    fun updateCustomWidgetChecklist(id: String, items: List<ChecklistItem>) {
        val current = _customDashboardWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(checklistItems = items)
            saveCustomDashboardWidgets(current)
        }
    }

    fun updateCustomWidgetNote(id: String, text: String) {
        val current = _customDashboardWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(noteText = text)
            saveCustomDashboardWidgets(current)
        }
    }

    // Dashboard-Element Reihenfolge & Sortierung (Drag & Drop / Reordering)
    private val DEFAULT_WIDGET_ORDER = listOf(
        "STANDARD_QUICK_ACTIONS",
        "STANDARD_RECENT_DOCS",
        "STANDARD_KPI",
        "STANDARD_DEADLINES_RADAR",
        "STANDARD_FINANCE_CHART",
        "STANDARD_SECURITY_SCORE",
        "STANDARD_QUICK_SCAN",
        "STANDARD_CASH_TRACKER",
        "STANDARD_RECONCILIATION",
        "STANDARD_CATEGORY_PIE",
        "STANDARD_CUSTOM_FIELDS",
        "STANDARD_FOLDER_SHORTCUTS",
        "STANDARD_INBOX_DOCS",
        "STANDARD_BUDGET_WATCH",
        "STANDARD_QUICK_NOTE"
    )

    private val _dashboardWidgetOrder = MutableStateFlow<List<String>>(loadDashboardWidgetOrder())
    val dashboardWidgetOrder: StateFlow<List<String>> = _dashboardWidgetOrder.asStateFlow()

    private fun loadDashboardWidgetOrder(): List<String> {
        val str = prefs.getString("dashboard_widget_order_list", null)
        if (str.isNullOrBlank()) {
            return DEFAULT_WIDGET_ORDER
        }
        val savedList = str.split(",").filter { it.isNotBlank() }
        // Ensure all default ones exist
        val combined = savedList.toMutableList()
        DEFAULT_WIDGET_ORDER.forEach { defId ->
            if (!combined.contains(defId)) combined.add(defId)
        }
        return combined
    }

    fun setDashboardWidgetOrder(order: List<String>) {
        prefs.edit().putString("dashboard_widget_order_list", order.joinToString(",")).apply()
        _dashboardWidgetOrder.value = order
    }

    fun moveWidgetUp(widgetId: String) {
        val current = _dashboardWidgetOrder.value.toMutableList()
        val idx = current.indexOf(widgetId)
        if (idx > 0) {
            val temp = current[idx]
            current[idx] = current[idx - 1]
            current[idx - 1] = temp
            setDashboardWidgetOrder(current)
        }
    }

    fun moveWidgetDown(widgetId: String) {
        val current = _dashboardWidgetOrder.value.toMutableList()
        val idx = current.indexOf(widgetId)
        if (idx != -1 && idx < current.size - 1) {
            val temp = current[idx]
            current[idx] = current[idx + 1]
            current[idx + 1] = temp
            setDashboardWidgetOrder(current)
        }
    }

    fun resetWidgetOrder() {
        setDashboardWidgetOrder(DEFAULT_WIDGET_ORDER)
    }

    // Element-spezifische Zeitraum-Scopes (Period Scopes pro Dashboard Element)
    private val _elementPeriodScopes = MutableStateFlow<Map<String, ElementPeriodScope>>(loadElementPeriodScopes())
    val elementPeriodScopes: StateFlow<Map<String, ElementPeriodScope>> = _elementPeriodScopes.asStateFlow()

    private fun loadElementPeriodScopes(): Map<String, ElementPeriodScope> {
        val map = mutableMapOf<String, ElementPeriodScope>()
        val defaultMap = mapOf(
            "STANDARD_KPI" to ElementPeriodScope.ALL,
            "STANDARD_RECENT_DOCS" to ElementPeriodScope.ALL,
            "STANDARD_FINANCE_CHART" to ElementPeriodScope.YEAR,
            "STANDARD_CASH_TRACKER" to ElementPeriodScope.MONTH,
            "STANDARD_RECONCILIATION" to ElementPeriodScope.MONTH,
            "STANDARD_CATEGORY_PIE" to ElementPeriodScope.ALL,
            "STANDARD_BUDGET_WATCH" to ElementPeriodScope.MONTH,
            "STANDARD_DEADLINES_RADAR" to ElementPeriodScope.ALL,
            "STANDARD_DEADLINES_MONTH" to ElementPeriodScope.LAST_30_DAYS,
            "STANDARD_CUSTOM_FIELDS" to ElementPeriodScope.ALL
        )
        defaultMap.forEach { (id, defScope) ->
            val savedStr = prefs.getString("element_period_scope_$id", null)
            val scope = if (savedStr != null) {
                try { ElementPeriodScope.valueOf(savedStr) } catch (e: Exception) { defScope }
            } else defScope
            map[id] = scope
        }
        return map
    }

    fun getElementPeriodScope(elementId: String, defaultScope: ElementPeriodScope = ElementPeriodScope.ALL): ElementPeriodScope {
        return _elementPeriodScopes.value[elementId] ?: defaultScope
    }

    fun setElementPeriodScope(elementId: String, scope: ElementPeriodScope) {
        prefs.edit().putString("element_period_scope_$elementId", scope.name).apply()
        val current = _elementPeriodScopes.value.toMutableMap()
        current[elementId] = scope
        _elementPeriodScopes.value = current
    }

    // Dark / Light Theme Einstellung: "SYSTEM", "DARK", "LIGHT"
    private val _themeMode = MutableStateFlow(prefs.getString("app_theme_mode", "DARK") ?: "DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString("app_theme_mode", mode).apply()
        _themeMode.value = mode
    }

    // Farbschema / Color Skin: "BLUE", "EMERALD", "SLATE", "AMBER", "PURPLE", "ROSE"
    private val _colorSkin = MutableStateFlow(prefs.getString("app_color_skin", "BLUE") ?: "BLUE")
    val colorSkin: StateFlow<String> = _colorSkin.asStateFlow()

    fun setColorSkin(skin: String) {
        prefs.edit().putString("app_color_skin", skin).apply()
        _colorSkin.value = skin
    }

    // Barrierefreiheit: Hoher Kontrast Modus (AAA WCAG Standard für Sehbehinderungen)
    private val _highContrastMode = MutableStateFlow(prefs.getBoolean("app_high_contrast_mode", false))
    val highContrastMode: StateFlow<Boolean> = _highContrastMode.asStateFlow()

    fun setHighContrastMode(enabled: Boolean) {
        prefs.edit().putBoolean("app_high_contrast_mode", enabled).apply()
        _highContrastMode.value = enabled
    }

    // Barrierefreiheit: Vergrößerte Touch-Targets (mindestens 56dp)
    private val _largeTouchTargets = MutableStateFlow(prefs.getBoolean("app_large_touch_targets", false))
    val largeTouchTargets: StateFlow<Boolean> = _largeTouchTargets.asStateFlow()

    fun setLargeTouchTargets(enabled: Boolean) {
        prefs.edit().putBoolean("app_large_touch_targets", enabled).apply()
        _largeTouchTargets.value = enabled
    }

    // Papierkorb / Mülleimer: Automatische Löschung nach definierten Tagen (0 = Nie / Manuell, 7, 14, 30, 60, 90)
    private val _trashRetentionDays = MutableStateFlow(prefs.getInt("trash_retention_days", 30))
    val trashRetentionDays: StateFlow<Int> = _trashRetentionDays.asStateFlow()

    fun setTrashRetentionDays(days: Int) {
        prefs.edit().putInt("trash_retention_days", days).apply()
        _trashRetentionDays.value = days
    }

    // Fristen & Kalender-Integration: "INTERNAL", "EXTERNAL", "BOTH"
    private val _calendarIntegrationMode = MutableStateFlow(prefs.getString("calendar_integration_mode", "BOTH") ?: "BOTH")
    val calendarIntegrationMode: StateFlow<String> = _calendarIntegrationMode.asStateFlow()

    fun setCalendarIntegrationMode(mode: String) {
        prefs.edit().putString("calendar_integration_mode", mode).apply()
        _calendarIntegrationMode.value = mode
    }

    private val _defaultReminderDaysBefore = MutableStateFlow(prefs.getInt("default_reminder_days_before", 14))
    val defaultReminderDaysBefore: StateFlow<Int> = _defaultReminderDaysBefore.asStateFlow()

    fun setDefaultReminderDaysBefore(days: Int) {
        prefs.edit().putInt("default_reminder_days_before", days).apply()
        _defaultReminderDaysBefore.value = days
    }

    // App-Ansicht: STANDARD (fokussiert), ADVANCED (erweitert), EXPERT (volle Kontrolle)
    private val initialViewLevel = runCatching {
        AppViewLevel.valueOf(prefs.getString("app_view_level", if (prefs.getBoolean("app_is_expert_mode", false)) "EXPERT" else "STANDARD") ?: "STANDARD")
    }.getOrDefault(AppViewLevel.STANDARD)

    private val _appViewLevel = MutableStateFlow(initialViewLevel)
    val appViewLevel: StateFlow<AppViewLevel> = _appViewLevel.asStateFlow()

    private val _isExpertMode = MutableStateFlow(initialViewLevel == AppViewLevel.EXPERT)
    val isExpertMode: StateFlow<Boolean> = _isExpertMode.asStateFlow()

    private val _isAdvancedOrExpert = MutableStateFlow(initialViewLevel != AppViewLevel.STANDARD)
    val isAdvancedOrExpert: StateFlow<Boolean> = _isAdvancedOrExpert.asStateFlow()

    fun setAppViewLevel(level: AppViewLevel) {
        prefs.edit()
            .putString("app_view_level", level.name)
            .putBoolean("app_is_expert_mode", level == AppViewLevel.EXPERT)
            .apply()
        _appViewLevel.value = level
        _isExpertMode.value = (level == AppViewLevel.EXPERT)
        _isAdvancedOrExpert.value = (level != AppViewLevel.STANDARD)
    }

    fun setExpertMode(enabled: Boolean) {
        setAppViewLevel(if (enabled) AppViewLevel.EXPERT else AppViewLevel.STANDARD)
    }

    private val _pdfSettings = MutableStateFlow(
        PdfSettings(
            pageSize = prefs.getString("pdf_page_size", "A4") ?: "A4",
            dpi = prefs.getInt("pdf_dpi", 300),
            compressionLevel = prefs.getString("pdf_compression", "Standard") ?: "Standard",
            defaultColorMode = prefs.getString("pdf_color_mode", "AUTO") ?: "AUTO",
            useIdPrefixes = prefs.getBoolean("pdf_use_id_prefixes", true),
            folderPrefixStyle = prefs.getString("pdf_folder_prefix_style", "AKTENPLAN_STANDARD") ?: "AKTENPLAN_STANDARD",
            baseStorageLocation = prefs.getString("pdf_base_storage_location", "APP_STORAGE") ?: "APP_STORAGE",
            customStoragePath = prefs.getString("pdf_custom_storage_path", "") ?: "",
            isOptimizationEnabled = prefs.getBoolean("pdf_opt_enabled", true),
            activeOptimizationTemplateId = prefs.getString("pdf_opt_template_id", "opt_standard_text") ?: "opt_standard_text",
            autoDeskewEnabled = prefs.getBoolean("pdf_opt_autodeskew", true),
            shadowRemovalEnabled = prefs.getBoolean("pdf_opt_shadow_removal", true),
            backgroundCleaningEnabled = prefs.getBoolean("pdf_opt_clean_bg", true)
        )
    )
    val pdfSettings: StateFlow<PdfSettings> = _pdfSettings.asStateFlow()

    // Scanner- & Auslöser-Einstellungen
    private val _scannerSettings = MutableStateFlow(
        ScannerSettings(
            scanMode = prefs.getString("scanner_mode", "AUTO") ?: "AUTO",
            triggerMode = prefs.getString("scanner_trigger_mode", "AUTO_DETECT") ?: "AUTO_DETECT",
            countdownSeconds = prefs.getInt("scanner_countdown_seconds", 3),
            isBulkMode = prefs.getBoolean("scanner_is_bulk_mode", false),
            enableHardwareButtons = prefs.getBoolean("scanner_hardware_buttons", true),
            defaultTemplateId = prefs.getString("scanner_default_template_id", "") ?: "",
            tripodAutoScan = prefs.getBoolean("scanner_tripod_auto_scan", true),
            preScanDelaySeconds = prefs.getInt("scanner_pre_scan_delay_sec", 2),
            postScanDelaySeconds = prefs.getInt("scanner_post_scan_delay_sec", 3),
            autoScanDelayMs = prefs.getLong("scanner_auto_scan_delay_ms", 2000L),
            toolsInfoOnly = prefs.getBoolean("scanner_tools_info_only", true),
            flashMode = prefs.getString("scanner_flash_mode", "OFF") ?: "OFF",
            sheetChangeSensitivity = prefs.getString("scanner_sheet_sensitivity", "MEDIUM") ?: "MEDIUM",
            soundProfile = prefs.getString("scanner_sound_profile", "SUCCESS_BEEP") ?: "SUCCESS_BEEP",
            enableVibration = prefs.getBoolean("scanner_enable_vibration", false)
        )
    )
    val scannerSettings: StateFlow<ScannerSettings> = _scannerSettings.asStateFlow()

    fun updateScannerSettings(settings: ScannerSettings) {
        prefs.edit()
            .putString("scanner_mode", settings.scanMode)
            .putString("scanner_trigger_mode", settings.triggerMode)
            .putInt("scanner_countdown_seconds", settings.countdownSeconds)
            .putBoolean("scanner_is_bulk_mode", settings.isBulkMode)
            .putBoolean("scanner_hardware_buttons", settings.enableHardwareButtons)
            .putString("scanner_default_template_id", settings.defaultTemplateId)
            .putBoolean("scanner_tripod_auto_scan", settings.tripodAutoScan)
            .putInt("scanner_pre_scan_delay_sec", settings.preScanDelaySeconds)
            .putInt("scanner_post_scan_delay_sec", settings.postScanDelaySeconds)
            .putLong("scanner_auto_scan_delay_ms", (settings.preScanDelaySeconds * 1000L))
            .putBoolean("scanner_tools_info_only", settings.toolsInfoOnly)
            .putString("scanner_flash_mode", settings.flashMode)
            .putString("scanner_sheet_sensitivity", settings.sheetChangeSensitivity)
            .putString("scanner_sound_profile", settings.soundProfile)
            .putBoolean("scanner_enable_vibration", settings.enableVibration)
            .apply()
        _scannerSettings.value = settings
    }

    // Vollständig frei editier- und erweiterbare Doc-Typen (ohne feste random Typen)
    private val _docTypes = MutableStateFlow<List<DocTypeItem>>(loadDocTypes())
    val docTypes: StateFlow<List<DocTypeItem>> = _docTypes.asStateFlow()

    private fun loadDocTypes(): List<DocTypeItem> {
        val json = prefs.getString("custom_doc_types_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<DocTypeItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DocTypeItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        colorHex = obj.optLong("colorHex", 0xFF2563EB)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDocTypes(list: List<DocTypeItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("description", item.description)
                put("colorHex", item.colorHex)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_doc_types_json", array.toString()).apply()
        _docTypes.value = list
    }

    fun addDocType(docType: DocTypeItem) {
        val updated = _docTypes.value.toMutableList().apply { add(docType) }
        saveDocTypes(updated)
    }

    fun updateDocType(docType: DocTypeItem) {
        val updated = _docTypes.value.map { if (it.id == docType.id) docType else it }
        saveDocTypes(updated)
    }

    fun deleteDocType(docTypeId: String) {
        val updated = _docTypes.value.filterNot { it.id == docTypeId }
        saveDocTypes(updated)
    }

    // Standard-Vorlagen (nur noch für interne Fallbacks / Schnellscans)
    private val _templates = MutableStateFlow<List<TemplateItem>>(loadTemplates())
    val templates: StateFlow<List<TemplateItem>> = _templates.asStateFlow()

    // Ordner-Struktur (Ebene 1 Hauptkategorien & Ebene 2 Unterordner)
    private val _folders = MutableStateFlow<List<com.example.model.FolderCategoryItem>>(loadFolders())
    val folders: StateFlow<List<com.example.model.FolderCategoryItem>> = _folders.asStateFlow()

    private fun loadFolders(): List<com.example.model.FolderCategoryItem> {
        val json = prefs.getString("custom_folders_hierarchy_json", null) ?: return com.example.model.PRESET_FOLDERS
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<com.example.model.FolderCategoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val subArray = obj.optJSONArray("subFolders")
                val subList = mutableListOf<com.example.model.FolderSubCategoryItem>()
                if (subArray != null) {
                    for (j in 0 until subArray.length()) {
                        val subObj = subArray.getJSONObject(j)
                        subList.add(
                            com.example.model.FolderSubCategoryItem(
                                id = subObj.optString("id", UUID.randomUUID().toString()),
                                name = subObj.getString("name"),
                                prefixId = subObj.optString("prefixId", ""),
                                iconName = subObj.optString("iconName", ""),
                                companyLogo = subObj.optString("companyLogo", ""),
                                customLogoUri = subObj.optString("customLogoUri", "")
                            )
                        )
                    }
                }
                list.add(
                    com.example.model.FolderCategoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        prefixId = obj.optString("prefixId", ""),
                        iconName = obj.optString("iconName", ""),
                        companyLogo = obj.optString("companyLogo", ""),
                        customLogoUri = obj.optString("customLogoUri", ""),
                        subFolders = subList
                    )
                )
            }
            if (list.isEmpty()) com.example.model.PRESET_FOLDERS else list
        } catch (e: Exception) {
            com.example.model.PRESET_FOLDERS
        }
    }

    fun saveFolders(list: List<com.example.model.FolderCategoryItem>) {
        val array = JSONArray()
        for (cat in list) {
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("prefixId", cat.prefixId)
                put("iconName", cat.iconName)
                put("companyLogo", cat.companyLogo)
                put("customLogoUri", cat.customLogoUri)
                val subArr = JSONArray()
                cat.subFolders.forEach { sub ->
                    val sObj = JSONObject().apply {
                        put("id", sub.id)
                        put("name", sub.name)
                        put("prefixId", sub.prefixId)
                        put("iconName", sub.iconName)
                        put("companyLogo", sub.companyLogo)
                        put("customLogoUri", sub.customLogoUri)
                    }
                    subArr.put(sObj)
                }
                put("subFolders", subArr)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_folders_hierarchy_json", array.toString()).apply()
        _folders.value = list
    }

    fun updateFolderIconAndLogo(
        mainFolderId: String,
        subFolderId: String? = null,
        iconName: String,
        companyLogo: String,
        customLogoUri: String = ""
    ) {
        val updated = _folders.value.map { mainCat ->
            val matchesMain = mainCat.id == mainFolderId || mainCat.name.equals(mainFolderId, ignoreCase = true)
            if (subFolderId == null && matchesMain) {
                mainCat.copy(
                    iconName = iconName,
                    companyLogo = companyLogo,
                    customLogoUri = customLogoUri
                )
            } else if (matchesMain && subFolderId != null) {
                val updatedSubs = mainCat.subFolders.map { sub ->
                    if (sub.id == subFolderId || sub.name.equals(subFolderId, ignoreCase = true)) {
                        sub.copy(
                            iconName = iconName,
                            companyLogo = companyLogo,
                            customLogoUri = customLogoUri
                        )
                    } else sub
                }
                mainCat.copy(subFolders = updatedSubs)
            } else mainCat
        }
        saveFolders(updated)
    }

    fun addMainFolder(name: String, prefixId: String = "") {
        val nextIdx = _folders.value.size + 1
        val calcPrefix = if (prefixId.isNotBlank()) prefixId else String.format(java.util.Locale.US, "A%02d", nextIdx)
        val newFolder = com.example.model.FolderCategoryItem(
            name = name.trim(),
            prefixId = calcPrefix,
            subFolders = listOf(
                com.example.model.FolderSubCategoryItem(
                    name = "Allgemein",
                    prefixId = "B$nextIdx.01"
                )
            )
        )
        val updated = _folders.value.toMutableList().apply { add(newFolder) }
        saveFolders(updated)
    }

    fun addSubFolder(mainFolderId: String, name: String, prefixId: String = "") {
        val updated = _folders.value.map { mainCat ->
            if (mainCat.id == mainFolderId || mainCat.name.equals(mainFolderId, ignoreCase = true)) {
                val subIdx = mainCat.subFolders.size + 1
                val mainNum = mainCat.prefixId.filter { it.isDigit() }.toIntOrNull() ?: 1
                val calcSubPrefix = if (prefixId.isNotBlank()) prefixId else "B$mainNum.%02d".format(subIdx)
                val newSub = com.example.model.FolderSubCategoryItem(
                    name = name.trim(),
                    prefixId = calcSubPrefix
                )
                mainCat.copy(subFolders = mainCat.subFolders + newSub)
            } else mainCat
        }
        saveFolders(updated)
    }

    fun renameMainFolder(mainFolderId: String, newName: String) {
        val updated = _folders.value.map { mainCat ->
            if (mainCat.id == mainFolderId || mainCat.name.equals(mainFolderId, ignoreCase = true)) {
                mainCat.copy(name = newName.trim())
            } else mainCat
        }
        saveFolders(updated)
    }

    fun renameSubFolder(mainFolderId: String, subFolderId: String, newName: String) {
        val updated = _folders.value.map { mainCat ->
            if (mainCat.id == mainFolderId || mainCat.name.equals(mainFolderId, ignoreCase = true)) {
                val updatedSubs = mainCat.subFolders.map { sub ->
                    if (sub.id == subFolderId || sub.name.equals(subFolderId, ignoreCase = true)) {
                        sub.copy(name = newName.trim())
                    } else sub
                }
                mainCat.copy(subFolders = updatedSubs)
            } else mainCat
        }
        saveFolders(updated)
    }

    fun deleteMainFolder(mainFolderId: String) {
        val updated = _folders.value.filterNot { it.id == mainFolderId || it.name.equals(mainFolderId, ignoreCase = true) }
        saveFolders(updated)
    }

    fun deleteSubFolder(mainFolderId: String, subFolderId: String) {
        val updated = _folders.value.map { mainCat ->
            if (mainCat.id == mainFolderId || mainCat.name.equals(mainFolderId, ignoreCase = true)) {
                val updatedSubs = mainCat.subFolders.filterNot { it.id == subFolderId || it.name.equals(subFolderId, ignoreCase = true) }
                mainCat.copy(subFolders = updatedSubs)
            } else mainCat
        }
        saveFolders(updated)
    }

    private val dummyTagKeywords = setOf(
        "Mobilfunk", "Vertrag", "Monatlich", "Miete", "Laufender Vertrag", "Wohnung",
        "Gehalt", "Lohn", "Abrechnung", "Quittung", "Barbeleg", "Spesen", "Strom",
        "Stadtwerke", "Abschlag", "Versicherung", "Police", "Schutz", "Kaution",
        "Vollkasko", "Haftpflicht", "Flatrate", "Guthaben"
    )

    private fun loadTemplates(): List<TemplateItem> {
        val json = prefs.getString("custom_templates_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<TemplateItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsArray = obj.optJSONArray("defaultTags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        val t = tagsArray.getString(j)
                        if (t !in dummyTagKeywords) {
                            tags.add(t)
                        }
                    }
                }
                list.add(
                    TemplateItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        mainCategory = obj.getString("mainCategory"),
                        subCategory = obj.getString("subCategory"),
                        mainCategoryId = obj.optString("mainCategoryId", ""),
                        subCategoryId = obj.optString("subCategoryId", ""),
                        defaultDocType = obj.optString("defaultDocType", ""),
                        defaultSender = obj.optString("defaultSender", ""),
                        namingPattern = obj.optString("namingPattern", "YYYY-MM-DD_[Sender]_[Title]"),
                        defaultTags = tags,
                        defaultColorMode = obj.optString("defaultColorMode", "BW")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTemplates(list: List<TemplateItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("mainCategory", item.mainCategory)
                put("subCategory", item.subCategory)
                put("mainCategoryId", item.mainCategoryId)
                put("subCategoryId", item.subCategoryId)
                put("defaultDocType", item.defaultDocType)
                put("defaultSender", item.defaultSender)
                put("namingPattern", item.namingPattern)
                val tagsArr = JSONArray()
                item.defaultTags.forEach { tagsArr.put(it) }
                put("defaultTags", tagsArr)
                put("defaultColorMode", item.defaultColorMode)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_templates_json", array.toString()).apply()
        _templates.value = list
    }

    fun addTemplate(template: TemplateItem) {
        val updated = _templates.value.toMutableList().apply { add(template) }
        saveTemplates(updated)
    }

    fun updateTemplate(template: TemplateItem) {
        val updated = _templates.value.map { if (it.id == template.id) template else it }
        saveTemplates(updated)
    }

    fun deleteTemplate(templateId: String) {
        val updated = _templates.value.filterNot { it.id == templateId }
        saveTemplates(updated)
    }

    // Wiederkehrende Import-Templates für strukturierte wiederkehrende Importe
    private val _importTemplates = MutableStateFlow<List<ImportTemplateItem>>(loadImportTemplates())
    val importTemplates: StateFlow<List<ImportTemplateItem>> = _importTemplates.asStateFlow()

    private fun loadImportTemplates(): List<ImportTemplateItem> {
        val json = prefs.getString("custom_import_templates_json", null) ?: return PRESET_IMPORT_TEMPLATES
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<ImportTemplateItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsArr = obj.optJSONArray("defaultTags")
                val tags = mutableListOf<String>()
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        val t = tagsArr.getString(j)
                        if (t !in dummyTagKeywords) {
                            tags.add(t)
                        }
                    }
                }
                val rawSender = obj.optString("defaultSender", "")
                val cleanSender = if (rawSender in setOf("Vodafone Deutschland", "Hausverwaltung", "Stadtwerke München", "HUK-COBURG", "Arbeitgeber")) "" else rawSender
                list.add(
                    ImportTemplateItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        mainCategory = obj.getString("mainCategory"),
                        subCategory = obj.getString("subCategory"),
                        mainCategoryId = obj.optString("mainCategoryId", ""),
                        subCategoryId = obj.optString("subCategoryId", ""),
                        defaultDocType = obj.optString("defaultDocType", ""),
                        defaultSender = cleanSender,
                        defaultTags = tags,
                        targetFormat = obj.optString("targetFormat", "PDF"),
                        runOcrByDefault = obj.optBoolean("runOcrByDefault", true),
                        colorMode = obj.optString("colorMode", "COLOR"),
                        iconType = obj.optString("iconType", "receipt")
                    )
                )
            }
            if (list.isEmpty()) PRESET_IMPORT_TEMPLATES else list
        } catch (e: Exception) {
            PRESET_IMPORT_TEMPLATES
        }
    }

    fun saveImportTemplates(list: List<ImportTemplateItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("description", item.description)
                put("mainCategory", item.mainCategory)
                put("subCategory", item.subCategory)
                put("mainCategoryId", item.mainCategoryId)
                put("subCategoryId", item.subCategoryId)
                put("defaultDocType", item.defaultDocType)
                put("defaultSender", item.defaultSender)
                val tagsArr = JSONArray()
                item.defaultTags.forEach { tagsArr.put(it) }
                put("defaultTags", tagsArr)
                put("targetFormat", item.targetFormat)
                put("runOcrByDefault", item.runOcrByDefault)
                put("colorMode", item.colorMode)
                put("iconType", item.iconType)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_import_templates_json", array.toString()).apply()
        _importTemplates.value = list
    }

    fun addImportTemplate(item: ImportTemplateItem) {
        val updated = _importTemplates.value.toMutableList().apply { add(item) }
        saveImportTemplates(updated)
    }

    fun deleteImportTemplate(templateId: String) {
        val updated = _importTemplates.value.filterNot { it.id == templateId }
        saveImportTemplates(updated)
    }

    fun updatePdfSettings(newSettings: PdfSettings) {
        prefs.edit()
            .putString("pdf_page_size", newSettings.pageSize)
            .putInt("pdf_dpi", newSettings.dpi)
            .putString("pdf_compression", newSettings.compressionLevel)
            .putString("pdf_color_mode", newSettings.defaultColorMode)
            .putBoolean("pdf_use_id_prefixes", newSettings.useIdPrefixes)
            .putString("pdf_folder_prefix_style", newSettings.folderPrefixStyle)
            .putString("pdf_base_storage_location", newSettings.baseStorageLocation)
            .putString("pdf_custom_storage_path", newSettings.customStoragePath)
            .putBoolean("pdf_opt_enabled", newSettings.isOptimizationEnabled)
            .putString("pdf_opt_template_id", newSettings.activeOptimizationTemplateId)
            .putBoolean("pdf_opt_autodeskew", newSettings.autoDeskewEnabled)
            .putBoolean("pdf_opt_shadow_removal", newSettings.shadowRemovalEnabled)
            .putBoolean("pdf_opt_clean_bg", newSettings.backgroundCleaningEnabled)
            .apply()
        _pdfSettings.value = newSettings
    }

    // First-Run Setup Wizard Status (v3 stellt sicher, dass der Assistent beim aktuellen App-Start direkt angezeigt wird)
    private val _isWizardCompleted = MutableStateFlow(prefs.getBoolean("setup_wizard_completed_v3", false))
    val isWizardCompleted: StateFlow<Boolean> = _isWizardCompleted.asStateFlow()

    fun setWizardCompleted(completed: Boolean) {
        prefs.edit().putBoolean("setup_wizard_completed_v3", completed).apply()
        _isWizardCompleted.value = completed
    }

    fun resetWizard() {
        prefs.edit().putBoolean("setup_wizard_completed_v3", false).apply()
        _isWizardCompleted.value = false
    }

    // Zero-Knowledge AES-256 Verschlüsselung & Cloud-Sync
    private val _syncPasswordKey = MutableStateFlow(prefs.getString("sync_master_password", "MyDocAnizer2026MasterKey!") ?: "MyDocAnizer2026MasterKey!")
    val syncPasswordKey: StateFlow<String> = _syncPasswordKey.asStateFlow()

    fun setSyncPasswordKey(key: String) {
        prefs.edit().putString("sync_master_password", key).apply()
        _syncPasswordKey.value = key
    }

    private val _autoCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("cloud_sync_auto_enabled", false))
    val autoCloudSyncEnabled: StateFlow<Boolean> = _autoCloudSyncEnabled.asStateFlow()

    fun setAutoCloudSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_sync_auto_enabled", enabled).apply()
        _autoCloudSyncEnabled.value = enabled
    }

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong("cloud_sync_last_time", 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("cloud_sync_last_time", timestamp).apply()
        _lastSyncTimestamp.value = timestamp
    }

    private val _cloudSyncConfig = MutableStateFlow(
        com.example.model.CloudSyncConfig(
            syncTarget = prefs.getString("cloud_sync_target", "OFFLINE_ONLY") ?: "OFFLINE_ONLY",
            enableGoogleDrive = prefs.getBoolean("cloud_sync_enable_drive", false),
            enableWebDavNas = prefs.getBoolean("cloud_sync_enable_nas", false),
            enableLocalVault = prefs.getBoolean("cloud_sync_enable_local", true),
            enableManualUsbExport = prefs.getBoolean("cloud_sync_enable_manual_usb", false),
            googleDriveAccount = prefs.getString("cloud_sync_drive_account", "mr.locke84@gmail.com") ?: "mr.locke84@gmail.com",
            googleDriveFolder = prefs.getString("cloud_sync_drive_folder", "myDocAnizer_Vault") ?: "myDocAnizer_Vault",
            googleDriveConnected = prefs.getBoolean("cloud_sync_drive_connected", false),
            nasProtocol = prefs.getString("cloud_sync_nas_proto", "SMB") ?: "SMB",
            nasShareName = prefs.getString("cloud_sync_nas_share", "myDocAnizer_Backup") ?: "myDocAnizer_Backup",
            nasConnected = prefs.getBoolean("cloud_sync_nas_connected", false),
            webDavUrl = prefs.getString("cloud_sync_webdav_url", "") ?: "",
            webDavUsername = prefs.getString("cloud_sync_webdav_user", "") ?: "",
            webDavPassword = prefs.getString("cloud_sync_webdav_pass", "") ?: "",
            syncTrigger = prefs.getString("cloud_sync_trigger", "AUTO_AFTER_SCAN") ?: "AUTO_AFTER_SCAN",
            syncWifiOnly = prefs.getBoolean("cloud_sync_wifi_only", true),
            autoMirrorToBackupDir = prefs.getBoolean("cloud_sync_auto_mirror", true),
            isAirGappedStrict = prefs.getBoolean("cloud_sync_air_gapped", false),
            usbSyncWarningOnScan = prefs.getBoolean("cloud_sync_usb_warning", true)
        )
    )
    val cloudSyncConfig: StateFlow<com.example.model.CloudSyncConfig> = _cloudSyncConfig.asStateFlow()

    fun updateCloudSyncConfig(config: com.example.model.CloudSyncConfig) {
        prefs.edit()
            .putString("cloud_sync_target", config.syncTarget)
            .putBoolean("cloud_sync_enable_drive", config.enableGoogleDrive)
            .putBoolean("cloud_sync_enable_nas", config.enableWebDavNas)
            .putBoolean("cloud_sync_enable_local", config.enableLocalVault)
            .putBoolean("cloud_sync_enable_manual_usb", config.enableManualUsbExport)
            .putString("cloud_sync_drive_account", config.googleDriveAccount)
            .putString("cloud_sync_drive_folder", config.googleDriveFolder)
            .putBoolean("cloud_sync_drive_connected", config.googleDriveConnected)
            .putString("cloud_sync_nas_proto", config.nasProtocol)
            .putString("cloud_sync_nas_share", config.nasShareName)
            .putBoolean("cloud_sync_nas_connected", config.nasConnected)
            .putString("cloud_sync_webdav_url", config.webDavUrl)
            .putString("cloud_sync_webdav_user", config.webDavUsername)
            .putString("cloud_sync_webdav_pass", config.webDavPassword)
            .putString("cloud_sync_trigger", config.syncTrigger)
            .putBoolean("cloud_sync_wifi_only", config.syncWifiOnly)
            .putBoolean("cloud_sync_auto_mirror", config.autoMirrorToBackupDir)
            .putBoolean("cloud_sync_air_gapped", config.isAirGappedStrict)
            .putBoolean("cloud_sync_usb_warning", config.usbSyncWarningOnScan)
            .apply()
        _cloudSyncConfig.value = config
    }

    // Passkey & Tresor-Verschlüsselungsmodus ("PASSKEY", "PASSWORD", "BIOMETRIC")
    private val _securityMethod = MutableStateFlow(prefs.getString("security_method", "PASSKEY") ?: "PASSKEY")
    val securityMethod: StateFlow<String> = _securityMethod.asStateFlow()

    private val _passkeyName = MutableStateFlow(prefs.getString("security_passkey_name", "Google Passkey (Android Keystore)") ?: "Google Passkey (Android Keystore)")
    val passkeyName: StateFlow<String> = _passkeyName.asStateFlow()

    private val _passkeyCreatedAt = MutableStateFlow(prefs.getLong("security_passkey_created", System.currentTimeMillis()))
    val passkeyCreatedAt: StateFlow<Long> = _passkeyCreatedAt.asStateFlow()

    fun setSecurityMethod(method: String) {
        prefs.edit().putString("security_method", method).apply()
        _securityMethod.value = method
    }

    fun registerPasskey(name: String) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString("security_method", "PASSKEY")
            .putString("security_passkey_name", name)
            .putLong("security_passkey_created", now)
            .apply()
        _securityMethod.value = "PASSKEY"
        _passkeyName.value = name
        _passkeyCreatedAt.value = now
    }

    // USB-Stick Backup-Verwaltung (Mehrere Sticks, Offsite-Aufbewahrung, Sync-Tracking)
    private val defaultUsbSticks = listOf(
        com.example.model.UsbBackupDrive(
            id = "usb-1",
            name = "USB-Stick 1 (Zuhause)",
            locationNote = "Schreibtisch-Schublade (Nahbereich)",
            lastSyncTimestamp = System.currentTimeMillis() - 86400000L,
            totalSyncedDocs = 0
        ),
        com.example.model.UsbBackupDrive(
            id = "usb-2",
            name = "USB-Stick 2 (Ausgelagert)",
            locationNote = "Bei Verwandten / Bankschließfach (Brandschutz & Hochwasser)",
            lastSyncTimestamp = 0L,
            totalSyncedDocs = 0
        )
    )

    private val _usbBackupDrives = MutableStateFlow<List<com.example.model.UsbBackupDrive>>(defaultUsbSticks)
    val usbBackupDrives: StateFlow<List<com.example.model.UsbBackupDrive>> = _usbBackupDrives.asStateFlow()

    fun addUsbBackupDrive(name: String, locationNote: String) {
        val newDrive = com.example.model.UsbBackupDrive(
            name = name,
            locationNote = locationNote,
            lastSyncTimestamp = 0L,
            totalSyncedDocs = 0
        )
        _usbBackupDrives.value = _usbBackupDrives.value + newDrive
    }

    fun deleteUsbBackupDrive(id: String) {
        _usbBackupDrives.value = _usbBackupDrives.value.filter { it.id != id }
    }

    fun syncUsbDrive(id: String, docCount: Int) {
        val now = System.currentTimeMillis()
        _usbBackupDrives.value = _usbBackupDrives.value.map {
            if (it.id == id) it.copy(lastSyncTimestamp = now, totalSyncedDocs = docCount) else it
        }
    }

    fun connectGoogleDrive(account: String) {
        updateCloudSyncConfig(
            _cloudSyncConfig.value.copy(
                enableGoogleDrive = true,
                googleDriveAccount = account,
                googleDriveConnected = true
            )
        )
    }

    fun connectNas(protocol: String, share: String) {
        updateCloudSyncConfig(
            _cloudSyncConfig.value.copy(
                enableWebDavNas = true,
                nasProtocol = protocol,
                nasShareName = share,
                nasConnected = true
            )
        )
    }

    // Optionale Biometrische App-Autorisierung (Fingerabdruck, Gesichtserkennung, PIN)
    private val _biometricAuthEnabled = MutableStateFlow(prefs.getBoolean("biometric_auth_enabled", false))
    val biometricAuthEnabled: StateFlow<Boolean> = _biometricAuthEnabled.asStateFlow()

    fun setBiometricAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_auth_enabled", enabled).apply()
        _biometricAuthEnabled.value = enabled
    }

    // On-Device LLM Inferenz- & Hardware-Parameter
    private val _llmInferenceConfig = MutableStateFlow(
        com.example.model.LlmInferenceConfig(
            temperature = prefs.getFloat("llm_temp", 0.1f),
            maxTokens = prefs.getInt("llm_max_tokens", 512),
            topP = prefs.getFloat("llm_top_p", 0.9f),
            systemPromptFocus = prefs.getString("llm_system_focus", "STANDARD") ?: "STANDARD",
            threadCount = prefs.getInt("llm_threads", 4),
            contextLength = prefs.getInt("llm_ctx_len", 2048),
            onlyShowCompatibleModels = prefs.getBoolean("llm_filter_compat", true)
        )
    )
    val llmInferenceConfig: StateFlow<com.example.model.LlmInferenceConfig> = _llmInferenceConfig.asStateFlow()

    fun updateLlmInferenceConfig(config: com.example.model.LlmInferenceConfig) {
        prefs.edit()
            .putFloat("llm_temp", config.temperature)
            .putInt("llm_max_tokens", config.maxTokens)
            .putFloat("llm_top_p", config.topP)
            .putString("llm_system_focus", config.systemPromptFocus)
            .putInt("llm_threads", config.threadCount)
            .putInt("llm_ctx_len", config.contextLength)
            .putBoolean("llm_filter_compat", config.onlyShowCompatibleModels)
            .apply()
        _llmInferenceConfig.value = config
    }
}
