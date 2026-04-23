package com.sezzle.sdk.checkout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Fallback redirect handler for browsers that don't support Auth Tab (Chrome < 137).
 *
 * Catches sezzle-sdk://checkout/ redirects via intent-filter,
 * dispatches to the checkout listener, and navigates back to the
 * launching activity to clear the Custom Tab from the back stack.
 */
class SezzleRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Snapshot state BEFORE clearing — order matters
        val launchingClassName = CheckoutState.launchingActivityClassName
        val listener = CheckoutState.listener
        val orderUUID = CheckoutState.orderUUID
        val uri = intent?.data

        // Clear state immediately so the lifecycle observer doesn't fire BrowserDismissed
        CheckoutState.clear()

        // Navigate back to the launching activity first, clearing Custom Tab from back stack
        if (launchingClassName != null) {
            try {
                val backIntent = Intent(this, Class.forName(launchingClassName))
                backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(backIntent)
            } catch (_: ClassNotFoundException) { }
        }

        // Dispatch the result AFTER navigation, on the next frame.
        // This ensures ProductActivity is resumed before the listener starts ResultActivity.
        if (uri != null && listener != null && orderUUID != null) {
            Handler(Looper.getMainLooper()).post {
                CheckoutHandler.handleCallbackUri(uri, orderUUID, listener)
            }
        }

        finish()
    }
}
