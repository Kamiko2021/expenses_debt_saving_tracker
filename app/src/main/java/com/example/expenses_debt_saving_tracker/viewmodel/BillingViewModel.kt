package com.example.expenses_debt_saving_tracker.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.android.billingclient.api.ProductDetails
import com.example.expenses_debt_saving_tracker.util.BillingManager
import kotlinx.coroutines.flow.StateFlow

class BillingViewModel(application: Application) : AndroidViewModel(application) {
    private val billingManager = BillingManager(application)
    
    val isSubscribed: StateFlow<Boolean> = billingManager.isSubscribed
    val products: StateFlow<List<ProductDetails>> = billingManager.products

    fun buySubscription(activity: Activity, productDetails: ProductDetails) {
        billingManager.launchPurchaseFlow(activity, productDetails)
    }

    fun refreshStatus() {
        billingManager.checkSubscriptionStatus()
    }
}
