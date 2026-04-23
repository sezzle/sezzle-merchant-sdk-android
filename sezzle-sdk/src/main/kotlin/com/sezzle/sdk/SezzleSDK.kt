package com.sezzle.sdk

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.checkout.CheckoutState
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleEnvironment
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.HttpClient
import com.sezzle.sdk.networking.SessionService

/**
 * The main entry point for the Sezzle Merchant SDK.
 *
 * Configure once at app startup, then start checkouts from anywhere.
 *
 * ```kotlin
 * // 1. Configure in Application.onCreate()
 * SezzleSDK.configure(publicKey = "sz_pub_...", environment = SezzleEnvironment.SANDBOX)
 *
 * // 2. Start checkout
 * SezzleSDK.startCheckout(checkout, activity, listener)
 * ```
 */
object SezzleSDK {
    private var publicKey: String? = null
    private var environment: SezzleEnvironment? = null
    private var lifecycleCallbacksRegistered = false

    /**
     * Configure the SDK with your Sezzle public key.
     *
     * Call this once at app startup (e.g., in `Application.onCreate()`),
     * before making any other SDK calls.
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
     * Start a Sezzle checkout.
     *
     * Opens the Sezzle checkout in a Chrome Custom Tab. When the user completes,
     * cancels, or encounters an error, the appropriate [listener] method is called
     * on the main thread.
     *
     * @param checkout The customer and order data for this checkout.
     * @param activity The activity to launch the Custom Tab from.
     * @param listener Receives checkout completion, cancellation, or error callbacks.
     */
    fun startCheckout(
        checkout: SezzleCheckout,
        activity: Activity,
        listener: SezzleCheckoutListener
    ) {
        val key = publicKey
        val env = environment
        if (key == null || env == null) {
            listener.onCheckoutError(SezzleError.NotConfigured)
            return
        }

        registerLifecycleCallbacks(activity)

        val httpClient = HttpClient(key, env)
        val sessionService = SessionService(httpClient)
        val handler = CheckoutHandler(sessionService)
        handler.startCheckout(checkout, activity, listener)
    }

    /** Whether the SDK has been configured. */
    val isConfigured: Boolean
        get() = publicKey != null && environment != null

    /**
     * Register Activity lifecycle callbacks to detect browser dismiss.
     *
     * When the user presses back in the Custom Tab (instead of completing/cancelling),
     * SezzleRedirectActivity is never triggered. We detect this by watching the
     * launching Activity's onResume — if CheckoutState is still pending, it means
     * the browser was dismissed.
     */
    private fun registerLifecycleCallbacks(activity: Activity) {
        if (lifecycleCallbacksRegistered) return
        lifecycleCallbacksRegistered = true

        activity.application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    // If a checkout was in progress and the redirect activity didn't handle it,
                    // the user dismissed the browser (back press / swipe)
                    if (CheckoutState.checkoutInProgress) {
                        val listener = CheckoutState.listener
                        CheckoutState.clear()
                        listener?.onCheckoutError(SezzleError.BrowserDismissed)
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }
}
