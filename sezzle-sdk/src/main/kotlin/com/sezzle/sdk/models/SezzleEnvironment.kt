package com.sezzle.sdk.models

/**
 * Sezzle API environment. Use [SANDBOX] for development and [PRODUCTION] for live transactions.
 *
 * - [gatewayUrl] hosts checkout / session endpoints used by the SDK-creates-session flow.
 * - [apiUrl] hosts the sezzle-pay user-facing API. `/v4/users/logout` (called by
 *   `clearWebViewData`) lives here, not on the gateway.
 */
enum class SezzleEnvironment(val gatewayUrl: String, val apiUrl: String) {
    PRODUCTION("https://gateway.sezzle.com", "https://api.sezzle.com"),
    SANDBOX("https://sandbox.gateway.sezzle.com", "https://sandbox.api.sezzle.com")
}
