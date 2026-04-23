package com.sezzle.sdk.models

/** Errors that can occur during Sezzle SDK operations. */
sealed class SezzleError(override val message: String) : Exception(message) {

    /** The SDK has not been configured. Call [com.sezzle.sdk.SezzleSDK.configure] first. */
    data object NotConfigured : SezzleError(
        "Sezzle SDK is not configured. Call SezzleSDK.configure() before starting checkout."
    )

    /** A network error occurred (no connectivity, timeout, DNS failure, etc.). */
    class NetworkError(val underlying: Throwable) : SezzleError(
        "Network error: ${underlying.localizedMessage ?: underlying.message ?: "Unknown"}"
    )

    /** The Sezzle API returned a non-success response. */
    class ApiError(val statusCode: Int, val apiMessage: String) : SezzleError(
        "Sezzle API error ($statusCode): $apiMessage"
    )

    /** The user dismissed the checkout browser before completing. */
    data object BrowserDismissed : SezzleError(
        "Checkout was dismissed before completion."
    )

    /** The API response could not be parsed. */
    data object InvalidResponse : SezzleError(
        "Could not parse the response from Sezzle."
    )
}
