package com.kryptos.vault

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles simulated "one-time payment" to unlock premium features.
 * In a real app, this would integrate with Google Play Billing Library.
 */
class BillingManager(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kryptos_billing_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium

    /** 
     * Simulated purchase. In production, this would launch the Play Store 
     * billing flow and verify the purchase token on a server.
     */
    fun purchasePremium() {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
        _isPremium.value = true
    }

    companion object {
        private const val KEY_IS_PREMIUM = "is_premium"
        const val FREE_ENTRY_LIMIT = 10
    }
}
