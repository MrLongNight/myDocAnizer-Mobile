package com.example.ui.views

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DocRule
import com.example.service.HuggingFaceModelInfo
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

/**
 * Ansicht zur Verwaltung des Schlagwort-Regelwerks und der lokalen HuggingFace On-Device LLMs.
 * Hier kann der Nutzer die KI beauftragen, neue Regeln vorzuschlagen, diese VORHER
 * prüfen/anpassen und hat 100%ige Transparenz und Kontrolle über das Sortierverhalten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndAiSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val docRules by viewModel.docRules.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isGeneratingAi by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Schlagwort-Regeln, 1 = On-Device KI-Modelle
    var showAiRuleDialog by remember { mutableStateOf(false) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DocRule?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kopfzeile mit Aktionen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Regeln & On-Device KI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (selectedTab == 0) "${docRules.size} aktive Erkennungs-Regeln" else "Lokale HuggingFace LLM-Modelle (100% Offline)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedTab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { showAiRuleDialog = true },
                        modifier = Modifier.testTag("btn_open_ai_rule_generator")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("KI-Assistent", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { showCreateRuleDialog = true },
                        modifier = Modifier.testTag("btn_add_rule_manual")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Neu", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Sub-Reiter
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Schlagwort-Regeln (${docRules.size})") },
                icon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("HuggingFace LLMs") },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Diagnose & Log") },
                icon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
                // TAB 1: Schlagwort-Regeln vor KI
                RulesListContent(
                    rules = docRules,
                    onToggleRule = { id, enabled -> viewModel.toggleDocRule(id, enabled) },
                    onEditRule = { rule -> editingRule = rule },
                    onDeleteRule = { id -> viewModel.deleteDocRule(id) },
                    onOpenAiAssistant = { showAiRuleDialog = true },
                    onAddNewRule = { showCreateRuleDialog = true }
                )
            }
            1 -> {
                // TAB 2: HuggingFace On-Device Modelle
                HuggingFaceModelsContent(
                    viewModel = viewModel
                )
            }
            2 -> {
                // TAB 3: 100% Lokales Diagnose- & Inferenz-Audit-Log
                AuditLogContent(
                    viewModel = viewModel
                )
            }
        }
    }

    // KI Regel-Assistent Dialog
    if (showAiRuleDialog) {
        AiRuleGeneratorDialog(
            viewModel = viewModel,
            isGenerating = isGeneratingAi,
            onDismiss = { showAiRuleDialog = false },
            onAddApprovedRules = { approvedRules ->
                viewModel.addDocRules(approvedRules)
                showAiRuleDialog = false
            }
        )
    }

    // Neue Regel manuell anlegen Dialog
    if (showCreateRuleDialog) {
        EditRuleDialog(
            rule = DocRule(
                name = "",
                matchKeywords = emptyList(),
                excludeKeywords = emptyList(),
                targetMainCategoryId = "A01",
                targetSubCategoryId = "B1.01",
                targetDocType = "Rechnung",
                detectedSender = "",
                targetTags = emptyList()
            ),
            isNew = true,
            onDismiss = { showCreateRuleDialog = false },
            onSave = { newRule ->
                viewModel.addDocRule(newRule)
                showCreateRuleDialog = false
            }
        )
    }

    // Regel bearbeiten Dialog
    editingRule?.let { rule ->
        EditRuleDialog(
            rule = rule,
            isNew = false,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                viewModel.updateDocRule(updated)
                editingRule = null
            }
        )
    }
}

@Composable
fun RulesListContent(
    rules: List<DocRule>,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (DocRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onAddNewRule: () -> Unit = {}
) {
    if (rules.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderSpecial,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Noch keine Regeln definiert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lass dir vom lokalen KI-Assistenten passende Regeln vorschlagen oder erstelle manuell eigene Schlagwort-Filter für deine Dokumente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onAddNewRule) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manuell anlegen")
                    }
                    Button(
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.testTag("btn_empty_create_rules_ai")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mit KI erstellen")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diese Regeln greifen im Scan-Betrieb sofort (unter 5ms). Nur wenn kein Schlagwort passt, analysiert das lokale On-Device LLM den Text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("rule_card_${rule.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (rule.isAiGenerated) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "KI-erstellt",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Ziel: ${rule.targetMainCategoryId}_${rule.targetSubCategoryId} • Typ: ${rule.targetDocType.ifBlank { "Rechnung" }} • ${rule.detectedSender.ifBlank { "Auto" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { onToggleRule(rule.id, it) },
                                modifier = Modifier.testTag("switch_rule_${rule.id}")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Schlagwörter Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rule.matchKeywords.take(4).forEach { kw ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "+ $kw",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            rule.excludeKeywords.firstOrNull()?.let { excl ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Ausschluss: -$excl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Aktionen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onEditRule(rule) }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prüfen / Anpassen")
                            }
                            IconButton(onClick = { onDeleteRule(rule.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HuggingFaceModelsContent(
    viewModel: DocAnizerViewModel
) {
    val models by viewModel.availableModels.collectAsState()
    val hardwareInfo by viewModel.deviceHardwareInfo.collectAsState()
    val inferenceConfig by viewModel.llmInferenceConfig.collectAsState()
    val isExpertMode by viewModel.isExpertMode.collectAsState()
    val isCheckingNewModels by viewModel.isCheckingNewModels.collectAsState()
    val newModelsNotification by viewModel.newModelsNotification.collectAsState()
    var isAdvancedInferenceExpanded by remember { mutableStateOf(false) }
    var onlyShowCompatible by remember { mutableStateOf(true) }
    var selectedCategoryFilter by remember { mutableStateOf("Alle") }

    val displayedModels = remember(models, onlyShowCompatible, hardwareInfo, selectedCategoryFilter) {
        models.filter { model ->
            val matchHardware = if (onlyShowCompatible && hardwareInfo.totalRamGb > 0) {
                model.recommendedRamGb <= hardwareInfo.totalRamGb + 0.5f
            } else true
            val matchCategory = if (selectedCategoryFilter == "Alle") true else model.modelCategory == selectedCategoryFilter
            matchHardware && matchCategory
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // NOTIFICATION BANNER BEI NEUEN MODELLEN
        newModelsNotification?.let { notifText ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notifText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.dismissNewModelsNotification() }) {
                                Text("Schließen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // INFO HEADER
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OfflinePin, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Lokale Ausführung: Vortrainierte HuggingFace Modelle. Vollständig offline auf deinem Gerät – keine Cloud-Kosten, kein Datenabfluss.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // HARDWARE-BAROMETER CARD
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Geräte-Hardware & LLM-Eignung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.refreshHardwareInfo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("RAM (Gesamt / Frei)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", hardwareInfo.totalRamGb)} GB (${String.format(java.util.Locale.US, "%.1f", hardwareInfo.freeRamGb)} GB frei)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hardwareInfo.isLowRamDevice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("CPU & Leistungsklasse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${hardwareInfo.cpuCores} Kerne • ${hardwareInfo.performanceTier}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nur für dieses Gerät geeignete Modelle anzeigen",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = onlyShowCompatible,
                            onCheckedChange = { onlyShowCompatible = it }
                        )
                    }
                }
            }
        }

        // PERSISTENTER MODELL-SPEICHER CARD (Überlebt App-Updates & Deinstallationen)
        item {
            var scanFeedbackMessage by remember { mutableStateOf<String?>(null) }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Persistenter Modellspeicher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Download/myDocAnizer_Models/ (bleibt bei APK-Updates & Neuinstallationen erhalten)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vorhandene .gguf-Dateien scannen:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = {
                                val found = viewModel.rescanPersistedModels()
                                scanFeedbackMessage = if (found > 0) "$found Modell(e) erfolgreich erkannt & verknüpft!" else "Keine neuen .gguf-Dateien im Download-Ordner gefunden."
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Speicher scannen", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    scanFeedbackMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (msg.contains("erfolgreich")) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // INFERENZ-PARAMETER CARD (Im Standard-Modus kompakt/eingeklappt, im Experten-Modus voll geöffnet)
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isExpertMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("inference_config_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isExpertMode) Icons.Default.Tune else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isExpertMode) MaterialTheme.colorScheme.primary else Color(0xFF16A34A)
                            )
                            Column {
                                Text(
                                    text = if (isExpertMode) "LLM Inferenz- & Leistungsparameter" else "Inferenz-Parameter automatisch optimiert",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!isExpertMode) {
                                    Text(
                                        text = "Optimal konfiguriert für ${hardwareInfo.cpuCores} Kerne (${hardwareInfo.performanceTier})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!isExpertMode) {
                            TextButton(onClick = { isAdvancedInferenceExpanded = !isAdvancedInferenceExpanded }) {
                                Text(if (isAdvancedInferenceExpanded) "Ausblenden" else "Details")
                                Icon(
                                    imageVector = if (isAdvancedInferenceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isExpertMode || isAdvancedInferenceExpanded) {
                        // 1. Temperatur
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Temperatur (Präzision vs. Kreativität):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("${String.format(java.util.Locale.US, "%.2f", inferenceConfig.temperature)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = inferenceConfig.temperature,
                                onValueChange = { viewModel.updateLlmInferenceConfig(inferenceConfig.copy(temperature = it)) },
                                valueRange = 0.0f..0.7f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (inferenceConfig.temperature < 0.2f) "0.0: Streng deterministisch & exakt (Ideal für Rechnungen/Beträge)" else "0.3+: Flexiblere Textinterpretation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // 2. System-Prompt Fokus
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("System-Prompt Ausrichtung:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "DEFAULT" to "Standard",
                                    "FINANCIAL" to "Finanzen & Rechnungen",
                                    "CONTRACTS" to "Verträge & Fristen",
                                    "SUMMARY" to "Kompakte Zusammenfassung"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = inferenceConfig.systemPromptFocus == key,
                                        onClick = { viewModel.updateLlmInferenceConfig(inferenceConfig.copy(systemPromptFocus = key)) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // 3. Max Tokens & CPU Threads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Max. Antwort-Länge:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(256, 512, 1024).forEach { tokens ->
                                        FilterChip(
                                            selected = inferenceConfig.maxTokens == tokens,
                                            onClick = { viewModel.updateLlmInferenceConfig(inferenceConfig.copy(maxTokens = tokens)) },
                                            label = { Text("$tokens", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("CPU Inferenz-Threads:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(2, 4, 8).forEach { threads ->
                                        FilterChip(
                                            selected = inferenceConfig.threadCount == threads,
                                            onClick = { viewModel.updateLlmInferenceConfig(inferenceConfig.copy(threadCount = threads)) },
                                            label = { Text("$threads Kerne", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // MODELLE LISTE HEADER & SYNC ACTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HuggingFace Modelle (${displayedModels.size} verfügbar):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Geprüfte, quantisierte GGUF-Modelle für On-Device Inferenz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.syncModelCatalogFromRemote(forceCheck = true) },
                        enabled = !isCheckingNewModels,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_sync_model_catalog")
                    ) {
                        if (isCheckingNewModels) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prüfe...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Katalog prüfen", fontSize = 11.sp)
                        }
                    }
                }

                // KATEGORIE-FILTER CHIPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Alle", "Reasoning & Logik", "Verträge & Jura", "Finanzen & Tabellen", "Mehrsprachig").forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        items(displayedModels, key = { it.id }) { model ->
            val compatibility = hardwareInfo.getCompatibility(model.recommendedRamGb)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (model.isSelected) 3.dp else 1.dp,
                border = BorderStroke(
                    width = if (model.isSelected) 2.dp else 1.dp,
                    color = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("model_card_${model.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Kopfzeile mit Name, Aktiv-Status, Neu-Badge & RAM-Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (model.isNewRelease) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Text(
                                            text = "NEU",
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (model.isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "Aktiv",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Entwickler: ${model.author} • ${model.modelCategory}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• ✓ ${model.approvalStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Dynamic Compatibility Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (compatibility) {
                                com.example.model.ModelCompatibilityLevel.OPTIMAL -> Color(0xFF16A34A).copy(alpha = 0.15f)
                                com.example.model.ModelCompatibilityLevel.LIMITED -> Color(0xFFEAB308).copy(alpha = 0.2f)
                                com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = when (compatibility) {
                                    com.example.model.ModelCompatibilityLevel.OPTIMAL -> "✅ Optimal"
                                    com.example.model.ModelCompatibilityLevel.LIMITED -> "⚠️ Hohe RAM-Last"
                                    com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> "❌ Zu wenig RAM"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (compatibility) {
                                    com.example.model.ModelCompatibilityLevel.OPTIMAL -> Color(0xFF15803D)
                                    com.example.model.ModelCompatibilityLevel.LIMITED -> Color(0xFFB45309)
                                    com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFDC2626)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Deutsche Erklärung
                    Text(
                        text = model.descriptionDe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Kriterien-Übersicht
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Kriterien & Profileigenschaften:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            model.criteria.forEach { criterion ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = criterion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metriken (Größe, Parameter)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download: ~${model.downloadSizeMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Parameter: ${model.parameterSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Download- und Auswahl-Steuerung
                    if (model.isDownloading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { model.downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val mbDownloaded = model.downloadedBytes / (1024 * 1024)
                            val mbTotal = if (model.totalBytes > 0) model.totalBytes / (1024 * 1024) else model.downloadSizeMb.toLong()
                            Text(
                                text = "Lade echtes Modell herunter: $mbDownloaded MB / $mbTotal MB (${(model.downloadProgress * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!model.isDownloaded) {
                                Text(
                                    text = "Nicht auf Gerät (${model.downloadSizeMb} MB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(onClick = { viewModel.downloadModel(model.id) }) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download (${model.downloadSizeMb} MB)")
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val sizeMb = if (model.localFileSizeBytes > 0) model.localFileSizeBytes / (1024 * 1024) else model.downloadSizeMb.toLong()
                                    Text(
                                        text = "$sizeMb MB auf Gerät",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981)
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteModel(model.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Modell löschen",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (!model.isSelected) {
                                    Button(onClick = { viewModel.selectModel(model.id) }) {
                                        Text("Aktivieren")
                                    }
                                } else {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Aktiv",
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
}

@Composable
fun AiRuleGeneratorDialog(
    viewModel: DocAnizerViewModel,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onAddApprovedRules: (List<DocRule>) -> Unit
) {
    var userPrompt by remember { mutableStateOf("") }
    var generatedDrafts by remember { mutableStateOf<List<DocRule>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regel-Vorschlag von KI generieren", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Beschreibe, welche Unterlagen du hast (z.B. 'Ich habe Versicherungspolicen von Allianz und Stromrechnungen von Stadtwerke München'). Die lokale KI erstellt daraus feste Regeln, die du vor Aktivierung siehst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("z.B. Vodafone Handyvertrag und Allianz Kfz Versicherung...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                if (isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Lokales Modell generiert Regeln...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (generatedDrafts.isNotEmpty()) {
                    Text(
                        text = "Vorgeschlagene Regeln (Bitte prüfen & freigeben):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )

                    generatedDrafts.forEachIndexed { index, draft ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(draft.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Schlagwörter: ${draft.matchKeywords.joinToString(", ")}", fontSize = 11.sp)
                                Text("Zielordner: ${draft.targetMainCategoryId}_${draft.targetSubCategoryId} (${draft.detectedSender})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedDrafts.isEmpty()) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val results = viewModel.generateRulesWithAi(userPrompt)
                            generatedDrafts = results
                        }
                    },
                    enabled = userPrompt.isNotBlank() && !isGenerating
                ) {
                    Text("Regeln vorschlagen")
                }
            } else {
                Button(onClick = { onAddApprovedRules(generatedDrafts) }) {
                    Text("Freigeben & Speichern (${generatedDrafts.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun EditRuleDialog(
    rule: DocRule,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (DocRule) -> Unit
) {
    var name by remember { mutableStateOf(rule.name) }
    var keywordsStr by remember { mutableStateOf(rule.matchKeywords.joinToString(", ")) }
    var excludeStr by remember { mutableStateOf(rule.excludeKeywords.joinToString(", ")) }
    var sender by remember { mutableStateOf(rule.detectedSender) }
    var docType by remember { mutableStateOf(rule.targetDocType.ifBlank { "Rechnung" }) }
    var mainCat by remember { mutableStateOf(rule.targetMainCategoryId) }
    var subCat by remember { mutableStateOf(rule.targetSubCategoryId) }
    var tagsStr by remember { mutableStateOf(rule.targetTags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) "Neue Schlagwort-Regel anlegen" else "Schlagwort-Regel anpassen",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Regel-Name (z.B. Vodafone DSL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsStr,
                    onValueChange = { keywordsStr = it },
                    label = { Text("Pflicht-Schlagwörter (Kommagetrennt)") },
                    placeholder = { Text("Vodafone, Internet, Kundenkonto") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = excludeStr,
                    onValueChange = { excludeStr = it },
                    label = { Text("Ausschluss-Wörter (optional)") },
                    placeholder = { Text("Mahnung, Kündigung") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Absender / Firma") },
                    placeholder = { Text("Vodafone GmbH") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = docType,
                    onValueChange = { docType = it },
                    label = { Text("Dokument-Typ") },
                    placeholder = { Text("Rechnung / Vertrag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mainCat,
                        onValueChange = { mainCat = it },
                        label = { Text("Ebene 1 (z.B. A01)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subCat,
                        onValueChange = { subCat = it },
                        label = { Text("Ebene 2 (z.B. B1.01)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = tagsStr,
                    onValueChange = { tagsStr = it },
                    label = { Text("Tags / Schlagworte (Kommagetrennt)") },
                    placeholder = { Text("Telekom, Monatsabrechnung") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kws = keywordsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val excludes = excludeStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val tags = tagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(
                        rule.copy(
                            name = name.ifBlank { "Neue Regel" },
                            matchKeywords = kws,
                            excludeKeywords = excludes,
                            detectedSender = sender,
                            targetDocType = docType.ifBlank { "Rechnung" },
                            targetMainCategoryId = mainCat.ifBlank { "A01" },
                            targetSubCategoryId = subCat.ifBlank { "B1.01" },
                            targetTags = tags
                        )
                    )
                },
                enabled = keywordsStr.isNotBlank() || name.isNotBlank()
            ) {
                Text(if (isNew) "Regel erstellen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogContent(viewModel: DocAnizerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val modelBenchmarks by viewModel.modelBenchmarks.collectAsStateWithLifecycle()
    val activeModel = availableModels.find { it.isSelected } ?: availableModels.firstOrNull()

    var selectedCategoryFilter by remember { mutableStateOf<com.example.service.LogCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showBenchmarkOverview by remember { mutableStateOf(false) }

    val filteredLogs = remember(auditLogs, selectedCategoryFilter, searchQuery) {
        auditLogs.filter { entry ->
            val matchesCategory = selectedCategoryFilter == null || entry.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.details.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                (entry.reasoningTrace?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("audit_log_tab"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status- & Export-Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("100% Lokales Audit- & KI-Diagnose-Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("DSGVO-konform: Keine Cloud-Übertragung, 0 Byte Abfluss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Aktives KI-Modell:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${activeModel?.name ?: "Keines"} (${activeModel?.quantFormat ?: ""})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Protokoll-Einträge:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${auditLogs.size} Ereignisse im Speicher", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        if (modelBenchmarks.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modell-Benchmarks:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = { showBenchmarkOverview = !showBenchmarkOverview },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = if (showBenchmarkOverview) "Statistik verbergen" else "${modelBenchmarks.size} Modelle verglichen ▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Ausklappbare Modell-Benchmark-Statistik
                AnimatedVisibility(visible = showBenchmarkOverview && modelBenchmarks.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Hardware- & Genauigkeits-Vergleich:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            modelBenchmarks.values.forEach { bench ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(bench.modelName.take(28), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("${bench.totalInferences} Durchläufe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Ø Latenz: ${bench.avgLatencyMs}ms", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Peak RAM: ${"%.1f".format(bench.peakRamDeltaMb)} MB", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Ø Felder: ${"%.1f".format(bench.avgFieldsExtracted)}", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportAndShareAuditLog(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_export_audit_log"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log exportieren / teilen", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearAuditLogs(context) },
                        modifier = Modifier.testTag("btn_clear_audit_log")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leeren", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Filter-Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategoryFilter == null,
                onClick = { selectedCategoryFilter = null },
                label = { Text("Alle (${auditLogs.size})") }
            )
            com.example.service.LogCategory.values().forEach { cat ->
                val count = auditLogs.count { it.category == cat }
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                    label = { Text("${cat.icon} ${cat.label} ($count)") }
                )
            }
        }

        // Suchzeile
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Im Log filtern (z.B. Modell, OCR, Think)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Log-Liste
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (auditLogs.isEmpty()) "Noch keine Protokolleinträge vorhanden." else "Keine Einträge für den Filter gefunden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    AuditLogItemCard(entry)
                }
            }
        }
    }
}

@Composable
fun AuditLogItemCard(entry: com.example.service.AuditLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (entry.isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.category.icon)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            // Hardware Telemetrie Badges (wenn vorhanden)
            if (entry.ramDeltaMb != null || entry.latencyMs != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (entry.latencyMs != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "⚡ ${entry.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (entry.ramDeltaMb != null && entry.ramDeltaMb > 0f) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "🧠 RAM-Δ: ${entry.ramDeltaMb}MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            if (entry.details.isNotBlank()) {
                if (expanded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry.details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "Details: ${entry.details.take(90)}${if (entry.details.length > 90) "..." else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Detaillierter Reasoning-Trace (Gedankengang) wenn vorhanden & ausgeklappt
            if (expanded && !entry.reasoningTrace.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("KI-Gedankengang & Reasoning-Trace (<think>):", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = entry.reasoningTrace,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}
