package com.example.ui.views

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.service.PairingResult
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2pSyncTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val pairedDevices by viewModel.p2pPairedDevices.collectAsState()
    val serverStatus by viewModel.p2pServerStatus.collectAsState()
    val hostPin by viewModel.p2pHostPairingPin.collectAsState()
    val pendingRequests by viewModel.p2pPendingPairingRequests.collectAsState()
    val syncProgress by viewModel.p2pSyncProgress.collectAsState()
    val activeConflicts by viewModel.p2pActiveConflicts.collectAsState()
    val syncHistory by viewModel.p2pSyncHistory.collectAsState()
    val localDeviceName by viewModel.p2pLocalDeviceName.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }
    var showEditDeviceNameDialog by remember { mutableStateOf(false) }
    var selectedConflictForModal by remember { mutableStateOf<SyncConflict?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. INFO BANNER: Master-Master P2P Erklärung
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SyncAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "dual-sync mittels myDocAnizer-Mobile auf zusätzlichen Gerät",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Verbindet ein zusätzliches Smartphone oder Tablet dauerhaft im lokalen WLAN. Dokumente, Kategorien und Metadaten werden bidirektional synchronisiert – 100% offline & Ende-zu-Ende verschlüsselt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // BANNER / SCHNELLZUGRIFF: PC & Desktop-Scanner Anbindung
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "myDocAnizer-Desktop",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.launchPcCompanionWizard() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("PC-Verbindung", fontSize = 12.sp)
                }
            }
        }

        // 2. SERVER STATUS CARD (WLAN IP, Port, Listener)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (serverStatus.isRunning) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (serverStatus.isRunning) "P2P-Server aktiv (Wartet auf Anfragen)" else "P2P-Server angehalten",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (serverStatus.isRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = serverStatus.isRunning,
                        onCheckedChange = { start ->
                            if (start) viewModel.startP2pServer() else viewModel.stopP2pServer()
                        },
                        modifier = Modifier.testTag("p2p_server_toggle")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Detaildaten: IP, Port, Gerätename
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dieses Gerät",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = localDeviceName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showEditDeviceNameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Name ändern", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lokale WLAN-Adresse",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${serverStatus.localIp}:${serverStatus.port}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("${serverStatus.localIp}:${serverStatus.port}"))
                            Toast.makeText(context, "IP-Adresse in die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kopieren", fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. EINGEHENDE AUTORISIERUNGSANFRAGE (EXTRA SICHERHEIT)
        if (pendingRequests.isNotEmpty()) {
            pendingRequests.forEach { req ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚠️ Autorisierung erforderlich!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ein neues Gerät im lokalen WLAN möchte sich für den Master-Master-Sync mit dieser App verbinden:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Gerätename: ${req.clientDeviceName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "IP-Adresse: ${req.clientIp}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(text = "Eingegebener PIN: ${req.pinEntered}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Ablehnen")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Erlauben & Verbinden")
                            }
                        }
                    }
                }
            }
        }

        // 4. KONFLIKT-CENTER (TRANSPARENZ BEI PROBLEMEN & MANUELLE LÖSUNG)
        if (activeConflicts.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.5.dp, Color(0xFFDC2626))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activeConflicts.size} Sync-Konflikt(e) erfordern Klärung",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                    Text(
                        text = "Um Datenverlust auszuschließen, wurde der Sync an folgenden Dokumenten pausiert. Bitte wähle, welche Version behalten werden soll:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    activeConflicts.forEach { conflict ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conflict.docTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1E293B)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(conflict.causeType.badgeColorHex).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = conflict.causeType.title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(conflict.causeType.badgeColorHex),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ursache: ${conflict.diagnosticMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Gegenüberstellung
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("📱 Lokale Version", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                                        Text("Titel: ${conflict.localSummary.title}", fontSize = 11.sp, maxLines = 1)
                                        Text("Absender: ${conflict.localSummary.sender.ifBlank { "-" }}", fontSize = 11.sp, maxLines = 1)
                                        Text("Ordner: ${conflict.localSummary.mainCategory}", fontSize = 11.sp, maxLines = 1)
                                        Text("Geändert: ${conflict.localSummary.modifiedFormatted}", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("💻 ${conflict.peerName}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                                        Text("Titel: ${conflict.remoteSummary.title}", fontSize = 11.sp, maxLines = 1)
                                        Text("Absender: ${conflict.remoteSummary.sender.ifBlank { "-" }}", fontSize = 11.sp, maxLines = 1)
                                        Text("Ordner: ${conflict.remoteSummary.mainCategory}", fontSize = 11.sp, maxLines = 1)
                                        Text("Geändert: ${conflict.remoteSummary.modifiedFormatted}", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Aktionen zur Konfliktlösung
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.resolveP2pConflict(conflict, ConflictResolutionAction.KEEP_LOCAL) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Lokal behalten", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.resolveP2pConflict(conflict, ConflictResolutionAction.ACCEPT_REMOTE) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Remote nutzen", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                    Button(
                                        onClick = { viewModel.resolveP2pConflict(conflict, ConflictResolutionAction.KEEP_BOTH_AS_COPY) },
                                        modifier = Modifier.weight(1.1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Beide als Kopie", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. SYNCHRONISATION FORTSCHRITT (WÄHREND AKTIVEM SYNC)
        AnimatedVisibility(visible = syncProgress.isSyncing) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Synchronisiere mit ${syncProgress.peerName}...",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { syncProgress.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = syncProgress.currentStage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 6. VERBUNDENE GERÄTE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Verbundene Geräte (${pairedDevices.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = { showPairingDialog = true },
                modifier = Modifier.testTag("pair_new_device_button")
            ) {
                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gerät verbinden")
            }
        }

        if (pairedDevices.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Noch kein Zweitgerät verbunden",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verbinde deinen Desktop-PC (mit Scanner) oder dein Tablet, um alle Dokumente automatisch im lokalen WLAN zu synchronisieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showPairingDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jetzt PC / Zweitgerät verbinden")
                    }
                }
            }
        } else {
            pairedDevices.forEach { device ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (device.name.contains("PC", ignoreCase = true) || device.name.contains("Computer", ignoreCase = true)) Icons.Default.Computer else Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${device.ipAddress}:${device.port} • ${device.role}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.removeP2pPairedDevice(device.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Trennen", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Status: ${device.lastSyncStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (device.lastSyncStatus.contains("Konflikt")) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Zuletzt abgeglichen: ${device.lastSyncFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.syncWithP2pDevice(device) { res ->
                                        res.onSuccess {
                                            Toast.makeText(context, "Sync mit ${device.name} erfolgreich!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Fehler: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Jetzt Sync")
                            }
                        }
                    }
                }
            }
        }

        // 7. MASTER BUTTON: Alle synchronisieren & Audit-Log Button
        if (pairedDevices.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync-Historie")
                }

                Button(
                    onClick = {
                        viewModel.syncAllP2pDevices { success, conflicts ->
                            Toast.makeText(
                                context,
                                "Sync abgeschlossen ($success Geräte synchronisiert, $conflicts Konflikte)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("sync_all_p2p_button")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle abgleichen")
                }
            }
        }
    }

    // DIALOG: ZWEITGERÄT KOPPELN (HOST- ODER CLIENT-MODUS)
    if (showPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = serverStatus.localIp,
            serverPort = serverStatus.port,
            onDismiss = {
                viewModel.stopP2pHostPairingMode()
                showPairingDialog = false
            }
        )
    }

    // DIALOG: GERÄTENAME BEARBEITEN
    if (showEditDeviceNameDialog) {
        var tempName by remember { mutableStateOf(localDeviceName) }
        AlertDialog(
            onDismissRequest = { showEditDeviceNameDialog = false },
            title = { Text("Eigener Gerätename") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Gerätename im WLAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempName.isNotBlank()) {
                        viewModel.setP2pDeviceName(tempName)
                    }
                    showEditDeviceNameDialog = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDeviceNameDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // DIALOG: SYNC-HISTORIE / AUDIT-LOG
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("P2P Sync-Historie & Audit-Log") },
            text = {
                if (syncHistory.isEmpty()) {
                    Text("Bisher wurden noch keine P2P-Synchronisationen protokolliert.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(syncHistory) { log ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.peerName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = log.timestampFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = log.diagnosticMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (log.status == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHistoryDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }
}

/**
 * 2-Wege Verbindungsdialog für zwei Android-Geräte:
 * Modus A (Host): Zeigt 6-stelligen PIN & IP an, die man am zweiten Android-Gerät (z.B. Tablet) eingibt.
 * Modus B (Client): Man gibt IP und PIN des anderen Android-Geräts ein.
 */
@Composable
fun PairingWizardDialog(
    viewModel: DocAnizerViewModel,
    serverIp: String,
    serverPort: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Host (Code anzeigen), 1: Client (IP eingeben)
    var activeHostPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        activeHostPin = viewModel.startP2pHostPairingMode()
    }

    var peerIpInput by remember { mutableStateOf("192.168.1.") }
    var peerPortInput by remember { mutableStateOf("8765") }
    var peerPinInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zwei Android-Apps verbinden") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("1. Code anzeigen") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("2. IP eingeben") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // HOST MODUS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Gib diese Verbindungsdaten in der myDocAnizer App auf deinem Zweitgerät (Tablet / Smartphone) ein:",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Einmaliger Verbindungs-PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val formattedPin = if (activeHostPin.length == 6) {
                                    "${activeHostPin.substring(0, 3)} ${activeHostPin.substring(3)}"
                                } else activeHostPin

                                Text(
                                    text = formattedPin,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("IP-Adresse dieses Geräts:", fontSize = 11.sp)
                                    Text(
                                        text = "$serverIp:$serverPort",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                }
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sobald das andere Gerät anfragt, erscheint hier eine Bestätigung zur Autorisierung.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // CLIENT MODUS
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gib die IP-Adresse und den PIN des anderen Geräts ein:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = peerIpInput,
                            onValueChange = { peerIpInput = it },
                            label = { Text("IP-Adresse des Partners (z.B. PC)") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = peerPortInput,
                            onValueChange = { peerPortInput = it },
                            label = { Text("Port (Standard: 8765)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = peerPinInput,
                            onValueChange = { peerPinInput = it },
                            label = { Text("6-stelliger PIN") },
                            placeholder = { Text("z.B. 482195") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (isConnecting) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verbindung wird hergestellt & autorisiert...")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                Button(
                    onClick = {
                        val port = peerPortInput.toIntOrNull() ?: 8765
                        if (peerIpInput.isBlank() || peerPinInput.isBlank()) {
                            errorMessage = "Bitte IP und PIN ausfüllen"
                            return@Button
                        }
                        isConnecting = true
                        errorMessage = null
                        viewModel.pairWithP2pDevice(peerIpInput.trim(), port, peerPinInput.trim()) { res ->
                            isConnecting = false
                            res.onSuccess {
                                Toast.makeText(context, "Erfolgreich verbunden!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.onFailure { err ->
                                errorMessage = err.localizedMessage ?: "Verbindung fehlgeschlagen"
                            }
                        }
                    },
                    enabled = !isConnecting
                ) {
                    Text("Verbinden")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Fertig")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
