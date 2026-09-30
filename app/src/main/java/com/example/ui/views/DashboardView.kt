package com.example.ui.views

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.CashTransactionEntity
import com.example.model.CustomDashboardWidget
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.widget.BargeldTrackerWidget
import com.example.widget.BelegQuickScanWidget
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class DashboardPeriod(val label: String) {
    ALL("Gesamt"),
    YEAR("Dieses Jahr"),
    MONTH("Dieser Monat")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardView(
    viewModel: DocAnizerViewModel,
    onNavigateToScan: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allDocuments by viewModel.allDocuments.collectAsState()
    val contractDeadlines by viewModel.contractDeadlines.collectAsState()
    val backupSyncHealth by viewModel.backupSyncHealth.collectAsState()
    val enableIncomeExpenseTracking by viewModel.enableIncomeExpenseTracking.collectAsState()
    val enableCashTracker by viewModel.enableCashTracker.collectAsState()
    val enableReceiptExpenses by viewModel.enableReceiptExpenses.collectAsState()
    val enableBankStatementImport by viewModel.enableBankStatementImport.collectAsState()
    val enableMonthlyReconciliation by viewModel.enableMonthlyReconciliation.collectAsState()

    // Standard Widget Sichtbarkeiten
    val showBelegQuickScanWidget by viewModel.showBelegQuickScanWidget.collectAsState()
    val showCashTrackerWidget by viewModel.showCashTrackerWidget.collectAsState()
    val showKpiWidgets by viewModel.showKpiWidgets.collectAsState()
    val showDeadlinesWidget by viewModel.showDeadlinesWidget.collectAsState()
    val showFinanceWidget by viewModel.showFinanceWidget.collectAsState()
    val showReconciliationWidget by viewModel.showReconciliationWidget.collectAsState()
    val showCategoryDistributionWidget by viewModel.showCategoryDistributionWidget.collectAsState()
    val showSecurityScoreWidget by viewModel.showSecurityScoreWidget.collectAsState()
    val showCustomFieldsWidget by viewModel.showCustomFieldsWidget.collectAsState()
    val showRecentDocsWidget by viewModel.showRecentDocsWidget.collectAsState()
    val showFolderShortcutsWidget by viewModel.showFolderShortcutsWidget.collectAsState()
    val showInboxDocsWidget by viewModel.showInboxDocsWidget.collectAsState()
    val showBudgetWatchWidget by viewModel.showBudgetWatchWidget.collectAsState()
    val showQuickActionsWidget by viewModel.showQuickActionsWidget.collectAsState()
    val showQuickNoteWidget by viewModel.showQuickNoteWidget.collectAsState()
    val quickNoteText by viewModel.quickNoteText.collectAsState()

    // Individuelle Dashboard Elemente, Scopes & Sortierung
    val customWidgets by viewModel.customDashboardWidgets.collectAsState()
    val widgetOrder by viewModel.dashboardWidgetOrder.collectAsState()
    val elementPeriodScopes by viewModel.elementPeriodScopes.collectAsState()

    val customFields by viewModel.customFields.collectAsState()
    val allCustomFieldValues by viewModel.allDocumentCustomFieldValues.collectAsState()
    val cashTransactions by viewModel.cashTransactions.collectAsState()

    var isEditMode by remember { mutableStateOf(false) }
    var showTemplatesCatalogDialog by remember { mutableStateOf(false) }
    var showCreateCustomWidgetDialog by remember { mutableStateOf(false) }
    var editingCustomWidget by remember { mutableStateOf<CustomDashboardWidget?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }
    var showBankImportDialog by remember { mutableStateOf(false) }
    var showQuickAddCashExpenseDialog by remember { mutableStateOf(false) }
    var quickExpenseInitialAmount by remember { mutableStateOf(0.0) }

    val pinWidgetToHomescreen: (Class<*>) -> Unit = { providerClass ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = ComponentName(context, providerClass)
                appWidgetManager.requestPinAppWidget(provider, null, null)
                Toast.makeText(context, "Android Homescreen-Widget wird hinzugefügt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Tippe lange auf deinen Homescreen, um das Widget hinzuzufügen", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Tippe lange auf deinen Homescreen, um das Widget hinzuzufügen", Toast.LENGTH_LONG).show()
        }
    }

    val directOpenCashTracker by viewModel.directOpenCashTracker.collectAsState()
    val directOpenReconciliation by viewModel.directOpenReconciliation.collectAsState()

    LaunchedEffect(directOpenCashTracker, directOpenReconciliation) {
        if (directOpenCashTracker || directOpenReconciliation) {
            showReconciliationDialog = true
            viewModel.setDirectOpenCashTracker(false)
            viewModel.setDirectOpenReconciliation(false)
        }
    }

    val now = System.currentTimeMillis()

    // Element-spezifische gefilterte Datenmengen
    val kpiScope = elementPeriodScopes["STANDARD_KPI"] ?: ElementPeriodScope.ALL
    val kpiDocs = remember(allDocuments, kpiScope) { kpiScope.filterDocuments(allDocuments, now) }
    val kpiTotalDocCount = kpiDocs.size
    val kpiTotalAmount = kpiDocs.mapNotNull { it.amount }.sum()

    val financeScope = elementPeriodScopes["STANDARD_FINANCE_CHART"] ?: ElementPeriodScope.YEAR
    val financeDocs = remember(allDocuments, financeScope) { financeScope.filterDocuments(allDocuments, now) }
    val financeTotalAmount = financeDocs.mapNotNull { it.amount }.sum()

    val deadlinesScope = elementPeriodScopes["STANDARD_DEADLINES_RADAR"] ?: ElementPeriodScope.ALL
    val deadlinesDocs = remember(contractDeadlines, deadlinesScope) {
        deadlinesScope.filterDocuments(contractDeadlines, now)
    }
    val activeDeadlines = deadlinesDocs.filter {
        val deadline = it.cancellationDeadline ?: it.contractEndDate
        deadline != null && deadline >= now - 86400000L * 2
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
            maximumFractionDigits = 2
        }
    }

    val categories = remember(allDocuments) {
        allDocuments.map { it.mainCategory.ifBlank { "Allgemein" } }.distinct()
    }

    // Kombinierte Liste aller darstellbaren Elemente in sortierter Reihenfolge
    // Erstelle Liste von IDs: Standard IDs + Custom Widget IDs
    val activeOrderedWidgetIds = remember(
        widgetOrder,
        showKpiWidgets,
        showRecentDocsWidget,
        showQuickActionsWidget,
        showDeadlinesWidget,
        showFinanceWidget,
        showSecurityScoreWidget,
        showBelegQuickScanWidget,
        showCashTrackerWidget,
        showReconciliationWidget,
        showCategoryDistributionWidget,
        showCustomFieldsWidget,
        showFolderShortcutsWidget,
        showInboxDocsWidget,
        showBudgetWatchWidget,
        showQuickNoteWidget,
        customWidgets
    ) {
        val result = mutableListOf<String>()
        val customMap = customWidgets.associateBy { it.id }

        widgetOrder.forEach { id ->
            val isEnabled = when (id) {
                "STANDARD_KPI" -> showKpiWidgets
                "STANDARD_RECENT_DOCS" -> showRecentDocsWidget
                "STANDARD_QUICK_ACTIONS" -> showQuickActionsWidget
                "STANDARD_DEADLINES_RADAR", "STANDARD_DEADLINES_MONTH" -> showDeadlinesWidget
                "STANDARD_FINANCE_CHART" -> showFinanceWidget
                "STANDARD_SECURITY_SCORE" -> showSecurityScoreWidget
                "STANDARD_QUICK_SCAN" -> showBelegQuickScanWidget
                "STANDARD_CASH_TRACKER" -> showCashTrackerWidget
                "STANDARD_RECONCILIATION" -> showReconciliationWidget
                "STANDARD_CATEGORY_PIE" -> showCategoryDistributionWidget
                "STANDARD_CUSTOM_FIELDS" -> showCustomFieldsWidget
                "STANDARD_FOLDER_SHORTCUTS" -> showFolderShortcutsWidget
                "STANDARD_INBOX_DOCS" -> showInboxDocsWidget
                "STANDARD_BUDGET_WATCH" -> showBudgetWatchWidget
                "STANDARD_QUICK_NOTE" -> showQuickNoteWidget
                else -> customMap[id]?.isEnabled == true
            }
            if (isEnabled) {
                result.add(id)
            }
        }

        // Falls noch nicht im Order enthaltene aktive custom widgets vorhanden sind
        customWidgets.filter { it.isEnabled && !result.contains(it.id) }.forEach {
            result.add(it.id)
        }

        result
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_view"),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER: TITEL, VORLAGEN-KATALOG & BEARBEITUNGS-MODUS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Individuelles Cockpit & Vorlagen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Mode Toggle Button
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Tune,
                                contentDescription = if (isEditMode) "Bearbeitung beenden" else "Anordnung anpassen",
                                tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Vorlagen-Katalog & Widgets hinzufügen
                        IconButton(
                            onClick = { showTemplatesCatalogDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = "Vorlagen-Katalog & Elemente",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Export Button
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bericht & Export",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // AKTIONS-BANNER BEI AKTIVEM BEARBEITUNGS-MODUS
                if (isEditMode) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Anordnungs-Modus aktiv",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Verschiebe Elemente mit ▲/▼ oder blende sie mit ✕ aus.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { isEditMode = false },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Fertig", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SCHNELLZUGRIFFE & AKTIONEN
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { showTemplatesCatalogDialog = true },
                        label = { Text("Vorlagen-Katalog", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
                    )

                    AssistChip(
                        onClick = { showCreateCustomWidgetDialog = true },
                        label = { Text("+ Eigenes Element", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${activeOrderedWidgetIds.size} aktiv",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. DYNAMISCHE DARSTELLUNG ALLER AKTIVEN WIDGETS IN NUTZERDEFINIERTER REIHENFOLGE
        if (activeOrderedWidgetIds.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Dein Dashboard ist aktuell leer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wähle fertige Vorlagen aus dem Katalog (Dokumente, Finanzen, Fristen, Sicherheit) oder erstelle eigene persönliche Elemente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { showTemplatesCatalogDialog = true }) {
                                Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vorlagen öffnen")
                            }
                            OutlinedButton(onClick = { showCreateCustomWidgetDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eigenes Element")
                            }
                        }
                    }
                }
            }
        } else {
            activeOrderedWidgetIds.forEach { widgetId ->
                item(key = widgetId) {
                    when (widgetId) {
                        // STANDARD: KPI-KACHELN
                        "STANDARD_KPI" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isEditMode) {
                                    DashboardElementEditBar(
                                        title = "KPI-Zusammenfassung",
                                        onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_KPI") },
                                        onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_KPI") },
                                        onHide = { viewModel.setShowKpiWidgets(false) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "KENNZAHLEN-ÜBERSICHT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    ElementPeriodScopeChip(
                                        currentScope = kpiScope,
                                        onScopeChange = { viewModel.setElementPeriodScope("STANDARD_KPI", it) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    KpiStatCard(
                                        title = "Dokumente",
                                        value = "$kpiTotalDocCount",
                                        subtext = "${kpiDocs.count { it.isSynced }} gesichert",
                                        icon = Icons.Default.Folder,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    KpiStatCard(
                                        title = "Belegsumme",
                                        value = currencyFormat.format(kpiTotalAmount),
                                        subtext = "${kpiDocs.count { (it.amount ?: 0.0) > 0 }} Belege",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        accentColor = Color(0xFF10B981),
                                        modifier = Modifier.weight(1f)
                                    )

                                    KpiStatCard(
                                        title = "Fristen",
                                        value = "${activeDeadlines.size}",
                                        subtext = if (activeDeadlines.isEmpty()) "Keine fällig" else "Aktiv überwacht",
                                        icon = Icons.Default.Event,
                                        accentColor = if (activeDeadlines.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // STANDARD: KÜRZLICH HINZUGEFÜGTE DOKUMENTE
                        "STANDARD_RECENT_DOCS" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Kürzlich hinzugefügt",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_RECENT_DOCS") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_RECENT_DOCS") },
                                            onHide = { viewModel.setShowRecentDocsWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Kürzlich hinzugefügte Dokumente",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            TextButton(
                                                onClick = {
                                                    viewModel.setTargetNavigationTab("DOCANIZER")
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Alle anzeigen", fontSize = 12.sp, maxLines = 1)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        val recentDocs = remember(allDocuments) {
                                            allDocuments.take(4)
                                        }

                                        if (recentDocs.isEmpty()) {
                                            Text(
                                                text = "Noch keine Dokumente vorhanden. Nutze den Scan- oder Import-Button, um dein erstes Dokument abzulegen.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            val df = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                                            recentDocs.forEach { doc ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.setTargetNavigationTab("DOCANIZER")
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = doc.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${doc.mainCategory.ifBlank { "Allgemein" }} • ${df.format(Date(doc.createdAt))}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        if ((doc.amount ?: 0.0) > 0) {
                                                            Text(
                                                                text = currencyFormat.format(doc.amount),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: SCHNELLAKTIONEN
                        "STANDARD_QUICK_ACTIONS" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Schnellaktionen",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_QUICK_ACTIONS") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_QUICK_ACTIONS") },
                                            onHide = { viewModel.setShowQuickActionsWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "SCHNELLAKTIONEN",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = onNavigateToScan,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Scannen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }

                                            FilledTonalButton(
                                                onClick = onNavigateToImport,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Importieren", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: BELEG QUICK-SCAN WIDGET
                        "STANDARD_QUICK_SCAN" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Beleg Quick-Scan",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_QUICK_SCAN") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_QUICK_SCAN") },
                                            onHide = { viewModel.setShowBelegQuickScanWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PhotoCamera,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Beleg Quick-Scan",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Kassenbons & Quittungen sofort digitalisieren",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            AssistChip(
                                                onClick = { pinWidgetToHomescreen(BelegQuickScanWidget::class.java) },
                                                label = { Text("Homescreen", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = onNavigateToScan,
                                                modifier = Modifier.weight(1.3f),
                                                contentPadding = PaddingValues(vertical = 10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Beleg scannen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }

                                            FilledTonalButton(
                                                onClick = onNavigateToImport,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 10.dp)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Import", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: BARGELD-TRACKER
                        "STANDARD_CASH_TRACKER" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Bargeld-Tracker",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_CASH_TRACKER") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_CASH_TRACKER") },
                                            onHide = { viewModel.setShowCashTrackerWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val currentMonthCashExpenses = remember(cashTransactions) {
                                            val cal = Calendar.getInstance()
                                            val curMonth = SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time)
                                            cashTransactions.filter { tx ->
                                                cal.timeInMillis = tx.date
                                                SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time) == curMonth
                                            }.sumOf { it.amount }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Bargeld-Tracker",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Diesen Monat bar ausgegeben: ${currencyFormat.format(currentMonthCashExpenses)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            AssistChip(
                                                onClick = { pinWidgetToHomescreen(BargeldTrackerWidget::class.java) },
                                                label = { Text("Homescreen", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        Text(
                                            text = "Barausgabe schnell erfassen:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(5.0, 10.0, 20.0, 50.0).forEach { amt ->
                                                FilledTonalButton(
                                                    onClick = {
                                                        quickExpenseInitialAmount = amt
                                                        showQuickAddCashExpenseDialog = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("-${amt.toInt()} €", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    quickExpenseInitialAmount = 0.0
                                                    showQuickAddCashExpenseDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("+ Frei", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: FRISTEN- & KÜNDIGUNGS-RADAR
                        "STANDARD_DEADLINES_RADAR", "STANDARD_DEADLINES_MONTH" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Fristen- & Kündigungs-Radar",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp(widgetId) },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown(widgetId) },
                                            onHide = { viewModel.setShowDeadlinesWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Fristen-Radar",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                ElementPeriodScopeChip(
                                                    currentScope = deadlinesScope,
                                                    onScopeChange = { viewModel.setElementPeriodScope(widgetId, it) }
                                                )

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "${activeDeadlines.size} Frist(en)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFF59E0B),
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (activeDeadlines.isEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Keine überfälligen oder anstehenden Kündigungsfristen im gewählten Zeitraum.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                                            activeDeadlines.take(4).forEach { doc ->
                                                val targetDate = doc.cancellationDeadline ?: doc.contractEndDate ?: 0L
                                                val daysLeft = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()

                                                val statusColor = when {
                                                    daysLeft < 0 -> Color(0xFFEF4444)
                                                    daysLeft <= 14 -> Color(0xFFF59E0B)
                                                    else -> Color(0xFF10B981)
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = doc.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${doc.sender} • Fällig: ${dateFormat.format(Date(targetDate))}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }

                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = statusColor.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    daysLeft < 0 -> "Überfällig (${-daysLeft}T)"
                                                                    daysLeft == 0 -> "Heute fällig!"
                                                                    else -> "noch ${daysLeft}T"
                                                                },
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = statusColor,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: FINANZ- & AUSGABEN-VERLAUF
                        "STANDARD_FINANCE_CHART" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Finanz- & Ausgaben-Verlauf",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_FINANCE_CHART") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_FINANCE_CHART") },
                                            onHide = { viewModel.setShowFinanceWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.BarChart,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Finanz- & Ausgaben-Verlauf",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ElementPeriodScopeChip(
                                                    currentScope = financeScope,
                                                    onScopeChange = { viewModel.setElementPeriodScope("STANDARD_FINANCE_CHART", it) }
                                                )

                                                Text(
                                                    text = currencyFormat.format(financeTotalAmount),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }

                                        // Balkendiagramm
                                        val monthlyBuckets = remember(financeDocs) {
                                            calculateMonthlyAmounts(financeDocs)
                                        }

                                        ExpenseBarChart(
                                            buckets = monthlyBuckets,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // STANDARD: AUSGABEN- & BELEG-CHECK (RECONCILIATION)
                        "STANDARD_RECONCILIATION" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Ausgaben- & Beleg-Check",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_RECONCILIATION") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_RECONCILIATION") },
                                            onHide = { viewModel.setShowReconciliationWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SyncAlt,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Ausgaben- & Beleg-Check",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "SELBSTKONTROLLE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Gleicht Bargeld-Abhebungen mit manuell erfassten Bar-Belegen ab. Sieh sofort wohin dein Geld fließt und ob Quittungen fehlen.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Button(
                                            onClick = { showReconciliationDialog = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        ) {
                                            Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Bargeld- & Beleg-Check öffnen", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: 3-2-1 DATENSICHERHEITS-SCORE
                        "STANDARD_SECURITY_SCORE" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "3-2-1 Datensicherheits-Score",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_SECURITY_SCORE") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_SECURITY_SCORE") },
                                            onHide = { viewModel.setShowSecurityScoreWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "3-2-1 Datensicherheits-Score",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            val score = calculateSecurityScore(backupSyncHealth)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (score >= 70) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "$score% GESCHÜTZT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (score >= 70) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        val syncedCount = (backupSyncHealth.totalConfiguredLocations - backupSyncHealth.unsyncedLocationsCount).coerceAtLeast(0)
                                        Text(
                                            text = "Lokale Hardware-Verschlüsselung (AES-256) aktiv. Konfigurierte Backup-Ziele: $syncedCount von ${backupSyncHealth.totalConfiguredLocations.coerceAtLeast(1)} gesichert.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // STANDARD: SCHNELLNOTIZ / MERKZETTEL
                        "STANDARD_QUICK_NOTE" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Schnellnotiz / Merkzettel",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_QUICK_NOTE") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_QUICK_NOTE") },
                                            onHide = { viewModel.setShowQuickNoteWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Text("Persönlicher Merkzettel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        }
                                        OutlinedTextField(
                                            value = quickNoteText,
                                            onValueChange = { viewModel.setQuickNoteText(it) },
                                            placeholder = { Text("Spontane Notizen, Telefonnotizen oder Pendenzen hier eintragen...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2
                                        )
                                    }
                                }
                            }
                        }

                        // STANDARD: KATEGORIEN-VERTEILUNG
                        "STANDARD_CATEGORY_PIE" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Kategorien-Verteilung",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_CATEGORY_PIE") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_CATEGORY_PIE") },
                                            onHide = { viewModel.setShowCategoryDistributionWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val categoryScope = elementPeriodScopes["STANDARD_CATEGORY_PIE"] ?: ElementPeriodScope.ALL
                                        val catDocs = remember(allDocuments, categoryScope) {
                                            categoryScope.filterDocuments(allDocuments, now)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Text("Kategorien-Verteilung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            }

                                            ElementPeriodScopeChip(
                                                currentScope = categoryScope,
                                                onScopeChange = { viewModel.setElementPeriodScope("STANDARD_CATEGORY_PIE", it) }
                                            )
                                        }
                                        val categoryStats = remember(catDocs) {
                                            catDocs.groupBy { it.mainCategory.ifBlank { "Allgemein" } }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
                                        }
                                        if (categoryStats.isNotEmpty()) {
                                            CategoryDistributionBar(
                                                stats = categoryStats,
                                                total = catDocs.size,
                                                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // STANDARD: ZUSATZFELDER & METADATEN
                        "STANDARD_CUSTOM_FIELDS" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column {
                                    if (isEditMode) {
                                        WidgetEditModeHeader(
                                            title = "Zusatzfelder & Metadaten",
                                            onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_CUSTOM_FIELDS") },
                                            onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_CUSTOM_FIELDS") },
                                            onHide = { viewModel.setShowCustomFieldsWidget(false) }
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Text("Zusatzfelder & Metadaten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (customFields.isEmpty()) {
                                            Text("Noch keine Zusatzfelder angelegt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                customFields.take(4).forEach { field ->
                                                    val count = allCustomFieldValues.count { it.customFieldId == field.id && it.fieldValue.isNotBlank() }
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(field.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                            Text("$count Belege", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // BENUTZERDEFINIERTE (VOM NUTZER ERSTELLTE) INDIVIDUELLE ELEMENTE
                        else -> {
                            val customWidget = customWidgets.find { it.id == widgetId }
                            if (customWidget != null) {
                                RenderCustomDashboardElementCard(
                                    widget = customWidget,
                                    allDocuments = allDocuments,
                                    isEditMode = isEditMode,
                                    onMoveUp = { viewModel.moveDashboardWidgetUp(customWidget.id) },
                                    onMoveDown = { viewModel.moveDashboardWidgetDown(customWidget.id) },
                                    onHide = { viewModel.toggleCustomDashboardWidget(customWidget.id, false) },
                                    onEdit = {
                                        editingCustomWidget = customWidget
                                        showCreateCustomWidgetDialog = true
                                    },
                                    onScopeChange = { newScope ->
                                        viewModel.updateCustomDashboardWidget(customWidget.copy(periodScope = newScope))
                                    },
                                    onUpdateChecklist = { updatedList ->
                                        viewModel.updateCustomWidgetChecklist(customWidget.id, updatedList)
                                    },
                                    onUpdateNote = { newNote ->
                                        viewModel.updateCustomWidgetNote(customWidget.id, newNote)
                                    },
                                    onNavigateToCategory = {
                                        viewModel.setTargetNavigationTab("DOCANIZER")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // UNTERER BUTTON: + ELEMENT AUS VORLAGEN ODER SELBST ERSTELLEN
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showTemplatesCatalogDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vorlagen-Katalog", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        editingCustomWidget = null
                        showCreateCustomWidgetDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eigenes Element", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // MODAL: VORLAGEN-KATALOG & THEMENBEREICHE
    if (showTemplatesCatalogDialog) {
        DashboardTemplatesCatalogDialog(
            viewModel = viewModel,
            showQuickScan = showBelegQuickScanWidget,
            showCashTracker = showCashTrackerWidget,
            showKpi = showKpiWidgets,
            showDeadlines = showDeadlinesWidget,
            showFinance = showFinanceWidget,
            showReconciliation = showReconciliationWidget,
            showCategoryDistribution = showCategoryDistributionWidget,
            showSecurityScore = showSecurityScoreWidget,
            showCustomFields = showCustomFieldsWidget,
            showRecentDocs = showRecentDocsWidget,
            showFolderShortcuts = showFolderShortcutsWidget,
            showInboxDocs = showInboxDocsWidget,
            showBudgetWatch = showBudgetWatchWidget,
            showQuickActions = showQuickActionsWidget,
            showQuickNote = showQuickNoteWidget,
            elementPeriodScopes = elementPeriodScopes,
            customWidgets = customWidgets,
            onOpenCreateCustomWidget = {
                editingCustomWidget = null
                showCreateCustomWidgetDialog = true
            },
            onEditCustomWidget = { widgetToEdit ->
                editingCustomWidget = widgetToEdit
                showCreateCustomWidgetDialog = true
            },
            onDismiss = { showTemplatesCatalogDialog = false }
        )
    }

    // MODAL: EIGENES ELEMENT ERSTELLEN / BEARBEITEN
    if (showCreateCustomWidgetDialog) {
        CreateOrEditCustomWidgetDialog(
            initialWidget = editingCustomWidget,
            categories = categories,
            onSave = { savedWidget ->
                if (editingCustomWidget != null) {
                    viewModel.updateCustomDashboardWidget(savedWidget)
                } else {
                    viewModel.addCustomDashboardWidget(savedWidget)
                }
                showCreateCustomWidgetDialog = false
                editingCustomWidget = null
                Toast.makeText(context, "Element '${savedWidget.title}' gespeichert", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCreateCustomWidgetDialog = false
                editingCustomWidget = null
            }
        )
    }

    // MODAL: MULTIFORMAT-EXPORT
    if (showExportDialog) {
        ExportReportDialog(
            context = context,
            allDocuments = allDocuments,
            activeDeadlines = activeDeadlines,
            onDismiss = { showExportDialog = false }
        )
    }

    // MODAL: KASSEN- & BELEG-ABGLEICH (RECONCILIATION)
    if (showReconciliationDialog) {
        ReconciliationDialog(
            viewModel = viewModel,
            onDismiss = { showReconciliationDialog = false },
            onNavigateToScan = onNavigateToScan
        )
    }

    // MODAL: KONTOAUSZUG IMPORTIEREN (CSV / PDF)
    if (showBankImportDialog) {
        BankStatementImportDialog(
            viewModel = viewModel,
            onDismiss = { showBankImportDialog = false }
        )
    }

    // MODAL: BAR-AUSGABE SCHNELL ERFASSEN
    if (showQuickAddCashExpenseDialog) {
        val cal = Calendar.getInstance()
        val curMonth = SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time)
        AddCashTransactionDialog(
            defaultMonthKey = curMonth,
            initialAmount = quickExpenseInitialAmount,
            onDismiss = { showQuickAddCashExpenseDialog = false },
            onSave = { tx ->
                viewModel.addCashTransaction(tx)
                showQuickAddCashExpenseDialog = false
                Toast.makeText(context, "Barausgabe '${tx.title}' erfasst", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun WidgetEditModeHeader(
    title: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    DashboardElementEditBar(
        title = title,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onHide = onHide,
        onConfigure = onConfigure,
        accentColor = accentColor
    )
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpenseBarChart(
    buckets: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val maxVal = (buckets.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    val primaryColor = MaterialTheme.colorScheme.primary
    val barColor = Color(0xFF10B981)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { (_, amount) ->
                val fraction = (amount / maxVal).toFloat().coerceIn(0.05f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(barColor, primaryColor)
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            buckets.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CategoryDistributionBar(
    stats: List<Pair<String, Int>>,
    total: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        stats.forEachIndexed { index, (_, count) ->
            val fraction = if (total > 0) count.toFloat() / total else 0f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(getCategoryColor(index))
                )
            }
        }
    }
}

private fun getCategoryColor(index: Int): Color {
    val palette = listOf(
        Color(0xFF3B82F6), // Blau
        Color(0xFF10B981), // Smaragd
        Color(0xFFF59E0B), // Bernstein
        Color(0xFF8B5CF6), // Violett
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4)  // Cyan
    )
    return palette[index % palette.size]
}

private fun calculateMonthlyAmounts(docs: List<DocumentEntity>): List<Pair<String, Double>> {
    val cal = Calendar.getInstance()
    val monthFormat = SimpleDateFormat("MMM", Locale.GERMANY)
    val result = mutableListOf<Pair<String, Double>>()

    for (i in 5 downTo 0) {
        val targetCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -i)
        }
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetMonth = targetCal.get(Calendar.MONTH)
        val label = monthFormat.format(targetCal.time)

        val sum = docs.filter { doc ->
            cal.timeInMillis = doc.createdAt
            cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) == targetMonth
        }.mapNotNull { it.amount }.sum()

        result.add(Pair(label, sum))
    }
    return result
}

private fun calculateSecurityScore(health: com.example.model.OverallBackupSyncHealth): Int {
    var score = 50 // Basis-Verschlüsselung AES-256 lokal
    if (health.totalConfiguredLocations > 0) score += 20
    if (health.unsyncedLocationsCount == 0 && health.totalConfiguredLocations > 0) score += 20
    if (!health.hasWarnings) score += 10
    return score.coerceIn(10, 100)
}

/**
 * Dialog zum Erstellen von Multiformat-Exporten (PDF, CSV für Steuerberater, JSON)
 */
@Composable
private fun ExportReportDialog(
    context: Context,
    allDocuments: List<DocumentEntity>,
    activeDeadlines: List<DocumentEntity>,
    onDismiss: () -> Unit
) {
    var exportScope by remember { mutableStateOf(ElementPeriodScope.ALL) }
    val scopedDocs = remember(allDocuments, exportScope) {
        exportScope.filterDocuments(allDocuments)
    }
    val scopedDeadlines = remember(activeDeadlines, exportScope) {
        exportScope.filterDocuments(activeDeadlines)
    }
    val scopedTotalAmount = remember(scopedDocs) {
        scopedDocs.mapNotNull { it.amount }.sum()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Bericht & Export", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Zeitraum für Export wählen:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ElementPeriodScope.values().forEach { scope ->
                        FilterChip(
                            selected = exportScope == scope,
                            onClick = { exportScope = scope },
                            label = { Text(scope.shortLabel, fontSize = 11.sp) }
                        )
                    }
                }

                Text(
                    text = "Exportumfang: ${scopedDocs.size} Belege / ${scopedDeadlines.size} Fristen (${exportScope.label})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                HorizontalDivider()

                // 1. PDF-BERICHT
                ExportFormatCard(
                    title = "📄 PDF-Finanz- & Fristenbericht",
                    description = "Druckfähiges Dokument mit Zusammenfassung, Fristen-Tabelle und Belegsummen.",
                    badge = "Ideal für Steuerberater",
                    onClick = {
                        generateAndSharePdfReport(context, scopedDocs, scopedDeadlines, scopedTotalAmount, exportScope)
                        onDismiss()
                    }
                )

                // 2. CSV-TABELLE (EXCEL / DATEV)
                ExportFormatCard(
                    title = "📊 CSV-Tabelle (Excel / DATEV)",
                    description = "Strukturierte Tabellendaten mit Datum, Beträgen, Kategorien und Fristen.",
                    badge = "Buchhaltung",
                    onClick = {
                        generateAndShareCsvReport(context, scopedDocs)
                        onDismiss()
                    }
                )

                // 3. JSON METADATEN
                ExportFormatCard(
                    title = "💾 JSON-Tresor Metadaten",
                    description = "Vollständiger maschinenlesbarer Metadaten-Export aller Dokumente.",
                    badge = "Archivierung",
                    onClick = {
                        generateAndShareJsonReport(context, scopedDocs)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun ExportFormatCard(
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun generateAndSharePdfReport(
    context: Context,
    documents: List<DocumentEntity>,
    deadlines: List<DocumentEntity>,
    totalAmount: Double,
    period: ElementPeriodScope
) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Format
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("myDocAnizer-Mobile • Finanz- & Fristenbericht", 40f, 50f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.DKGRAY
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        canvas.drawText("Erstellt am: ${df.format(Date())} | Zeitraum: ${period.label}", 40f, 70f, paint)

        paint.color = android.graphics.Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 85f, 555f, 85f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("1. Kennzahlen-Übersicht", 40f, 110f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        val currFmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        canvas.drawText("• Erfasste Dokumente: ${documents.size}", 50f, 130f, paint)
        canvas.drawText("• Gesamtsumme Belege/Rechnungen: ${currFmt.format(totalAmount)}", 50f, 145f, paint)
        canvas.drawText("• Aktive Kündigungsfristen: ${deadlines.size}", 50f, 160f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("2. Anstehende Fristen & Kündigungen", 40f, 190f, paint)

        paint.textSize = 9f
        paint.isFakeBoldText = false
        var yPos = 210f
        val dateOnly = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        deadlines.take(12).forEach { doc ->
            val deadlineDate = doc.cancellationDeadline ?: doc.contractEndDate ?: 0L
            val dateStr = if (deadlineDate > 0) dateOnly.format(Date(deadlineDate)) else "Keine Angabe"
            canvas.drawText("• [${dateStr}] ${doc.title.take(35)} (Absender: ${doc.sender.take(20)})", 50f, yPos, paint)
            yPos += 14f
        }

        yPos += 15f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("3. Belege & Ausgaben (Auszug)", 40f, yPos, paint)

        yPos += 20f
        paint.textSize = 9f
        paint.isFakeBoldText = false
        documents.filter { (it.amount ?: 0.0) > 0 }.take(15).forEach { doc ->
            val amtStr = currFmt.format(doc.amount ?: 0.0)
            canvas.drawText("• ${doc.title.take(30)} | ${doc.mainCategory} | Betrag: $amtStr", 50f, yPos, paint)
            yPos += 14f
        }

        paint.textSize = 8f
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("myDocAnizer-Mobile • Vertraulicher Bericht • 100% lokal generiert", 40f, 810f, paint)

        pdfDocument.finishPage(page)

        val exportFile = File(context.cacheDir, "myDocAnizer_Finanzbericht.pdf")
        try {
            FileOutputStream(exportFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
        } finally {
            pdfDocument.close()
        }

        shareFile(context, exportFile, "application/pdf", "myDocAnizer Finanz- & Fristenbericht")
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun generateAndShareCsvReport(context: Context, documents: List<DocumentEntity>) {
    try {
        val csvFile = File(context.cacheDir, "myDocAnizer_Buchhaltung_Export.csv")
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)

        val csvContent = buildString {
            append("sep=;\n")
            append("ID;Datum;Titel;Absender;Hauptkategorie;Unterkategorie;Dokumenttyp;Betrag_EUR;Frist;Notizen\n")
            documents.forEach { doc ->
                val dateStr = df.format(Date(doc.createdAt))
                val deadlineStr = doc.cancellationDeadline?.let { df.format(Date(it)) } ?: ""
                val amtStr = doc.amount?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: ""
                val cleanTitle = doc.title.replace(";", ",")
                val cleanSender = doc.sender.replace(";", ",")
                val cleanCat = doc.mainCategory.replace(";", ",")
                val cleanSubCat = doc.subCategory.replace(";", ",")
                val cleanType = doc.docType.replace(";", ",")
                append("${doc.id};$dateStr;$cleanTitle;$cleanSender;$cleanCat;$cleanSubCat;$cleanType;$amtStr;$deadlineStr;${doc.reminderNotes}\n")
            }
        }

        csvFile.writeText(csvContent, Charsets.UTF_8)
        shareFile(context, csvFile, "text/csv", "myDocAnizer CSV Export")
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim CSV-Export: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun generateAndShareJsonReport(context: Context, documents: List<DocumentEntity>) {
    try {
        val jsonFile = File(context.cacheDir, "myDocAnizer_Metadata_Export.json")
        val jsonArray = org.json.JSONArray()

        documents.forEach { doc ->
            val obj = org.json.JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("sender", doc.sender)
                put("mainCategory", doc.mainCategory)
                put("subCategory", doc.subCategory)
                put("docType", doc.docType)
                put("amount", doc.amount ?: 0.0)
                put("createdAt", doc.createdAt)
                put("cancellationDeadline", doc.cancellationDeadline ?: 0L)
            }
            jsonArray.put(obj)
        }

        jsonFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
        shareFile(context, jsonFile, "application/json", "myDocAnizer JSON Export")
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim JSON-Export: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Keine passende App zum Teilen gefunden", Toast.LENGTH_SHORT).show()
    }
}
