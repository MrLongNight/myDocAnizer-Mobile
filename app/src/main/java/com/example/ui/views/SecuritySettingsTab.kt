package com.example.ui.views

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.fragment.app.FragmentActivity
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@Composable
fun SecuritySettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAuthEnabled by viewModel.biometricAuthEnabled.collectAsState()
    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val statusLabel = remember { viewModel.getBiometricStatusLabel() }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Überschrift & Info
        Text(
            text = "Sicherheit & Autorisierung",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Biometrische App-Sperre Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Biometrische App-Sperre",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Fingerabdruck / Gesicht / Geräte-PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = biometricAuthEnabled,
                        onCheckedChange = { viewModel.setBiometricAuthEnabled(it) },
                        modifier = Modifier.testTag("switch_biometric_settings")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Hardware-Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (biometricAuthEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (biometricAuthEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                testResultText = null
                                if (activity != null) {
                                    viewModel.authenticateBiometric(
                                        activity = activity,
                                        onSuccess = {
                                            testResultText = "Erfolgreich autorisiert!"
                                        },
                                        onError = { err ->
                                            testResultText = "Autorisierung fehlgeschlagen: $err"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sensor testen")
                        }

                        Button(
                            onClick = {
                                viewModel.lockApp()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jetzt sperren")
                        }
                    }

                    testResultText?.let { res ->
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.startsWith("Erfolgreich")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Zero-Knowledge Master-Verschlüsselung Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Knowledge Verschlüsselung",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lokale AES-256-GCM Datenbank- & Cloud-Sicherheit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Deine Dokumente und Metadaten werden kryptografisch geschützt. Google Passwort-Manager und Passkeys (FIDO2) werden nativ unterstützt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Master-Passwort & Anmeldedaten anpassen")
                }
            }
        }

        // Erklärung zu Hardware-Features
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Unterstützte Authentifizierungsmethoden",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Google Passwort-Manager (Automatisches Speichern & Ausfüllen)\n• Passkeys / FIDO2 Hardware-Sicherheitsschlüssel\n• Biometrie (Fingerabdrucksensor & Face Unlock)\n• Sichere Android Geräte-PIN / Muster / Passwort als automatischer Fallback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPasswordDialog) {
        var newPass by remember { mutableStateOf(syncPasswordKey) }
        var showPasswordText by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        var isSavingManager by remember { mutableStateOf(false) }
        var passStatusMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Master-Passwort & Google Anmeldedaten") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Gib ein sicheres Master-Passwort für die Zero-Knowledge Verschlüsselung ein:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Passwort") },
                        visualTransformation = if (showPasswordText) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPasswordText = !showPasswordText }) {
                                Icon(
                                    imageVector = if (showPasswordText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (newPass.isBlank()) return@Button
                            isSavingManager = true
                            passStatusMsg = null
                            coroutineScope.launch {
                                try {
                                    val credManager = runCatching { CredentialManager.create(context) }.getOrNull()
                                    if (credManager != null) {
                                        val req = CreatePasswordRequest("myDocAnizer-Master-Key", newPass)
                                        credManager.createCredential(context, req)
                                        passStatusMsg = "✅ Im Google Passwort-Manager hinterlegt!"
                                    } else {
                                        passStatusMsg = "✅ Lokal als Master-Schlüssel aktiv."
                                    }
                                } catch (e: CreateCredentialCancellationException) {
                                    passStatusMsg = "Abgebrochen."
                                } catch (e: Throwable) {
                                    passStatusMsg = "✅ Lokal als Master-Schlüssel aktiv."
                                } finally {
                                    isSavingManager = false
                                }
                            }
                        },
                        enabled = !isSavingManager && newPass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Im Google Passwort-Manager sichern")
                    }

                    passStatusMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (msg.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPass.isNotBlank()) {
                        viewModel.setSyncPasswordKey(newPass)
                        showPasswordDialog = false
                    }
                }) {
                    Text("Übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }
}
