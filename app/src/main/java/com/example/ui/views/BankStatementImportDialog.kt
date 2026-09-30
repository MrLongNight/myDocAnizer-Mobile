package com.example.ui.views

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BankStatementEntryEntity
import com.example.service.BankStatementParseResult
import com.example.service.BankStatementParserService
import com.example.ui.DocAnizerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankStatementImportDialog(
    viewModel: DocAnizerViewModel,
    defaultMonthKey: String = "",
    onDismiss: () -> Unit,
    onImportSuccess: (count: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY) }

    var parseResult by remember { mutableStateOf<BankStatementParseResult?>(null) }
    var selectedEntries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeEntriesList by remember { mutableStateOf<List<BankStatementEntryEntity>>(emptyList()) }
    var isManualTextOpen by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Datei-Picker via SAF (Storage Access Framework)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            try {
                // Ermittle Dateinamen
                var filename = "Kontoauszug"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        filename = cursor.getString(nameIndex)
                    }
                }

                val result = BankStatementParserService.parseFromUri(context, uri, filename)
                parseResult = result
                activeEntriesList = result.entries
                selectedEntries = result.entries.map { it.id }.toSet()

                if (result.entries.isEmpty()) {
                    Toast.makeText(context, "Keine Buchungen gefunden. Bitte Dateiformat prüfen.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Lesen der Datei: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("dialog_bank_statement_import"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Kontoauszug importieren",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "CSV / PDF / Online-Banking Textauszug",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. HAUPTBEREICH
                if (parseResult == null) {
                    // INITIALER STATUS: DATEI AUSWÄHLEN ODER MUSTER LADEN
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Info Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatischer Bargeld- & Auszugsabgleich",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Unterstützt CSV-Dateien aller gängigen Banken (Sparkasse, Volksbank, DKB, ING, Postbank, Commerzbank) sowie Text- und PDF-Exporte. Bargeldabhebungen werden für den Steuerberater-Abgleich automatisch erkannt.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // HAUPTAKTION: DATEI AUSWÄHLEN
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.ms-excel",
                                        "application/csv",
                                        "application/pdf",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_select_bank_statement_file"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kontoauszug-Datei auswählen (CSV / PDF)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // ZWEITAKTION: MUSTER-AUSZUG ZUM TESTEN
                        OutlinedButton(
                            onClick = {
                                val month = if (defaultMonthKey.isNotBlank()) defaultMonthKey else SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(Date())
                                val demoEntries = BankStatementParserService.generateDemoStatement(month)
                                parseResult = BankStatementParseResult(
                                    entries = demoEntries,
                                    totalParsed = demoEntries.size,
                                    cashWithdrawalsCount = demoEntries.count { it.isCashWithdrawal },
                                    totalCashWithdrawalsAmount = demoEntries.filter { it.isCashWithdrawal }.sumOf { abs(it.amount) },
                                    detectedBankFormat = "Sparkasse & VR-Bank Muster-Kontoauszug",
                                    errors = emptyList()
                                )
                                activeEntriesList = demoEntries
                                selectedEntries = demoEntries.map { it.id }.toSet()
                                Toast.makeText(context, "Muster-Auszug mit ${demoEntries.size} Buchungen geladen!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_load_demo_statement"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Muster-Kontoauszug laden (Test-Modus)")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ZWISCHENABLAGE / MANUELL EINFÜGEN
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isManualTextOpen = !isManualTextOpen },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Auszugstext aus Zwischenablage einfügen", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Icon(if (isManualTextOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                }

                                AnimatedVisibility(visible = isManualTextOpen) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = manualText,
                                            onValueChange = { manualText = it },
                                            placeholder = { Text("CSV-Zeilen oder Text aus dem Online-Banking hier einfügen...") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(130.dp),
                                            maxLines = 6
                                        )

                                        Button(
                                            onClick = {
                                                if (manualText.isNotBlank()) {
                                                    val result = BankStatementParserService.parseCsvOrText(manualText, "Clipboard")
                                                    parseResult = result
                                                    activeEntriesList = result.entries
                                                    selectedEntries = result.entries.map { it.id }.toSet()
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            enabled = manualText.isNotBlank()
                                        ) {
                                            Text("Text parsen & prüfen")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // PARSE-ERGEBNIS VORSCHAU & BESTÄTIGUNG
                    val result = parseResult!!
                    val selectedList = activeEntriesList.filter { selectedEntries.contains(it.id) }
                    val selectedCashWithdrawals = selectedList.filter { it.isCashWithdrawal }
                    val selectedCashTotal = selectedCashWithdrawals.sumOf { abs(it.amount) }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Status Card mit erkannten Infos
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            text = result.detectedBankFormat,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Text(
                                        text = "${selectedList.size} von ${activeEntriesList.size} ausgewählt",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedCashWithdrawals.isNotEmpty()) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("🏦 Erkannte Barabhebungen", style = MaterialTheme.typography.labelSmall, color = if (selectedCashWithdrawals.isNotEmpty()) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${selectedCashWithdrawals.size} Posten (${currencyFormat.format(selectedCashTotal)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedCashWithdrawals.isNotEmpty()) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Sonstige Buchungen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${selectedList.size - selectedCashWithdrawals.size} Posten",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Liste der geparsten Buchungen mit Checkboxen und Umschalt-Möglichkeit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GEFUNDENE BUCHUNGEN:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { selectedEntries = activeEntriesList.map { it.id }.toSet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Alle an", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { selectedEntries = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Keine", fontSize = 11.sp)
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(activeEntriesList, key = { it.id }) { item ->
                                val isSelected = selectedEntries.contains(item.id)

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        if (item.isCashWithdrawal) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.dp else 0.5.dp,
                                        color = if (isSelected) {
                                            if (item.isCashWithdrawal) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                                        } else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedEntries = if (isSelected) {
                                                    selectedEntries - item.id
                                                } else {
                                                    selectedEntries + item.id
                                                }
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedEntries = if (checked) {
                                                    selectedEntries + item.id
                                                } else {
                                                    selectedEntries - item.id
                                                }
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dateFormat.format(Date(item.date)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                Text(
                                                    text = currencyFormat.format(item.amount),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.amount < 0) MaterialTheme.colorScheme.error else Color(0xFF16A34A)
                                                )
                                            }

                                            Text(
                                                text = item.bookingText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (item.purpose.isNotBlank()) {
                                                Text(
                                                    text = item.purpose,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // CHIP: ALS BARGELDABHEBUNG MARKIEREN / ENTFERNEN
                                            FilterChip(
                                                selected = item.isCashWithdrawal,
                                                onClick = {
                                                    val updated = activeEntriesList.map {
                                                        if (it.id == item.id) it.copy(isCashWithdrawal = !it.isCashWithdrawal) else it
                                                    }
                                                    activeEntriesList = updated
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (item.isCashWithdrawal) Icons.Default.Check else Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = if (item.isCashWithdrawal) "Als Barabhebung markiert" else "Als Barabhebung festlegen",
                                                        fontSize = 10.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFD1FAE5),
                                                    selectedLabelColor = Color(0xFF065F46)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. FOOTER-AKTIONEN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (parseResult != null) {
                                parseResult = null
                                activeEntriesList = emptyList()
                                selectedEntries = emptySet()
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (parseResult != null) "Zurück" else "Abbrechen")
                    }

                    if (parseResult != null) {
                        val selectedList = activeEntriesList.filter { selectedEntries.contains(it.id) }
                        Button(
                            onClick = {
                                if (selectedList.isNotEmpty()) {
                                    viewModel.importBankStatementEntries(selectedList) { count ->
                                        Toast.makeText(
                                            context,
                                            "$count Buchungen erfolgreich in den Tresor übernommen!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onImportSuccess(count)
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("btn_confirm_import_entries"),
                            enabled = selectedList.isNotEmpty()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Übernehmen (${selectedList.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
