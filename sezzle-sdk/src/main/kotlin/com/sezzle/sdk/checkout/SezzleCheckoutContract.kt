package com.sezzle.sdk.checkout

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.sezzle.sdk.models.SezzleCheckoutMode

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
 *         is SezzleCheckoutContract.Output.Cancel -> { /* user cancelled */ }
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

        /** User cancelled checkout (closed via the X, hardware back at root, etc.). */
        object Cancel : Output()

        /**
         * Checkout failed. [code] is one of the [ErrorCode] string constants and identifies
         * the error type for programmatic handling; [message] is a human-readable description.
         */
        data class Error(val code: String, val message: String) : Output()
    }

    /** String codes for [Output.Error.code]. */
    object ErrorCode {
        const val BROWSER_DISMISSED = "browser_dismissed"
        const val INVALID_RESPONSE = "invalid_response"
        const val NETWORK_ERROR = "network_error"
        const val NOT_CONFIGURED = "not_configured"
        const val NO_RESULT = "no_result"
    }

    override fun createIntent(context: Context, input: Input): Intent {
        return Intent(context, SezzleCheckoutWebViewActivity::class.java).apply {
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CHECKOUT_URL, input.checkoutUrl)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_ORDER_UUID, input.orderUuid)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_COMPLETE_URL, input.completeUrl.toString())
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CANCEL_URL, input.cancelUrl.toString())
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Output {
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
            // No extras = activity finished without setResult (e.g. process death without
            // savedInstanceState restoration). Treat as user dismissal — best fallback.
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
