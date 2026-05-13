package com.sezzle.sdk.checkout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Redirect handler for the Chrome Custom Tabs system-browser path.
 *
 * Catches the SDK's default `sezzle-sdk://checkout/` redirects via intent-filter,
 * dispatches to the checkout listener, and navigates back to the launching activity
 * to clear the Custom Tab from the back stack.
 *
 * **Note:** the SDK ships an intent-filter for `sezzle-sdk://checkout` only.
 * Merchants using the server-driven entrypoint with a custom scheme must register
 * an intent-filter for their own scheme in their `AndroidManifest.xml`, pointing
 * at this activity (or an activity that forwards to [CheckoutHandler.handleCallbackUri]).
 */
class SezzleRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Defense-in-depth: FLAG_SECURE on the redirect activity. The activity is
        // Theme.Translucent.NoTitleBar and renders nothing — practically a no-op,
        // but it removes the activity from posture-scanner findings for SDKs whose
        // task affinity could in theory be inspected mid-redirect.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Snapshot state BEFORE clearing — order matters
        val launchingClassName = CheckoutState.launchingActivityClassName
        val listener = CheckoutState.listener
        val orderUUID = CheckoutState.orderUUID
        val completeUrl = CheckoutState.completeUrl ?: CheckoutHandler.DEFAULT_COMPLETE_URL
        val cancelUrl = CheckoutState.cancelUrl ?: CheckoutHandler.DEFAULT_CANCEL_URL
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
        // This ensures the launching Activity is resumed before the listener runs.
        if (uri != null && listener != null) {
            Handler(Looper.getMainLooper()).post {
                CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, orderUUID, listener)
            }
        }

        finish()
    }
}
