package com.kryptos.vault

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles Google Play Billing for one-time Pro upgrade.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

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

    private val _isPremium = MutableStateFlow(true)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS_ENABLED, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled

    init {
        // Connection disabled as Pro billing is replaced by Buy Me a Coffee
        // startConnection()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
        _remindersEnabled.value = enabled
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                android.util.Log.d("BillingMgr", "Connected: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                android.util.Log.w("BillingMgr", "Billing service disconnected")
            }
        })
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
            
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            android.util.Log.d("BillingMgr", "queryPurchases: code=${result.responseCode}, msg=${result.debugMessage}, count=${purchases.size}")
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any { purchase ->
                    purchase.products.contains(PRODUCT_ID_PRO) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                android.util.Log.d("BillingMgr", "Has pro purchase: $hasPro")
                // Keep premium true regardless of Google Play billing status
                setPremium(true)
            }
        }
    }

    fun purchasePremium(activity: Activity) {
        // No-op as premium is unlocked
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        android.util.Log.d("BillingMgr", "onPurchasesUpdated: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}, purchases=${purchases?.size}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            for (purchase in purchases) {
                android.util.Log.d("BillingMgr", "Purchase: ${purchase.products}, state=${purchase.purchaseState}, ack=${purchase.isAcknowledged}")
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            android.util.Log.e("BillingMgr", "Purchase update error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    setPremium(true)
                }
            }
        } else {
            setPremium(true)
        }
    }

    fun restorePurchases() {
        // No-op as premium is unlocked
    }

    private fun setPremium(value: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
        _isPremium.value = true
    }

    companion object {
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val FREE_ENTRY_LIMIT = 10
        // The real Product ID from Google Play Console
        const val PRODUCT_ID_PRO = "kryptos_pro_upgrade"
    }
}
