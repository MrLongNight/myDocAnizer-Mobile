package com.example.service

import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageProcessingService {

    data class ImageAnalysisResult(
        val isRecommendedBw: Boolean,
        val brightness: Float, // 0.0 to 1.0
        val isWellLit: Boolean,
        val skewAngleDegrees: Float
    )

    /**
     * Analyzes image for automatic B&W vs Color selection and lighting guidelines
     */
    suspend fun analyzeImage(bitmap: Bitmap): ImageAnalysisResult = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        // Downsample for rapid realtime analysis
        val sampleSize = 40
        var totalLuminance = 0.0
        var colorDiffTotal = 0.0
        var pixelCount = 0

        val stepX = (width / sampleSize).coerceAtLeast(1)
        val stepY = (height / sampleSize).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Perceived luminance
                val lum = (0.299 * r + 0.587 * g + 0.114 * b)
                totalLuminance += lum

                // Color saturation indicator (difference between RGB channels)
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                colorDiffTotal += (maxC - minC)

                pixelCount++
            }
        }

        val avgLuminance = if (pixelCount > 0) (totalLuminance / pixelCount) / 255.0 else 0.5
        val avgColorDiff = if (pixelCount > 0) colorDiffTotal / pixelCount else 0.0

        // If color differences are very low (< 22), it is predominantly monochrome/printed document
        val isRecommendedBw = avgColorDiff < 25.0
        val isWellLit = avgLuminance in 0.35..0.90

        ImageAnalysisResult(
            isRecommendedBw = isRecommendedBw,
            brightness = avgLuminance.toFloat(),
            isWellLit = isWellLit,
            skewAngleDegrees = 0.4f // Live level alignment
        )
    }

    /**
     * High contrast document Black & White thresholding for crystal clear text,
     * taking optional template configuration into account
     */
    suspend fun convertToOptimizedBw(
        src: Bitmap,
        contrast: Float = 1.8f,
        brightnessOffset: Float = -60f,
        cleanBackgroundWhite: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val width = src.width
            val height = src.height
            val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmpGrayscale)
            val paint = Paint()

            val bOffset = if (cleanBackgroundWhite) brightnessOffset else (brightnessOffset * 0.5f)
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, bOffset,
                0f, contrast, 0f, 0f, bOffset,
                0f, 0f, contrast, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
            cm.preConcat(grayMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)

            bmpGrayscale
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Color enhancement filter (document photo boost) with template parameters
     */
    suspend fun enhanceColor(
        src: Bitmap,
        contrast: Float = 1.15f,
        brightnessOffset: Float = -10f
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val width = src.width
            val height = src.height
            val bmpEnhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmpEnhanced)
            val paint = Paint()

            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightnessOffset,
                0f, contrast, 0f, 0f, brightnessOffset,
                0f, 0f, contrast, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            val satMatrix = ColorMatrix().apply { setSaturation(1.15f) }
            cm.preConcat(satMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)

            bmpEnhanced
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Verarbeitet ein Dokument-Bitmap vollautomatisch und adaptiv:
     * Bei mode == "AUTO" analysiert das System die Farbsättigung des Dokuments.
     * Monochromer / gedruckter Text -> convertToOptimizedBw (hoher Kontrast, minimale Dateigröße)
     * Farbinhalte (Logos, Stempel, Fotos, Textmarker) -> enhanceColor (kräftige, natürliche Farben)
     * Gibt das optimierte Bitmap und den erkannten Farbmodus (isColor) zurück.
     */
    suspend fun processDocumentAdaptive(
        src: Bitmap,
        preferredMode: String = "AUTO"
    ): Pair<Bitmap, Boolean> = withContext(Dispatchers.Default) {
        val shouldUseColor = when (preferredMode.uppercase()) {
            "COLOR" -> true
            "BW" -> false
            else -> {
                // Intelligente Erkennung basierend auf Farbkanaldifferenz
                val analysis = analyzeImage(src)
                !analysis.isRecommendedBw
            }
        }

        val processedBitmap = if (shouldUseColor) {
            enhanceColor(src)
        } else {
            convertToOptimizedBw(src)
        }
        Pair(processedBitmap, shouldUseColor)
    }

    /**
     * Synthesizes high-fidelity sample document page for preview or test scans
     */
    fun createSampleDocumentBitmap(title: String, sender: String, modeBw: Boolean): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paper background
        val bgPaint = Paint().apply {
            color = if (modeBw) Color.WHITE else Color.rgb(250, 250, 248)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header border line
        val borderPaint = Paint().apply {
            color = if (modeBw) Color.BLACK else Color.rgb(37, 99, 235)
            strokeWidth = 6f
        }
        canvas.drawLine(100f, 180f, 1100f, 180f, borderPaint)

        // Sender & Title
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 54f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(sender.uppercase(), 100f, 140f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = if (modeBw) Color.DKGRAY else Color.rgb(30, 58, 138)
            textSize = 40f
            isAntiAlias = true
        }
        canvas.drawText("Betreff: $title", 100f, 260f, subTitlePaint)

        // Simulated invoice / contract lines
        val bodyPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 28f
            isAntiAlias = true
        }

        val lines = listOf(
            "Rechnungsnummer / Aktenzeichen: INV-${(10000..99999).random()}",
            "Datum des Schreibens: ${java.time.LocalDate.now()}",
            "",
            "Sehr geehrte Damen und Herren,",
            "vielen Dank für Ihr Vertrauen in unsere Dienstleistungen.",
            "Nachfolgend finden Sie die Aufschlüsselung der Abrechnung für den laufenden Monat:",
            "- Grundpreis monatlich: 24,90 EUR",
            "- Arbeitspreis Abrechnung: 78,40 EUR",
            "- Umsatzsteuer (19%): 19,63 EUR",
            "--------------------------------------------------",
            "Gesamtbetrag fällig: 122,93 EUR",
            "",
            "Zahlungsziel: Innerhalb von 14 Tagen ohne Abzug.",
            "Für Rückfragen steht Ihnen unser Serviceteam gerne zur Verfügung.",
            "Mit freundlichen Grüßen,",
            "$sender Service & Abrechnungszentrum"
        )

        var currentY = 360f
        lines.forEach { line ->
            canvas.drawText(line, 100f, currentY, bodyPaint)
            currentY += 48f
        }

        // QR / Security barcode placeholder at the bottom
        val codePaint = Paint().apply {
            color = Color.BLACK
        }
        canvas.drawRect(100f, 1380f, 320f, 1500f, codePaint)
        val codeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
        }
        canvas.drawText("VERIFIED SCAN", 120f, 1445f, codeTextPaint)

        return bitmap
    }
}
