package com.example.ui.views

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.DocRule
import com.example.model.PendingRuleSuggestion

@Composable
fun PendingRuleSuggestionDialog(
    suggestion: PendingRuleSuggestion,
    availableDocTypes: List<com.example.model.DocTypeItem> = emptyList()
) {
    var ruleName by remember { mutableStateOf(suggestion.suggestedRule.name) }
    var sender by remember { mutableStateOf(suggestion.suggestedRule.detectedSender) }
    var docType by remember { mutableStateOf(suggestion.suggestedRule.targetDocType) }
    var keywordsStr by remember { mutableStateOf(suggestion.suggestedRule.matchKeywords.joinToString(", ")) }
    var mainCatId by remember { mutableStateOf(suggestion.suggestedRule.targetMainCategoryId) }
    var subCatId by remember { mutableStateOf(suggestion.suggestedRule.targetSubCategoryId) }
    var selectedIcon by remember { mutableStateOf(suggestion.suggestedRule.targetIcon.ifBlank { "description" }) }
    var selectedLogo by remember { mutableStateOf(suggestion.suggestedRule.targetLogo) }
    var showIconPicker by remember { mutableStateOf(false) }
    var customFieldsMap by remember { mutableStateOf(suggestion.suggestedRule.targetCustomFields) }

    if (showIconPicker) {
        com.example.ui.components.UniversalIconAndLogoPickerDialog(
            title = "Ziel-Icon & Firmenlogo",
            subtitle = ruleName,
            currentIcon = selectedIcon,
            currentLogo = selectedLogo,
            currentCustomLogoUri = "",
            isFolder = false,
            onSave = { icon, logo, _ ->
                selectedIcon = icon
                selectedLogo = logo
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    Dialog(onDismissRequest = { suggestion.onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Titelzeile mit KI-Icon & Live-Vorschau des Ziel-Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Neuer KI-Regelvorschlag",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keine bestehende Regel hat gematcht",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live-Vorschau des zugewiesenen Icons
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { showIconPicker = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            com.example.ui.components.DocumentOrFolderIcon(
                                iconName = selectedIcon,
                                companyLogo = selectedLogo,
                                isFolder = false,
                                size = 44.dp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Die KI hat den OCR-Text analysiert und folgenden Vorschlag für Ablage und Regel erarbeitet. Du kannst ihn anpassen und entscheiden, ob diese Regel dauerhaft gespeichert oder nur einmalig angewendet wird. Tippe rechts oben auf das Symbol, um Icon & Logo anzupassen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Anpassbare Felder
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Bezeichnung / Dokumentname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        label = { Text("Erkannter Absender") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Logo/Icon")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mainCatId,
                        onValueChange = { mainCatId = it },
                        label = { Text("E1-Kategorie") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = subCatId,
                        onValueChange = { subCatId = it },
                        label = { Text("E2-Unterkategorie") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = keywordsStr,
                    onValueChange = { keywordsStr = it },
                    label = { Text("Schlagwörter (durch Komma getrennt)") },
                    supportingText = { Text("Triggert künftig diesen Dokumenttyp automatisch") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Erkannte strukturierte Zusatzfelder
                if (customFieldsMap.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Erkannte Zusatzfelder (${customFieldsMap.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            customFieldsMap.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Aktions-Buttons: Einmalig anwenden vs. Dauerhafte Regel
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val kwList = keywordsStr.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                             val updatedRule = suggestion.suggestedRule.copy(
                                name = ruleName.ifBlank { "Scan $sender" },
                                detectedSender = sender.ifBlank { "Unbekannt" },
                                targetDocType = docType,
                                targetMainCategoryId = mainCatId,
                                targetSubCategoryId = subCatId,
                                matchKeywords = if (kwList.isNotEmpty()) kwList else listOf(sender.lowercase()),
                                targetIcon = selectedIcon,
                                targetLogo = selectedLogo,
                                targetCustomFields = customFieldsMap
                            )
                            suggestion.onSaveAsPermanentRule(updatedRule)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_permanent_rule"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dauerhafte Regel speichern", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val kwList = keywordsStr.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                            val updatedRule = suggestion.suggestedRule.copy(
                                name = ruleName.ifBlank { "Scan $sender" },
                                detectedSender = sender.ifBlank { "Unbekannt" },
                                targetDocType = docType,
                                targetMainCategoryId = mainCatId,
                                targetSubCategoryId = subCatId,
                                matchKeywords = if (kwList.isNotEmpty()) kwList else listOf(sender.lowercase()),
                                targetIcon = selectedIcon,
                                targetLogo = selectedLogo,
                                targetCustomFields = customFieldsMap
                            )
                            // We use updatedRule for single use too so the created DocumentEntity receives the customized Icon, Logo and CustomFields!
                            suggestion.onSingleUseOnly()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_single_use_rule")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nur einmalig anwenden")
                    }

                    TextButton(
                        onClick = { suggestion.onDismiss() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    }
}
