package com.sezzle.sdk.checkout

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionServiceProtocol

/**
 * Orchestrates the checkout flow: create session -> open browser -> handle callback.
 *
 * Uses [AuthTabIntent] when supported (Chrome 137+) for secure browser-based checkout
 * with built-in callback handling. Falls back to [CustomTabsIntent] with
 * [SezzleRedirectActivity] on older browsers.
 */
internal class CheckoutHandler(
    private val sessionService: SessionServiceProtocol
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CALLBACK_SCHEME = "sezzle-sdk"

        /** Parse a callback URI and dispatch to the listener. */
        internal fun handleCallbackUri(
            uri: Uri,
            orderUUID: String,
            listener: SezzleCheckoutListener
        ) {
            if (uri.host != "checkout") {
                listener.onCheckoutError(SezzleError.InvalidResponse)
                return
            }

            when (uri.path?.trimStart('/')) {
                "confirmed" -> listener.onCheckoutComplete(orderUUID)
                "cancelled" -> listener.onCheckoutCancel()
                else -> listener.onCheckoutError(SezzleError.InvalidResponse)
            }
        }
    }

    fun startCheckout(
        checkout: SezzleCheckout,
        activity: ComponentActivity,
        listener: SezzleCheckoutListener
    ) {
        sessionService.createSession(
            checkout = checkout,
            onSuccess = { response ->
                mainHandler.post {
                    val orderUUID = response.orderUUID
                    val checkoutUri = Uri.parse(response.checkoutURL)
                    if (checkoutUri == null) {
                        listener.onCheckoutError(SezzleError.InvalidResponse)
                        return@post
                    }

                    if (isAuthTabSupported(activity)) {
                        launchAuthTab(activity, checkoutUri, orderUUID, listener)
                    } else {
                        launchCustomTab(activity, checkoutUri, orderUUID, listener)
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    listener.onCheckoutError(error)
                }
            }
        )
    }

    /** Auth Tab requires Chrome 137+. Check the installed Chrome version. */
    private fun isAuthTabSupported(activity: ComponentActivity): Boolean {
        return try {
            val pm = activity.packageManager
            // Resolve which browser handles https:// — this gives us the active (updated) version
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://"))
            val resolveInfo = pm.resolveActivity(browserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val browserPackage = resolveInfo?.activityInfo?.packageName ?: return false

            val packageInfo = pm.getPackageInfo(browserPackage, 0)
            val majorVersion = packageInfo.versionName?.split(".")?.firstOrNull()?.toIntOrNull() ?: 0
            majorVersion >= 137
        } catch (_: Exception) {
            false
        }
    }

    /** Auth Tab path — Chrome 137+. Result comes via ActivityResultLauncher. */
    private fun launchAuthTab(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String,
        listener: SezzleCheckoutListener
    ) {
        var resultDelivered = false

        val launcher = activity.activityResultRegistry.register(
            "sezzle_checkout_${System.nanoTime()}",
            AuthTabIntent.AuthenticateUserResultContract()
        ) { result ->
            if (resultDelivered) return@register
            resultDelivered = true

            if (result.resultCode == AuthTabIntent.RESULT_OK && result.resultUri != null) {
                handleCallbackUri(result.resultUri!!, orderUUID, listener)
            } else {
                listener.onCheckoutError(SezzleError.BrowserDismissed)
            }
        }

        val authTab = AuthTabIntent.Builder().build()
        authTab.launch(launcher, checkoutUri, CALLBACK_SCHEME)
    }

    /** Custom Tab fallback — Chrome < 137. Result comes via SezzleRedirectActivity. */
    private fun launchCustomTab(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String,
        listener: SezzleCheckoutListener
    ) {
        CheckoutState.listener = listener
        CheckoutState.orderUUID = orderUUID
        CheckoutState.launchingActivityClassName = activity.javaClass.name

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(activity, checkoutUri)
    }
}
