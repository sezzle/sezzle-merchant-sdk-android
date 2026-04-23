package com.sezzle.sdk.models

/**
 * Order details for a Sezzle checkout.
 *
 * @property referenceId Your internal order/reference ID.
 * @property description Order description. Defaults to "Mobile SDK Order" if null.
 * @property amount Total order amount.
 * @property intent Checkout intent. Defaults to [SezzleIntent.AUTH].
 * @property items Optional line items.
 */
data class SezzleOrder(
    val referenceId: String,
    val description: String? = null,
    val amount: SezzleAmount,
    val intent: SezzleIntent = SezzleIntent.AUTH,
    val items: List<SezzleItem>? = null
)
