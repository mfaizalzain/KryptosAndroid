package com.kryptos.vault.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.kryptos.vault.BillingManager
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.backup.DriveBackupManager
import com.kryptos.vault.security.AuthManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as KryptosApp
    val auth = remember { app.authManager }
    val backup = remember { app.backupManager }
    val billing = remember { app.billingManager }
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf<AuthManager.Account?>(auth.currentAccount) }
    val isPremium by billing.isPremium.collectAsState()
    var working by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<BackupAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(Unit) { account = auth.currentAccount }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            working = null
            feedback = "Drive access was denied."
            return@rememberLauncherForActivityResult
        }
        val client = Identity.getAuthorizationClient(ctx as Activity)
        val authResult = runCatching {
            client.getAuthorizationResultFromIntent(result.data)
        }.getOrNull()
        val token = authResult?.accessToken
        val action = pendingAction
        if (token == null || action == null) {
            working = null
            feedback = "Couldn't obtain Drive access token."
            return@rememberLauncherForActivityResult
        }
        pendingAccessToken = token
        runDriveAction(scope, backup, token, action,
            onWorking = { working = it },
            onFeedback = { feedback = it })
        pendingAction = null
    }

    fun runDriveFlow(action: BackupAction) {
        working = when (action) {
            BackupAction.BACKUP -> "Backing up to Drive…"
            BackupAction.RESTORE -> "Restoring from Drive…"
            BackupAction.BACKUP_OWN -> "Backing up to your Drive…"
        }
        feedback = null
        pendingAction = action
        val activity = ctx as Activity
        val scopeStrings = if (action == BackupAction.BACKUP_OWN) {
            listOf(DriveBackupManager.DRIVE_FILE_SCOPE)
        } else {
            listOf(DriveBackupManager.DRIVE_APPDATA_SCOPE)
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopeStrings.map { Scope(it) })
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val sender = authResult.pendingIntent?.intentSender
                    if (sender != null) {
                        consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        working = null
                        feedback = "Drive consent could not be requested."
                        pendingAction = null
                    }
                } else {
                    val token = authResult.accessToken
                    if (token == null) {
                        working = null
                        feedback = "Drive returned no access token."
                        pendingAction = null
                    } else {
                        runDriveAction(scope, backup, token, action,
                            onWorking = { working = it },
                            onFeedback = { feedback = it })
                        pendingAction = null
                    }
                }
            }
            .addOnFailureListener {
                working = null
                feedback = it.localizedMessage ?: "Drive authorization failed."
                pendingAction = null
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(Modifier.size(width = 32.dp, height = 4.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // --- Account section ---
            val current = account
            if (current != null) {
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
                        onClick = {
                            scope.launch {
                                working = "Signing out…"
                                auth.signOut()
                                account = null
                                working = null
                                onSignOut()
                            }
                        },
                        enabled = working == null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (working == "Signing out…") {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(if (working == "Signing out…") "Signing out…" else "Sign out")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Premium section ---
            Section("Pro Version") {
                if (isPremium) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Pro version unlocked. Thank you for your support!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Text(
                        "Upgrade to Pro to remove the ${BillingManager.FREE_ENTRY_LIMIT} entry limit and support future development.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { billing.purchasePremium() },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Upgrade to Pro (One-time)")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Cloud backup ---
            Section("Cloud backup") {
                Text(
                    "Backs up the encrypted vault file to your private Google Drive AppData.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val last = backup.lastBackupAtMillis
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (last == 0L) "No backup yet."
                        else "Last backup: ${DateFormat.getDateTimeInstance().format(Date(last))}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { runDriveFlow(BackupAction.BACKUP) },
                        enabled = current != null && working == null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back up")
                    }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = current != null && working == null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore")
                    }
                }
                if (isPremium) {
                    OutlinedButton(
                        onClick = { runDriveFlow(BackupAction.BACKUP_OWN) },
                        enabled = current != null && working == null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back up to My Drive (Pro)")
                    }
                }
                if (working != null && working != "Signing out…") {
                    Text(working!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                feedback?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Danger zone ---
            Section("Danger zone") {
                Text(
                    "Permanently deletes every entry and encryption keys from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { confirmDelete = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Delete all vault data")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This wipes every entry, encryption keys, and account state from this device. " +
                            "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.nukeAllData()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Drive?") },
            text = {
                Text(
                    "Replaces the current local vault with the latest Drive backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    runDriveFlow(BackupAction.RESTORE)
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }
}

private enum class BackupAction { BACKUP, RESTORE, BACKUP_OWN }

private fun runDriveAction(
    scope: kotlinx.coroutines.CoroutineScope,
    backup: DriveBackupManager,
    accessToken: String,
    action: BackupAction,
    onWorking: (String?) -> Unit,
    onFeedback: (String?) -> Unit,
) {
    scope.launch {
        try {
            when (action) {
                BackupAction.BACKUP -> {
                    backup.backup(accessToken)
                    onFeedback("Backup complete.")
                }
                BackupAction.BACKUP_OWN -> {
                    backup.backupToOwnDrive(accessToken)
                    onFeedback("Backup to My Drive complete.")
                }
                BackupAction.RESTORE -> {
                    val ok = backup.restore(accessToken)
                    onFeedback(
                        if (ok) "Restore complete. Restarting in 1 second…"
                        else "No backup found in Drive."
                    )
                    if (ok) {
                        kotlinx.coroutines.delay(1000)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
        } catch (t: Throwable) {
            onFeedback(t.localizedMessage ?: "Drive operation failed.")
        } finally {
            onWorking(null)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}
