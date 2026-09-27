package com.example.ui.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppViewLevel
import com.example.model.DocTypeItem
import com.example.model.DOC_NAMING_STYLE_OPTIONS
import com.example.model.PREFIX_STYLE_OPTIONS
import com.example.model.PrefixStyleOption
import com.example.model.PRESET_COLOR_SKINS
import com.example.ui.components.AppLogoBanner
import com.example.model.PRESET_OPTIMIZATION_TEMPLATES
import com.example.model.PdfSettings
import com.example.model.ScannerSettings
import com.example.model.TemplateItem
import com.example.service.CloudSyncWorkerService
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.TemplateEditorDialog
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SettingsView in 4 separaten Tabs unterteilt:
 * 1. Vorlagen & Ordner-Baum (Templates mit aufklappbarer Baumstruktur E1/E2 & IDs)
 * 2. Doc-Typen (Verwaltung der optionalen Doc-Typen)
 * 3. PDF & Scan (DPI, Kompression, Ordner-Präfixe A01_B1.01-)
 * 4. Erscheinungsbild (Dark Mode, Light Mode, System)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSettingsTab by remember { mutableStateOf(0) }
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val appViewLevel by viewModel.appViewLevel.collectAsState()
    val isExpertMode by viewModel.isExpertMode.collectAsState()
    val isAdvancedOrExpert by viewModel.isAdvancedOrExpert.collectAsState()

    data class TabInfo(val title: String, val icon: ImageVector)
    val tabs = listOf(
        TabInfo("Scanner & Kamera", Icons.Default.PhotoCamera),
        TabInfo("PDF & Optimierung", Icons.Default.PictureAsPdf),
        TabInfo("Dokumenten-Gruppen", Icons.Default.Label),
        TabInfo("Regeln & KI", Icons.Default.AutoAwesome),
        TabInfo("Sicherheit", Icons.Default.Security),
        TabInfo("WLAN & P2P Sync", Icons.Default.SyncAlt),
        TabInfo("Cloud & Backup", Icons.Default.CloudSync),
        TabInfo("Design", Icons.Default.Palette)
    )

    // Automatisches sanftes Sichtbarmachen des aktiven Tabs
    LaunchedEffect(selectedSettingsTab) {
        val targetOffset = (selectedSettingsTab * 140).coerceAtMost(tabScrollState.maxValue)
        tabScrollState.animateScrollTo(targetOffset)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_view")
    ) {
        // 3-STUFIGE ANSICHTS-AUSWAHL (STANDARD / ERWEITERT / EXPERTE)
        Surface(
            color = when (appViewLevel) {
                AppViewLevel.STANDARD -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                AppViewLevel.ADVANCED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                AppViewLevel.EXPERT -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (appViewLevel) {
                                AppViewLevel.STANDARD -> Icons.Default.Visibility
                                AppViewLevel.ADVANCED -> Icons.Default.Tune
                                AppViewLevel.EXPERT -> Icons.Default.Build
                            },
                            contentDescription = null,
                            tint = when (appViewLevel) {
                                AppViewLevel.STANDARD -> MaterialTheme.colorScheme.primary
                                AppViewLevel.ADVANCED -> MaterialTheme.colorScheme.secondary
                                AppViewLevel.EXPERT -> MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Ansichts-Ebene:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 3 FilterChips nebeneinander
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = appViewLevel == AppViewLevel.STANDARD,
                            onClick = { viewModel.setAppViewLevel(AppViewLevel.STANDARD) },
                            label = { Text("Standard", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (appViewLevel == AppViewLevel.STANDARD) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.testTag("chip_view_standard")
                        )
                        FilterChip(
                            selected = appViewLevel == AppViewLevel.ADVANCED,
                            onClick = { viewModel.setAppViewLevel(AppViewLevel.ADVANCED) },
                            label = { Text("Erweitert", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (appViewLevel == AppViewLevel.ADVANCED) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.testTag("chip_view_advanced")
                        )
                        FilterChip(
                            selected = appViewLevel == AppViewLevel.EXPERT,
                            onClick = { viewModel.setAppViewLevel(AppViewLevel.EXPERT) },
                            label = { Text("Experte", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (appViewLevel == AppViewLevel.EXPERT) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.testTag("chip_view_expert")
                        )
                    }
                }

                // Dynamische Kurzbeschreibung der aktiven Ebene
                Text(
                    text = when (appViewLevel) {
                        AppViewLevel.STANDARD -> "🟢 Standard: Fokussiert & übersichtlich. Technische Details optimal vorkonfiguriert."
                        AppViewLevel.ADVANCED -> "🟡 Erweitert: Mehr Optionen für Workflows, Vorlagen & flexible Synchronisation."
                        AppViewLevel.EXPERT -> "🔴 Experte: Voller Zugriff auf Inferenz-Parameter, SQLite WAL-Wartung & Netzwerk."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // MANUELLER START DES KONFIGURATIONS-ASSISTENTEN
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Anpassung der App-Konfig",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Speicherort, Scanner, KI & Backup geführt durchgehen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.launchSetupWizardManually() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_launch_wizard_manual")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assistent starten", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // MANUELLER START DES PC- & SCANNER-ASSISTENTEN
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "PC- & Scanner-Anbindung",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Am PC scannen, Web-Portal & Watchfolder einrichten",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = { viewModel.launchPcCompanionWizard() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_launch_pc_wizard")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PC-Assistent", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Tab-Leiste für Einstellungen mit dynamischen Pfeilen
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Scrollbare Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabScrollState)
                        .padding(horizontal = 42.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedSettingsTab == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSettingsTab = index },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("settings_tab_$index")
                        )
                    }
                }

                // Dynamischer Pfeil LINKS (sichtbar wenn noch Tabs nach links verborgen sind)
                androidx.compose.animation.AnimatedVisibility(
                    visible = tabScrollState.canScrollBackward,
                    enter = fadeIn() + slideInHorizontally { -it },
                    exit = fadeOut() + slideOutHorizontally { -it },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(52.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val target = (tabScrollState.value - 240).coerceAtLeast(0)
                                    tabScrollState.animateScrollTo(target)
                                }
                            },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(36.dp)
                                .testTag("settings_tab_scroll_left")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Vorherige Einstellungen-Tabs anzeigen",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Dynamischer Pfeil RECHTS (sichtbar wenn noch Tabs nach rechts verborgen sind)
                androidx.compose.animation.AnimatedVisibility(
                    visible = tabScrollState.canScrollForward,
                    enter = fadeIn() + slideInHorizontally { it },
                    exit = fadeOut() + slideOutHorizontally { it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(52.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val target = (tabScrollState.value + 240).coerceAtMost(tabScrollState.maxValue)
                                    tabScrollState.animateScrollTo(target)
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .testTag("settings_tab_scroll_right")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Weitere Einstellungen-Tabs anzeigen",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        when (selectedSettingsTab) {
            0 -> ScannerSettingsTab(viewModel)
            1 -> PdfSettingsTab(viewModel)
            2 -> DocTypesSettingsTab(viewModel)
            3 -> RulesAndAiSettingsTab(viewModel)
            4 -> SecuritySettingsTab(viewModel)
            5 -> P2pSyncTab(viewModel)
            6 -> CloudSyncSettingsTab(viewModel)
            7 -> DesignSettingsTab(viewModel)
        }
    }
}

/**
 * TAB 0: Scanner & Kamera-Einstellungen
 */
@Composable
fun ScannerSettingsTab(viewModel: DocAnizerViewModel) {
    val scannerSettings by viewModel.scannerSettings.collectAsState()

    var isModeExpanded by remember { mutableStateOf(true) }
    var isTripodExpanded by remember { mutableStateOf(false) }
    var isTriggerExpanded by remember { mutableStateOf(false) }
    var isHardwareExpanded by remember { mutableStateOf(false) }
    var isFlashExpanded by remember { mutableStateOf(false) }
    var isFeedbackExpanded by remember { mutableStateOf(false) }

    val allExpanded = isModeExpanded && isTripodExpanded && isTriggerExpanded && isHardwareExpanded && isFlashExpanded && isFeedbackExpanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scanner & Kamera-Einstellungen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Steuere Auslöser, Dauerscan, Kamera-Licht und akustische Rückmeldungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    val nextState = !allExpanded
                    isModeExpanded = nextState
                    isTripodExpanded = nextState
                    isTriggerExpanded = nextState
                    isHardwareExpanded = nextState
                    isFlashExpanded = nextState
                    isFeedbackExpanded = nextState
                }
            ) {
                Text(
                    text = if (allExpanded) "Alle zuklappen" else "Alle aufklappen",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 1. Scan-Modus
        CollapsibleSettingsCard(
            title = "Scan-Modus",
            subtitle = if (scannerSettings.scanMode == "AUTO") "⚡ Auto-Scan (Standard)" else "⚙️ Manuell",
            icon = Icons.Default.Bolt,
            isExpanded = isModeExpanded,
            onToggle = { isModeExpanded = !isModeExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Standard-Scan-Modus",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Im Auto-Scan-Modus werden Dokumente ohne Zwischenklicks fortlaufend erfasst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scannerSettings.scanMode == "AUTO",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "AUTO")) },
                        label = { Text("⚡ Auto-Scan (Standard)") },
                        leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = scannerSettings.scanMode == "MANUAL",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "MANUAL")) },
                        label = { Text("⚙️ Manuell") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // 2. Stativ-Dauerscan
        CollapsibleSettingsCard(
            title = "Stativ-Dauerscan (Hands-Free)",
            subtitle = if (scannerSettings.tripodAutoScan) "Aktiv (${scannerSettings.preScanDelaySeconds}s Vorlauf / ${scannerSettings.postScanDelaySeconds}s Nachlauf)" else "Deaktiviert",
            icon = Icons.Default.VideoCameraFront,
            isExpanded = isTripodExpanded,
            onToggle = { isTripodExpanded = !isTripodExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔭 Stativ-Dauerscan aktivieren",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Handy im Stativ fixieren: Dokumente werden automatisch erkannt, die Beruhigungszeit abgewartet und sofort ausgelöst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = scannerSettings.tripodAutoScan,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(tripodAutoScan = it)) }
                    )
                }

                if (scannerSettings.tripodAutoScan) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    // Schieberegler 1: Vorlaufzeit vor dem Scan
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ Vorlaufzeit vor Auslösung (nach Erkennung):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${scannerSettings.preScanDelaySeconds} s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Wartezeit zur Beruhigung, nachdem das Dokument automatisch scharf erkannt wurde.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = scannerSettings.preScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(1, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(preScanDelaySeconds = sec, autoScanDelayMs = sec * 1000L))
                            },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Schieberegler 2: Nachlaufzeit / Wartezeit bis zur nächsten Erkennung
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔄 Nachlaufzeit bis Wiederaktivierung:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${scannerSettings.postScanDelaySeconds} s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Zeitfenster zum Umblättern oder Auflegen des nächsten Dokuments, bevor die automatische Erkennung wieder aktiv wird.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = scannerSettings.postScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(1, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(postScanDelaySeconds = sec))
                            },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Blattwechsel-Empfindlichkeit:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW" to "Träge", "MEDIUM" to "Normal", "HIGH" to "Schnell").forEach { (lvl, label) ->
                            FilterChip(
                                selected = scannerSettings.sheetChangeSensitivity == lvl,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(sheetChangeSensitivity = lvl)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Auslöser-Verhalten
        CollapsibleSettingsCard(
            title = "Auslöser-Methode & Countdown",
            subtitle = when (scannerSettings.triggerMode) {
                "AUTO_DETECT" -> "Automatisch bei Erkennung"
                "COUNTDOWN" -> "Selbstauslöser (${scannerSettings.countdownSeconds}s)"
                else -> "Manuell per Auslöser-Button"
            },
            icon = Icons.Default.Timer,
            isExpanded = isTriggerExpanded,
            onToggle = { isTriggerExpanded = !isTriggerExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val triggerOptions = listOf(
                    Triple("AUTO_DETECT", "Automatisch bei Erkennung", "Löst automatisch aus, sobald ein Dokument stabil erfasst ist"),
                    Triple("COUNTDOWN", "Zeitverzögerter Selbstauslöser", "Startet einen Countdown vor der Aufnahme"),
                    Triple("MANUAL", "Manuell (Display / Hardware)", "Löst ausschließlich per Klick oder Tastendruck aus")
                )

                triggerOptions.forEach { (key, title, desc) ->
                    val isSelected = scannerSettings.triggerMode == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateScannerSettings(scannerSettings.copy(triggerMode = key)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(triggerMode = key)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (scannerSettings.triggerMode == "COUNTDOWN") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Countdown-Dauer:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = scannerSettings.countdownSeconds == sec,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(countdownSeconds = sec)) },
                                label = { Text("$sec s") }
                            )
                        }
                    }
                }
            }
        }

        // 4. Hardware & Tasten
        CollapsibleSettingsCard(
            title = "Hardware-Tasten & Stapelscan",
            subtitle = "Lautstärketasten: ${if (scannerSettings.enableHardwareButtons) "An" else "Aus"} • Stapelscan: ${if (scannerSettings.isBulkMode) "An" else "Aus"}",
            icon = Icons.Default.Keyboard,
            isExpanded = isHardwareExpanded,
            onToggle = { isHardwareExpanded = !isHardwareExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isManualMode = scannerSettings.scanMode == "MANUAL"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lautstärketasten als Auslöser",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isManualMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Text(
                            text = if (isManualMode) {
                                "Lauter/Leiser-Tasten am Gerät lösen den Scan sofort aus."
                            } else {
                                "Nur im manuellen Scan-Modus verfügbar (im Auto-Scan löst die Kamera automatisch aus)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isManualMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = scannerSettings.enableHardwareButtons && isManualMode,
                        enabled = isManualMode,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableHardwareButtons = it)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stapelverarbeitung (Bulk-Modus)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Dokument sofort im Zielordner ablegen und direkt für das nächste Dokument bereit sein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = scannerSettings.isBulkMode,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(isBulkMode = it)) }
                    )
                }
            }
        }

        // 5. Blitz & Beleuchtung
        CollapsibleSettingsCard(
            title = "Kamera-Licht & Blitzvoreinstellung",
            subtitle = when (scannerSettings.flashMode) {
                "FLASH" -> "Blitz aktiv"
                "TORCH" -> "Dauerlicht"
                else -> "Aus"
            },
            icon = Icons.Default.FlashOn,
            isExpanded = isFlashExpanded,
            onToggle = { isFlashExpanded = !isFlashExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Standardmäßige Kamera-Ausleuchtung",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scannerSettings.flashMode == "OFF",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(flashMode = "OFF")) },
                        label = { Text("Aus") },
                        leadingIcon = { Icon(Icons.Default.FlashOff, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = scannerSettings.flashMode == "FLASH",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(flashMode = "FLASH")) },
                        label = { Text("Blitz") },
                        leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = scannerSettings.flashMode == "TORCH",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(flashMode = "TORCH")) },
                        label = { Text("Dauerlicht") },
                        leadingIcon = { Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // 6. Akustisches Feedback & Vibration
        CollapsibleSettingsCard(
            title = "Signalton & Vibrationsfeedback",
            subtitle = "Ton: ${when(scannerSettings.soundProfile){ "SUCCESS_BEEP" -> "Gong"; "CLICK" -> "Klick"; "CHIME" -> "Chime"; else -> "Stumm" }} • Vibration: ${if (scannerSettings.enableVibration) "An" else "Aus"}",
            icon = Icons.Default.VolumeUp,
            isExpanded = isFeedbackExpanded,
            onToggle = { isFeedbackExpanded = !isFeedbackExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Erfolgs-Signalton auswählen",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SUCCESS_BEEP" to "Erfolgs-Gong",
                        "CLICK" to "Klick",
                        "CHIME" to "Chime",
                        "MUTE" to "Stumm"
                    ).forEach { (profile, label) ->
                        FilterChip(
                            selected = scannerSettings.soundProfile == profile,
                            onClick = {
                                viewModel.updateScannerSettings(scannerSettings.copy(soundProfile = profile))
                                com.example.service.ScanFeedbackService.playCaptureSound(profile)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { com.example.service.ScanFeedbackService.playCaptureSound(scannerSettings.soundProfile) }
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Signalton testen")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vibrationsfeedback",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Haptischer Impuls beim erfolgreichen Erfassen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scannerSettings.enableVibration,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableVibration = it)) }
                    )
                }
            }
        }
    }
}

/**
 * TAB 2: Vollständig editierbare Doc-Typen (Erstellen, Bearbeiten, Löschen)
 */
@Composable
fun DocTypesSettingsTab(viewModel: DocAnizerViewModel) {
    val docTypes by viewModel.docTypes.collectAsState()
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingDocType by remember { mutableStateOf<DocTypeItem?>(null) }
    var docTypeToDelete by remember { mutableStateOf<DocTypeItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Doc-Typen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Optionale Gruppierungsmerkmale für deine Dokumente. Du kannst eigene Typen frei anlegen, bearbeiten oder löschen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    editingDocType = null
                    showEditorDialog = true
                },
                modifier = Modifier.testTag("btn_add_doc_type")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Neu")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (docTypes.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Keine Doc-Typen angelegt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Doc-Typen sind optional. Lege jetzt eigene Kategorien an oder lade optionale Start-Vorschläge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editingDocType = null
                                showEditorDialog = true
                            }
                        ) {
                            Text("+ Neuer Doc-Typ")
                        }
                        OutlinedButton(
                            onClick = {
                                val samples = listOf(
                                    DocTypeItem(name = "Rechnungen & Belege", description = "Zahlungen, Quittungen und Kaufbelege", colorHex = 0xFF2563EB),
                                    DocTypeItem(name = "Verträge & Policen", description = "Laufende Verträge, Vereinbarungen und Versicherungen", colorHex = 0xFF7C3AED),
                                    DocTypeItem(name = "Wichtige Dokumente", description = "Ausweise, Urkunden und amtliche Bescheide", colorHex = 0xFFDC2626)
                                )
                                samples.forEach { viewModel.addDocType(it) }
                            }
                        ) {
                            Text("Beispiele laden")
                        }
                    }
                }
            }
        } else {
            docTypes.forEach { docType ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(docType.colorHex))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = docType.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (docType.description.isNotBlank()) {
                                    Text(
                                        text = docType.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    editingDocType = docType
                                    showEditorDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = { docTypeToDelete = docType }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog zum Erstellen / Bearbeiten von Doc-Typen
    if (showEditorDialog) {
        DocTypeEditorDialog(
            initialItem = editingDocType,
            onSave = { saved ->
                if (editingDocType != null) {
                    viewModel.updateDocType(saved)
                } else {
                    viewModel.addDocType(saved)
                }
                showEditorDialog = false
            },
            onDismiss = { showEditorDialog = false }
        )
    }

    // Bestätigungs-Dialog zum Löschen
    if (docTypeToDelete != null) {
        AlertDialog(
            onDismissRequest = { docTypeToDelete = null },
            title = { Text("Doc-Typ löschen?") },
            text = { Text("Möchtest du den Doc-Typ '${docTypeToDelete?.name}' wirklich löschen? Bestehende Dokumente bleiben erhalten.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = docTypeToDelete ?: return@Button
                        viewModel.deleteDocType(toDelete.id)
                        docTypeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { docTypeToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun DocTypeEditorDialog(
    initialItem: DocTypeItem?,
    onSave: (DocTypeItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name.orEmpty()) }
    var description by remember { mutableStateOf(initialItem?.description.orEmpty()) }
    var selectedColor by remember { mutableStateOf(initialItem?.colorHex ?: 0xFF2563EB) }

    val colorsPalette = listOf(
        0xFF2563EB, // Blau
        0xFF059669, // Grün
        0xFF7C3AED, // Violett
        0xFFDC2626, // Rot
        0xFFD97706, // Amber
        0xFF0D9488, // Teal
        0xFF4F46E5, // Indigo
        0xFFE11D48  // Rose
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Neuer Doc-Typ" else "Doc-Typ bearbeiten") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    placeholder = { Text("z.B. Rechnungen, Verträge") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung (optional)") },
                    placeholder = { Text("z.B. Monatliche Zahlungsbelege") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Farbe auswählen:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorsPalette.forEach { colorVal ->
                        val isSelected = selectedColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .clickable { selectedColor = colorVal },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            DocTypeItem(
                                id = initialItem?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                description = description.trim(),
                                colorHex = selectedColor
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Speichern")
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
 * TAB 3: PDF-, Scanner- und Ordner-Konfiguration
 */
/**
 * TAB 3: PDF-, Scanner- und Ordner-Konfiguration
 * Kompakte Ansicht: Einstellungen sind standardmäßig zusammengeklappt, um langes Scrollen zu vermeiden.
 */
@Composable
fun CollapsibleSettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Einklappen" else "Aufklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsTab(viewModel: DocAnizerViewModel) {
    val pdfSettings by viewModel.pdfSettings.collectAsState()
    val scannerSettings by viewModel.scannerSettings.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var isOptimizationExpanded by remember { mutableStateOf(true) }
    var isQualityExpanded by remember { mutableStateOf(false) }
    var isNamingExpanded by remember { mutableStateOf(false) }
    var isStorageExpanded by remember { mutableStateOf(false) }

    val allExpanded = isOptimizationExpanded && isQualityExpanded && isNamingExpanded && isStorageExpanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PDF, Optimierung & Speicherpfade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Konfiguriere Bildverbesserung, Kompression, DPI-Auflösung und Aktenplan-Strukturen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    val nextState = !allExpanded
                    isOptimizationExpanded = nextState
                    isQualityExpanded = nextState
                    isNamingExpanded = nextState
                    isStorageExpanded = nextState
                }
            ) {
                Text(
                    text = if (allExpanded) "Alle zuklappen" else "Alle aufklappen",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 1. DOKUMENTEN-OPTIMIERUNG & TEMPLATES (Aufklappbar)
        val activeTemplate = PRESET_OPTIMIZATION_TEMPLATES.find { it.id == pdfSettings.activeOptimizationTemplateId }
            ?: PRESET_OPTIMIZATION_TEMPLATES.first()
        val optSubtitle = if (pdfSettings.isOptimizationEnabled) {
            "Aktiviert • ${activeTemplate.name}"
        } else {
            "Deaktiviert (Scans werden ohne Nachbearbeitung gespeichert)"
        }

        CollapsibleSettingsCard(
            title = "Dokumenten-Optimierung & Filter-Templates",
            subtitle = optSubtitle,
            icon = Icons.Default.AutoFixHigh,
            isExpanded = isOptimizationExpanded,
            onToggle = { isOptimizationExpanded = !isOptimizationExpanded }
        ) {
            // Haupt-Schalter für Optimierung
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatische Dokumenten-Optimierung",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Verbessert Lesbarkeit, Schärfe und entfernt Schlagschatten vor dem Speichern.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = pdfSettings.isOptimizationEnabled,
                    onCheckedChange = { viewModel.setOptimizationEnabled(it) }
                )
            }

            if (pdfSettings.isOptimizationEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Text(
                    text = "Optimierungs-Profil nach Dokumentenart:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                PRESET_OPTIMIZATION_TEMPLATES.forEach { template ->
                    val isSelected = pdfSettings.activeOptimizationTemplateId == template.id
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setOptimizationTemplate(template.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setOptimizationTemplate(template.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (template.removeShadows) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Schattenentfernung",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (template.cleanBackgroundWhite) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Hintergrund weiß",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (template.autoDeskew) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Auto-Begradigung",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Text(
                    text = "Individuelle Bildkorrekturen:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Schattenentfernung
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Schattenentfernung", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Entfernt störende Hand- und Körperschatten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = pdfSettings.shadowRemovalEnabled,
                        onCheckedChange = { viewModel.updatePdfSettings(pdfSettings.copy(shadowRemovalEnabled = it)) }
                    )
                }

                // Auto-Begradigung
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Begradigung (Deskew)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Richtet schräg liegende Dokumente exakt waagerecht aus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = pdfSettings.autoDeskewEnabled,
                        onCheckedChange = { viewModel.updatePdfSettings(pdfSettings.copy(autoDeskewEnabled = it)) }
                    )
                }

                // Hintergrund-Bereinigung
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hintergrund-Aufhellung", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Reinigt graue Ränder und Verfärbungen für reinweißes Papier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = pdfSettings.backgroundCleaningEnabled,
                        onCheckedChange = { viewModel.updatePdfSettings(pdfSettings.copy(backgroundCleaningEnabled = it)) }
                    )
                }
            }
        }

        // 2. FARBPROFIL & PDF-QUALITÄT (Aufklappbar)
        val colorModeLabel = when (pdfSettings.defaultColorMode) {
            "AUTO" -> "Auto-Erkennung"
            "BW" -> "S/W"
            else -> "Farbe"
        }
        val qualitySubtitle = "$colorModeLabel • ${pdfSettings.dpi} DPI • ${pdfSettings.compressionLevel}"

        CollapsibleSettingsCard(
            title = "PDF-Qualität, DPI & Farbmodus",
            subtitle = qualitySubtitle,
            icon = Icons.Default.Tune,
            isExpanded = isQualityExpanded,
            onToggle = { isQualityExpanded = !isQualityExpanded }
        ) {
            // Farbmodus Standard
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Standard-Farbprofil",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pdfSettings.defaultColorMode == "AUTO",
                        onClick = { viewModel.updatePdfSettings(pdfSettings.copy(defaultColorMode = "AUTO")) },
                        label = { Text("Auto (S/W & Farbe)") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = pdfSettings.defaultColorMode == "BW",
                        onClick = { viewModel.updatePdfSettings(pdfSettings.copy(defaultColorMode = "BW")) },
                        label = { Text("Schwarz/Weiß (S/W)") },
                        leadingIcon = { Icon(Icons.Default.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = pdfSettings.defaultColorMode == "COLOR",
                        onClick = { viewModel.updatePdfSettings(pdfSettings.copy(defaultColorMode = "COLOR")) },
                        label = { Text("Farbe") },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // DPI
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Scan-Auflösung (DPI)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 300, 600).forEach { dpi ->
                        FilterChip(
                            selected = pdfSettings.dpi == dpi,
                            onClick = { viewModel.updatePdfSettings(pdfSettings.copy(dpi = dpi)) },
                            label = { Text("$dpi DPI") }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // PDF-Kompression
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PDF-Kompression",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Standard", "Hoch", "Verlustfrei").forEach { comp ->
                        FilterChip(
                            selected = pdfSettings.compressionLevel == comp,
                            onClick = { viewModel.updatePdfSettings(pdfSettings.copy(compressionLevel = comp)) },
                            label = { Text(comp) }
                        )
                    }
                }
            }
        }

        // 2. DATEINAMEN-MUSTER FÜR DOKUMENTE (Aufklappbar)
        var isDocNamingDropdownExpanded by remember { mutableStateOf(false) }
        val activeDocNamingOption = DOC_NAMING_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.docNamingStyle } ?: DOC_NAMING_STYLE_OPTIONS.first()

        CollapsibleSettingsCard(
            title = "Dateinamen für Dokumente",
            subtitle = "${activeDocNamingOption.title} (z.B. ${activeDocNamingOption.example})",
            icon = Icons.Default.DriveFileRenameOutline,
            isExpanded = isNamingExpanded,
            onToggle = { isNamingExpanded = !isNamingExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Namensmuster für gespeicherte Dokumente",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Bestimmt, wie gescannte und importierte Dateien automatisch benannt werden:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = isDocNamingDropdownExpanded,
                    onExpandedChange = { isDocNamingDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = activeDocNamingOption.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Muster für Dokument-Dateinamen") },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDocNamingDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isDocNamingDropdownExpanded,
                        onDismissRequest = { isDocNamingDropdownExpanded = false }
                    ) {
                        DOC_NAMING_STYLE_OPTIONS.forEach { option ->
                            val isSelected = option.id == pdfSettings.docNamingStyle
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Ausgewählt",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "📄 ${option.example}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    isDocNamingDropdownExpanded = false
                                    viewModel.updatePdfSettings(pdfSettings.copy(docNamingStyle = option.id))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. SPEICHERPFADE & ORDNERSTRUKTUR (Aufklappbar)
        val context = androidx.compose.ui.platform.LocalContext.current
        val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                val pathStr = uri.path ?: uri.toString()
                viewModel.updatePdfSettings(
                    pdfSettings.copy(
                        baseStorageLocation = "CUSTOM",
                        customStoragePath = pathStr
                    )
                )
            }
        }

        val storageLabel = when (pdfSettings.baseStorageLocation) {
            "APP_STORAGE" -> "App-Tresor"
            "DOCUMENTS" -> "Geräte-Dokumente"
            "CUSTOM" -> "Eigener Ordner"
            else -> "App-Speicher"
        }
        val activePrefixOption = PREFIX_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.folderPrefixStyle } ?: PREFIX_STYLE_OPTIONS.first()
        val prefixSummary = if (pdfSettings.useIdPrefixes && pdfSettings.folderPrefixStyle != "NONE") activePrefixOption.shortName else "Keine Nummern"
        val storageSubtitle = "$storageLabel • Ordner: $prefixSummary"

        CollapsibleSettingsCard(
            title = "Speicherort & Ordner-Nummerierung",
            subtitle = storageSubtitle,
            icon = Icons.Default.FolderSpecial,
            isExpanded = isStorageExpanded,
            onToggle = { isStorageExpanded = !isStorageExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Play Store & Google Konformitäts-Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Google Play Datenschutz- & Speicherrichtlinien",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• App-Tresor: Höchste Isolation im gesicherten App-Speicher (Scoped Sandbox) – 0 Berechtigungen nötig.\n• Eigener Ordner: Android fragt über das offizielle Storage Access Framework (SAF) nur für diesen einen Ordner nach Freigabe. Es wird kein Vollzugriff auf das Smartphone gefordert.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Speicherort für deine Dokumente",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Wähle, wo deine Dokumente und Ordner abgelegt werden:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val storageOptions = listOf(
                    Triple("APP_STORAGE", "Sicherer App-Tresor (Empfohlen)", "Vollständig geschützt im App-Speicher. Höchste Sicherheit, sofort startklar ohne Berechtigungen."),
                    Triple("DOCUMENTS", "Standard Android Dokumente-Ordner", "Im allgemeinen Dokumente-Verzeichnis deines Gerätes ablegen."),
                    Triple("CUSTOM", "Eigener Ordner auf dem Smartphone", if (pdfSettings.customStoragePath.isNotBlank()) pdfSettings.customStoragePath else "Ordner im Android-Dateimanager auswählen")
                )

                storageOptions.forEach { (key, title, desc) ->
                    val isSelected = pdfSettings.baseStorageLocation == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = key))
                                if (key == "CUSTOM" && pdfSettings.customStoragePath.isBlank()) {
                                    folderPickerLauncher.launch(null)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = key))
                                    if (key == "CUSTOM" && pdfSettings.customStoragePath.isBlank()) {
                                        folderPickerLauncher.launch(null)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (pdfSettings.baseStorageLocation == "CUSTOM") {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (pdfSettings.customStoragePath.isNotBlank()) "Anderen Ordner wählen" else "Ordner im Dateimanager auswählen")
                            }
                            if (pdfSettings.customStoragePath.isNotBlank()) {
                                Text(
                                    text = "Aktueller Pfad: ${pdfSettings.customStoragePath}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Ordner-Nummerierung & Sortierung (Dropdown-Menü)
            var isPrefixDropdownExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ordner automatisch nummerieren",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Fügt Ordnern Nummern hinzu (z.B. 01_Wohnung) für eine feste Reihenfolge im Dateimanager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = pdfSettings.useIdPrefixes && pdfSettings.folderPrefixStyle != "NONE",
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val fallbackStyle = if (pdfSettings.folderPrefixStyle == "NONE") "AKTENPLAN_STANDARD" else pdfSettings.folderPrefixStyle
                                viewModel.updatePdfSettings(pdfSettings.copy(useIdPrefixes = true, folderPrefixStyle = fallbackStyle))
                            } else {
                                viewModel.updatePdfSettings(pdfSettings.copy(useIdPrefixes = false, folderPrefixStyle = "NONE"))
                            }
                        }
                    )
                }

                // Dropdown-Menü zur Auswahl der Systematik
                ExposedDropdownMenuBox(
                    expanded = isPrefixDropdownExpanded,
                    onExpandedChange = { isPrefixDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = activePrefixOption.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Nummerierungs-Stil für Ordner") },
                        leadingIcon = {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPrefixDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isPrefixDropdownExpanded,
                        onDismissRequest = { isPrefixDropdownExpanded = false }
                    ) {
                        PREFIX_STYLE_OPTIONS.forEach { option ->
                            val isSelected = option.id == pdfSettings.folderPrefixStyle
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Ausgewählt",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "📁 ${option.example}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    isPrefixDropdownExpanded = false
                                    if (option.id == "NONE") {
                                        viewModel.updatePdfSettings(pdfSettings.copy(folderPrefixStyle = "NONE", useIdPrefixes = false))
                                    } else {
                                        viewModel.updatePdfSettings(pdfSettings.copy(folderPrefixStyle = option.id, useIdPrefixes = true))
                                    }
                                }
                            )
                        }
                    }
                }
            }
                // Live-Vorschau der Ordnerstruktur
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Aktuelle Ordnerpfad-Vorschau:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val folderEx = if (pdfSettings.useIdPrefixes && pdfSettings.folderPrefixStyle != "NONE") {
                            activePrefixOption.example
                        } else {
                            "Versicherungen / Policen"
                        }
                        val docNamingEx = activeDocNamingOption.example
                        Text(
                            text = "📁 [Speicherort] / 📂 $folderEx / 📄 $docNamingEx",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

/**
 * TAB 4: Design & Farbschemata (Dark/Light Mode & Color Skins)
 */
@Composable
fun DesignSettingsTab(viewModel: DocAnizerViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colorSkin by viewModel.colorSkin.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App-Logo Banner
        AppLogoBanner(
            iconHeight = 44.dp,
            fontSize = 24f,
            includeContainer = true,
            showTagline = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            Text(
                text = "Design & Farbschema",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Passe das optische Design und die Akzentfarben von myDocAnizer an",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Helligkeitsmodus
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Anzeigemodus (Helligkeit):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = themeMode == "DARK",
                        onClick = { viewModel.setThemeMode("DARK") },
                        label = { Text("Dunkel") },
                        leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    FilterChip(
                        selected = themeMode == "LIGHT",
                        onClick = { viewModel.setThemeMode("LIGHT") },
                        label = { Text("Hell") },
                        leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    FilterChip(
                        selected = themeMode == "SYSTEM",
                        onClick = { viewModel.setThemeMode("SYSTEM") },
                        label = { Text("System") },
                        leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Color Skins
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = "Color Skins (Farbschema):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Wähle deine bevorzugte Akzent- und Primärfarbe für Schaltflächen, Badges und Menüs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PRESET_COLOR_SKINS.forEach { skin ->
                        val isSelected = colorSkin == skin.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            onClick = { viewModel.setColorSkin(skin.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Farbmuster-Kreise
                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(skin.primaryHex))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(skin.secondaryHex))
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = skin.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Text(
                                            text = skin.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setColorSkin(skin.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // BARRIEREFREIHEIT & SEHHILFEN (WCAG AAA Kontrast, Skalierbarkeit & Touch-Größen)
        val highContrastMode by viewModel.highContrastMode.collectAsState()
        val largeTouchTargets by viewModel.largeTouchTargets.collectAsState()

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Barrierefreiheit & Anzeige-Ergonomie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Unterstützung für Sehbehinderungen, maximale Lesbarkeit & große Touchflächen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 1. Hoher Kontrast-Modus (AAA)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Hoher Kontrast-Modus (WCAG AAA)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Aktiviert tiefschwarze Hintergründe, reinweiße Texte und leuchtende Signal-Akzente für Menschen mit Sehbeeinträchtigungen oder starke Sonneneinstrahlung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = highContrastMode,
                        onCheckedChange = { viewModel.setHighContrastMode(it) }
                    )
                }

                // 2. Extra große Touch-Bedienflächen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Große Touch-Bedienflächen (min. 48-56dp)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Vergrößert Schaltflächen, Checkboxen und Interaktionselemente gemäß den offiziellen Barrierefreiheitsstandards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = largeTouchTargets,
                        onCheckedChange = { viewModel.setLargeTouchTargets(it) }
                    )
                }

                // 3. Status-Hinweis für dynamische System-Schriftgrößen & Screen-Größen
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "System-Schriftgrößen & Displayauflösung",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "✅ Dynamische Textskalierung (sp): myDocAnizer passt alle Texte vollautomatisch an deine Android-Schriftgrößeneinstellung (100% bis 200%) an, ohne dass Schaltflächen oder Bezeichnungen abgeschnitten werden.\n\n✅ Alle Bildschirme und Dialoge verfügen über dynamisches Scrollen und flexibles Wrapping für kompakte Handys, Querformat, Foldables und Tablets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * TAB 6: Cloud-Sync & AES-256-GCM Zero-Knowledge Verschlüsselung
 */
@Composable
fun CloudSyncSettingsTab(viewModel: DocAnizerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncState by viewModel.syncState.collectAsState()
    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val autoSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()

    var passwordInput by remember(syncPasswordKey) { mutableStateOf(syncPasswordKey) }
    var showPassword by remember { mutableStateOf(false) }

    var webDavUrl by remember(cloudSyncConfig.webDavUrl) { mutableStateOf(cloudSyncConfig.webDavUrl) }
    var webDavUser by remember(cloudSyncConfig.webDavUsername) { mutableStateOf(cloudSyncConfig.webDavUsername) }
    var webDavPass by remember(cloudSyncConfig.webDavPassword) { mutableStateOf(cloudSyncConfig.webDavPassword) }
    var isTestingWebDav by remember { mutableStateOf(false) }
    var webDavTestResult by remember { mutableStateOf<String?>(null) }
    var webDavTestSuccess by remember { mutableStateOf(false) }

    val usbDrives by viewModel.usbDrives.collectAsState()
    var showAddUsbDialog by remember { mutableStateOf(false) }
    var newUsbName by remember { mutableStateOf("") }
    var isTestingDrive by remember { mutableStateOf(false) }
    var driveTestResult by remember { mutableStateOf<String?>(null) }

    var isExportingBackup by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var restoreFeedback by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePasswordInput by remember { mutableStateOf(syncPasswordKey) }
    var activeInfoDetailKey by remember { mutableStateOf<String?>(null) }
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    var showP2pPairingDialog by remember { mutableStateOf(false) }

    // Info-Popup Dialog bei Klick auf ein Info-Icon
    if (activeInfoDetailKey != null) {
        val detail = com.example.model.STORAGE_INFO_DETAILS[activeInfoDetailKey]
        if (detail != null) {
            AlertDialog(
                onDismissRequest = { activeInfoDetailKey = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(detail.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = detail.shortSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🔍 Wie funktioniert das?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(detail.howItWorks, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🔒 Schutz vor unberechtigtem Zugriff", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(detail.securityExplanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📱 Was passiert bei Verlust oder Defekt des Handys?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(detail.whatHappensOnLoss, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            text = "💡 Empfehlung: ${detail.recommendation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { activeInfoDetailKey = null }) {
                        Text("Schließen")
                    }
                }
            )
        }
    }

    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val pendingUploadCount = remember(allDocs) {
        allDocs.count { !it.isSynced }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zero-Knowledge Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ende-zu-Ende Verschlüsselung (AES-256-GCM + PBKDF2)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Dokumente und der zentrale Disaster-Recovery-Index (doc_index.enc) werden lokal mit 100.000 PBKDF2-Iterationen und 12-Byte Zufalls-IV verschlüsselt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 1-KLICK-SICHERUNG & EXPORT
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1-Klick-Sicherung & Archiv-Export",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Erzeugt ein vollwertiges, verschlüsseltes Archiv (.enc) aller ${allDocs.size} archivierten Dokumente inklusive Disaster-Recovery-Index für Offline-Backups auf USB, Festplatte oder PC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        isExportingBackup = true
                        exportFeedback = null
                        viewModel.exportEncryptedBackup { file, error ->
                            isExportingBackup = false
                            if (file != null) {
                                val sizeKb = (file.length() / 1024).coerceAtLeast(1)
                                exportFeedback = "Sicherung erfolgreich erstellt!\nDatei: ${file.name} ($sizeKb KB)\nPfad: ${file.parent}"
                            } else {
                                exportFeedback = "Fehler: $error"
                            }
                        }
                    },
                    enabled = !isExportingBackup && allDocs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExportingBackup) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verschlüssele und packe Archiv...")
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verschlüsseltes Archiv (.enc) jetzt erstellen")
                    }
                }

                // WIEDERHERSTELLUNG (RESTORE) BUTTON
                OutlinedButton(
                    onClick = {
                        restoreFeedback = null
                        restoreFilePickerLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !isRestoringBackup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup-Archiv wiederherstellen (.enc)")
                }

                if (exportFeedback != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportFeedback ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (restoreFeedback != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (restoreFeedback?.startsWith("Erfolg") == true) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = restoreFeedback ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (restoreFeedback?.startsWith("Erfolg") == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // DIALOG: WIEDERHERSTELLUNG BESTÄTIGEN & MASTER-PASSWORT PRÜFEN
        if (showRestoreDialog && selectedRestoreUri != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!isRestoringBackup) showRestoreDialog = false
                },
                title = { Text("Backup wiederherstellen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ausgewählte Sicherung: ${selectedRestoreUri?.lastPathSegment ?: "Archiv.enc"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Gib das Master-Passwort ein, mit dem dieses Backup damals verschlüsselt wurde:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = restorePasswordInput,
                            onValueChange = { restorePasswordInput = it },
                            label = { Text("Master-Passwort des Backups") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uri = selectedRestoreUri ?: return@Button
                            isRestoringBackup = true
                            viewModel.restoreEncryptedBackup(uri, restorePasswordInput) { count, error ->
                                isRestoringBackup = false
                                showRestoreDialog = false
                                if (error == null) {
                                    restoreFeedback = "Erfolgreich wiederhergestellt! $count Dokumente wurden importiert und in die Datenbank eingepflegt."
                                } else {
                                    restoreFeedback = "Wiederherstellung fehlgeschlagen: $error"
                                }
                            }
                        },
                        enabled = !isRestoringBackup && restorePasswordInput.isNotBlank()
                    ) {
                        if (isRestoringBackup) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Entschlüssele...")
                        } else {
                            Text("Jetzt wiederherstellen")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRestoreDialog = false },
                        enabled = !isRestoringBackup
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        // MODAL: USB-STICK REGISTRIEREN
        if (showAddUsbDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddUsbDialog = false
                    newUsbName = ""
                },
                title = { Text("USB-Stick registrieren") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Gib dem USB-Stick einen sprechenden Namen (z.B. nach Aufbewahrungsort):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = newUsbName,
                            onValueChange = { newUsbName = it },
                            label = { Text("Stick-Name") },
                            placeholder = { Text("z.B. USB #2 (Eltern / Extern)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newUsbName.isNotBlank()) {
                                viewModel.addUsbDrive(newUsbName.trim())
                                showAddUsbDialog = false
                                newUsbName = ""
                            }
                        },
                        enabled = newUsbName.isNotBlank()
                    ) {
                        Text("Hinzufügen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddUsbDialog = false
                        newUsbName = ""
                    }) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        // SYNCHRONISATIONS-ZIEL & KONTO
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Dauerhafte Sicherung & Ausfallsicherheit (Cloud & NAS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val safetyScore = com.example.model.calculateBackupSafetyScore(cloudSyncConfig)
                val backupSyncHealth by viewModel.backupSyncHealth.collectAsState()

                // 1. LIVE SYNCHRONISATIONS-PRÜFUNG ALLER DEFINIERTEN SPEICHERORTE
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (backupSyncHealth.hasWarnings) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            else Color(0xFF16A34A).copy(alpha = 0.1f),
                    border = BorderStroke(
                        1.5.dp,
                        if (backupSyncHealth.hasWarnings) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        else Color(0xFF16A34A).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("backup_sync_health_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (backupSyncHealth.hasWarnings) Icons.Default.SyncProblem else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (backupSyncHealth.hasWarnings) MaterialTheme.colorScheme.error else Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (backupSyncHealth.hasWarnings) "Automatische Sync-Prüfung: Warnung!" else "Automatische Sync-Prüfung: Aktuell",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (backupSyncHealth.hasWarnings) MaterialTheme.colorScheme.error else Color(0xFF16A34A)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (backupSyncHealth.hasWarnings) MaterialTheme.colorScheme.error else Color(0xFF16A34A)
                            ) {
                                Text(
                                    text = if (backupSyncHealth.hasWarnings) "${backupSyncHealth.unsyncedLocationsCount} unvollständig" else "100% synchron",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Liste der Speicherorte mit individueller Status-Anzeige
                        backupSyncHealth.locationStatuses.forEach { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (status.isSynced) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (status.isSynced) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = status.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (status.warningMessage != null) {
                                            Text(
                                                text = status.warningMessage,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else if (status.lastSyncFormatted != null) {
                                            Text(
                                                text = "Letzte Sicherung: ${status.lastSyncFormatted}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (!status.isSynced) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${status.pendingDocsCount} offen",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // DYNAMISCHES AUSFALLSICHERHEITS-BAROMETER
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(safetyScore.colorHex).copy(alpha = 0.08f),
                    border = BorderStroke(1.5.dp, Color(safetyScore.colorHex).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (safetyScore.scorePercent >= 80) Icons.Default.Shield else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(safetyScore.colorHex),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Schutz-Status: ${safetyScore.scorePercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(safetyScore.colorHex)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(safetyScore.colorHex)
                            ) {
                                Text(
                                    text = safetyScore.badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { safetyScore.scorePercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(safetyScore.colorHex),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Text(
                            text = safetyScore.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Indikatoren
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("🛡️ Schutz vor fremdem Zugriff:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(safetyScore.privacyRating, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("⚠️ Bei Handyverlust / Defekt:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(safetyScore.lossRiskStatus, style = MaterialTheme.typography.labelSmall, color = Color(safetyScore.colorHex), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        TextButton(
                            onClick = { activeInfoDetailKey = "RULE_321" },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Was bedeutet die 3-2-1 Regel?", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // TRANSPARENTER HINWEIS: DATENSICHERHEIT VS. DATENVERLUST-DILEMMA
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Text("Wichtig: Schutz vor Dritten vs. Schutz vor Datenverlust", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "• Vor fremdem Zugriff sicher: Alle Backups (Google Drive & NAS) sind per persönlichem Master-Passwort (AES-256) verschlüsselt. Niemand im Netz kann mitlesen.\n• Vor Totalverlust sicher: Dokumente nur auf dem Handy zu lassen ist riskant. Bei Defekt, Diebstahl oder Wasserschaden sind sie für immer verloren!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text("Verfügbare Speicher-Stufen (beliebig kombinierbar):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                // BASIS: LOKALER SMARTPHONE-APP-SPEICHER
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Basis: Lokaler Smartphone-App-Speicher", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Standard (Immer aktiv)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            IconButton(onClick = { activeInfoDetailKey = "LOCAL_VAULT" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Info, contentDescription = "Erklärung", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Text(
                            text = "🟢 Vorteil: Funktioniert komplett ohne Internet, sofortiger lokaler Zugriff.\n🔴 Risiko: Bei Verlust, Diebstahl oder Defekt des Handys sind ALLE Dokumente unwiederbringlich verloren!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // STUFE 1: REGELMÄSSIGES 1-KLICK BACKUP (USB-STICK / SD / PC)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (cloudSyncConfig.enableManualUsbExport) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, if (cloudSyncConfig.enableManualUsbExport) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = cloudSyncConfig.enableManualUsbExport,
                                    onCheckedChange = { checked ->
                                        viewModel.updateCloudSyncConfig(
                                            cloudSyncConfig.copy(enableManualUsbExport = checked)
                                        )
                                    }
                                )
                                Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                Text("Stufe 1: USB-Stick Sicherung (100% Offline)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = { activeInfoDetailKey = "MANUAL_USB" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Info, contentDescription = "Erklärung", tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }

                        Text(
                            text = "🟢 Vorteil: 100% offline & maximale Datenhoheit. Keine Cloud oder Server nötig.\n🔴 Nachteil: Menschlicher Faktor – neue Dokumente sind erst nach dem nächsten USB-Backup geschützt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (cloudSyncConfig.enableManualUsbExport) {
                            // SICHERHEITSHINWEISE & ANLEITUNG
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("💡 Wichtige Hinweise & Voraussetzungen:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    Text("• Formatierung: Handelsübliche USB-Sticks mit exFAT oder FAT32 werden von Android nativ unterstützt. (exFAT wird besonders für große Sicherungen empfohlen).", style = MaterialTheme.typography.labelSmall)
                                    Text("• Anschluss: USB-C Speicherstick oder normaler USB-A Stick mit einem kleinen USB-OTG-Adapter.", style = MaterialTheme.typography.labelSmall)
                                    Text("• Weiternutzung als normaler Speicher: Dein Stick wird NICHT formatiert! DocAnizer legt lediglich einen eigenen separaten Ordner ('/DocAnizer_Backup/') an. Deine privaten Fotos, Musik und sonstigen Dateien auf dem Stick bleiben komplett erhalten.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                                    Text("• Räumliche Trennung: Lagere mindestens 1 Backup-Stick an einem anderen Ort (z.B. bei Familie oder im Schließfach) für bestmöglichen Schutz.", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // WARNHINWEIS TOGGLE BEI NEUEN SCANS / IMPORTEN
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Warnung bei neuen Scans/Importen", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("Erinnert automatisch daran, die USB-Sticks zu synchronisieren.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = cloudSyncConfig.usbSyncWarningOnScan,
                                        onCheckedChange = { checked ->
                                            viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(usbSyncWarningOnScan = checked))
                                        }
                                    )
                                }
                            }

                            // EINGERICHTETE USB-STICKS
                            Text("Eingerichtete USB-Sticks (${usbDrives.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            usbDrives.forEach { drive ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(drive.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (drive.lastBackupDate.isNullOrBlank()) "Noch nie gesichert" else "Letzte Sicherung: ${drive.lastBackupDate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    viewModel.syncUsbDrive(drive.id) { _, msg ->
                                                        exportFeedback = msg
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Sichern", fontSize = 11.sp)
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeUsbDrive(drive.id) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showAddUsbDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Weiteren USB-Stick registrieren", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // STUFE 2: LOKALES HEIM-NAS & WEBDAV
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (cloudSyncConfig.enableWebDavNas) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, if (cloudSyncConfig.enableWebDavNas) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = cloudSyncConfig.enableWebDavNas,
                                    onCheckedChange = { checked ->
                                        viewModel.updateCloudSyncConfig(
                                            cloudSyncConfig.copy(
                                                enableWebDavNas = checked
                                            )
                                        )
                                    }
                                )
                                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Text("Stufe 2: Lokales Heim-NAS / WebDAV", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { activeInfoDetailKey = "WEBDAV_NAS" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Erklärung anzeigen", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }

                        Text(
                            text = "🟢 Vorteil: Volle Datenhoheit im eigenen Heimnetzwerk (Synology, QNAP, Nextcloud oder FRITZ!Box).\n🔴 Nachteil: Einmalige Server-Einrichtung nötig, Heimnetz/WLAN/VPN erforderlich.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Falls NAS aktiv ist: Konfigurationsfelder + BRANDSCHUTZ-HINWEIS
                        if (cloudSyncConfig.enableWebDavNas) {
                            // ⚠️ WICHTIGER BRANDSCHUTZ-HINWEIS FÜR NAS
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Text("Brandschutz-Hinweis zum Heim-NAS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(
                                        text = "Dein Heim-NAS und dein Smartphone befinden sich üblicherweise in derselben Wohnung. Bei einem Brand, Hochwasser oder Blitzeinschlag werden BEIDE Speichermedien gleichzeitig vernichtet! Nutze ein Heim-NAS daher nie als alleiniges Backup, sondern kombiniere es mit einem extern gelagerten USB-Stick oder Cloud-Tresor.",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = "WebDAV-Konfiguration (Synology, QNAP, Nextcloud, FRITZ!NAS)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = webDavUrl,
                                    onValueChange = {
                                        webDavUrl = it
                                        viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavUrl = it))
                                    },
                                    label = { Text("Server WebDAV-URL") },
                                    placeholder = { Text("https://192.168.1.100/remote.php/dav/files/...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = webDavUser,
                                        onValueChange = {
                                            webDavUser = it
                                            viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavUsername = it))
                                        },
                                        label = { Text("Benutzername") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = webDavPass,
                                        onValueChange = {
                                            webDavPass = it
                                            viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavPassword = it))
                                        },
                                        label = { Text("Passwort / App-Token") },
                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        isTestingWebDav = true
                                        webDavTestResult = null
                                        coroutineScope.launch {
                                            val res = viewModel.testWebDavConnection(webDavUrl, webDavUser, webDavPass)
                                            isTestingWebDav = false
                                            res.onSuccess {
                                                webDavTestSuccess = true
                                                webDavTestResult = it
                                            }.onFailure {
                                                webDavTestSuccess = false
                                                webDavTestResult = it.localizedMessage ?: "Verbindung fehlgeschlagen"
                                            }
                                        }
                                    },
                                    enabled = !isTestingWebDav && webDavUrl.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isTestingWebDav) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Prüfe Verbindung...")
                                    } else {
                                        Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NAS-Verbindung einrichten & testen")
                                    }
                                }

                                if (webDavTestResult != null) {
                                    Text(
                                        text = webDavTestResult ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (webDavTestSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // STUFE 3: GOOGLE DRIVE CLOUD-TRESOR
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (cloudSyncConfig.enableGoogleDrive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, if (cloudSyncConfig.enableGoogleDrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = cloudSyncConfig.enableGoogleDrive,
                                    onCheckedChange = { checked ->
                                        viewModel.updateCloudSyncConfig(
                                            cloudSyncConfig.copy(
                                                enableGoogleDrive = checked
                                            )
                                        )
                                    }
                                )
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Stufe 3: Google Drive Cloud-Tresor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { activeInfoDetailKey = "GOOGLE_DRIVE" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Erklärung anzeigen", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(
                            text = "🟢 Vorteil: Vollautomatisch (Set & Forget). Bietet verlässlichen Schutz vor Geräteverlust, Diebstahl oder Elementarschäden.\n🔴 Hinweis: Dokumente sind vor dem Upload mit deinem Master-Passwort (AES-256) verschlüsselt; Google sieht nur Datenmüll.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (cloudSyncConfig.enableGoogleDrive) {
                            Button(
                                onClick = {
                                    isTestingDrive = true
                                    driveTestResult = null
                                    coroutineScope.launch {
                                        viewModel.testCloudSync("GOOGLE_DRIVE") { success, msg ->
                                            isTestingDrive = false
                                            driveTestResult = if (success) "✅ Google Drive Tresor erfolgreich verbunden & einsatzbereit!" else "Fehler: $msg"
                                        }
                                    }
                                },
                                enabled = !isTestingDrive,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isTestingDrive) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verbindung wird hergestellt...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Google Drive Verbindung einrichten & testen", fontSize = 12.sp)
                                }
                            }

                            driveTestResult?.let { msg ->
                                Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // STUFE 4: LOKALE GERÄTE-VERBINDUNG & SYNC
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Stufe 4: Lokale Geräte-Verbindung & Multi-Device Sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Synchronisiere deinen Dokumenten-Tresor direkt über das lokale Netzwerk mit weiteren Geräten – ohne Cloud, 100% offline & AES-256-GCM verschlüsselt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option 1: Mehrere Android Geräte dauerhaft verbinden
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("Mehrere Android Geräte dauerhaft verbinden", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "dual-sync mittels myDocAnizer-Mobile auf zusätzlichen Gerät",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Verbindet ein zusätzliches Smartphone oder Tablet dauerhaft im WLAN. Vollständiger Datenabgleich in Millisekunden.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                                        showP2pPairingDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Zusätzliches Gerät jetzt verbinden", fontSize = 12.sp)
                                }
                            }
                        }

                        // Option 2: Scanner, Drucker oder Tresor Zugriff auf Windows, macOS oder Linux konfigurieren
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text("Scanner, Drucker oder Tresor Zugriff auf Windows, macOS oder Linux konfigurieren", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { viewModel.launchPcCompanionWizard() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("myDocAnizer-Desktop verbinden / PC-Assistent", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // SYNC TRIGGER & BEDINGUNGEN
                Text(
                    text = "Sync-Auslöser (Trigger)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "AUTO_AFTER_SCAN" to "Nach jedem Scan",
                        "DAILY" to "Täglich",
                        "MANUAL" to "Nur manuell"
                    ).forEach { (trigger, label) ->
                        FilterChip(
                            selected = cloudSyncConfig.syncTrigger == trigger,
                            onClick = { viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(syncTrigger = trigger)) },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nur im WLAN synchronisieren",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Spart Mobilfunk-Datenvolumen. Uploads pausieren automatisch im Mobilnetz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cloudSyncConfig.syncWifiOnly,
                        onCheckedChange = { viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(syncWifiOnly = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hintergrund-Synchronisation aktiv",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Führt die Datensicherung automatisch gemäß Trigger im Hintergrund aus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoCloudSyncEnabled(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // MANUELLER SYNC JETZT AUSFÜHREN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ausstehend: $pendingUploadCount Dokumente",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (lastSyncTime > 0) {
                                val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                                "Letzter Sync: $date"
                            } else "Noch nicht synchronisiert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerCloudSync() },
                        enabled = syncState !is CloudSyncWorkerService.SyncState.Syncing,
                        modifier = Modifier.testTag("btn_trigger_sync_now")
                    ) {
                        if (syncState is CloudSyncWorkerService.SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synchronisiere...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jetzt sichern")
                        }
                    }
                }

                // Sync Fortschritts-Anzeige
                when (val state = syncState) {
                    is CloudSyncWorkerService.SyncState.Syncing -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(state.progressText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is CloudSyncWorkerService.SyncState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Erfolgreich! ${state.uploadedCount} Dokumente verschlüsselt hochgeladen. Disaster-Index aktualisiert.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    is CloudSyncWorkerService.SyncState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // Master-Schlüssel / Verschlüsselungspasswort
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Master-Passwort (AES-256 Verschlüsselungsschlüssel)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dieser Schlüssel leitet den 256-Bit PBKDF2 Schlüssel ab (100.000 Runden). Nur mit diesem Passwort können die verschlüsselten Backups und der Recovery-Index wiederhergestellt werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Verschlüsselungspasswort") },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Passwort verbergen" else "Passwort anzeigen"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var copiedToManager by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("myDocAnizer Master-Key", passwordInput))
                            copiedToManager = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedToManager) "Im Passwort-Manager gemerkt" else "Google Passwort-Manager")
                    }

                    Button(
                        onClick = { viewModel.setSyncPasswordKey(passwordInput) },
                        enabled = passwordInput.isNotBlank() && passwordInput != syncPasswordKey
                    ) {
                        Text("Passwort speichern")
                    }
                }
            }
        }

        // TRANSPARENTE OFFLINE- & DATENSCHUTZ-GARANTIE
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "100% Offline-Standard & Datenschutz-Garantie",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Standardmäßig verlassen keinerlei Daten dein Smartphone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "myDocAnizer verarbeitet alle Scans, Texterkennungen (OCR), KI-Kategorisierungen und Volltextsuchen vollständig lokal auf deinem Smartphone. Es gibt keine Hintergrund-Telemetrie und keine ungefragten Server-Uploads. Netzwerkzugriffe finden ausschließlich dann statt, wenn du gezielt Google Drive oder dein eigenes Heim-NAS aktivierst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showP2pPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = p2pServerStatus.localIp,
            serverPort = p2pServerStatus.port,
            onDismiss = {
                viewModel.stopP2pHostPairingMode()
                showP2pPairingDialog = false
            }
        )
    }
}
