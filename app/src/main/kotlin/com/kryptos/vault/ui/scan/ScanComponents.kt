package com.kryptos.vault.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.theme.BrandGold
import com.kryptos.vault.ui.theme.BrandGoldDeep
import com.kryptos.vault.ui.theme.BrandGoldOnDeep

private val ScanGold = BrandGold

@Composable
internal fun ScannerFrame(bmp: Bitmap?, busy: Boolean, template: Template) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val glowTransition = rememberInfiniteTransition(label = "ScannerGlow")

                    val glowOffset by glowTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "GlowOffset"
                    )

                    val borderGlowBrush = Brush.linearGradient(
                        colors = listOf(
                            BrandGold.copy(alpha = 0.9f),
                            BrandGold.copy(alpha = 0.45f),
                            BrandGold.copy(alpha = 0.9f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f * glowOffset, 1000f)
                    )

                    val laserY by glowTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "LaserY"
                    )

                    if (bmp != null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0x10FFFFFF),
                            modifier = Modifier
                                .fillMaxSize()
                                .border(BorderStroke(2.dp, borderGlowBrush), RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Scanned document",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * laserY
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                ScanGold,
                                                Color.Transparent
                                            )
                                        ),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 3.5.dp.toPx()
                                    )

                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0x22C9A227),
                                                Color.Transparent
                                            )
                                        ),
                                        topLeft = Offset(0f, y - 24.dp.toPx()),
                                        size = Size(size.width, 24.dp.toPx())
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0x06FFFFFF),
                            modifier = Modifier
                                .fillMaxSize()
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (busy) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val y = size.height * laserY
                                        drawLine(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    ScanGold,
                                                    Color.Transparent
                                                )
                                            ),
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val length = 20.dp.toPx()
                                    val stroke = 3.dp.toPx()
                                    val margin = 16.dp.toPx()
                                    val bracketColor = if (busy) BrandGoldDeep else BrandGold.copy(alpha = 0.5f)

                                    drawLine(bracketColor, Offset(margin, margin), Offset(margin + length, margin), strokeWidth = stroke)
                                    drawLine(bracketColor, Offset(margin, margin), Offset(margin, margin + length), strokeWidth = stroke)

                                    drawLine(bracketColor, Offset(size.width - margin, margin), Offset(size.width - margin - length, margin), strokeWidth = stroke)
                                    drawLine(bracketColor, Offset(size.width - margin, margin), Offset(size.width - margin, margin + length), strokeWidth = stroke)

                                    drawLine(bracketColor, Offset(margin, size.height - margin), Offset(margin + length, size.height - margin), strokeWidth = stroke)
                                    drawLine(bracketColor, Offset(margin, size.height - margin), Offset(margin, size.height - margin - length), strokeWidth = stroke)

                                    drawLine(bracketColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - length, size.height - margin), strokeWidth = stroke)
                                    drawLine(bracketColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - length), strokeWidth = stroke)
                                }

                                if (busy) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = BrandGold,
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "PROCESSING SECURE SCAN\u2026",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = BrandGold
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DocumentScanner,
                                            contentDescription = null,
                                            tint = BrandGold.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = if (template == Template.PAYMENT_CARD) {
                                                "Align credit card inside targeting brackets. Camera works best for embossed text. Try NFC scan for flat debit cards."
                                            } else {
                                                "Align document page inside the targeting frame. Google AI scanner will crop edges and extract text automatically."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
}

@Composable
internal fun AutoFillPreview(parsed: Map<String, String>, capturedBitmap: Bitmap?, rawText: String) {
                if (parsed.isNotEmpty()) {
                    Text(
                        text = "AUTO-FILL RECOGNITION PREVIEW",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = BrandGoldDeep,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        parsed.forEach { (k, v) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x14C9A227),
                                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.18f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = k.uppercase(),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = v,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                } else if (capturedBitmap != null && rawText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x08FFFFFF),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "RAW EXTRACTED TEXT (NO DIRECT FIELDS DETECTED)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = rawText.take(300) + if (rawText.length > 300) "\u2026" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
}

@Composable
internal fun ScanControls(capturedBitmap: Bitmap?, onRescan: () -> Unit, onCancel: () -> Unit) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onRescan,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandGoldDeep,
                            contentColor = BrandGoldOnDeep
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (capturedBitmap == null) "OPEN CAMERA SCANNER" else "RESCAN DOCUMENT",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text = if (capturedBitmap == null) "Cancel" else "Discard",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
}
