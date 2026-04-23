package com.sezzle.sdk.checkout

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Fallback redirect handler for browsers that don't support Auth Tab (Chrome < 137).
 *
 * Catches sezzle-sdk://checkout/ redirects via intent-filter,
 * dispatches to the checkout listener, and navigates back to the
 * launching activity to clear the Custom Tab from the back stack.
 *
 * When Auth Tab IS supported, this activity is never used — the result
 * comes directly via ActivityResultLauncher.
 */
class SezzleRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read launching activity BEFORE handleCallbackUrl clears the state
        val launchingClassName = CheckoutState.launchingActivityClassName

        val uri = intent?.data
        if (uri != null) {
            val listener = CheckoutState.listener
            val orderUUID = CheckoutState.orderUUID
            if (listener != null && orderUUID != null) {
                CheckoutState.clear()
                CheckoutHandler.handleCallbackUri(uri, orderUUID, listener)
            }
        }

        // Navigate back to the launching activity, clearing Custom Tab from back stack
        if (launchingClassName != null) {
            try {
                val backIntent = Intent(this, Class.forName(launchingClassName))
                backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(backIntent)
            } catch (_: ClassNotFoundException) { }
        }

        finish()
    }
}
