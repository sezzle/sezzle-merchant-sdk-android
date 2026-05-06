package com.sezzle.sdk

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.checkout.CheckoutState
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleCheckoutMode
import com.sezzle.sdk.models.SezzleEnvironment
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.HttpClient
import com.sezzle.sdk.networking.SessionService
import com.sezzle.sdk.networking.SezzleEventLogger

/**
 * The main entry point for the Sezzle Merchant SDK.
 *
 * Two checkout flows are supported:
 *
 * **SDK-creates-session flow** — call [configure] once at app startup, then call
 * [startCheckout] with a [SezzleCheckout] payload. The SDK creates the session via
 * Sezzle's API and presents checkout.
 *
 * **Server-driven flow** — your backend creates the session via `POST /v2/session`
 * directly, then call [startCheckout] with the response data. No public key on-device,
 * no [configure] required.
 */
object SezzleSDK {
    private var publicKey: String? = null
    private var environment: SezzleEnvironment? = null
    private var lifecycleCallbacksRegistered = false

    /**
     * Configure the SDK with your Sezzle public key.
     *
     * Required only for the SDK-creates-session flow. The server-driven entrypoint
     * (the `startCheckout(checkoutUrl, completeUrl, cancelUrl, ...)` overload)
     * works without ever calling `configure`.
     *
     * @param publicKey Your Sezzle public key (starts with `sz_pub_...`).
     * @param environment [SezzleEnvironment.SANDBOX] for testing, [SezzleEnvironment.PRODUCTION] for live.
     */
    fun configure(
        publicKey: String,
        environment: SezzleEnvironment = SezzleEnvironment.PRODUCTION
    ) {
        this.publicKey = publicKey
        this.environment = environment
    }

    /**
     * Start a Sezzle checkout — SDK creates the session.
     *
     * Requires [configure] to have been called.
     *
     * On success, [SezzleCheckoutListener.onCheckoutComplete] is called with `result.orderUUID`
     * populated. Send that UUID to your backend to capture the payment via
     * `POST /v2/order/{uuid}/capture`.
     *
     * @param checkout The customer and order data for this checkout.
     * @param activity The activity to launch from. Must extend [ComponentActivity].
     * @param listener Receives checkout completion, cancellation, or error callbacks on the main thread.
     * @param mode How the checkout is presented. Defaults to [SezzleCheckoutMode.SYSTEM_BROWSER].
     */
    fun startCheckout(
        checkout: SezzleCheckout,
        activity: Activity,
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode = SezzleCheckoutMode.SYSTEM_BROWSER
    ) {
        val key = publicKey
        val env = environment
        if (key == null || env == null) {
            listener.onCheckoutError(SezzleError.NotConfigured)
            return
        }

        val componentActivity = activity as? ComponentActivity
        if (componentActivity == null) {
            listener.onCheckoutError(SezzleError.NotConfigured)
            return
        }

        if (mode == SezzleCheckoutMode.SYSTEM_BROWSER) {
            registerLifecycleCallbacks(activity)
        }

        val httpClient = HttpClient(key, env)
        val sessionService = SessionService(httpClient)
        val eventLogger = SezzleEventLogger(key, env)
        val handler = CheckoutHandler(sessionService, eventLogger)
        handler.startCheckout(checkout, componentActivity, listener, mode)
    }

    /**
     * Start a Sezzle checkout — your backend already created the session.
     *
     * Use this when your server creates the Sezzle session via `POST /v2/session` directly
     * (e.g. to keep your private key off-device). Pass the `order.checkout_url` from the
     * session response, plus the same `complete_url.href` and `cancel_url.href` your server
     * supplied in the request — the SDK intercepts navigation to those URLs and dispatches
     * the corresponding listener method.
     *
     * Does NOT require [configure] to have been called.
     *
     * On success, [SezzleCheckoutListener.onCheckoutComplete] is called with `result.callbackURL`
     * populated — read query parameters there to recover any state you encoded in your
     * `complete_url`. `result.orderUUID` is `null` because your backend already has it from
     * the session-creation response.
     *
     * **Manifest note (Chrome <137 / Custom Tabs fallback):** if you use [SezzleCheckoutMode.SYSTEM_BROWSER]
     * with a callback scheme other than `sezzle-sdk`, register an intent-filter for that scheme
     * in your own `AndroidManifest.xml` pointing at [com.sezzle.sdk.checkout.SezzleRedirectActivity]
     * (or your own forwarding activity that calls `CheckoutHandler.handleCallbackUri`).
     * The SDK's bundled intent-filter only covers `sezzle-sdk://checkout`. WebView mode and
     * AuthTab (Chrome ≥137) need no manifest work.
     *
     * @param checkoutUrl The `order.checkout_url` from your `POST /v2/session` response.
     * @param completeUrl The same URL you passed as `complete_url.href` in the session request.
     * @param cancelUrl The same URL you passed as `cancel_url.href` in the session request.
     * @param activity The activity to launch from. Must extend [ComponentActivity].
     * @param listener Receives checkout completion, cancellation, or error callbacks on the main thread.
     * @param mode How the checkout is presented. Defaults to [SezzleCheckoutMode.SYSTEM_BROWSER].
     */
    fun startCheckout(
        checkoutUrl: String,
        completeUrl: Uri,
        cancelUrl: Uri,
        activity: Activity,
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode = SezzleCheckoutMode.SYSTEM_BROWSER
    ) {
        val componentActivity = activity as? ComponentActivity
        if (componentActivity == null) {
            listener.onCheckoutError(SezzleError.NotConfigured)
            return
        }

        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            validateUrls(checkoutUrl, completeUrl, cancelUrl)
        }

        if (mode == SezzleCheckoutMode.SYSTEM_BROWSER) {
            registerLifecycleCallbacks(activity)
        }

        // No HttpClient, no SessionService, no EventLogger — pass-URL flow doesn't need them.
        val handler = CheckoutHandler(sessionService = null, eventLogger = null)
        handler.startCheckout(checkoutUrl, completeUrl, cancelUrl, componentActivity, listener, mode)
    }

    /** Whether the SDK has been configured. */
    val isConfigured: Boolean
        get() = publicKey != null && environment != null

    private fun validateUrls(checkoutUrl: String, completeUrl: Uri, cancelUrl: Uri) {
        val host = Uri.parse(checkoutUrl).host?.lowercase()
        if (host != null && !host.endsWith("sezzle.com")) {
            Log.w("SezzleSDK", "checkoutUrl host '$host' is not a sezzle.com domain. Are you sure this is right?")
        }
        if (completeUrl.scheme != cancelUrl.scheme) {
            Log.w("SezzleSDK", "completeUrl scheme (${completeUrl.scheme}) and cancelUrl scheme (${cancelUrl.scheme}) differ. They should typically share a scheme.")
        }
    }

    /**
     * Lifecycle callbacks for Custom Tab fallback dismiss detection.
     * When Auth Tab is used, this is not needed (Auth Tab has its own result callback).
     * But when falling back to Custom Tabs, we detect browser dismiss via onResume.
     */
    private fun registerLifecycleCallbacks(activity: Activity) {
        if (lifecycleCallbacksRegistered) return
        lifecycleCallbacksRegistered = true

        activity.application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(resumedActivity: Activity) {
                    val launchingClass = CheckoutState.launchingActivityClassName ?: return
                    if (resumedActivity.javaClass.name != launchingClass) return
                    if (CheckoutState.listener == null) return

                    val listener = CheckoutState.listener
                    CheckoutState.clear()
                    listener?.onCheckoutError(SezzleError.BrowserDismissed)
                }
                override fun onActivityCreated(a: Activity, s: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            }
        )
    }
}
