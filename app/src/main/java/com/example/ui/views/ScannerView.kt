package com.example.ui.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.model.ScannerSettings
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.CameraPreview
import com.example.ui.components.CameraFrameAnalysis
import com.example.ui.components.FlashOption
import com.example.ui.components.TemplateSelectModal
import com.example.ui.components.imageProxyToBitmap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerView(
    viewModel: DocAnizerViewModel,
    onDocumentScanned: (DocumentEntity) -> Unit,
    onNavigateToBatchInbox: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedTemplate by viewModel.selectedTemplate.collectAsStateWithLifecycle()
    val isColorMode by viewModel.isColorMode.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingScan.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val docTypes by viewModel.docTypes.collectAsStateWithLifecycle()
    val scannerSettings by viewModel.scannerSettings.collectAsStateWithLifecycle()
    val lastBulkSavedDoc by viewModel.lastBulkSavedDoc.collectAsStateWithLifecycle()
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isBatchModeActive by viewModel.isBatchModeActive.collectAsStateWithLifecycle()
    val pendingRuleSuggestion by viewModel.pendingRuleSuggestion.collectAsStateWithLifecycle()

    var showTemplateModal by remember { mutableStateOf(false) }
    var flashOption by remember(scannerSettings.flashMode) {
        mutableStateOf(
            when (scannerSettings.flashMode) {
                "FLASH" -> FlashOption.FLASH
                "TORCH" -> FlashOption.TORCH
                else -> FlashOption.OFF
            }
        )
    }
    var frameAnalysis by remember { mutableStateOf(CameraFrameAnalysis()) }
    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var isCameraHardwareAvailable by remember { mutableStateOf(true) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showTemplateModal) {
        showTemplateModal = false
    }

    BackHandler(enabled = !showTemplateModal && capturedPages.isNotEmpty()) {
        viewModel.clearPages()
    }

    LaunchedEffect(scanErrorMessage) {
        if (scanErrorMessage != null) {
            kotlinx.coroutines.delay(4000L)
            scanErrorMessage = null
        }
    }

    // Countdown Zustand für zeitverzögerten Selbstauslöser
    var countdownValue by remember { mutableStateOf(0) }
    var isCountdownRunning by remember { mutableStateOf(false) }

    // Auto-Scan Schutz: Stoppt sofort nach Erfassung eines Dokuments, bis ein neues Blatt aufgelegt wird
    var hasCapturedCurrentDocument by remember { mutableStateOf(false) }
    var motionDetectedAfterCapture by remember { mutableStateOf(false) }
    var autoDetectProgress by remember { mutableStateOf(0f) }

    // Sensoren für Scan-Qualitätstools (Wasserwaage, Schärfe, Kontrast, Ausleuchtung)
    var sensorTiltX by remember { mutableStateOf(0f) }
    var sensorTiltY by remember { mutableStateOf(0f) }
    var sensorTiltZ by remember { mutableStateOf(9.8f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // Jitter-Filter gegen unnötige Dauer-Recompositions
                    if (kotlin.math.abs(x - sensorTiltX) > 0.06f ||
                        kotlin.math.abs(y - sensorTiltY) > 0.06f ||
                        kotlin.math.abs(z - sensorTiltZ) > 0.06f
                    ) {
                        sensorTiltX = x
                        sensorTiltY = y
                        sensorTiltZ = z
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // 1. Wasserwaage / Telefon in Waage (Minimierung von Trapezverzerrungen)
    val xyMagnitude = sqrt((sensorTiltX * sensorTiltX + sensorTiltY * sensorTiltY).toDouble()).toFloat()
    val tiltAngleDegrees = (atan2(xyMagnitude.toDouble(), abs(sensorTiltZ).toDouble()) * (180.0 / Math.PI)).toFloat()
    val isPhoneInWaage = tiltAngleDegrees <= 3.2f

    // 2. Bildschärfe-Prüfung (Erschütterung / Stillstandsanalyse)
    val sharpnessScore = when {
        xyMagnitude < 0.45f -> 100
        xyMagnitude < 0.95f -> 95
        xyMagnitude < 1.6f -> 85
        xyMagnitude < 2.5f -> 65
        else -> 40
    }
    val isSharpnessOptimal = sharpnessScore >= 85

    val isAutoMode = scannerSettings.scanMode == "AUTO"

    // Funktion zum Ausführen des eigentlichen Bildabzugs
    val executeCapture: () -> Unit = {
        hasCapturedCurrentDocument = true
        motionDetectedAfterCapture = false
        autoDetectProgress = 0f

        val capture = imageCaptureInstance
        val onBmpReady: (android.graphics.Bitmap) -> Unit = { bmp ->
            hasCapturedCurrentDocument = true
            motionDetectedAfterCapture = false
            autoDetectProgress = 0f

            // Akustisches Feedback & optionale Vibration
            com.example.service.ScanFeedbackService.playCaptureSound(scannerSettings.soundProfile)
            com.example.service.ScanFeedbackService.triggerVibration(context, scannerSettings.enableVibration)

            if (isBatchModeActive) {
                // Neuer Stapel-Puffer Modus: Sofort in die Inbox (entkoppelt für schnellen Stativscan)
                viewModel.enqueueBatchDocument(bmp)
            } else if (scannerSettings.isBulkMode || isAutoMode) {
                // Einzel-/Bulk-Modus: Direktes Speichern mit intelligenter Regelprüfung & KI-Vorschlag
                viewModel.saveSinglePageBulk(bmp) { savedDoc ->
                    // Bleibt im Scanner, zeigt Bestätigung
                }
            } else {
                // Manueller Mehrseiten-Modus: Seite der Liste hinzufügen
                viewModel.addPage(bmp)
            }
        }

        if (capture != null) {
            try {
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val bmp = try {
                                imageProxyToBitmap(imageProxy)
                            } catch (t: Throwable) {
                                Log.e("ScannerView", "Fehler beim Dekodieren des ImageProxy: ${t.message}", t)
                                null
                            } finally {
                                imageProxy.close()
                            }
                            if (bmp != null) {
                                onBmpReady(bmp)
                            } else {
                                Log.e("ScannerView", "Bild konnte nicht decodiert werden")
                                scanErrorMessage = "Kamerabild konnte nicht decodiert werden. Bitte erneut auslösen."
                                hasCapturedCurrentDocument = false
                                autoDetectProgress = 0f
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("ScannerView", "Kamera-Fehler bei Aufnahme: ${exception.message}", exception)
                            scanErrorMessage = "Kamerafehler: ${exception.localizedMessage ?: "Auslösung fehlgeschlagen"}"
                            hasCapturedCurrentDocument = false
                            autoDetectProgress = 0f
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ScannerView", "Ausnahme beim Kamera-Auslösen: ${e.message}", e)
                scanErrorMessage = "Auslöser-Fehler: ${e.localizedMessage ?: "Auslösung fehlgeschlagen"}"
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            }
        } else {
            if (!isCameraHardwareAvailable) {
                // Nur im Emulator ohne Hardware-Kamera: Simuliertes Testblatt
                val template = selectedTemplate ?: templates.firstOrNull()
                val title = template?.subCategory ?: "Dokument"
                val sender = template?.defaultSender ?: "Scan"
                val isColor = if (isAutoMode) viewModel.pdfSettings.value.defaultColorMode == "COLOR" else isColorMode
                val mockBmp = com.example.service.ImageProcessingService.createSampleDocumentBitmap(title, sender, !isColor)
                onBmpReady(mockBmp)
            } else {
                Log.w("ScannerView", "Kamera noch nicht bereit")
                scanErrorMessage = "Kamera wird initialisiert... Bitte kurz warten."
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            }
        }
    }

    // Trigger-Logik (berücksichtigt Countdown / Sofort & setzt Blattstatus zurück)
    val startCaptureProcess: () -> Unit = {
        if (!isProcessing && !isCountdownRunning) {
            hasCapturedCurrentDocument = false
            motionDetectedAfterCapture = false
            if (scannerSettings.triggerMode == "COUNTDOWN") {
                isCountdownRunning = true
                countdownValue = scannerSettings.countdownSeconds
            } else {
                executeCapture()
            }
        }
    }

    // Countdown Coroutine
    LaunchedEffect(isCountdownRunning, countdownValue) {
        if (isCountdownRunning) {
            if (countdownValue > 0) {
                kotlinx.coroutines.delay(1000L)
                countdownValue -= 1
            } else {
                isCountdownRunning = false
                executeCapture()
            }
        }
    }

    // Hardware-Button Event (Lautstärketasten)
    LaunchedEffect(Unit) {
        viewModel.hardwareTriggerEvent.collect {
            startCaptureProcess()
        }
    }

    // 1. Automatische Erkennung des Blattwechsels:
    // Nachdem ein Blatt gescannt wurde, wartet das System darauf, dass das gescannte Blatt entfernt
    // und das nächste Dokument im Scanbereich platziert wird.
    // Ein Blattwechsel liegt vor, wenn:
    // - Das Dokument kurzzeitig nicht mehr im Bild ist (!frameAnalysis.isDocumentDetected)
    // - ODER signifikante Bewegung (Hand/Papierwechsel) gemessen wird.
    // Sobald das neue Blatt ruhig im Bild liegt, schaltet das System sofort wieder für den nächsten Auto-Scan frei!
    LaunchedEffect(
        hasCapturedCurrentDocument,
        frameAnalysis.motionScore,
        xyMagnitude,
        isPhoneInWaage,
        isSharpnessOptimal,
        frameAnalysis.isDocumentDetected,
        scannerSettings.toolsInfoOnly,
        scannerSettings.sheetChangeSensitivity,
        isCameraHardwareAvailable
    ) {
        if (hasCapturedCurrentDocument) {
            val motionThreshold = when (scannerSettings.sheetChangeSensitivity) {
                "HIGH" -> 3.5f
                "LOW" -> 7.5f
                else -> 5.0f
            }

            // Schritt 1: Wurde das alte Blatt weggenommen oder bewegt?
            if (!frameAnalysis.isDocumentDetected || frameAnalysis.motionScore > motionThreshold || xyMagnitude > 0.65f) {
                motionDetectedAfterCapture = true
            }

            // Schritt 2: Liegt das neue Blatt ruhig im Scanbereich?
            if (motionDetectedAfterCapture) {
                val isCalm = frameAnalysis.motionScore < (motionThreshold - 1.0f).coerceAtLeast(2.0f) && xyMagnitude < 0.5f
                val isDocReady = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))

                if (isCalm && isDocReady) {
                    val postDelay = (scannerSettings.postScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
                    kotlinx.coroutines.delay(postDelay)
                    hasCapturedCurrentDocument = false
                    motionDetectedAfterCapture = false
                }
            }

            // Schritt 3: Im Emulator oder bei Kamera-Simulation nach eingestellter Nachlaufzeit automatisch freigeben
            if (!isCameraHardwareAvailable) {
                val postDelay = (scannerSettings.postScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
                kotlinx.coroutines.delay(postDelay)
                hasCapturedCurrentDocument = false
                motionDetectedAfterCapture = false
            }
        }
    }

    // 2. Automatischer Scan-Ablauf:
    // Startet VOLLAUTOMATISCH, wenn ein neues Dokument ruhig und scharf im Scanbereich liegt (oder sofort bei Info-Only),
    // und STOPPT nach dem Auslösen sofort solange, bis wieder ein neues Dokument platziert wurde!
    LaunchedEffect(
        scannerSettings.triggerMode,
        scannerSettings.tripodAutoScan,
        scannerSettings.preScanDelaySeconds,
        scannerSettings.postScanDelaySeconds,
        scannerSettings.scanMode,
        scannerSettings.toolsInfoOnly,
        hasCapturedCurrentDocument,
        isProcessing,
        isCountdownRunning,
        isPhoneInWaage,
        isSharpnessOptimal,
        frameAnalysis.isDocumentDetected
    ) {
        val isTripodOrAuto = scannerSettings.scanMode == "AUTO" &&
            (scannerSettings.tripodAutoScan || scannerSettings.triggerMode == "AUTO_DETECT")

        // Nur starten wenn Auto-Scan aktiv, noch KEIN Blatt erfasst wurde und keine Verarbeitung läuft
        if (isTripodOrAuto && !hasCapturedCurrentDocument && !isProcessing && !isCountdownRunning) {
            val isReady = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))
            if (!isReady) {
                autoDetectProgress = 0f
                return@LaunchedEffect
            }

            val detectionDelay = (scannerSettings.preScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
            val steps = (scannerSettings.preScanDelaySeconds * 10).coerceIn(10, 100)
            val stepTime = (detectionDelay / steps)

            autoDetectProgress = 0f
            for (i in 1..steps) {
                kotlinx.coroutines.delay(stepTime)
                // Bei Verwackeln, Verkippen oder Entfernen des Blatts abbrechen (außer bei Info-Only)
                val checkCondition = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))
                if (!checkCondition) {
                    autoDetectProgress = 0f
                    return@LaunchedEffect
                }
                autoDetectProgress = i / steps.toFloat()
            }

            // Automatisch auslösen!
            executeCapture()
            // Scan SOFORT stoppen und auf nächstes Blatt warten
            hasCapturedCurrentDocument = true
            motionDetectedAfterCapture = false
            autoDetectProgress = 0f
        } else {
            autoDetectProgress = 0f
        }
    }

    // Foto / Galerie Picker für Emulator & Gerät
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        if (scannerSettings.isBulkMode || isAutoMode) {
                            viewModel.saveSinglePageBulk(bmp)
                        } else {
                            viewModel.addPage(bmp)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // Kamera-Berechtigung
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Animierter Scan-Laser
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("scanner_view_container")
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
                    .clickable {
                        if (hasCapturedCurrentDocument) {
                            hasCapturedCurrentDocument = false
                            motionDetectedAfterCapture = false
                        }
                    }
                    .testTag("camera_preview_surface"),
                flashOption = flashOption,
                onFrameAnalysis = { analysis ->
                    frameAnalysis = analysis
                },
                onImageCaptureCreated = { capture ->
                    imageCaptureInstance = capture
                },
                onCameraAvailableChanged = { available ->
                    isCameraHardwareAvailable = available
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamerazugriff erforderlich",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "myDocAnizer benötigt Zugriff auf die Kamera, um Dokumente, Belege und Akten einzuscannen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kamera-Berechtigung erteilen")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("ScannerView", "Einstellungen konnten nicht geöffnet werden: ${e.message}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In Android-Einstellungen erlauben")
                    }
                }
            }
        }

        // Overlay: Dokumenten-Rahmen und Laser
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            val padH = size.width * 0.08f
            val padV = size.height * 0.12f
            val boxWidth = size.width - (padH * 2)
            val boxHeight = size.height - (padV * 2) - 40f

            val bracketLen = 42f
            val strokeW = 4.5f
            val cornerColor = Color(0xFF38BDF8)

            // 4 Eck-Klammern (je 2 Schenkel)
            // Oben-Links
            drawLine(cornerColor, Offset(padH, padV), Offset(padH + bracketLen, padV), strokeW)
            drawLine(cornerColor, Offset(padH, padV), Offset(padH, padV + bracketLen), strokeW)
            // Oben-Rechts
            drawLine(cornerColor, Offset(padH + boxWidth, padV), Offset(padH + boxWidth - bracketLen, padV), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV), Offset(padH + boxWidth, padV + bracketLen), strokeW)
            // Unten-Links
            drawLine(cornerColor, Offset(padH, padV + boxHeight), Offset(padH + bracketLen, padV + boxHeight), strokeW)
            drawLine(cornerColor, Offset(padH, padV + boxHeight), Offset(padH, padV + boxHeight - bracketLen), strokeW)
            // Unten-Rechts
            drawLine(cornerColor, Offset(padH + boxWidth, padV + boxHeight), Offset(padH + boxWidth - bracketLen, padV + boxHeight), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV + boxHeight), Offset(padH + boxWidth, padV + boxHeight - bracketLen), strokeW)

            // Laser
            val currentLaserY = padV + (boxHeight * laserProgress)
            drawLine(
                color = Color(0xFF06B6D4).copy(alpha = 0.85f),
                start = Offset(padH + 4f, currentLaserY),
                end = Offset(padH + boxWidth - 4f, currentLaserY),
                strokeWidth = 3f
            )

            // 5. Wasserwaage / Bullseye zur Horizont-Ausrichtung (Telefon in Waage)
            val centerX = size.width / 2f
            val centerY = padV + (boxHeight / 2f)
            val outerRadius = 42f
            val targetRadius = 15f
            val waageColor = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFF59E0B)

            // Äußerer Fadenkreuz-Ring
            drawCircle(
                color = waageColor.copy(alpha = 0.5f),
                radius = outerRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )

            // Innerer Ziel-Ring
            drawCircle(
                color = waageColor.copy(alpha = 0.85f),
                radius = targetRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )

            // Fadenkreuz-Schnittlinien
            drawLine(
                color = waageColor.copy(alpha = 0.4f),
                start = Offset(centerX - outerRadius - 12f, centerY),
                end = Offset(centerX + outerRadius + 12f, centerY),
                strokeWidth = 2f
            )
            drawLine(
                color = waageColor.copy(alpha = 0.4f),
                start = Offset(centerX, centerY - outerRadius - 12f),
                end = Offset(centerX, centerY + outerRadius + 12f),
                strokeWidth = 2f
            )

            // Wasserwaagen-Blase (bewegt sich entsprechend Neigung des Telefons)
            val maxOffset = outerRadius - 8f
            val bubbleX = (sensorTiltX * 14f).coerceIn(-maxOffset, maxOffset)
            val bubbleY = (sensorTiltY * 14f).coerceIn(-maxOffset, maxOffset)
            drawCircle(
                color = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFFBBF24),
                radius = 8f,
                center = Offset(centerX + bubbleX, centerY + bubbleY)
            )
        }

        // OBERE LEISTE: Dokumenten-Scan Navigation & Galerie-Import
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { onNavigateBack() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("btn_scanner_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück zum Dokumenten Tresor",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.7f)
            ) {
                Text(
                    text = "Dokumenten Scan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            FilledTonalIconButton(
                onClick = { galleryLauncher.launch("image/*") },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("btn_import_gallery_quick")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Dokument aus Galerie importieren",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // MITTIGE STATUS-OVERLAYS (Countdown, Auto-Detect Indikator, Bulk-Erfolgsmeldung)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Countdown-Overlay
            if (isCountdownRunning) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$countdownValue",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // 2. Erfasstes Dokument -> Wartet auf nächstes Blatt
            if (hasCapturedCurrentDocument && !isCountdownRunning) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable {
                            // Tippen zum sofortigen Freigeben des nächsten Scans
                            hasCapturedCurrentDocument = false
                            motionDetectedAfterCapture = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Dokument erfasst ✓",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Nächstes Blatt auflegen (Scan startet automatisch)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA7F3D0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Auto-Detect / Stativ-Scan Fortschritt
            val isAutoOrTripod = scannerSettings.tripodAutoScan || scannerSettings.triggerMode == "AUTO_DETECT"
            if (isAutoOrTripod && !isCountdownRunning && !hasCapturedCurrentDocument && autoDetectProgress > 0f) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { autoDetectProgress },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 3.dp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val statusLabel = if (scannerSettings.tripodAutoScan) {
                                "Dokument erkannt • Scannt gleich..."
                            } else {
                                "Dokument erkannt • Ruhig halten..."
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 4. Schnelles Bulk-Erfolgs-Overlay ("Dokument erfasst, nächstes auflegen...")
            AnimatedVisibility(
                visible = lastBulkSavedDoc != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF065F46).copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "✓ Gespeichert: ${lastBulkSavedDoc?.fileName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "In '${lastBulkSavedDoc?.mainCategory} ➔ ${lastBulkSavedDoc?.subCategory}' • Nächstes Dokument auflegen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }

            // 5. Fehler-Banner bei echten Hardware-/Kamera-Problemen
            AnimatedVisibility(
                visible = scanErrorMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = scanErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // UNTERES BEDIENFELD: Deutlich verkleinert & mit Status-Dashboard
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Vorlagenzeile ist flach und kompakt
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTemplateModal = true }
                        .testTag("template_selector_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val prefixStr = if (selectedTemplate?.mainCategoryId?.isNotBlank() == true) {
                                "${selectedTemplate?.mainCategoryId}_${selectedTemplate?.subCategoryId}- "
                            } else ""
                            val templateLabel = if (selectedTemplate != null)
                                "$prefixStr${selectedTemplate?.mainCategory} ➔ ${selectedTemplate?.subCategory}"
                            else
                                "Zielordner / Vorlage wählen"
                            Text(
                                text = templateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Wechseln",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // MULTI-SEITEN VORSCHAU-LEISTE (nur im manuellen Modus oder wenn Seiten in Bearbeitung)
                if (!isAutoMode && capturedPages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${capturedPages.size} Seite(n) erfasst",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { viewModel.clearPages() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Verwerfen", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        itemsIndexed(capturedPages) { index, _ ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Seite ${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.removePage(index) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Entfernen", modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // LIVE STATUS-PANEL (Information zum aktuellen Scanvorgang)
                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PDF wird erstellt & archiviert...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (hasCapturedCurrentDocument) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF065F46).copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                hasCapturedCurrentDocument = false
                                motionDetectedAfterCapture = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✓ Blatt erfasst • Neues Blatt auflegen oder weiter scannen",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else if (autoDetectProgress > 0f) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { autoDetectProgress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ Dokument erkannt • Scannt automatisch...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // AUSLÖSER-BUTTONS (Jederzeit manuell auslösbar)
                if (capturedPages.isEmpty()) {
                    Button(
                        onClick = { startCaptureProcess() },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(21.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(44.dp)
                            .testTag("btn_capture_page")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Wird verarbeitet..." else if (isCountdownRunning) "Selbstauslöser ($countdownValue)..." else "Dokument scannen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    // Mehrseiten-Modus mit gesammelten Seiten
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { startCaptureProcess() },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(21.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("btn_capture_page")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Seite", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.finishAndSaveMultiPageScan { createdDoc ->
                                    onDocumentScanned(createdDoc)
                                }
                            },
                            enabled = capturedPages.isNotEmpty() && !isProcessing,
                            shape = RoundedCornerShape(21.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .testTag("btn_finish_scan")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF (${capturedPages.size} S.) fertig", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Auto-Dismiss für Bulk-Erfolgsanzeige nach 2.5 Sekunden
    LaunchedEffect(lastBulkSavedDoc) {
        if (lastBulkSavedDoc != null) {
            kotlinx.coroutines.delay(2500L)
            viewModel.clearLastBulkSavedDoc()
        }
    }

    if (showTemplateModal) {
        TemplateSelectModal(
            templates = templates,
            selectedTemplate = selectedTemplate,
            availableDocTypes = docTypes,
            onTemplateSelected = { tmpl ->
                viewModel.selectTemplate(tmpl)
            },
            onSaveTemplate = { updated ->
                viewModel.updateTemplate(updated)
            },
            onDeleteTemplate = { id ->
                viewModel.deleteTemplate(id)
            },
            onDismissRequest = { showTemplateModal = false }
        )
    }

    pendingRuleSuggestion?.let { suggestion ->
        PendingRuleSuggestionDialog(
            suggestion = suggestion,
            availableDocTypes = docTypes
        )
    }
}

/**
 * Mini-Wasserwaage mit dynamischer Blasen-Animation statt reiner Grad-Zahlen.
 * Zeigt unmittelbar visuell an, ob das Smartphone waagerecht über dem Dokument liegt.
 */
@Composable
fun MiniSpiritLevel(
    sensorTiltX: Float,
    sensorTiltY: Float,
    isPhoneInWaage: Boolean,
    modifier: Modifier = Modifier
) {
    val animX by animateFloatAsState(
        targetValue = (sensorTiltX * 4f).coerceIn(-6f, 6f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spirit_bubble_x"
    )
    val animY by animateFloatAsState(
        targetValue = (sensorTiltY * 4f).coerceIn(-6f, 6f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spirit_bubble_y"
    )

    Box(
        modifier = modifier
            .size(20.dp)
            .testTag("mini_spirit_level"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f - 1f
            val targetRadius = 4f
            val color = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFF59E0B)

            // Äußerer Kreis
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Innerer Ziel-Kreis (Fokuspunkt)
            drawCircle(
                color = color.copy(alpha = 0.9f),
                radius = targetRadius,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // Feine Fadenkreuz-Linien
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(center.x - outerRadius, center.y),
                end = Offset(center.x + outerRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(center.x, center.y - outerRadius),
                end = Offset(center.x, center.y + outerRadius),
                strokeWidth = 1f
            )

            // Wasserwaagen-Blase (animiert)
            drawCircle(
                color = if (isPhoneInWaage) Color(0xFF34D399) else Color(0xFFFBBF24),
                radius = 2.8f,
                center = Offset(center.x + animX, center.y + animY)
            )
        }
    }
}

/**
 * Einziges zentrales, platzsparendes Status-Element:
 * 1. Mini-Waage (Animation)
 * 2. Fokus / Schärfe (Icon)
 * 3. Dokument / Kontrast (Icon)
 * 4. Ausleuchtung / Licht (Icon)
 * 5. Knackige, kurze Statusbezeichnung
 */
@Composable
fun CompactScanStatusBar(
    sensorTiltX: Float,
    sensorTiltY: Float,
    isPhoneInWaage: Boolean,
    isSharpnessOptimal: Boolean,
    isDocumentDetected: Boolean,
    hasGoodLight: Boolean,
    hasCapturedCurrentDoc: Boolean,
    isAutoScanning: Boolean,
    toolsInfoOnly: Boolean = false,
    onToggleInfoOnly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isAllOptimal = isPhoneInWaage && isSharpnessOptimal && isDocumentDetected

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                hasCapturedCurrentDoc -> Color(0xFF10B981).copy(alpha = 0.8f)
                isAutoScanning -> Color(0xFF38BDF8).copy(alpha = 0.8f)
                toolsInfoOnly -> Color(0xFF38BDF8).copy(alpha = 0.6f)
                isAllOptimal -> Color(0xFF10B981).copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.15f)
            }
        ),
        modifier = modifier
            .clickable { onToggleInfoOnly() }
            .testTag("scan_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Mini-Waage mit Blasen-Animation statt Zahlen
            MiniSpiritLevel(
                sensorTiltX = sensorTiltX,
                sensorTiltY = sensorTiltY,
                isPhoneInWaage = isPhoneInWaage
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 2. Schärfe / Erschütterung
            Icon(
                imageVector = Icons.Default.FilterCenterFocus,
                contentDescription = "Schärfe",
                tint = if (isSharpnessOptimal) Color(0xFF34D399) else Color(0xFFFBBF24),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 3. Dokumenterkennung / Kontrast
            Icon(
                imageVector = Icons.Default.CropFree,
                contentDescription = "Dokument",
                tint = if (isDocumentDetected) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 4. Ausleuchtung / Licht
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = "Licht",
                tint = if (hasGoodLight) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 5. Kurze, typische Statusbezeichnung
            val (statusText, statusColor) = when {
                hasCapturedCurrentDoc -> "✓ Erfasst" to Color(0xFF34D399)
                isAutoScanning -> "Scannt..." to Color(0xFF38BDF8)
                toolsInfoOnly -> "Info" to Color(0xFF38BDF8)
                !isPhoneInWaage -> "Ausrichten" to Color(0xFFFBBF24)
                !isSharpnessOptimal -> "Ruhig halten" to Color(0xFFFBBF24)
                !isDocumentDetected -> "Kein Dok." to Color(0xFF94A3B8)
                else -> "Bereit" to Color(0xFF34D399)
            }
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


