package com.kryptos.vault.ui.scan

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.ocr.OcrParsers
import com.kryptos.vault.ocr.OcrScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    template: Template,
    onCancel: () -> Unit,
    onApply: (parsedFieldsJson: String, rawText: String, attachment: ByteArray) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var parsed by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var rawText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoLaunched by remember { mutableStateOf(false) }

    // Google's on-device document scanner: edge detect, perspective correct, enhance.
    val scanner = remember {
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) {
            // User cancelled — leave UI in the manual "Rescan" state.
            return@rememberLauncherForActivityResult
        }
        val data = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pageUri = data?.pages?.firstOrNull()?.imageUri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            try {
                val bmp = withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(ctx.contentResolver, pageUri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                }
                capturedBitmap = bmp
                capturedBytes = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                }
                val ocr = withContext(Dispatchers.Default) {
                    runCatching { OcrScanner.extractStructured(bmp, 0) }.getOrNull()
                }
                rawText = ocr?.text.orEmpty()
                parsed = if (ocr != null) OcrParsers.parse(template, ocr) else emptyMap()
            } catch (t: Throwable) {
                error = t.localizedMessage ?: t.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    fun startScan() {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                error = it.localizedMessage ?: "Couldn't start scanner. Make sure Google Play Services is up to date."
            }
    }

    LaunchedEffect(Unit) {
        if (!autoLaunched && capturedBitmap == null) {
            autoLaunched = true
            startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan document") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val bytes = capturedBytes
                    if (bytes != null) {
                        TextButton(onClick = {
                            val parsedJson = FieldsCodec.encode(parsed.toList())
                            onApply(parsedJson, rawText, bytes)
                        }) { Text("Use") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val bmp = capturedBitmap
            if (bmp != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Scanned document",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                    )
                }
            } else if (busy) {
                Text("Processing scan…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    if (template == Template.PAYMENT_CARD) {
                        "Position your card in the frame. Tip: Camera scan works best for embossed cards. For modern flat cards, use the 'Scan NFC chip' option instead."
                    } else {
                        "Position the document inside the camera frame. The scanner will detect edges automatically and crop, then run OCR."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (parsed.isNotEmpty()) {
                Text(
                    "Auto-fill preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                parsed.forEach { (k, v) ->
                    Text("$k: $v", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (capturedBitmap != null && rawText.isNotBlank()) {
                Text(
                    "Couldn't detect labelled fields — raw text will be saved into a \"Scanned text\" field.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(rawText.take(300), style = MaterialTheme.typography.bodySmall)
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = ::startScan,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Text(
                    if (capturedBitmap == null) "  Open scanner" else "  Rescan",
                )
            }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (capturedBitmap == null) "Cancel" else "Discard") }
        }
    }
}
