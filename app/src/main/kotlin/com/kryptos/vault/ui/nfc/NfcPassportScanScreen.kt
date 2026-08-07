package com.kryptos.vault.ui.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import com.kryptos.vault.ui.findActivity
import androidx.compose.runtime.rememberUpdatedState
import android.os.Bundle
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.nfc.PassportNfcReader
import com.kryptos.vault.nfc.PassportScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.fmz.kryptos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcPassportScanScreen(
    prefillFieldsJson: String?,
    onCancel: () -> Unit,
    onApply: (parsedFieldsJson: String, attachment: ByteArray?) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val currentOnApply by rememberUpdatedState(onApply)
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
                val localDocNumber = docNumber
                val localDob = dob
                val localExpiry = expiry
                val isReady = localDocNumber.length in 6..14 &&
                    localDob.length == 6 && localDob.all { it.isDigit() } &&
                    localExpiry.length == 6 && localExpiry.all { it.isDigit() }

                if (!isReady) {
                    status = "Please complete passport details before scanning."
                    return@launch
                }

                working = true
                status = "Reading chip…"
                try {
                    val result = withContext(Dispatchers.IO) {
                        PassportNfcReader.read(isoDep, localDocNumber, localDob, localExpiry)
                    }
                    val (json, attachment) = encodeScanResult(result)
                    status = "Done."
                    currentOnApply(json, attachment)
                } catch (t: Throwable) {
                    status = "Read failed: ${t.message ?: t.javaClass.simpleName}. Re-check MRZ and try again."
                } finally {
                    working = false
                }
            }
        }
    }

    DisposableEffect(callback, activity) {
        if (activity != null && nfcAdapter != null && nfcAdapter.isEnabled) {
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
        onDispose { 
            if (activity != null) {
                nfcAdapter?.disableReaderMode(activity)
            }
        }
    }

    // Rich premium custom matte slate dark gradient mesh
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F131D),
            Color(0xFF1C2230)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Scan Passport Chip", 
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) { 
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = Color.White
                            ) 
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Header guidance message
                Text(
                    text = "Verify identity via biometric RFID chip. Fill in the MRZ fields manually or prefill with camera scan first, then hold the passport flat against the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )

                // NFC status chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        nfcAdapter == null -> NoticeChip("Device lacks NFC hardware")
                        !nfcAdapter.isEnabled -> NoticeChip("NFC is disabled - turn on in settings")
                    }
                }

                // Custom gold & matte slate text fields
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD4AF37), // Warm gold focus accent
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = Color(0xFFD4AF37),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFD4AF37)
                )

                OutlinedTextField(
                    value = docNumber,
                    onValueChange = { docNumber = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text(stringResource(R.string.passport_number)) },
                    supportingText = { Text(stringResource(R.string.as_printed_on_the_passport_photo_page), color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    singleLine = true
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.dob_yymmdd)) },
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.expiry_yymmdd)) },
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }

                // Interactive Radar Wave Animation & Passport graphic
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Pulse animation
                    val infiniteTransition = rememberInfiniteTransition(label = "PassportRadar")
                    
                    val wave1 by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 2.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Wave 1"
                    )
                    val alpha1 by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Alpha 1"
                    )

                    val wave2 by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 2.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, delayMillis = 1400, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Wave 2"
                    )
                    val alpha2 by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, delayMillis = 1400, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Alpha 2"
                    )

                    // Laser sweep position
                    val sweepPosition by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Sweep"
                    )

                    // Wave colors based on state
                    val radarColor = when {
                        !readyToScan -> Color.White.copy(alpha = 0.12f)
                        working -> Color(0xFFD4AF37) // Warm gold
                        else -> Color(0xFF8F9CAE) // Muted matte slate gray
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = 60.dp.toPx()
                        if (readyToScan) {
                            drawCircle(
                                color = radarColor,
                                radius = baseRadius * wave1,
                                alpha = alpha1,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = radarColor,
                                radius = baseRadius * wave2,
                                alpha = alpha2,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        } else {
                            drawCircle(
                                color = radarColor,
                                radius = baseRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // Glassmorphic Passport Notebook UI
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (readyToScan) Color(0x158F9CAE) else Color(0x0AFFFFFF),
                        modifier = Modifier
                            .size(100.dp, 140.dp)
                            .border(
                                BorderStroke(
                                    1.5.dp, 
                                    if (readyToScan) Color(0xFFD4AF37).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)
                                ), 
                                RoundedCornerShape(12.dp)
                            ),
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = if (readyToScan) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Icon(
                                    Icons.Filled.Nfc,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (readyToScan) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.2f)
                                )
                            }

                            // Laser sweep line if reading
                            if (working) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * sweepPosition
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0xFFD4AF37),
                                                Color.Transparent
                                            )
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive HUD terminal-style status line
                val statusText = when {
                    !readyToScan -> "WAITING FOR COMPLETE DETAILS"
                    working -> "DECRYPTING RFID TRANSFERS"
                    status != null && status!!.contains("failed", ignoreCase = true) -> "READ ERROR - REPOSITION CHIP"
                    else -> "READY TO CONNECT RFID"
                }

                val terminalColor = when {
                    !readyToScan -> Color.White.copy(alpha = 0.4f)
                    working -> Color(0xFFD4AF37) // Warm gold
                    status != null && status!!.contains("failed", ignoreCase = true) -> Color(0xFFFF5252)
                    else -> Color(0xFF8F9CAE) // Muted matte slate gray
                }

                val terminalBg = when {
                    !readyToScan -> Color.White.copy(alpha = 0.03f)
                    working -> Color(0x15D4AF37)
                    status != null && status!!.contains("failed", ignoreCase = true) -> Color(0x15FF5252)
                    else -> Color(0x158F9CAE)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = terminalBg,
                    border = BorderStroke(1.dp, terminalColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (working) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = terminalColor
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = terminalColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                status?.let {
                    Text(
                        text = it, 
                        color = Color(0xFFFF5252), 
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Cancel Button styled in clean matte slate glassmorphic surface
                Button(
                    onClick = onCancel,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E3547), // Muted matte slate container
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { 
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                }
            }
        }
    }
}

@Composable
private fun NoticeChip(text: String) {
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
