package com.sezzle.sdk.models

/** Customer information sent with the checkout session. */
data class SezzleCustomer(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val dob: String? = null,
    val billingAddress: SezzleAddress? = null,
    val shippingAddress: SezzleAddress? = null,
    val tokenize: Boolean? = null,
    val recurring: Boolean? = null,
    val recurringMetadata: Map<String, String>? = null
)
