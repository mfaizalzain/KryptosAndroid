package com.kryptos.vault.ui.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.nfc.CardNfcReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcCardScanScreen(
    onCancel: () -> Unit,
    onApply: (parsedFieldsJson: String) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Ready to scan") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(ctx) }

    val callback = remember {
        NfcAdapter.ReaderCallback { tag ->
            val isoDep = IsoDep.get(tag) ?: return@ReaderCallback
            scope.launch {
                working = true
                error = null
                status = "Reading card…"
                try {
                    val result = withContext(Dispatchers.IO) {
                        CardNfcReader.read(isoDep)
                    }
                    val fields = listOf(
                        "Number" to result.pan,
                        "Expiry" to (result.expiry ?: ""),
                        "Issuer" to (result.type ?: "")
                    )
                    onApply(FieldsCodec.encode(fields))
                } catch (t: Throwable) {
                    error = t.message ?: "Read failed. Reposition card and try again."
                    status = "Ready to scan"
                } finally {
                    working = false
                }
            }
        }
    }

    DisposableEffect(callback) {
        if (nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            nfcAdapter.enableReaderMode(activity, callback, flags, null)
        }
        onDispose { nfcAdapter?.disableReaderMode(activity) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Credit/Debit Card") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
            
            Text(
                text = "Hold your card against the back of your phone",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            
            Text(
                text = "Kryptos will securely read the card number and expiry date via the chip. Note: CVV must be entered manually for security.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))

            Box(contentAlignment = Alignment.Center) {
                if (working) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    Icon(
                        Icons.Filled.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.titleMedium,
                color = if (working) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            error?.let {
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
