package com.kryptos.vault.ui.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import com.kryptos.vault.ui.findActivity
import androidx.compose.runtime.rememberUpdatedState
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.kryptos.vault.nfc.CardNfcReader
import com.kryptos.vault.ui.theme.BrandGold
import com.kryptos.vault.ui.theme.BrandGoldDeep
import com.kryptos.vault.ui.theme.BrandGoldOnDeep
import com.kryptos.vault.ui.theme.BrandGoldSoft
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
    val activity = remember(ctx) { ctx.findActivity() }
    val currentOnApply by rememberUpdatedState(onApply)
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
                    currentOnApply(FieldsCodec.encode(fields))
                } catch (t: Throwable) {
                    error = t.message ?: "Read failed. Reposition card and try again."
                    status = "Ready to scan"
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
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            nfcAdapter.enableReaderMode(activity, callback, flags, null)
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
                            text = "Scan Credit/Debit Card", 
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Intro Text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SECURE CHIP READER",
                        style = MaterialTheme.typography.labelLarge,
                        color = BrandGoldDeep, // Luxury gold accent
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Hold card against the back of your phone",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Kryptos will securely extract card details via encrypted NFC connection. CVV remains private.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Interactive Radar Wave Animation & Card UI
                Box(
                    modifier = Modifier
                        .height(280.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Infinite pulse transitions
                    val infiniteTransition = rememberInfiniteTransition(label = "RadarWaves")
                    
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
                            animation = tween(2800, delayMillis = 900, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Wave 2"
                    )
                    val alpha2 by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, delayMillis = 900, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Alpha 2"
                    )

                    val wave3 by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 2.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, delayMillis = 1800, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Wave 3"
                    )
                    val alpha3 by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2800, delayMillis = 1800, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Alpha 3"
                    )

                    // Laser sweep inside the card when reading
                    val sweepPosition by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Sweep"
                    )

                    // Radar waves drawn on canvas
                    val radarColor = if (working) BrandGold else Color(0xFF8F9CAE) // Muted matte slate or warm gold
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = 80.dp.toPx()
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
                        drawCircle(
                            color = radarColor,
                            radius = baseRadius * wave3,
                            alpha = alpha3,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Glassmorphic Credit Card Frame
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x158F9CAE), // Matte slate glassmorphic base
                        modifier = Modifier
                            .size(190.dp, 120.dp)
                            .border(BorderStroke(1.5.dp, BrandGold.copy(alpha = 0.35f)), RoundedCornerShape(16.dp)), // Premium gold outline
                        shadowElevation = 8.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Subtly blurred abstract background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0x228F9CAE), Color.Transparent),
                                            radius = 300f
                                        )
                                    )
                            )
                            
                            // Golden Chip
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 24.dp)
                                    .size(28.dp, 22.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFF9D976), Color(0xFFE9B646))
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            )

                            // Contactless Sign
                            Icon(
                                Icons.Filled.Nfc,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                                    .size(32.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )

                            // Card Brand Icon (Subtle CreditCard silhouette)
                            Icon(
                                Icons.Filled.CreditCard,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 16.dp, bottom = 16.dp)
                                    .size(24.dp),
                                tint = Color.White.copy(alpha = 0.4f)
                            )

                            // Laser sweep line if reading (Gold!)
                            if (working) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * sweepPosition
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                BrandGold,
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

                // Status & Controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Status Badge Container
                    val badgeBorderColor = when {
                        error != null -> Color(0xFFFF5252).copy(alpha = 0.4f)
                        working -> BrandGold.copy(alpha = 0.4f)
                        else -> Color(0xFF8F9CAE).copy(alpha = 0.4f)
                    }
                    val badgeBgColor = when {
                        error != null -> Color(0x15FF5252)
                        working -> Color(0x15C9A227)
                        else -> Color(0x158F9CAE)
                    }
                    val statusColor = when {
                        error != null -> Color(0xFFFF5252)
                        working -> BrandGold
                        else -> Color(0xFF8F9CAE)
                    }

                    val pulseAlpha by rememberInfiniteTransition(label = "StatusPulse").animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseAlpha"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBgColor,
                        border = BorderStroke(1.dp, badgeBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (working) pulseAlpha else 1.0f }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (working) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = statusColor
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = status.uppercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = statusColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = Color(0xFFFF5252),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

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
                            .height(56.dp)
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
}
