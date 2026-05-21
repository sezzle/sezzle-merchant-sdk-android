package com.sezzle.sdk.checkout

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.sezzle.sdk.SezzleSDK

/**
 * Lifecycle-safe entrypoint for presenting Sezzle's WebView checkout.
 *
 * Use this instead of the legacy [com.sezzle.sdk.SezzleCheckoutListener]-based
 * `startCheckout` overloads when your host activity can be destroyed and recreated
 * mid-checkout — e.g. the "Don't keep activities" developer option, "Background
 * process limit = No background processes", or normal low-memory conditions.
 *
 * Why this is needed: the listener-based API stores the merchant's callback in a
 * static field on `SezzleCheckoutWebViewActivity`. If Android kills and recreates
 * the launching activity while checkout is on screen, that reference points at the
 * dead activity and the result is silently dropped — leaving the merchant's
 * checkout stuck in a submitting state. The Activity Result API ([ActivityResultRegistry])
 * survives configuration changes and process recreation, so the callback reliably
 * reaches whichever instance of the host activity is alive when checkout finishes.
 *
 * **Usage:**
 *
 * Register the launcher in your activity's field declarations or `onCreate`
 * (before the activity reaches STARTED — same constraint as any other
 * `registerForActivityResult` call):
 *
 * ```kotlin
 * private val sezzleLauncher = registerForActivityResult(SezzleCheckoutContract()) { result ->
 *     when (result) {
 *         is SezzleCheckoutContract.Output.Complete -> {
 *             // result.orderUuid (SDK-creates-session flow) or result.callbackUrl (server-driven)
 *         }
 *         SezzleCheckoutContract.Output.Cancel -> { /* user cancelled */ }
 *         is SezzleCheckoutContract.Output.Error -> {
 *             // result.code identifies the error type; result.message is human-readable
 *         }
 *     }
 * }
 * ```
 *
 * Then launch via the SDK's launcher-aware overload:
 *
 * ```kotlin
 * SezzleSDK.startCheckoutForResult(sezzleLauncher, checkout)
 * // or, for the server-driven flow:
 * SezzleSDK.startCheckoutForResult(sezzleLauncher, checkoutUrl, completeUrl, cancelUrl)
 * ```
 *
 * **Scope:** this contract covers `WEB_VIEW` mode only. The system-browser
 * (Custom Tabs) flow has a different recreation profile and is handled
 * separately by [com.sezzle.sdk.SezzleSDK.startCheckout]'s listener-based path.
 */
class SezzleCheckoutContract : ActivityResultContract<SezzleCheckoutContract.Input, SezzleCheckoutContract.Output>() {

    /**
     * Internal input shape. Construct via [SezzleSDK.startCheckoutForResult] —
     * not intended to be built directly by merchants.
     */
    data class Input internal constructor(
        internal val checkoutUrl: String,
        internal val orderUuid: String?,
        internal val completeUrl: Uri,
        internal val cancelUrl: Uri,
    )

    /** The terminal result of a checkout. */
    sealed class Output {
        /**
         * Checkout completed successfully.
         *
         * @param orderUuid Populated for the SDK-creates-session flow. Send this to your
         *                  backend to capture payment via `POST /v2/order/{uuid}/capture`.
         * @param callbackUrl Populated for the server-driven flow. Read query parameters
         *                    to recover any state you encoded in your `complete_url`.
         */
        data class Complete(val orderUuid: String?, val callbackUrl: Uri?) : Output()

        /**
         * User cancelled checkout. This covers every non-error dismissal path:
         * the close X button, the hardware back button at the root of the WebView's
         * navigation, the checkout page navigating to the configured `cancelUrl`,
         * swiping the activity out of Recents, or the system destroying the activity
         * without a delivered result (process death under "Don't keep activities").
         */
        data object Cancel : Output()

        /**
         * Checkout failed. [code] is one of the [ErrorCode] string constants and identifies
         * the error type for programmatic handling; [message] is a human-readable description.
         */
        data class Error(val code: String, val message: String) : Output()
    }

    /** String codes for [Output.Error.code]. */
    object ErrorCode {
        /**
         * Reserved for future use. The current implementation routes user-dismissal events
         * (X close, hardware back, system destroy) to [Output.Cancel]. Legacy listener-based
         * integrations still see `onCheckoutError(BrowserDismissed)` for the same events.
         */
        const val BROWSER_DISMISSED = "browser_dismissed"

        /** The checkout activity received malformed input or an unexpected callback URL. */
        const val INVALID_RESPONSE = "invalid_response"

        /** A network error occurred while loading the checkout page (DNS, TLS, timeout, etc.). */
        const val NETWORK_ERROR = "network_error"

        /**
         * The SDK was not configured before launching. Call [SezzleSDK.configure] at app
         * startup with your `sz_pub_...` public key. Only emitted by the SDK-creates-session
         * `startCheckoutForResult` overload — the server-driven overload does not require
         * configuration.
         */
        const val NOT_CONFIGURED = "not_configured"

        /**
         * The checkout activity terminated without delivering a result. Possible causes
         * include the activity being destroyed by the system before it could finish
         * (very rare under normal conditions; can occur under aggressive memory pressure)
         * or a hard crash in the WebView. Treat as a transient failure.
         */
        const val NO_RESULT = "no_result"
    }

    override fun createIntent(context: Context, input: Input): Intent {
        return Intent(context, SezzleCheckoutWebViewActivity::class.java).apply {
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CHECKOUT_URL, input.checkoutUrl)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_ORDER_UUID, input.orderUuid)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_COMPLETE_URL, input.completeUrl.toString())
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CANCEL_URL, input.cancelUrl.toString())
            // Intentionally do NOT set EXTRA_USE_LEGACY_LISTENER — this entrypoint
            // routes results via the Intent, not via the static listener.
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Output {
        // Clear the SDK's overlap gate now that the checkout has terminated, regardless
        // of result type. Without this, the gate stays set forever if the merchant launches
        // via [SezzleSDK.startCheckoutForResult] (which sets the gate but no longer clears
        // it immediately — see notes there).
        SezzleSDK.notifyLauncherCheckoutEnded()

        // RESULT_CANCELED with no intent extras is what the system delivers when the
        // checkout activity was destroyed without setResult — e.g. swiped from Recents,
        // or the user pressed Home → back-to-app under a memory-pressure scenario where
        // the system killed the activity. Treat as cancellation, not error.
        if (resultCode == Activity.RESULT_CANCELED && intent == null) {
            return Output.Cancel
        }

        val type = intent?.getStringExtra(RESULT_TYPE_KEY)
        return when (type) {
            RESULT_TYPE_COMPLETE -> Output.Complete(
                orderUuid = intent.getStringExtra(RESULT_ORDER_UUID_KEY),
                callbackUrl = intent.getStringExtra(RESULT_CALLBACK_URL_KEY)?.let(Uri::parse),
            )
            RESULT_TYPE_CANCEL -> Output.Cancel
            RESULT_TYPE_ERROR -> Output.Error(
                code = intent.getStringExtra(RESULT_ERROR_CODE_KEY) ?: ErrorCode.INVALID_RESPONSE,
                message = intent.getStringExtra(RESULT_ERROR_MESSAGE_KEY) ?: "Unknown error",
            )
            // Result-OK but no type extra means the activity set a result via the wrong
            // path. Shouldn't happen in practice; treat as a defensive failure.
            else -> Output.Error(ErrorCode.NO_RESULT, "Checkout activity finished without delivering a result")
        }
    }

    companion object {
        internal const val RESULT_TYPE_KEY = "sezzle_result_type"
        internal const val RESULT_TYPE_COMPLETE = "complete"
        internal const val RESULT_TYPE_CANCEL = "cancel"
        internal const val RESULT_TYPE_ERROR = "error"

        internal const val RESULT_ORDER_UUID_KEY = "sezzle_order_uuid"
        internal const val RESULT_CALLBACK_URL_KEY = "sezzle_callback_url"
        internal const val RESULT_ERROR_CODE_KEY = "sezzle_error_code"
        internal const val RESULT_ERROR_MESSAGE_KEY = "sezzle_error_message"
    }
}
