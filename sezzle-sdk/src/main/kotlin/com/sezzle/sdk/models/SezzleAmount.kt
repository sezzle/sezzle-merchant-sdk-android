package com.sezzle.sdk.models

/**
 * A monetary amount.
 *
 * @property amountInCents The amount in cents (e.g., 4999 = $49.99).
 * @property currency ISO 4217 currency code (e.g., "USD", "CAD").
 */
data class SezzleAmount(
    val amountInCents: Int,
    val currency: String = "USD"
)
