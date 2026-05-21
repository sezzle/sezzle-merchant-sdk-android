package com.sezzle.sdk.checkout

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsIntent
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.SezzleCheckoutResult
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleCheckoutMode
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionServiceProtocol
import com.sezzle.sdk.networking.SezzleEventLogger

internal class CheckoutHandler(
    private val sessionService: SessionServiceProtocol? = null,
    private val eventLogger: SezzleEventLogger? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionUUID: String = ""
    private var checkoutUUID: String = ""
    private var checkoutMode: String = ""

    companion object {
        const val CALLBACK_SCHEME = "sezzle-sdk"
        val DEFAULT_COMPLETE_URL: Uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        val DEFAULT_CANCEL_URL: Uri = Uri.parse("sezzle-sdk://checkout/cancelled")

        /** Matches if [uri] shares scheme + host + path with [target]. Query/fragment may differ. */
        internal fun matches(uri: Uri, target: Uri): Boolean {
            return uri.scheme?.lowercase() == target.scheme?.lowercase()
                && uri.host?.lowercase() == target.host?.lowercase()
                && uri.path == target.path
        }

        internal fun handleCallbackUri(
            uri: Uri,
            completeUrl: Uri,
            cancelUrl: Uri,
            orderUUID: String?,
            listener: SezzleCheckoutListener
        ) {
            when {
                matches(uri, completeUrl) -> {
                    val result = SezzleCheckoutResult(
                        orderUUID = orderUUID,
                        callbackURL = if (orderUUID == null) uri else null
                    )
                    listener.onCheckoutComplete(result)
                }
                matches(uri, cancelUrl) -> listener.onCheckoutCancel()
                else -> listener.onCheckoutError(SezzleError.InvalidResponse)
            }
        }
    }

    // MARK: SDK-creates-session flow
    fun startCheckout(
        checkout: SezzleCheckout,
        activity: ComponentActivity,
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode
    ) {
        val service = sessionService
        if (service == null) {
            listener.onCheckoutError(SezzleError.NotConfigured)
            return
        }

        checkoutMode = if (mode == SezzleCheckoutMode.WEB_VIEW) "webview" else "system_browser"
        eventLogger?.log(event = SezzleEventLogger.Event.POPUP_CREATED, mode = checkoutMode, message = "checkout initiated")

        val loggingListener = wrapWithEventLogging(listener)

        service.createSession(
            checkout = checkout,
            onSuccess = { response ->
                sessionUUID = response.uuid
                checkoutUUID = Uri.parse(response.checkoutURL).getQueryParameter("id") ?: ""

                eventLogger?.log(
                    event = SezzleEventLogger.Event.LOADED,
                    sessionUUID = response.uuid,
                    orderUUID = response.orderUUID,
                    checkoutUUID = checkoutUUID,
                    mode = checkoutMode
                )

                mainHandler.post {
                    val checkoutUri = Uri.parse(response.checkoutURL).buildUpon()
                        .appendQueryParameter("isWebView", "true")
                        .build()

                    presentCheckout(
                        activity = activity,
                        checkoutUri = checkoutUri,
                        orderUUID = response.orderUUID,
                        completeUrl = DEFAULT_COMPLETE_URL,
                        cancelUrl = DEFAULT_CANCEL_URL,
                        listener = loggingListener,
                        mode = mode
                    )
                }
            },
            onError = { error ->
                mainHandler.post {
                    loggingListener.onCheckoutError(error)
                }
            }
        )
    }

    // MARK: Server-driven (pass-URL) flow
    fun startCheckout(
        checkoutUrl: String,
        completeUrl: Uri,
        cancelUrl: Uri,
        activity: ComponentActivity,
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode
    ) {
        checkoutMode = if (mode == SezzleCheckoutMode.WEB_VIEW) "webview" else "system_browser"
        // No event logging — no public key on this path.
        // No session creation — merchant did it server-side.

        val checkoutUri = Uri.parse(checkoutUrl).buildUpon()
            .appendQueryParameter("isWebView", "true")
            .build()

        presentCheckout(
            activity = activity,
            checkoutUri = checkoutUri,
            orderUUID = null,
            completeUrl = completeUrl,
            cancelUrl = cancelUrl,
            listener = listener,
            mode = mode
        )
    }

    private fun presentCheckout(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String?,
        completeUrl: Uri,
        cancelUrl: Uri,
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode
    ) {
        when (mode) {
            SezzleCheckoutMode.WEB_VIEW -> {
                launchWebView(activity, checkoutUri.toString(), orderUUID, completeUrl, cancelUrl, listener)
            }
            SezzleCheckoutMode.SYSTEM_BROWSER -> {
                launchCustomTab(activity, checkoutUri, orderUUID, completeUrl, cancelUrl, listener)
            }
        }
    }

    /** Wrap a listener to log analytics events via [eventLogger] (no-op when logger is null). */
    private fun wrapWithEventLogging(listener: SezzleCheckoutListener): SezzleCheckoutListener {
        if (eventLogger == null) return listener
        return object : SezzleCheckoutListener {
            override fun onCheckoutComplete(result: SezzleCheckoutResult) {
                eventLogger.log(
                    event = SezzleEventLogger.Event.SUCCESS,
                    sessionUUID = sessionUUID,
                    orderUUID = result.orderUUID ?: "",
                    checkoutUUID = checkoutUUID,
                    mode = checkoutMode
                )
                listener.onCheckoutComplete(result)
            }
            override fun onCheckoutCancel() {
                eventLogger.log(
                    event = SezzleEventLogger.Event.CANCEL,
                    sessionUUID = sessionUUID,
                    checkoutUUID = checkoutUUID,
                    mode = checkoutMode
                )
                listener.onCheckoutCancel()
            }
            override fun onCheckoutError(error: SezzleError) {
                eventLogger.log(
                    event = SezzleEventLogger.Event.FAILURE,
                    sessionUUID = sessionUUID,
                    checkoutUUID = checkoutUUID,
                    mode = checkoutMode,
                    message = error.message
                )
                listener.onCheckoutError(error)
            }
        }
    }

    private fun launchCustomTab(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String?,
        completeUrl: Uri,
        cancelUrl: Uri,
        listener: SezzleCheckoutListener
    ) {
        // Custom Tabs fallback uses the OS intent-filter mechanism. The SDK's manifest
        // registers `sezzle-sdk://checkout` for the existing flow. Merchants using a
        // custom callback scheme MUST register an intent-filter in their own
        // AndroidManifest.xml pointing at SezzleRedirectActivity (or their own
        // forwarding activity that calls handleCallbackUri).
        CheckoutState.listener = listener
        CheckoutState.orderUUID = orderUUID
        CheckoutState.completeUrl = completeUrl
        CheckoutState.cancelUrl = cancelUrl
        CheckoutState.launchingActivityClassName = activity.javaClass.name
        val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabsIntent.launchUrl(activity, checkoutUri)
    }

    private fun launchWebView(
        activity: ComponentActivity,
        checkoutUrl: String,
        orderUUID: String?,
        completeUrl: Uri,
        cancelUrl: Uri,
        listener: SezzleCheckoutListener
    ) {
        SezzleCheckoutWebViewActivity.listener = listener
        val intent = Intent(activity, SezzleCheckoutWebViewActivity::class.java).apply {
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CHECKOUT_URL, checkoutUrl)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_ORDER_UUID, orderUUID)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_COMPLETE_URL, completeUrl.toString())
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CANCEL_URL, cancelUrl.toString())
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_USE_LEGACY_LISTENER, true)
        }
        activity.startActivity(intent)
    }

    // MARK: SDK-creates-session flow, lifecycle-safe via ActivityResultLauncher.
    /**
     * Equivalent of [startCheckout] above, but delivers the result through the merchant's
     * [ActivityResultLauncher] instead of a static listener. This is the lifecycle-safe path —
     * the launcher's callback survives host-activity destruction (Don't Keep Activities,
     * low-memory recreation, etc.).
     */
    fun startCheckoutForResult(
        checkout: SezzleCheckout,
        launcher: ActivityResultLauncher<SezzleCheckoutContract.Input>,
        onError: (SezzleError) -> Unit,
        onLaunched: () -> Unit,
        // Fired after createSession succeeds, with enough context for the SDK to log
        // terminal analytics events (SUCCESS/CANCEL/FAILURE) when parseResult fires.
        // Without this, the launcher path would emit only POPUP_CREATED and LOADED —
        // the legacy startCheckout path emits the terminal events via wrapWithEventLogging
        // around the merchant's listener, but the launcher path has no listener to wrap.
        onSessionReady: (sessionUUID: String, checkoutUUID: String) -> Unit,
    ) {
        val service = sessionService
        if (service == null) {
            onError(SezzleError.NotConfigured)
            return
        }

        checkoutMode = "webview"
        eventLogger?.log(event = SezzleEventLogger.Event.POPUP_CREATED, mode = checkoutMode, message = "checkout initiated")

        service.createSession(
            checkout = checkout,
            onSuccess = { response ->
                sessionUUID = response.uuid
                checkoutUUID = Uri.parse(response.checkoutURL).getQueryParameter("id") ?: ""

                eventLogger?.log(
                    event = SezzleEventLogger.Event.LOADED,
                    sessionUUID = response.uuid,
                    orderUUID = response.orderUUID,
                    checkoutUUID = checkoutUUID,
                    mode = checkoutMode
                )

                onSessionReady(response.uuid, checkoutUUID)

                mainHandler.post {
                    try {
                        val checkoutUri = Uri.parse(response.checkoutURL).buildUpon()
                            .appendQueryParameter("isWebView", "true")
                            .build()
                        launcher.launch(
                            SezzleCheckoutContract.Input(
                                checkoutUrl = checkoutUri.toString(),
                                orderUuid = response.orderUUID,
                                completeUrl = DEFAULT_COMPLETE_URL,
                                cancelUrl = DEFAULT_CANCEL_URL,
                            )
                        )
                        onLaunched()
                    } catch (t: Throwable) {
                        // launcher.launch throws IllegalStateException if the host activity
                        // (and its ActivityResultRegistry) was destroyed during the in-flight
                        // createSession call. Route to onError instead of crashing the main
                        // looper, and let the SezzleSDK wrapper clear the in-flight gate.
                        onError(SezzleError.NetworkError(t))
                    }
                }
            },
            onError = { error ->
                mainHandler.post { onError(error) }
            }
        )
    }

    // MARK: Server-driven flow, lifecycle-safe via ActivityResultLauncher.
    fun startCheckoutForResult(
        checkoutUrl: String,
        completeUrl: Uri,
        cancelUrl: Uri,
        launcher: ActivityResultLauncher<SezzleCheckoutContract.Input>,
    ) {
        val checkoutUri = Uri.parse(checkoutUrl).buildUpon()
            .appendQueryParameter("isWebView", "true")
            .build()
        launcher.launch(
            SezzleCheckoutContract.Input(
                checkoutUrl = checkoutUri.toString(),
                orderUuid = null,
                completeUrl = completeUrl,
                cancelUrl = cancelUrl,
            )
        )
    }
}
