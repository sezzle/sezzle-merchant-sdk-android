package com.sezzle.sdk.models

/** Sezzle API environment. Use [SANDBOX] for development and [PRODUCTION] for live transactions. */
enum class SezzleEnvironment(val gatewayUrl: String) {
    PRODUCTION("https://gateway.sezzle.com"),
    SANDBOX("https://sandbox.gateway.sezzle.com")
}
