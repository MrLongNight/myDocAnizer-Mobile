package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailModal(
    document: DocumentEntity,
    availableDocTypes: List<DocTypeItem>,
    onUpdateDocType: (Long, String) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
    onDismissRequest: () -> Unit,
    onUpdateContractReminder: ((Long, Long?, Long?, Int, Boolean, String, String, Double?) -> Unit)? = null,
    allCustomFields: List<com.example.model.CustomFieldEntity> = emptyList(),
    documentCustomFieldValues: List<com.example.model.DocumentCustomFieldValueEntity> = emptyList(),
    onUpdateCustomFieldValue: ((Long, String, String) -> Unit)? = null,
    onUpdateDocumentIconAndLogo: ((Long, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    var currentDocType by remember { mutableStateOf(document.docType) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showIconPickerDialog by remember { mutableStateOf(false) }
    val isImage = document.fileName.endsWith(".jpg", ignoreCase = true) ||
            document.fileName.endsWith(".png", ignoreCase = true)

    if (showIconPickerDialog) {
        UniversalIconAndLogoPickerDialog(
            title = "Dokument-Icon & Logo",
            subtitle = document.title,
            currentIcon = document.customIcon,
            currentLogo = document.companyLogo,
            currentCustomLogoUri = "",
            isFolder = false,
            onSave = { iconName, companyLogo, _ ->
                onUpdateDocumentIconAndLogo?.invoke(document.id, iconName, companyLogo)
                showIconPickerDialog = false
            },
            onDismiss = { showIconPickerDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { showIconPickerDialog = true }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        DocumentOrFolderIcon(
                            iconName = document.customIcon,
                            companyLogo = document.companyLogo,
                            isFolder = false,
                            isImage = isImage,
                            size = 52.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = document.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { showIconPickerDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Icon / Logo anpassen", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadaten-Karten
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow("Format", if (isImage) "Original Bilddatei (JPG)" else "Archiv-PDF (A4)")
                    val prefixE1 = if (document.mainCategoryId.isNotBlank()) "${document.mainCategoryId}_" else ""
                    val prefixE2 = if (document.subCategoryId.isNotBlank()) "${document.subCategoryId}-" else ""
                    DetailRow("Ablageort", "$prefixE1${document.mainCategory} ➔ $prefixE2${document.subCategory}")
                    DetailRow("Seitenanzahl", if (isImage) "1 Bild" else "${document.pageCount} Seite(n)")
                    DetailRow("Farbmodus", if (document.colorMode == "COLOR") "Farbe" else "Schwarz/Weiß (S/W)")
                    DetailRow("Dateigröße", document.fileSizeFormatted)
                    DetailRow("Erstellt am", formatDate(document.createdAt))
                }
            }

            // OCR-Text anzeigen falls vorhanden
            if (document.ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Indexierter OCR-Volltext (${document.ocrText.length} Zeichen)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = document.ocrText.take(280) + if (document.ocrText.length > 280) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Optionaler Doc-Typ
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Doc-Typ (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (currentDocType.isNotBlank()) currentDocType else "Kein Doc-Typ zugewiesen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(onClick = { showTypePicker = true }) {
                        Text("Ändern")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VERTRÄGE, LAUFZEITEN & KALENDER-ERINNERUNGEN
            var showContractDialog by remember { mutableStateOf(false) }
            val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            val hasDeadlineInfo = document.cancellationDeadline != null || document.contractEndDate != null

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasDeadlineInfo) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Vertrag, Laufzeit & Fristen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { showContractDialog = true }) {
                            Text(if (hasDeadlineInfo) "Bearbeiten" else "+ Frist setzen")
                        }
                    }

                    if (hasDeadlineInfo) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (document.contractEndDate != null) {
                            DetailRow("Vertragsende / Laufzeit", sdfDate.format(Date(document.contractEndDate)))
                        }
                        if (document.cancellationDeadline != null) {
                            val days = ((document.cancellationDeadline - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                            DetailRow("Kündigungsfrist", "${sdfDate.format(Date(document.cancellationDeadline))} (${if (days > 0) "in $days Tagen" else "überfällig"})")
                        }
                        if (document.amount != null && document.amount > 0.0) {
                            DetailRow("Betrag / Beitrag", String.format(Locale.GERMAN, "%.2f €", document.amount))
                        }
                        if (document.reminderNotes.isNotBlank()) {
                            DetailRow("Notiz", document.reminderNotes)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val targetDate = document.cancellationDeadline ?: document.contractEndDate ?: System.currentTimeMillis()
                                    val desc = "Kündigungsfrist für ${document.title} (${document.sender}). Notiz: ${document.reminderNotes}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                            data = android.provider.CalendarContract.Events.CONTENT_URI
                                            putExtra(android.provider.CalendarContract.Events.TITLE, "Kündigungsfrist: ${document.title}")
                                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, targetDate)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, targetDate + 3600000L)
                                            putExtra(android.provider.CalendarContract.Events.ALL_DAY, true)
                                            putExtra(android.provider.CalendarContract.Events.HAS_ALARM, 1)
                                        }
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Kalender-App konnte nicht geöffnet werden", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("In Google Kalender eintragen", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(
                            text = "Hinterlege Laufzeiten und Kündigungsfristen, um rechtzeitig in myDocAnizer oder im Android-Kalender erinnert zu werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // VORDEFINIERTE ZUSATZFELDER (CUSTOM FIELDS)
            val applicableFields = remember(allCustomFields, document) {
                allCustomFields
                    .filter {
                        it.scope == com.example.model.CustomFieldScope.GLOBAL ||
                        it.targetMainCategory.isBlank() ||
                        it.targetMainCategory.equals(document.mainCategory, ignoreCase = true)
                    }
                    .distinctBy { it.name.trim().lowercase() }
            }

            if (applicableFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Zusatzfelder & Metadaten",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        applicableFields.forEach { field ->
                            var fieldEditing by remember { mutableStateOf(false) }
                            val existingVal = documentCustomFieldValues.find {
                                it.documentId == document.id && it.customFieldId == field.id
                            }?.fieldValue ?: field.defaultValue
                            var tempVal by remember(existingVal) { mutableStateOf(existingVal) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = field.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (existingVal.isNotBlank()) existingVal else "— Nicht gesetzt —",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (existingVal.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                TextButton(onClick = { fieldEditing = true }) {
                                    Text(if (existingVal.isNotBlank()) "Bearbeiten" else "Setzen")
                                }
                            }

                            if (fieldEditing) {
                                AlertDialog(
                                    onDismissRequest = { fieldEditing = false },
                                    title = { Text(field.name, fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (field.description.isNotBlank()) {
                                                Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (field.type == com.example.model.CustomFieldType.SELECTION && field.options.isNotBlank()) {
                                                val opts = field.options.split(",").map { it.trim() }
                                                opts.forEach { opt ->
                                                    FilterChip(
                                                        selected = tempVal == opt,
                                                        onClick = { tempVal = opt },
                                                        label = { Text(opt) }
                                                    )
                                                }
                                            } else if (field.type == com.example.model.CustomFieldType.BOOLEAN) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    FilterChip(selected = tempVal == "Ja", onClick = { tempVal = "Ja" }, label = { Text("Ja") })
                                                    FilterChip(selected = tempVal == "Nein", onClick = { tempVal = "Nein" }, label = { Text("Nein") })
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = tempVal,
                                                    onValueChange = { tempVal = it },
                                                    label = { Text("Wert eingeben") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            onUpdateCustomFieldValue?.invoke(document.id, field.id, tempVal)
                                            fieldEditing = false
                                        }) {
                                            Text("Speichern")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { fieldEditing = false }) {
                                            Text("Abbrechen")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showContractDialog) {
                var editEndDate by remember { mutableStateOf(if (document.contractEndDate != null) sdfDate.format(Date(document.contractEndDate)) else "") }
                var editCancelDate by remember { mutableStateOf(if (document.cancellationDeadline != null) sdfDate.format(Date(document.cancellationDeadline)) else "") }
                var editReminderDays by remember { mutableStateOf(document.reminderDaysBefore) }
                var editNotes by remember { mutableStateOf(document.reminderNotes) }
                var editAmount by remember { mutableStateOf(if (document.amount != null) document.amount.toString() else "") }
                var editMode by remember { mutableStateOf(document.calendarEventType) }

                AlertDialog(
                    onDismissRequest = { showContractDialog = false },
                    title = { Text("Vertrag & Fristen verwalten", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = editEndDate,
                                onValueChange = { editEndDate = it },
                                label = { Text("Vertragsende (z.B. 31.12.2025)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editCancelDate,
                                onValueChange = { editCancelDate = it },
                                label = { Text("Kündigungsfrist (z.B. 30.09.2025)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                label = { Text("Monats- oder Jahresbetrag (€)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Notiz / Kündigungsklausel") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Erinnerungsart:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = editMode == "INTERNAL",
                                    onClick = { editMode = "INTERNAL" },
                                    label = { Text("Nur myDocAnizer") }
                                )
                                FilterChip(
                                    selected = editMode == "EXTERNAL",
                                    onClick = { editMode = "EXTERNAL" },
                                    label = { Text("Nur Kalender") }
                                )
                                FilterChip(
                                    selected = editMode == "BOTH",
                                    onClick = { editMode = "BOTH" },
                                    label = { Text("Beides") }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val parseEndDate = try {
                                if (editEndDate.isNotBlank()) sdfDate.parse(editEndDate)?.time else null
                            } catch (e: Exception) { null }

                            val parseCancelDate = try {
                                if (editCancelDate.isNotBlank()) sdfDate.parse(editCancelDate)?.time else null
                            } catch (e: Exception) { null }

                            val parseAmount = editAmount.toDoubleOrNull()

                            onUpdateContractReminder?.invoke(
                                document.id,
                                parseEndDate,
                                parseCancelDate,
                                editReminderDays,
                                (parseEndDate != null || parseCancelDate != null),
                                editMode,
                                editNotes,
                                parseAmount
                            )
                            showContractDialog = false
                        }) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showContractDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

            if (showTypePicker) {
                AlertDialog(
                    onDismissRequest = { showTypePicker = false },
                    title = { Text("Doc-Typ auswählen") },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    currentDocType = ""
                                    onUpdateDocType(document.id, "")
                                    showTypePicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("— Kein Doc-Typ —", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            availableDocTypes.forEach { type ->
                                TextButton(
                                    onClick = {
                                        currentDocType = type.name
                                        onUpdateDocType(document.id, type.name)
                                        showTypePicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(type.name)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showTypePicker = false }) {
                            Text("Schließen")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Aktionen: In den Papierkorb verschieben & Fertig
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDelete(document)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("In Papierkorb")
                }

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fertig")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}
