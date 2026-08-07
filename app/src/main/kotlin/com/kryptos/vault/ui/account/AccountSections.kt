package com.kryptos.vault.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kryptos.vault.security.AuthManager
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.res.stringResource
import com.fmz.kryptos.R

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun AccountHeader(
    account: AuthManager.Account?,
    working: String?,
    onSignOut: () -> Unit,
) {
    val current = account ?: return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            if (!current.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = current.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                current.displayName ?: current.email ?: "Signed in",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            current.email?.takeIf { it != current.displayName }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            enabled = working == null,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (working == "Signing out…") {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Text(if (working == "Signing out…") "Signing out…" else "Sign out")
        }
    }
}

@Composable
internal fun SupportSection(
    adsRemoved: Boolean,
    message: String?,
    onRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
) {
    if (!adsRemoved) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemoveAds),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Remove Ads",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "One-time purchase to hide ads in the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRestorePurchases) {
                Text(stringResource(R.string.restore_purchases))
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Ads removed — thanks for supporting Kryptos!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun BackupSection(
    account: AuthManager.Account?,
    working: String?,
    feedback: String?,
    lastBackupAtMillis: Long,
    onRefresh: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    passphraseSet: Boolean,
    onSetPassphrase: () -> Unit,
    onRemovePassphrase: () -> Unit,
) {
    Text(
        "Backups include your encrypted documents and the unique key needed to unlock them.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (lastBackupAtMillis == 0L) "No local backup date found."
                    else "Last known backup: ${DateFormat.getDateTimeInstance().format(Date(lastBackupAtMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (lastBackupAtMillis == 0L) {
                    Text(
                        "Check your Google Drive for existing data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (lastBackupAtMillis == 0L && account != null) {
                TextButton(
                    onClick = onRefresh,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.check_drive), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onBackup,
            enabled = account != null && working == null,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back_up))
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = account != null && working == null,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.restore))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (passphraseSet) "Backup passphrase set" else "No backup passphrase",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (passphraseSet) {
                    "Cloud backups are protected. Keep this passphrase safe — it cannot be recovered."
                } else {
                    "Backups upload the raw key. Set a passphrase for zero-knowledge protection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (passphraseSet) {
            TextButton(onClick = onSetPassphrase, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Change")
            }
            TextButton(onClick = onRemovePassphrase, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = onSetPassphrase, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Set")
            }
        }
    }
    if (working != null && working != "Signing out…") {
        Text(working, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    feedback?.let {
        Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
internal fun GeneralSection(
    remindersEnabled: Boolean,
    onToggleReminders: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Expiry Reminders",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Get notified before your documents and cards expire.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = remindersEnabled,
            onCheckedChange = onToggleReminders
        )
    }
}

@Composable
internal fun DangerZoneSection(onDelete: () -> Unit) {
    Text(
        "Permanently deletes every entry, encryption keys, and sign you out of your account on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onDelete,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.delete_vault_and_account_1))
    }
}
