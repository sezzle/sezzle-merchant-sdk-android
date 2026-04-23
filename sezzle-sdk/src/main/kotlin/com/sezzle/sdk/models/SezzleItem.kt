package com.sezzle.sdk.models

/**
 * A line item in a Sezzle order.
 *
 * @property name Item name.
 * @property sku Stock keeping unit identifier.
 * @property quantity Number of items.
 * @property price Price per item.
 */
data class SezzleItem(
    val name: String,
    val sku: String? = null,
    val quantity: Int,
    val price: SezzleAmount
)
