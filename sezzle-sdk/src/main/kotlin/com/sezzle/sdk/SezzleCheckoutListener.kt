package com.sezzle.sdk

import com.sezzle.sdk.models.SezzleError

/**
 * Receives callbacks from the Sezzle checkout flow.
 *
 * All methods are called on the **main thread**.
 *
 * ```kotlin
 * class MyActivity : AppCompatActivity(), SezzleCheckoutListener {
 *     override fun onCheckoutComplete(result: SezzleCheckoutResult) {
 *         result.orderUUID?.let { /* SDK-creates-session flow — capture via your backend */ }
 *         result.callbackURL?.let { /* Server-driven flow — read query params */ }
 *     }
 *     override fun onCheckoutCancel() {}
 *     override fun onCheckoutError(error: SezzleError) {}
 * }
 * ```
 */
interface SezzleCheckoutListener {
    /**
     * Called when the customer successfully completes checkout.
     *
     * See [SezzleCheckoutResult] for which field is populated by which flow.
     */
    fun onCheckoutComplete(result: SezzleCheckoutResult)

    /** Called when the customer cancels checkout from within the Sezzle checkout page. */
    fun onCheckoutCancel()

    /** Called when an error occurs during the checkout flow. */
    fun onCheckoutError(error: SezzleError)
}
