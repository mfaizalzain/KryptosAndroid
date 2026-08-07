package com.kryptos.vault.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.backup.DriveBackupManager
import com.kryptos.vault.backup.BackupPassphraseIncorrectException
import com.kryptos.vault.backup.BackupPassphraseRequiredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class BackupAction { REFRESH, BACKUP, RESTORE, BACKUP_OWN }

/** Runs a Drive backup action off the calling scope. */
internal fun runDriveAction(
    scope: CoroutineScope,
    backup: DriveBackupManager,
    accessToken: String,
    action: BackupAction,
    userId: String?,
    app: KryptosApp,
    onWorking: (String?) -> Unit,
    onFeedback: (String?) -> Unit,
    providedPassphrase: String? = null,
    onPassphraseRequired: () -> Unit = {},
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
                    var needsPassphrase = false
                    val ok = try {
                        backup.restore(accessToken, userId, providedPassphrase)
                    } catch (e: BackupPassphraseRequiredException) {
                        needsPassphrase = true
                        onFeedback(e.localizedMessage ?: "Backup passphrase required.")
                        onPassphraseRequired()
                        false
                    } catch (e: BackupPassphraseIncorrectException) {
                        onFeedback(e.localizedMessage ?: "The backup passphrase is incorrect.")
                        false
                    } catch (e: Exception) {
                        onFeedback("Restore failed: ${e.localizedMessage}")
                        false
                    }
                    if (ok) {
                        onFeedback("Restore successful. Restarting app…")
                        kotlinx.coroutines.delay(1500)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else if (!needsPassphrase) {
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

/** Walks the context wrapper chain to find the hosting [Activity]. */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
