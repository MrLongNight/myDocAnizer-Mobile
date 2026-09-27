package com.example.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.DocumentDetailModal
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveFilterSheet {
    NONE,
    SELECT_DIMENSION,
    CATEGORY,
    DOC_TYPE,
    PAGE_COUNT,
    DATE_RANGE,
    SORT
}

/**
 * DocAnizer Explorer:
 * - Reduziertes, übersichtliches Suchfeld ("Suchen...")
 * - State-of-the-Art Progressive Disclosure: Filter werden nur gezielt ausgewählt und eingeblendet
 * - Interaktive Baum-Struktur (Tree View) für Dokument-Typen mit Aufklapp-Funktion
 * - Direkte Anbindung an die dedizierte Import-Funktion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmsExplorerView(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToImport: () -> Unit = {}
) {
    val documents by viewModel.filteredDocuments.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val storageUsage by viewModel.storageUsage.collectAsState()
    val pdfSettings by viewModel.pdfSettings.collectAsState()
    var isStorageOverviewExpanded by remember { mutableStateOf(false) }
    val filterDocType by viewModel.filterDocType.collectAsState()
    val filterMainCategory by viewModel.filterMainCategory.collectAsState()
    val filterSubCategory by viewModel.filterSubCategory.collectAsState()
    val filterDateRange by viewModel.filterDateRange.collectAsState()
    val filterPageFilter by viewModel.filterPageFilter.collectAsState()
    val filterSortBy by viewModel.filterSortBy.collectAsState()
    val activeFilterCount by viewModel.activeFilterCount.collectAsState()
    val docTypes by viewModel.docTypes.collectAsState()

    // Papierkorb, Fristen & Kalender, KI-Chat
    val trashDocs by viewModel.trashDocuments.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()
    val contractDeadlines by viewModel.contractDeadlines.collectAsState()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsState()
    val calendarIntegrationMode by viewModel.calendarIntegrationMode.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Ordner, 1: Gruppen, 2: Fristen & Kalender, 3: Papierkorb
    var selectedDocumentForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var showAiChatDialog by remember { mutableStateOf(false) }

    // Dialog-Zustände für Ordner-Verwaltung
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var parentForNewSubFolder by remember { mutableStateOf<String?>(null) }
    var folderToRename by remember { mutableStateOf<Pair<String, String?>?>(null) } // (mainCat, subCat)
    var folderToDelete by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var showCreateDocTypeDialog by remember { mutableStateOf(false) }

    // Aktiver Filter-Konfigurations-Dialog
    var currentFilterSheet by remember { mutableStateOf(ActiveFilterSheet.NONE) }

    // Aufgeklappte Doc-Typ Knoten in der Baum-Struktur (Set von DocType-Namen)
    var expandedDocTypes by remember { mutableStateOf(setOf<String>()) }

    // Dynamisch verfügbare Ordner aus bestehenden Dokumenten und Templates ermitteln
    val availableMainCategories = remember(allDocs, templates) {
        (allDocs.map { it.mainCategory } + templates.map { it.mainCategory }).filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableSubCategories = remember(allDocs, templates, filterMainCategory) {
        if (filterMainCategory != null) {
            (allDocs.filter { it.mainCategory.equals(filterMainCategory, ignoreCase = true) }.map { it.subCategory } +
             templates.filter { it.mainCategory.equals(filterMainCategory, ignoreCase = true) }.map { it.subCategory })
                .filter { it.isNotBlank() }.distinct().sorted()
        } else {
            (allDocs.map { it.subCategory } + templates.map { it.subCategory }).filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("docanizer_view")
    ) {
        // OBERE SUCHLEISTE & PROGRESSIVE DISCLOSURE FILTER-BAR
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 1. Schlankes Suchfeld mit modernem "Suchen..." Platzhalter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Suchen...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Suche",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Löschen")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("docanizer_search_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Filter-Button mit Badge für aktive Filter
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("$activeFilterCount")
                                }
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = { currentFilterSheet = ActiveFilterSheet.SELECT_DIMENSION },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (activeFilterCount > 0)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("btn_open_filter_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter konfigurieren",
                                tint = if (activeFilterCount > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Lokaler KI-Dokumenten-Chat / Assistent Button
                    FilledTonalIconButton(
                        onClick = { showAiChatDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("btn_open_ai_chat")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "myDocAnizer KI-Assistent",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 2. STATE OF THE ART DYNAMISCHE FILTER-LEISTE (Progressive Disclosure)
                // Zeigt bei aktiven Filtern die explizit aktivierten Filter-Pills an
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Aktiver Ordner-Filter
                        if (filterMainCategory != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.CATEGORY },
                                label = {
                                    Text(
                                        if (filterSubCategory != null) "📁 $filterMainCategory / $filterSubCategory"
                                        else "📁 Ordner: $filterMainCategory"
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                viewModel.setFilterMainCategory(null)
                                                viewModel.setFilterSubCategory(null)
                                            }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Doc-Typ Filter
                    if (filterDocType != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.DOC_TYPE },
                                label = {
                                    Text(if (filterDocType == "__NONE__") "🏷️ Ohne Doc-Typ" else "🏷️ Typ: $filterDocType")
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterDocType(null) }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Seitenanzahl-Filter
                    if (filterPageFilter != "ALL") {
                        val pageLabel = when (filterPageFilter) {
                            "SINGLE" -> "1 Seite"
                            "MULTI" -> "Mehrseitig (≥2)"
                            "3_PLUS" -> "≥3 Seiten"
                            "5_PLUS" -> "≥5 Seiten"
                            else -> filterPageFilter
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.PAGE_COUNT },
                                label = { Text("📄 Seiten: $pageLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterPageFilter("ALL") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Zeitraum-Filter
                    if (filterDateRange != "ALL") {
                        val dateLabel = when (filterDateRange) {
                            "TODAY" -> "Heute"
                            "WEEK" -> "Letzte 7 Tage"
                            "MONTH" -> "Letzte 30 Tage"
                            "YEAR" -> "Dieses Jahr"
                            else -> filterDateRange
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.DATE_RANGE },
                                label = { Text("📅 $dateLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterDateRange("ALL") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Sortier-Filter (falls abweichend von Standard)
                    if (filterSortBy != "DATE_DESC") {
                        val sortLabel = when (filterSortBy) {
                            "DATE_ASC" -> "Älteste zuerst"
                            "TITLE_ASC" -> "Titel A-Z"
                            "TITLE_DESC" -> "Titel Z-A"
                            "PAGES_DESC" -> "Meiste Seiten"
                            else -> "Sortierung"
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.SORT },
                                label = { Text("↕️ $sortLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setSortBy("DATE_DESC") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Alle zurücksetzen
                    if (activeFilterCount > 0) {
                        item {
                            TextButton(
                                onClick = { viewModel.resetAdvancedFilters() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Zurücksetzen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

                Spacer(modifier = Modifier.height(8.dp))

                // Sub-Reiter: Ordner, Gruppen, Fristen & Kalender, Papierkorb
                ScrollableTabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = { Text("Ordner") },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = { Text("Gruppen") },
                        icon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Fristen & Kalender")
                                if (contractDeadlines.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("${contractDeadlines.size}")
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = activeSubTab == 3,
                        onClick = { activeSubTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Papierkorb")
                                if (trashCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("$trashCount", color = MaterialTheme.colorScheme.onError)
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Speicherplatz-Ausnutzung Leiste (Klickbar für Detail-Übersicht)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { isStorageOverviewExpanded = !isStorageOverviewExpanded }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "App-Speicher: ${storageUsage.totalAppFormatted} (${storageUsage.totalDocumentCount} Dok. • ${storageUsage.totalPagesCount} Seiten)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = if (isStorageOverviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isStorageOverviewExpanded && storageUsage.folderUsageList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Speicherplatz nach Ordnern (Ebene 1):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            storageUsage.folderUsageList.forEach { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (folder.mainCategoryId.isNotBlank()) "${folder.mainCategoryId}_${folder.mainCategory}" else folder.mainCategory,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${folder.documentCount} Dok. • ${folder.formattedSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // HAUPTBEREICH: DOKUMENTE & BAUMSTRUKTUR
        if (searchQuery.isNotBlank() || activeFilterCount > 0) {
            // Direkte Trefferliste für schnelle Übersicht bei aktiver Suche
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Keine passenden Dokumente gefunden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Überprüfe deine Suchbegriffe oder passe die aktiven Filter an.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item(key = "search_header_summary") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${documents.size} Treffer gefunden",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = when (filterSortBy) {
                                        "DATE_ASC" -> "Älteste zuerst"
                                        "TITLE_ASC" -> "Titel A-Z"
                                        "TITLE_DESC" -> "Titel Z-A"
                                        "PAGES_DESC" -> "Meiste Seiten"
                                        else -> "Neueste zuerst"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    items(documents, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            searchQuery = searchQuery,
                            onClick = { selectedDocumentForDetail = doc }
                        )
                    }
                }
            }
        } else if (activeSubTab == 0) {
            // 1. ORDNER STRUKTUR (Ebene 1 Hauptkategorie / Ebene 2 Unterordner)
            // Kombiniert alle Kategorien aus Dokumenten und definierten Templates
            val allMainCats = remember(allDocs, templates) {
                (allDocs.map { it.mainCategory } + templates.map { it.mainCategory }).filter { it.isNotBlank() }.distinct().sorted()
            }

            // Vorabberechnung der Ordner- und Unterordnergrößen
            val folderSizes = remember(allDocs) {
                val mainSizes = mutableMapOf<String, String>()
                val subSizes = mutableMapOf<String, String>()

                fun formatBytes(bytes: Long): String = when {
                    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
                    bytes >= 1024 -> "${bytes / 1024} KB"
                    else -> "$bytes B"
                }

                allDocs.groupBy { it.mainCategory }.forEach { (mainCat, mainDocs) ->
                    val mainBytes = mainDocs.sumOf { doc ->
                        val f = File(doc.filePath)
                        if (f.exists()) f.length() else 0L
                    }
                    mainSizes[mainCat] = formatBytes(mainBytes)

                    mainDocs.groupBy { it.subCategory }.forEach { (subCat, subDocs) ->
                        val subBytes = subDocs.sumOf { doc ->
                            val f = File(doc.filePath)
                            if (f.exists()) f.length() else 0L
                        }
                        subSizes["${mainCat}_$subCat"] = formatBytes(subBytes)
                    }
                }
                Pair(mainSizes, subSizes)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Aktionsleiste für Ordner Struktur
                item(key = "folder_structure_action_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ordner Struktur",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalButton(
                            onClick = {
                                parentForNewSubFolder = null
                                showCreateFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_create_main_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Neuer Ordner", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (allMainCats.isEmpty()) {
                    item(key = "empty_folder_state") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Noch keine Ordner vorhanden",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Erstelle jetzt deinen ersten Hauptordner für strukturierte Ablage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        parentForNewSubFolder = null
                                        showCreateFolderDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hauptordner anlegen")
                                }
                            }
                        }
                    }
                }

                allMainCats.forEach { mainCat ->
                    val mainDocs = allDocs.filter { it.mainCategory == mainCat }
                    val mainTmpls = templates.filter { it.mainCategory == mainCat }
                    val mainPrefix = mainDocs.firstOrNull { it.mainCategoryId.isNotBlank() }?.mainCategoryId
                        ?: mainTmpls.firstOrNull { it.mainCategoryId.isNotBlank() }?.mainCategoryId.orEmpty()
                    val mainDocsSizeFormatted = folderSizes.first[mainCat] ?: "0 B"

                    item(key = "header_$mainCat") {
                        var showMainCatMenu by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (mainPrefix.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = mainPrefix,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = mainCat,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${mainDocs.size} ($mainDocsSizeFormatted)",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showMainCatMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Ordner-Aktionen",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMainCatMenu,
                                        onDismissRequest = { showMainCatMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Unterordner erstellen") },
                                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                showMainCatMenu = false
                                                parentForNewSubFolder = mainCat
                                                showCreateFolderDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ordner umbenennen") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                showMainCatMenu = false
                                                folderToRename = Pair(mainCat, null)
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Ordner löschen", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMainCatMenu = false
                                                folderToDelete = Pair(mainCat, null)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Unterordner ermitteln
                    val subCats = (mainDocs.map { it.subCategory } + mainTmpls.map { it.subCategory })
                        .filter { it.isNotBlank() }.distinct().sorted()

                    if (subCats.isEmpty()) {
                        item(key = "sub_empty_$mainCat") {
                            Text(
                                text = "Keine Unterordner vorhanden. Klicke auf '...' um einen Unterordner anzulegen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
                            )
                        }
                    }

                    subCats.forEach { subCat ->
                        val subDocs = mainDocs.filter { it.subCategory == subCat }
                        val subTmpls = mainTmpls.filter { it.subCategory == subCat }
                        val subPrefix = subDocs.firstOrNull { it.subCategoryId.isNotBlank() }?.subCategoryId
                            ?: subTmpls.firstOrNull { it.subCategoryId.isNotBlank() }?.subCategoryId.orEmpty()
                        val subSizeFormatted = folderSizes.second["${mainCat}_$subCat"] ?: "0 B"

                        item(key = "sub_${mainCat}_$subCat") {
                            var showSubCatMenu by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (subPrefix.isNotBlank()) "↳ $subPrefix-$subCat" else "↳ $subCat",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${subDocs.size} Dok. • $subSizeFormatted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showSubCatMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Unterordner-Aktionen",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showSubCatMenu,
                                            onDismissRequest = { showSubCatMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Unterordner umbenennen") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showSubCatMenu = false
                                                    folderToRename = Pair(mainCat, subCat)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Unterordner löschen", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showSubCatMenu = false
                                                    folderToDelete = Pair(mainCat, subCat)
                                                }
                                            )
                                        }
                                    }
                                }

                                if (subDocs.isEmpty()) {
                                    Text(
                                        text = "Dieser Unterordner ist leer.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                } else {
                                    subDocs.forEach { doc ->
                                        DocumentCard(
                                            document = doc,
                                            searchQuery = searchQuery,
                                            onClick = { selectedDocumentForDetail = doc }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == 1) {
            // 2. INTERAKTIVE BAUM-STRUKTUR (TREE VIEW) FÜR DOKUMENT-TYPEN
            // Nach Doc-Typ gruppiert, jeweils bei Bedarf aufklappbar
            val groupedByType = remember(documents) {
                documents.groupBy { if (it.docType.isNotBlank()) it.docType else "Ohne Doc-Typ" }
            }
            val allTypeKeys = groupedByType.keys.toList()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Tree Header Aktionen (Alle aufklappen / zuklappen & Neue Gruppe erstellen)
                item(key = "tree_view_action_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dokumenten Gruppen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { expandedDocTypes = allTypeKeys.toSet() },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Alle auf", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { expandedDocTypes = emptySet() },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Alle zu", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { showCreateDocTypeDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_create_doc_group")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gruppe", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Jeder Doc-Typ als interaktiver Baum-Knoten
                groupedByType.forEach { (docTypeName, typeDocs) ->
                    val isExpanded = expandedDocTypes.contains(docTypeName)
                    val docTypeColor = docTypes.firstOrNull { it.name == docTypeName }?.colorHex ?: 0xFF2563EB

                    item(key = "tree_node_$docTypeName") {
                        DocTypeTreeNodeHeader(
                            docTypeName = docTypeName,
                            docCount = typeDocs.size,
                            isExpanded = isExpanded,
                            colorHex = docTypeColor,
                            onToggle = {
                                expandedDocTypes = if (isExpanded) {
                                    expandedDocTypes - docTypeName
                                } else {
                                    expandedDocTypes + docTypeName
                                }
                            }
                        )
                    }

                    // Untergeordnete Dokumente im aufgeklappten Zustand
                    if (isExpanded) {
                        items(typeDocs, key = { "tree_item_${docTypeName}_${it.id}" }) { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp)
                            ) {
                                // Visuelle Baum-Führungslinie
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(72.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    DocumentCard(
                                        document = doc,
                                        searchQuery = searchQuery,
                                        onClick = { selectedDocumentForDetail = doc }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == 2) {
            // 3. FRISTEN- & KALENDER-ZENTRALE
            CalendarAndDeadlinesTab(
                documentsWithDeadlines = contractDeadlines,
                calendarIntegrationMode = calendarIntegrationMode,
                onSelectDocument = { selectedDocumentForDetail = it },
                onSetCalendarMode = { viewModel.setCalendarIntegrationMode(it) }
            )
        } else if (activeSubTab == 3) {
            // 4. MÜLLEIMER / PAPIERKORB
            TrashRecycleBinTab(
                trashDocuments = trashDocs,
                trashRetentionDays = trashRetentionDays,
                onRestoreDocument = { viewModel.restoreDocument(it) },
                onPermanentlyDeleteDocument = { viewModel.permanentlyDeleteDocument(it) },
                onEmptyTrash = { viewModel.emptyTrash() },
                onSetRetentionDays = { viewModel.setTrashRetentionDays(it) }
            )
        }
    }

    // MODAL-SHEET: FILTER-DIMENSION AUSWÄHLEN (Progressive Disclosure Auswahl)
    if (currentFilterSheet == ActiveFilterSheet.SELECT_DIMENSION) {
        ModalBottomSheet(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter auswählen & anpassen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeFilterCount > 0) {
                        TextButton(
                            onClick = {
                                viewModel.resetAdvancedFilters()
                                currentFilterSheet = ActiveFilterSheet.NONE
                            }
                        ) {
                            Text("Alle zurücksetzen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Ordner
                FilterCategoryTile(
                    icon = Icons.Default.Folder,
                    title = "Ordner / Kategorie",
                    currentValue = if (filterMainCategory != null) "$filterMainCategory" + (filterSubCategory?.let { " / $it" } ?: "") else "Alle Ordner",
                    isActive = filterMainCategory != null,
                    onClick = { currentFilterSheet = ActiveFilterSheet.CATEGORY }
                )

                // 2. Dokumenten-Gruppen
                FilterCategoryTile(
                    icon = Icons.Default.Bookmarks,
                    title = "Dokumenten-Gruppen",
                    currentValue = filterDocType ?: "Alle Gruppen",
                    isActive = filterDocType != null,
                    onClick = { currentFilterSheet = ActiveFilterSheet.DOC_TYPE }
                )

                // 3. Seitenanzahl
                FilterCategoryTile(
                    icon = Icons.Default.FilterFrames,
                    title = "Seitenanzahl",
                    currentValue = when (filterPageFilter) {
                        "SINGLE" -> "Nur 1 Seite"
                        "MULTI" -> "Mehrseitig (≥2)"
                        "3_PLUS" -> "≥3 Seiten"
                        "5_PLUS" -> "≥5 Seiten"
                        else -> "Alle Seiten"
                    },
                    isActive = filterPageFilter != "ALL",
                    onClick = { currentFilterSheet = ActiveFilterSheet.PAGE_COUNT }
                )

                // 4. Zeitraum
                FilterCategoryTile(
                    icon = Icons.Default.DateRange,
                    title = "Zeitraum / Datum",
                    currentValue = when (filterDateRange) {
                        "TODAY" -> "Heute"
                        "WEEK" -> "Letzte 7 Tage"
                        "MONTH" -> "Letzte 30 Tage"
                        "YEAR" -> "Dieses Jahr"
                        else -> "Gesamter Zeitraum"
                    },
                    isActive = filterDateRange != "ALL",
                    onClick = { currentFilterSheet = ActiveFilterSheet.DATE_RANGE }
                )

                // 5. Sortierung
                FilterCategoryTile(
                    icon = Icons.Default.Sort,
                    title = "Sortierung",
                    currentValue = when (filterSortBy) {
                        "DATE_ASC" -> "Älteste zuerst"
                        "TITLE_ASC" -> "Titel A-Z"
                        "TITLE_DESC" -> "Titel Z-A"
                        "PAGES_DESC" -> "Meiste Seiten"
                        else -> "Neueste zuerst"
                    },
                    isActive = filterSortBy != "DATE_DESC",
                    onClick = { currentFilterSheet = ActiveFilterSheet.SORT }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // SPEZIFISCHER FILTER-DIALOG: ORDNER
    if (currentFilterSheet == ActiveFilterSheet.CATEGORY) {
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Ordner-Filter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hauptordner wählen:", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        onClick = {
                            viewModel.setFilterMainCategory(null)
                            viewModel.setFilterSubCategory(null)
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterMainCategory == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Alle Ordner (Filter entfernen)", modifier = Modifier.padding(12.dp))
                    }
                    availableMainCategories.forEach { cat ->
                        Surface(
                            onClick = {
                                viewModel.setFilterMainCategory(cat)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterMainCategory == cat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📁 $cat", modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: DOKUMENT-TYP
    if (currentFilterSheet == ActiveFilterSheet.DOC_TYPE) {
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Dokument-Typ Filter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            viewModel.setFilterDocType(null)
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterDocType == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Alle Dokument-Typen", modifier = Modifier.padding(12.dp))
                    }
                    docTypes.forEach { dt ->
                        Surface(
                            onClick = {
                                viewModel.setFilterDocType(dt.name)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterDocType == dt.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(dt.colorHex))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(dt.name)
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            viewModel.setFilterDocType("__NONE__")
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterDocType == "__NONE__") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ohne Dokument-Typ", modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: SEITENANZAHL
    if (currentFilterSheet == ActiveFilterSheet.PAGE_COUNT) {
        val options = listOf(
            "ALL" to "Alle Seiten anzeigen",
            "SINGLE" to "Nur 1 Seite (Einzelseiter)",
            "MULTI" to "Mehrseitig (≥2 Seiten)",
            "3_PLUS" to "Mindestens 3 Seiten",
            "5_PLUS" to "Mindestens 5 Seiten"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Seitenanzahl") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setFilterPageFilter(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterPageFilter == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: ZEITRAUM
    if (currentFilterSheet == ActiveFilterSheet.DATE_RANGE) {
        val dateOptions = listOf(
            "ALL" to "Gesamter Zeitraum",
            "TODAY" to "Heute",
            "WEEK" to "Letzte 7 Tage",
            "MONTH" to "Letzte 30 Tage",
            "YEAR" to "Dieses Jahr"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Zeitraum") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dateOptions.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setFilterDateRange(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterDateRange == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: SORTIERUNG
    if (currentFilterSheet == ActiveFilterSheet.SORT) {
        val sortOptions = listOf(
            "DATE_DESC" to "Neueste zuerst",
            "DATE_ASC" to "Älteste zuerst",
            "TITLE_ASC" to "Titel A-Z",
            "TITLE_DESC" to "Titel Z-A",
            "PAGES_DESC" to "Meiste Seiten"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Sortierung") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortOptions.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setSortBy(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterSortBy == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // DETAIL-MODAL FÜR EIN DOKUMENT
    selectedDocumentForDetail?.let { doc ->
        DocumentDetailModal(
            document = doc,
            availableDocTypes = docTypes,
            onUpdateDocType = { docId, newType ->
                viewModel.updateDocumentDocType(docId, newType)
            },
            onDelete = { d ->
                viewModel.deleteDocument(d)
            },
            onUpdateContractReminder = { docId, endDate, cancelDate, remDays, hasRem, eventType, notes, amt ->
                viewModel.updateContractReminders(docId, endDate, cancelDate, remDays, hasRem, eventType, notes, amt)
            },
            onDismissRequest = { selectedDocumentForDetail = null }
        )
    }

    // LOKALER KI-DOKUMENTEN-ASSISTENT CHAT
    if (showAiChatDialog) {
        AiDocumentAssistantDialog(
            chatMessages = chatMessages,
            onSendMessage = { viewModel.sendChatMessage(it) },
            onClearChat = { viewModel.clearChat() },
            onDismissRequest = { showAiChatDialog = false }
        )
    }

    // DIALOG: NEUER ORDNER / UNTERORDNER
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentCategory = parentForNewSubFolder,
            existingMainCategories = availableMainCategories,
            prefixesEnabled = pdfSettings.useIdPrefixes,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { mainCat, mainPrefix, subCat, subPrefix ->
                viewModel.saveFolderStructure(mainCat, mainPrefix, subCat, subPrefix)
                showCreateFolderDialog = false
            }
        )
    }

    // DIALOG: ORDNER / UNTERORDNER UMBENENNEN (Präfix bleibt geschützt)
    folderToRename?.let { (mainCat, subCat) ->
        val isSub = subCat != null
        val currentPrefix = if (isSub) {
            allDocs.firstOrNull { it.mainCategory == mainCat && it.subCategory == subCat }?.subCategoryId
                ?: templates.firstOrNull { it.mainCategory == mainCat && it.subCategory == subCat }?.subCategoryId.orEmpty()
        } else {
            allDocs.firstOrNull { it.mainCategory == mainCat }?.mainCategoryId
                ?: templates.firstOrNull { it.mainCategory == mainCat }?.mainCategoryId.orEmpty()
        }
        val currentName = subCat ?: mainCat

        RenameFolderDialog(
            currentName = currentName,
            prefix = currentPrefix,
            isSubCategory = isSub,
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                if (isSub && subCat != null) {
                    viewModel.renameSubCategory(mainCat, subCat, newName)
                } else {
                    viewModel.renameMainCategory(mainCat, newName)
                }
                folderToRename = null
            }
        )
    }

    // DIALOG: ORDNER LÖSCHEN (MIT DOPPELTER SICHERHEITSABFRAGE WENN INHALTE EXISTIEREN)
    folderToDelete?.let { (mainCat, subCat) ->
        val isSub = subCat != null
        val affectedDocs = if (isSub) {
            allDocs.filter { it.mainCategory == mainCat && it.subCategory == subCat }
        } else {
            allDocs.filter { it.mainCategory == mainCat }
        }
        val folderDisplayName = if (isSub) "$mainCat ↳ $subCat" else mainCat

        DeleteFolderDialog(
            folderName = folderDisplayName,
            docCount = affectedDocs.size,
            onDismiss = { folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolderStructure(mainCat, subCat, deleteContainedDocuments = true)
                folderToDelete = null
            }
        )
    }

    // DIALOG: NEUE DOKUMENTEN-GRUPPE ANLEGEN
    if (showCreateDocTypeDialog) {
        CreateDocTypeDialog(
            onDismiss = { showCreateDocTypeDialog = false },
            onConfirm = { name, prefix, colorHex ->
                viewModel.addDocType(DocTypeItem(name = name, description = prefix, colorHex = colorHex))
                showCreateDocTypeDialog = false
            }
        )
    }
}

@Composable
private fun FilterCategoryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    currentValue: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun DocTypeTreeNodeHeader(
    docTypeName: String,
    docCount: Int,
    isExpanded: Boolean,
    colorHex: Long,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "chevron"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("tree_node_$docTypeName")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(colorHex))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = docTypeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isExpanded) "Tippen zum Zuklappen" else "Tippen zum Aufklappen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "$docCount Dok.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: DocumentEntity,
    searchQuery: String = "",
    onClick: () -> Unit
) {
    val isImage = document.fileName.endsWith(".jpg", ignoreCase = true) ||
            document.fileName.endsWith(".png", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("document_card_${document.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isImage) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isImage) Icons.Default.Image else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = if (isImage) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                val metadataLine = buildString {
                    append("📁 ")
                    append(document.mainCategory)
                    if (document.subCategory.isNotBlank()) {
                        append(" / ")
                        append(document.subCategory)
                    }
                    append(" • ")
                    if (isImage) {
                        append("Bild-Datei")
                    } else if (document.pageCount > 1) {
                        append("${document.pageCount} Seiten")
                    } else {
                        append("1 Seite")
                    }
                    append(" • ")
                    append(formatDate(document.createdAt))
                }

                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // OCR-Treffer Vorschau bei Volltextsuche
                if (searchQuery.isNotBlank() && document.ocrText.isNotBlank()) {
                    val tokens = searchQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val matchedToken = tokens.firstOrNull { document.ocrText.contains(it, ignoreCase = true) }
                    if (matchedToken != null) {
                        val idx = document.ocrText.indexOf(matchedToken, ignoreCase = true)
                        val start = (idx - 25).coerceAtLeast(0)
                        val end = (idx + matchedToken.length + 35).coerceAtMost(document.ocrText.length)
                        val snippet = document.ocrText.substring(start, end).replace("\n", " ").trim()

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🔍 Treffer im Text: „…$snippet…“",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (document.docType.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = document.docType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isImage) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "JPG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (document.pageCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${document.pageCount} Seiten",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = document.fileSizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}

@Composable
fun CreateFolderDialog(
    parentCategory: String?,
    existingMainCategories: List<String>,
    prefixesEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (mainCat: String, mainPrefix: String, subCat: String, subPrefix: String) -> Unit
) {
    var selectedMainCategory by remember { mutableStateOf(parentCategory ?: existingMainCategories.firstOrNull().orEmpty()) }
    var newMainCategory by remember { mutableStateOf("") }
    var mainPrefix by remember { mutableStateOf("") }
    var subCategory by remember { mutableStateOf("") }
    var subPrefix by remember { mutableStateOf("") }
    var isNewMainCategoryMode by remember { mutableStateOf(parentCategory == null && existingMainCategories.isEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (parentCategory != null) "Neuer Unterordner" else "Neuer Ordner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (parentCategory != null) {
                    Text(
                        text = "Hauptordner: $parentCategory",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    if (existingMainCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isNewMainCategoryMode,
                                onClick = { isNewMainCategoryMode = false },
                                label = { Text("Bestehender Hauptordner") }
                            )
                            FilterChip(
                                selected = isNewMainCategoryMode,
                                onClick = { isNewMainCategoryMode = true },
                                label = { Text("Neuer Hauptordner") }
                            )
                        }
                    }

                    if (isNewMainCategoryMode || existingMainCategories.isEmpty()) {
                        if (prefixesEnabled) {
                            OutlinedTextField(
                                value = mainPrefix,
                                onValueChange = { mainPrefix = it.take(6) },
                                label = { Text("Hauptordner-Präfix (z.B. 01)") },
                                placeholder = { Text("01") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = newMainCategory,
                            onValueChange = { newMainCategory = it },
                            label = { Text("Name des Hauptordners") },
                            placeholder = { Text("z.B. Finanzen, Verträge") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Unterordner Eingabe
                HorizontalDivider()
                Text(
                    text = "Unterordner (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                if (prefixesEnabled) {
                    OutlinedTextField(
                        value = subPrefix,
                        onValueChange = { subPrefix = it.take(6) },
                        label = { Text("Unterordner-Präfix (z.B. 01_01)") },
                        placeholder = { Text("01_01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = subCategory,
                    onValueChange = { subCategory = it },
                    label = { Text("Name des Unterordners (optional)") },
                    placeholder = { Text("z.B. Rechnungen, Gehalt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMain = if (parentCategory != null) {
                        parentCategory
                    } else if (isNewMainCategoryMode || existingMainCategories.isEmpty()) {
                        newMainCategory.trim()
                    } else {
                        selectedMainCategory.trim()
                    }

                    if (finalMain.isNotBlank()) {
                        onConfirm(finalMain, mainPrefix.trim(), subCategory.trim(), subPrefix.trim())
                    }
                },
                enabled = (parentCategory != null || (isNewMainCategoryMode && newMainCategory.isNotBlank()) || (!isNewMainCategoryMode && selectedMainCategory.isNotBlank()))
            ) {
                Text("Erstellen")
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
fun RenameFolderDialog(
    currentName: String,
    prefix: String,
    isSubCategory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSubCategory) "Unterordner umbenennen" else "Hauptordner umbenennen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (prefix.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Präfix: $prefix (geschützt)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Neuer Ordnername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Hinweis: Es wird nur die Bezeichnung geändert. Alle enthaltenen Dokumente bleiben unberührt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    }
                },
                enabled = newName.isNotBlank() && newName.trim() != currentName
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

@Composable
fun DeleteFolderDialog(
    folderName: String,
    docCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // Step 1: Vorwarnung, Step 2: Doppelte Sicherheitsabfrage

    if (docCount == 0) {
        // Einfacher Bestätigungsdialog da Ordner leer ist
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ordner löschen?") },
            text = {
                Text("Möchtest du den leeren Ordner \"$folderName\" wirklich löschen?")
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        )
    } else {
        // DOPPELTE SICHERHEITSABFRAGE MIT DOKUMENTEN-WARNUNG
        if (step == 1) {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Ordner enthält Dokumente!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Achtung: Der Ordner \"$folderName\" enthält aktuell $docCount Dokument(e).",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Wenn du fortfährst, werden alle darin abgelegten Dokumente ebenfalls gelöscht.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { step = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Weiter zum Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        } else {
            // STEP 2: ZWEITE SICHERHEITSABFRAGE
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Endgültige Bestätigung") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Bist du dir absolut sicher?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Möchtest du den Ordner \"$folderName\" samt den $docCount Dokumenten unwiderruflich löschen? Diese Aktion kann nicht rückgängig gemacht werden.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ja, Ordner & $docCount Dokumente löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}

@Composable
fun CreateDocTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, prefix: String, colorHex: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    val colorOptions = listOf(
        0xFF2563EB, // Blau
        0xFF16A34A, // Grün
        0xFFD97706, // Bernstein
        0xFFDC2626, // Rot
        0xFF9333EA, // Lila
        0xFF0D9488, // Türkis
        0xFFE11D48, // Rose
        0xFF4F46E5  // Indigo
    )
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Neue Dokumenten-Gruppe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name der Gruppe") },
                    placeholder = { Text("z.B. Versicherung, Rechnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.take(4) },
                    label = { Text("Kürzel (optional, max 4 Zeichen)") },
                    placeholder = { Text("z.B. RE, V") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Farbe auswählen:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .clickable { selectedColor = colorHex },
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
                        onConfirm(name.trim(), prefix.trim(), selectedColor)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Hinzufügen")
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
 * Reiter 3: Fristen- & Kalender-Zentrale
 * Verträge, Kündigungsfristen, Laufzeiten mit Dringlichkeits-Farbcodierung & Google-Kalender Export
 */
@Composable
private fun CalendarAndDeadlinesTab(
    documentsWithDeadlines: List<DocumentEntity>,
    calendarIntegrationMode: String,
    onSelectDocument: (DocumentEntity) -> Unit,
    onSetCalendarMode: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
    val now = System.currentTimeMillis()

    val urgentCount = documentsWithDeadlines.count {
        val target = it.cancellationDeadline ?: it.contractEndDate ?: 0L
        target in now..(now + 30L * 24L * 60L * 60L * 1000L)
    }

    val totalCosts = documentsWithDeadlines.mapNotNull { it.amount }.sum()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Übersichtskarte mit Dringlichkeits-Zähler & Kalender-Modus
        item(key = "calendar_summary_card") {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Verträge & Fristen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (urgentCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "$urgentCount dringend",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Laufende Verträge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${documentsWithDeadlines.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (totalCosts > 0.0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Erfasste Monats-/Jahresbeiträge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.GERMAN, "%.2f €", totalCosts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Kalender-Integrationsmodus
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Kalender-Synchronisation:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = calendarIntegrationMode == "INTERNAL",
                            onClick = { onSetCalendarMode("INTERNAL") },
                            label = { Text("Nur intern", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = calendarIntegrationMode == "EXTERNAL",
                            onClick = { onSetCalendarMode("EXTERNAL") },
                            label = { Text("Google Kalender", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = calendarIntegrationMode == "BOTH",
                            onClick = { onSetCalendarMode("BOTH") },
                            label = { Text("Beides", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (documentsWithDeadlines.isEmpty()) {
            item(key = "calendar_empty_state") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Keine Fristen oder Termine hinterlegt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tippe bei einem beliebigen Dokument im Explorer auf '+ Frist setzen', um Mindestlaufzeiten, Kündigungstermine und Erinnerungen zu hinterlegen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = documentsWithDeadlines.sortedBy { it.cancellationDeadline ?: it.contractEndDate ?: Long.MAX_VALUE },
                key = { "deadline_${it.id}" }
            ) { doc ->
                val deadlineDate = doc.cancellationDeadline ?: doc.contractEndDate ?: 0L
                val daysRemaining = ((deadlineDate - now) / (1000 * 60 * 60 * 24)).toInt()

                val urgencyColor = when {
                    daysRemaining < 0 -> MaterialTheme.colorScheme.error
                    daysRemaining < 14 -> MaterialTheme.colorScheme.error
                    daysRemaining < 45 -> Color(0xFFF57C00) // Orange
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDocument(doc) }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (doc.amount != null && doc.amount > 0.0) {
                                Text(
                                    text = String.format(Locale.GERMAN, "%.2f €", doc.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "Absender: ${doc.sender.ifBlank { "Unbekannt" }} • ${doc.mainCategory}/${doc.subCategory}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Frist-Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = urgencyColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (daysRemaining < 14) Icons.Default.Warning else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = urgencyColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (doc.cancellationDeadline != null) {
                                        "Kündigen bis: ${sdf.format(Date(doc.cancellationDeadline))} (${if (daysRemaining >= 0) "noch $daysRemaining Tage" else "Frist abgelaufen"})"
                                    } else {
                                        "Laufzeitende: ${sdf.format(Date(deadlineDate))} (${if (daysRemaining >= 0) "noch $daysRemaining Tage" else "Vorbei"})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = urgencyColor
                                )
                            }
                        }

                        if (doc.reminderNotes.isNotBlank()) {
                            Text(
                                text = "Hinweis: ${doc.reminderNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val desc = "Kündigungsfrist für ${doc.title} (${doc.sender}). Notiz: ${doc.reminderNotes}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                            data = android.provider.CalendarContract.Events.CONTENT_URI
                                            putExtra(android.provider.CalendarContract.Events.TITLE, "Kündigungsfrist: ${doc.title}")
                                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadlineDate)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, deadlineDate + 3600000L)
                                            putExtra(android.provider.CalendarContract.Events.ALL_DAY, true)
                                            putExtra(android.provider.CalendarContract.Events.HAS_ALARM, 1)
                                        }
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Kalender-App nicht gefunden", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In Google Kalender", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reiter 4: Mülleimer / Papierkorb
 * Soft-Delete Ablage mit Wiederherstellung, dauerhafter Bereinigung & einstellbarer Aufbewahrungsdauer
 */
@Composable
private fun TrashRecycleBinTab(
    trashDocuments: List<DocumentEntity>,
    trashRetentionDays: Int,
    onRestoreDocument: (DocumentEntity) -> Unit,
    onPermanentlyDeleteDocument: (DocumentEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onSetRetentionDays: (Int) -> Unit
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Papierkorb unwiderruflich leeren?") },
            text = {
                Text("Alle ${trashDocuments.size} Dokumente im Papierkorb werden endgültig vom Smartphone gelöscht und können nicht mehr wiederhergestellt werden.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Endgültig leeren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Papierkorb Einstellungs- & Aktionsleiste
        item(key = "trash_header_actions") {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Mülleimer / Papierkorb", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (trashDocuments.isNotEmpty()) {
                            TextButton(
                                onClick = { showEmptyConfirmDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Papierkorb leeren", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Automatische Aufbewahrungsdauer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatische Löschung nach:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (trashRetentionDays == 0) "Nie (Nur manuell leeren)" else "$trashRetentionDays Tagen",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { showRetentionMenu = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Frist ändern", style = MaterialTheme.typography.labelSmall)
                            }

                            DropdownMenu(
                                expanded = showRetentionMenu,
                                onDismissRequest = { showRetentionMenu = false }
                            ) {
                                val retentionOptions = listOf(
                                    7 to "7 Tage",
                                    14 to "14 Tage",
                                    30 to "30 Tage (Standard)",
                                    60 to "60 Tage",
                                    90 to "90 Tage",
                                    0 to "Nie automatisch löschen"
                                )
                                retentionOptions.forEach { (days, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (trashRetentionDays == days) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            onSetRetentionDays(days)
                                            showRetentionMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (trashDocuments.isEmpty()) {
            item(key = "trash_empty_state") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Der Papierkorb ist leer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gelöschte Dokumente landen zuerst hier und können jederzeit wiederhergestellt werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(trashDocuments, key = { "trash_${it.id}" }) { doc ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Ursprünglicher Ordner: ${doc.mainCategory}/${doc.subCategory} • Absender: ${doc.sender.ifBlank { "Unbekannt" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (doc.deletedAt != null) {
                            Text(
                                text = "Gelöscht am: ${sdf.format(Date(doc.deletedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRestoreDocument(doc) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wiederherstellen", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { onPermanentlyDeleteDocument(doc) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Endgültig weg", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 100% On-Device & Offline KI Dokumenten-Assistent Bottom-Sheet
 * Beantwortet natürliche Fragen wie "Wie viele laufende Verträge habe ich und welche sind es?"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDocumentAssistantDialog(
    chatMessages: List<com.example.model.ChatMessage>,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "myDocAnizer KI-Assistent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100% On-Device & Offline RAG-Suche",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onClearChat) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Chat leeren", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Question Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    SuggestionChip(
                        onClick = { onSendMessage("Wie viele laufende Verträge habe ich und welche sind es?") },
                        label = { Text("📋 Laufende Verträge?", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { onSendMessage("Welche Kündigungsfristen stehen demnächst an?") },
                        label = { Text("⏰ Kündigungsfristen?", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { onSendMessage("Wie hoch sind meine Ausgaben und Beiträge?") },
                        label = { Text("💶 Vertragskosten?", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { onSendMessage("Welche Dokumente habe ich in der Kategorie Verträge?") },
                        label = { Text("📁 Ordner-Suche", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chat-Verlauf
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatMessages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (msg.isUser) 14.dp else 2.dp,
                                bottomEnd = if (msg.isUser) 2.dp else 14.dp
                            ),
                            color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Frage zu deinen Dokumenten...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Senden",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
