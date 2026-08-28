package com.kryptos.vault.ui.scan

import android.app.Activity
import android.graphics.Bitmap
import com.kryptos.vault.ui.findActivity
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val activity = remember(ctx) { ctx.findActivity() }
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
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener {
                    error = it.localizedMessage ?: "Couldn't start scanner. Make sure Google Play Services is up to date."
                }
        } else {
            error = "Unable to find base Activity context wrapper"
        }
    }

    LaunchedEffect(Unit) {
        if (!autoLaunched && capturedBitmap == null) {
            autoLaunched = true
            startScan()
        }
    }

    // Deep premium background gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF070B14),
            Color(0xFF111728)
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
                            text = "Scan Document", 
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
                    actions = {
                        val bytes = capturedBytes
                        if (bytes != null) {
                            TextButton(onClick = {
                                val parsedJson = FieldsCodec.encode(parsed.toList())
                                onApply(parsedJson, rawText, bytes)
                            }) { 
                                Text(
                                    text = "Use", 
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ) 
                            }
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val bmp = capturedBitmap
                
                // Holographic Scanner Frame or Photo Preview Container

                ScannerFrame(bmp = capturedBitmap, busy = busy, template = template)

                if (parsed.isNotEmpty() || (capturedBitmap != null && rawText.isNotBlank())) {
                    AutoFillPreview(parsed = parsed, capturedBitmap = capturedBitmap, rawText = rawText)
                }

                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                ScanControls(
                    capturedBitmap = capturedBitmap,
                    onRescan = ::startScan,
                    onCancel = onCancel,
                )
            }
        }
    }
}
