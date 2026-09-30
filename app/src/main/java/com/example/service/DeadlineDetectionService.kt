package com.example.service

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class DetectedDeadline(
    val type: DeadlineType,
    val dateTimestamp: Long,
    val formattedDate: String,
    val rawSnippet: String,
    val confidence: Float,
    val label: String
)

enum class DeadlineType {
    CANCELLATION,    // Kündigungsfrist
    CONTRACT_END,    // Vertragslaufzeit-Ende
    PAYMENT_DUE,     // Zahlungsziel / Fälligkeit
    EXPIRATION,      // Ablaufdatum / Gültigkeit
    OBJECTION        // Widerspruchs- / Einspruchsfrist
}

object DeadlineDetectionService {

    private val GERMAN_MONTHS = mapOf(
        "januar" to 0, "jan" to 0,
        "februar" to 1, "feb" to 1,
        "märz" to 2, "maerz" to 2, "mrz" to 2,
        "april" to 3, "apr" to 3,
        "mai" to 4,
        "juni" to 5, "jun" to 5,
        "juli" to 6, "jul" to 6,
        "august" to 7, "aug" to 7,
        "september" to 8, "sep" to 8, "sept" to 8,
        "oktober" to 9, "okt" to 9,
        "november" to 10, "nov" to 10,
        "dezember" to 11, "dez" to 11
    )

    // Regex für Datumsformate: 15.11.2026, 15.11.26, 15. November 2026
    private val NUMERIC_DATE_REGEX = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""")
    private val TEXTUAL_DATE_REGEX = Regex(
        """\b(\d{1,2})\.?\s+(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Jan|Feb|Mrz|Apr|Jun|Jul|Aug|Sep|Okt|Nov|Dez)\.?\s+(\d{2,4})\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Analysiert OCR-Volltext auf Fristen, Kündigungsstichtage, Zahlungsziele und Ablaufdaten.
     */
    fun detectDeadlines(ocrText: String): List<DetectedDeadline> {
        if (ocrText.isBlank()) return emptyList()

        val results = mutableListOf<DetectedDeadline>()
        val lines = ocrText.lines()
        val now = System.currentTimeMillis()

        // 1. Kündigungsfristen & Kündigungsstichtage
        val cancellationPatterns = listOf(
            Regex("""(?:kündigen\s+bis|kündbar\s+bis|spätestens\s+kündbar\s+bis|kündigung\s+bis|kündigungsstichtag|kündigungstermin)[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:kündigen\s+bis|kündigung\s+bis)[\s\w:]*?(\d{1,2}\.?\s+[A-Za-zä]+\s+\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""kündigungsfrist[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in cancellationPatterns) {
            pattern.findAll(ocrText).forEach { match ->
                val dateStr = match.groupValues.getOrNull(1) ?: return@forEach
                val parsed = parseDateString(dateStr)
                if (parsed != null) {
                    val snippet = match.value.trim().take(80)
                    results.add(
                        DetectedDeadline(
                            type = DeadlineType.CANCELLATION,
                            dateTimestamp = parsed,
                            formattedDate = formatDate(parsed),
                            rawSnippet = snippet,
                            confidence = 0.95f,
                            label = "Kündigungsfrist"
                        )
                    )
                }
            }
        }

        // 2. Vertragslaufzeit-Ende / Mindestlaufzeit
        val contractEndPatterns = listOf(
            Regex("""(?:vertragslaufzeit\s+bis|laufzeit\s+bis|mindestvertragslaufzeit\s+bis|mindestlaufzeit\s+bis|vertragsende)[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:vertragslaufzeit\s+bis|vertragsende)[\s\w:]*?(\d{1,2}\.?\s+[A-Za-zä]+\s+\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in contractEndPatterns) {
            pattern.findAll(ocrText).forEach { match ->
                val dateStr = match.groupValues.getOrNull(1) ?: return@forEach
                val parsed = parseDateString(dateStr)
                if (parsed != null) {
                    val snippet = match.value.trim().take(80)
                    results.add(
                        DetectedDeadline(
                            type = DeadlineType.CONTRACT_END,
                            dateTimestamp = parsed,
                            formattedDate = formatDate(parsed),
                            rawSnippet = snippet,
                            confidence = 0.90f,
                            label = "Vertragslaufzeit"
                        )
                    )
                }
            }
        }

        // 3. Zahlungsziel / Fälligkeiten
        val paymentPatterns = listOf(
            Regex("""(?:zahlbar\s+bis|zahlungsziel|fällig\s+am|fällig\s+bis|überweisen\s+sie\s+bis|rechnungsausgleich\s+bis)[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:fällig|zahlbar)[\s\w:]*?(\d{1,2}\.?\s+[A-Za-zä]+\s+\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in paymentPatterns) {
            pattern.findAll(ocrText).forEach { match ->
                val dateStr = match.groupValues.getOrNull(1) ?: return@forEach
                val parsed = parseDateString(dateStr)
                if (parsed != null) {
                    val snippet = match.value.trim().take(80)
                    results.add(
                        DetectedDeadline(
                            type = DeadlineType.PAYMENT_DUE,
                            dateTimestamp = parsed,
                            formattedDate = formatDate(parsed),
                            rawSnippet = snippet,
                            confidence = 0.88f,
                            label = "Zahlungsziel"
                        )
                    )
                }
            }
        }

        // 4. Ablaufdatum & Gültigkeit (z.B. Ausweise, Pässe, Bescheide, TÜV)
        val expirationPatterns = listOf(
            Regex("""(?:gültig\s+bis|ablaufdatum|gilt\s+bis|verfällt\s+am)[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:widerspruchsfrist\s+bis|einspruchsfrist\s+bis)[\s\w:]*?(\d{1,2}\.\d{1,2}\.\d{2,4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in expirationPatterns) {
            pattern.findAll(ocrText).forEach { match ->
                val dateStr = match.groupValues.getOrNull(1) ?: return@forEach
                val parsed = parseDateString(dateStr)
                if (parsed != null) {
                    val snippet = match.value.trim().take(80)
                    val isObjection = match.value.contains("widerspruch", ignoreCase = true) || match.value.contains("einspruch", ignoreCase = true)
                    results.add(
                        DetectedDeadline(
                            type = if (isObjection) DeadlineType.OBJECTION else DeadlineType.EXPIRATION,
                            dateTimestamp = parsed,
                            formattedDate = formatDate(parsed),
                            rawSnippet = snippet,
                            confidence = 0.85f,
                            label = if (isObjection) "Widerspruchsfrist" else "Gültigkeit / Ablauf"
                        )
                    )
                }
            }
        }

        // Duplikate bereinigen (behalte höchsten Confidence-Wert pro Timestamp)
        return results
            .groupBy { "${it.type}_${it.dateTimestamp}" }
            .map { it.value.maxByOrNull { d -> d.confidence } ?: it.value.first() }
            .sortedBy { it.dateTimestamp }
    }

    /**
     * Ermittelt die primäre Kündigungsfrist oder das primäre Fälligkeitsdatum für ein Dokument
     */
    fun extractPrimaryDeadlines(ocrText: String): Pair<Long?, Long?> {
        val detected = detectDeadlines(ocrText)
        val cancellation = detected.firstOrNull { it.type == DeadlineType.CANCELLATION }?.dateTimestamp
            ?: detected.firstOrNull { it.type == DeadlineType.PAYMENT_DUE }?.dateTimestamp
            ?: detected.firstOrNull { it.type == DeadlineType.OBJECTION }?.dateTimestamp
        val contractEnd = detected.firstOrNull { it.type == DeadlineType.CONTRACT_END }?.dateTimestamp
            ?: detected.firstOrNull { it.type == DeadlineType.EXPIRATION }?.dateTimestamp

        return Pair(cancellation, contractEnd)
    }

    private fun parseDateString(dateStr: String): Long? {
        val clean = dateStr.trim().replace(",", ".").replace("/", ".")
        val cal = Calendar.getInstance()

        // Numerisch: 15.11.2026 oder 15.11.26
        NUMERIC_DATE_REGEX.find(clean)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            var year = match.groupValues[3].toIntOrNull() ?: return null
            if (year < 100) year += 2000

            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 12)
            return cal.timeInMillis
        }

        // Textuell: 15. November 2026
        TEXTUAL_DATE_REGEX.find(clean)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val monthStr = match.groupValues[2].lowercase(Locale.GERMAN)
            val month = GERMAN_MONTHS[monthStr] ?: return null
            var year = match.groupValues[3].toIntOrNull() ?: return null
            if (year < 100) year += 2000

            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 12)
            return cal.timeInMillis
        }

        return null
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }
}
