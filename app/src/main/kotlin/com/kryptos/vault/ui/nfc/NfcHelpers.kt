package com.kryptos.vault.ui.nfc

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.nfc.PassportScan

@Composable
fun NoticeChip(text: String) {
        AssistChip(
            onClick = {}, 
            label = { Text(text, color = Color(0xFFFF5252)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color(0x15FF5252)
            ),
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = Color(0xFFFF5252).copy(alpha = 0.3f)
            )
        )
    }

    /** Best-effort conversion of common date formats to MRZ YYMMDD. */
fun mrzDate(input: String): String {
        val digits = input.filter(Char::isDigit)
        return when (digits.length) {
            6 -> digits                                       // already YYMMDD
            8 -> digits.substring(2, 8)                       // YYYYMMDD → YYMMDD
            else -> ""
        }
    }

fun encodeScanResult(scan: PassportScan): Pair<String, ByteArray?> {
        val fields = mutableListOf<Pair<String, String>>()
        fun add(name: String, value: String) {
            if (value.isNotBlank()) fields += name to value
        }
        add("Surname", scan.surname)
        add("Given names", scan.givenNames)
        add("Passport number", scan.documentNumber)
        add("Nationality", scan.nationality)
        add("Date of birth", formatMrzDate(scan.dateOfBirth))
        add("Sex", scan.sex)
        add("Expiry", formatMrzDate(scan.expiry))
        val photo = scan.photoBytes?.takeIf { it.isNotEmpty() && scan.photoMimeType?.contains("jpeg") == true }
        return FieldsCodec.encode(fields) to photo
    }

fun formatMrzDate(yymmdd: String): String {
        if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return yymmdd
        val yy = yymmdd.substring(0, 2).toInt()
        // ICAO 9303 has no century — treat <=70 as 20yy, else 19yy. Good enough for DOB/expiry.
        val century = if (yy <= 70) 2000 else 1900
        val year = century + yy
        val month = yymmdd.substring(2, 4)
        val day = yymmdd.substring(4, 6)
        return "$year-$month-$day"
    }
