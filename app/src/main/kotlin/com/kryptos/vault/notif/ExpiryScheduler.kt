package com.kryptos.vault.notif

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Schedules WorkManager reminders ahead of an entry's expiry. Re-run on every upsert; existing
 * reminders for the entry are cancelled by tag before new ones are enqueued, so updates supersede.
 */
object ExpiryScheduler {

    /** Per-template warning periods. Order matters for unique notification ids per entry. */
    private fun thresholdsFor(template: Template): List<Pair<Period, String>> =when (template) {
        Template.PASSPORT -> listOf(
            Period.ofMonths(6) to "Passport expires in 6 months",
            Period.ofMonths(1) to "Passport expires in 1 month",
        )
        Template.DRIVERS_LICENSE -> listOf(
            Period.ofWeeks(1) to "Driver's license expires in 1 week",
        )
        Template.PAYMENT_CARD -> listOf(
            Period.ofMonths(1) to "Card expires in 1 month",
        )
        Template.ID_CARD,
        Template.BIRTH_CERTIFICATE,
        Template.BANK_ACCOUNT,
        Template.TAX_NUMBER,
        Template.API_KEY,
        Template.NOTE -> emptyList()
    }

    fun scheduleFor(context: Context, entry: VaultEntry) {
        val wm = WorkManager.getInstance(context)
        val tag = tagFor(entry.id)
        wm.cancelAllWorkByTag(tag)

        val thresholds = thresholdsFor(entry.template)
        if (thresholds.isEmpty()) return

        val expiry = extractExpiry(entry) ?: return
        val today = LocalDate.now()
        if (!expiry.isAfter(today)) return  // already expired; nothing to remind about

        val title = entry.title.ifBlank { "Vault entry" }

        thresholds.forEachIndexed { index, (period, body) ->
            val trigger = expiry.minus(period)
            if (!trigger.isAfter(today)) return@forEachIndexed  // threshold already passed

            val delayMs = trigger.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - System.currentTimeMillis()
            if (delayMs <= 0) return@forEachIndexed

            val work = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ExpiryReminderWorker.KEY_TITLE to title,
                        ExpiryReminderWorker.KEY_BODY to "$body (${expiry})",
                        ExpiryReminderWorker.KEY_ENTRY_ID to entry.id,
                        ExpiryReminderWorker.KEY_THRESHOLD_INDEX to index,
                    )
                )
                .addTag(tag)
                .addTag(GLOBAL_TAG)
                .build()
            wm.enqueue(work)
        }
    }

    fun cancelFor(context: Context, entryId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(entryId))
    }

    private fun tagFor(entryId: Long) = "kryptos-expiry-$entryId"

    private const val GLOBAL_TAG = "kryptos-expiry"

    /** Extracts the "Expiry" field, handling both YYYY-MM-DD and MM/YY (credit card) formats. */
    private fun extractExpiry(entry: VaultEntry): LocalDate? {
        val fields = FieldsCodec.decode(entry.fieldsJson)
        val raw = fields.firstOrNull { it.first.equals("Expiry", ignoreCase = true) }
            ?.second?.trim()
            ?: return null
        if (raw.isBlank()) return null

        // Try ISO yyyy-MM-dd first.
        runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
        // MM/YY → last day of that month.
        Regex("""^(\d{1,2})\s*[/\-]\s*(\d{2})$""").matchEntire(raw)?.let { m ->
            val mm = m.groupValues[1].toIntOrNull() ?: return null
            val yy = m.groupValues[2].toIntOrNull() ?: return null
            val year = 2000 + yy
            return LocalDate.of(year, mm, 1).withDayOfMonth(LocalDate.of(year, mm, 1).lengthOfMonth())
        }
        // dd/MM/yyyy or dd-MM-yyyy.
        Regex("""^(\d{1,2})[\-/.](\d{1,2})[\-/.](\d{4})$""").matchEntire(raw)?.let { m ->
            return runCatching {
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }.getOrNull()
        }
        // yyyy/MM/dd.
        Regex("""^(\d{4})[\-/.](\d{1,2})[\-/.](\d{1,2})$""").matchEntire(raw)?.let { m ->
            return runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
        }
        // Best effort: let DateTimeFormatter try several patterns.
        for (pattern in listOf("yyyy-MM-dd", "dd MMM yyyy", "MMM dd yyyy", "MMM yyyy")) {
            runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern)) }
                .getOrNull()?.let { return it }
        }
        return null
    }
}
