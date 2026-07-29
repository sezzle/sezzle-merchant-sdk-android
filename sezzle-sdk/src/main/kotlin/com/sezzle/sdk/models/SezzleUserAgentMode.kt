package com.sezzle.sdk.models

/**
 * How the merchant surface hosts the Sezzle checkout page.
 *
 * Sent as `order.checkout_mode` on `POST /v2/session` and recorded on the
 * checkout as `sezzle_user_agent_mode`. Checkout uses it to decide how to
 * signal completion back to whatever embedded it.
 *
 * Distinct from `SezzleCheckoutMode`, which controls how *this SDK* presents
 * checkout (system browser vs. in-app WebView).
 *
 * Defaults to [REDIRECT] on [SezzleOrder], which is correct for both of this
 * SDK's presentation modes — completion is detected from the redirect to your
 * complete/cancel URL. Override it only if you have a reason to.
 */
enum class SezzleUserAgentMode(val value: String) {
    /** Checkout signals completion by redirecting to the complete/cancel URL. */
    REDIRECT("redirect"),
    /** Checkout is embedded in an iframe and posts a message to the parent frame. */
    IFRAME("iframe"),
    /** Checkout runs in a popup window and posts a message to its opener. */
    POPUP("popup")
}
