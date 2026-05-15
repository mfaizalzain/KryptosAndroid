package com.kryptos.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.app.Activity
import android.content.Context
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.BillingManager
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.data.VaultRepository
import com.kryptos.vault.notif.ExpiryScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repo: VaultRepository,
    private val billingManager: BillingManager,
    private val appContext: Context,
) : ViewModel() {

    val entries: StateFlow<List<VaultEntry>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPremium: StateFlow<Boolean> = billingManager.isPremium

    fun purchasePremium(activity: Activity) = billingManager.purchasePremium(activity)

    suspend fun get(id: Long) = repo.get(id)

    fun delete(entry: VaultEntry) = viewModelScope.launch {
        ExpiryScheduler.cancelFor(appContext, entry.id)
        repo.delete(entry)
    }

    suspend fun upsert(entry: VaultEntry): Long {
        val id = repo.upsert(entry)
        ExpiryScheduler.scheduleFor(appContext, entry.copy(id = id))
        return id
    }

    /** Returns an existing entry that seems to be a duplicate of the given one. */
    fun findDuplicate(entry: VaultEntry): VaultEntry? {
        val currentEntries = entries.value
        val title = entry.title.trim().lowercase()
        val fields = FieldsCodec.decode(entry.fieldsJson)

        val keys = when (entry.template) {
            Template.ID_CARD -> listOf("ID number")
            Template.PASSPORT -> listOf("Passport number")
            Template.DRIVERS_LICENSE -> listOf("License number")
            Template.BIRTH_CERTIFICATE -> listOf("Registration number")
            Template.PAYMENT_CARD -> listOf("Number")
            Template.BANK_ACCOUNT -> listOf("Account number", "IBAN")
            Template.TAX_NUMBER -> listOf("Tax number")
            Template.API_KEY -> listOf("Key")
            Template.NOTE -> emptyList()
            Template.QR_CODE -> listOf("Data")
        }

        val identifiers = keys.mapNotNull { k ->
            fields.firstOrNull { it.first.equals(k, ignoreCase = true) }
                ?.second?.filter { it.isLetterOrDigit() }?.lowercase()
                ?.takeIf { it.isNotBlank() }
        }

        return currentEntries.firstOrNull { existing ->
            if (existing.id == entry.id) return@firstOrNull false

            // Match by title (if not blank)
            if (title.isNotBlank() && existing.title.trim().lowercase() == title) return@firstOrNull true

            // Match by template and any primary identifier
            if (existing.template == entry.template && identifiers.isNotEmpty()) {
                val existingFields = FieldsCodec.decode(existing.fieldsJson)
                val match = keys.any { k ->
                    val existingVal = existingFields.firstOrNull { it.first.equals(k, ignoreCase = true) }
                        ?.second?.filter { it.isLetterOrDigit() }?.lowercase()
                    existingVal != null && existingVal in identifiers
                }
                if (match) return@firstOrNull true
            }
            false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KryptosApp
                val userId = app.authManager.currentAccount?.id
                VaultViewModel(app.getRepository(userId), app.billingManager, app)
            }
        }
    }
}
