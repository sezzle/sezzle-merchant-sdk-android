package com.sezzle.sdk.checkout

import android.net.Uri
import com.sezzle.sdk.SezzleCheckoutListener

/**
 * Internal singleton for the Custom Tabs system-browser path.
 * Holds checkout state between the launching Activity and SezzleRedirectActivity
 * (the redirect activity reads complete/cancel URLs and the listener from here
 * after the browser routes the callback intent back to the app).
 */
internal object CheckoutState {
    var listener: SezzleCheckoutListener? = null
    var orderUUID: String? = null
    var completeUrl: Uri? = null
    var cancelUrl: Uri? = null
    var launchingActivityClassName: String? = null

    fun clear() {
        listener = null
        orderUUID = null
        completeUrl = null
        cancelUrl = null
        launchingActivityClassName = null
    }
}
