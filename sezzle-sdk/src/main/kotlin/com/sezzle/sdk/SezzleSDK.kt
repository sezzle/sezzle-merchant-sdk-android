package com.sezzle.sdk

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

        // Register lifecycle callbacks for Custom Tab fallback dismiss detection
        if (mode == SezzleCheckoutMode.SYSTEM_BROWSER) {
            registerLifecycleCallbacks(activity)
        }

        val httpClient = HttpClient(key, env)
        val sessionService = SessionService(httpClient)
        val eventLogger = SezzleEventLogger(key, env)
        val handler = CheckoutHandler(sessionService, eventLogger)
        handler.startCheckout(checkout, componentActivity, listener, mode)
    }

    /** Whether the SDK has been configured. */
    val isConfigured: Boolean
        get() = publicKey != null && environment != null

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
                    // Only fire for the Custom Tab fallback path, and only for the
                    // specific activity that started checkout — not ResultActivity,
                    // not SezzleRedirectActivity, not any other activity.
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
