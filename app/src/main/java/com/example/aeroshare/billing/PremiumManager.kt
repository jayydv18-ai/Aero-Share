package com.example.aeroshare.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.example.aeroshare.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "PremiumManager"
        const val PRODUCT_ID_PRO = "aeroshare_remove_ads_pro"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proProductDetails = MutableStateFlow<ProductDetails?>(null)
    val proProductDetails: StateFlow<ProductDetails?> = _proProductDetails.asStateFlow()

    private val _purchaseStatusMessage = MutableStateFlow<String?>(null)
    val purchaseStatusMessage: StateFlow<String?> = _purchaseStatusMessage.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun initialize() {
        scope.launch {
            settingsRepository.isProPurchasedFlow.collect { savedPro ->
                _isPro.value = savedPro
            }
        }
        startBillingConnection()
    }

    private fun startBillingConnection() {
        try {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing service connected successfully")
                        queryProducts()
                        queryExistingPurchases()
                    } else {
                        Log.w(TAG, "Billing setup finished with code: ${billingResult.responseCode}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing service disconnected")
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Exception starting billing connection", e)
        }
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val proProduct = productDetailsList.firstOrNull {
                    it.productId == PRODUCT_ID_PRO
                }
                _proProductDetails.value = proProduct
                Log.d(TAG, "Found product details: ${proProduct?.name} - ${proProduct?.oneTimePurchaseOfferDetails?.formattedPrice}")
            } else {
                Log.w(TAG, "Failed querying product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActivePro = false
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_ID_PRO) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        hasActivePro = true
                        acknowledgePurchaseIfNeeded(purchase)
                    }
                }
                updateProStatus(hasActivePro)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = _proProductDetails.value
        if (details == null) {
            _purchaseStatusMessage.value = "Product details not available yet. Please try again."
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseStatusMessage.value = "Purchase cancelled."
        } else {
            _purchaseStatusMessage.value = "Purchase failed: ${billingResult.debugMessage}"
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.products.contains(PRODUCT_ID_PRO)) {
                acknowledgePurchaseIfNeeded(purchase)
                updateProStatus(true)
                _purchaseStatusMessage.value = "Congratulations! Pro unlocked and ads removed."
            }
        }
    }

    private fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                Log.d(TAG, "Acknowledge result: ${result.responseCode}")
            }
        }
    }

    private fun updateProStatus(isPro: Boolean) {
        _isPro.value = isPro
        scope.launch {
            settingsRepository.setProPurchased(isPro)
        }
    }

    fun clearStatusMessage() {
        _purchaseStatusMessage.value = null
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
