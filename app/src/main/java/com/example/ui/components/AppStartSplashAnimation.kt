package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 7-Sekunden Ununterbrochene High-Fidelity Start-Animation:
 * - Läuft kontinuierlich und flüssig für exakt 7 Sekunden ohne Unterbrechung oder statische Zwischenbilder.
 * - Durchgehender 0-100% Ladebalken & System-Telemetrie.
 * - Neon-Laser Scanner, wirbelnde Orbital-Partikel und pulsierende myDocAnizer Holographie.
 * - Sanfter finaler Übergang direkt in die Haupt-App / Wizard.
 * - Jederzeit per Klick sofort überspringbar.
 */
@Composable
fun AppStartSplashAnimation(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = 7000
    val progressAnim = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    // Endlose Hilfs-Rotationen für Partikel & Scan-Pulse während der 7s
    val particleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_rotation"
    )

    val laserScanY by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_scan"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        // Kontinuierlicher 7.0 Sekunden Ablauf
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val progress = progressAnim.value
    val percentInt = (progress * 100).toInt().coerceIn(0, 100)

    // Gesamter Ausblend-Alpha-Wert kurz vor Ende (ab 90% bis 100%)
    val overallAlpha = if (progress > 0.90f) {
        ((1f - progress) / 0.10f).coerceIn(0f, 1f)
    } else {
        1f
    }

    // Farben & Gradients
    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFFF007F)
    val deepViolet = Color(0xFF8B5CF6)
    val darkBg = Color(0xFF060A14)

    val anizerGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF8B5CF6),
            Color(0xFFC026D3),
            Color(0xFFFF007F)
        )
    )

    // 36 Kosmische / Cyber-Partikel
    val particles = remember {
        List(36) { i ->
            val radius = 80f + (i * 7f) % 110f
            val speed = if (i % 2 == 0) 1.0f else -0.8f
            val offsetAngle = (i * 0.1745f) * 3.14159f
            val color = when (i % 3) {
                0 -> neonCyan
                1 -> neonMagenta
                else -> deepViolet
            }
            val size = 2.2f + (i % 4) * 1.3f
            SplashParticle(radius, speed, offsetAngle, color, size)
        }
    }

    // Status-Text je nach Fortschritt
    val statusText = when {
        progress < 0.25f -> "INITIALISIERUNG DER PRIVATEN SANDBOX..."
        progress < 0.50f -> "ON-DEVICE OCR & KI-INFERENZ GELADEN..."
        progress < 0.75f -> "ZERO-KNOWLEDGE VERSCHLÜSSELUNG AKTIV..."
        progress < 0.95f -> "DOKUMENTEN-TRESOR BEREIT..."
        else -> "STARTET..."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overallAlpha)
            .background(darkBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Bei Klick sofort überspringen
                onAnimationFinished()
            }
            .testTag("app_start_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // 1. KONTINUIERLICHE HINTERGRUND- & LASER-CANVAS (ununterbrochen aktiv)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f - 60f)

            // Zentraler pulsierender Radial-Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonCyan.copy(alpha = 0.22f * pulseScale),
                        deepViolet.copy(alpha = 0.14f * pulseScale),
                        Color.Transparent
                    ),
                    center = center,
                    radius = 320f
                ),
                center = center,
                radius = 320f
            )

            // Cyber-Eckmarkierungen (Neon Cyan & Magenta)
            val boxRadius = 140f
            val strokeWidth = 3f

            // Cyan Ecke (Links-Oben & Links-Unten)
            val cyanPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - boxRadius, center.y - 40f)
                lineTo(center.x - boxRadius, center.y + boxRadius - 20f)
                quadraticTo(
                    center.x - boxRadius, center.y + boxRadius,
                    center.x - boxRadius + 20f, center.y + boxRadius
                )
                lineTo(center.x + 30f, center.y + boxRadius)
            }
            drawPath(
                path = cyanPath,
                color = neonCyan,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Magenta Ecke (Rechts-Unten & Rechts-Oben)
            val magentaPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x + boxRadius, center.y + 40f)
                lineTo(center.x + boxRadius, center.y - boxRadius + 20f)
                quadraticTo(
                    center.x + boxRadius, center.y - boxRadius,
                    center.x + boxRadius - 20f, center.y - boxRadius
                )
                lineTo(center.x - 30f, center.y - boxRadius)
            }
            drawPath(
                path = magentaPath,
                color = neonMagenta,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Vertikal oszillierender Laser-Scan-Strahl
            val scanY = center.y + laserScanY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        neonCyan.copy(alpha = 0.85f),
                        neonMagenta.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    startX = center.x - boxRadius,
                    endX = center.x + boxRadius
                ),
                start = Offset(center.x - boxRadius + 10f, scanY),
                end = Offset(center.x + boxRadius - 10f, scanY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            // Rotierende Orbital-Partikel
            val radAngle = Math.toRadians(particleAngle.toDouble()).toFloat()
            particles.forEach { p ->
                val currentAngle = p.offsetAngle + (p.speed * radAngle)
                val currentRadius = p.radius + sin(radAngle * 2f + p.offsetAngle) * 12f
                val px = center.x + cos(currentAngle) * currentRadius
                val py = center.y + sin(currentAngle) * currentRadius
                val alpha = (0.35f + 0.65f * sin(radAngle * 3f + p.offsetAngle)).coerceIn(0.2f, 1f)

                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size,
                    center = Offset(px, py)
                )
            }
        }

        // 2. ZENTRALES LOGO UND MARKEN-BANNER (Flüssig und durchgehend)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 80.dp)
                .scale(pulseScale)
        ) {
            AppLogoBanner(
                iconHeight = 60.dp,
                fontSize = 28f,
                includeContainer = true,
                showTagline = true,
                tagline = "first private and smart document management system"
            )
        }

        // 3. UNTERER BEREICH: 0-100% LADEBALKEN & STATUS-TELEMETRIE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 32.dp, end = 32.dp)
                .fillMaxWidth()
        ) {
            // Status-Text & Prozentanzeige
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8)
                    )
                )
                Text(
                    text = "$percentInt%",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fortschrittsbalken mit Neon-Verlauf
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(neonCyan, deepViolet, neonMagenta)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tippen zum Überspringen",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.40f)
            )
        }
    }
}

private data class SplashParticle(
    val radius: Float,
    val speed: Float,
    val offsetAngle: Float,
    val color: Color,
    val size: Float
)
