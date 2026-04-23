package com.sezzle.sdk

import com.sezzle.sdk.models.SezzleError

/**
 * Receives callbacks from the Sezzle checkout flow.
 *
 * All methods are called on the **main thread**.
 */
interface SezzleCheckoutListener {
    /**
     * Called when the customer successfully completes checkout.
     *
     * Send the [orderUUID] to your backend to capture the payment via
     * `POST /v2/order/{uuid}/capture`.
     */
    fun onCheckoutComplete(orderUUID: String)

    /** Called when the customer cancels checkout from within the Sezzle checkout page. */
    fun onCheckoutCancel()

    /** Called when an error occurs during the checkout flow. */
    fun onCheckoutError(error: SezzleError)
}
