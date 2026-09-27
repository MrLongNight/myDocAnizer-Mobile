package com.example.ui.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.ImageProcessingService
import com.example.ui.DocAnizerViewModel

enum class PcConnectionType {
    WEB_BROWSER,      // 1. Lokales Web-Portal im Browser mit Zero-Knowledge AES-256 Verschlüsselung
    WATCHFOLDER_NAS,  // 2. Netzwerk-Scanner / SMBv3 / HTTPS-WebDAV Watchfolder
    DESKTOP_CLIENT,   // 3. Desktop-Scanner Tool (TLS 1.3 & mTLS für ScanSnap/Brother)
    USB_CABLE         // 4. USB-Kabel Direktübertragung (MTP / Air-Gapped)
}

/**
 * Eigenständiger, optionaler Einrichtungs-Assistent für PC-zu-App Schnittstellen & PC-Scannen.
 * Kann am Ende des Ersteinrichtungs-Wizards oder im Settings-Menü gestartet werden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcCompanionWizardDialog(
    viewModel: DocAnizerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    val hostPin by viewModel.p2pHostPairingPin.collectAsState()

    var activeStep by rememberSaveable { mutableIntStateOf(1) } // 1: Auswahl, 2: Setup, 3: Ziel-Ablage, 4: Fertig
    var selectedType by rememberSaveable { mutableStateOf(PcConnectionType.WEB_BROWSER) }
    var autoSortWithAi by rememberSaveable { mutableStateOf(false) }
    var isSimulatingTestScan by remember { mutableStateOf(false) }
    var testScanFeedback by remember { mutableStateOf<String?>(null) }

    // Beim Öffnen sicherstellen, dass P2P-Server aktiv ist für Web & P2P
    LaunchedEffect(Unit) {
        if (!p2pServerStatus.isRunning) {
            viewModel.startP2pServer()
        }
        if (hostPin.isNullOrBlank()) {
            viewModel.startP2pHostPairingMode()
        }
    }

    BackHandler {
        if (activeStep > 1) {
            activeStep--
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pc_companion_wizard_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header mit Schließen-Button & Schrittzähler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                text = "PC- & Scanner-Anbindung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Schritt $activeStep von 4: " + when (activeStep) {
                                    1 -> "Verbindungsweg wählen"
                                    2 -> "Schnittstelle einrichten"
                                    3 -> "Ablage-Verhalten"
                                    else -> "Bereit & Test"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { activeStep.toFloat() / 4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Inhalt des aktuellen Schritts
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeStep) {
                        1 -> Step1SelectConnectionType(
                            selectedType = selectedType,
                            onSelect = { selectedType = it }
                        )
                        2 -> Step2ConfigureConnection(
                            connectionType = selectedType,
                            serverIp = p2pServerStatus.localIp,
                            serverPort = p2pServerStatus.port,
                            pin = hostPin.orEmpty(),
                            context = context,
                            viewModel = viewModel
                        )
                        3 -> Step3ConfigureDestination(
                            autoSortWithAi = autoSortWithAi,
                            onToggleAutoSort = { autoSortWithAi = it }
                        )
                        4 -> Step4SummaryAndTest(
                            connectionType = selectedType,
                            serverIp = p2pServerStatus.localIp,
                            serverPort = p2pServerStatus.port,
                            autoSortWithAi = autoSortWithAi,
                            isSimulating = isSimulatingTestScan,
                            feedback = testScanFeedback,
                            onSendTestScan = {
                                isSimulatingTestScan = true
                                testScanFeedback = null
                                // Simuliert die Übertragung eines Testdokuments vom PC
                                val testBmp = ImageProcessingService.createSampleDocumentBitmap(
                                    title = "PC_Test_Dokument",
                                    sender = "Desktop-Scanner",
                                    modeBw = false
                                )
                                viewModel.saveSinglePageBulk(testBmp) { doc ->
                                    isSimulatingTestScan = false
                                    testScanFeedback = "✅ Test-Dokument '${doc.title}' erfolgreich empfangen & archiviert!"
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons (Zurück & Weiter/Fertig)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeStep > 1) {
                        TextButton(onClick = { activeStep-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zurück")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Abbrechen")
                        }
                    }

                    Button(
                        onClick = {
                            if (activeStep < 4) {
                                activeStep++
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("btn_pc_wizard_next")
                    ) {
                        Text(if (activeStep == 4) "Einrichtung abschließen" else "Weiter")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (activeStep == 4) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * SCHRITT 1: Auswahl aus 4 Möglichkeiten, wie PC & App verbunden werden
 */
@Composable
private fun Step1SelectConnectionType(
    selectedType: PcConnectionType,
    onSelect: (PcConnectionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Offizielles myDocAnizer-Light Logo Banner
        com.example.ui.components.MyDocAnizerLightLogo(
            modifier = Modifier.fillMaxWidth(),
            height = 58.dp,
            showSubtitle = true
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Wie möchtest du deinen Computer oder PC-Scanner einbinden?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Wähle die passende Methode für deine Arbeitsweise. Du kannst die Optionen später jederzeit anpassen oder kombinieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // OPTION 1: Lokales Web-Interface im Browser mit Zero-Knowledge Verschlüsselung
        ConnectionOptionCard(
            title = "1. Web-Portal im PC-Browser (Zero-Knowledge AES-256 Verschlüsselung)",
            badge = "Empfohlen • 100% Ende-zu-Ende Verschlüsselt",
            icon = Icons.Default.EnhancedEncryption,
            color = Color(0xFF0284C7),
            isSelected = selectedType == PcConnectionType.WEB_BROWSER,
            description = "Öffne einfach Chrome, Edge oder Firefox am PC. Dokumente werden direkt im Browser-RAM mit AES-256-GCM verschlüsselt, bevor sie das Gerät verlassen.",
            highlights = listOf(
                "Zero-Knowledge Kryptographie: Lokale WebCrypto API verschlüsselt vor dem Senden",
                "Keinerlei Klartext-Übertragung im WLAN – abhörsicher selbst ohne öffentliche Zertifizierungsstelle",
                "Optional: 1-Klick lokales Root-Zertifikat für HTTPS mit grünem Schloss ohne Warnung",
                "Funktioniert ohne Software-Installation auch an Firmen-Laptops"
            ),
            onClick = { onSelect(PcConnectionType.WEB_BROWSER) }
        )

        // OPTION 2: Netzwerk-Scanner / SMBv3 / HTTPS-WebDAV Watchfolder
        ConnectionOptionCard(
            title = "2. Netzwerk-Scanner / Watchfolder (SMBv3 / HTTPS-WebDAV)",
            badge = "Voll verschlüsselt für Multifunktionsdrucker",
            icon = Icons.Default.Print,
            color = Color(0xFF16A34A),
            isSelected = selectedType == PcConnectionType.WATCHFOLDER_NAS,
            description = "Dein Dokumentenscanner scannt per 'Scan-to-Folder' verschlüsselt über SMB 3.1.1 oder HTTPS-WebDAV auf dein Heim-NAS, deine Fritz!Box oder einen freigegebenen Ordner.",
            highlights = listOf(
                "Ein Knopfdruck am Scanner genügt – kein PC muss geöffnet sein",
                "Transportverschlüsselung über TLS/HTTPS oder SMB3 mit Integritätsprüfung",
                "Automatischer Hintergrund-Import & lokale Texterkennung (OCR)"
            ),
            onClick = { onSelect(PcConnectionType.WATCHFOLDER_NAS) }
        )

        // OPTION 3: myDocAnizer-Desktop (Windows, macOS, Linux)
        ConnectionOptionCard(
            title = "3. myDocAnizer-Desktop (Windows, macOS, Linux)",
            badge = "Open-Source • Lokaler Voll-Spiegel",
            icon = Icons.Default.Laptop,
            color = Color(0xFF7C3AED),
            isSelected = selectedType == PcConnectionType.DESKTOP_CLIENT,
            description = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
            highlights = listOf(
                "QR-Code Kopplung: Einmal mit der Smartphone-Kamera vom Monitor scannen",
                "Scan-to-Android: Am PC scannen (WIA/TWAIN/SANE) & mobil per KI indexieren lassen",
                "Lokale FTS5-Volltextsuche, Filter, Bearbeiten & Löschen direkt am PC",
                "PDF-Export & nativer Druckerdialog ohne Cloud"
            ),
            onClick = { onSelect(PcConnectionType.DESKTOP_CLIENT) }
        )

        // OPTION 4: USB-Kabel Direktübertragung (Air-Gapped)
        ConnectionOptionCard(
            title = "4. USB-Kabel Direktübertragung (MTP / Air-Gapped)",
            badge = "100% Offline • Physisch abhörsicher",
            icon = Icons.Default.Usb,
            color = Color(0xFFD97706),
            isSelected = selectedType == PcConnectionType.USB_CABLE,
            description = "Smartphone per USB-C-Kabel an den PC anschließen. Scans in 'Documents/myDocAnizer/Import' kopieren. Vollkommen isoliert ohne jedes Funknetzwerk.",
            highlights = listOf(
                "Kein WLAN, kein Netzwerk, keine Funkverbindung – physische Barriere",
                "Höchste Übertragungsrate für riesige Aktenordner",
                "Ideal für streng vertrauliche Geheimhaltungsstufen"
            ),
            onClick = { onSelect(PcConnectionType.USB_CABLE) }
        )
    }
}

/**
 * SCHRITT 2: Interaktive Konfiguration der gewählten Methode
 */
@Composable
private fun Step2ConfigureConnection(
    connectionType: PcConnectionType,
    serverIp: String,
    serverPort: Int,
    pin: String,
    context: Context,
    viewModel: DocAnizerViewModel
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val secureHttpsUrl = "https://$serverIp:$serverPort"
    val browserUrl = "http://$serverIp:$serverPort"
    val pairedDevices by viewModel.p2pPairedDevices.collectAsState()

    var showQrScanDialog by remember { mutableStateOf(false) }
    var qrInputText by remember {
        mutableStateOf(
            """{"v":2,"id":"desktop-${System.currentTimeMillis() % 10000}","name":"Desktop-Light PC","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
        )
    }
    var pairingFeedback by remember { mutableStateOf<String?>(null) }
    var isPairingLoading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (connectionType) {
            PcConnectionType.WEB_BROWSER -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("1. Web-Portal im PC-Browser aufrufen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Stelle sicher, dass Smartphone und PC im selben WLAN sind. Gib folgende Adresse in Chrome, Edge oder Firefox ein:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // URL-Box mit Kopier-Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = browserUrl,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "🔒 E2E-Verschlüsselt via WebCrypto (AES-256-GCM)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("myDocAnizer URL", browserUrl))
                                        Toast.makeText(context, "URL in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren")
                                }
                            }
                        }

                        // Sicherheits-Erklärung: Warum Zero-Knowledge Verschlüsselung abhörsicher ist
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF16A34A).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                    Text("Wie wird die Übertragung ohne Browser-Warnung 100% sicher?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                }
                                Text(
                                    text = "• Clientseitige Zero-Knowledge Verschlüsselung: Die hardwarebeschleunigte WebCrypto API deines PC-Browsers verschlüsselt jede Datei im PC-Arbeitsspeicher mit AES-256-GCM (Schlüssel abgeleitet aus deiner Einmal-PIN), BEVOR sie die Netzwerkkarte verlässt.\n• Selbst bei unverschlüsseltem Transportweg sieht ein Angreifer im WLAN nur unlesbaren Ciphertext-Datenmüll.\n• Keine nervigen Zertifikatswarnungen im Browser, da die Kryptographie direkt im Browserfenster läuft.\n• Alternativ: Lade das lokale myDocAnizer Root-CA-Zertifikat herunter (über http://$serverIp:$serverPort/cert/mydocanizer-ca.crt) und installiere es auf deinem PC für echtes HTTPS mit grünem Schloss.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text("2. Sicherheits-PIN zur Autorisierung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Beim ersten Öffnen am PC wirst du nach der 6-stelligen Freigabe-PIN gefragt, damit Fremde im selben WLAN keinen Zugriff haben:", style = MaterialTheme.typography.bodySmall)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Einmal-Freigabe-PIN & Verschlüsselungsschlüssel:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = if (pin.length == 6) "${pin.substring(0, 3)} ${pin.substring(3)}" else pin.ifBlank { "Wird erzeugt..." },
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            PcConnectionType.WATCHFOLDER_NAS -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = Color(0xFF16A34A))
                            Text("Netzwerk-Watchfolder Konfiguration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Konfiguriere an deinem Scanner oder Drucker das Scan-Ziel auf deine Fritz!Box, dein Synology/QNAP NAS oder einen freigegebenen Windows-Ordner.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Empfohlener Ordnername:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("\\\\FRITZ-NAS\\Dokumente\\Scan_InBox\\", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text("Dateiformat am Scanner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("PDF (Mehrseitig oder Einzelseiten, 300 DPI)", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            text = "myDocAnizer synchronisiert diesen Ordner automatisch im Hintergrund, sobald du im Heim-WLAN bist.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PcConnectionType.DESKTOP_CLIENT -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.example.ui.components.MyDocAnizerDesktopLogo(
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp,
                            showSubtitle = true
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Laptop, contentDescription = null, tint = Color(0xFF7C3AED))
                            Text("myDocAnizer-Desktop Kopplung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "1. Starte die myDocAnizer-Desktop App auf deinem Computer (Windows, macOS oder Linux).\n2. Der automatische Start-Wizard zeigt auf dem Bildschirm einen dynamischen QR-Code.\n3. Tippe hier auf 'Desktop QR-Code scannen', um die verschlüsselte Verbindung herzustellen:",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // QR-Code Scan & Verbindungs-Aktion
                        Button(
                            onClick = { showQrScanDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth().testTag("btn_scan_desktop_qr")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📷 Desktop QR-Code scannen / verbinden", fontWeight = FontWeight.Bold)
                        }

                        if (pairingFeedback != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (pairingFeedback?.startsWith("✅") == true) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = pairingFeedback.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Liste verbundener Desktop-Geräte
                        val desktopDevices = pairedDevices.filter { it.deviceType == "DESKTOP_LIGHT_APP" || it.role.contains("Desktop") }
                        if (desktopDevices.isNotEmpty()) {
                            Text("Verbundene Desktop-Clients:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            desktopDevices.forEach { dev ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                Text(dev.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text("${dev.ipAddress}:${dev.port} • ${dev.role}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Status: ${dev.lastSyncStatus}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.syncWithP2pDevice(dev) { res ->
                                                    pairingFeedback = if (res.isSuccess) "✅ Sync mit '${dev.name}' erfolgreich!" else "Sync-Fehler: ${res.exceptionOrNull()?.message}"
                                                }
                                            },
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text("Sync", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Manuelle Verbindungsdaten als Fallback
                        Text("Manuelle Verbindungsdaten (Fallback):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Server-IP: $serverIp", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Port: $serverPort", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Text("Verbindungs-PIN: $pin", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Modal-Dialog für QR-Code Eingabe / Simulation
                if (showQrScanDialog) {
                    AlertDialog(
                        onDismissRequest = { showQrScanDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF7C3AED))
                                Text("Desktop QR-Code erfassen")
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Fotografiere den QR-Code auf deinem PC-Monitor ab oder bestätige die automatisch erkannten Verbindungsdaten der myDocAnizer-Desktop App:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedTextField(
                                    value = qrInputText,
                                    onValueChange = { qrInputText = it },
                                    label = { Text("QR-Code JSON-Nutzdaten") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = {
                                        qrInputText = """{"v":2,"id":"win-light-${System.currentTimeMillis() % 1000}","name":"Windows PC (myDocAnizer-Desktop)","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
                                    }) {
                                        Text("Vorlage: Windows PC", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = {
                                        qrInputText = """{"v":2,"id":"mac-light-${System.currentTimeMillis() % 1000}","name":"MacBook (myDocAnizer-Desktop)","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
                                    }) {
                                        Text("Vorlage: Mac", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isPairingLoading = true
                                    viewModel.pairWithDesktopLightApp(qrInputText) { success, msg ->
                                        isPairingLoading = false
                                        pairingFeedback = msg
                                        showQrScanDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                            ) {
                                Text("Verbinden & Autorisieren")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showQrScanDialog = false }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }
            }

            PcConnectionType.USB_CABLE -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = Color(0xFFD97706))
                            Text("USB-Kabel Dateiübertragung (MTP)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "1. Schließe dein Smartphone mit dem USB-Kabel an den PC an.\n2. Wähle am Handy 'Dateiübertragung (MTP)'.\n3. Öffne am PC den Ordner:",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Dieser PC > Smartphone > Interner Speicher > Documents > myDocAnizer > Import",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Alle dort abgelegten PDFs oder Bilder werden von myDocAnizer sofort zur Sortierung angeboten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * SCHRITT 3: Ziel-Ablage & Automatisierung für PC-Scans
 */
@Composable
private fun Step3ConfigureDestination(
    autoSortWithAi: Boolean,
    onToggleAutoSort: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Wohin sollen vom PC empfangene Dokumente wandern?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Option A: Scan InBox (Empfohlen)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (!autoSortWithAi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.5.dp, if (!autoSortWithAi) MaterialTheme.colorScheme.primary else Color.Transparent),
            onClick = { onToggleAutoSort(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = !autoSortWithAi, onClick = { onToggleAutoSort(false) })
                Column(modifier = Modifier.weight(1f)) {
                    Text("In die 'Scan InBox' legen (Empfohlen)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Du behältst die volle Kontrolle. Neue Dokumente vom PC landen in der InBox, wo du sie kurz sichten, den KI-Vorschlag prüfen und mit einem Klick archivieren kannst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Option B: Direkte KI-Autosortierung
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (autoSortWithAi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.5.dp, if (autoSortWithAi) MaterialTheme.colorScheme.primary else Color.Transparent),
            onClick = { onToggleAutoSort(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = autoSortWithAi, onClick = { onToggleAutoSort(true) })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direkt per KI-Regeln einsortieren (Zero-Touch)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Gescannte Dokumente werden sofort per lokaler On-Device KI analysiert und nach deinen Schlagwort-Regeln automatisch in die passenden Zielordner verschoben.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * SCHRITT 4: Zusammenfassung & Test-Scan
 */
@Composable
private fun Step4SummaryAndTest(
    connectionType: PcConnectionType,
    serverIp: String,
    serverPort: Int,
    autoSortWithAi: Boolean,
    isSimulating: Boolean,
    feedback: String?,
    onSendTestScan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF16A34A).copy(alpha = 0.12f),
            border = BorderStroke(1.5.dp, Color(0xFF16A34A).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(28.dp))
                Column {
                    Text("PC-Schnittstelle ist einsatzbereit!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    Text(
                        text = "Gewählte Methode: " + when (connectionType) {
                            PcConnectionType.WEB_BROWSER -> "Web-Portal im Browser (http://$serverIp:$serverPort)"
                            PcConnectionType.WATCHFOLDER_NAS -> "Netzwerk-Watchfolder"
                            PcConnectionType.DESKTOP_CLIENT -> "myDocAnizer-Desktop (Windows, macOS, Linux)"
                            PcConnectionType.USB_CABLE -> "USB-Kabel Übertragung"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Test-Übertragung
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Funktionsprüfung & Test-Scan:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Möchtest du prüfen, wie Dokumente vom PC in myDocAnizer ankommen? Drücke auf den Test-Button, um eine Testübertragung zu simulieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSendTestScan,
                    enabled = !isSimulating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSimulating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Empfange PC-Testdokument...")
                    } else {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test-Dokument vom PC simulieren")
                    }
                }

                feedback?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text(
            text = "💡 Hinweis: Du kannst diesen Assistenten oder die Verbindungsdaten jederzeit im Hauptmenü unter Einstellungen > 'PC- & Scanner-Anbindung' erneut aufrufen.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionOptionCard(
    title: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    description: String,
    highlights: List<String>,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                RadioButton(selected = isSelected, onClick = onClick)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                highlights.forEach { h ->
                    Text("• $h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
