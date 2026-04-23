package com.sezzle.sdk.models

/**
 * Customer information for a Sezzle checkout.
 *
 * @property email Customer's email address (required).
 * @property firstName Customer's first name.
 * @property lastName Customer's last name.
 * @property phone Customer's phone number.
 */
data class SezzleCustomer(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null
)
