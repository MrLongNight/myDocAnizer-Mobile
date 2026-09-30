package com.example.service

import android.content.Context
import android.net.Uri
import com.example.model.BankStatementEntryEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class BankStatementParseResult(
    val entries: List<BankStatementEntryEntity>,
    val totalParsed: Int,
    val cashWithdrawalsCount: Int,
    val totalCashWithdrawalsAmount: Double,
    val detectedBankFormat: String,
    val errors: List<String> = emptyList()
)

object BankStatementParserService {

    private val CASH_KEYWORDS = listOf(
        "geldautomat",
        "ga-auszahlung",
        "ga auszahlung",
        "atm",
        "barauszahlung",
        "barabhebung",
        "bargeld",
        "kassenautomat",
        "eigengeldautomat",
        "fremdgeldautomat",
        "cash withdrawal",
        "bargeldauszahlung",
        "bar-abhebung",
        "bar abhebung"
    )

    /**
     * Liest eine Datei über einen Android Content-URI ein und parst diese.
     * Versucht zuerst UTF-8 und schlägt bei Sonderzeichen automatisch auf ISO-8859-1 (typisch für dt. Banken) um.
     */
    fun parseFromUri(context: Context, uri: Uri, filename: String = ""): BankStatementParseResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return BankStatementParseResult(emptyList(), 0, 0, 0.0, "Unbekannt", listOf("Datei konnte nicht gelesen werden."))

            // Versuche zuerst UTF-8, prüfe ob ISO-8859-1 sinnvoller ist (häufig bei Sparkassen/VR-Banken)
            var text = String(bytes, StandardCharsets.UTF_8)
            if (text.contains("") || (!text.contains("ä") && !text.contains("ö") && !text.contains("ü") && String(bytes, Charset.forName("ISO-8859-1")).contains("ü"))) {
                text = String(bytes, Charset.forName("ISO-8859-1"))
            }

            parseCsvOrText(text, filename)
        } catch (e: Exception) {
            BankStatementParseResult(
                entries = emptyList(),
                totalParsed = 0,
                cashWithdrawalsCount = 0,
                totalCashWithdrawalsAmount = 0.0,
                detectedBankFormat = "Fehler",
                errors = listOf("Fehler beim Lesen der Datei: ${e.localizedMessage}")
            )
        }
    }

    /**
     * Parst Text (CSV oder formatierter Text) in BankStatementEntryEntity-Einträge.
     */
    fun parseCsvOrText(rawContent: String, filename: String = ""): BankStatementParseResult {
        val lines = rawContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return BankStatementParseResult(emptyList(), 0, 0, 0.0, "Leere Datei", listOf("Die Datei enthält keinen lesbaren Text."))
        }

        // 1. Trennzeichen ermitteln (Semikolon ist deutscher Standard für Bank-CSV)
        val firstLine = lines.firstOrNull { it.contains(";") || it.contains(",") || it.contains("\t") } ?: lines.first()
        val delimiter = when {
            firstLine.count { it == ';' } >= 2 -> ';'
            firstLine.count { it == '\t' } >= 2 -> '\t'
            firstLine.count { it == ',' } >= 2 -> ','
            else -> ';'
        }

        val detectedFormat = detectFormat(lines, delimiter, filename)

        // 2. Finde Header-Zeile
        var headerIndex = -1
        var dateCol = -1
        var textCol = -1
        var purposeCol = -1
        var amountCol = -1

        for (i in lines.indices) {
            val cols = splitCsvLine(lines[i], delimiter).map { it.trim().lowercase(Locale.GERMAN) }
            val dIdx = cols.indexOfFirst { it.contains("buchungstag") || it.contains("buchungsdatum") || it.contains("belegdatum") || it.contains("valuta") || it == "datum" || it == "date" }
            val aIdx = cols.indexOfFirst { it.contains("betrag") || it.contains("umsatz") || it == "amount" || it == "wert" }

            if (dIdx != -1 && aIdx != -1) {
                headerIndex = i
                dateCol = dIdx
                amountCol = aIdx
                textCol = cols.indexOfFirst { it.contains("buchungstext") || it.contains("vorgang") || it.contains("transaktion") || it.contains("art") }
                purposeCol = cols.indexOfFirst { it.contains("verwendungszweck") || it.contains("beguenstigter") || it.contains("zahlungsempfaenger") || it.contains("auftraggeber") || it.contains("partner") || it.contains("name") }
                break
            }
        }

        val entries = mutableListOf<BankStatementEntryEntity>()
        val errors = mutableListOf<String>()

        if (headerIndex != -1 && dateCol != -1 && amountCol != -1) {
            // Tabellarisches CSV-Parsing
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
            val cal = Calendar.getInstance()

            for (i in (headerIndex + 1) until lines.size) {
                val row = splitCsvLine(lines[i], delimiter)
                if (row.size <= maxOf(dateCol, amountCol)) continue

                val dateStr = row[dateCol].trim()
                val amountStr = row[amountCol].trim()
                val bText = if (textCol != -1 && textCol < row.size) row[textCol].trim() else ""
                val purp = if (purposeCol != -1 && purposeCol < row.size) row[purposeCol].trim() else ""

                val dateTimestamp = parseDate(dateStr) ?: continue
                val amount = parseGermanAmount(amountStr) ?: continue

                cal.timeInMillis = dateTimestamp
                val monthKey = monthFormat.format(cal.time)

                val fullText = "$bText $purp".trim()
                val isCash = isCashWithdrawal(fullText)

                val entry = BankStatementEntryEntity(
                    id = UUID.randomUUID().toString(),
                    date = dateTimestamp,
                    bookingText = if (bText.isNotBlank()) bText else if (isCash) "Geldautomat Barabhebung" else "Bankbuchung",
                    purpose = purp,
                    amount = amount,
                    isCashWithdrawal = isCash,
                    monthYear = monthKey
                )
                entries.add(entry)
            }
        } else {
            // Fallback: Zeilenweises Regex-Parsing (z. B. aus PDF-Kontoauszug oder unstrukturiertem Text)
            val dateRegex = Regex("""(\d{2}\.\d{2}\.\d{2,4})""")
            val amountRegex = Regex("""(-?\d{1,3}(?:\.\d{3})*,\d{2}|-?\d+\.\d{2})\s*(?:EUR|€|S|H)?""")
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.GERMANY)
            val cal = Calendar.getInstance()

            for (line in lines) {
                val dateMatch = dateRegex.find(line)
                val amountMatch = amountRegex.find(line)

                if (dateMatch != null && amountMatch != null) {
                    val dateTimestamp = parseDate(dateMatch.value) ?: continue
                    val amount = parseGermanAmount(amountMatch.value) ?: continue

                    cal.timeInMillis = dateTimestamp
                    val monthKey = monthFormat.format(cal.time)

                    val cleanLine = line.replace(dateMatch.value, "").replace(amountMatch.value, "").trim()
                    val isCash = isCashWithdrawal(cleanLine)

                    val entry = BankStatementEntryEntity(
                        id = UUID.randomUUID().toString(),
                        date = dateTimestamp,
                        bookingText = if (isCash) "Geldautomat Barabhebung" else cleanLine.take(40).ifBlank { "Kontoauszugsbuchung" },
                        purpose = cleanLine,
                        amount = amount,
                        isCashWithdrawal = isCash,
                        monthYear = monthKey
                    )
                    entries.add(entry)
                }
            }
        }

        val cashWithdrawals = entries.filter { it.isCashWithdrawal }
        val totalCashAmount = cashWithdrawals.sumOf { abs(it.amount) }

        return BankStatementParseResult(
            entries = entries,
            totalParsed = entries.size,
            cashWithdrawalsCount = cashWithdrawals.size,
            totalCashWithdrawalsAmount = totalCashAmount,
            detectedBankFormat = detectedFormat,
            errors = errors
        )
    }

    private fun detectFormat(lines: List<String>, delimiter: Char, filename: String): String {
        val fullHeader = lines.take(5).joinToString(" ").lowercase(Locale.GERMAN)
        val fnLower = filename.lowercase(Locale.GERMAN)

        return when {
            fullHeader.contains("auftragskonto") && fullHeader.contains("buchungstag") -> "Sparkasse SEPA-CSV"
            fullHeader.contains("volksbank") || fullHeader.contains("raiffeisenbank") || (fullHeader.contains("bezeichnung auftragskonto") && fullHeader.contains("iban auftragskonto")) -> "Volksbank / VR-Bank CSV"
            fullHeader.contains("dkb") || fnLower.contains("dkb") -> "DKB Cash-CSV"
            fullHeader.contains("ing-diba") || fullHeader.contains("ing ") || fnLower.contains("ing") -> "ING Girokonto CSV"
            fullHeader.contains("postbank") || fnLower.contains("postbank") -> "Postbank CSV"
            fullHeader.contains("commerzbank") || fnLower.contains("commerzbank") -> "Commerzbank CSV"
            delimiter == ';' -> "Deutsches Standard Bank-CSV (;)"
            delimiter == ',' -> "Internationales Bank-CSV (,)"
            else -> "Kontoauszug Text / PDF"
        }
    }

    fun isCashWithdrawal(text: String): Boolean {
        val lower = text.lowercase(Locale.GERMAN)
        return CASH_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(cleanQuotes(current.toString()))
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        result.add(cleanQuotes(current.toString()))
        return result
    }

    private fun cleanQuotes(str: String): String {
        var s = str.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        }
        return s.replace("\"\"", "\"")
    }

    private fun parseDate(dateStr: String): Long? {
        val cleaned = dateStr.trim().replace("/", ".").replace("-", ".")
        val formats = listOf(
            SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY),
            SimpleDateFormat("dd.MM.yy", Locale.GERMANY),
            SimpleDateFormat("yyyy.MM.dd", Locale.GERMANY)
        )

        for (format in formats) {
            try {
                format.isLenient = false
                val date = format.parse(cleaned)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseGermanAmount(amountStr: String): Double? {
        var s = amountStr.trim().replace("€", "").replace("EUR", "").trim()
        val isNegativeSignAtEnd = s.endsWith("-") || s.endsWith("S")
        s = s.removeSuffix("-").removeSuffix("+").removeSuffix("S").removeSuffix("H").trim()

        // 1.250,50 -> 1250.50
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".")
        } else if (s.contains(",")) {
            s = s.replace(",", ".")
        }

        val parsed = s.toDoubleOrNull() ?: return null
        return if (isNegativeSignAtEnd) -abs(parsed) else parsed
    }

    /**
     * Erzeugt realitätsnahe Test-/Musterbuchungen für den gewählten Monat (z.B. für Tests oder Demonstration).
     */
    fun generateDemoStatement(monthKey: String): List<BankStatementEntryEntity> {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
        val (year, month) = try {
            val parts = monthKey.split("-")
            parts[0].toInt() to parts[1].toInt() - 1
        } catch (_: Exception) {
            cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
        }

        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)

        fun createTimestamp(day: Int): Long {
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 11)
            cal.set(Calendar.MINUTE, 30)
            return cal.timeInMillis
        }

        return listOf(
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(3),
                bookingText = "Geldautomat Sparkasse",
                purpose = "Barauszahlung GA Sparkasse Filiale Stadtmitte",
                amount = -200.00,
                isCashWithdrawal = true,
                monthYear = monthKey
            ),
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(7),
                bookingText = "Kartenzahlung REWE Markt",
                purpose = "Lebensmitteleinkauf Girocard",
                amount = -48.75,
                isCashWithdrawal = false,
                monthYear = monthKey
            ),
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(14),
                bookingText = "Geldautomat Volksbank",
                purpose = "Bargeldabhebung ATM Volksbank eG",
                amount = -150.00,
                isCashWithdrawal = true,
                monthYear = monthKey
            ),
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(18),
                bookingText = "Dauerauftrag Miete",
                purpose = "Wohnungsmiete & Nebenkosten",
                amount = -850.00,
                isCashWithdrawal = false,
                monthYear = monthKey
            ),
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(24),
                bookingText = "Auszahlung Geldautomat DKB",
                purpose = "GA-Auszahlung Visa Debit",
                amount = -100.00,
                isCashWithdrawal = true,
                monthYear = monthKey
            ),
            BankStatementEntryEntity(
                id = UUID.randomUUID().toString(),
                date = createTimestamp(28),
                bookingText = "Gehalt / Bezüge",
                purpose = "Lohnabrechnung Arbeitgeber",
                amount = 2650.00,
                isCashWithdrawal = false,
                monthYear = monthKey
            )
        )
    }
}
