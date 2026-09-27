package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocTypeItem
import com.example.model.TemplateItem
import java.util.UUID

/**
 * Baumstruktur-Vorlagen-Modal:
 * Zeigt zunächst nur Ebene 1 (Hauptkategorien).
 * Zeigt deutlich an, ob und wie viele Unterordner (Ebene 2) vorhanden sind.
 * Erlaubt das gezielte Aufklappen / Einklappen von Ebene 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateSelectModal(
    templates: List<TemplateItem>,
    selectedTemplate: TemplateItem?,
    availableDocTypes: List<DocTypeItem>,
    onTemplateSelected: (TemplateItem) -> Unit,
    onSaveTemplate: (TemplateItem) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateItem?>(null) }

    // Gruppierung nach Hauptkategorie (Ebene 1)
    val groupedTemplates = remember(templates) {
        templates.groupBy { it.mainCategory }
    }

    // Set der aufgeklappten Hauptkategorien
    var expandedCategories by remember {
        mutableStateOf(setOfNotNull(selectedTemplate?.mainCategory))
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("template_select_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zielordner & Vorlage wählen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Baumstruktur: Klicke auf eine Kategorie zum Aufklappen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val nextMainIdx = groupedTemplates.size + 1
                        val nextMainId = String.format("A%02d", nextMainIdx)
                        editingTemplate = TemplateItem(
                            id = UUID.randomUUID().toString(),
                            mainCategory = "",
                            subCategory = "",
                            mainCategoryId = nextMainId,
                            subCategoryId = "B$nextMainIdx.01",
                            defaultDocType = "",
                            defaultSender = "",
                            namingPattern = "YYYY-MM-DD_[Sender]_[Title]",
                            defaultTags = emptyList(),
                            defaultColorMode = "BW"
                        )
                        showEditDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Neu")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (templates.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Noch keine Ordner-Vorlagen definiert",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Vorlagen werden ausschließlich für tatsächlich existierende Ordner angelegt. Klicke oben auf '+ Neu', um deine erste Kategorie & Unterordner anzulegen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    groupedTemplates.forEach { (mainCategory, subTemplates) ->
                        val isExpanded = expandedCategories.contains(mainCategory)
                        val mainId = subTemplates.firstOrNull()?.mainCategoryId.orEmpty()
                        val hasSubFolders = subTemplates.any { it.subCategory.isNotBlank() }

                        item(key = "tree_main_$mainCategory") {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategories = if (isExpanded) {
                                            expandedCategories - mainCategory
                                        } else {
                                            expandedCategories + mainCategory
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))

                                    if (mainId.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = mainId,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Text(
                                        text = mainCategory,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Zähler für Unterordner
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (hasSubFolders) "${subTemplates.size} Unterordner" else "Keine Unterordner",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Einklappen" else "Aufklappen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Aufgeklappte Ebene 2 (Unterordner)
                        if (isExpanded) {
                            items(subTemplates, key = { "tree_sub_${it.id}" }) { subTemplate ->
                                val isSelected = selectedTemplate?.id == subTemplate.id
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp)
                                        .clickable {
                                            onTemplateSelected(subTemplate)
                                            onDismissRequest()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (subTemplate.subCategoryId.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = subTemplate.subCategoryId,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = subTemplate.subCategory,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (subTemplate.defaultDocType.isNotBlank()) {
                                                Text(
                                                    text = "Doc-Typ: ${subTemplate.defaultDocType}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                editingTemplate = subTemplate
                                                showEditDialog = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showEditDialog) {
        editingTemplate?.let { tmpl ->
            TemplateEditorDialog(
                template = tmpl,
                availableDocTypes = availableDocTypes,
                onSave = { updated ->
                    onSaveTemplate(updated)
                    showEditDialog = false
                },
                onDelete = { id ->
                    onDeleteTemplate(id)
                    showEditDialog = false
                },
                onDismiss = { showEditDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorDialog(
    template: TemplateItem,
    availableDocTypes: List<DocTypeItem>,
    onSave: (TemplateItem) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var mainCat by remember { mutableStateOf(template.mainCategory) }
    var subCat by remember { mutableStateOf(template.subCategory) }
    var mainId by remember { mutableStateOf(template.mainCategoryId) }
    var subId by remember { mutableStateOf(template.subCategoryId) }
    var sender by remember { mutableStateOf(template.defaultSender) }
    var selectedDocType by remember { mutableStateOf(template.defaultDocType) }
    var namingPattern by remember { mutableStateOf(template.namingPattern) }
    var tagsText by remember { mutableStateOf(template.defaultTags.joinToString(", ")) }
    var colorMode by remember { mutableStateOf(template.defaultColorMode) }
    var showTypeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (template.subCategory.isBlank()) "Neue Vorlage anlegen" else "Vorlage bearbeiten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ebene 1 & Präfix-ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mainId,
                        onValueChange = { mainId = it },
                        label = { Text("E1-ID") },
                        placeholder = { Text("A01") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = mainCat,
                        onValueChange = { mainCat = it },
                        label = { Text("Ebene 1 (Hauptkategorie)") },
                        placeholder = { Text("z.B. Stromversorger") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }

                // Ebene 2 & Präfix-ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = subId,
                        onValueChange = { subId = it },
                        label = { Text("E2-ID") },
                        placeholder = { Text("B1.01") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = subCat,
                        onValueChange = { subCat = it },
                        label = { Text("Ebene 2 (Unterordner)") },
                        placeholder = { Text("z.B. Rechnungen") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }

                // Optionaler Doc-Typ (kein Pflichtfeld!)
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = !showTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = if (selectedDocType.isBlank()) "Kein Doc-Typ (Optional)" else selectedDocType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Doc-Typ (Optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("— Kein Doc-Typ —", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                            onClick = {
                                selectedDocType = ""
                                showTypeDropdown = false
                            }
                        )
                        availableDocTypes.forEach { docType ->
                            DropdownMenuItem(
                                text = { Text(docType.name) },
                                onClick = {
                                    selectedDocType = docType.name
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = namingPattern,
                    onValueChange = { namingPattern = it },
                    label = { Text("Dateinamen-Muster") },
                    placeholder = { Text("YYYY-MM-DD_[Sender]_[Title]") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags / Schlagwörter (Optional)") },
                    placeholder = { Text("#rechnung, #fixkosten") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedTags = tagsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { if (it.startsWith("#")) it else "#$it" }

                    val updated = template.copy(
                        mainCategory = mainCat.trim().ifBlank { "Allgemein" },
                        subCategory = subCat.trim().ifBlank { "Dokument" },
                        mainCategoryId = mainId.trim(),
                        subCategoryId = subId.trim(),
                        defaultDocType = selectedDocType.trim(),
                        defaultSender = sender.trim(),
                        namingPattern = namingPattern.trim().ifBlank { "YYYY-MM-DD_[Sender]_[Title]" },
                        defaultTags = parsedTags,
                        defaultColorMode = colorMode
                    )
                    onSave(updated)
                },
                enabled = subCat.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.subCategory.isNotBlank()) {
                    TextButton(
                        onClick = { onDelete(template.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Löschen")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}
