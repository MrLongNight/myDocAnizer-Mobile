package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.model.ChatMessage
import com.example.model.ImportTemplateItem
import com.example.model.PdfSettings
import com.example.model.ScannerSettings
import com.example.model.TemplateItem
import com.example.model.DocRule
import com.example.model.BatchDocumentItem
import com.example.model.BatchItemStatus
import com.example.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocAnizerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val documentDao = db.documentDao()
    val customFieldDao = db.customFieldDao()
    val financeDao = db.financeDao()
    private val settingsRepo = SettingsRepository(application)
    val ruleRepo = DocRuleRepository(application)
    val llmService = LlmService(application)
    val biometricAuthManager = BiometricAuthManager(application)
    val passkeyAuthManager = PasskeyAuthManager(application)
    val p2pSyncManager = P2pSyncManager(application, db)

    init {
        AppAuditLogger.init(application)
        settingsRepo.incrementAppLaunchCount()
    }

    val auditLogs: StateFlow<List<AuditLogEntry>> = AppAuditLogger.logs

    fun exportAndShareAuditLog(context: android.content.Context) {
        try {
            val file = AppAuditLogger.exportLogFile(context)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "myDocAnizer Diagnose- & Audit-Protokoll")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Diagnose-Log teilen / exportieren").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("DocAnizerViewModel", "Fehler beim Exportieren des Audit-Logs: ${e.message}", e)
        }
    }

    fun clearAuditLogs(context: android.content.Context) {
        AppAuditLogger.clearLogs(context)
    }

    val appLaunchCount: StateFlow<Int> = settingsRepo.appLaunchCount
    val firstLaunchTime: StateFlow<Long> = settingsRepo.firstLaunchTime
    val lastRatingDismissedTime: StateFlow<Long> = settingsRepo.lastRatingDismissedTime
    val launchesSinceDismissal: StateFlow<Int> = settingsRepo.launchesSinceDismissal

    private val _showSuccessRatingPrompt = MutableStateFlow(false)
    val showSuccessRatingPrompt: StateFlow<Boolean> = _showSuccessRatingPrompt.asStateFlow()

    fun snoozeRatingPrompt() {
        settingsRepo.snoozeRatingPrompt()
        _showSuccessRatingPrompt.value = false
    }

    fun dismissSuccessRatingPrompt() {
        _showSuccessRatingPrompt.value = false
    }

    fun triggerRatingPromptOnSuccess() {
        val hasRated = hasRatedOrSkipped.value
        val launches = appLaunchCount.value
        val scans = scannedDocumentCount.value
        val firstLaunch = firstLaunchTime.value
        val dismissedTime = lastRatingDismissedTime.value
        val snoozeLaunches = launchesSinceDismissal.value

        val now = System.currentTimeMillis()
        val installedAtLeast3Days = (now - firstLaunch) >= 3 * 24 * 60 * 60 * 1000L
        val snoozeDaysPassed = (now - dismissedTime) >= 14 * 24 * 60 * 60 * 1000L
        val snoozeLaunchesPassed = snoozeLaunches >= 10

        // Verschärfte, professionelle Kriterien für absolute Unaufdringlichkeit:
        // 1. Hat noch nicht bewertet oder dauerhaft abgelehnt.
        // 2. Mindestens 10 App-Launches (etablierter Nutzer).
        // 3. Mindestens 5 erfolgreiche Dokumentenscans (kennt den Mehrwert der App).
        // 4. App muss seit mindestens 3 Tagen installiert sein (kein nerviges Pop-up am 1. Tag).
        // 5. Falls zuvor weggeschoben (snoozed), müssen 14 Tage vergangen UND 10 weitere App-Starts erfolgt sein.
        val isEligible = !hasRated &&
                launches >= 10 &&
                scans >= 5 &&
                installedAtLeast3Days &&
                (dismissedTime == 0L || (snoozeDaysPassed && snoozeLaunchesPassed))

        if (isEligible) {
            _showSuccessRatingPrompt.value = true
        }
    }
    val scannedDocumentCount: StateFlow<Int> = settingsRepo.scannedDocumentCount
    val hasRatedOrSkipped: StateFlow<Boolean> = settingsRepo.hasRatedOrSkipped

    fun incrementScannedDocumentCount() {
        settingsRepo.incrementScannedDocumentCount()
        triggerRatingPromptOnSuccess()
    }

    fun setHasRatedOrSkipped(value: Boolean) {
        settingsRepo.setHasRatedOrSkipped(value)
        _showSuccessRatingPrompt.value = false
    }

    // Vordefinierte & Benutzerdefinierte Zusatzfelder (Custom Fields)
    val customFields: StateFlow<List<com.example.model.CustomFieldEntity>> = customFieldDao.getAllCustomFields()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocumentCustomFieldValues: StateFlow<List<com.example.model.DocumentCustomFieldValueEntity>> = customFieldDao.getAllDocumentCustomFieldValues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enableIncomeExpenseTracking: StateFlow<Boolean> = settingsRepo.enableIncomeExpenseTracking
    val enableHouseholdBook: StateFlow<Boolean> = settingsRepo.enableHouseholdBook
    fun setEnableIncomeExpenseTracking(enabled: Boolean) = settingsRepo.setEnableIncomeExpenseTracking(enabled)
    fun setEnableHouseholdBook(enabled: Boolean) = settingsRepo.setEnableHouseholdBook(enabled)

    val enableCashTracker: StateFlow<Boolean> = settingsRepo.enableCashTracker
    fun setEnableCashTracker(enabled: Boolean) = settingsRepo.setEnableCashTracker(enabled)

    val enableReceiptExpenses: StateFlow<Boolean> = settingsRepo.enableReceiptExpenses
    fun setEnableReceiptExpenses(enabled: Boolean) = settingsRepo.setEnableReceiptExpenses(enabled)

    val enableBankStatementImport: StateFlow<Boolean> = settingsRepo.enableBankStatementImport
    fun setEnableBankStatementImport(enabled: Boolean) = settingsRepo.setEnableBankStatementImport(enabled)

    val enableMonthlyReconciliation: StateFlow<Boolean> = settingsRepo.enableMonthlyReconciliation
    fun setEnableMonthlyReconciliation(enabled: Boolean) = settingsRepo.setEnableMonthlyReconciliation(enabled)

    val notifyReconciliationDiscrepancies: StateFlow<Boolean> = settingsRepo.notifyReconciliationDiscrepancies
    fun setNotifyReconciliationDiscrepancies(enabled: Boolean) = settingsRepo.setNotifyReconciliationDiscrepancies(enabled)

    // Dashboard anpassbare Widgets
    val showBelegQuickScanWidget: StateFlow<Boolean> = settingsRepo.showBelegQuickScanWidget
    fun setShowBelegQuickScanWidget(show: Boolean) = settingsRepo.setShowBelegQuickScanWidget(show)

    val showCashTrackerWidget: StateFlow<Boolean> = settingsRepo.showCashTrackerWidget
    fun setShowCashTrackerWidget(show: Boolean) = settingsRepo.setShowCashTrackerWidget(show)

    val showKpiWidgets: StateFlow<Boolean> = settingsRepo.showKpiWidgets
    fun setShowKpiWidgets(show: Boolean) = settingsRepo.setShowKpiWidgets(show)

    val showDeadlinesWidget: StateFlow<Boolean> = settingsRepo.showDeadlinesWidget
    fun setShowDeadlinesWidget(show: Boolean) = settingsRepo.setShowDeadlinesWidget(show)

    val showFinanceWidget: StateFlow<Boolean> = settingsRepo.showFinanceWidget
    fun setShowFinanceWidget(show: Boolean) = settingsRepo.setShowFinanceWidget(show)

    val showReconciliationWidget: StateFlow<Boolean> = settingsRepo.showReconciliationWidget
    fun setShowReconciliationWidget(show: Boolean) = settingsRepo.setShowReconciliationWidget(show)

    val showCategoryDistributionWidget: StateFlow<Boolean> = settingsRepo.showCategoryDistributionWidget
    fun setShowCategoryDistributionWidget(show: Boolean) = settingsRepo.setShowCategoryDistributionWidget(show)

    val showSecurityScoreWidget: StateFlow<Boolean> = settingsRepo.showSecurityScoreWidget
    fun setShowSecurityScoreWidget(show: Boolean) = settingsRepo.setShowSecurityScoreWidget(show)

    val showCustomFieldsWidget: StateFlow<Boolean> = settingsRepo.showCustomFieldsWidget
    fun setShowCustomFieldsWidget(show: Boolean) = settingsRepo.setShowCustomFieldsWidget(show)

    val showRecentDocsWidget: StateFlow<Boolean> = settingsRepo.showRecentDocsWidget
    fun setShowRecentDocsWidget(show: Boolean) = settingsRepo.setShowRecentDocsWidget(show)

    val showFolderShortcutsWidget: StateFlow<Boolean> = settingsRepo.showFolderShortcutsWidget
    fun setShowFolderShortcutsWidget(show: Boolean) = settingsRepo.setShowFolderShortcutsWidget(show)

    val showInboxDocsWidget: StateFlow<Boolean> = settingsRepo.showInboxDocsWidget
    fun setShowInboxDocsWidget(show: Boolean) = settingsRepo.setShowInboxDocsWidget(show)

    val showBudgetWatchWidget: StateFlow<Boolean> = settingsRepo.showBudgetWatchWidget
    fun setShowBudgetWatchWidget(show: Boolean) = settingsRepo.setShowBudgetWatchWidget(show)

    val showQuickActionsWidget: StateFlow<Boolean> = settingsRepo.showQuickActionsWidget
    fun setShowQuickActionsWidget(show: Boolean) = settingsRepo.setShowQuickActionsWidget(show)

    val showQuickNoteWidget: StateFlow<Boolean> = settingsRepo.showQuickNoteWidget
    fun setShowQuickNoteWidget(show: Boolean) = settingsRepo.setShowQuickNoteWidget(show)

    val quickNoteText: StateFlow<String> = settingsRepo.quickNoteText
    fun setQuickNoteText(text: String) = settingsRepo.setQuickNoteText(text)

    // Individuelle Dashboard-Elemente & Sortierung
    val customDashboardWidgets: StateFlow<List<com.example.model.CustomDashboardWidget>> = settingsRepo.customDashboardWidgets
    fun addCustomDashboardWidget(widget: com.example.model.CustomDashboardWidget) = settingsRepo.addCustomWidget(widget)
    fun updateCustomDashboardWidget(widget: com.example.model.CustomDashboardWidget) = settingsRepo.updateCustomWidget(widget)
    fun deleteCustomDashboardWidget(id: String) = settingsRepo.deleteCustomWidget(id)
    fun toggleCustomDashboardWidget(id: String, enabled: Boolean) = settingsRepo.toggleCustomWidget(id, enabled)
    fun updateCustomWidgetChecklist(id: String, items: List<com.example.model.ChecklistItem>) = settingsRepo.updateCustomWidgetChecklist(id, items)
    fun updateCustomWidgetNote(id: String, text: String) = settingsRepo.updateCustomWidgetNote(id, text)

    val dashboardWidgetOrder: StateFlow<List<String>> = settingsRepo.dashboardWidgetOrder
    fun setDashboardWidgetOrder(order: List<String>) = settingsRepo.setDashboardWidgetOrder(order)
    fun moveDashboardWidgetUp(widgetId: String) = settingsRepo.moveWidgetUp(widgetId)
    fun moveDashboardWidgetDown(widgetId: String) = settingsRepo.moveWidgetDown(widgetId)
    fun resetDashboardWidgetOrder() = settingsRepo.resetWidgetOrder()

    // Element-spezifische Zeitraum-Scopes
    val elementPeriodScopes: StateFlow<Map<String, com.example.model.ElementPeriodScope>> = settingsRepo.elementPeriodScopes
    fun getElementPeriodScope(elementId: String, default: com.example.model.ElementPeriodScope = com.example.model.ElementPeriodScope.ALL): com.example.model.ElementPeriodScope = settingsRepo.getElementPeriodScope(elementId, default)
    fun setElementPeriodScope(elementId: String, scope: com.example.model.ElementPeriodScope) = settingsRepo.setElementPeriodScope(elementId, scope)

    // Homescreen Widget Intent Triggers
    private val _targetNavigationTab = MutableStateFlow<String?>(null)
    val targetNavigationTab: StateFlow<String?> = _targetNavigationTab.asStateFlow()
    fun setTargetNavigationTab(tab: String?) {
        _targetNavigationTab.value = tab
    }

    private val _directOpenCashTracker = MutableStateFlow(false)
    val directOpenCashTracker: StateFlow<Boolean> = _directOpenCashTracker.asStateFlow()
    fun setDirectOpenCashTracker(open: Boolean) {
        _directOpenCashTracker.value = open
    }

    private val _directOpenReconciliation = MutableStateFlow(false)
    val directOpenReconciliation: StateFlow<Boolean> = _directOpenReconciliation.asStateFlow()
    fun setDirectOpenReconciliation(open: Boolean) {
        _directOpenReconciliation.value = open
    }

    // Bargeld-Transaktionen & Kontoauszüge
    val cashTransactions: StateFlow<List<com.example.model.CashTransactionEntity>> = financeDao.getAllCashTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankStatementEntries: StateFlow<List<com.example.model.BankStatementEntryEntity>> = financeDao.getAllBankStatementEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCashTransaction(tx: com.example.model.CashTransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            financeDao.insertCashTransaction(tx)
            withContext(Dispatchers.Main) {
                triggerRatingPromptOnSuccess()
            }
        }
    }

    fun updateCashTransaction(tx: com.example.model.CashTransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            financeDao.updateCashTransaction(tx)
        }
    }

    fun deleteCashTransaction(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            financeDao.deleteCashTransactionById(id)
        }
    }

    suspend fun importBankStatementEntriesSync(entries: List<com.example.model.BankStatementEntryEntity>): Int {
        val existing = financeDao.getAllBankStatementEntriesList()
        val existingKeys = existing.map { "${it.date}_${it.amount}_${it.bookingText.trim().lowercase()}" }.toSet()
        val newEntries = entries.filter {
            val key = "${it.date}_${it.amount}_${it.bookingText.trim().lowercase()}"
            !existingKeys.contains(key)
        }
        if (newEntries.isNotEmpty()) {
            financeDao.insertBankStatementEntries(newEntries)
        }
        return newEntries.size
    }

    fun importBankStatementEntries(entries: List<com.example.model.BankStatementEntryEntity>, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val insertedCount = importBankStatementEntriesSync(entries)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(insertedCount)
            }
        }
    }

    fun importDemoBankStatement(monthKey: String, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val demoEntries = com.example.service.BankStatementParserService.generateDemoStatement(monthKey)
            val insertedCount = importBankStatementEntriesSync(demoEntries)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(insertedCount)
            }
        }
    }

    fun deleteBankStatementEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            financeDao.deleteBankStatementEntryById(id)
        }
    }

    fun clearBankStatementEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            financeDao.clearAllBankStatementEntries()
        }
    }

    suspend fun addCustomFieldSync(field: com.example.model.CustomFieldEntity): Boolean {
        val existing = customFieldDao.getAllCustomFieldsList()
        // Nur eindeutige Feldbezeichnungen erlauben
        if (existing.any { it.name.trim().equals(field.name.trim(), ignoreCase = true) }) {
            return false
        }
        customFieldDao.insertCustomField(field)
        return true
    }

    fun addCustomField(field: com.example.model.CustomFieldEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            addCustomFieldSync(field)
        }
    }

    suspend fun updateCustomFieldSync(field: com.example.model.CustomFieldEntity): Boolean {
        val existing = customFieldDao.getAllCustomFieldsList()
        // Nur eindeutige Feldbezeichnungen erlauben
        if (existing.any { it.id != field.id && it.name.trim().equals(field.name.trim(), ignoreCase = true) }) {
            return false
        }
        customFieldDao.updateCustomField(field)
        return true
    }

    fun updateCustomField(field: com.example.model.CustomFieldEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            updateCustomFieldSync(field)
        }
    }

    fun deleteCustomField(fieldId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            customFieldDao.deleteCustomFieldById(fieldId)
        }
    }

    fun setDocumentCustomFieldValue(documentId: Long, customFieldId: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (value.isBlank()) {
                customFieldDao.deleteFieldValue(documentId, customFieldId)
            } else {
                customFieldDao.insertOrUpdateFieldValue(
                    com.example.model.DocumentCustomFieldValueEntity(
                        documentId = documentId,
                        customFieldId = customFieldId,
                        fieldValue = value
                    )
                )
            }
        }
    }

    // Lokaler WLAN / P2P Master-Master Sync
    val p2pPairedDevices: StateFlow<List<com.example.model.PairedDevice>> = p2pSyncManager.pairedDevices
    val p2pServerStatus: StateFlow<com.example.model.P2pServerStatus> = p2pSyncManager.serverStatus
    val p2pHostPairingPin: StateFlow<String?> = p2pSyncManager.hostPairingPin
    val p2pPendingPairingRequests: StateFlow<List<com.example.model.IncomingPairingRequest>> = p2pSyncManager.pendingPairingRequests
    val p2pSyncProgress: StateFlow<com.example.model.P2pSyncProgressState> = p2pSyncManager.syncProgress
    val p2pActiveConflicts: StateFlow<List<com.example.model.SyncConflict>> = p2pSyncManager.activeConflicts
    val p2pSyncHistory: StateFlow<List<com.example.model.P2pSyncLog>> = p2pSyncManager.syncHistory
    val p2pLocalDeviceName: StateFlow<String> = p2pSyncManager.localDeviceName

    fun setP2pDeviceName(name: String) = p2pSyncManager.setLocalDeviceName(name)
    fun startP2pServer() = p2pSyncManager.startServer()
    fun stopP2pServer() = p2pSyncManager.stopServer()
    fun startP2pHostPairingMode(): String = p2pSyncManager.startHostPairingMode()
    fun stopP2pHostPairingMode() = p2pSyncManager.stopHostPairingMode()
    fun authorizeP2pPairingRequest(requestId: String, allow: Boolean) = p2pSyncManager.authorizePairingRequest(requestId, allow)
    fun pairWithP2pDevice(ip: String, port: Int = 8765, pin: String, onResult: (Result<PairingResult>) -> Unit) {
        viewModelScope.launch {
            val res = p2pSyncManager.pairWithRemoteDevice(ip, port, pin)
            onResult(res)
        }
    }
    fun syncWithP2pDevice(device: com.example.model.PairedDevice, onResult: (Result<com.example.model.P2pSyncLog>) -> Unit = {}) {
        viewModelScope.launch {
            val res = p2pSyncManager.syncWithDevice(device)
            onResult(res)
        }
    }
    fun syncAllP2pDevices(onComplete: (Int, Int) -> Unit = { _, _ -> }) {
        p2pSyncManager.syncAllPairedDevices(onComplete)
    }
    fun resolveP2pConflict(conflict: com.example.model.SyncConflict, action: com.example.model.ConflictResolutionAction) {
        viewModelScope.launch {
            p2pSyncManager.resolveConflict(conflict, action)
        }
    }
    fun removeP2pPairedDevice(deviceId: String) = p2pSyncManager.removePairedDevice(deviceId)

    /**
     * Koppelt die myDocAnizer-Desktop App über den vom Desktop-Monitor gescannten QR-Code
     */
    fun pairWithDesktopLightApp(qrJson: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = p2pSyncManager.pairWithDesktopQrData(qrJson)
            res.onSuccess { dev ->
                onResult(true, "✅ Erfolgreich mit Desktop '${dev.name}' verbunden!")
            }.onFailure { err ->
                onResult(false, "Verbindung fehlgeschlagen: ${err.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    // Biometrische Autorisierung (Fingerabdruck, Face Unlock, PIN)
    val biometricAuthEnabled: StateFlow<Boolean> = settingsRepo.biometricAuthEnabled
    private val _isAppUnlocked = MutableStateFlow(!settingsRepo.biometricAuthEnabled.value)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    fun setBiometricAuthEnabled(enabled: Boolean) {
        settingsRepo.setBiometricAuthEnabled(enabled)
        if (!enabled) {
            _isAppUnlocked.value = true
        }
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun lockApp() {
        if (biometricAuthEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    fun getBiometricStatusLabel(): String = biometricAuthManager.getHardwareStatusLabel()

    fun authenticateBiometric(
        activity: androidx.fragment.app.FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        biometricAuthManager.showBiometricPrompt(
            activity = activity,
            title = "myDocAnizer entsperren",
            subtitle = "Authentifiziere dich per Fingerabdruck, Gesicht oder PIN",
            onSuccess = {
                _isAppUnlocked.value = true
                onSuccess()
            },
            onError = onError
        )
    }

    // Deterministische Schlagwort-Regeln (vor KI)
    val docRules: StateFlow<List<com.example.model.DocRule>> = ruleRepo.rules

    fun addDocRule(rule: com.example.model.DocRule) = ruleRepo.addRule(rule)
    fun addDocRules(rules: List<com.example.model.DocRule>) = ruleRepo.addRules(rules)
    fun updateDocRule(rule: com.example.model.DocRule) = ruleRepo.updateRule(rule)
    fun deleteDocRule(ruleId: String) = ruleRepo.deleteRule(ruleId)
    fun toggleDocRule(ruleId: String, enabled: Boolean) = ruleRepo.toggleRule(ruleId, enabled)

    // On-Device LLM Modelle & Hardware-Telemetrie
    val availableModels: StateFlow<List<HuggingFaceModelInfo>> = llmService.availableModels
    val isGeneratingAi: StateFlow<Boolean> = llmService.isGenerating
    val deviceHardwareInfo: StateFlow<com.example.model.DeviceHardwareInfo> = llmService.deviceHardware
    val llmInferenceConfig: StateFlow<com.example.model.LlmInferenceConfig> = settingsRepo.llmInferenceConfig
    val isCheckingNewModels: StateFlow<Boolean> = llmService.isCheckingNewModels
    val newModelsNotification: StateFlow<String?> = llmService.newModelsNotification
    val lastCatalogSync: StateFlow<Long> = llmService.lastCatalogSync

    fun selectModel(modelId: String) = llmService.selectModel(modelId)
    fun selectHuggingFaceModel(modelId: String) = llmService.selectModel(modelId)
    fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) {
        viewModelScope.launch { llmService.downloadModel(modelId, onProgress) }
    }
    fun deleteModel(modelId: String) {
        llmService.deleteModel(modelId)
    }
    fun refreshHardwareInfo() = llmService.refreshHardwareInfo()
    fun updateLlmInferenceConfig(config: com.example.model.LlmInferenceConfig) = settingsRepo.updateLlmInferenceConfig(config)
    fun setLlmTemperature(temp: Float) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(temperature = temp))
    fun setSystemPromptPreset(preset: String) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(systemPromptFocus = preset))
    fun setLlmCpuThreads(threads: Int) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(threadCount = threads))
    fun dismissNewModelsNotification() = llmService.dismissNewModelsNotification()
    fun syncModelCatalogFromRemote(forceCheck: Boolean = true) {
        viewModelScope.launch {
            llmService.syncModelCatalogFromRemote(forceCheck)
        }
    }

    // First-Run Wizard & Manueller Start
    val isWizardCompleted: StateFlow<Boolean> = settingsRepo.isWizardCompleted
    fun setWizardCompleted(completed: Boolean) = settingsRepo.setWizardCompleted(completed)
    fun resetWizard() = settingsRepo.resetWizard()

    private val _showManualConfigWizard = MutableStateFlow(false)
    val showManualConfigWizard: StateFlow<Boolean> = _showManualConfigWizard.asStateFlow()

    fun launchSetupWizardManually() {
        _showManualConfigWizard.value = true
    }

    fun dismissManualConfigWizard() {
        _showManualConfigWizard.value = false
    }

    // Optionaler PC- & Desktop-Scanner Assistent
    private val _showPcCompanionWizard = MutableStateFlow(false)
    val showPcCompanionWizard: StateFlow<Boolean> = _showPcCompanionWizard.asStateFlow()

    fun launchPcCompanionWizard() {
        _showPcCompanionWizard.value = true
    }

    fun dismissPcCompanionWizard() {
        _showPcCompanionWizard.value = false
    }

    // Cloud-Sync & AES-256-GCM Zero-Knowledge Verschlüsselung
    val syncState: StateFlow<CloudSyncWorkerService.SyncState> = CloudSyncWorkerService.syncState
    val syncPasswordKey: StateFlow<String> = settingsRepo.syncPasswordKey
    val autoCloudSyncEnabled: StateFlow<Boolean> = settingsRepo.autoCloudSyncEnabled
    val lastSyncTimestamp: StateFlow<Long> = settingsRepo.lastSyncTimestamp
    val cloudSyncConfig: StateFlow<com.example.model.CloudSyncConfig> = settingsRepo.cloudSyncConfig

    fun setSyncPasswordKey(key: String) = settingsRepo.setSyncPasswordKey(key)
    fun setAutoCloudSyncEnabled(enabled: Boolean) = settingsRepo.setAutoCloudSyncEnabled(enabled)
    fun updateCloudSyncConfig(config: com.example.model.CloudSyncConfig) = settingsRepo.updateCloudSyncConfig(config)

    // Passkey & Tresor-Sicherheitsverfahren
    val securityMethod: StateFlow<String> = settingsRepo.securityMethod
    val passkeyName: StateFlow<String> = settingsRepo.passkeyName
    val passkeyCreatedAt: StateFlow<Long> = settingsRepo.passkeyCreatedAt
    fun setSecurityMethod(method: String) = settingsRepo.setSecurityMethod(method)
    fun registerPasskey(name: String) = settingsRepo.registerPasskey(name)

    // USB Backup-Sticks Verwaltung
    val usbBackupDrives: StateFlow<List<com.example.model.UsbBackupDrive>> = settingsRepo.usbBackupDrives
    val usbDrives: StateFlow<List<com.example.model.UsbBackupDrive>> get() = usbBackupDrives
    fun addUsbBackupDrive(name: String, location: String) = settingsRepo.addUsbBackupDrive(name, location)
    fun addUsbDrive(name: String, locationNote: String = "Zuhause") = settingsRepo.addUsbBackupDrive(name, locationNote)
    fun deleteUsbBackupDrive(id: String) = settingsRepo.deleteUsbBackupDrive(id)
    fun removeUsbDrive(id: String) = settingsRepo.deleteUsbBackupDrive(id)
    fun syncUsbDrive(id: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        val count = allDocuments.value.size
        settingsRepo.syncUsbDrive(id, count)
        _pendingUsbSyncWarning.value = null
        onComplete?.invoke(true, "USB-Stick erfolgreich synchronisiert ($count Dokumente gesichert)")
    }

    suspend fun testCloudSync(target: String, onComplete: (Boolean, String) -> Unit) {
        val config = cloudSyncConfig.value
        if (target == "WEBDAV" || target == "NAS" || config.enableWebDavNas) {
            if (config.webDavUrl.isBlank()) {
                onComplete(false, "WebDAV/NAS-URL darf nicht leer sein.")
                return
            }
            val res = WebDavClient.testConnection(config.webDavUrl, config.webDavUsername, config.webDavPassword)
            res.fold(
                onSuccess = { msg ->
                    connectNas(config.nasProtocol, config.nasShareName)
                    onComplete(true, msg)
                },
                onFailure = { err ->
                    onComplete(false, err.localizedMessage ?: "Verbindung zum WebDAV/NAS fehlgeschlagen.")
                }
            )
        } else if (target == "GOOGLE_DRIVE") {
            if (config.googleDriveAccount.isBlank()) {
                onComplete(false, "Google-Konto-E-Mail darf nicht leer sein.")
            } else {
                connectGoogleDrive(config.googleDriveAccount)
                onComplete(true, "Google Drive Ziel-Konto '${config.googleDriveAccount}' konfiguriert. Vor Upload wird Zero-Knowledge AES-256-GCM Verschlüsselung angewendet.")
            }
        } else {
            onComplete(true, "Lokaler Speicher bereit.")
        }
    }

    // Google Drive & NAS Anbindung
    fun connectGoogleDrive(account: String) = settingsRepo.connectGoogleDrive(account)
    fun connectNas(protocol: String, share: String) = settingsRepo.connectNas(protocol, share)

    // Ausstehender USB-Sync Warnhinweis
    private val _pendingUsbSyncWarning = MutableStateFlow<String?>(null)
    val pendingUsbSyncWarning: StateFlow<String?> = _pendingUsbSyncWarning.asStateFlow()
    fun dismissUsbSyncWarning() { _pendingUsbSyncWarning.value = null }
    fun triggerUsbSyncReminderIfNeeded() {
        if (cloudSyncConfig.value.enableManualUsbExport && cloudSyncConfig.value.usbSyncWarningOnScan) {
            _pendingUsbSyncWarning.value = "⚠️ USB-Backup ausstehend: Bitte schließe deine Backup-USB-Sticks per OTG-Adapter an, um die neuen Dokumente zu synchronisieren!"
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val docs = allDocuments.value
            CloudSyncWorkerService.performBackgroundSync(
                context = getApplication(),
                documentDao = documentDao,
                allDocuments = docs,
                userPasswordKey = syncPasswordKey.value,
                config = cloudSyncConfig.value
            )
            settingsRepo.setLastSyncTimestamp(System.currentTimeMillis())
        }
    }

    suspend fun testWebDavConnection(url: String, user: String, pass: String): Result<String> {
        return CloudSyncWorkerService.testWebDavConnection(url, user, pass)
    }

    /**
     * 1-Klick-Sicherung & Export: Erzeugt ein verschlüsseltes Archiv (.enc)
     * Packt alle PDF-Dokumente und die Metadaten-Indexdatei zusammen und
     * verschlüsselt das Gesamtarchiv mit AES-256-GCM (PBKDF2 100.000 Iterationen).
     */
    fun exportEncryptedBackup(onComplete: (java.io.File?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val docs = allDocuments.value
                val backupDir = java.io.File(getApplication<Application>().filesDir, "Backups").apply { mkdirs() }
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val targetEncFile = java.io.File(backupDir, "MyDocAnizer_Backup_$dateStr.enc")

                val exported = com.example.service.DocumentStorageService.createEncryptedBackupArchive(
                    context = getApplication(),
                    docs = docs,
                    outputEncFile = targetEncFile,
                    passwordKey = syncPasswordKey.value
                )
                onComplete(exported, null)
            } catch (e: Exception) {
                onComplete(null, e.localizedMessage ?: "Fehler beim Exportieren")
            }
        }
    }

    /**
     * Wiederherstellung (Disaster Recovery):
     * Liest eine verschlüsselte .enc Archivdatei (z.B. nach Handyverlust auf neuem Gerät)
     * entschlüsselt diese mit dem Master-Passwort und fügt alle Dokumente und Kategorien
     * wieder in die Room-Datenbank ein.
     */
    fun restoreEncryptedBackup(
        sourceUri: android.net.Uri,
        passwordOverride: String? = null,
        onComplete: (Int, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val password = passwordOverride?.ifBlank { null } ?: syncPasswordKey.value
                val tempEncFile = java.io.File(context.cacheDir, "restore_temp_${UUID.randomUUID()}.enc")
                
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tempEncFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Konnte Backup-Datei nicht lesen")

                val result = com.example.service.DocumentStorageService.restoreEncryptedBackupArchive(
                    context = context,
                    inputEncFile = tempEncFile,
                    passwordKey = password
                )

                tempEncFile.delete()

                result.onSuccess { restoredList ->
                    for (doc in restoredList) {
                        documentDao.insertDocument(doc)
                    }
                    if (passwordOverride != null && passwordOverride.isNotBlank()) {
                        setSyncPasswordKey(passwordOverride)
                    }
                    onComplete(restoredList.size, null)
                }.onFailure { err ->
                    onComplete(0, err.localizedMessage ?: "Fehler bei der Entschlüsselung")
                }
            } catch (e: Exception) {
                onComplete(0, e.localizedMessage ?: "Fehler bei der Wiederherstellung")
            }
        }
    }

    // Stapel-Puffer & Post-Processing (Review-Inbox)
    private val _batchQueue = MutableStateFlow<List<com.example.model.BatchDocumentItem>>(emptyList())
    val batchQueue: StateFlow<List<com.example.model.BatchDocumentItem>> = _batchQueue.asStateFlow()

    private val _isProcessingBatchQueue = MutableStateFlow(false)
    val isProcessingBatchQueue: StateFlow<Boolean> = _isProcessingBatchQueue.asStateFlow()

    // Dialog für neuen Regel-Vorschlag, falls kein automatischer Treffer
    private val _pendingRuleSuggestion = MutableStateFlow<com.example.model.PendingRuleSuggestion?>(null)
    val pendingRuleSuggestion: StateFlow<com.example.model.PendingRuleSuggestion?> = _pendingRuleSuggestion.asStateFlow()

    fun clearPendingRuleSuggestion() {
        _pendingRuleSuggestion.value = null
    }

    private val _isBatchModeActive = MutableStateFlow(false)
    val isBatchModeActive: StateFlow<Boolean> = _isBatchModeActive.asStateFlow()

    fun setBatchModeActive(active: Boolean) {
        _isBatchModeActive.value = active
    }

    /**
     * Fügt ein frisch gescanntes Blatt/Dokument in den Stapel-Puffer (Inbox) ein.
     * Bleibt im Status PENDING_PROCESS, damit der Nutzer am Stativ ohne jede Verzögerung
     * Blatt für Blatt auflegen und erfassen kann.
     */
    fun enqueueBatchDocument(bitmap: Bitmap, onEnqueued: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // Temporäres Bild speichern für spätere PDF-Erstellung
            val tempFile = File(context.cacheDir, "batch_${UUID.randomUUID()}.jpg")
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val item = com.example.model.BatchDocumentItem(
                tempImagePaths = listOf(tempFile.absolutePath),
                status = com.example.model.BatchItemStatus.PENDING_PROCESS
            )
            _batchQueue.value = _batchQueue.value + item
            withContext(Dispatchers.Main) { onEnqueued() }
        }
    }

    /**
     * Manuell getriggerte Stapelverarbeitung:
     * Analysiert alle im Puffer liegenden Dokumente mit OCR, prüft die Regeln
     * und zieht bei fehlender Regel den KI-Vorschlag hinzu.
     */
    fun processPendingBatchQueue(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingBatchQueue.value = true
            val itemsToProcess = _batchQueue.value.filter {
                it.status == com.example.model.BatchItemStatus.PENDING_PROCESS ||
                it.status == com.example.model.BatchItemStatus.WAITING_OCR
            }

            var processedCount = 0
            for (item in itemsToProcess) {
                // Status auf WAITING_OCR setzen
                updateBatchItem(item.copy(status = com.example.model.BatchItemStatus.WAITING_OCR))

                val bitmap = item.tempImagePaths.firstOrNull()?.let { path ->
                    try {
                        android.graphics.BitmapFactory.decodeFile(path)
                    } catch (_: Throwable) {
                        null
                    }
                }

                if (bitmap != null) {
                    try {
                        // 1. Echte Offline ML-Kit OCR ausführen
                        val ocrText = OcrService.recognizeText(bitmap, "", "")

                        // 2. Regelwerk VOR KI abgleichen
                        val matchedRule = ruleRepo.matchRule(ocrText)

                        if (matchedRule != null) {
                            // Eindeutiger Regeltreffer! 100% deterministisch ohne KI-Unsicherheit
                            val updated = item.copy(
                                ocrText = ocrText,
                                status = com.example.model.BatchItemStatus.RULE_MATCHED,
                                suggestedTitle = matchedRule.name,
                                suggestedSender = matchedRule.detectedSender,
                                suggestedMainCategoryId = matchedRule.targetMainCategoryId,
                                suggestedSubCategoryId = matchedRule.targetSubCategoryId,
                                suggestedDocType = matchedRule.targetDocType,
                                suggestedTags = matchedRule.targetTags,
                                matchedRuleName = matchedRule.name,
                                aiReasoning = "Exakter Treffer durch Schlagwort-Regel '${matchedRule.name}'"
                            )
                            updateBatchItem(updated)
                        } else {
                            // 3. Fallback: On-Device KI zur Erkennung & Regelvorschlag
                            val aiResult = llmService.classifyDocumentText(ocrText)
                            val updated = item.copy(
                                ocrText = ocrText,
                                status = com.example.model.BatchItemStatus.AI_SUGGESTED,
                                suggestedTitle = aiResult.title,
                                suggestedSender = aiResult.sender,
                                suggestedMainCategoryId = aiResult.mainCategoryId,
                                suggestedSubCategoryId = aiResult.subCategoryId,
                                suggestedDocType = aiResult.docType,
                                suggestedTags = aiResult.tags,
                                aiReasoning = aiResult.explanation
                            )
                            updateBatchItem(updated)
                        }
                        processedCount++
                    } catch (t: Throwable) {
                        Log.e("DocAnizerViewModel", "Fehler bei Stapelverarbeitung von Element ${item.id}: ${t.message}", t)
                        val errorItem = item.copy(
                            status = com.example.model.BatchItemStatus.ERROR,
                            aiReasoning = "Verarbeitung fehlgeschlagen: ${t.localizedMessage ?: "Unbekannter Fehler"}"
                        )
                        updateBatchItem(errorItem)
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    val errorItem = item.copy(
                        status = com.example.model.BatchItemStatus.ERROR,
                        aiReasoning = "Bilddatei konnte nicht geladen oder dekodiert werden"
                    )
                    updateBatchItem(errorItem)
                }
            }

            _isProcessingBatchQueue.value = false
            withContext(Dispatchers.Main) {
                onComplete(processedCount)
            }
        }
    }

    fun updateBatchItem(item: com.example.model.BatchDocumentItem) {
        _batchQueue.value = _batchQueue.value.map { if (it.id == item.id) item else it }
    }

    fun removeBatchItem(itemId: String) {
        val found = _batchQueue.value.find { it.id == itemId }
        found?.tempImagePaths?.forEach { File(it).delete() }
        _batchQueue.value = _batchQueue.value.filter { it.id != itemId }
    }

    fun clearBatchQueue() {
        _batchQueue.value.forEach { item ->
            item.tempImagePaths.forEach { File(it).delete() }
        }
        _batchQueue.value = emptyList()
    }

    /**
     * Speichert ein einzelnes freigegebenes Dokument aus dem Stapel-Puffer final im Archiv
     */
    private suspend fun commitBatchItemInternal(item: com.example.model.BatchDocumentItem): DocumentEntity? {
        val context = getApplication<Application>()
        val bitmaps = item.tempImagePaths.mapNotNull { path ->
            try {
                android.graphics.BitmapFactory.decodeFile(path)
            } catch (_: Throwable) {
                null
            }
        }
        if (bitmaps.isEmpty()) return null

        try {
            val template = templates.value.find {
                it.mainCategoryId == item.suggestedMainCategoryId && it.subCategoryId == item.suggestedSubCategoryId
            } ?: templates.value.firstOrNull() ?: createFolderAndTemplate("Allgemein", "Dokumente", "A01", "B1.01")

            val sender = item.suggestedSender.ifBlank { "Dokument" }
            val title = item.suggestedTitle.ifBlank { template.subCategory }

            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = item.suggestedMainCategoryId.ifBlank { template.mainCategoryId },
                subCategoryId = item.suggestedSubCategoryId.ifBlank { template.subCategoryId },
                useIdPrefixes = pdfSettings.value.useIdPrefixes,
                folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                locationType = pdfSettings.value.baseStorageLocation,
                customPath = pdfSettings.value.customStoragePath
            )

            val fileName = DocumentStorageService.formatFileName(sender, title)
            val targetPdfFile = File(targetDir, fileName)
            val tagsStr = item.suggestedTags.joinToString(", ")

            DocumentStorageService.generatePdf(
                context = context,
                pageBitmaps = bitmaps,
                ocrText = item.ocrText,
                metadataTags = tagsStr,
                docType = item.suggestedDocType.ifBlank { template.defaultDocType },
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                outputFile = targetPdfFile,
                settings = pdfSettings.value
            )

            val docEntity = DocumentEntity(
                title = title,
                sender = sender,
                fileName = fileName,
                filePath = targetPdfFile.absolutePath,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = item.suggestedMainCategoryId.ifBlank { template.mainCategoryId },
                subCategoryId = item.suggestedSubCategoryId.ifBlank { template.subCategoryId },
                docType = item.suggestedDocType.ifBlank { template.defaultDocType },
                tags = tagsStr,
                ocrText = item.ocrText,
                colorMode = if (_isColorMode.value) "COLOR" else "BW",
                pageCount = bitmaps.size,
                fileSizeFormatted = "${targetPdfFile.length() / 1024} KB"
            )
            documentDao.insertDocument(docEntity)
            incrementScannedDocumentCount()

            // Aus dem Puffer entfernen
            removeBatchItem(item.id)
            return docEntity
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    fun commitBatchItem(item: com.example.model.BatchDocumentItem, onFinished: (DocumentEntity) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val docEntity = commitBatchItemInternal(item)
            if (docEntity != null) {
                withContext(Dispatchers.Main) {
                    onFinished(docEntity)
                }
            }
        }
    }

    /**
     * Speichert ALLE Dokumente im Stapel-Puffer nacheinander ab (Bulk-Commit).
     * Sequentielle Abarbeitung verhindert gleichzeitige Bitmap-Ladevorgänge und OOM-Crashes.
     */
    fun commitAllBatchItems(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _batchQueue.value.toList()
            var count = 0
            for (item in list) {
                val doc = commitBatchItemInternal(item)
                if (doc != null) count++
            }
            withContext(Dispatchers.Main) {
                onComplete(count)
            }
        }
    }

    // KI-Regelgenerator: Erstellt DocRule-Vorschläge anhand von Benutzer-Eingaben
    suspend fun generateRulesWithAi(userDescription: String): List<com.example.model.DocRule> {
        return llmService.generateRulesFromPrompt(userDescription)
    }

    suspend fun refineRuleSuggestion(currentRule: com.example.model.DocRule, userFeedbackPrompt: String, ocrText: String): com.example.model.DocRule {
        return llmService.refineRuleSuggestion(currentRule, userFeedbackPrompt, ocrText)
    }

    val modelBenchmarks = com.example.service.AppAuditLogger.modelBenchmarks

    // Dark/Light Theme: "SYSTEM", "DARK", "LIGHT"
    val themeMode: StateFlow<String> = settingsRepo.themeMode

    fun setThemeMode(mode: String) {
        settingsRepo.setThemeMode(mode)
    }

    // Color Skin Farbschema
    val colorSkin: StateFlow<String> = settingsRepo.colorSkin

    fun setColorSkin(skin: String) {
        settingsRepo.setColorSkin(skin)
    }

    // Barrierefreiheit & Sehhilfe (Hoher Kontrast & Große Touch-Targets)
    val highContrastMode: StateFlow<Boolean> = settingsRepo.highContrastMode
    fun setHighContrastMode(enabled: Boolean) = settingsRepo.setHighContrastMode(enabled)

    val largeTouchTargets: StateFlow<Boolean> = settingsRepo.largeTouchTargets
    fun setLargeTouchTargets(enabled: Boolean) = settingsRepo.setLargeTouchTargets(enabled)

    // Dokumente aus Room-Datenbank (Nur aktive, ungelöschte Dokumente)
    val allDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Papierkorb / Mülleimer
    val trashDocuments: StateFlow<List<DocumentEntity>> = documentDao.getTrashDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashCount: StateFlow<Int> = documentDao.getTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Verträge & Fristen (Laufzeiten, Kündigungstermine)
    val contractDeadlines: StateFlow<List<DocumentEntity>> = documentDao.getContractDeadlineDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Papierkorb Retention & Kalender-Einstellungen
    val trashRetentionDays: StateFlow<Int> = settingsRepo.trashRetentionDays
    fun setTrashRetentionDays(days: Int) = settingsRepo.setTrashRetentionDays(days)

    val calendarIntegrationMode: StateFlow<String> = settingsRepo.calendarIntegrationMode
    fun setCalendarIntegrationMode(mode: String) = settingsRepo.setCalendarIntegrationMode(mode)

    val defaultReminderDaysBefore: StateFlow<Int> = settingsRepo.defaultReminderDaysBefore
    fun setDefaultReminderDaysBefore(days: Int) = settingsRepo.setDefaultReminderDaysBefore(days)

    // 3-stufige App-Ansicht: Standard, Erweitert, Experte
    val appViewLevel: StateFlow<com.example.model.AppViewLevel> = settingsRepo.appViewLevel
    val isAdvancedOrExpert: StateFlow<Boolean> = settingsRepo.isAdvancedOrExpert
    val isExpertMode: StateFlow<Boolean> = settingsRepo.isExpertMode
    fun setAppViewLevel(level: com.example.model.AppViewLevel) = settingsRepo.setAppViewLevel(level)
    fun setExpertMode(enabled: Boolean) = settingsRepo.setExpertMode(enabled)

    // Automatische Prüfung des Backup-Synchronisations-Status aller definierten Speicherorte
    private val _isSyncHealthWarningDismissed = MutableStateFlow(false)
    val isSyncHealthWarningDismissed: StateFlow<Boolean> = _isSyncHealthWarningDismissed.asStateFlow()
    fun dismissSyncHealthWarning() { _isSyncHealthWarningDismissed.value = true }
    fun resetSyncHealthWarningDismissal() { _isSyncHealthWarningDismissed.value = false }

    val backupSyncHealth: StateFlow<com.example.model.OverallBackupSyncHealth> = kotlinx.coroutines.flow.combine(
        allDocuments,
        cloudSyncConfig,
        usbBackupDrives,
        lastSyncTimestamp
    ) { docs, config, usbs, lastSync ->
        com.example.model.calculateBackupSyncHealth(
            totalDocumentsCount = docs.size,
            config = config,
            usbDrives = usbs,
            lastSyncTimestamp = lastSync
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.calculateBackupSyncHealth(
            totalDocumentsCount = 0,
            config = cloudSyncConfig.value,
            usbDrives = usbBackupDrives.value,
            lastSyncTimestamp = lastSyncTimestamp.value
        )
    )

    // Speicherplatz-Ausnutzung / Speicher-Verwaltung (Gesamter App-Speicher & Ordner-Größen)
    val storageUsage: StateFlow<com.example.model.StorageUsageInfo> = allDocuments.map { docs ->
        val context = getApplication<Application>()
        var docBytes = 0L
        var totalPages = 0

        val folderMap = mutableMapOf<String, Pair<String, Long>>() // mainCategory -> (mainCategoryId, bytes)
        val folderDocCount = mutableMapOf<String, Int>()

        docs.forEach { doc ->
            totalPages += doc.pageCount
            val f = File(doc.filePath)
            val size = if (f.exists()) f.length() else 0L
            docBytes += size

            val current = folderMap[doc.mainCategory] ?: Pair(doc.mainCategoryId, 0L)
            folderMap[doc.mainCategory] = Pair(current.first.ifBlank { doc.mainCategoryId }, current.second + size)
            folderDocCount[doc.mainCategory] = (folderDocCount[doc.mainCategory] ?: 0) + 1
        }

        // App-Gesamtspeicher (Dateien + Cache + Datenbanken)
        fun calculateDirSize(dir: File): Long {
            var total = 0L
            dir.listFiles()?.forEach { file ->
                total += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
            return total
        }

        val appDirSize = calculateDirSize(context.filesDir) +
                calculateDirSize(context.cacheDir) +
                (context.getExternalFilesDir(null)?.let { calculateDirSize(it) } ?: 0L)

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
                bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
                bytes >= 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }

        val folderUsageList = folderMap.map { (cat, pair) ->
            com.example.model.FolderStorageUsage(
                mainCategory = cat,
                mainCategoryId = pair.first,
                documentCount = folderDocCount[cat] ?: 0,
                totalBytes = pair.second,
                formattedSize = formatBytes(pair.second)
            )
        }.sortedByDescending { it.totalBytes }

        com.example.model.StorageUsageInfo(
            totalAppBytes = appDirSize.coerceAtLeast(docBytes),
            totalDocumentBytes = docBytes,
            totalDocumentCount = docs.size,
            totalPagesCount = totalPages,
            totalAppFormatted = formatBytes(appDirSize.coerceAtLeast(docBytes)),
            folderUsageList = folderUsageList
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.StorageUsageInfo(0L, 0L, 0, 0, "0 KB", emptyList())
    )

    val pdfSettings: StateFlow<PdfSettings> = settingsRepo.pdfSettings
    val scannerSettings: StateFlow<ScannerSettings> = settingsRepo.scannerSettings

    val availableOptimizationTemplates: List<com.example.model.OptimizationTemplate> = com.example.model.PRESET_OPTIMIZATION_TEMPLATES

    val activeOptimizationTemplate: StateFlow<com.example.model.OptimizationTemplate> = pdfSettings.map { settings ->
        com.example.model.PRESET_OPTIMIZATION_TEMPLATES.find { it.id == settings.activeOptimizationTemplateId }
            ?: com.example.model.PRESET_OPTIMIZATION_TEMPLATES.first()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.PRESET_OPTIMIZATION_TEMPLATES.first()
    )

    fun setOptimizationEnabled(enabled: Boolean) {
        settingsRepo.updatePdfSettings(pdfSettings.value.copy(isOptimizationEnabled = enabled))
    }

    fun setOptimizationTemplate(templateId: String) {
        settingsRepo.updatePdfSettings(pdfSettings.value.copy(activeOptimizationTemplateId = templateId))
    }

    fun updateScannerSettings(settings: ScannerSettings) {
        settingsRepo.updateScannerSettings(settings)
    }

    fun setScanMode(mode: String) {
        updateScannerSettings(scannerSettings.value.copy(scanMode = mode))
    }

    fun setTriggerMode(mode: String) {
        updateScannerSettings(scannerSettings.value.copy(triggerMode = mode))
    }

    // Dynamische Vorlagen & Doc-Typen & Ordner-Struktur
    val templates: StateFlow<List<TemplateItem>> = settingsRepo.templates
    val docTypes: StateFlow<List<DocTypeItem>> = settingsRepo.docTypes
    val importTemplates: StateFlow<List<ImportTemplateItem>> = settingsRepo.importTemplates
    val folders: StateFlow<List<com.example.model.FolderCategoryItem>> = settingsRepo.folders

    fun addDocType(docType: DocTypeItem) = settingsRepo.addDocType(docType)
    fun updateDocType(docType: DocTypeItem) = settingsRepo.updateDocType(docType)
    fun deleteDocType(docTypeId: String) = settingsRepo.deleteDocType(docTypeId)

    fun addImportTemplate(item: ImportTemplateItem) = settingsRepo.addImportTemplate(item)
    fun deleteImportTemplate(templateId: String) = settingsRepo.deleteImportTemplate(templateId)

    // Ordner-Verwaltung (Ebene 1 Hauptkategorien & Ebene 2 Unterordner)
    fun addMainFolder(name: String, prefixId: String = "") {
        settingsRepo.addMainFolder(name, prefixId)
    }

    fun addSubFolder(mainFolderIdOrName: String, name: String, prefixId: String = "") {
        settingsRepo.addSubFolder(mainFolderIdOrName, name, prefixId)
    }

    fun updateFolderIconAndLogo(
        mainCatNameOrId: String,
        subCatNameOrId: String? = null,
        iconName: String,
        companyLogo: String,
        customLogoUri: String = ""
    ) {
        settingsRepo.updateFolderIconAndLogo(
            mainFolderId = mainCatNameOrId,
            subFolderId = subCatNameOrId,
            iconName = iconName,
            companyLogo = companyLogo,
            customLogoUri = customLogoUri
        )
    }

    fun updateDocumentIconAndLogo(
        documentId: Long,
        customIcon: String,
        companyLogo: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId) ?: return@launch
            documentDao.updateDocument(doc.copy(customIcon = customIcon, companyLogo = companyLogo))
        }
    }

    fun renameMainFolder(oldName: String, newName: String) {
        val cleanNewName = newName.trim()
        if (cleanNewName.isBlank() || oldName == cleanNewName) return
        settingsRepo.renameMainFolder(oldName, cleanNewName)
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            docs.filter { it.mainCategory.equals(oldName, ignoreCase = true) }.forEach { doc ->
                documentDao.updateDocument(doc.copy(mainCategory = cleanNewName))
            }
        }
    }

    fun renameSubFolder(mainCatName: String, oldSubName: String, newSubName: String) {
        val cleanNewSubName = newSubName.trim()
        if (cleanNewSubName.isBlank() || oldSubName == cleanNewSubName) return
        settingsRepo.renameSubFolder(mainCatName, oldSubName, cleanNewSubName)
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            docs.filter {
                it.mainCategory.equals(mainCatName, ignoreCase = true) &&
                it.subCategory.equals(oldSubName, ignoreCase = true)
            }.forEach { doc ->
                documentDao.updateDocument(doc.copy(subCategory = cleanNewSubName))
            }
        }
    }

    fun deleteMainFolder(mainCatName: String, deleteDocuments: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = docs.filter { it.mainCategory.equals(mainCatName, ignoreCase = true) }
            if (docsToDelete.isNotEmpty() && deleteDocuments) {
                docsToDelete.forEach { doc ->
                    try {
                        val f = File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }
            settingsRepo.deleteMainFolder(mainCatName)
        }
    }

    fun deleteSubFolder(mainCatName: String, subCatName: String, deleteDocuments: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = docs.filter {
                it.mainCategory.equals(mainCatName, ignoreCase = true) &&
                it.subCategory.equals(subCatName, ignoreCase = true)
            }
            if (docsToDelete.isNotEmpty() && deleteDocuments) {
                docsToDelete.forEach { doc ->
                    try {
                        val f = File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }
            settingsRepo.deleteSubFolder(mainCatName, subCatName)
        }
    }

    fun saveFolderStructure(mainCat: String, mainPrefix: String, subCat: String, subPrefix: String) {
        addMainFolder(mainCat, mainPrefix)
        if (subCat.isNotBlank()) {
            addSubFolder(mainCat, subCat, subPrefix)
        }
    }

    // Hardware-Taste Auslöser Event (Lautstärketasten)
    private val _hardwareTriggerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hardwareTriggerEvent: SharedFlow<Unit> = _hardwareTriggerEvent.asSharedFlow()

    fun triggerHardwareCapture() {
        _hardwareTriggerEvent.tryEmit(Unit)
    }

    // Letztes im Bulk-Scan Modus gespeichertes Dokument (für 1.5s Bestätigungs-Overlay)
    private val _lastBulkSavedDoc = MutableStateFlow<DocumentEntity?>(null)
    val lastBulkSavedDoc: StateFlow<DocumentEntity?> = _lastBulkSavedDoc.asStateFlow()

    fun clearLastBulkSavedDoc() {
        _lastBulkSavedDoc.value = null
    }

    // UI-Zustand für den Scan-Vorgang
    private val _selectedTemplate = MutableStateFlow<TemplateItem?>(null)
    val selectedTemplate: StateFlow<TemplateItem?> = _selectedTemplate.asStateFlow()

    // Schiebeschalter für Scan-Modus: true = Farbe, false = S/W
    private val _isColorMode = MutableStateFlow(false)
    val isColorMode: StateFlow<Boolean> = _isColorMode.asStateFlow()

    // Multi-Page Scan: Liste der erfassten Seiten
    private val _capturedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedPages: StateFlow<List<Bitmap>> = _capturedPages.asStateFlow()

    private val _isProcessingScan = MutableStateFlow(false)
    val isProcessingScan: StateFlow<Boolean> = _isProcessingScan.asStateFlow()

    // Logische Explorer-Filterstruktur
    data class ExplorerFilter(
        val query: String = "",
        val sender: String = "",
        val docType: String? = null,
        val tag: String? = null,
        val mainCategory: String? = null,
        val subCategory: String? = null,
        val dateRange: String = "ALL", // "ALL", "TODAY", "WEEK", "MONTH", "YEAR"
        val pageFilter: String = "ALL", // "ALL", "SINGLE", "MULTI", "3_PLUS", "5_PLUS"
        val sortBy: String = "DATE_DESC", // "DATE_DESC", "DATE_ASC", "TITLE_ASC", "TITLE_DESC", "PAGES_DESC"
        val customFieldId: String? = null,
        val customFieldValue: String? = null
    )

    private val _filter = MutableStateFlow(ExplorerFilter())
    val filter: StateFlow<ExplorerFilter> = _filter.asStateFlow()

    private val _isAdvancedSearchVisible = MutableStateFlow(false)
    val isAdvancedSearchVisible: StateFlow<Boolean> = _isAdvancedSearchVisible.asStateFlow()

    val searchQuery: StateFlow<String> = _filter.map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val filterSender: StateFlow<String> = _filter.map { it.sender }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val filterDocType: StateFlow<String?> = _filter.map { it.docType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterTag: StateFlow<String?> = _filter.map { it.tag }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterMainCategory: StateFlow<String?> = _filter.map { it.mainCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterSubCategory: StateFlow<String?> = _filter.map { it.subCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterDateRange: StateFlow<String> = _filter.map { it.dateRange }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALL")
    val filterPageFilter: StateFlow<String> = _filter.map { it.pageFilter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALL")
    val filterSortBy: StateFlow<String> = _filter.map { it.sortBy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DATE_DESC")
    val filterCustomFieldId: StateFlow<String?> = _filter.map { it.customFieldId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterCustomFieldValue: StateFlow<String?> = _filter.map { it.customFieldValue }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Zähler aktiver Filter für Badge auf dem Filter-Button
    val activeFilterCount: StateFlow<Int> = _filter.map { f ->
        var count = 0
        if (f.docType != null) count++
        if (f.mainCategory != null) count++
        if (f.subCategory != null) count++
        if (f.dateRange != "ALL") count++
        if (f.pageFilter != "ALL") count++
        if (f.tag != null) count++
        if (f.sender.isNotBlank()) count++
        if (f.customFieldId != null) count++
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Gefilterte Dokumente für DocAnizer
    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        allDocumentCustomFieldValues,
        _filter
    ) { docs, customVals, f ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()

        docs.filter { doc ->
            // 1. Echte Volltextsuche: Mehrwort-Suche über Titel, OCR-Volltext, Dateiname, Tags, Ordner, Doc-Typ & Absender
            val qTrimmed = f.query.trim()
            val matchesQuery = if (qTrimmed.isBlank()) {
                true
            } else {
                val searchTokens = qTrimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                val searchableHaystack = buildString {
                    append(doc.title)
                    append(" ")
                    append(doc.ocrText)
                    append(" ")
                    append(doc.fileName)
                    append(" ")
                    append(doc.tags)
                    append(" ")
                    append(doc.docType)
                    append(" ")
                    append(doc.mainCategory)
                    append(" ")
                    append(doc.subCategory)
                    append(" ")
                    append(doc.sender)
                }.lowercase()
                searchTokens.all { token -> searchableHaystack.contains(token) }
            }

            // 2. Absender (optional)
            val matchesSender = f.sender.isBlank() || doc.sender.contains(f.sender, ignoreCase = true)

            // 3. Doc-Type (z.B. "laufender Vertrag")
            val matchesDocType = when {
                f.docType == null -> true
                f.docType == "__NONE__" -> doc.docType.isBlank()
                else -> doc.docType.equals(f.docType, ignoreCase = true) || doc.docType.contains(f.docType, ignoreCase = true)
            }

            // 4. Tags
            val matchesTag = f.tag == null || doc.tags.contains(f.tag, ignoreCase = true)

            // 5. Ordner Ebene 1 (Hauptkategorie)
            val matchesMainCat = f.mainCategory == null || doc.mainCategory.equals(f.mainCategory, ignoreCase = true)

            // 6. Ordner Ebene 2 (Unterordner)
            val matchesSubCat = f.subCategory == null || doc.subCategory.equals(f.subCategory, ignoreCase = true)

            // 7. Zeitraum
            val matchesDate = when (f.dateRange) {
                "TODAY" -> {
                    cal.timeInMillis = now
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    doc.createdAt >= cal.timeInMillis
                }
                "WEEK" -> {
                    doc.createdAt >= (now - 7L * 24 * 60 * 60 * 1000)
                }
                "MONTH" -> {
                    doc.createdAt >= (now - 30L * 24 * 60 * 60 * 1000)
                }
                "YEAR" -> {
                    doc.createdAt >= (now - 365L * 24 * 60 * 60 * 1000)
                }
                else -> true
            }

            // 8. Seitenzahl (Einzelseiter vs Mehrfachseiten)
            val matchesPageCount = when (f.pageFilter) {
                "SINGLE" -> doc.pageCount == 1
                "MULTI" -> doc.pageCount >= 2
                "3_PLUS" -> doc.pageCount >= 3
                "5_PLUS" -> doc.pageCount >= 5
                else -> true
            }

            // 9. Zusatzfelder Filter
            val matchesCustomField = when {
                f.customFieldId == null -> true
                else -> {
                    val matchingVals = customVals.filter { it.documentId == doc.id && it.customFieldId == f.customFieldId }
                    if (f.customFieldValue.isNullOrBlank()) {
                        matchingVals.isNotEmpty() && matchingVals.any { it.fieldValue.isNotBlank() }
                    } else {
                        matchingVals.any { it.fieldValue.contains(f.customFieldValue, ignoreCase = true) }
                    }
                }
            }

            matchesQuery && matchesSender && matchesDocType && matchesTag && matchesMainCat && matchesSubCat && matchesDate && matchesPageCount && matchesCustomField
        }.let { list ->
            when (f.sortBy) {
                "DATE_ASC" -> list.sortedBy { it.createdAt }
                "TITLE_ASC" -> list.sortedBy { it.title.lowercase() }
                "TITLE_DESC" -> list.sortedByDescending { it.title.lowercase() }
                "PAGES_DESC" -> list.sortedByDescending { it.pageCount }
                else -> list.sortedByDescending { it.createdAt }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Preset Zusatzfelder (Custom Fields) initialisieren falls DB leer ist
        viewModelScope.launch(Dispatchers.IO) {
            val existingFields = customFieldDao.getAllCustomFieldsList()
            if (existingFields.isEmpty()) {
                val presets = listOf(
                    com.example.model.CustomFieldEntity(
                        id = "cf_monthly_amount",
                        name = "Monatliche Kosten",
                        description = "Regelmäßige Fixkosten / Abo-Betrag",
                        type = com.example.model.CustomFieldType.AMOUNT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_contract_partner",
                        name = "Vertragspartner / Firma",
                        description = "Absender oder Anbieter",
                        type = com.example.model.CustomFieldType.TEXT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_customer_number",
                        name = "Kundennummer / Vertrags-ID",
                        description = "Eindeutiges Aktenzeichen",
                        type = com.example.model.CustomFieldType.TEXT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_status",
                        name = "Bearbeitungsstatus",
                        description = "Status im Tresor",
                        type = com.example.model.CustomFieldType.SELECTION,
                        options = "Offen,Bezahlt,Gekündigt,Prüfen,In Bearbeitung",
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_cancellation_notice",
                        name = "Kündigungsfrist",
                        description = "Datum der Kündigungsfrist",
                        type = com.example.model.CustomFieldType.DATE,
                        scope = com.example.model.CustomFieldScope.FOLDER_SPECIFIC,
                        targetMainCategory = "Verträge"
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_tax_relevant",
                        name = "Steuerrelevant",
                        description = "Für Steuererklärung vormerken",
                        type = com.example.model.CustomFieldType.BOOLEAN,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    )
                )
                presets.forEach { customFieldDao.insertCustomField(it) }
            } else {
                // Sicherstellen, dass nur eindeutige Feldbezeichnungen in der DB verbleiben
                val seenNames = mutableSetOf<String>()
                existingFields.forEach { f ->
                    val key = f.name.trim().lowercase()
                    if (seenNames.contains(key)) {
                        customFieldDao.deleteCustomFieldById(f.id)
                    } else {
                        seenNames.add(key)
                    }
                }
            }
        }

        viewModelScope.launch {
            templates.collect { list ->
                if (_selectedTemplate.value == null) {
                    list.firstOrNull()?.let { first ->
                        _selectedTemplate.value = first
                        _isColorMode.value = (first.defaultColorMode == "COLOR")
                    }
                }
            }
        }

        // Sofortige Bereinigung sämtlicher Dummy-Dokumente und Test-PDFs aus der Datenbank
        viewModelScope.launch(Dispatchers.IO) {
            val existing = documentDao.getAllDocuments().first()
            val dummySendersOrFiles = setOf(
                "Hausverwaltung Schmidt & Partner",
                "Vodafone Deutschland",
                "Stadtwerke München",
                "HUK-COBURG",
                "Muster Arbeitgeber GmbH",
                "2024-01-15_Schmidt_Mietvertrag.pdf",
                "2024-02-10_Vodafone_Vertrag.pdf",
                "2024-03-01_Stadtwerke_Abrechnung.pdf",
                "2023-11-20_HUK_KfzPolice.pdf",
                "2024-11-28_Gehaltsabrechnung.pdf"
            )
            existing.filter { doc ->
                doc.filePath.isBlank() ||
                doc.sender in dummySendersOrFiles ||
                doc.fileName in dummySendersOrFiles ||
                doc.tags.contains("Kaution") ||
                doc.tags.contains("Vodafone") ||
                doc.tags.contains("Stadtwerke") ||
                doc.tags.contains("Vollkasko")
            }.forEach {
                documentDao.deleteDocument(it)
            }
        }

        // Automatische Papierkorb-Bereinigung gemäß Aufbewahrungsdauer
        viewModelScope.launch(Dispatchers.IO) {
            cleanupExpiredTrash()
        }
    }

    fun selectTemplate(template: TemplateItem) {
        _selectedTemplate.value = template
        _isColorMode.value = (template.defaultColorMode == "COLOR")
    }

    fun setColorMode(isColor: Boolean) {
        _isColorMode.value = isColor
    }

    // Multi-Page Scan Aktionen
    fun addPage(bitmap: Bitmap) {
        _capturedPages.value = _capturedPages.value + bitmap
    }

    fun removePage(index: Int) {
        val current = _capturedPages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _capturedPages.value = current
        }
    }

    fun clearPages() {
        _capturedPages.value = emptyList()
    }

    fun setSearchQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun toggleAdvancedSearch() {
        _isAdvancedSearchVisible.value = !_isAdvancedSearchVisible.value
    }

    fun setFilterSender(sender: String) {
        _filter.value = _filter.value.copy(sender = sender)
    }

    fun setFilterDocType(type: String?) {
        _filter.value = _filter.value.copy(docType = type)
    }

    fun setFilterTag(tag: String?) {
        _filter.value = _filter.value.copy(tag = tag)
    }

    fun setFilterMainCategory(mainCat: String?) {
        _filter.value = _filter.value.copy(mainCategory = mainCat, subCategory = null)
    }

    fun setFilterSubCategory(subCat: String?) {
        _filter.value = _filter.value.copy(subCategory = subCat)
    }

    fun setFilterDateRange(dateRange: String) {
        _filter.value = _filter.value.copy(dateRange = dateRange)
    }

    fun setFilterPageFilter(pageFilter: String) {
        _filter.value = _filter.value.copy(pageFilter = pageFilter)
    }

    fun setFilterCustomField(fieldId: String?, value: String? = null) {
        _filter.value = _filter.value.copy(customFieldId = fieldId, customFieldValue = value)
    }

    fun setSortBy(sortBy: String) {
        _filter.value = _filter.value.copy(sortBy = sortBy)
    }

    fun resetAllFilters() {
        _filter.value = ExplorerFilter()
    }

    fun resetAdvancedFilters() {
        _filter.value = _filter.value.copy(
            sender = "",
            docType = null,
            tag = null,
            mainCategory = null,
            subCategory = null,
            dateRange = "ALL",
            pageFilter = "ALL",
            customFieldId = null,
            customFieldValue = null
        )
    }

    // Template & Folder Management
    fun createFolderAndTemplate(
        mainCategory: String,
        subCategory: String,
        mainCategoryId: String = "",
        subCategoryId: String = "",
        defaultDocType: String = "",
        defaultSender: String = "",
        defaultTags: List<String> = emptyList(),
        defaultColorMode: String = "BW"
    ): TemplateItem {
        val context = getApplication<Application>()
        // 1. Reale Ordnerstruktur physisch auf der Festplatte / Dateisystem erstellen
        val folder = DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = mainCategory,
            subCategory = subCategory,
            mainCategoryId = mainCategoryId,
            subCategoryId = subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )

        // 2. Template für diesen realen Ordner erstellen
        val template = TemplateItem(
            id = UUID.randomUUID().toString(),
            mainCategory = mainCategory,
            subCategory = subCategory,
            mainCategoryId = mainCategoryId,
            subCategoryId = subCategoryId,
            defaultDocType = defaultDocType,
            defaultSender = defaultSender,
            namingPattern = "YYYY-MM-DD_[Sender]_[Title]",
            defaultTags = defaultTags,
            defaultColorMode = defaultColorMode
        )
        addTemplate(template)
        _selectedTemplate.value = template
        return template
    }

    /**
     * Benennt einen Hauptordner um, aktualisiert alle zugehörigen Dokumente und Vorlagen
     */
    fun renameMainCategory(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val matchingDocs = docs.filter { it.mainCategory == oldName }
            for (doc in matchingDocs) {
                documentDao.updateDocument(doc.copy(mainCategory = newName.trim()))
            }
            val currentTemplates = settingsRepo.templates.value
            currentTemplates.filter { it.mainCategory == oldName }.forEach { tmpl ->
                settingsRepo.updateTemplate(tmpl.copy(mainCategory = newName.trim()))
            }
        }
    }

    /**
     * Benennt einen Unterordner um, aktualisiert alle zugehörigen Dokumente und Vorlagen
     */
    fun renameSubCategory(mainCategory: String, oldSubName: String, newSubName: String) {
        if (oldSubName == newSubName || newSubName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val matchingDocs = docs.filter { it.mainCategory == mainCategory && it.subCategory == oldSubName }
            for (doc in matchingDocs) {
                documentDao.updateDocument(doc.copy(subCategory = newSubName.trim()))
            }
            val currentTemplates = settingsRepo.templates.value
            currentTemplates.filter { it.mainCategory == mainCategory && it.subCategory == oldSubName }.forEach { tmpl ->
                settingsRepo.updateTemplate(tmpl.copy(subCategory = newSubName.trim()))
            }
        }
    }

    /**
     * Löscht einen Ordner oder Unterordner.
     * Falls deleteContainedDocuments = true, werden auch alle darin liegenden Dokumente aus der DB und dem Speicher gelöscht.
     */
    fun deleteFolderStructure(mainCategory: String, subCategory: String? = null, deleteContainedDocuments: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = if (subCategory != null && subCategory.isNotBlank()) {
                docs.filter { it.mainCategory == mainCategory && it.subCategory == subCategory }
            } else {
                docs.filter { it.mainCategory == mainCategory }
            }

            if (deleteContainedDocuments) {
                for (doc in docsToDelete) {
                    try {
                        val f = File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (ignored: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }

            // Zugehörige Vorlagen/Templates entfernen
            val currentTemplates = settingsRepo.templates.value
            val templatesToDelete = if (subCategory != null && subCategory.isNotBlank()) {
                currentTemplates.filter { it.mainCategory == mainCategory && it.subCategory == subCategory }
            } else {
                currentTemplates.filter { it.mainCategory == mainCategory }
            }
            for (t in templatesToDelete) {
                settingsRepo.deleteTemplate(t.id)
            }
        }
    }

    fun addTemplate(template: TemplateItem) {
        val context = getApplication<Application>()
        // Stellt sicher, dass der reale Ordner für das Template existiert
        DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = template.mainCategory,
            subCategory = template.subCategory,
            mainCategoryId = template.mainCategoryId,
            subCategoryId = template.subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )
        settingsRepo.addTemplate(template)
        if (_selectedTemplate.value == null) {
            _selectedTemplate.value = template
        }
    }

    fun updateTemplate(template: TemplateItem) {
        val context = getApplication<Application>()
        DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = template.mainCategory,
            subCategory = template.subCategory,
            mainCategoryId = template.mainCategoryId,
            subCategoryId = template.subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )
        settingsRepo.updateTemplate(template)
        if (_selectedTemplate.value?.id == template.id) {
            _selectedTemplate.value = template
        }
    }

    fun deleteTemplate(templateId: String) {
        settingsRepo.deleteTemplate(templateId)
        if (_selectedTemplate.value?.id == templateId) {
            _selectedTemplate.value = templates.value.firstOrNull { it.id != templateId }
        }
    }

    fun updatePdfSettings(settings: PdfSettings) {
        settingsRepo.updatePdfSettings(settings)
    }

    /**
     * Schließt den Multi-Seiten-Scan ab und erstellt das gemeinsame PDF
     */
    fun finishAndSaveMultiPageScan(
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            var processedPages: List<Bitmap> = emptyList()
            val rawPages = _capturedPages.value
            try {
                val context = getApplication<Application>()
                val template = _selectedTemplate.value 
                    ?: settingsRepo.templates.value.firstOrNull()
                    ?: TemplateItem(
                        id = "default_general",
                        mainCategory = "Allgemein",
                        subCategory = "Dokumente",
                        mainCategoryId = "A01",
                        subCategoryId = "B1.01",
                        defaultDocType = "",
                        defaultSender = "",
                        namingPattern = "YYYY-MM-DD_[Sender]_[Title]",
                        defaultTags = emptyList(),
                        defaultColorMode = "BW"
                    )
                val sender = template.defaultSender.ifBlank { "Unbekannt" }
                val title = template.subCategory.ifBlank { "Dokument" }

                if (rawPages.isEmpty()) {
                    return@launch
                }

                val preferredMode = pdfSettings.value.defaultColorMode
                var anyPageHasColor = false

                processedPages = rawPages.map { bmp ->
                    val (procBmp, isPageColor) = ImageProcessingService.processDocumentAdaptive(bmp, preferredMode)
                    if (isPageColor) anyPageHasColor = true
                    procBmp
                }
                val isColor = anyPageHasColor

                // 2. OCR auf erster Seite (falls vorhanden)
                val ocrText = processedPages.firstOrNull()?.let { firstBmp ->
                    OcrService.recognizeText(firstBmp, sender, title)
                } ?: ""

                // 3. Ordnerstruktur mit optionalem ID-Präfix und gewählter Speicher-Basis
                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                // 4. Dateinamen erstellen
                val fileName = DocumentStorageService.formatFileName(sender, title)
                val targetPdfFile = File(targetDir, fileName)

                // 5. Mehrseitiges PDF erstellen
                val tagsStr = template.defaultTags.joinToString(", ")
                DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = processedPages,
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = template.defaultDocType,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    outputFile = targetPdfFile,
                    settings = pdfSettings.value
                )

                // Fristen-Erkennung ausführen
                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                // 6. In Room-Datenbank speichern
                val entity = DocumentEntity(
                    title = title,
                    sender = sender,
                    fileName = fileName,
                    filePath = targetPdfFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    docType = template.defaultDocType,
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = processedPages.size,
                    fileSizeFormatted = "${(targetPdfFile.length() / 1024).coerceAtLeast(35)} KB",
                    isEncrypted = true,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd
                )

                val newId = documentDao.insertDocument(entity)
                val created = entity.copy(id = newId)

                triggerUsbSyncReminderIfNeeded()
                _capturedPages.value = emptyList()
                withContext(Dispatchers.Main) {
                    onComplete(created)
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler bei finishAndSaveMultiPageScan: ${e.message}", e)
            } finally {
                processedPages.forEach { runCatching { it.recycle() } }
                rawPages.forEach { runCatching { it.recycle() } }
                _isProcessingScan.value = false
            }
        }
    }

    /**
     * Sofortiges Speichern im Bulk / Auto-Scan Modus:
     * Erstellt sofort das PDF in der gewählten Vorlage und meldet Erfolg zurück,
     * damit der Nutzer ohne Zwischenschritte direkt das nächste Dokument scannen kann.
     */
    /**
     * Sofortiges Speichern im Einzel-/Bulk-Scan Modus mit intelligentem Regel-Workflow:
     * 1. OCR ausführen
     * 2. Wenn eine Schlagwort-Regel matcht -> sofortige automatische Zuordnung und Speicherung
     * 3. Wenn keine Regel matcht -> KI-Regelvorschlag anzeigen (Dauerhaft speichern vs. Einmalig anwenden)
     */
    fun saveSinglePageBulk(
        bitmap: Bitmap,
        onComplete: ((DocumentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            try {
                val context = getApplication<Application>()

                val preferredMode = if (scannerSettings.value.scanMode == "AUTO") {
                    pdfSettings.value.defaultColorMode
                } else {
                    if (_isColorMode.value) "COLOR" else "BW"
                }

                val (processed, isColor) = ImageProcessingService.processDocumentAdaptive(
                    src = bitmap,
                    preferredMode = preferredMode
                )
                if (processed != bitmap) {
                    runCatching { bitmap.recycle() }
                }

                // 1. Echte Offline ML-Kit OCR
                val ocrText = OcrService.recognizeText(processed, "", "")

                // 2. Regelwerk VOR KI abgleichen
                val matchedRule = ruleRepo.matchRule(ocrText)

                if (matchedRule != null) {
                    // Fall A: Regel matcht! Vollautomatische Zuordnung und Speicherung
                    saveProcessedBitmapWithRule(
                        bitmap = processed,
                        ocrText = ocrText,
                        rule = matchedRule,
                        isColor = isColor,
                        onComplete = onComplete
                    )
                } else {
                    // Fall B: Keine passende Regel -> Lokale On-Device KI erzeugt Regel-Vorschlag
                    val aiClass = llmService.classifyDocumentText(ocrText)
                    val smartKeywords = if (aiClass.smartKeywords.isNotEmpty()) {
                        aiClass.smartKeywords
                    } else {
                        ocrText.lowercase()
                            .replace(Regex("[^a-zäöüß0-9\\s]"), " ")
                            .split(Regex("\\s+"))
                            .filter { it.length in 5..20 }
                            .distinct()
                            .take(4)
                            .ifEmpty { listOf(aiClass.sender.lowercase()) }
                    }

                    val (suggestedIcon, suggestedLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(aiClass.sender, ocrText, aiClass.mainCategoryId)

                    val suggestedRule = com.example.model.DocRule(
                        name = aiClass.title.ifBlank { "Scan ${aiClass.sender}" },
                        matchKeywords = smartKeywords,
                        targetMainCategoryId = aiClass.mainCategoryId,
                        targetSubCategoryId = aiClass.subCategoryId,
                        targetDocType = aiClass.docType,
                        detectedSender = aiClass.sender,
                        targetTags = aiClass.tags,
                        isEnabled = true,
                        isAiGenerated = true,
                        targetIcon = suggestedIcon,
                        targetLogo = suggestedLogo,
                        targetCustomFields = aiClass.customFields
                    )

                    _isProcessingScan.value = false

                    // Dialog für Nutzer-Entscheidung anzeigen
                    _pendingRuleSuggestion.value = com.example.model.PendingRuleSuggestion(
                        bitmap = processed,
                        ocrText = ocrText,
                        suggestedRule = suggestedRule,
                        onSingleUseOnly = { finalRule ->
                            _pendingRuleSuggestion.value = null
                            saveProcessedBitmapWithRule(
                                bitmap = processed,
                                ocrText = ocrText,
                                rule = finalRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onSaveAsPermanentRule = { finalRule ->
                            _pendingRuleSuggestion.value = null
                            // Regel dauerhaft im Repository speichern
                            addDocRule(finalRule)
                            saveProcessedBitmapWithRule(
                                bitmap = processed,
                                ocrText = ocrText,
                                rule = finalRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onDismiss = {
                            _pendingRuleSuggestion.value = null
                            runCatching { processed.recycle() }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler in saveSinglePageBulk: ${e.message}", e)
                _isProcessingScan.value = false
            }
        }
    }

    private fun saveProcessedBitmapWithRule(
        bitmap: Bitmap,
        ocrText: String,
        rule: com.example.model.DocRule,
        isColor: Boolean,
        onComplete: ((DocumentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            try {
                val context = getApplication<Application>()
                val template = templates.value.find {
                    it.mainCategoryId == rule.targetMainCategoryId && it.subCategoryId == rule.targetSubCategoryId
                } ?: templates.value.firstOrNull() ?: createFolderAndTemplate("Allgemein", "Dokumente", "A01", "B1.01")

                val sender = rule.detectedSender.ifBlank { template.defaultSender.ifBlank { "Dokument" } }
                val title = rule.name.ifBlank { template.subCategory.ifBlank { "Dokument" } }

                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = rule.targetMainCategoryId.ifBlank { template.mainCategoryId },
                    subCategoryId = rule.targetSubCategoryId.ifBlank { template.subCategoryId },
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                val fileName = DocumentStorageService.formatFileName(sender, title)
                val targetPdfFile = File(targetDir, fileName)
                val tagsStr = if (rule.targetTags.isNotEmpty()) rule.targetTags.joinToString(", ") else template.defaultTags.joinToString(", ")

                DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = listOf(bitmap),
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = rule.targetDocType.ifBlank { template.defaultDocType },
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    outputFile = targetPdfFile,
                    settings = pdfSettings.value
                )

                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                val (autoIcon, autoLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(sender, ocrText, template.mainCategory)
                val finalIcon = rule.targetIcon.ifBlank { autoIcon }
                val finalLogo = rule.targetLogo.ifBlank { autoLogo }

                val entity = DocumentEntity(
                    title = title,
                    sender = sender,
                    fileName = fileName,
                    filePath = targetPdfFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = rule.targetMainCategoryId.ifBlank { template.mainCategoryId },
                    subCategoryId = rule.targetSubCategoryId.ifBlank { template.subCategoryId },
                    docType = rule.targetDocType.ifBlank { template.defaultDocType },
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = 1,
                    fileSizeFormatted = "${(targetPdfFile.length() / 1024).coerceAtLeast(35)} KB",
                    isEncrypted = true,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd,
                    customIcon = finalIcon,
                    companyLogo = finalLogo
                )

                val newId = documentDao.insertDocument(entity)
                val created = entity.copy(id = newId)

                // Strukturierte Zusatzfelder in Room Datenbank persistieren
                if (rule.targetCustomFields.isNotEmpty()) {
                    val existingFields = customFieldDao.getAllCustomFieldsList()
                    rule.targetCustomFields.forEach { (fieldName, fieldValue) ->
                        if (fieldName.isNotBlank() && fieldValue.isNotBlank()) {
                            val matchingField = existingFields.find { it.name.equals(fieldName, ignoreCase = true) }
                            val fieldId = matchingField?.id ?: run {
                                val newField = com.example.model.CustomFieldEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = fieldName,
                                    description = "Automatisch von lokaler KI erfasst",
                                    type = when {
                                        fieldValue.contains("€") || fieldValue.matches(Regex(".*[0-9]+[.,][0-9]{2}.*")) -> com.example.model.CustomFieldType.AMOUNT
                                        fieldValue.matches(Regex(".*[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}.*")) -> com.example.model.CustomFieldType.DATE
                                        else -> com.example.model.CustomFieldType.TEXT
                                    },
                                    scope = com.example.model.CustomFieldScope.GLOBAL
                                )
                                customFieldDao.insertCustomField(newField)
                                newField.id
                            }
                            customFieldDao.insertOrUpdateFieldValue(
                                com.example.model.DocumentCustomFieldValueEntity(
                                    documentId = newId,
                                    customFieldId = fieldId,
                                    fieldValue = fieldValue
                                )
                            )
                        }
                    }
                }

                AppAuditLogger.log(
                    category = LogCategory.STORAGE_DMS,
                    tag = "DocumentStorage",
                    message = "Dokument '${created.title}' (${created.sender}) erfolgreich im Tresor abgelegt",
                    details = "ID: $newId | Pfad: ${created.filePath} | Regel: '${rule.name}' | Zusatzfelder: ${rule.targetCustomFields.size}"
                )

                _lastBulkSavedDoc.value = created

                // Automatischer Cloud-/Off-Site-Sync nach jedem Scan (sofern aktiviert und Ziel vorhanden)
                if (autoCloudSyncEnabled.value && (cloudSyncConfig.value.enableGoogleDrive || cloudSyncConfig.value.enableWebDavNas) && cloudSyncConfig.value.syncTrigger == "AUTO_AFTER_SCAN") {
                    triggerCloudSync()
                }

                // Automatisches lokales Spiegeln in das Backup-Verzeichnis
                if (cloudSyncConfig.value.autoMirrorToBackupDir) {
                    launch(Dispatchers.IO) {
                        try {
                            val backupDir = File(context.filesDir, "Backups").apply { mkdirs() }
                            val encTarget = File(backupDir, "${entity.fileName}.enc")
                            DocumentStorageService.encryptFile(targetPdfFile, encTarget, syncPasswordKey.value)
                        } catch (e: Exception) {
                            Log.e("DocAnizerViewModel", "Auto-Mirror fehlgeschlagen: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(created)
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler in saveProcessedBitmapWithRule: ${e.message}", e)
            } finally {
                runCatching { bitmap.recycle() }
                _isProcessingScan.value = false
            }
        }
    }

    fun updateDocumentDocType(documentId: Long, newDocType: String) {
        viewModelScope.launch {
            val doc = documentDao.getDocumentById(documentId)
            if (doc != null) {
                documentDao.updateDocument(doc.copy(docType = newDocType))
            }
        }
    }

    /**
     * Verschiebt ein Dokument in den Mülleimer / Papierkorb (Soft-Delete).
     * Datei bleibt vorerst auf dem Gerät erhalten und kann wiederhergestellt werden.
     */
    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.softDeleteDocument(document.id, System.currentTimeMillis())
        }
    }

    /**
     * Stellt ein zuvor gelöschtes Dokument aus dem Papierkorb wieder her.
     */
    fun restoreDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.restoreDocument(document.id)
        }
    }

    /**
     * Endgültiges Löschen: Löscht die PDF-Datei unwiderruflich von der Festplatte
     * und entfernt den Datensatz aus der Datenbank.
     */
    fun permanentlyDeleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(document.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (ignored: Exception) {}
            documentDao.deleteDocument(document)
        }
    }

    /**
     * Leert den gesamten Papierkorb unwiderruflich.
     */
    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val trashed = documentDao.getTrashDocuments().first()
            for (doc in trashed) {
                try {
                    val file = File(doc.filePath)
                    if (file.exists()) file.delete()
                } catch (ignored: Exception) {}
            }
            documentDao.emptyTrash()
        }
    }

    /**
     * Bereinigt Dokumente im Papierkorb, deren Löschdatum älter als trashRetentionDays ist.
     * 0 = Nie automatisch löschen (nur manuell leeren).
     */
    fun cleanupExpiredTrash() {
        val days = trashRetentionDays.value
        if (days <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            val trashed = documentDao.getTrashDocuments().first()
            val toDelete = trashed.filter { (it.deletedAt ?: 0L) < threshold }
            for (doc in toDelete) {
                try {
                    val file = File(doc.filePath)
                    if (file.exists()) file.delete()
                } catch (ignored: Exception) {}
            }
            documentDao.deleteTrashOlderThan(threshold)
        }
    }

    /**
     * Speichert oder aktualisiert Fristen, Vertragsdaten und Kalender-Erinnerungen.
     */
    fun updateContractReminders(
        documentId: Long,
        contractEndDate: Long?,
        cancellationDeadline: Long?,
        reminderDays: Int,
        hasReminder: Boolean,
        eventType: String,
        reminderNotes: String,
        amount: Double?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId) ?: return@launch
            val updated = doc.copy(
                contractEndDate = contractEndDate,
                cancellationDeadline = cancellationDeadline,
                reminderDaysBefore = reminderDays,
                hasCalendarReminder = hasReminder,
                calendarEventType = eventType,
                reminderNotes = reminderNotes,
                amount = amount
            )
            documentDao.updateDocument(updated)
        }
    }

    // Lokaler KI-Dokumenten-Chat / Assistent (On-Device RAG)
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Hallo! Ich bin dein lokaler myDocAnizer Dokumenten-Assistent. Ich laufe zu 100% offline direkt auf deinem Smartphone.\n\nDu kannst mich alles zu deinen abgelegten Dokumenten, Verträgen, Rechnungen oder anstehenden Kündigungsfristen fragen!",
            isUser = false
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val userMsg = ChatMessage(text = userText, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            val docs = allDocuments.value
            val responseText = llmService.queryDocumentAssistant(userText, docs)
            val assistantMsg = ChatMessage(text = responseText, isUser = false)
            _chatMessages.value = _chatMessages.value + assistantMsg
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Der Chat-Verlauf wurde zurückgesetzt. Wie kann ich dir bei deinen Dokumenten helfen?",
                isUser = false
            )
        )
    }

    /**
     * Importiert ein Bild unabhängig vom Scan-Modus und archiviert es als durchsuchbares PDF
     */
    fun importImageDocument(
        bitmap: Bitmap,
        title: String,
        sender: String,
        template: TemplateItem,
        isColor: Boolean = true,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = title.ifBlank { "Bild-Import" }
            val safeSender = sender.trim()
            val processed = if (!isColor) {
                ImageProcessingService.convertToOptimizedBw(bitmap)
            } else {
                ImageProcessingService.enhanceColor(bitmap)
            }
            try {
                val ocrText = OcrService.recognizeText(processed, safeSender, safeTitle)

                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                val fileName = DocumentStorageService.formatFileName(safeSender, safeTitle)
                val targetPdfFile = File(targetDir, fileName)
                val tagsStr = template.defaultTags.joinToString(", ")

                DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = listOf(processed),
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = template.defaultDocType,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    outputFile = targetPdfFile,
                    settings = pdfSettings.value
                )

                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                val entity = DocumentEntity(
                    title = safeTitle,
                    sender = safeSender,
                    fileName = fileName,
                    filePath = targetPdfFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    docType = template.defaultDocType,
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = 1,
                    fileSizeFormatted = "${(targetPdfFile.length() / 1024).coerceAtLeast(30)} KB",
                    isEncrypted = true,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd
                )

                val newId = documentDao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(entity.copy(id = newId))
                }
            } finally {
                runCatching { processed.recycle() }
                if (bitmap != processed) {
                    runCatching { bitmap.recycle() }
                }
            }
        }
    }

    /**
     * Importiert eine E-Mail (aus .eml Datei oder manueller Eingabe), erzeugt ein formatiertes PDF
     * und indexiert den gesamten E-Mail-Text in der Volltextsuche
     */
    fun importEmailDocument(
        sender: String,
        recipient: String,
        subject: String,
        body: String,
        dateStr: String,
        template: TemplateItem,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = subject.ifBlank { "E-Mail Beleg" }
            val safeSender = sender.trim()
            val ocrText = "Betreff: $safeTitle\nVon: $safeSender\nAn: $recipient\nDatum: $dateStr\n\n$body"

            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = template.mainCategoryId,
                subCategoryId = template.subCategoryId,
                useIdPrefixes = pdfSettings.value.useIdPrefixes,
                folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                locationType = pdfSettings.value.baseStorageLocation,
                customPath = pdfSettings.value.customStoragePath
            )

            val fileName = DocumentStorageService.formatFileName(safeSender, safeTitle)
            val targetPdfFile = File(targetDir, fileName)
            val tagsList = (template.defaultTags + listOf("#email", "#korrespondenz")).distinct()
            val tagsStr = tagsList.joinToString(", ")

            DocumentStorageService.generateEmailPdf(
                context = context,
                sender = safeSender,
                recipient = recipient,
                dateStr = dateStr,
                subject = safeTitle,
                bodyText = body,
                outputFile = targetPdfFile,
                settings = pdfSettings.value
            )

            val entity = DocumentEntity(
                title = safeTitle,
                sender = safeSender,
                fileName = fileName,
                filePath = targetPdfFile.absolutePath,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = template.mainCategoryId,
                subCategoryId = template.subCategoryId,
                docType = if (template.defaultDocType.isNotBlank()) template.defaultDocType else "E-Mail / Schriftverkehr",
                tags = tagsStr,
                ocrText = ocrText,
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${(targetPdfFile.length() / 1024).coerceAtLeast(20)} KB",
                isEncrypted = true,
                isSynced = false
            )

            val newId = documentDao.insertDocument(entity)
            withContext(Dispatchers.Main) {
                onComplete(entity.copy(id = newId))
            }
        }
    }

    /**
     * Führt eine On-Demand OCR-Analyse auf einem Bild durch und liefert den erkannten Text
     */
    suspend fun performOcrAnalysis(bitmap: Bitmap, sender: String = "", title: String = ""): String {
        return withContext(Dispatchers.IO) {
            val processed = ImageProcessingService.convertToOptimizedBw(bitmap)
            try {
                OcrService.recognizeText(processed, sender, title)
            } finally {
                runCatching { processed.recycle() }
            }
        }
    }

    /**
     * Universelle Importfunktion:
     * - Unterstützt Zielformat PDF oder Original-Bild (JPEG/PNG)
     * - Bei Bedarf mit OCR-Text
     * - Speichert im DMS-Ordnersystem und Room DB
     */
    fun importDocumentExtended(
        bitmap: Bitmap?,
        pdfBytes: ByteArray? = null,
        title: String,
        sender: String,
        mainCategory: String,
        subCategory: String,
        mainCategoryId: String = "",
        subCategoryId: String = "",
        docType: String = "",
        tags: List<String> = emptyList(),
        targetFormat: String = "PDF", // "PDF" oder "IMAGE"
        ocrTextProvided: String = "",
        runOcr: Boolean = true,
        isColor: Boolean = true,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = title.trim().ifBlank { "Import_Dokument" }
            val safeSender = sender.trim()
            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = mainCategory,
                subCategory = subCategory,
                mainCategoryId = mainCategoryId,
                subCategoryId = subCategoryId,
                useIdPrefixes = pdfSettings.value.useIdPrefixes,
                folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                locationType = pdfSettings.value.baseStorageLocation,
                customPath = pdfSettings.value.customStoragePath
            )

            var finalOcrText = ocrTextProvided
            val extension = if (targetFormat == "IMAGE") "jpg" else "pdf"
            val fileName = DocumentStorageService.formatFileName(safeSender, safeTitle, extension = extension)
            val targetFile = File(targetDir, fileName)

            var pageCount = 1
            var processedBmpToRecycle: Bitmap? = null

            try {
                if (targetFormat == "IMAGE" && bitmap != null) {
                    // Als Original-Bilddatei im DMS archivieren
                    val processed = if (!isColor) {
                        ImageProcessingService.convertToOptimizedBw(bitmap)
                    } else {
                        bitmap
                    }
                    if (processed != bitmap) processedBmpToRecycle = processed
                    if (finalOcrText.isBlank() && runOcr) {
                        finalOcrText = OcrService.recognizeText(processed, safeSender, safeTitle)
                    }
                    DocumentStorageService.saveImageFile(processed, targetFile)
                    pageCount = 1
                } else if (targetFormat == "PDF" && bitmap != null) {
                    // Bild als durchsuchbares PDF archivieren
                    val processed = if (!isColor) {
                        ImageProcessingService.convertToOptimizedBw(bitmap)
                    } else {
                        ImageProcessingService.enhanceColor(bitmap)
                    }
                    processedBmpToRecycle = processed
                    if (finalOcrText.isBlank() && runOcr) {
                        finalOcrText = OcrService.recognizeText(processed, safeSender, safeTitle)
                    }
                    val tagsStr = tags.joinToString(", ")
                    DocumentStorageService.generatePdf(
                        context = context,
                        pageBitmaps = listOf(processed),
                        ocrText = finalOcrText,
                        metadataTags = tagsStr,
                        docType = docType,
                        mainCategory = mainCategory,
                        subCategory = subCategory,
                        outputFile = targetFile,
                        settings = pdfSettings.value
                    )
                    pageCount = 1
                } else if (pdfBytes != null) {
                    // Reines PDF direkt wegschreiben
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(pdfBytes)
                    pageCount = 1
                }

                val tagsStr = tags.joinToString(", ")
                val fileSizeKb = (targetFile.length() / 1024).coerceAtLeast(15)

                val entity = DocumentEntity(
                    title = safeTitle,
                    sender = safeSender,
                    fileName = fileName,
                    filePath = targetFile.absolutePath,
                    mainCategory = mainCategory,
                    subCategory = subCategory,
                    mainCategoryId = mainCategoryId,
                    subCategoryId = subCategoryId,
                    docType = docType,
                    tags = tagsStr,
                    ocrText = finalOcrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = pageCount,
                    fileSizeFormatted = "$fileSizeKb KB",
                    isEncrypted = true,
                    isSynced = false
                )

                val newId = documentDao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(entity.copy(id = newId))
                }
            } finally {
                processedBmpToRecycle?.let { runCatching { it.recycle() } }
            }
        }
    }
}
