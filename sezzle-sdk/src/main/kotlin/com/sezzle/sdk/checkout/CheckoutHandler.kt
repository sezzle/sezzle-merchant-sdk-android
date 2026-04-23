package com.sezzle.sdk.checkout

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionRequest
import com.sezzle.sdk.networking.SessionServiceProtocol

/**
 * Orchestrates the checkout flow: create session -> open Chrome Custom Tab -> handle callback.
 */
internal class CheckoutHandler(
    private val sessionService: SessionServiceProtocol
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startCheckout(
        checkout: SezzleCheckout,
        activity: Activity,
        listener: SezzleCheckoutListener
    ) {
        sessionService.createSession(
            checkout = checkout,
            onSuccess = { response ->
                mainHandler.post {
                    CheckoutState.listener = listener
                    CheckoutState.orderUUID = response.orderUUID
                    CheckoutState.checkoutInProgress = true
                    CheckoutState.launchingActivityClass = activity.javaClass
                    openBrowser(response.checkoutURL, activity, listener)
                }
            },
            onError = { error ->
                mainHandler.post {
                    listener.onCheckoutError(error)
                }
            }
        )
    }

    private fun openBrowser(url: String, activity: Activity, listener: SezzleCheckoutListener) {
        val uri = Uri.parse(url)
        if (uri == null) {
            listener.onCheckoutError(SezzleError.InvalidResponse)
            CheckoutState.clear()
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(activity, uri)
    }

    companion object {
        const val CALLBACK_SCHEME = "sezzle-sdk"

        /** Parse a callback URL and dispatch to the listener. Returns true if handled. */
        fun handleCallbackUrl(uri: Uri): Boolean {
            if (uri.scheme != CALLBACK_SCHEME) return false
            if (uri.host != "checkout") return false

            val listener = CheckoutState.listener ?: return false
            val path = uri.path?.trimStart('/')

            val mainHandler = Handler(Looper.getMainLooper())

            when (path) {
                "confirmed" -> {
                    val orderUUID = CheckoutState.orderUUID
                    CheckoutState.clear()
                    if (orderUUID != null) {
                        mainHandler.post { listener.onCheckoutComplete(orderUUID) }
                    } else {
                        mainHandler.post { listener.onCheckoutError(SezzleError.InvalidResponse) }
                    }
                }
                "cancelled" -> {
                    CheckoutState.clear()
                    mainHandler.post { listener.onCheckoutCancel() }
                }
                else -> {
                    CheckoutState.clear()
                    mainHandler.post { listener.onCheckoutError(SezzleError.InvalidResponse) }
                }
            }

            return true
        }
    }
}
