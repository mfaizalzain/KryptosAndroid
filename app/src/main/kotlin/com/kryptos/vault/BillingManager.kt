package com.kryptos.vault

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-time IAP that removes ads. Reminders toggle preserved.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

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

    private val _adsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false))
    val adsRemoved: StateFlow<Boolean> = _adsRemoved

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS_ENABLED, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled

    private var productDetails: ProductDetails? = null
    private var connected = false

    init {
        startConnection()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
        _remindersEnabled.value = enabled
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                android.util.Log.d("BillingMgr", "Connected: code=${result.responseCode}, msg=${result.debugMessage}")
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProductDetails()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                connected = false
                android.util.Log.w("BillingMgr", "Billing service disconnected")
            }
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, queryProductDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = queryProductDetailsResult.productDetailsList.firstOrNull()
            } else {
                android.util.Log.w("BillingMgr", "queryProductDetails failed: ${result.debugMessage}")
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.firstOrNull { p ->
                    p.products.contains(PRODUCT_ID_REMOVE_ADS) &&
                        p.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned != null) {
                    if (!owned.isAcknowledged) acknowledge(owned) else setAdsRemoved(true)
                }
            }
        }
    }

    /** Returns true if the billing flow was launched. */
    fun purchaseRemoveAds(activity: Activity): Boolean {
        if (_adsRemoved.value) return true
        val details = productDetails ?: run {
            if (connected) queryProductDetails() else startConnection()
            return false
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restorePurchases() {
        if (connected) queryPurchases() else startConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            for (purchase in purchases) {
                if (purchase.products.contains(PRODUCT_ID_REMOVE_ADS) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                ) {
                    if (!purchase.isAcknowledged) acknowledge(purchase) else setAdsRemoved(true)
                }
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { ack ->
            if (ack.responseCode == BillingClient.BillingResponseCode.OK) {
                setAdsRemoved(true)
            }
        }
    }

    private fun setAdsRemoved(value: Boolean) {
        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
        _adsRemoved.value = value
    }

    companion object {
        private const val KEY_ADS_REMOVED = "ads_removed"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val PRODUCT_ID_REMOVE_ADS = "kryptos_pro_upgrade"
    }
}
