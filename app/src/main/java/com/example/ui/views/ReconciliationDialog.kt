package com.example.ui.views

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.content.FileProvider
import com.example.model.BankStatementEntryEntity
import com.example.model.CashTransactionEntity
import com.example.model.CashTransactionType
import com.example.model.DocumentEntity
import com.example.ui.DocAnizerViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationDialog(
    viewModel: DocAnizerViewModel,
    onDismiss: () -> Unit,
    onNavigateToScan: () -> Unit = {}
) {
    val context = LocalContext.current
    val cashTransactions by viewModel.cashTransactions.collectAsState()
    val bankStatementEntries by viewModel.bankStatementEntries.collectAsState()
    val allDocuments by viewModel.allDocuments.collectAsState()
    val toleranceDays by viewModel.reconciliationToleranceDays.collectAsState()

    // Aktueller und vergangene Monate zur Auswahl (letzte 6 Monate)
    val monthOptions = remember {
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
        val displayFormat = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
        (0..5).map { i ->
            cal.time = Date()
            cal.add(Calendar.MONTH, -i)
            val key = format.format(cal.time)
            val label = displayFormat.format(cal.time)
            key to label
        }
    }

    var selectedMonthKey by remember { mutableStateOf(monthOptions.first().first) }
    val selectedMonthLabel = monthOptions.firstOrNull { it.first == selectedMonthKey }?.second ?: selectedMonthKey

    var showAddCashDialog by remember { mutableStateOf(false) }
    var showAddBankEntryDialog by remember { mutableStateOf(false) }
    var showPrivateWithdrawalDialog by remember { mutableStateOf<Double?>(null) }

    // Filtere Buchungen für den ausgewählten Monat
    val monthCashTx = remember(cashTransactions, selectedMonthKey) {
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
        cashTransactions.filter { tx ->
            cal.timeInMillis = tx.date
            format.format(cal.time) == selectedMonthKey || tx.matchReconciliationMonth == selectedMonthKey
        }
    }

    // Belege aus dem Dokumenten-Tresor, die als Kassenbon / Quittung markiert oder Bar-Beträge sind
    val monthReceiptDocs = remember(allDocuments, selectedMonthKey) {
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
        allDocuments.filter { doc ->
            cal.timeInMillis = doc.createdAt
            format.format(cal.time) == selectedMonthKey && (doc.amount ?: 0.0) > 0
        }
    }

    // Bankabhebungen im ausgewählten Monat
    val monthBankWithdrawals = remember(bankStatementEntries, selectedMonthKey) {
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
        bankStatementEntries.filter { entry ->
            cal.timeInMillis = entry.date
            (format.format(cal.time) == selectedMonthKey || entry.monthYear == selectedMonthKey) && entry.isCashWithdrawal
        }
    }

    // Berechnungen
    val totalWithdrawals = monthBankWithdrawals.sumOf { abs(it.amount) }
    val totalReceiptsAndExpenses = monthCashTx.filter { it.type == CashTransactionType.EXPENSE }.sumOf { it.amount } +
            monthReceiptDocs.sumOf { it.amount ?: 0.0 }
    val totalPrivateWithdrawals = monthCashTx.filter { it.type == CashTransactionType.PRIVATE_WITHDRAWAL }.sumOf { it.amount }

    // Differenz (Lücke): Was wurde an Bargeld abgehoben, aber noch nicht durch Belege oder Privatentnahme erklärt?
    val difference = totalWithdrawals - (totalReceiptsAndExpenses + totalPrivateWithdrawals)
    val isPerfectMatch = abs(difference) < 0.01

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).apply { maximumFractionDigits = 2 }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TOP BAR
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Kassen- & Beleg-Abgleich",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lückenlose Nachweisführung für Steuerberater",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen")
                        }
                    },
                    actions = {
                        // Export Prüfbericht
                        FilledTonalButton(
                            onClick = {
                                generateAndShareReconciliationReport(
                                    context = context,
                                    monthLabel = selectedMonthLabel,
                                    withdrawals = monthBankWithdrawals,
                                    cashTx = monthCashTx,
                                    receiptDocs = monthReceiptDocs,
                                    totalWithdrawals = totalWithdrawals,
                                    totalReceipts = totalReceiptsAndExpenses,
                                    totalPrivate = totalPrivateWithdrawals,
                                    difference = difference
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prüfbericht", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                // CONTENT
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // MONATS-AUSWAHL
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prüfungszeitraum:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Toleranzfenster: ±$toleranceDays Tage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                monthOptions.take(3).forEach { (key, label) ->
                                    val isSelected = selectedMonthKey == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedMonthKey = key },
                                        label = { Text(label.take(12), fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // DIFFERENZ- & STATUS-BANNER
                    if (isPerfectMatch && totalWithdrawals > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(28.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "100% BELEGT & GEKLÄRT 🎉",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "Alle Bargeldabhebungen (${currencyFormat.format(totalWithdrawals)}) stimmen exakt mit deinen Bar-Belegen und Entnahmen überein. Perfekt für deinen Steuerberater!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                        }
                    } else if (difference > 0.01) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "LÜCKE ENTDECKT: ${currencyFormat.format(difference)} ungeklärt!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Du hast mehr Bargeld vom Konto abgehoben (${currencyFormat.format(totalWithdrawals)}) als bisher durch Bar-Belege nachgewiesen (${currencyFormat.format(totalReceiptsAndExpenses)}).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))

                                Text(
                                    text = "SOFORT-LÖSUNG ZUR STEUERLICHEN KORREKTUR:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onNavigateToScan()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Beleg scannen", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { showPrivateWithdrawalDialog = difference },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Als Privatentnahme", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else if (totalWithdrawals == 0.0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keine Bargeldabhebungen für diesen Monat hinterlegt",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Füge deine Kontoauszug-Abhebungen unten per Klick hinzu oder importiere einen Auszug, um den Abgleich zu starten.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // SOLL / IST GEGENÜBERSTELLUNG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. SPALTE: KONTOAUSZUG ABHEBUNGEN
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🏦 Abhebungen", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(currencyFormat.format(totalWithdrawals), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                HorizontalDivider()

                                if (monthBankWithdrawals.isEmpty()) {
                                    Text("Keine Einträge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val df = SimpleDateFormat("dd.MM.", Locale.GERMANY)
                                    monthBankWithdrawals.forEach { w ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${df.format(Date(w.date))} ${w.bookingText.take(10)}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            Text(currencyFormat.format(abs(w.amount)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showAddBankEntryDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abhebung +", fontSize = 11.sp)
                                }
                            }
                        }

                        // 2. SPALTE: BELEGE & BAR-AUSGABEN
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🧾 Bar-Belege", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(currencyFormat.format(totalReceiptsAndExpenses + totalPrivateWithdrawals), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }

                                HorizontalDivider()

                                if (monthReceiptDocs.isEmpty() && monthCashTx.isEmpty()) {
                                    Text("Keine Belege", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val df = SimpleDateFormat("dd.MM.", Locale.GERMANY)
                                    monthReceiptDocs.take(3).forEach { doc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${df.format(Date(doc.createdAt))} ${doc.title.take(10)}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            Text(currencyFormat.format(doc.amount ?: 0.0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    monthCashTx.take(3).forEach { tx ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${df.format(Date(tx.date))} ${tx.title.take(10)}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            Text(currencyFormat.format(tx.amount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showAddCashDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bar-Ausgabe +", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // ERKLÄRUNG FÜR DEN STEUERBERATER
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Rechtssicherheit & Nachweis", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Der automatische Monatsabgleich verhindert, dass bei der Betriebsprüfung oder Einkommensteuererklärung ungeklärte Bargeldentnahmen beanstandet werden. Über den Prüfbericht oben rechts erhält dein Steuerberater eine fertige Gegenüberstellung inklusive Bestätigungsklausel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // DIALOG: BAR-AUSGABE SCHNELL ERFASSEN
    if (showAddCashDialog) {
        AddCashTransactionDialog(
            defaultMonthKey = selectedMonthKey,
            onDismiss = { showAddCashDialog = false },
            onSave = { tx ->
                viewModel.addCashTransaction(tx)
                showAddCashDialog = false
            }
        )
    }

    // DIALOG: KONTOAUSZUG-ABHEBUNG ERFASSEN
    if (showAddBankEntryDialog) {
        AddBankWithdrawalDialog(
            defaultMonthKey = selectedMonthKey,
            onDismiss = { showAddBankEntryDialog = false },
            onSave = { entry ->
                viewModel.importBankStatementEntries(listOf(entry))
                showAddBankEntryDialog = false
            }
        )
    }

    // DIALOG: PRIVATENTNAHME BUCHEN (LÜCKENSCHLUSS)
    showPrivateWithdrawalDialog?.let { diffAmount ->
        AddPrivateWithdrawalDialog(
            amount = diffAmount,
            monthKey = selectedMonthKey,
            onDismiss = { showPrivateWithdrawalDialog = null },
            onConfirm = { tx ->
                viewModel.addCashTransaction(tx)
                showPrivateWithdrawalDialog = null
                Toast.makeText(context, "Privatentnahme verbucht: Lücke geschlossen!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AddCashTransactionDialog(
    defaultMonthKey: String,
    onDismiss: () -> Unit,
    onSave: (CashTransactionEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tagesausgaben") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bar-Ausgabe / Quittung erfassen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bezeichnung / Zweck (z.B. Tanken, Porto)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.replace(',', '.') },
                    label = { Text("Betrag in € (z.B. 45.50)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategorie") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz / Quittungsnummer (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0.0) {
                        onSave(
                            CashTransactionEntity(
                                title = title,
                                amount = amt,
                                type = CashTransactionType.EXPENSE,
                                category = category,
                                note = note,
                                matchReconciliationMonth = defaultMonthKey
                            )
                        )
                    }
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun AddBankWithdrawalDialog(
    defaultMonthKey: String,
    onDismiss: () -> Unit,
    onSave: (BankStatementEntryEntity) -> Unit
) {
    var text by remember { mutableStateOf("Geldautomat Barabhebung") }
    var amountStr by remember { mutableStateOf("200.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontoauszug-Abhebung eintragen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Buchungstext (z.B. Geldautomat Sparkasse)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.replace(',', '.') },
                    label = { Text("Abgehobener Betrag in € (z.B. 200.00)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(
                            BankStatementEntryEntity(
                                date = System.currentTimeMillis(),
                                bookingText = text,
                                purpose = "Bargeldauszahlung",
                                amount = -amt,
                                isCashWithdrawal = true,
                                monthYear = defaultMonthKey
                            )
                        )
                    }
                }
            ) {
                Text("Eintragen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun AddPrivateWithdrawalDialog(
    amount: Double,
    monthKey: String,
    onDismiss: () -> Unit,
    onConfirm: (CashTransactionEntity) -> Unit
) {
    var note by remember { mutableStateOf("Privatentnahme Haushalt / Lebenshaltung") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Als Privatentnahme buchen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Möchtest du die Differenz von ${String.format(Locale.GERMANY, "%.2f €", amount)} als Privatentnahme (Eigenbedarf) buchen? Damit ist die Bargeldabhebung steuerlich zu 100% geklärt und erfordert keine weiteren Quittungen.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Buchungsvermerk / Begründung") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CashTransactionEntity(
                            title = "Privatentnahme (geklärt)",
                            amount = amount,
                            type = CashTransactionType.PRIVATE_WITHDRAWAL,
                            category = "Privat",
                            note = note,
                            matchReconciliationMonth = monthKey
                        )
                    )
                }
            ) {
                Text("Jetzt buchen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun generateAndShareReconciliationReport(
    context: Context,
    monthLabel: String,
    withdrawals: List<BankStatementEntryEntity>,
    cashTx: List<CashTransactionEntity>,
    receiptDocs: List<DocumentEntity>,
    totalWithdrawals: Double,
    totalReceipts: Double,
    totalPrivate: Double,
    difference: Double
) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val currFmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

        // Kopfzeile
        paint.textSize = 16f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("myDocAnizer-Mobile • Monatsabgleich-Prüfbericht", 40f, 45f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawText("Prüfungszeitraum: $monthLabel | Erstellt am: ${df.format(Date())} | Mandanten-Dokumentation", 40f, 65f, paint)

        paint.color = android.graphics.Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 75f, 555f, 75f, paint)

        // Status Box
        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("1. Gesamtergebnis Kassen- & Belegprüfung", 40f, 100f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("• Bargeldabhebungen vom Konto (Soll): ${currFmt.format(totalWithdrawals)}", 50f, 120f, paint)
        canvas.drawText("• Erfasste Belege & Barausgaben: ${currFmt.format(totalReceipts)}", 50f, 135f, paint)
        canvas.drawText("• Deklarierte Privatentnahmen: ${currFmt.format(totalPrivate)}", 50f, 150f, paint)

        paint.isFakeBoldText = true
        val statusText = if (abs(difference) < 0.01) "0,00 € (VOLLSTÄNDIG GEKLÄRT & BELEGT)" else "${currFmt.format(difference)} (LÜCKE VORHANDEN)"
        canvas.drawText("• Verbleibende Differenz: $statusText", 50f, 170f, paint)

        // Abhebungen
        paint.textSize = 12f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("2. Aufstellung Bankabhebungen (Girokonto)", 40f, 205f, paint)

        paint.textSize = 9f
        paint.isFakeBoldText = false
        var yPos = 225f
        if (withdrawals.isEmpty()) {
            canvas.drawText("Keine Barabhebungen im Auszug verzeichnet.", 50f, yPos, paint)
            yPos += 14f
        } else {
            withdrawals.forEach { w ->
                canvas.drawText("• ${df.format(Date(w.date))} | ${w.bookingText.take(35)} | Betrag: ${currFmt.format(abs(w.amount))}", 50f, yPos, paint)
                yPos += 14f
            }
        }

        // Gegenüberstellung Belege
        yPos += 15f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("3. Quittierte Belege & Kassenbelege", 40f, yPos, paint)

        yPos += 20f
        paint.textSize = 9f
        paint.isFakeBoldText = false
        receiptDocs.forEach { doc ->
            canvas.drawText("• ${df.format(Date(doc.createdAt))} | ${doc.title.take(30)} | ${doc.mainCategory} | ${currFmt.format(doc.amount ?: 0.0)}", 50f, yPos, paint)
            yPos += 14f
        }
        cashTx.forEach { tx ->
            val typeLabel = if (tx.type == CashTransactionType.PRIVATE_WITHDRAWAL) "[Privatentnahme]" else "[Barausgabe]"
            canvas.drawText("• ${df.format(Date(tx.date))} | $typeLabel ${tx.title.take(25)} | ${currFmt.format(tx.amount)}", 50f, yPos, paint)
            yPos += 14f
        }

        // Bescheinigung für Steuerberater
        yPos += 25f
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("4. Erklärung & Bestätigungsvermerk für den Steuerberater", 40f, yPos, paint)

        yPos += 18f
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Hiermit wird bestätigt, dass die oben aufgeführten Barbelege und Bargeldabhebungen", 40f, yPos, paint)
        yPos += 12f
        canvas.drawText("für den Monat $monthLabel vollständig erfasst und abgeglichen wurden.", 40f, yPos, paint)

        yPos += 40f
        canvas.drawLine(40f, yPos, 220f, yPos, paint)
        canvas.drawLine(340f, yPos, 520f, yPos, paint)
        yPos += 12f
        canvas.drawText("Datum, Unterschrift Mandant", 40f, yPos, paint)
        canvas.drawText("Geprüft Steuerberater / Buchhaltung", 340f, yPos, paint)

        pdfDocument.finishPage(page)

        val reportFile = File(context.cacheDir, "myDocAnizer_Monatsabgleich_${monthLabel.replace(" ", "_")}.pdf")
        val outputStream = FileOutputStream(reportFile)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "myDocAnizer Kassen- & Belegabgleich $monthLabel")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Prüfbericht teilen"))
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim Erstellen des Prüfberichts: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
