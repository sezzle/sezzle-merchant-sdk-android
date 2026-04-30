package com.sezzle.sdk.models

/** Order details for the checkout session. */
data class SezzleOrder(
    val referenceId: String,
    val description: String? = null,
    val amount: SezzleAmount,
    val intent: SezzleIntent = SezzleIntent.AUTH,
    val items: List<SezzleItem>? = null,
    val discounts: List<SezzleDiscount>? = null,
    val taxAmount: SezzleAmount? = null,
    val shippingAmount: SezzleAmount? = null,
    val metadata: Map<String, String>? = null,
    val requiresShippingInfo: Boolean? = null,
    val locale: SezzleLocale? = null,
    val checkoutFinancingOptions: List<SezzleFinancingOption>? = null
)
