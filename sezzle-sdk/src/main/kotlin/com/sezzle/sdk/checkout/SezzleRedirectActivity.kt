package com.sezzle.sdk.checkout

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Transparent activity that catches sezzle-sdk://checkout/ redirects.
 *
 * Declared in the SDK's AndroidManifest.xml with an intent-filter for the
 * sezzle-sdk scheme. Auto-merges into the merchant's manifest.
 *
 * This activity parses the redirect URL, dispatches to the checkout listener,
 * navigates back to the launching activity (clearing the Custom Tab from
 * the back stack), and finishes itself.
 */
class SezzleRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the launching activity BEFORE handleCallbackUrl clears the state
        val activityClass = CheckoutState.launchingActivityClass

        val uri = intent?.data
        if (uri != null) {
            CheckoutHandler.handleCallbackUrl(uri)
        }

        // Navigate back to the activity that started checkout, clearing the
        // Chrome Custom Tab from the back stack. Without this, pressing back
        // from the result screen would reopen the Custom Tab.
        if (activityClass != null) {
            val backIntent = Intent(this, activityClass)
            backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(backIntent)
        }

        finish()
    }
}
