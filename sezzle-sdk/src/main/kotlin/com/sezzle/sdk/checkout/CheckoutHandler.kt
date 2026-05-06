package com.sezzle.sdk.checkout

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
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
                if (isAuthTabSupported(activity)) {
                    launchAuthTab(activity, checkoutUri, orderUUID, completeUrl, cancelUrl, listener)
                } else {
                    launchCustomTab(activity, checkoutUri, orderUUID, completeUrl, cancelUrl, listener)
                }
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

    private fun isAuthTabSupported(activity: ComponentActivity): Boolean {
        return try {
            val pm = activity.packageManager
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            val resolveInfo = pm.resolveActivity(browserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val browserPackage = resolveInfo?.activityInfo?.packageName ?: return false
            val packageInfo = pm.getPackageInfo(browserPackage, 0)
            val majorVersion = packageInfo.versionName?.split(".")?.firstOrNull()?.toIntOrNull() ?: 0
            majorVersion >= 137
        } catch (_: Exception) {
            false
        }
    }

    private fun launchAuthTab(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String?,
        completeUrl: Uri,
        cancelUrl: Uri,
        listener: SezzleCheckoutListener
    ) {
        var resultDelivered = false
        // AuthTabIntent's callbackScheme is the scheme of the merchant's completeUrl.
        // Both completeUrl and cancelUrl should share a scheme; we use completeUrl's.
        val callbackScheme = completeUrl.scheme ?: CALLBACK_SCHEME
        val launcher = activity.activityResultRegistry.register(
            "sezzle_checkout_${System.nanoTime()}",
            AuthTabIntent.AuthenticateUserResultContract()
        ) { result ->
            if (resultDelivered) return@register
            resultDelivered = true
            if (result.resultCode == AuthTabIntent.RESULT_OK && result.resultUri != null) {
                handleCallbackUri(result.resultUri!!, completeUrl, cancelUrl, orderUUID, listener)
            } else {
                listener.onCheckoutError(SezzleError.BrowserDismissed)
            }
        }
        val authTab = AuthTabIntent.Builder().build()
        authTab.launch(launcher, checkoutUri, callbackScheme)
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
        }
        activity.startActivity(intent)
    }
}
