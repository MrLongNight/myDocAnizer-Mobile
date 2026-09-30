package com.example.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppLogoBanner

enum class MainNavigationTab {
    SCANNER,
    DOCANIZER,
    DASHBOARD,
    BATCH_INBOX,
    IMPORT,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DocAnizerViewModel = viewModel()
) {
    val biometricAuthEnabled by viewModel.biometricAuthEnabled.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()

    // Wenn Biometrie aktiviert und App noch gesperrt ist, Lock-Screen anzeigen
    if (biometricAuthEnabled && !isAppUnlocked) {
        AppLockScreen(viewModel = viewModel)
        return
    }

    // App startet direkt im Dokumenten Tresor Modus
    var currentTab by rememberSaveable { mutableStateOf(MainNavigationTab.DOCANIZER) }
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isWizardCompleted by viewModel.isWizardCompleted.collectAsStateWithLifecycle()
    val showManualWizard by viewModel.showManualConfigWizard.collectAsStateWithLifecycle()
    var sessionDismissedWizard by rememberSaveable { mutableStateOf(false) }

    val targetTab by viewModel.targetNavigationTab.collectAsStateWithLifecycle()
    LaunchedEffect(targetTab) {
        targetTab?.let { tabStr ->
            when (tabStr) {
                "SCANNER" -> currentTab = MainNavigationTab.SCANNER
                "DOCANIZER" -> currentTab = MainNavigationTab.DOCANIZER
                "DASHBOARD" -> currentTab = MainNavigationTab.DASHBOARD
                "BATCH_INBOX" -> currentTab = MainNavigationTab.BATCH_INBOX
                "SETTINGS" -> currentTab = MainNavigationTab.SETTINGS
                "IMPORT" -> currentTab = MainNavigationTab.IMPORT
            }
            viewModel.setTargetNavigationTab(null)
        }
    }

    val p2pPendingRequests by viewModel.p2pPendingPairingRequests.collectAsStateWithLifecycle()
    val p2pConflicts by viewModel.p2pActiveConflicts.collectAsStateWithLifecycle()

    // Animation für das Drehen des Plus-Icons im FAB (0° -> 45° wie ein Schließen-Kreuz)
    val fabRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fab_rotation"
    )

    // Android System BackHandler: FAB schließen oder zurück zum Haupt-Tab
    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    BackHandler(enabled = !isFabExpanded && currentTab != MainNavigationTab.DOCANIZER) {
        currentTab = MainNavigationTab.DOCANIZER
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (currentTab != MainNavigationTab.SCANNER) {
                    TopAppBar(
                        modifier = Modifier.height(84.dp),
                        title = {
                            AppLogoBanner(
                                iconHeight = 76.dp,
                                fontSize = 21f,
                                includeContainer = false,
                                showTagline = false,
                                modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                            )
                        },
                        navigationIcon = {
                            if (currentTab == MainNavigationTab.SETTINGS || currentTab == MainNavigationTab.IMPORT) {
                                IconButton(
                                    onClick = {
                                        currentTab = MainNavigationTab.DOCANIZER
                                        isFabExpanded = false
                                    },
                                    modifier = Modifier.testTag("btn_top_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Zurück zum Dokumenten Tresor"
                                    )
                                }
                            }
                        },
                        actions = {
                            // Settings Button nur als Icon ohne Bezeichnung ganz oben rechts
                            if (currentTab != MainNavigationTab.SETTINGS) {
                                IconButton(
                                    onClick = {
                                        isFabExpanded = false
                                        currentTab = MainNavigationTab.SETTINGS
                                    },
                                    modifier = Modifier.testTag("btn_top_settings")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Einstellungen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (currentTab != MainNavigationTab.SCANNER) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("main_bottom_nav_bar")
                    ) {
                        val isDocanizerSelected = currentTab == MainNavigationTab.DOCANIZER
                        NavigationBarItem(
                            selected = isDocanizerSelected,
                            onClick = {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.DOCANIZER
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isDocanizerSelected) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                    contentDescription = "Dokumenten Tresor",
                                    modifier = Modifier.size(if (isDocanizerSelected) 26.dp else 22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Dokumenten Tresor",
                                    fontWeight = if (isDocanizerSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (isDocanizerSelected) 13.sp else 12.sp
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_item_docanizer")
                        )

                        val isDashboardSelected = currentTab == MainNavigationTab.DASHBOARD
                        NavigationBarItem(
                            selected = isDashboardSelected,
                            onClick = {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.DASHBOARD
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isDashboardSelected) Icons.Default.Dashboard else Icons.Default.DashboardCustomize,
                                    contentDescription = "Dashboard",
                                    modifier = Modifier.size(if (isDashboardSelected) 26.dp else 22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Dashboard",
                                    fontWeight = if (isDashboardSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (isDashboardSelected) 13.sp else 12.sp
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_item_dashboard")
                        )

                        val isBatchSelected = currentTab == MainNavigationTab.BATCH_INBOX
                        NavigationBarItem(
                            selected = isBatchSelected,
                            onClick = {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.BATCH_INBOX
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (batchQueue.isNotEmpty()) {
                                            Badge(containerColor = MaterialTheme.colorScheme.error) { Text("${batchQueue.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isBatchSelected) Icons.Default.AllInbox else Icons.Default.Inbox,
                                        contentDescription = "Scan InBox",
                                        modifier = Modifier.size(if (isBatchSelected) 26.dp else 22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = "Scan InBox",
                                    fontWeight = if (isBatchSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (isBatchSelected) 13.sp else 12.sp
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_item_batch_inbox")
                        )
                    }
                }
            }
        ) { innerPadding ->
            val pendingUsbSyncWarning by viewModel.pendingUsbSyncWarning.collectAsState()
            val backupSyncHealth by viewModel.backupSyncHealth.collectAsState()
            val isSyncHealthWarningDismissed by viewModel.isSyncHealthWarningDismissed.collectAsState()

            val showSyncWarning = pendingUsbSyncWarning != null || (backupSyncHealth.hasWarnings && !isSyncHealthWarningDismissed)
            val displayWarningMsg = pendingUsbSyncWarning ?: (
                "⚠️ ${backupSyncHealth.unsyncedLocationsCount} von ${backupSyncHealth.totalConfiguredLocations} Backup-Zielen nicht synchron: " +
                (backupSyncHealth.warnings.firstOrNull() ?: "Sicherung ausstehend")
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(
                    visible = showSyncWarning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = displayWarningMsg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            FilledTonalButton(
                                onClick = {
                                    viewModel.dismissUsbSyncWarning()
                                    viewModel.dismissSyncHealthWarning()
                                    currentTab = MainNavigationTab.SETTINGS
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Sync / Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.dismissUsbSyncWarning()
                                    viewModel.dismissSyncHealthWarning()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // P2P Konflikt-Banner
                AnimatedVisibility(
                    visible = p2pConflicts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(0.dp),
                        border = BorderStroke(1.dp, Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "⚠️ ${p2pConflicts.size} Sync-Konflikt(e) beim WLAN-Sync entdeckt!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { currentTab = MainNavigationTab.SETTINGS },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("Lösen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Crossfade(
                    targetState = currentTab,
                    modifier = Modifier.weight(1f),
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        MainNavigationTab.SCANNER -> {
                            ScannerView(
                                viewModel = viewModel,
                                onDocumentScanned = { _ ->
                                    // Nach dem erfolgreichen Scan direkt zum Dokumenten Tresor wechseln
                                    currentTab = MainNavigationTab.DOCANIZER
                                },
                                onNavigateToBatchInbox = {
                                    currentTab = MainNavigationTab.BATCH_INBOX
                                },
                                onNavigateBack = {
                                    currentTab = MainNavigationTab.DOCANIZER
                                }
                            )
                        }
                        MainNavigationTab.DOCANIZER -> {
                            DmsExplorerView(
                                viewModel = viewModel,
                                onNavigateToImport = { currentTab = MainNavigationTab.IMPORT }
                            )
                        }
                        MainNavigationTab.DASHBOARD -> {
                            DashboardView(
                                viewModel = viewModel,
                                onNavigateToScan = { currentTab = MainNavigationTab.SCANNER },
                                onNavigateToImport = { currentTab = MainNavigationTab.IMPORT }
                            )
                        }
                        MainNavigationTab.BATCH_INBOX -> {
                            BatchInboxView(
                                viewModel = viewModel,
                                onBackToScanner = { currentTab = MainNavigationTab.SCANNER }
                            )
                        }
                        MainNavigationTab.IMPORT -> {
                            ImportView(
                                viewModel = viewModel,
                                onNavigateToDocAnizer = { currentTab = MainNavigationTab.DOCANIZER }
                            )
                        }
                        MainNavigationTab.SETTINGS -> {
                            SettingsView(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        // 1. Abdunklung (Scrim) hinter FAB & Subbuttons: Schließt das FAB-Menü bei Klick daneben
        AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFabExpanded = false
                    }
            )
        }

        // 2. Moderner Speed-Dial FAB unten rechts (BottomEnd) mit Subbuttons nach oben
        if (currentTab != MainNavigationTab.SCANNER) {
            val expandProgress by animateFloatAsState(
                targetValue = if (isFabExpanded) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 400f
                ),
                label = "fab_expand_progress"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 96.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                // Vertikale Subbuttons nach oben (nur sichtbar/klickbar wenn ausgeklappt oder animiert)
                if (isFabExpanded || expandProgress > 0.01f) {
                    // Option 1: Doku-Scan (oberer Subbutton)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (-4.dp.toPx() * expandProgress).toInt(),
                                    y = (-116.dp.toPx() * expandProgress).toInt()
                                )
                            }
                            .graphicsLayer {
                                alpha = expandProgress.coerceIn(0f, 1f)
                                scaleX = 0.4f + 0.6f * expandProgress
                                scaleY = 0.4f + 0.6f * expandProgress
                            }
                            .clickable {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.SCANNER
                            }
                            .testTag("fab_option_doku_scan")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = "Doku-Scan",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Doku-Scan",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Option 2: Datei-Import (mittlerer Subbutton)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (-4.dp.toPx() * expandProgress).toInt(),
                                    y = (-62.dp.toPx() * expandProgress).toInt()
                                )
                            }
                            .graphicsLayer {
                                alpha = expandProgress.coerceIn(0f, 1f)
                                scaleX = 0.4f + 0.6f * expandProgress
                                scaleY = 0.4f + 0.6f * expandProgress
                            }
                            .clickable {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.IMPORT
                            }
                            .testTag("fab_option_file_import")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = "Datei-Import",
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Datei-Import",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Haupt-FAB (+)
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.testTag("main_add_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isFabExpanded) "Menü schließen" else "Hinzufügen",
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(fabRotation)
                    )
                }
            }
        }

        // Geführter Ersteinrichtungs- & Konfigurations-Assistent
        if ((!isWizardCompleted && !sessionDismissedWizard) || showManualWizard) {
            SetupWizardDialog(
                viewModel = viewModel,
                isManualReconfig = showManualWizard,
                onDismissSession = {
                    if (showManualWizard) {
                        viewModel.dismissManualConfigWizard()
                    } else {
                        sessionDismissedWizard = true
                    }
                },
                onFinish = {
                    if (showManualWizard) {
                        viewModel.dismissManualConfigWizard()
                    } else {
                        viewModel.setWizardCompleted(true)
                    }
                }
            )
        }

        // Optionaler PC- & Desktop-Scanner Assistent
        val showPcCompanionWizard by viewModel.showPcCompanionWizard.collectAsState()
        if (showPcCompanionWizard) {
            PcCompanionWizardDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissPcCompanionWizard() }
            )
        }

        // Globaler Autorisierungsdialog für eingehende Kopplungsanfragen
        if (p2pPendingRequests.isNotEmpty()) {
            val req = p2pPendingRequests.first()
            AlertDialog(
                onDismissRequest = { /* Nicht schließen ohne Entscheidung */ },
                icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Zweitgerät autorisieren?", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Das Gerät „${req.clientDeviceName}“ (IP: ${req.clientIp}) möchte sich für den Offline Master-Master-Sync mit deinem Dokumenten Tresor koppeln.")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Eingegebener PIN:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = req.pinEntered,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Möchtest du dieses Gerät als vertrauenswürdigen Master-Sync-Partner autorisieren? Alle Dokumente werden dann bidirektional abgeglichen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("Autorisieren & Koppeln")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = false) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ablehnen")
                    }
                }
            )
        }
    }
}

