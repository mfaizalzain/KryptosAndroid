package com.kryptos.vault

import android.app.Activity
import android.content.Context
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
import com.kryptos.vault.data.SecurePrefs
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
        SecurePrefs(context, "kryptos_billing_prefs")
    }

    private val _adsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false))
    val adsRemoved: StateFlow<Boolean> = _adsRemoved

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS_ENABLED, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var productDetails: ProductDetails? = null
    private var connected = false
    private var restoringPurchases = false

    init {
        startConnection()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_REMINDERS_ENABLED, enabled)
        _remindersEnabled.value = enabled
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProductDetails()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                connected = false
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
                    if (restoringPurchases) {
                        restoringPurchases = false
                        _message.value = "Restore complete."
                    }
                } else if (purchases.any { it.products.contains(PRODUCT_ID_REMOVE_ADS) }) {
                    if (restoringPurchases) {
                        restoringPurchases = false
                        _message.value = "A purchase is pending approval."
                    }
                } else if (purchases.none { it.products.contains(PRODUCT_ID_REMOVE_ADS) }) {
                    // Successful query with no matching purchase (e.g. refunded): reconcile.
                    setAdsRemoved(false)
                    if (restoringPurchases) {
                        restoringPurchases = false
                        _message.value = "No purchases found to restore."
                    }
                }
            } else if (restoringPurchases) {
                restoringPurchases = false
                _message.value = "Could not reach Google Play. Try again."
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
        restoringPurchases = true
        _message.value = "Restoring purchases…"
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
        prefs.putBoolean(KEY_ADS_REMOVED, value)
        _adsRemoved.value = value
    }

    companion object {
        private const val KEY_ADS_REMOVED = "ads_removed"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val PRODUCT_ID_REMOVE_ADS = "kryptos_pro_upgrade"
    }
}
