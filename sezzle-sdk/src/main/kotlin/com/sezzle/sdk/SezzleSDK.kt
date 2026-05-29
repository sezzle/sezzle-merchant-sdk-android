package com.sezzle.sdk

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.checkout.CheckoutState
import com.sezzle.sdk.checkout.SezzleCheckoutContract
import com.sezzle.sdk.checkout.SezzleSessionScrubber
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
    private var isCheckoutInProgress = false

    /**
     * Per-in-flight-checkout context for emitting terminal analytics events
     * (SUCCESS / CANCEL / FAILURE) on the launcher path. Captured during
     * SDK-creates-session [startCheckoutForResult] after the session API call
     * succeeds; consumed by [notifyLauncherCheckoutEnded]. Null for the
     * server-driven launcher path (no public key on-device → no event logging).
     */
    private var pendingLauncherEventContext: LauncherEventContext? = null

    private data class LauncherEventContext(
        val eventLogger: SezzleEventLogger,
        val sessionUUID: String,
        val checkoutUUID: String,
        val mode: String,
    )

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
     * **Note:** for [SezzleCheckoutMode.WEB_VIEW] mode, prefer [startCheckoutForResult] —
     * it delivers the result through the Android Activity Result API, which survives
     * host-activity destruction (e.g. "Don't keep activities" developer option). The
     * listener-based path here can drop the result if the host activity is destroyed
     * mid-checkout.
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

        // Reject overlapping calls (e.g. rapid double-tap). A second startCheckout
        // before the first delivers its result can confuse Custom Tabs / WebView
        // presentation and surface a bogus checkoutDidFail to the merchant.
        if (isCheckoutInProgress) return
        isCheckoutInProgress = true

        if (mode == SezzleCheckoutMode.SYSTEM_BROWSER) {
            registerLifecycleCallbacks(activity)
        }

        val httpClient = HttpClient(key, env)
        val sessionService = SessionService(httpClient)
        val eventLogger = SezzleEventLogger(key, env)
        val handler = CheckoutHandler(sessionService, eventLogger)
        val wrappedListener = ProgressTrackingListener(listener) { isCheckoutInProgress = false }
        handler.startCheckout(checkout, componentActivity, wrappedListener, mode)
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
     * **Note:** for [SezzleCheckoutMode.WEB_VIEW] mode, prefer [startCheckoutForResult] —
     * it delivers the result through the Android Activity Result API, which survives
     * host-activity destruction (e.g. "Don't keep activities" developer option). The
     * listener-based path here can drop the result if the host activity is destroyed
     * mid-checkout.
     *
     * **Manifest note for [SezzleCheckoutMode.SYSTEM_BROWSER]:** if you use a callback scheme
     * other than `sezzle-sdk`, register an intent-filter for that scheme in your own
     * `AndroidManifest.xml` pointing at [com.sezzle.sdk.checkout.SezzleRedirectActivity]
     * (or your own forwarding activity that calls `CheckoutHandler.handleCallbackUri`).
     * The SDK's bundled intent-filter only covers `sezzle-sdk://checkout`. [SezzleCheckoutMode.WEB_VIEW]
     * mode needs no manifest work — any scheme is intercepted by the WebView client directly.
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

        // Reject overlapping calls (see notes on the SezzleCheckout overload above).
        if (isCheckoutInProgress) return
        isCheckoutInProgress = true

        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            validateUrls(checkoutUrl, completeUrl, cancelUrl)
        }

        if (mode == SezzleCheckoutMode.SYSTEM_BROWSER) {
            registerLifecycleCallbacks(activity)
        }

        // No HttpClient, no SessionService, no EventLogger — pass-URL flow doesn't need them.
        val handler = CheckoutHandler(sessionService = null, eventLogger = null)
        val wrappedListener = ProgressTrackingListener(listener) { isCheckoutInProgress = false }
        handler.startCheckout(checkoutUrl, completeUrl, cancelUrl, componentActivity, wrappedListener, mode)
    }

    /**
     * Lifecycle-safe SDK-creates-session checkout entrypoint.
     *
     * Equivalent to [startCheckout] but delivers the result through the merchant's
     * [ActivityResultLauncher] (via [SezzleCheckoutContract]) instead of a static
     * listener. Use this if your host activity can be destroyed and recreated mid-checkout
     * — e.g. "Don't keep activities" developer option, "Background process limit", or
     * normal low-memory conditions. The launcher's callback is bound to the Activity
     * Result Registry, which Android re-binds on activity recreation, so the result
     * still reaches the live host activity.
     *
     * **WebView mode only.** This entrypoint always uses [SezzleCheckoutMode.WEB_VIEW];
     * the system-browser (Custom Tabs) flow has a different recreation profile and
     * continues to use the listener-based [startCheckout].
     *
     * Register the launcher in your activity's field declarations or `onCreate`:
     *
     * ```kotlin
     * private val sezzleLauncher = registerForActivityResult(SezzleCheckoutContract()) { result ->
     *     when (result) {
     *         is SezzleCheckoutContract.Output.Complete -> { /* result.orderUuid */ }
     *         is SezzleCheckoutContract.Output.Cancel -> { /* user cancelled */ }
     *         is SezzleCheckoutContract.Output.Error -> { /* result.code, result.message */ }
     *     }
     * }
     * ```
     *
     * @param launcher The merchant's registered launcher. Must be registered before
     *                 the host activity reaches STARTED.
     * @param checkout The customer and order data for this checkout.
     * @param onError Called when a pre-launch failure prevents the launcher from firing —
     *                SDK not configured ([SezzleError.NotConfigured]), session creation
     *                fails ([SezzleError.NetworkError], [SezzleError.ApiError]), or the
     *                merchant's launcher was already unregistered by the time we tried to
     *                launch ([SezzleError.NetworkError] wrapping `IllegalStateException`).
     *                **Required** — a default no-op would silently eat these errors and
     *                leave the merchant's checkout button looking unresponsive. Once the
     *                launcher fires, all subsequent errors are delivered through it.
     */
    fun startCheckoutForResult(
        launcher: ActivityResultLauncher<SezzleCheckoutContract.Input>,
        checkout: SezzleCheckout,
        onError: (SezzleError) -> Unit,
    ) {
        val key = publicKey
        val env = environment
        if (key == null || env == null) {
            onError(SezzleError.NotConfigured)
            return
        }
        if (isCheckoutInProgress) return
        isCheckoutInProgress = true

        val httpClient = HttpClient(key, env)
        val sessionService = SessionService(httpClient)
        val eventLogger = SezzleEventLogger(key, env)
        val handler = CheckoutHandler(sessionService, eventLogger)
        // Gate is held through the entire checkout: cleared on (a) network error before
        // launch — pre-launch path here — or (b) the terminal result arriving back to the
        // launcher — see [notifyLauncherCheckoutEnded], called from [SezzleCheckoutContract.parseResult].
        // Matches the legacy listener path's overlap protection.
        handler.startCheckoutForResult(
            checkout = checkout,
            launcher = launcher,
            onError = { error ->
                isCheckoutInProgress = false
                onError(error)
            },
            onLaunched = { /* leave gate set; parseResult clears it on result */ },
            onSessionReady = { sessionUUID, checkoutUUID ->
                // Capture context for the terminal analytics events (SUCCESS/CANCEL/FAILURE).
                // The launcher path has no listener to wrap, so we fire from
                // notifyLauncherCheckoutEnded() instead. Server-driven path doesn't go
                // through createSession and never sets this — server-driven flow has no
                // event logging by design (no public key on-device).
                pendingLauncherEventContext = LauncherEventContext(
                    eventLogger = eventLogger,
                    sessionUUID = sessionUUID,
                    checkoutUUID = checkoutUUID,
                    mode = "webview",
                )
            },
        )
    }

    /**
     * Lifecycle-safe server-driven checkout entrypoint.
     *
     * Same rationale as the [SezzleCheckout] overload above. Use this when your backend
     * has already created the Sezzle session via `POST /v2/session` directly.
     *
     * @param launcher The merchant's registered launcher.
     * @param checkoutUrl The `order.checkout_url` from your session-creation response.
     * @param completeUrl The same URL you passed as `complete_url.href`.
     * @param cancelUrl The same URL you passed as `cancel_url.href`.
     * @param onError Called when `launcher.launch(...)` throws — typically because the
     *                merchant's host activity (and its `ActivityResultRegistry`) was
     *                destroyed before this call ran. Mirrors the [SezzleCheckout]
     *                overload's error channel so a single `onError` handler works for
     *                both entrypoints.
     */
    fun startCheckoutForResult(
        launcher: ActivityResultLauncher<SezzleCheckoutContract.Input>,
        checkoutUrl: String,
        completeUrl: Uri,
        cancelUrl: Uri,
        onError: (SezzleError) -> Unit,
    ) {
        if (isCheckoutInProgress) return
        isCheckoutInProgress = true

        val handler = CheckoutHandler(sessionService = null, eventLogger = null)
        // Gate is held until the terminal result arrives — see [notifyLauncherCheckoutEnded],
        // called from [SezzleCheckoutContract.parseResult]. If launcher.launch throws (e.g.
        // unregistered launcher), route via onError + clear the gate so the merchant gets a
        // signal and the SDK doesn't strand. Same channel as the SDK-creates-session
        // overload — consistent API surface across both entrypoints.
        try {
            handler.startCheckoutForResult(
                checkoutUrl = checkoutUrl,
                completeUrl = completeUrl,
                cancelUrl = cancelUrl,
                launcher = launcher,
            )
        } catch (t: Throwable) {
            isCheckoutInProgress = false
            onError(SezzleError.NetworkError(t))
        }
    }

    /**
     * Called by [SezzleCheckoutContract.parseResult] when a launcher-based checkout
     * terminates. Clears the overlap gate and emits the terminal analytics event
     * (SUCCESS / CANCEL / FAILURE) if event-logging context was captured at session
     * creation — i.e. only for the SDK-creates-session launcher overload. The
     * server-driven launcher overload has no event-logging context and emits no events,
     * matching the legacy server-driven path's behavior.
     *
     * Internal — merchants should never invoke this directly.
     */
    internal fun notifyLauncherCheckoutEnded(output: SezzleCheckoutContract.Output) {
        isCheckoutInProgress = false

        val ctx = pendingLauncherEventContext
        pendingLauncherEventContext = null
        if (ctx == null) return

        when (output) {
            is SezzleCheckoutContract.Output.Complete -> ctx.eventLogger.log(
                event = SezzleEventLogger.Event.SUCCESS,
                sessionUUID = ctx.sessionUUID,
                orderUUID = output.orderUuid ?: "",
                checkoutUUID = ctx.checkoutUUID,
                mode = ctx.mode,
            )
            SezzleCheckoutContract.Output.Cancel -> ctx.eventLogger.log(
                event = SezzleEventLogger.Event.CANCEL,
                sessionUUID = ctx.sessionUUID,
                checkoutUUID = ctx.checkoutUUID,
                mode = ctx.mode,
            )
            is SezzleCheckoutContract.Output.Error -> ctx.eventLogger.log(
                event = SezzleEventLogger.Event.FAILURE,
                sessionUUID = ctx.sessionUUID,
                checkoutUUID = ctx.checkoutUUID,
                mode = ctx.mode,
                message = output.message,
            )
        }
    }

    /** Whether the SDK has been configured. */
    val isConfigured: Boolean
        get() = publicKey != null && environment != null

    /**
     * Ends Sezzle's session and clears Sezzle's cookies + Web storage from the app-wide
     * WebView state.
     *
     * **Call this when your app's user logs out** (or switches accounts) so the next
     * Sezzle checkout starts with a fresh session.
     *
     * What it does, in order:
     * 1. Reads the WebView's Sezzle auth cookies (if present) and POSTs them to
     *    `/v4/users/logout` so Sezzle's backend invalidates the refresh token and forgets
     *    the device→user binding. Best-effort, off the main thread — 5-second timeout,
     *    errors are swallowed so a slow network never blocks the merchant's logout flow.
     * 2. Removes Sezzle-domain cookies + per-origin Web storage (localStorage, IndexedDB,
     *    etc.) from the app-wide WebView state.
     *
     * Step 1 is what fixes cross-user account leakage on real devices: a fully-empty
     * WebView jar is not enough because Sezzle's backend can still recognize the device
     * and pre-bind the next checkout to the prior user's account. Invalidating the refresh
     * token server-side is what closes the loop.
     *
     * The clear is **scoped to Sezzle's own domains** — your other cookies and Web storage
     * are not touched. Safe to call repeatedly; safe to call when no Sezzle checkout has
     * ever run in this process.
     *
     * Affects `WEB_VIEW` mode only. `SYSTEM_BROWSER` mode (Chrome Custom Tabs) shares
     * cookies with Chrome and is outside the SDK's reach — if you need to clear those, the
     * user should clear them in Chrome.
     */
    fun clearWebViewData() {
        SezzleSessionScrubber.clear(environment)
    }

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
     * Lifecycle callbacks for Custom Tabs dismiss detection: if the launching activity
     * is resumed while a checkout is still in flight (no redirect arrived yet), the user
     * dismissed the Custom Tab — fire BrowserDismissed.
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

/**
 * Wraps a merchant-supplied listener so SezzleSDK can clear its in-progress
 * flag on terminal callbacks (complete / cancel / error). Allows the SDK to
 * reject overlapping `startCheckout` calls without leaking the gate state.
 */
private class ProgressTrackingListener(
    private val wrapped: SezzleCheckoutListener,
    private val onTerminal: () -> Unit
) : SezzleCheckoutListener {
    override fun onCheckoutComplete(result: SezzleCheckoutResult) {
        onTerminal()
        wrapped.onCheckoutComplete(result)
    }
    override fun onCheckoutCancel() {
        onTerminal()
        wrapped.onCheckoutCancel()
    }
    override fun onCheckoutError(error: SezzleError) {
        onTerminal()
        wrapped.onCheckoutError(error)
    }
}

