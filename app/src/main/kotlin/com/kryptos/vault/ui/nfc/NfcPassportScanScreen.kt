package com.kryptos.vault.ui.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.nfc.PassportNfcReader
import com.kryptos.vault.nfc.PassportScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcPassportScanScreen(
    prefillFieldsJson: String?,
    onCancel: () -> Unit,
    onApply: (parsedFieldsJson: String, attachment: ByteArray?) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var docNumber by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }
    var autoScanAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(prefillFieldsJson) {
        if (prefilled || prefillFieldsJson.isNullOrBlank()) return@LaunchedEffect
        val fields = FieldsCodec.decode(prefillFieldsJson).associate { it.first.lowercase() to it.second }
        docNumber = fields["passport number"].orEmpty().uppercase().filter { it.isLetterOrDigit() }
        dob = mrzDate(fields["date of birth"].orEmpty())
        expiry = mrzDate(fields["expiry"].orEmpty())
        prefilled = true
    }

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(ctx) }
    val readyToScan = docNumber.length in 6..14 &&
        dob.length == 6 && dob.all { it.isDigit() } &&
        expiry.length == 6 && expiry.all { it.isDigit() }

    val callback = remember {
        NfcAdapter.ReaderCallback { tag ->
            val isoDep = IsoDep.get(tag) ?: return@ReaderCallback
            // Increase timeout for slow passport chips
            isoDep.timeout = 20_000
            scope.launch {
                working = true
                status = "Reading chip…"
                try {
                    val result = withContext(Dispatchers.IO) {
                        PassportNfcReader.read(isoDep, docNumber, dob, expiry)
                    }
                    val (json, attachment) = encodeScanResult(result)
                    status = "Done."
                    onApply(json, attachment)
                } catch (t: Throwable) {
                    status = "Read failed: ${t.message ?: t.javaClass.simpleName}. Re-check MRZ and try again."
                } finally {
                    working = false
                }
            }
        }
    }

    DisposableEffect(readyToScan, callback) {
        if (readyToScan && nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            val opts = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1500)
            }
            nfcAdapter.enableReaderMode(activity, callback, flags, opts)
        }
        onDispose { nfcAdapter?.disableReaderMode(activity) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan passport chip") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Enter the MRZ from the photo page, then hold the passport flat against the back of the phone until reading completes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                nfcAdapter == null -> NoticeChip("This device has no NFC.")
                !nfcAdapter.isEnabled -> NoticeChip("NFC is off — enable it in Settings.")
            }

            OutlinedTextField(
                value = docNumber,
                onValueChange = { docNumber = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                label = { Text("Passport number") },
                supportingText = { Text("As printed on the photo page") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dob,
                onValueChange = { dob = it.filter(Char::isDigit).take(6) },
                label = { Text("Date of birth (YYMMDD)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = it.filter(Char::isDigit).take(6) },
                label = { Text("Expiry (YYMMDD)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Nfc,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (readyToScan) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        when {
                            !readyToScan -> "Fill in all three fields above"
                            working -> "Reading…"
                            else -> "Ready — tap passport to phone"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun NoticeChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}

/** Best-effort conversion of common date formats to MRZ YYMMDD. */
private fun mrzDate(input: String): String {
    val digits = input.filter(Char::isDigit)
    return when (digits.length) {
        6 -> digits                                       // already YYMMDD
        8 -> digits.substring(2, 8)                       // YYYYMMDD → YYMMDD
        else -> ""
    }
}

private fun encodeScanResult(scan: PassportScan): Pair<String, ByteArray?> {
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

private fun formatMrzDate(yymmdd: String): String {
    if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return yymmdd
    val yy = yymmdd.substring(0, 2).toInt()
    // ICAO 9303 has no century — treat <=70 as 20yy, else 19yy. Good enough for DOB/expiry.
    val century = if (yy <= 70) 2000 else 1900
    val year = century + yy
    val month = yymmdd.substring(2, 4)
    val day = yymmdd.substring(4, 6)
    return "$year-$month-$day"
}
