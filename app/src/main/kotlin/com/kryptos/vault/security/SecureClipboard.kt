package com.kryptos.vault.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Copies sensitive text to the clipboard, marks it sensitive so the OS hides previews,
 * and clears it after [CLEAR_AFTER_MS]. Only the value we wrote is cleared — if the user
 * has copied something else in the meantime, we leave it alone.
 */
object SecureClipboard {
    private const val CLEAR_AFTER_MS = 30_000L
    private val pendingJob = AtomicReference<Job?>(null)

    fun copy(context: Context, label: String, value: String, sensitive: Boolean = isSensitiveLabel(label)) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value).apply {
            if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    // Pre-Android 13 OEMs read this older key.
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        cm.setPrimaryClip(clip)

        pendingJob.getAndSet(null)?.cancel()
        val job = CoroutineScope(Dispatchers.Main).launch {
            delay(CLEAR_AFTER_MS)
            val current = cm.primaryClip
            if (current != null && current.itemCount > 0 &&
                current.getItemAt(0).text?.toString() == value
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cm.clearPrimaryClip()
                } else {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
        pendingJob.set(job)
    }

    /**
     * Marks a clip sensitive only for true secrets — passwords, PINs, CVVs, tokens. Marking
     * everything sensitive made some keyboards (Gboard) render the clipboard preview as
     * "•••••" which the user reads as a masked paste, so we restrict it to credentials.
     */
    private fun isSensitiveLabel(label: String): Boolean {
        val n = label.lowercase()
        return listOf("password", "pin", "cvv", "secret", "token").any { it in n }
    }
}
