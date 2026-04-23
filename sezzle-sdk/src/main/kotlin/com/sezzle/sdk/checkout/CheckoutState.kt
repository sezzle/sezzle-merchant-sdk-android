package com.sezzle.sdk.checkout

import android.app.Activity
import com.sezzle.sdk.SezzleCheckoutListener

/**
 * Internal singleton that holds checkout state between the launching Activity,
 * the Chrome Custom Tab, and SezzleRedirectActivity.
 *
 * This is necessary because SezzleRedirectActivity is launched by the system
 * (via intent-filter), not by our code — so we can't pass the listener directly.
 */
internal object CheckoutState {
    var listener: SezzleCheckoutListener? = null
    var orderUUID: String? = null
    var checkoutInProgress: Boolean = false
    /** The Activity class that started checkout — used to navigate back and clear the Custom Tab from the back stack. */
    var launchingActivityClass: Class<out Activity>? = null

    fun clear() {
        listener = null
        orderUUID = null
        checkoutInProgress = false
        launchingActivityClass = null
    }
}
