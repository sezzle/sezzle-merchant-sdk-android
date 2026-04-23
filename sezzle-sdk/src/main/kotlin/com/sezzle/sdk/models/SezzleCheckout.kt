package com.sezzle.sdk.models

/**
 * Groups customer and order data for a Sezzle checkout.
 *
 * @property customer The customer placing the order.
 * @property order The order details.
 */
data class SezzleCheckout(
    val customer: SezzleCustomer,
    val order: SezzleOrder
)
