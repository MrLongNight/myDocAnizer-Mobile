package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DocumentEntity
import com.example.model.TemplateItem
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog zum Importieren eines Bildes (aus Galerie oder Dateisystem)
 * Völlig unabhängig vom Scan-Modus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageImportDialog(
    templates: List<TemplateItem>,
    onDismiss: () -> Unit,
    onImport: (bitmap: Bitmap, title: String, sender: String, template: TemplateItem, isColor: Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var title by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(templates.firstOrNull()) }
    var isColorMode by remember { mutableStateOf(true) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
                selectedBitmap = bmp
                if (title.isBlank()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMANY)
                    title = "Import_${sdf.format(Date())}"
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Bild als Dokument importieren",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Foto aus Galerie auswählen und als PDF archivieren",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bildauswahl / Vorschau
                val bmp = selectedBitmap
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Bildvorschau",
                            modifier = Modifier.fillMaxSize()
                        )
                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text("Anderes Bild", fontSize = 12.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bild aus Galerie auswählen")
                    }
                }

                // Dokument-Titel
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Dokument-Titel / Bezeichnung") },
                    placeholder = { Text("z.B. Rechnung_Kaufland_2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Zielordner / Vorlage auswählen
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Zielordner (Vorlage):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ExposedDropdownMenuBox(
                        expanded = templateDropdownExpanded,
                        onExpandedChange = { templateDropdownExpanded = !templateDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate?.let { "${it.mainCategory} ➔ ${it.subCategory}" } ?: "Ordner wählen...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = templateDropdownExpanded,
                            onDismissRequest = { templateDropdownExpanded = false }
                        ) {
                            templates.forEach { tmpl ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${tmpl.mainCategory} ➔ ${tmpl.subCategory}")
                                    },
                                    onClick = {
                                        selectedTemplate = tmpl
                                        templateDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Farbmodus Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isColorMode) "Farbmodus: Farbe" else "Farbmodus: S/W Optimiert",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isColorMode,
                        onCheckedChange = { isColorMode = it }
                    )
                }

                // Aktionen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val bmp = selectedBitmap
                            val tmpl = selectedTemplate
                            if (bmp != null && tmpl != null) {
                                onImport(bmp, title.ifBlank { "Bild_Dokument" }, "", tmpl, isColorMode)
                                onDismiss()
                            }
                        },
                        enabled = selectedBitmap != null && selectedTemplate != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importieren & Archivieren")
                    }
                }
            }
        }
    }
}

/**
 * Dialog zum Importieren von E-Mails (.eml Datei oder Text)
 * Extrahiert Metadaten (Absender, Empfänger, Datum, Betreff) und archiviert als PDF Beleg
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailImportDialog(
    templates: List<TemplateItem>,
    onDismiss: () -> Unit,
    onImport: (sender: String, recipient: String, subject: String, body: String, dateStr: String, template: TemplateItem) -> Unit
) {
    val context = LocalContext.current
    var sender by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var dateStr by remember {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        mutableStateOf(sdf.format(Date()))
    }
    var body by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(templates.firstOrNull()) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }

    // Launcher zum Laden von .eml oder .txt Dateien
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fullText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                parseEmlContent(fullText) { parsedSender, parsedRecipient, parsedSubject, parsedDate, parsedBody ->
                    if (parsedSender.isNotBlank()) sender = parsedSender
                    if (parsedRecipient.isNotBlank()) recipient = parsedRecipient
                    if (parsedSubject.isNotBlank()) subject = parsedSubject
                    if (parsedDate.isNotBlank()) dateStr = parsedDate
                    if (parsedBody.isNotBlank()) body = parsedBody
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "E-Mail Beleg importieren",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aus .eml Datei laden oder E-Mail Text einfügen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Schneller Datei-Import (.eml / .txt)
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(".eml / Text-Datei öffnen")
                }

                // Betreff
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Betreff (Subject) *") },
                    placeholder = { Text("z.B. Vertragsbestätigung 2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Absender & Empfänger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        label = { Text("Von (Absender)") },
                        placeholder = { Text("service@firma.de") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("An (Empfänger)") },
                        placeholder = { Text("ich@mail.de") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Datum
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("E-Mail Datum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // E-Mail Text / Nachricht
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Nachricht / E-Mail Inhalt") },
                    placeholder = { Text("Hier den Inhalt der E-Mail einfügen...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // Zielordner / Vorlage auswählen
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Zielordner (Vorlage):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ExposedDropdownMenuBox(
                        expanded = templateDropdownExpanded,
                        onExpandedChange = { templateDropdownExpanded = !templateDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate?.let { "${it.mainCategory} ➔ ${it.subCategory}" } ?: "Ordner wählen...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = templateDropdownExpanded,
                            onDismissRequest = { templateDropdownExpanded = false }
                        ) {
                            templates.forEach { tmpl ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${tmpl.mainCategory} ➔ ${tmpl.subCategory}")
                                    },
                                    onClick = {
                                        selectedTemplate = tmpl
                                        templateDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Aktionen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tmpl = selectedTemplate
                            if (subject.isNotBlank() && tmpl != null) {
                                onImport(sender, recipient, subject, body, dateStr, tmpl)
                                onDismiss()
                            }
                        },
                        enabled = subject.isNotBlank() && selectedTemplate != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("E-Mail Archivieren")
                    }
                }
            }
        }
    }
}

/**
 * Einfacher Parser für Standard-MIME / .eml Kopfzeilen
 */
private fun parseEmlContent(
    content: String,
    onParsed: (sender: String, recipient: String, subject: String, date: String, body: String) -> Unit
) {
    var sender = ""
    var recipient = ""
    var subject = ""
    var date = ""
    val lines = content.lines()
    var isHeader = true
    val bodyBuilder = StringBuilder()

    for (line in lines) {
        if (isHeader) {
            val lower = line.lowercase()
            when {
                lower.startsWith("from:") -> sender = line.substringAfter(":").trim()
                lower.startsWith("to:") -> recipient = line.substringAfter(":").trim()
                lower.startsWith("subject:") -> subject = line.substringAfter(":").trim()
                lower.startsWith("date:") -> date = line.substringAfter(":").trim()
                line.isBlank() -> isHeader = false
            }
        } else {
            bodyBuilder.appendLine(line)
        }
    }

    if (subject.isBlank() && isHeader && content.isNotBlank()) {
        // Falls keine typischen Headers vorhanden waren, gesamten Text als Body und erste Zeile als Betreff
        val firstLine = lines.firstOrNull()?.trim() ?: "E-Mail"
        onParsed(
            sender,
            recipient,
            firstLine.take(50),
            date.ifBlank { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date()) },
            content
        )
    } else {
        onParsed(sender, recipient, subject, date, bodyBuilder.toString().trim())
    }
}
