package com.example.ui.views

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.model.DocumentEntity
import com.example.ui.DocAnizerViewModel
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

    // Widget-Sichtbarkeit
    val showKpiWidgets by viewModel.showKpiWidgets.collectAsState()
    val showDeadlinesWidget by viewModel.showDeadlinesWidget.collectAsState()
    val showFinanceWidget by viewModel.showFinanceWidget.collectAsState()
    val showReconciliationWidget by viewModel.showReconciliationWidget.collectAsState()
    val showCategoryDistributionWidget by viewModel.showCategoryDistributionWidget.collectAsState()
    val showSecurityScoreWidget by viewModel.showSecurityScoreWidget.collectAsState()

    var selectedPeriod by remember { mutableStateOf(DashboardPeriod.ALL) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }

    // Filterung der Dokumente nach ausgewähltem Zeitraum
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    val startOfMonth = calendar.apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis

    val startOfYear = calendar.apply {
        set(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    val filteredDocs = remember(allDocuments, selectedPeriod) {
        when (selectedPeriod) {
            DashboardPeriod.ALL -> allDocuments
            DashboardPeriod.YEAR -> allDocuments.filter { it.createdAt >= startOfYear }
            DashboardPeriod.MONTH -> allDocuments.filter { it.createdAt >= startOfMonth }
        }
    }

    // Kennzahlen
    val totalDocCount = filteredDocs.size
    val totalAmount = filteredDocs.mapNotNull { it.amount }.sum()
    val activeDeadlines = contractDeadlines.filter {
        val deadline = it.cancellationDeadline ?: it.contractEndDate
        deadline != null && deadline >= now - 86400000L * 2
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
            maximumFractionDigits = 2
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_view"),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER: TITEL & AKTIONEN
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
                    Column {
                        Text(
                            text = "Dashboard & Analysen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Fristen-Radar, Finanzen & Datensicherheit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showCustomizeDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Widgets anpassen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bericht & Export",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ZEITRAUM-FILTER
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DashboardPeriod.values().forEach { period ->
                        val isSelected = selectedPeriod == period
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // 2. KPI CARDS (SCHNELL-ÜBERSICHT)
        if (showKpiWidgets) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiStatCard(
                        title = "Dokumente",
                        value = "$totalDocCount",
                        subtext = "${filteredDocs.map { it.mainCategory }.distinct().size} Ordner",
                        icon = Icons.Default.Description,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )

                    KpiStatCard(
                        title = "Belege & Summe",
                        value = if (totalAmount > 0) currencyFormat.format(totalAmount) else "0,00 €",
                        subtext = "${filteredDocs.count { (it.amount ?: 0.0) > 0 }} Beträge",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )

                    KpiStatCard(
                        title = "Fristen Radar",
                        value = "${activeDeadlines.size}",
                        subtext = if (activeDeadlines.isEmpty()) "Keine fällig" else "Aktiv überwacht",
                        icon = Icons.Default.Event,
                        accentColor = if (activeDeadlines.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. FRISTEN & WIEDERVORLAGEN RADAR WIDGET
        if (showDeadlinesWidget) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
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
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Fristen- & Kündigungs-Radar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${activeDeadlines.size} Frist(en)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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
                            activeDeadlines.take(5).forEach { doc ->
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

        // 4. FINANZ- & AUSGABEN-MONITOR (DIAGRAMM)
        if (showFinanceWidget) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                            Text(
                                text = currencyFormat.format(totalAmount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        // Visuelles Monats-Balkendiagramm (Canvas)
                        val monthlyBuckets = remember(filteredDocs) {
                            calculateMonthlyAmounts(filteredDocs)
                        }

                        ExpenseBarChart(
                            buckets = monthlyBuckets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        // Top Ausgabenkategorien
                        val categoryExpenses = remember(filteredDocs) {
                            filteredDocs
                                .filter { (it.amount ?: 0.0) > 0 }
                                .groupBy { it.mainCategory.ifBlank { "Allgemein" } }
                                .mapValues { (_, docs) -> docs.sumOf { it.amount ?: 0.0 } }
                                .toList()
                                .sortedByDescending { it.second }
                                .take(3)
                        }

                        if (categoryExpenses.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(
                                text = "Top Kategorien nach Betrag:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            categoryExpenses.forEach { (cat, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = currencyFormat.format(amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. KASSEN- & BELEG-ABGLEICH (RECONCILIATION RADAR)
        if (showReconciliationWidget && enableIncomeExpenseTracking) {
            item {
                val allThreeActive = enableCashTracker && enableReceiptExpenses && enableBankStatementImport

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableMonthlyReconciliation) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (enableMonthlyReconciliation) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
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
                                    text = "Monatlicher Beleg- & Kassenabgleich",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (enableMonthlyReconciliation) "STEUERBERATER-MODUS AKTIV" else "BEREIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = if (enableMonthlyReconciliation) {
                                "Gleicht Bargeld-Abhebungen von Kontoauszügen mit manuell erfassten Bar-Belegen ab. Keine ungeklärten Beleglücken vor der Steuererklärung."
                            } else {
                                "Aktiviere den automatischen Monatsabgleich in den Einstellungen oder im Setup, um Barabhebungen und Beleglücken automatisch abzugleichen."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Status-Anzeige
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Bargeld-Tracker", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (enableCashTracker) "Aktiv" else "Inaktiv", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Belege / OCR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (enableReceiptExpenses) "Aktiv" else "Inaktiv", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Kontoauszüge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (enableBankStatementImport) "Aktiv" else "Inaktiv", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = { showReconciliationDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abgleich öffnen & prüfen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 6. KATEGORIEN-VERTEILUNG (DIAGRAMM)
        if (showCategoryDistributionWidget) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Kategorien- & Ordnerverteilung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val categoryStats = remember(filteredDocs) {
                            filteredDocs
                                .groupBy { it.mainCategory.ifBlank { "Allgemein" } }
                                .mapValues { it.value.size }
                                .toList()
                                .sortedByDescending { it.second }
                        }

                        if (categoryStats.isEmpty()) {
                            Text(
                                text = "Noch keine Dokumente zur Visualisierung vorhanden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            CategoryDistributionBar(
                                stats = categoryStats,
                                total = totalDocCount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                            )

                            // Legende
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                categoryStats.take(4).forEachIndexed { index, (cat, count) ->
                                    val color = getCategoryColor(index)
                                    val percent = if (totalDocCount > 0) (count * 100f / totalDocCount).roundToInt() else 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(color, CircleShape)
                                            )
                                            Text(text = cat, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            text = "$count ($percent%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. 3-2-1 DATENSICHERHEITS-SCORE
        if (showSecurityScoreWidget) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
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
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "3-2-1 Datensicherheits-Score",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
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

        // 8. SCHNELLAKTIONEN (QUICK ACTIONS)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    // MODAL: WIDGETS ANPASSEN
    if (showCustomizeDialog) {
        CustomizeWidgetsDialog(
            viewModel = viewModel,
            onDismiss = { showCustomizeDialog = false }
        )
    }

    // MODAL: MULTIFORMAT-EXPORT
    if (showExportDialog) {
        ExportReportDialog(
            context = context,
            documents = filteredDocs,
            deadlines = activeDeadlines,
            totalAmount = totalAmount,
            period = selectedPeriod,
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
            buckets.forEach { (label, amount) ->
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

    // Letzte 6 Monate
    for (i in 5 downTo 0) {
        cal.time = Date()
        cal.add(Calendar.MONTH, -i)
        val monthYear = cal.get(Calendar.MONTH) to cal.get(Calendar.YEAR)
        val label = monthFormat.format(cal.time)

        val sum = docs.filter { doc ->
            val docCal = Calendar.getInstance().apply { timeInMillis = doc.createdAt }
            docCal.get(Calendar.MONTH) == monthYear.first && docCal.get(Calendar.YEAR) == monthYear.second
        }.sumOf { it.amount ?: 0.0 }

        result.add(label to sum)
    }

    return result
}

private fun calculateSecurityScore(health: com.example.model.OverallBackupSyncHealth): Int {
    var score = 40 // Basis: 40% für lokale Hardware-AES-256 Verschlüsselung
    val syncedCount = (health.totalConfiguredLocations - health.unsyncedLocationsCount).coerceAtLeast(0)
    if (health.totalConfiguredLocations > 0) {
        score += ((syncedCount.toFloat() / health.totalConfiguredLocations) * 50).toInt()
    }
    if (!health.hasWarnings) {
        score += 10
    }
    return score.coerceIn(0, 100)
}

/**
 * Dialog zum Anpassen der Widgets auf dem Dashboard
 */
@Composable
private fun CustomizeWidgetsDialog(
    viewModel: DocAnizerViewModel,
    onDismiss: () -> Unit
) {
    val showKpi by viewModel.showKpiWidgets.collectAsState()
    val showDeadlines by viewModel.showDeadlinesWidget.collectAsState()
    val showFinance by viewModel.showFinanceWidget.collectAsState()
    val showReconciliation by viewModel.showReconciliationWidget.collectAsState()
    val showCategories by viewModel.showCategoryDistributionWidget.collectAsState()
    val showSecurity by viewModel.showSecurityScoreWidget.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Widgets anpassen", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Blende einzelne Widgets ein oder aus, um dein persönliches Dashboard zu gestalten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                WidgetToggleRow("📊 KPI-Zusammenfassung", showKpi) { viewModel.setShowKpiWidgets(it) }
                WidgetToggleRow("⏰ Fristen- & Kündigungsradar", showDeadlines) { viewModel.setShowDeadlinesWidget(it) }
                WidgetToggleRow("💰 Finanz- & Ausgabenmonitor", showFinance) { viewModel.setShowFinanceWidget(it) }
                WidgetToggleRow("🔄 Kassen- & Belegabgleich", showReconciliation) { viewModel.setShowReconciliationWidget(it) }
                WidgetToggleRow("🥧 Kategorien-Verteilung", showCategories) { viewModel.setShowCategoryDistributionWidget(it) }
                WidgetToggleRow("🛡️ 3-2-1 Datensicherheits-Score", showSecurity) { viewModel.setShowSecurityScoreWidget(it) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fertig")
            }
        }
    )
}

@Composable
private fun WidgetToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Dialog zum Erstellen von Multiformat-Exporten (PDF, CSV für Steuerberater, JSON)
 */
@Composable
private fun ExportReportDialog(
    context: Context,
    documents: List<DocumentEntity>,
    deadlines: List<DocumentEntity>,
    totalAmount: Double,
    period: DashboardPeriod,
    onDismiss: () -> Unit
) {
    var isExporting by remember { mutableStateOf(false) }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Wähle das gewünschte Format zur Weitergabe an deinen Steuerberater oder zur Archivierung:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. PDF-BERICHT
                ExportFormatCard(
                    title = "📄 PDF-Finanz- & Fristenbericht",
                    description = "Druckfähiges Dokument mit Zusammenfassung, Fristen-Tabelle und Belegsummen.",
                    badge = "Ideal für Steuerberater",
                    onClick = {
                        isExporting = true
                        generateAndSharePdfReport(context, documents, deadlines, totalAmount, period)
                        isExporting = false
                        onDismiss()
                    }
                )

                // 2. CSV-TABELLE (EXCEL / DATEV)
                ExportFormatCard(
                    title = "📊 CSV-Tabelle (Excel / DATEV)",
                    description = "Strukturierte Tabellendaten mit Datum, Beträgen, Kategorien und Fristen.",
                    badge = "Buchhaltung",
                    onClick = {
                        isExporting = true
                        generateAndShareCsvReport(context, documents)
                        isExporting = false
                        onDismiss()
                    }
                )

                // 3. JSON METADATEN
                ExportFormatCard(
                    title = "💾 JSON-Tresor Metadaten",
                    description = "Vollständiger maschinenlesbarer Metadaten-Export aller Dokumente.",
                    badge = "Archivierung",
                    onClick = {
                        isExporting = true
                        generateAndShareJsonReport(context, documents)
                        isExporting = false
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
    period: DashboardPeriod
) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Format
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Kopfzeile
        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("myDocAnizer-Mobile • Finanz- & Fristenbericht", 40f, 50f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.DKGRAY
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        canvas.drawText("Erstellt am: ${df.format(Date())} | Zeitraum: ${period.label}", 40f, 70f, paint)

        // Trennlinie
        paint.color = android.graphics.Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 85f, 555f, 85f, paint)

        // Übersicht
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

        // Fristen-Tabelle
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

        // Belege-Auszug
        yPos += 15f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("3. Belege & Ausgaben (Auszug für Steuerberater)", 40f, yPos, paint)

        yPos += 20f
        paint.textSize = 9f
        paint.isFakeBoldText = false
        documents.filter { (it.amount ?: 0.0) > 0 }.take(15).forEach { doc ->
            val amtStr = currFmt.format(doc.amount ?: 0.0)
            canvas.drawText("• ${doc.title.take(30)} | ${doc.mainCategory} | Betrag: $amtStr", 50f, yPos, paint)
            yPos += 14f
        }

        // Fußzeile
        paint.textSize = 8f
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("myDocAnizer-Mobile • Vertraulicher Bericht • 100% lokal generiert", 40f, 810f, paint)

        pdfDocument.finishPage(page)

        val exportFile = File(context.cacheDir, "myDocAnizer_Finanzbericht.pdf")
        val outputStream = FileOutputStream(exportFile)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()

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
        shareFile(context, csvFile, "text/csv", "myDocAnizer CSV Export (Steuerberater/Excel)")
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

    context.startActivity(Intent.createChooser(shareIntent, title))
}
