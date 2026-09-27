package com.example.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BatchDocumentItem
import com.example.model.BatchItemStatus
import com.example.ui.DocAnizerViewModel

/**
 * Review-Inbox & Stapel-Puffer:
 * Erlaubt das schnelle Abarbeiten von eingescannten Dokumentenstapeln mit
 * 1-Klick-Übernahme, visueller Anzeige (Schlagwort-Regel vs. KI-Vorschlag)
 * und Korrektur-Möglichkeit vor dem finalen Abspeichern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInboxView(
    viewModel: DocAnizerViewModel,
    onBackToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isProcessingBatchQueue by viewModel.isProcessingBatchQueue.collectAsStateWithLifecycle()
    val pendingRuleSuggestion by viewModel.pendingRuleSuggestion.collectAsStateWithLifecycle()
    val docTypes by viewModel.docTypes.collectAsStateWithLifecycle()
    var editingItem by remember { mutableStateOf<BatchDocumentItem?>(null) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    val pendingCount = batchQueue.count { it.status == BatchItemStatus.PENDING_PROCESS || it.status == BatchItemStatus.WAITING_OCR }

    BackHandler {
        if (editingItem != null) {
            editingItem = null
        } else {
            onBackToScanner()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scanner InBox & Prüfung",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${batchQueue.size} Dokument(e) im Post-Processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToScanner,
                        modifier = Modifier.testTag("btn_back_from_inbox")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zum Scanner")
                    }
                },
                actions = {
                    if (batchQueue.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.commitAllBatchItems { savedCount ->
                                    snackbarMessage = "$savedCount Dokument(e) erfolgreich archiviert!"
                                    showSuccessSnackbar = true
                                }
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("btn_commit_all_batch")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Alle archivieren", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            if (showSuccessSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showSuccessSnackbar = false }) {
                            Text("OK", color = Color.White)
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(snackbarMessage)
                }
            }
        },
        modifier = modifier
    ) { padding ->
        if (batchQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AllInbox,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanner InBox ist leer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanne Dokumente im 'Scanner-InBox-Modus'. Alle Seiten landen hier zur automatischen Vor-Sortierung durch Regeln & On-Device-KI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBackToScanner,
                        modifier = Modifier.testTag("btn_start_batch_scan")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stapel scannen")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Manuelles Auslösen der Verarbeitung (Stativ-Scan entkoppelt)
                if (pendingCount > 0 || isProcessingBatchQueue) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isProcessingBatchQueue) "Stapel-Analyse läuft..." else "$pendingCount Dokument(e) bereit zur Analyse",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Startet OCR, Regelabgleich & KI-Klassifizierung für alle erfassten Seiten.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                if (isProcessingBatchQueue) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.processPendingBatchQueue { count ->
                                                snackbarMessage = "$count Dokument(e) analysiert!"
                                                showSuccessSnackbar = true
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_trigger_batch_analysis"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Jetzt automatische Analyse starten", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Infokarte Workflow
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Dokumente mit grünem Haken wurden durch feste Schlagwort-Regeln zugeordnet. Blaue Einträge sind KI-Vorschläge, die du mit einem Klick bestätigen oder korrigieren kannst.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(batchQueue, key = { it.id }) { item ->
                    BatchItemCard(
                        item = item,
                        onApproveAndSave = {
                            viewModel.commitBatchItem(item) {
                                snackbarMessage = "'${it.fileName}' archiviert!"
                                showSuccessSnackbar = true
                            }
                        },
                        onEdit = { editingItem = item },
                        onDelete = { viewModel.removeBatchItem(item.id) }
                    )
                }
            }
        }

        // Bearbeitungs-Modal für manuelle Korrektur vor dem Speichern
        editingItem?.let { item ->
            EditBatchItemDialog(
                item = item,
                onDismiss = { editingItem = null },
                onSave = { updated ->
                    viewModel.updateBatchItem(updated)
                    editingItem = null
                }
            )
        }

        // KI-Regel-Vorschlagsdialog falls bei Stapelverarbeitung eine Regel vorgeschlagen wird
        pendingRuleSuggestion?.let { suggestion ->
            PendingRuleSuggestionDialog(
                suggestion = suggestion,
                availableDocTypes = docTypes
            )
        }
    }
}

@Composable
fun BatchItemCard(
    item: BatchDocumentItem,
    onApproveAndSave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isRuleMatch = item.status == BatchItemStatus.RULE_MATCHED
    val isAiMatch = item.status == BatchItemStatus.AI_SUGGESTED
    val isPending = item.status == BatchItemStatus.PENDING_PROCESS
    val isWaiting = item.status == BatchItemStatus.WAITING_OCR

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = if (isRuleMatch) 1.5.dp else 1.dp,
            color = when {
                isRuleMatch -> Color(0xFF10B981)
                isAiMatch -> MaterialTheme.colorScheme.primary
                isPending -> Color(0xFFF59E0B)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth().testTag("batch_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Kopfzeile: Status-Badge & Seiten-Zähler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Erkennungs-Status
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isRuleMatch -> Color(0xFF10B981).copy(alpha = 0.15f)
                        isAiMatch -> MaterialTheme.colorScheme.primaryContainer
                        isPending -> Color(0xFFFEF3C7)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isRuleMatch -> Icons.Default.CheckCircle
                                isAiMatch -> Icons.Default.AutoAwesome
                                isPending -> Icons.Default.PauseCircle
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = when {
                                isRuleMatch -> Color(0xFF10B981)
                                isAiMatch -> MaterialTheme.colorScheme.primary
                                isPending -> Color(0xFFD97706)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isRuleMatch -> "Regel: ${item.matchedRuleName ?: "Treffer"}"
                                isAiMatch -> "KI-Vorschlag"
                                isPending -> "Wartet auf Stapel-Start"
                                else -> "OCR läuft..."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isRuleMatch -> Color(0xFF047857)
                                isAiMatch -> MaterialTheme.colorScheme.primary
                                isPending -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Löschen Button
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Entfernen",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Haupt-Informationen
            Text(
                text = item.suggestedTitle.ifBlank { "Unbenanntes Dokument" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.suggestedSender.isNotBlank()) {
                Text(
                    text = "Absender: ${item.suggestedSender}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ziel-Kategorie / Ordner
            if (item.suggestedMainCategoryId.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Zielordner: ${item.suggestedMainCategoryId}_${item.suggestedSubCategoryId}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // KI-Erklärung oder Regel-Hinweis
            if (!item.aiReasoning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.aiReasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Aktionsleiste: Übernehmen & Speichern ODER Korrigieren
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).testTag("btn_edit_batch_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Anpassen")
                }

                Button(
                    onClick = onApproveAndSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRuleMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1.3f).testTag("btn_commit_batch_${item.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speichern", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EditBatchItemDialog(
    item: BatchDocumentItem,
    onDismiss: () -> Unit,
    onSave: (BatchDocumentItem) -> Unit
) {
    var title by remember { mutableStateOf(item.suggestedTitle) }
    var sender by remember { mutableStateOf(item.suggestedSender) }
    var mainCat by remember { mutableStateOf(item.suggestedMainCategoryId) }
    var subCat by remember { mutableStateOf(item.suggestedSubCategoryId) }
    var docType by remember { mutableStateOf(item.suggestedDocType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dokument-Zuordnung anpassen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel / Bezeichnung") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Absender / Firma") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mainCat,
                        onValueChange = { mainCat = it },
                        label = { Text("Haupt-Kat (z.B. A01)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subCat,
                        onValueChange = { subCat = it },
                        label = { Text("Unter-Kat (B1.01)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = docType,
                    onValueChange = { docType = it },
                    label = { Text("Dokumenttyp (Rechnung, Vertrag...)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            suggestedTitle = title,
                            suggestedSender = sender,
                            suggestedMainCategoryId = mainCat,
                            suggestedSubCategoryId = subCat,
                            suggestedDocType = docType,
                            status = BatchItemStatus.APPROVED
                        )
                    )
                }
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
