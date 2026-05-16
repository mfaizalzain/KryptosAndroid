package com.kryptos.vault.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
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
    val remindersEnabled by billing.remindersEnabled.collectAsState()
    var working by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<BackupAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(account?.id) {
        val id = account?.id ?: return@LaunchedEffect
        if (backup.getLastBackupAtMillis(id) == 0L) {
            android.util.Log.d("AccountSheet", "Checking Drive for existing backup for user: $id")
            val activity = ctx.findActivity() ?: return@LaunchedEffect
            val scopes = listOf(
                DriveBackupManager.DRIVE_APPDATA_SCOPE,
                DriveBackupManager.DRIVE_FILE_SCOPE,
            )
            
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(scopes.map { Scope(it) })
                .build()
            try {
                val authResult = withTimeout(5000) {
                    Identity.getAuthorizationClient(activity).authorize(request).await()
                }
                if (!authResult.hasResolution()) {
                    authResult.accessToken?.let { token ->
                        backup.refreshLastBackupDate(token, id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AccountSheet", "Silent backup sync failed: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) { account = auth.currentAccount }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            working = null
            feedback = "Drive access was denied."
            return@rememberLauncherForActivityResult
        }
        val activity = ctx.findActivity() ?: return@rememberLauncherForActivityResult
        val client = Identity.getAuthorizationClient(activity)
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
        runDriveAction(scope, backup, token, action, account?.id, app,
            onWorking = { working = it },
            onFeedback = { feedback = it })
        pendingAction = null
    }

    fun runDriveFlow(action: BackupAction) {
        val activity = ctx.findActivity()
        if (activity == null) {
            feedback = "Internal error: Activity not found."
            return
        }

        working = "Authorizing Drive..."
        feedback = null
        pendingAction = action

        val scopeStrings = when (action) {
            BackupAction.BACKUP -> listOf(DriveBackupManager.DRIVE_APPDATA_SCOPE)
            BackupAction.BACKUP_OWN -> listOf(DriveBackupManager.DRIVE_FILE_SCOPE)
            BackupAction.REFRESH,
            BackupAction.RESTORE -> listOf(
                DriveBackupManager.DRIVE_APPDATA_SCOPE,
                DriveBackupManager.DRIVE_FILE_SCOPE,
            )
        }
        
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopeStrings.map { Scope(it) })
            .build()
            
        scope.launch {
            try {
                android.util.Log.d("DriveBackup", "Requesting Drive authorization...")
                val authResult = withTimeout(15000) {
                    Identity.getAuthorizationClient(activity).authorize(request).await()
                }
                
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
                        working = "Processing..."
                        runDriveAction(scope, backup, token, action, account?.id, app,
                            onWorking = { working = it },
                            onFeedback = { feedback = it })
                        pendingAction = null
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("DriveBackup", "Auth failed", t)
                working = null
                feedback = if (t is kotlinx.coroutines.TimeoutCancellationException) {
                    "Authorization timed out. Check internet."
                } else {
                    t.localizedMessage ?: "Drive auth failed."
                }
                pendingAction = null
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

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
                        onClick = { ctx.findActivity()?.let { billing.purchasePremium(it) } },
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
                    TextButton(
                        onClick = { billing.restorePurchases() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Already purchased? Restore Purchase")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Cloud backup ---
            Section("Cloud backup") {
                Text(
                    "Backups include your encrypted documents and the unique key needed to unlock them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val last = backup.getLastBackupAtMillis(account?.id)
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
                                if (last == 0L) "No local backup date found."
                                else "Last known backup: ${DateFormat.getDateTimeInstance().format(Date(last))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (last == 0L) {
                                Text(
                                    "Check your Google Drive for existing data.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (last == 0L && current != null) {
                            TextButton(
                                onClick = { runDriveFlow(BackupAction.REFRESH) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Check Drive", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
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

            // --- Notifications ---
            Section("General") {
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
                        onCheckedChange = { billing.setRemindersEnabled(it) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Danger zone ---
            Section("Danger zone") {
                Text(
                    "Permanently deletes every entry, encryption keys, and sign you out of your account on this device.",
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
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Delete vault and account")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete vault and account?") },
            text = {
                Text(
                    "This will permanently wipe every entry, encryption keys, and sign you out of your account on this device. " +
                            "Your local data will be completely erased. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.nukeAllData()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("Delete Everything", color = MaterialTheme.colorScheme.error) }
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

private enum class BackupAction { REFRESH, BACKUP, RESTORE, BACKUP_OWN }

private fun runDriveAction(
    scope: kotlinx.coroutines.CoroutineScope,
    backup: DriveBackupManager,
    accessToken: String,
    action: BackupAction,
    userId: String?,
    app: KryptosApp,
    onWorking: (String?) -> Unit,
    onFeedback: (String?) -> Unit,
) {
    scope.launch {
        try {
            when (action) {
                BackupAction.REFRESH -> {
                    backup.refreshLastBackupDate(accessToken, userId)
                    onFeedback(null)
                }
                BackupAction.BACKUP -> {
                    backup.backup(accessToken, userId)
                    onFeedback("Backup complete.")
                }
                BackupAction.BACKUP_OWN -> {
                    backup.backupToOwnDrive(accessToken, userId)
                    onFeedback("Backup to My Drive complete.")
                }
                BackupAction.RESTORE -> {
                    onWorking("Restoring vault...")
                    app.closeDatabase()
                    val ok = try {
                        backup.restore(accessToken, userId)
                    } catch (e: Exception) {
                        android.util.Log.e("AccountSheet", "Restore failed", e)
                        onFeedback("Restore failed: ${e.localizedMessage}")
                        false
                    }
                    if (ok) {
                        onFeedback("Restore successful. Restarting app…")
                        kotlinx.coroutines.delay(1500)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        onFeedback("No backup found in Drive.")
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}
