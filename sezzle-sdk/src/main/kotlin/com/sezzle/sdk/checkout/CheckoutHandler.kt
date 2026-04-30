package com.sezzle.sdk.checkout

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleCheckoutMode
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionServiceProtocol
import com.sezzle.sdk.networking.SezzleEventLogger

internal class CheckoutHandler(
    private val sessionService: SessionServiceProtocol,
    private val eventLogger: SezzleEventLogger? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionUUID: String = ""
    private var checkoutUUID: String = ""
    private var checkoutMode: String = ""

    companion object {
        const val CALLBACK_SCHEME = "sezzle-sdk"

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
        listener: SezzleCheckoutListener,
        mode: SezzleCheckoutMode
    ) {
        checkoutMode = if (mode == SezzleCheckoutMode.WEB_VIEW) "webview" else "system_browser"
        eventLogger?.log(event = SezzleEventLogger.Event.POPUP_CREATED, mode = checkoutMode, message = "checkout initiated")

        // Wrap listener to add event logging
        val loggingListener = object : SezzleCheckoutListener {
            override fun onCheckoutComplete(orderUUID: String) {
                eventLogger?.log(event = SezzleEventLogger.Event.SUCCESS, sessionUUID = sessionUUID, orderUUID = orderUUID, checkoutUUID = checkoutUUID, mode = checkoutMode)
                listener.onCheckoutComplete(orderUUID)
            }
            override fun onCheckoutCancel() {
                eventLogger?.log(event = SezzleEventLogger.Event.CANCEL, sessionUUID = sessionUUID, checkoutUUID = checkoutUUID, mode = checkoutMode)
                listener.onCheckoutCancel()
            }
            override fun onCheckoutError(error: SezzleError) {
                eventLogger?.log(event = SezzleEventLogger.Event.FAILURE, sessionUUID = sessionUUID, checkoutUUID = checkoutUUID, mode = checkoutMode, message = error.message)
                listener.onCheckoutError(error)
            }
        }

        sessionService.createSession(
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
                    val orderUUID = response.orderUUID
                    // Append isWebView=true for all modes
                    val checkoutUri = Uri.parse(response.checkoutURL).buildUpon()
                        .appendQueryParameter("isWebView", "true")
                        .build()

                    when (mode) {
                        SezzleCheckoutMode.WEB_VIEW -> {
                            launchWebView(activity, checkoutUri.toString(), orderUUID, loggingListener)
                        }
                        SezzleCheckoutMode.SYSTEM_BROWSER -> {
                            if (isAuthTabSupported(activity)) {
                                launchAuthTab(activity, checkoutUri, orderUUID, loggingListener)
                            } else {
                                launchCustomTab(activity, checkoutUri, orderUUID, loggingListener)
                            }
                        }
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    loggingListener.onCheckoutError(error)
                }
            }
        )
    }

    private fun isAuthTabSupported(activity: ComponentActivity): Boolean {
        return try {
            val pm = activity.packageManager
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

    private fun launchCustomTab(
        activity: ComponentActivity,
        checkoutUri: Uri,
        orderUUID: String,
        listener: SezzleCheckoutListener
    ) {
        CheckoutState.listener = listener
        CheckoutState.orderUUID = orderUUID
        CheckoutState.launchingActivityClassName = activity.javaClass.name
        val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabsIntent.launchUrl(activity, checkoutUri)
    }

    private fun launchWebView(
        activity: ComponentActivity,
        checkoutUrl: String,
        orderUUID: String,
        listener: SezzleCheckoutListener
    ) {
        SezzleCheckoutWebViewActivity.listener = listener
        val intent = Intent(activity, SezzleCheckoutWebViewActivity::class.java).apply {
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_CHECKOUT_URL, checkoutUrl)
            putExtra(SezzleCheckoutWebViewActivity.EXTRA_ORDER_UUID, orderUUID)
        }
        activity.startActivity(intent)
    }
}
