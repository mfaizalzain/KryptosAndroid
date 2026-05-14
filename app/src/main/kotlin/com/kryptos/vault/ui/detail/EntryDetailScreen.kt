package com.kryptos.vault.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.security.SecureClipboard
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.cards.HeroCard
import com.kryptos.vault.ui.cards.heroFieldKeys

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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
        val allFields = remember(current.fieldsJson) { FieldsCodec.decode(current.fieldsJson) }
        val heroKeys = remember(current.template) { heroFieldKeys(current.template) }
        val showAttachmentSeparately = current.template !in setOf(Template.ID_CARD, Template.PASSPORT)
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
                    )
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

            current.attachment?.takeIf { showAttachmentSeparately }?.let { bytes ->
                item {
                    Text(
                        text = "ATTACHMENT",
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
                                contentDescription = "Attachment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                            )
                        }
                    }
                }
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
