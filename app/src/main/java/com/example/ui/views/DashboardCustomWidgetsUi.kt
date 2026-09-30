package com.example.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ChecklistItem
import com.example.model.CustomDashboardWidget
import com.example.model.CustomDashboardWidgetType
import com.example.model.DashboardCategory
import com.example.model.DashboardTemplateInfo
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.model.STANDARD_DASHBOARD_TEMPLATES
import com.example.ui.DocAnizerViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Wandelt Farb-Skin Namen in Composable UI Color um
 */
fun getWidgetAccentColor(colorSkin: String): Color {
    return when (colorSkin.uppercase()) {
        "BLUE" -> Color(0xFF3B82F6)
        "EMERALD" -> Color(0xFF10B981)
        "AMBER" -> Color(0xFFF59E0B)
        "ROSE" -> Color(0xFFF43F5E)
        "PURPLE" -> Color(0xFF8B5CF6)
        "SLATE" -> Color(0xFF64748B)
        else -> Color(0xFF3B82F6)
    }
}

/**
 * Wandelt Icon-Namen in ImageVector um
 */
fun getWidgetIconVector(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "folder", "folder_open", "folder_special" -> Icons.Default.FolderOpen
        "label", "bookmark" -> Icons.Default.Label
        "analytics", "bar_chart" -> Icons.Default.Analytics
        "edit_note" -> Icons.Default.EditNote
        "checklist", "playlist_add_check" -> Icons.Default.PlaylistAddCheck
        "savings", "account_balance_wallet" -> Icons.Default.Savings
        "shield" -> Icons.Default.Shield
        "star" -> Icons.Default.Star
        "event" -> Icons.Default.Event
        "notifications_active" -> Icons.Default.NotificationsActive
        "history" -> Icons.Default.History
        "pie_chart" -> Icons.Default.PieChart
        "sync_alt" -> Icons.Default.SyncAlt
        "bolt" -> Icons.Default.Tune
        else -> Icons.Default.DashboardCustomize
    }
}

/**
 * Kompakte Zeitraum-Scope Auswahl-Pille für Dashboard Elemente
 */
@Composable
fun ElementPeriodScopeChip(
    currentScope: ElementPeriodScope,
    onScopeChange: (ElementPeriodScope) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = currentScope.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            ElementPeriodScope.values().forEach { scope ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(scope.label, fontWeight = if (scope == currentScope) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            Text(scope.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    },
                    onClick = {
                        onScopeChange(scope)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (scope == currentScope) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Überarbeiteter, hochmoderner Vorlagen-Katalog Dialog für Dashboard-Elemente
 */
@Composable
fun DashboardTemplatesCatalogDialog(
    viewModel: DocAnizerViewModel,
    showQuickScan: Boolean,
    showCashTracker: Boolean,
    showKpi: Boolean,
    showDeadlines: Boolean,
    showFinance: Boolean,
    showReconciliation: Boolean,
    showCategoryDistribution: Boolean,
    showSecurityScore: Boolean,
    showCustomFields: Boolean,
    showRecentDocs: Boolean,
    showFolderShortcuts: Boolean,
    showInboxDocs: Boolean,
    showBudgetWatch: Boolean,
    showQuickActions: Boolean,
    showQuickNote: Boolean,
    elementPeriodScopes: Map<String, ElementPeriodScope>,
    customWidgets: List<CustomDashboardWidget>,
    onOpenCreateCustomWidget: () -> Unit,
    onEditCustomWidget: (CustomDashboardWidget) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val categories = DashboardCategory.values()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // 1. DIALOG HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Vorlagen für Dashboard-Elemente",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Themenbereiche wählen & Zeiträume pro Element steuern",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. THEMENBEREICHE HORIZONTAL CHIPS MIT ANZAHL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEachIndexed { index, category ->
                        val isSelected = selectedCategoryIndex == index
                        val itemCount = if (category == DashboardCategory.CUSTOM) {
                            customWidgets.size
                        } else {
                            STANDARD_DASHBOARD_TEMPLATES.count { it.category == category }
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryIndex = index },
                            label = {
                                Text(
                                    text = "${category.title} ($itemCount)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = getWidgetIconVector(category.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                val currentCategory = categories[selectedCategoryIndex]

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = currentCategory.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // 3. SCROLLBARER INHALTSBEREICH
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentCategory == DashboardCategory.CUSTOM) {
                        // EIGENE ELEMENTE BEREICH
                        Button(
                            onClick = onOpenCreateCustomWidget,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Neues individuelles Element anlegen", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        if (customWidgets.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DashboardCustomize,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Noch keine eigenen Elemente erstellt",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Erstelle z.B. eigene Notizblöcke, To-Do Checklisten, Ordner-Filter oder persönliche Sparziele mit eigenem Zeitraum.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            customWidgets.forEach { customWidget ->
                                CustomElementCatalogCard(
                                    widget = customWidget,
                                    onToggle = { enabled -> viewModel.toggleCustomDashboardWidget(customWidget.id, enabled) },
                                    onScopeChange = { newScope ->
                                        viewModel.updateCustomDashboardWidget(customWidget.copy(periodScope = newScope))
                                    },
                                    onEdit = { onEditCustomWidget(customWidget) },
                                    onDelete = { viewModel.deleteCustomDashboardWidget(customWidget.id) }
                                )
                            }
                        }
                    } else {
                        // STANDARD-VORLAGEN FÜR DEN AUSGEWÄHLTEN THEMENBEREICH
                        val templatesForCategory = STANDARD_DASHBOARD_TEMPLATES.filter { it.category == currentCategory }

                        templatesForCategory.forEach { template ->
                            val isChecked = when (template.id) {
                                "STANDARD_KPI" -> showKpi
                                "STANDARD_RECENT_DOCS" -> showRecentDocs
                                "STANDARD_QUICK_SCAN" -> showQuickScan
                                "STANDARD_FOLDER_SHORTCUTS" -> showFolderShortcuts
                                "STANDARD_INBOX_DOCS" -> showInboxDocs
                                "STANDARD_CUSTOM_FIELDS" -> showCustomFields
                                "STANDARD_FINANCE_CHART" -> showFinance
                                "STANDARD_CASH_TRACKER" -> showCashTracker
                                "STANDARD_RECONCILIATION" -> showReconciliation
                                "STANDARD_CATEGORY_PIE" -> showCategoryDistribution
                                "STANDARD_BUDGET_WATCH" -> showBudgetWatch
                                "STANDARD_DEADLINES_RADAR" -> showDeadlines
                                "STANDARD_DEADLINES_MONTH" -> showDeadlines
                                "STANDARD_SECURITY_SCORE" -> showSecurityScore
                                "STANDARD_QUICK_ACTIONS" -> showQuickActions
                                "STANDARD_QUICK_NOTE" -> showQuickNote
                                else -> false
                            }

                            val currentScope = elementPeriodScopes[template.id] ?: template.defaultScope

                            val onToggle: (Boolean) -> Unit = { enabled ->
                                when (template.id) {
                                    "STANDARD_KPI" -> viewModel.setShowKpiWidgets(enabled)
                                    "STANDARD_RECENT_DOCS" -> viewModel.setShowRecentDocsWidget(enabled)
                                    "STANDARD_QUICK_SCAN" -> viewModel.setShowBelegQuickScanWidget(enabled)
                                    "STANDARD_FOLDER_SHORTCUTS" -> viewModel.setShowFolderShortcutsWidget(enabled)
                                    "STANDARD_INBOX_DOCS" -> viewModel.setShowInboxDocsWidget(enabled)
                                    "STANDARD_CUSTOM_FIELDS" -> viewModel.setShowCustomFieldsWidget(enabled)
                                    "STANDARD_FINANCE_CHART" -> viewModel.setShowFinanceWidget(enabled)
                                    "STANDARD_CASH_TRACKER" -> viewModel.setShowCashTrackerWidget(enabled)
                                    "STANDARD_RECONCILIATION" -> viewModel.setShowReconciliationWidget(enabled)
                                    "STANDARD_CATEGORY_PIE" -> viewModel.setShowCategoryDistributionWidget(enabled)
                                    "STANDARD_BUDGET_WATCH" -> viewModel.setShowBudgetWatchWidget(enabled)
                                    "STANDARD_DEADLINES_RADAR" -> viewModel.setShowDeadlinesWidget(enabled)
                                    "STANDARD_DEADLINES_MONTH" -> viewModel.setShowDeadlinesWidget(enabled)
                                    "STANDARD_SECURITY_SCORE" -> viewModel.setShowSecurityScoreWidget(enabled)
                                    "STANDARD_QUICK_ACTIONS" -> viewModel.setShowQuickActionsWidget(enabled)
                                    "STANDARD_QUICK_NOTE" -> viewModel.setShowQuickNoteWidget(enabled)
                                }
                            }

                            TemplateElementCatalogCard(
                                template = template,
                                isChecked = isChecked,
                                currentScope = currentScope,
                                onScopeChange = { newScope ->
                                    viewModel.setElementPeriodScope(template.id, newScope)
                                },
                                onCheckedChange = onToggle
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. FOOTER BUTTON
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Fertig & Zurück zum Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Platzoptimierte, saubere Karte für eine Dashboard Element Vorlage im Katalog
 */
@Composable
private fun TemplateElementCatalogCard(
    template: DashboardTemplateInfo,
    isChecked: Boolean,
    currentScope: ElementPeriodScope,
    onScopeChange: (ElementPeriodScope) -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getWidgetIconVector(template.iconName),
                            contentDescription = null,
                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = template.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = template.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = template.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Untere Zeile: Zeitraum-Scope Auswahl & Aktiv-Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Zeitraum:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    ElementPeriodScopeChip(
                        currentScope = currentScope,
                        onScopeChange = onScopeChange
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isChecked) "✓ Auf Dashboard" else "✕ Ausgeblendet",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Karte für eigene Dashboard Elemente im Katalog
 */
@Composable
private fun CustomElementCatalogCard(
    widget: CustomDashboardWidget,
    onToggle: (Boolean) -> Unit,
    onScopeChange: (ElementPeriodScope) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = getWidgetAccentColor(widget.colorSkin)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (widget.isEnabled) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (widget.isEnabled) accentColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(accentColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getWidgetIconVector(widget.iconName),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(widget.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${widget.type.label} • ${if (widget.subtitle.isNotBlank()) widget.subtitle else "Individuell"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(checked = widget.isEnabled, onCheckedChange = onToggle)
            }

            // Zeitraum-Auswahl & Aktionen (Kompakt und ohne Zeilenumbrüche)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Zeitraum:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    ElementPeriodScopeChip(
                        currentScope = widget.periodScope,
                        onScopeChange = onScopeChange
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Bearbeiten", fontSize = 11.sp, maxLines = 1)
                    }

                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Löschen", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Dialog zum Erstellen oder Bearbeiten eines individuellen Dashboard Elements
 */
@Composable
fun CreateOrEditCustomWidgetDialog(
    initialWidget: CustomDashboardWidget? = null,
    categories: List<String>,
    onSave: (CustomDashboardWidget) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialWidget?.title ?: "") }
    var subtitle by remember { mutableStateOf(initialWidget?.subtitle ?: "") }
    var selectedType by remember { mutableStateOf(initialWidget?.type ?: CustomDashboardWidgetType.FILTER_DOCUMENTS) }
    var selectedColorSkin by remember { mutableStateOf(initialWidget?.colorSkin ?: "BLUE") }
    var selectedIconName by remember { mutableStateOf(initialWidget?.iconName ?: "folder_open") }
    var selectedPeriodScope by remember { mutableStateOf(initialWidget?.periodScope ?: ElementPeriodScope.ALL) }
    var targetCategory by remember { mutableStateOf(initialWidget?.targetCategory ?: (categories.firstOrNull() ?: "Finanzen")) }
    var targetTag by remember { mutableStateOf(initialWidget?.targetTag ?: "#wichtig") }
    var noteText by remember { mutableStateOf(initialWidget?.noteText ?: "") }
    var targetAmountText by remember { mutableStateOf(if ((initialWidget?.targetAmount ?: 0.0) > 0) initialWidget?.targetAmount.toString() else "500") }

    val isEditing = initialWidget != null

    val colorOptions = listOf(
        "BLUE" to "Blau",
        "EMERALD" to "Smaragd",
        "AMBER" to "Bernstein",
        "ROSE" to "Rose",
        "PURPLE" to "Violett",
        "SLATE" to "Schiefer"
    )

    val iconOptions = listOf(
        "folder_open" to "Ordner",
        "label" to "Tag",
        "analytics" to "Statistik",
        "edit_note" to "Notiz",
        "playlist_add_check" to "Checkliste",
        "savings" to "Sparziel",
        "shield" to "Tresor",
        "star" to "Favorit"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = getWidgetAccentColor(selectedColorSkin)
                )
                Text(
                    text = if (isEditing) "Dashboard Element bearbeiten" else "Neues Dashboard Element erstellen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
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
                    text = "Konfiguriere dein individuelles Element mit Typ, passendem Zeitraum und Farbgebung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. TITEL & UNTERTITEL
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel des Dashboard Elements *") },
                    placeholder = { Text("z.B. Garantiebelege, Notizen, KFZ...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Untertitel / Beschreibung (optional)") },
                    placeholder = { Text("z.B. Schnellzugriff & Übersicht") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. ZEITRAUM-SCOPE DIESES ELEMENTS
                Text("Zeitraum-Scope für dieses Element:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ElementPeriodScope.values().forEach { scope ->
                        FilterChip(
                            selected = selectedPeriodScope == scope,
                            onClick = { selectedPeriodScope = scope },
                            label = { Text(scope.shortLabel, fontSize = 12.sp) }
                        )
                    }
                }

                // 3. ELEMENT-TYP WÄHLEN
                Text("Element-Typ:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomDashboardWidgetType.values().forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedType = type
                                    selectedIconName = type.defaultIcon
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = getWidgetIconVector(type.defaultIcon),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(type.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                                    Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // 4. TYP-SPEZIFISCHE PARAMETER
                when (selectedType) {
                    CustomDashboardWidgetType.FILTER_DOCUMENTS, CustomDashboardWidgetType.STAT_COUNTER -> {
                        Text("Zielkategorie / Ordner:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val allCats = if (categories.isEmpty()) listOf("Finanzen", "Verträge", "Belege", "Versicherungen", "Allgemein") else categories
                            allCats.forEach { cat ->
                                FilterChip(
                                    selected = targetCategory == cat,
                                    onClick = { targetCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                    CustomDashboardWidgetType.TAG_FILTER -> {
                        OutlinedTextField(
                            value = targetTag,
                            onValueChange = { targetTag = it },
                            label = { Text("Filter-Schlagwort / Tag") },
                            placeholder = { Text("#garantie oder #steuer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CustomDashboardWidgetType.NOTE_MEMO -> {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("Notiztext (direkt bearbeitbar)") },
                            placeholder = { Text("Wichtige Notizen hier eintragen...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    CustomDashboardWidgetType.CHECKLIST -> {
                        Text(
                            text = "Eine Standard-Checkliste wird erstellt und kann direkt auf dem Dashboard interaktiv abgehakt & ergänzt werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    CustomDashboardWidgetType.BUDGET_GOAL -> {
                        OutlinedTextField(
                            value = targetAmountText,
                            onValueChange = { targetAmountText = it },
                            label = { Text("Ziel- / Monatsbudget (€)") },
                            placeholder = { Text("500.00") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider()

                // 5. FARBSCHEMA WÄHLEN
                Text("Farbschema:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    colorOptions.forEach { (code, label) ->
                        val isSelected = selectedColorSkin == code
                        val col = getWidgetAccentColor(code)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedColorSkin = code },
                            label = { Text(label, fontSize = 11.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(col, CircleShape)
                                )
                            }
                        )
                    }
                }

                // 6. ICON WÄHLEN
                Text("Icon-Symbol:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iconOptions.forEach { (iconKey, iconLabel) ->
                        val isSelected = selectedIconName == iconKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedIconName = iconKey },
                            label = { Text(iconLabel, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getWidgetIconVector(iconKey),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        title = when (selectedType) {
                            CustomDashboardWidgetType.FILTER_DOCUMENTS -> "$targetCategory Dokumente"
                            CustomDashboardWidgetType.TAG_FILTER -> "$targetTag Belege"
                            CustomDashboardWidgetType.STAT_COUNTER -> "$targetCategory Zähler"
                            CustomDashboardWidgetType.NOTE_MEMO -> "Persönliche Notiz"
                            CustomDashboardWidgetType.CHECKLIST -> "Monats-Checkliste"
                            CustomDashboardWidgetType.BUDGET_GOAL -> "Budget-Ziel"
                        }
                    }

                    val initialItems = if (selectedType == CustomDashboardWidgetType.CHECKLIST && initialWidget?.checklistItems.isNullOrEmpty()) {
                        listOf(
                            ChecklistItem(text = "Monatsbelege erfassen & ablegen", isDone = false),
                            ChecklistItem(text = "Kontoauszug prüfen", isDone = false),
                            ChecklistItem(text = "Fristen kontrollieren", isDone = false)
                        )
                    } else {
                        initialWidget?.checklistItems ?: emptyList()
                    }

                    val targetAmt = targetAmountText.toDoubleOrNull() ?: 500.0

                    val widget = CustomDashboardWidget(
                        id = initialWidget?.id ?: java.util.UUID.randomUUID().toString(),
                        title = title.trim(),
                        subtitle = subtitle.trim(),
                        type = selectedType,
                        iconName = selectedIconName,
                        colorSkin = selectedColorSkin,
                        periodScope = selectedPeriodScope,
                        targetCategory = targetCategory,
                        targetTag = targetTag,
                        noteText = noteText,
                        checklistItems = initialItems,
                        targetAmount = targetAmt,
                        isEnabled = initialWidget?.isEnabled ?: true,
                        orderIndex = initialWidget?.orderIndex ?: 0,
                        createdAt = initialWidget?.createdAt ?: System.currentTimeMillis()
                    )

                    onSave(widget)
                },
                colors = ButtonDefaults.buttonColors(containerColor = getWidgetAccentColor(selectedColorSkin))
            ) {
                Text(if (isEditing) "Speichern" else "Element anlegen", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Reorder- und Aktions-Leiste im Bearbeitungsmodus jedes Dashboard Elements
 */
@Composable
fun DashboardElementEditBar(
    title: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onConfigure != null) {
                    IconButton(onClick = onConfigure, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Konfigurieren", modifier = Modifier.size(14.dp), tint = accentColor)
                    }
                }

                IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Nach oben verschieben", modifier = Modifier.size(14.dp), tint = accentColor)
                }

                IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Nach unten verschieben", modifier = Modifier.size(14.dp), tint = accentColor)
                }

                IconButton(onClick = onHide, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Ausblenden", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Composable zur Darstellung eines individuellen Dashboard Elements
 */
@Composable
fun RenderCustomDashboardElementCard(
    widget: CustomDashboardWidget,
    allDocuments: List<DocumentEntity>,
    isEditMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit,
    onScopeChange: (ElementPeriodScope) -> Unit,
    onUpdateChecklist: (List<ChecklistItem>) -> Unit,
    onUpdateNote: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = getWidgetAccentColor(widget.colorSkin)
    val currFmt = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    // Dokumente nach dem element-spezifischen Zeitraum filtern!
    val scopedDocs = remember(allDocuments, widget.periodScope) {
        widget.periodScope.filterDocuments(allDocuments)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column {
            if (isEditMode) {
                DashboardElementEditBar(
                    title = "Eigenes Element: ${widget.title}",
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onHide = onHide,
                    onConfigure = onEdit,
                    accentColor = accentColor
                )
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header mit Zeitraum-Chip
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
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(accentColor.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getWidgetIconVector(widget.iconName),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = widget.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (widget.subtitle.isNotBlank()) {
                                Text(
                                    text = widget.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ElementPeriodScopeChip(
                            currentScope = widget.periodScope,
                            onScopeChange = onScopeChange
                        )

                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Inhaltsbereich je nach Typ
                when (widget.type) {
                    CustomDashboardWidgetType.FILTER_DOCUMENTS -> {
                        val matchingDocs = remember(scopedDocs, widget.targetCategory) {
                            scopedDocs.filter {
                                it.mainCategory.equals(widget.targetCategory, ignoreCase = true) ||
                                it.subCategory.equals(widget.targetCategory, ignoreCase = true)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${matchingDocs.size} Beleg(e) in '${widget.targetCategory}'",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = { onNavigateToCategory(widget.targetCategory) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Im Tresor öffnen", fontSize = 11.sp, color = accentColor)
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = accentColor)
                            }
                        }

                        if (matchingDocs.isNotEmpty()) {
                            matchingDocs.take(3).forEach { doc ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(doc.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if ((doc.amount ?: 0.0) > 0) {
                                            Text(currFmt.format(doc.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = accentColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CustomDashboardWidgetType.TAG_FILTER -> {
                        val searchTag = widget.targetTag.removePrefix("#").trim()
                        val matchingDocs = remember(scopedDocs, searchTag) {
                            scopedDocs.filter {
                                it.tags.contains(searchTag, ignoreCase = true) ||
                                it.title.contains(searchTag, ignoreCase = true)
                            }
                        }

                        Text(
                            text = "${matchingDocs.size} Belege mit '${widget.targetTag}' im gewählten Zeitraum",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (matchingDocs.isNotEmpty()) {
                            matchingDocs.take(3).forEach { doc ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(doc.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(doc.mainCategory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    CustomDashboardWidgetType.STAT_COUNTER -> {
                        val matchingDocs = remember(scopedDocs, widget.targetCategory) {
                            scopedDocs.filter { it.mainCategory.equals(widget.targetCategory, ignoreCase = true) }
                        }
                        val sumAmount = matchingDocs.mapNotNull { it.amount }.sum()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Anzahl Belege", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${matchingDocs.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Gesamtsumme", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currFmt.format(sumAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                            }
                        }
                    }

                    CustomDashboardWidgetType.NOTE_MEMO -> {
                        var isEditingText by remember { mutableStateOf(false) }
                        var tempText by remember(widget.noteText) { mutableStateOf(widget.noteText) }

                        if (isEditingText) {
                            OutlinedTextField(
                                value = tempText,
                                onValueChange = { tempText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                placeholder = { Text("Notiz tippen...") }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { isEditingText = false }) { Text("Abbrechen") }
                                Button(
                                    onClick = {
                                        onUpdateNote(tempText)
                                        isEditingText = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text("Speichern")
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEditingText = true }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (widget.noteText.isBlank()) "Tippe hier, um eine Notiz zu erfassen..." else widget.noteText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (widget.noteText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    CustomDashboardWidgetType.CHECKLIST -> {
                        var newTodoText by remember { mutableStateOf("") }
                        val items = widget.checklistItems

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = items.map {
                                                if (it.id == item.id) it.copy(isDone = !it.isDone) else it
                                            }
                                            onUpdateChecklist(updated)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.isDone,
                                        onCheckedChange = { checked ->
                                            val updated = items.map {
                                                if (it.id == item.id) it.copy(isDone = checked) else it
                                            }
                                            onUpdateChecklist(updated)
                                        }
                                    )
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val updated = items.filter { it.id != item.id }
                                            onUpdateChecklist(updated)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Löschen", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = newTodoText,
                                    onValueChange = { newTodoText = it },
                                    placeholder = { Text("Neuer Punkt...", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (newTodoText.isNotBlank()) {
                                            val updated = items + ChecklistItem(text = newTodoText.trim(), isDone = false)
                                            onUpdateChecklist(updated)
                                            newTodoText = ""
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    CustomDashboardWidgetType.BUDGET_GOAL -> {
                        val totalSpent = remember(scopedDocs) {
                            scopedDocs.mapNotNull { it.amount }.sum()
                        }
                        val target = if (widget.targetAmount > 0) widget.targetAmount else 500.0
                        val progress = (totalSpent / target).toFloat().coerceIn(0f, 1f)
                        val isExceeded = totalSpent > target

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ausgegeben: ${currFmt.format(totalSpent)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("Limit: ${currFmt.format(target)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isExceeded) Color(0xFFEF4444) else accentColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            if (isExceeded) {
                                Text(
                                    text = "⚠️ Limit um ${currFmt.format(totalSpent - target)} überschritten!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
