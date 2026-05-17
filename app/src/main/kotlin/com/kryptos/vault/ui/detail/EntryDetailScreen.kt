package com.kryptos.vault.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.shareId
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.security.SecureClipboard
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.cards.HeroCard
import com.kryptos.vault.ui.cards.heroFieldKeys
import com.kryptos.vault.ui.scan.QrGenerator
import com.kryptos.vault.ui.scan.QrSharer
import org.json.JSONObject

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
    val ctx = LocalContext.current
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var qrData by remember { mutableStateOf<String?>(null) }

    fun sharePayload(current: VaultEntry): String {
        val allFields = FieldsCodec.decode(current.fieldsJson)
        if (current.template == Template.QR_CODE) {
            return allFields.firstOrNull { it.first.equals("Content", ignoreCase = true) || it.first.equals("Data", ignoreCase = true) }?.second
                ?: allFields.firstOrNull()?.second
                ?: ""
        }
        return JSONObject().apply {
            put("kryptos", 1)
            put("template", current.template.shareId())
            put("title", current.title)
            put("fields", JSONObject().apply {
                allFields.forEach { put(it.first, it.second) }
            })
        }.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        entry?.title?.ifBlank { "Entry Details" } ?: "Loading...",
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
                    IconButton(onClick = {
                        val current = entry ?: return@IconButton
                        qrData = sharePayload(current)
                    }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Share entry")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this entry?") },
                text = {
                    Text("\"${current.title.ifBlank { "Untitled" }}\" will be permanently removed from your vault. This cannot be undone.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(current)
                        onBack()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        qrData?.let { data ->
            QrCodeDialog(
                data = data,
                title = current.title,
                onDismiss = { qrData = null }
            )
        }

        val allFields = remember(current.fieldsJson) { FieldsCodec.decode(current.fieldsJson) }
        val heroKeys = remember(current.template) { heroFieldKeys(current.template) }
        val extraFields = remember(allFields, heroKeys) {
            allFields.filter { it.first.lowercase() !in heroKeys && it.second.isNotBlank() }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
                start = 24.dp, end = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HeroCard(
                        template = current.template,
                        title = current.title,
                        fields = allFields,
                        attachment = current.attachment,
                        onCopy = { label, value -> SecureClipboard.copy(ctx, label, value) },
                        onShare = { _, _ -> qrData = sharePayload(current) }
                    )
                }
            }

            item {
                Button(
                    onClick = { qrData = sharePayload(current) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share entry")
                }
            }

            current.attachment?.let { bytes ->
                item {
                    Text(
                        text = "ORIGINAL ATTACHMENT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                item {
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    if (bmp != null) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Original attachment",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                    .clip(RoundedCornerShape(24.dp)),
                            )
                        }
                    }
                }
            }

            if (extraFields.isNotEmpty()) {
                item {
                    Text(
                        text = "DETAILS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            items(extraFields.size) { i ->
                val (name, value) = extraFields[i]
                val isSecret = looksSecret(name)
                val show = !isSecret || (revealed[i] == true)
                FieldCard(
                    name = name,
                    value = if (show) value else "•".repeat(value.length.coerceAtMost(16)),
                    showToggle = isSecret,
                    revealed = show,
                    onToggle = { revealed[i] = !(revealed[i] ?: false) },
                    onCopy = { SecureClipboard.copy(ctx, name, value) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap any field to copy. The clipboard clears automatically after 30 seconds for your security.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun QrCodeDialog(data: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val bitmap = remember(data) { QrGenerator.generate(data) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Share with another Kryptos user",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "They can scan this QR code in Kryptos to import the entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { QrSharer.share(ctx, bitmap, title) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share QR image")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (showToggle) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun looksSecret(name: String): Boolean {
    val n = name.lowercase()
    return listOf("password", "pin", "cvv", "secret", "token", "key", "code", "ssn").any { it in n }
}
