package com.kryptos.vault.ocr

import com.kryptos.vault.data.Template

/**
 * Extracts canonical field values from raw OCR text. Returned keys match the field names
 * the hero cards and edit screen know how to render, so the result can be merged directly.
 *
 * Returns only the fields it's confident about — callers should leave existing values for
 * keys not present in the map.
 */
object OcrParsers {

    fun parse(template: Template, text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return when (template) {
            Template.ID_CARD -> parseIdCard(text)
            Template.PASSPORT -> parsePassport(text)
            Template.DRIVERS_LICENSE -> parseDriversLicense(text)
            Template.BIRTH_CERTIFICATE -> parseBirthCertificate(text)
            Template.PAYMENT_CARD -> parseCreditCard(text)
            Template.BANK_ACCOUNT -> parseBankAccount(text)
            Template.TAX_NUMBER -> parseTaxNumber(text)
            Template.API_KEY -> emptyMap()
            Template.NOTE -> emptyMap()
        }
    }

    /**
     * Parse using both flat text and per-element bounding boxes. Lets credit-card parsing
     * reconstruct numbers whose digit groups OCR onto staggered baselines (common on
     * modern cards), which the regex path cannot.
     */
    fun parse(template: Template, result: OcrResult): Map<String, String> {
        val base = parse(template, result.text).toMutableMap()
        if (template == Template.PAYMENT_CARD) {
            spatialCardNumber(result.tokens)?.let { base["Number"] = it }
        }
        return base
    }

    // ---- Driver's license --------------------------------------------------

    private val DL_NUMBER = Regex("""(?im)(?:DL|LIC(?:ENSE)?\.?\s*(?:NO|#)?)\s*[:#]?\s*([A-Z0-9\-]{5,20})""")
    private val DL_CLASS = Regex("""(?im)(?:CLASS|CLS)\s*[:#]?\s*([A-Z0-9]{1,4})""")

    private fun parseDriversLicense(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()

        DL_NUMBER.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            out["License number"] = it
        }
        DL_CLASS.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            out["Class"] = it
        }
        // Dates: prefer labelled DOB/EXP, else fall back to earliest/latest.
        DOB_LABEL.find(text)?.groupValues?.get(1)?.let { out["Date of birth"] = normalizeDate(it) }
        EXP_LABEL.find(text)?.groupValues?.get(1)?.let { out["Expiry"] = normalizeDate(it) }
        if (!out.containsKey("Date of birth") || !out.containsKey("Expiry")) {
            val dates = (DATE_YMD.findAll(text) + DATE_DMY.findAll(text))
                .map { it.groupValues[1] }
                .map(::normalizeDate)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
            if (dates.isNotEmpty()) {
                out.putIfAbsent("Date of birth", dates.first())
                if (dates.size >= 2) out.putIfAbsent("Expiry", dates.last())
            }
        }

        val name = NAME_LABEL.find(text)?.groupValues?.get(1)?.trim() ?: guessNameLine(text)
        if (!name.isNullOrBlank()) out["Full name"] = titleCase(name)

        // Country/state often appears under a "STATE OF X" / "COUNTRY: Y" header.
        Regex("""(?im)(?:STATE OF|STATE|COUNTRY|PROVINCE)\s*(?:OF\s+)?[:\-]?\s*([A-Z][A-Z' ]{2,})""")
            .find(text)?.groupValues?.get(1)?.trim()?.let { out["Country/State"] = titleCase(it) }

        return out.filterValues { it.isNotBlank() }
    }

    // ---- Birth certificate -------------------------------------------------

    private val REG_NUMBER = Regex("""(?im)(?:REG(?:ISTRATION)?\.?\s*(?:NO|NUMBER|#)?)\s*[:#]?\s*([A-Z0-9\-/]{4,20})""")
    private val FATHER = Regex("""(?im)(?:FATHER(?:'S)?\s*(?:NAME)?|BAPA)\s*[:\-]?\s*([A-Z][A-Z' ]{2,})""")
    private val MOTHER = Regex("""(?im)(?:MOTHER(?:'S)?\s*(?:NAME)?|IBU)\s*[:\-]?\s*([A-Z][A-Z' ]{2,})""")
    private val PLACE_OF_BIRTH = Regex("""(?im)(?:PLACE OF BIRTH|TEMPAT LAHIR|BORN AT|BORN IN)\s*[:\-]?\s*([A-Z][A-Z' ,]{2,})""")
    private val DATE_OF_ISSUE = Regex("""(?im)(?:DATE OF ISSUE|ISSUED ON|TARIKH KELUAR)\s*[:\-]?\s*(\d{2,4}[\-/. ]?\d{2}[\-/. ]?\d{2,4})""")

    private fun parseBirthCertificate(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()

        val name = NAME_LABEL.find(text)?.groupValues?.get(1)?.trim() ?: guessNameLine(text)
        if (!name.isNullOrBlank()) out["Full name"] = titleCase(name)

        DOB_LABEL.find(text)?.groupValues?.get(1)?.let { out["Date of birth"] = normalizeDate(it) }
        DATE_OF_ISSUE.find(text)?.groupValues?.get(1)?.let { out["Date of issue"] = normalizeDate(it) }
        if (!out.containsKey("Date of issue")) {
            // Fallback: pick the most recent date as issue.
            val dates = (DATE_YMD.findAll(text) + DATE_DMY.findAll(text))
                .map { it.groupValues[1] }
                .map(::normalizeDate)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
            if (dates.size >= 2) out["Date of issue"] = dates.last()
        }

        PLACE_OF_BIRTH.find(text)?.groupValues?.get(1)?.trim()?.let {
            out["Place of birth"] = titleCase(it.trimEnd(',', '.'))
        }
        FATHER.find(text)?.groupValues?.get(1)?.trim()?.let { out["Father's name"] = titleCase(it) }
        MOTHER.find(text)?.groupValues?.get(1)?.trim()?.let { out["Mother's name"] = titleCase(it) }
        REG_NUMBER.find(text)?.groupValues?.get(1)?.trim()?.let { out["Registration number"] = it }

        return out.filterValues { it.isNotBlank() }
    }

    // ---- ID card -----------------------------------------------------------

    private val ID_NUMBER = Regex("""\b(\d{2,4}[- ]?\d{2,7}[- ]?\d{2,7})\b""")
    private val MY_NRIC = Regex("""\b(\d{6}-\d{2}-\d{4})\b""")
    private val DATE_DMY = Regex("""\b(\d{2}[\-/.]\d{2}[\-/.]\d{2,4})\b""")
    private val DATE_YMD = Regex("""\b(\d{4}[\-/.]\d{2}[\-/.]\d{2})\b""")
    private val DOB_LABEL = Regex("""(?im)(?:DOB|D\.O\.B|DATE OF BIRTH|TARIKH LAHIR|BIRTH)[^\n]{0,30}?(\d{2,4}[\-/. ]?\d{2}[\-/. ]?\d{2,4})""")
    private val EXP_LABEL = Regex("""(?im)(?:EXP|EXPIRY|EXPIRES|VALID THRU|VALID UNTIL|TAMAT)[^\n]{0,30}?(\d{2,4}[\-/. ]?\d{2}[\-/. ]?\d{2,4})""")
    private val NAT_LABEL = Regex("""(?im)(?:NATIONALITY|WARGANEGARA|NATION)\s*[:\-]?\s*([A-Z][A-Z ]{2,})""")
    private val NAME_LABEL = Regex("""(?im)(?:NAME|NAMA)\s*[:\-]?\s*([A-Z][A-Z'\- ]{2,})""")

    private fun parseIdCard(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()

        // ID number: prefer MY NRIC pattern, fall back to generic hyphenated number.
        (MY_NRIC.find(text) ?: ID_NUMBER.find(text))?.let {
            out["ID number"] = it.groupValues[1].trim()
        }

        // DOB: labelled wins; otherwise pick the earliest date in the text.
        DOB_LABEL.find(text)?.groupValues?.get(1)?.let {
            out["Date of birth"] = normalizeDate(it)
        }
        EXP_LABEL.find(text)?.groupValues?.get(1)?.let {
            out["Expiry"] = normalizeDate(it)
        }
        if (!out.containsKey("Date of birth") || !out.containsKey("Expiry")) {
            val allDates = (DATE_YMD.findAll(text) + DATE_DMY.findAll(text))
                .map { it.groupValues[1] }
                .map(::normalizeDate)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
            if (allDates.isNotEmpty()) {
                out.putIfAbsent("Date of birth", allDates.first())
                if (allDates.size >= 2) out.putIfAbsent("Expiry", allDates.last())
            }
        }

        NAT_LABEL.find(text)?.groupValues?.get(1)?.trim()?.let {
            out["Nationality"] = titleCase(it)
        }

        // Name: labelled wins; otherwise the longest uppercase-ish line that isn't an address.
        val labelled = NAME_LABEL.find(text)?.groupValues?.get(1)?.trim()
        val name = labelled ?: guessNameLine(text)
        if (!name.isNullOrBlank()) out["Full name"] = titleCase(name)

        return out
    }

    private fun guessNameLine(text: String): String? {
        // Heuristic: pick the longest line that is mostly uppercase letters/spaces, 8–60 chars,
        // not a date, not a number, not a known address keyword.
        val noisy = setOf("MALAYSIA", "REPUBLIC", "DEPARTMENT", "GOVERNMENT", "IDENTITY", "CARD")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.length in 8..60 }
            .filter { it.matches(Regex("""[A-Z][A-Z'\- /.]{6,}""")) }
            .filter { ln -> noisy.none { ln.contains(it) } || ln.length > 18 }
            .filter { !DATE_DMY.containsMatchIn(it) && !DATE_YMD.containsMatchIn(it) }
            .maxByOrNull { it.length }
    }

    // ---- Passport ----------------------------------------------------------

    /**
     * ICAO 9303 MRZ for TD3 passports: two 44-char lines starting with P<.
     * Line 1: P<COUNTRY<SURNAME<<GIVEN<NAMES<<<...
     * Line 2: PASSPORT_NO<COUNTRY<DOB<SEX<EXP<...
     */
    private val MRZ_LINE1 = Regex("""P<([A-Z<]{3})([A-Z<]+)""")
    private val MRZ_LINE2 = Regex("""([A-Z0-9<]{9})\d?([A-Z<]{3})(\d{6})\d?([MFX<])(\d{6})""")

    private fun parsePassport(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val collapsed = text.uppercase().replace(Regex("""\s+"""), "")

        MRZ_LINE1.find(collapsed)?.let { m ->
            val nat = m.groupValues[1].trim('<')
            val names = m.groupValues[2]
            val parts = names.split("<<", limit = 2)
            val surname = parts.getOrNull(0)?.replace("<", " ")?.trim().orEmpty()
            val given = parts.getOrNull(1)?.replace("<", " ")?.trim().orEmpty()
            if (surname.isNotBlank()) out["Surname"] = titleCase(surname)
            if (given.isNotBlank()) out["Given names"] = titleCase(given)
            if (nat.isNotBlank()) out["Nationality"] = nat
        }
        MRZ_LINE2.find(collapsed)?.let { m ->
            out["Passport number"] = m.groupValues[1].trimEnd('<')
            out["Date of birth"] = normalizeMrzDate(m.groupValues[3])
            out["Sex"] = m.groupValues[4].takeIf { it != "<" } ?: ""
            out["Expiry"] = normalizeMrzDate(m.groupValues[5])
        }
        return out.filterValues { it.isNotBlank() }
    }

    // ---- Credit card -------------------------------------------------------

    private val CARD_NUMBER = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val CARD_EXPIRY = Regex("""\b(0[1-9]|1[0-2])\s*[/\-]\s*(\d{2}|\d{4})\b""")
    private val NAME_LINE = Regex("""^[A-Z][A-Z'\.\- ]{4,}$""")

    private fun parseCreditCard(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()

        CARD_NUMBER.findAll(text)
            .map { it.value }
            .firstOrNull { isPlausibleCardNumber(it) }
            ?.let { out["Number"] = it.filter { c -> c.isDigit() }.chunked(4).joinToString(" ") }

        CARD_EXPIRY.find(text)?.let { m ->
            val mm = m.groupValues[1]
            val yy = m.groupValues[2].takeLast(2)
            out["Expiry"] = "$mm/$yy"
        }

        // Cardholder: an ALL-CAPS line that isn't the issuer name or a date.
        text.lineSequence()
            .map { it.trim() }
            .filter { NAME_LINE.matches(it) }
            .filter { it.length in 8..30 }
            .filter { !it.contains(Regex("""\d""")) }
            .filter { !it.matches(Regex("""(VISA|MASTERCARD|AMEX|DISCOVER|JCB|UNIONPAY).*""")) }
            .firstOrNull()
            ?.let { out["Cardholder"] = titleCase(it) }

        // Issuer: keyword sniff.
        listOf("VISA", "MASTERCARD", "AMEX", "AMERICAN EXPRESS", "DISCOVER", "JCB", "UNIONPAY")
            .firstOrNull { text.contains(it, ignoreCase = true) }
            ?.let { out["Issuer"] = it.lowercase().replaceFirstChar(Char::uppercase) }

        return out
    }

    /**
     * Reconstruct a card number from individual OCR tokens with bounding boxes. Handles
     * modern layouts where digit groups are spread across multiple lines or staggered
     * (common on modern vertical or minimalist cards).
     *
     * Algorithm:
     * 1. Collect all digit-only tokens of length 3-6.
     * 2. Sort them primarily by Y coordinate (top to bottom) and secondarily by X (left to right).
     * 3. Group them into potential "rows" based on vertical overlap.
     * 4. For cards where the number is split across lines (e.g., 2 groups on line 1, 2 on line 2),
     *    we also try concatenating all groups found across the whole image in their reading order.
     */
    private fun spatialCardNumber(tokens: List<OcrToken>): String? {
        val digitGroups = tokens
            .filter { it.text.all(Char::isDigit) && it.text.length in 3..6 }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .ifEmpty { return null }

        // --- Strategy 1: Traditional horizontal/staggered single row ---
        // (Handles embossed or flat numbers that might be slightly wonky but are one "logical" line)
        val avgHeight = digitGroups.map { it.bounds.height() }.average()
        val tolerance = (avgHeight * 0.7).toInt().coerceAtLeast(10)

        val rows = mutableListOf<MutableList<OcrToken>>()
        for (tok in digitGroups) {
            val cy = tok.bounds.centerY()
            val target = rows.firstOrNull { row ->
                val rowCy = row.map { it.bounds.centerY() }.average()
                kotlin.math.abs(rowCy - cy) <= tolerance
            }
            if (target != null) target.add(tok) else rows.add(mutableListOf(tok))
        }

        for (row in rows) {
            row.sortBy { it.bounds.left }
            val joined = row.joinToString("") { it.text }
            if (joined.length in 13..19 && isPlausibleCardNumber(joined)) {
                return joined.chunked(4).joinToString(" ")
            }
        }

        // --- Strategy 2: Multi-line / Vertical concatenation ---
        // (Handles cards where the 16 digits are printed as a 2x2 or 4x1 block of 4-digit groups)
        // We try sliding windows of groups in reading order (top-to-bottom, left-to-right).
        for (start in digitGroups.indices) {
            var current = ""
            for (end in start until digitGroups.size) {
                current += digitGroups[end].text
                if (current.length in 13..19 && isPlausibleCardNumber(current)) {
                    return current.chunked(4).joinToString(" ")
                }
                if (current.length > 19) break
            }
        }

        return null
    }

    /** Luhn check — most embossed numbers OCR cleanly enough that this is a useful filter. */
    private fun isPlausibleCardNumber(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var alt = false
        for (i in digits.lastIndex downTo 0) {
            var d = digits[i].digitToInt()
            if (alt) { d *= 2; if (d > 9) d -= 9 }
            sum += d
            alt = !alt
        }
        return sum % 10 == 0
    }

    // ---- Bank account ------------------------------------------------------

    private val IBAN = Regex("""\b([A-Z]{2}\d{2}[ ]?(?:[A-Z0-9]{1,4}[ ]?){2,8}[A-Z0-9]{1,4})\b""")
    private val SWIFT = Regex("""\b([A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\b""")
    private val ACCOUNT_NO = Regex("""\b(\d{6,18})\b""")

    private fun parseBankAccount(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        IBAN.find(text.uppercase())?.let { out["IBAN"] = it.groupValues[1].replace(" ", "") }
        SWIFT.find(text.uppercase())?.let { out["SWIFT/BIC"] = it.groupValues[1] }
        if (!out.containsKey("IBAN")) {
            ACCOUNT_NO.find(text)?.let { out["Account number"] = it.groupValues[1] }
        }
        return out
    }

    // ---- Tax number --------------------------------------------------------

    private val TAX_NO = Regex("""\b(?:\d{2}[. ]?\d{3}[. ]?\d{3}[. ]?\d{1}[- ]?\d{3}[. ]?\d{2}|\d{9,15})\b""")

    private fun parseTaxNumber(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        TAX_NO.find(text)?.let { out["Tax number"] = it.value.trim() }
        
        val name = NAME_LABEL.find(text)?.groupValues?.get(1)?.trim() ?: guessNameLine(text)
        if (!name.isNullOrBlank()) out["Full name"] = titleCase(name)

        return out
    }

    // ---- helpers -----------------------------------------------------------

    /** Normalises common date formats to YYYY-MM-DD, returning empty string on failure. */
    private fun normalizeDate(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '-' || it == '/' || it == '.' }
        val parts = digits.split('-', '/', '.', ' ').filter { it.isNotBlank() }
        if (parts.size != 3) return ""
        return runCatching {
            val (a, b, c) = parts
            when {
                a.length == 4 -> "%04d-%02d-%02d".format(a.toInt(), b.toInt(), c.toInt())  // YYYY-MM-DD
                c.length == 4 -> "%04d-%02d-%02d".format(c.toInt(), b.toInt(), a.toInt())  // DD-MM-YYYY
                else -> {
                    // 2-digit year; assume <=70 → 20yy else 19yy.
                    val yy = c.toInt()
                    val year = if (yy <= 70) 2000 + yy else 1900 + yy
                    "%04d-%02d-%02d".format(year, b.toInt(), a.toInt())
                }
            }
        }.getOrDefault("")
    }

    private fun normalizeMrzDate(yymmdd: String): String {
        if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return ""
        val yy = yymmdd.substring(0, 2).toInt()
        val year = if (yy <= 70) 2000 + yy else 1900 + yy
        return "%04d-%s-%s".format(year, yymmdd.substring(2, 4), yymmdd.substring(4, 6))
    }

    private fun titleCase(s: String): String =
        s.lowercase().split(' ', '\t').joinToString(" ") { word ->
            if (word.isEmpty()) word else word.replaceFirstChar(Char::uppercase)
        }
}
