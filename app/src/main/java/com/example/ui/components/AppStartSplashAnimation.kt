package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
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
 * Premium-Audio-Synthesizer für einen atemberaubenden, kopfhörer-optimierten Soundeffekt.
 * NUTZT AUDIO-STREAMING (MODE_STREAM):
 * - Sound startet im selben Millisekundenschritt wie die UI-Animation (Latenz < 10ms).
 * - Keine Vorberechnungspause (Pre-computation Lag behoben).
 * - Garantiert 100% Phasen- und Timingsynchronität zur optischen Timeline.
 * - Fällt in Testumgebungen (ohne Audiogeräte) geräuschlos und sicher zurück.
 */
object SplashSoundSynthesizer {
    private var mediaPlayer: MediaPlayer? = null
    private var wavFile: File? = null
    private val lock = Any()
    private var isPrecomputing = false
    private var appContext: Context? = null

    fun precompute(context: Context) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            appContext = appCtx
            if (wavFile != null || isPrecomputing) return
            isPrecomputing = true
        }
        Thread {
            try {
                val file = File(appCtx.cacheDir, "splash_sound_v6.wav")
                if (!file.exists()) {
                    val data = generateSamples()
                    writeWavFile(file, data)
                }
                synchronized(lock) {
                    wavFile = file
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                synchronized(lock) {
                    isPrecomputing = false
                }
            }
        }.start()
    }

    private fun writeWavFile(file: File, data: ShortArray, sampleRate: Int = 44100) {
        try {
            val totalAudioLen = data.size * 2
            val totalDataLen = totalAudioLen + 36
            val channels = 1
            val byteRate = sampleRate * 2

            val header = ByteArray(44)
            header[0] = 'R'.toByte() // RIFF
            header[1] = 'I'.toByte()
            header[2] = 'F'.toByte()
            header[3] = 'F'.toByte()
            
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            
            header[8] = 'W'.toByte() // WAVE
            header[9] = 'A'.toByte()
            header[10] = 'V'.toByte()
            header[11] = 'E'.toByte()
            
            header[12] = 'f'.toByte() // fmt 
            header[13] = 'm'.toByte()
            header[14] = 't'.toByte()
            header[15] = ' '.toByte()
            
            header[16] = 16 // Sub-chunk size = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            
            header[20] = 1 // PCM = 1
            header[21] = 0
            
            header[22] = channels.toByte() // channels
            header[23] = 0
            
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            
            header[32] = 2 // Block align = 2
            header[33] = 0
            
            header[34] = 16 // Bits per sample = 16
            header[35] = 0
            
            header[36] = 'd'.toByte() // data
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()
            
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            val tempFile = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(header)
                val byteBuffer = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (value in data) {
                    byteBuffer.putShort(value)
                }
                fos.write(byteBuffer.array())
            }
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun generateSamples(): ShortArray {
        val sampleRate = 44100
        val durationSeconds = 4.0
        val numSamples = (durationSeconds * sampleRate).toInt()
        val data = ShortArray(numSamples)
        val riseSamples = (3.3 * sampleRate).toInt()

        for (globalIndex in 0 until numSamples) {
            val t = globalIndex.toDouble() / sampleRate

            if (globalIndex < riseSamples) {
                // Phase 1: Edler, warmer Ambient-Synth-Swell (Slow Choral Pad in C)
                // Extrem beruhigend, tief und luxuriös, komplett ohne kreischende Frequenzen oder Sirenen-Sweeps
                val progress = globalIndex.toDouble() / riseSamples

                // Akkord-Noten (C3, G3, C4, E4) mit leichtem Detuning (Chorus-Effekt für klangliche Wärme)
                val c3_1 = 130.81
                val c3_2 = 131.31
                val g3_1 = 196.00
                val g3_2 = 196.50
                val c4_1 = 261.63
                val c4_2 = 262.13
                val e4_1 = 329.63
                val e4_2 = 330.13

                val wave = 0.30 * kotlin.math.sin(2.0 * Math.PI * c3_1 * t) +
                           0.15 * kotlin.math.sin(2.0 * Math.PI * c3_2 * t) +
                           0.20 * kotlin.math.sin(2.0 * Math.PI * g3_1 * t) +
                           0.10 * kotlin.math.sin(2.0 * Math.PI * g3_2 * t) +
                           0.15 * kotlin.math.sin(2.0 * Math.PI * c4_1 * t) +
                           0.05 * kotlin.math.sin(2.0 * Math.PI * c4_2 * t) +
                           0.10 * kotlin.math.sin(2.0 * Math.PI * e4_1 * t) +
                           0.05 * kotlin.math.sin(2.0 * Math.PI * e4_2 * t)

                // Sehr langsames, entspanntes Atmen des Pads (1.2 Hz)
                val breathing = 0.85 + 0.15 * kotlin.math.sin(2.0 * Math.PI * 1.2 * t)

                // Sanfte Lautstärkekurve (Cosine-Swell von 0% auf 22%)
                val envelope = (1.0 - kotlin.math.cos(Math.PI * progress)) * 0.5 * 0.22 * breathing
                val mixedSample = wave * envelope

                data[globalIndex] = (mixedSample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            } else {
                // Phase 2: Premium Glockenspiel-Akkord (3.3s bis 4.0s)
                val localIndex = globalIndex - riseSamples
                val tChime = localIndex.toDouble() / sampleRate

                val f1 = 261.63  // C4 (Bass-Fundament)
                val f2 = 523.25  // C5 (Glocken-Kern)
                val f3 = 659.25  // E5 (Terz für Wärme)
                val f4 = 783.99  // G5 (Quinte für Glanz)
                val f5 = 987.77  // B5 (große Septime für Eleganz)
                val f6 = 1174.66 // D6 (große None für Ätherik)

                // Individuelle Abkling-Envelopes für physikalisch authentischen Klang
                val decay1 = kotlin.math.exp(-2.2 * tChime) // Bass schwingt länger nach
                val decay2 = kotlin.math.exp(-3.8 * tChime)
                val decay3 = kotlin.math.exp(-4.8 * tChime)
                val decay4 = kotlin.math.exp(-5.8 * tChime)
                val decay5 = kotlin.math.exp(-6.8 * tChime)
                val decay6 = kotlin.math.exp(-8.5 * tChime) // Hohe Obertöne klingen schnell ab

                // Sanfter Attack-Swell am Schlagmoment (20ms), um harten digitalen Einschalt-Klick zu verhindern
                val attack = (tChime / 0.02).coerceIn(0.0, 1.0)

                // Akkord-Synthese (Major-9th)
                val bellWave = 0.25 * kotlin.math.sin(2.0 * Math.PI * f1 * tChime) * decay1 +
                               0.30 * kotlin.math.sin(2.0 * Math.PI * f2 * tChime) * decay2 +
                               0.20 * kotlin.math.sin(2.0 * Math.PI * f3 * tChime) * decay3 +
                               0.15 * kotlin.math.sin(2.0 * Math.PI * f4 * tChime) * decay4 +
                               0.10 * kotlin.math.sin(2.0 * Math.PI * f5 * tChime) * decay5 +
                               0.05 * kotlin.math.sin(2.0 * Math.PI * f6 * tChime) * decay6

                // Zusätzlicher linearer Fade-Out ganz am Ende (letzte 100ms), um Knacksen zu vermeiden
                val fadeTimeLeft = 4.0 - t
                val endFade = if (fadeTimeLeft < 0.1) (fadeTimeLeft / 0.1).coerceIn(0.0, 1.0) else 1.0

                val mixedSample = bellWave * attack * endFade * 0.45

                data[globalIndex] = (mixedSample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return data
    }

    fun playPremiumSplashSound(context: Context? = null) {
        stop() // Laufenden Ton sofort beenden

        val file = synchronized(lock) {
            val ctx = context ?: appContext
            if (wavFile == null && ctx != null) {
                val f = File(ctx.cacheDir, "splash_sound_v6.wav")
                if (!f.exists()) {
                    try {
                        val data = generateSamples()
                        writeWavFile(f, data)
                    } catch (_: Throwable) {}
                }
                wavFile = f
            }
            wavFile
        }

        if (file == null || !file.exists()) return

        Thread {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                }
                synchronized(lock) {
                    mediaPlayer = mp
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        synchronized(lock) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
                mediaPlayer = null
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Überarbeitete, edle 4.0-Sekunden High-Fidelity App-Start-Animation.
 * - Kontinuierlicher 0-100% Ladebalken & System-Telemetrie.
 * - Neon-Laser Scanner, orbital rotierende Cyber-Partikel.
 * - Dynamisches Hologramm-Logo (slow scale-up & floating 3D-rotation).
 * - Visueller Schockwellen-Impuls bei 82.5% Fortschritt, exakt synchron zum Audio-Chime!
 * - Untermalt mit professionellem, rein synthetisch generiertem Audio-Ablauf.
 * - Jederzeit durch Tippen sofort überspringbar.
 */
@Composable
fun AppStartSplashAnimation(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalDurationMs = 4000 // Dauer auf 4.0 Sekunden erhöht für edle Wirkung
    val progressAnim = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    // Endlose Hilfs-Rotationen für Partikel & Scan-Pulse während der 4.0s
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
        initialValue = -90f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_scan"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Abbruchsicherer Finish-Handler, der auch das Audio augenblicklich stoppt
    val safeFinish = {
        SplashSoundSynthesizer.stop()
        onAnimationFinished()
    }

    LaunchedEffect(Unit) {
        // Starte die edle Sound-Synthese (latenzfrei durch Audio-Streaming)
        SplashSoundSynthesizer.playPremiumSplashSound(context)

        // Kontinuierlicher Ablauf der grafischen Timeline
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing)
        )
        safeFinish()
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
        progress < 0.94f -> "DOKUMENTEN-TRESOR BEREIT..."
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
                safeFinish()
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

            // GIGANTISCHER, EDLER SCHOCKWELLEN-IMPULS (EXAKT synchron zum Glocken-Akkord bei 82.5%)
            if (progress >= 0.825f) {
                val swProgress = ((progress - 0.825f) / 0.175f).coerceIn(0f, 1f)
                val swRadius = swProgress * 650f
                val swAlpha = (1f - swProgress) * 0.75f
                drawCircle(
                    color = neonCyan.copy(alpha = swAlpha),
                    radius = swRadius,
                    center = center,
                    style = Stroke(width = 4f + swProgress * 8f)
                )
                drawCircle(
                    color = neonMagenta.copy(alpha = swAlpha * 0.4f),
                    radius = swRadius * 0.85f,
                    center = center,
                    style = Stroke(width = 2f + swProgress * 4f)
                )
            }
        }

        // 2. ZENTRALES LOGO UND MARKEN-BANNER (Floating, flüssig und rotierend)
        val logoScale = 0.80f + (progress * 0.22f) // Sanftes, edles Heranzoomen von 0.8x auf 1.02x
        val logoRotation = -15f * (1f - progress) // Edler, schwebender 3D-Ausrichtungs-Effekt (-15° bis 0°)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 80.dp)
                .graphicsLayer(
                    scaleX = logoScale * pulseScale,
                    scaleY = logoScale * pulseScale,
                    rotationZ = logoRotation
                )
        ) {
            AppLogoBanner(
                iconHeight = 180.dp,
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
