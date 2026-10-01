package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.ImageProcessingService
import kotlinx.coroutines.launch

@Composable
fun ScanQualityCheckDialog(
    rawBitmap: Bitmap,
    isColorMode: Boolean,
    onAcceptProcessed: (Bitmap) -> Unit,
    onRetake: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var previewMode by remember { mutableIntStateOf(1) } // 0 = Original, 1 = Optimiert, 2 = Ecken-Justierer
    var optimizedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingPreview by remember { mutableStateOf(true) }

    val sharpnessScore = remember(rawBitmap) {
        ImageProcessingService.measureSharpness(rawBitmap)
    }

    // 4 Ecken im relativen Koordinatenraum (0.0f bis 1.0f)
    var cornerTL by remember { mutableStateOf(Offset(0.06f, 0.06f)) }
    var cornerTR by remember { mutableStateOf(Offset(0.94f, 0.06f)) }
    var cornerBR by remember { mutableStateOf(Offset(0.94f, 0.94f)) }
    var cornerBL by remember { mutableStateOf(Offset(0.06f, 0.94f)) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Generiere initiale optimierte Vorschau
    LaunchedEffect(rawBitmap, isColorMode) {
        isProcessingPreview = true
        val opt = ImageProcessingService.generateComparisonPreview(rawBitmap, isColorMode)
        optimizedBitmap = opt
        isProcessingPreview = false
    }

    Dialog(
        onDismissRequest = { onRetake() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header mit Qualitäts-Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aufnahme-Qualitätsprüfung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Schärfe & Ausrichtung vor der Verarbeitung prüfen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Schärfe-Score Badge
                    val sharpnessColor = when {
                        sharpnessScore >= 80f -> Color(0xFF10B981)
                        sharpnessScore >= 60f -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sharpnessColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, sharpnessColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (sharpnessScore >= 75f) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = sharpnessColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${sharpnessScore.toInt()}% Schärfe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = sharpnessColor
                            )
                        }
                    }
                }

                // Ansichts-Umschalter (Original vs. Optimiert vs. Ecken anpassen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = previewMode == 1,
                        onClick = { previewMode = 1 },
                        label = { Text("✨ Optimiert", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).height(32.dp).testTag("chip_preview_optimized")
                    )
                    FilterChip(
                        selected = previewMode == 0,
                        onClick = { previewMode = 0 },
                        label = { Text("📷 Original", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).height(32.dp).testTag("chip_preview_original")
                    )
                    FilterChip(
                        selected = previewMode == 2,
                        onClick = { previewMode = 2 },
                        label = { Text("📐 Ecken", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).height(32.dp).testTag("chip_preview_corners")
                    )
                }

                // Bild-Vorschau-Bereich mit Zoom/Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.9f))
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    val activeBmp = if (previewMode == 1 && optimizedBitmap != null) optimizedBitmap!! else rawBitmap

                    Image(
                        bitmap = activeBmp.asImageBitmap(),
                        contentDescription = "Scan-Vorschau",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Wenn im Ecken-Justier-Modus: Interaktives 4-Ecken-Overlay
                    if (previewMode == 2 && containerSize.width > 0 && containerSize.height > 0) {
                        val cw = containerSize.width.toFloat()
                        val ch = containerSize.height.toFloat()

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val touchPos = change.position
                                        val relPos = Offset(
                                            (touchPos.x / cw).coerceIn(0f, 1f),
                                            (touchPos.y / ch).coerceIn(0f, 1f)
                                        )

                                        // Finde die nächstgelegene Ecke
                                        val dTL = (Offset(cornerTL.x * cw, cornerTL.y * ch) - touchPos).getDistance()
                                        val dTR = (Offset(cornerTR.x * cw, cornerTR.y * ch) - touchPos).getDistance()
                                        val dBR = (Offset(cornerBR.x * cw, cornerBR.y * ch) - touchPos).getDistance()
                                        val dBL = (Offset(cornerBL.x * cw, cornerBL.y * ch) - touchPos).getDistance()

                                        val minD = minOf(dTL, dTR, dBR, dBL)
                                        when (minD) {
                                            dTL -> cornerTL = relPos
                                            dTR -> cornerTR = relPos
                                            dBR -> cornerBR = relPos
                                            dBL -> cornerBL = relPos
                                        }
                                    }
                                }
                        ) {
                            val ptTL = Offset(cornerTL.x * cw, cornerTL.y * ch)
                            val ptTR = Offset(cornerTR.x * cw, cornerTR.y * ch)
                            val ptBR = Offset(cornerBR.x * cw, cornerBR.y * ch)
                            val ptBL = Offset(cornerBL.x * cw, cornerBL.y * ch)

                            // Rahmenlinien des erkannten Dokuments
                            val lineColor = Color(0xFF3B82F6)
                            drawLine(color = lineColor, start = ptTL, end = ptTR, strokeWidth = 3f)
                            drawLine(color = lineColor, start = ptTR, end = ptBR, strokeWidth = 3f)
                            drawLine(color = lineColor, start = ptBR, end = ptBL, strokeWidth = 3f)
                            drawLine(color = lineColor, start = ptBL, end = ptTL, strokeWidth = 3f)

                            // 4 Anfasser-Kreise (Griffe)
                            val handleRadius = 14f
                            listOf(ptTL, ptTR, ptBR, ptBL).forEach { pt ->
                                drawCircle(color = Color.White, radius = handleRadius + 2f, center = pt)
                                drawCircle(color = lineColor, radius = handleRadius, center = pt)
                                drawCircle(color = Color.White, radius = 4f, center = pt)
                            }
                        }
                    }

                    if (isProcessingPreview) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Detail-Information & Tipps
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (previewMode == 2) {
                                "Ziehe die 4 Eckpunkte mit dem Finger, um die Dokumentenränder exakt anzupassen."
                            } else {
                                "Auflösung: ${rawBitmap.width}x${rawBitmap.height}px • Modus: ${if (isColorMode) "Farbe" else "Schwarz-Weiß"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Aktions-Schaltflächen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("btn_retake_scan")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Neu scannen", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val finalBmp = if (previewMode == 2) {
                                    // Wende 4-Ecken-Warp an
                                    val bw = rawBitmap.width.toFloat()
                                    val bh = rawBitmap.height.toFloat()
                                    val corners = listOf(
                                        PointF(cornerTL.x * bw, cornerTL.y * bh),
                                        PointF(cornerTR.x * bw, cornerTR.y * bh),
                                        PointF(cornerBR.x * bw, cornerBR.y * bh),
                                        PointF(cornerBL.x * bw, cornerBL.y * bh)
                                    )
                                    val warped = ImageProcessingService.cropAndWarpPerspective(rawBitmap, corners)
                                    val (opt, _) = ImageProcessingService.processDocumentAdaptive(warped, if (isColorMode) "COLOR" else "BW")
                                    opt
                                } else {
                                    optimizedBitmap ?: rawBitmap
                                }
                                onAcceptProcessed(finalBmp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).height(46.dp).testTag("btn_accept_scan")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Übernehmen", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
