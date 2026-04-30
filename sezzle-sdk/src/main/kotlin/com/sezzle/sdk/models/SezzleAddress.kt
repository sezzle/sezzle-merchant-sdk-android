package com.sezzle.sdk.models

/** A postal address for billing or shipping. */
data class SezzleAddress(
    val name: String? = null,
    val street: String? = null,
    val street2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val phone: String? = null
)
