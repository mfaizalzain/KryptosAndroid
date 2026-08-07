package com.kryptos.vault.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.backup.DriveBackupManager
import com.kryptos.vault.security.AuthManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.res.stringResource
import com.fmz.kryptos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as KryptosApp
    val auth = remember { app.authManager }
    val backup = remember { app.backupManager }
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf<AuthManager.Account?>(auth.currentAccount) }
    val remindersEnabled by app.billingManager.remindersEnabled.collectAsState()
    val adsRemoved by app.billingManager.adsRemoved.collectAsState()
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
            AccountHeader(
                account = account,
                working = working,
                onSignOut = {
                    scope.launch {
                        working = "Signing out…"
                        auth.signOut()
                        account = null
                        working = null
                        onSignOut()
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Support section ---
            Section("Support") {
                SupportSection(
                    adsRemoved = adsRemoved,
                    onRemoveAds = {
                        val activity = ctx.findActivity()
                        if (activity == null) {
                            feedback = "Internal error: Activity not found."
                        } else {
                            val launched = app.billingManager.purchaseRemoveAds(activity)
                            if (!launched) {
                                feedback = "Couldn't start purchase. Try again in a moment."
                            }
                        }
                    },
                    onRestorePurchases = { app.billingManager.restorePurchases() },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Cloud backup ---
            Section("Cloud backup") {
                BackupSection(
                    account = account,
                    working = working,
                    feedback = feedback,
                    lastBackupAtMillis = backup.getLastBackupAtMillis(account?.id),
                    onRefresh = { runDriveFlow(BackupAction.REFRESH) },
                    onBackup = { runDriveFlow(BackupAction.BACKUP) },
                    onRestore = { confirmRestore = true },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- General ---
            Section("General") {
                GeneralSection(
                    remindersEnabled = remindersEnabled,
                    onToggleReminders = { app.billingManager.setRemindersEnabled(it) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- Danger zone ---
            Section("Danger zone") {
                DangerZoneSection(onDelete = { confirmDelete = true })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_vault_and_account)) },
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
                }) { Text(stringResource(R.string.delete_everything), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.restore_from_drive)) },
            text = {
                Text(
                    "Replaces the current local vault with the latest Drive backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    runDriveFlow(BackupAction.RESTORE)
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
