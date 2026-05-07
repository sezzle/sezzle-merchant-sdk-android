package com.sezzle.sdk

import android.net.Uri

/**
 * The result of a successful Sezzle checkout, delivered via
 * [SezzleCheckoutListener.onCheckoutComplete].
 *
 * Two flows produce different fields:
 *
 * **SDK-creates-session flow** ([SezzleSDK.startCheckout] with a [com.sezzle.sdk.models.SezzleCheckout]):
 * [orderUUID] is populated; [callbackURL] is `null`.
 *
 * **Server-driven flow** ([SezzleSDK.startCheckout] with a `checkoutUrl`):
 * [callbackURL] is populated with the full URL the user landed on (so you can
 * read query params you encoded in your `complete_url`); [orderUUID] is `null`
 * because your backend already has it from the session-creation response.
 */
data class SezzleCheckoutResult(
    /**
     * The Sezzle order UUID, populated only by the SDK-creates-session flow.
     * Send this to your backend to capture the payment via `POST /v2/order/{uuid}/capture`.
     */
    val orderUUID: String? = null,

    /**
     * The full callback URL the user landed on, populated only by the server-driven flow.
     * Read query parameters here to recover any state you encoded in your `complete_url`.
     */
    val callbackURL: Uri? = null
)
