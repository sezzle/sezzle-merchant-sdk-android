package com.sezzle.sdk.checkout

import com.sezzle.sdk.SezzleCheckoutListener

/**
 * Internal singleton for the Custom Tabs fallback path.
 * Holds checkout state between the launching Activity and SezzleRedirectActivity.
 *
 * Only used when AuthTab is not supported (Chrome < 137).
 * When AuthTab is available, the result comes via ActivityResultLauncher instead.
 */
internal object CheckoutState {
    var listener: SezzleCheckoutListener? = null
    var orderUUID: String? = null
    var launchingActivityClassName: String? = null

    fun clear() {
        listener = null
        orderUUID = null
        launchingActivityClassName = null
    }
}
