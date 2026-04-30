package com.sezzle.sdk.models

/** A line item in the order. */
data class SezzleItem(
    val name: String,
    val sku: String? = null,
    val quantity: Int,
    val price: SezzleAmount,
    val brand: String? = null,
    val imageUrl: String? = null,
    val productUrl: String? = null,
    val globalTradeItemNumber: String? = null,
    val manufacturerPartNumber: String? = null,
    val categoryPath: String? = null
)
