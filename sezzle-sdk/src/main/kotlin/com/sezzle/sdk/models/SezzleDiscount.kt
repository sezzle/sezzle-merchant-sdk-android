package com.sezzle.sdk.models

/** A discount applied to the order. */
data class SezzleDiscount(
    val name: String,
    val amount: SezzleAmount
)
