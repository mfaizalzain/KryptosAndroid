package com.kryptos.vault.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.shareId
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.security.SecureClipboard
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.cards.HeroCard
import com.kryptos.vault.ui.cards.Mono
import com.kryptos.vault.ui.components.NativeAdCard
import com.kryptos.vault.ui.scan.QrGenerator
import com.kryptos.vault.ui.scan.QrSharer
import com.kryptos.vault.ui.theme.AppShapeSheet
import org.json.JSONObject
import com.fmz.kryptos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    id: Long,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    var entry by remember { mutableStateOf<VaultEntry?>(null) }
    LaunchedEffect(id) { entry = viewModel.get(id) }
    val adsRemoved by viewModel.adsRemoved.collectAsState()
    val ctx = LocalContext.current
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var qrData by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    fun sharePayload(current: VaultEntry): String {
        val allFields = FieldsCodec.decode(current.fieldsJson)
        val shareFields = if (current.template == Template.QR_CODE) {
            val qrData = allFields
                .firstOrNull { it.first.equals("Data", ignoreCase = true) || it.first.equals("Content", ignoreCase = true) }
                ?.second
                .orEmpty()
            allFields
                .filterNot { it.first.equals("Data", ignoreCase = true) || it.first.equals("Content", ignoreCase = true) }
                .let { fields -> if (qrData.isBlank()) fields else listOf("Data" to qrData) + fields }
        } else {
            allFields
        }
        return JSONObject().apply {
            put("kryptos", 1)
            put("template", current.template.shareId())
            put("title", current.title)
            put("fields", JSONObject().apply {
                shareFields.forEach { put(it.first, it.second) }
            })
        }.toString()
    }

    fun rawQrPayload(current: VaultEntry): String? {
        if (current.template != Template.QR_CODE) return null
        val allFields = FieldsCodec.decode(current.fieldsJson)
        return allFields
            .firstOrNull { it.first.equals("Data", ignoreCase = true) || it.first.equals("Content", ignoreCase = true) }
            ?.second
            ?.takeIf { it.isNotBlank() }
            ?: allFields.firstOrNull()?.second?.takeIf { it.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        "Entry Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_this_entry)) },
                text = {
                    Text(stringResource(R.string.delete_entry_warning, current.title.ifBlank { "Untitled" }))
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(current)
                        onBack()
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        qrData?.let { data ->
            QrCodeDialog(
                data = data,
                genericData = rawQrPayload(current),
                title = current.title,
                onDismiss = { qrData = null }
            )
        }

        val allFields = remember(current.fieldsJson) { FieldsCodec.decode(current.fieldsJson) }
        val detailFields = remember(allFields) {
            allFields.filter { it.second.isNotBlank() }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 40.dp,
                start = 20.dp, end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    template = current.template,
                    title = current.title,
                    fields = allFields,
                    attachment = current.attachment,
                    onCopy = { label, value -> SecureClipboard.copy(ctx, label, value) },
                    onShare = { _, _ -> qrData = sharePayload(current) },
                    interactive = true,
                )
            }

            item {
                Button(
                    onClick = { qrData = sharePayload(current) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_entry))
                }
            }

            current.attachment?.let { bytes ->
                item {
                    SectionLabel("Original attachment")
                }
                item {
                    val configuration = LocalConfiguration.current
                    val density = LocalDensity.current
                    val targetWidthPx = with(density) {
                        (configuration.screenWidthDp.dp - 40.dp).toPx().toInt().coerceAtLeast(1)
                    }
                    var bmp by remember(bytes) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(bytes, targetWidthPx) {
                        val decoded = withContext(Dispatchers.Default) {
                            decodeSampledBitmap(bytes, targetWidthPx)
                        }
                        bmp = decoded
                    }
                    val currentBmp = bmp
                    if (currentBmp != null) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Image(
                                bitmap = currentBmp.asImageBitmap(),
                                contentDescription = "Original attachment",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(currentBmp.width.toFloat() / currentBmp.height.toFloat())
                                    .clip(RoundedCornerShape(22.dp)),
                            )
                        }
                    }
                }
            }

            if (detailFields.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Details")
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (detailFields.any { looksSecretFor(current.template, it.first) }) {
                                Text(
                                    if (showRaw) "Hiding" else "Showing",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(detailFields) { i, (name, value) ->
                val isSecret = looksSecretFor(current.template, name)
                val show = !isSecret || (revealed[i] == true) || showRaw
                FieldCard(
                    name = name,
                    value = if (show) value else "\u2022".repeat(value.length.coerceAtMost(16)),
                    showToggle = isSecret && !showRaw,
                    revealed = show,
                    onToggle = { revealed[i] = !(revealed[i] ?: false) },
                    onCopy = { SecureClipboard.copy(ctx, name, value) },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.clipboard_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            if (!adsRemoved) {
                item {
                    Spacer(Modifier.height(16.dp))
                    NativeAdCard(
                        adUnitId = "ca-app-pub-1016705366714872/4650414807",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun FieldCard(
    name: String,
    value: String,
    showToggle: Boolean,
    revealed: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCopy),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (shouldUseMonoField(name)) Mono else FontFamily.Default,
                )
            }
            if (showToggle) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

private fun shouldUseMonoField(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("number") || lower.contains("expiry") || lower.contains("cvv") ||
        lower.contains("cvc") || lower == "key" || lower.contains("secret") || lower.contains("token") ||
        lower.contains("pin") || lower.contains("iban")
}

private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun looksSecret(name: String): Boolean {
    val n = name.lowercase()
    return listOf(
        "password", "pin", "cvv", "cvc", "secret", "token", "key", "code", "ssn",
        "account number", "account no", "iban", "tax number", "tax id"
    ).any { it in n }
}

private fun looksSecretFor(template: Template, name: String): Boolean {
    val n = name.lowercase()
    if (template == Template.PAYMENT_CARD && (n == "number" || n.contains("card number"))) return true
    return looksSecret(name)
}

@Composable
private fun QrCodeDialog(data: String, genericData: String?, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var showKryptosPayload by remember(data, genericData) { mutableStateOf(genericData == null) }
    val displayData = if (showKryptosPayload || genericData == null) data else genericData
    var bitmap by remember(displayData) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(displayData) {
        val generated = withContext(Dispatchers.Default) {
            QrGenerator.generate(displayData)
        }
        bitmap = generated
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppShapeSheet,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showKryptosPayload) "Share with another Kryptos user" else "Share original QR code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (showKryptosPayload) {
                        "They can scan this QR code in Kryptos to import the entry."
                    } else {
                        "Generic QR scanners will read this as the saved QR content."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBmp = bitmap
                    if (currentBmp != null) {
                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val currentBmp = bitmap
                        if (currentBmp != null) {
                            QrSharer.share(ctx, currentBmp, title)
                        }
                    },
                    enabled = bitmap != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_qr_image))
                }
                if (genericData != null) {
                    TextButton(onClick = { showKryptosPayload = !showKryptosPayload }) {
                        Text(if (showKryptosPayload) "Show original QR" else "Show Kryptos import QR")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
