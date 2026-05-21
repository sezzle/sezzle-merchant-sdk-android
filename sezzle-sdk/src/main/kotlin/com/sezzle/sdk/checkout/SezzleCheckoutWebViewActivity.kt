package com.sezzle.sdk.checkout

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.SezzleCheckoutResult
import com.sezzle.sdk.models.SezzleError

/**
 * Presents the Sezzle checkout in a WebView inside the app.
 *
 * Used when [SezzleCheckoutMode.WEB_VIEW][com.sezzle.sdk.models.SezzleCheckoutMode.WEB_VIEW]
 * is specified. Intercepts navigation to the configured `completeUrl` / `cancelUrl`
 * via [WebViewClient] (any scheme — typically `sezzle-sdk://` for the SDK-creates-session
 * flow, or merchant-supplied for the server-driven flow).
 */
class SezzleCheckoutWebViewActivity : Activity() {

    companion object {
        internal const val EXTRA_CHECKOUT_URL = "checkout_url"
        internal const val EXTRA_ORDER_UUID = "order_uuid"
        internal const val EXTRA_COMPLETE_URL = "complete_url"
        internal const val EXTRA_CANCEL_URL = "cancel_url"
        internal var listener: SezzleCheckoutListener? = null
    }

    private var resultDelivered = false
    private var orderUUID: String? = null
    private lateinit var completeUrl: Uri
    private lateinit var cancelUrl: Uri
    private lateinit var webView: WebView
    private lateinit var loadingSpinner: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots, screen recording, and Recent-Apps thumbnails on the
        // checkout screen — the WebView hosts a payment page where the user enters
        // card/bank credentials.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL)
        orderUUID = intent.getStringExtra(EXTRA_ORDER_UUID)
        completeUrl = intent.getStringExtra(EXTRA_COMPLETE_URL)
            ?.let { Uri.parse(it) }
            ?: CheckoutHandler.DEFAULT_COMPLETE_URL
        cancelUrl = intent.getStringExtra(EXTRA_CANCEL_URL)
            ?.let { Uri.parse(it) }
            ?: CheckoutHandler.DEFAULT_CANCEL_URL

        if (checkoutUrl == null) {
            deliverResult(TerminalResult.Error(SezzleError.InvalidResponse))
            finish()
            return
        }

        // isWebView=true is already appended by CheckoutHandler
        val urlWithParam = checkoutUrl

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Header
        val header = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // Close button
        val closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#333333"))
            textSize = 18f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                deliverResult(TerminalResult.Error(SezzleError.BrowserDismissed))
                finish()
            }
        }
        header.addView(closeButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END
        ))

        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Separator
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E5EA"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (0.5f * density).toInt()
        ))

        // Content area with WebView + centered loading spinner
        val content = FrameLayout(this)

        // WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            webViewClient = SezzleWebViewClient()
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        val tempWebView = WebView(this@SezzleCheckoutWebViewActivity)
                        tempWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                startActivity(intent)
                                return true
                            }
                        }
                        transport.webView = tempWebView
                        resultMsg.sendToTarget()
                    }
                    return true
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }
        content.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Loading spinner — centered, Sezzle purple
        loadingSpinner = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#8333D4"))
        }
        content.addView(loadingSpinner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Android 14+ renders activities edge-to-edge by default, which would push our
        // close-button header behind the system status bar. Inset the root with the
        // status-bar height so the header stays tappable.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        webView.loadUrl(urlWithParam)
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            deliverResult(TerminalResult.Error(SezzleError.BrowserDismissed))
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (!resultDelivered) {
            deliverResult(TerminalResult.Error(SezzleError.BrowserDismissed))
        }
        webView.destroy()
        super.onDestroy()
    }

    /**
     * Internal model for the three terminal checkout states. Used so that
     * [deliverResult] can write to both the [SezzleCheckoutContract] activity-result
     * Intent (lifecycle-safe path) AND the legacy static [listener] (backward-compat
     * path) without the call sites having to repeat themselves.
     */
    private sealed class TerminalResult {
        data class Complete(val orderUuid: String?, val callbackUrl: Uri?) : TerminalResult()
        object Cancel : TerminalResult()
        data class Error(val error: SezzleError) : TerminalResult()
    }

    private fun deliverResult(result: TerminalResult) {
        if (resultDelivered) return
        resultDelivered = true

        // 1. Activity-result Intent — lifecycle-safe path consumed by SezzleCheckoutContract.
        //    Survives host-activity destruction (e.g. "Don't keep activities" developer option),
        //    because the merchant's ActivityResultLauncher is registered with their activity's
        //    ActivityResultRegistry, which Android re-binds on recreation.
        val resultIntent = Intent()
        when (result) {
            is TerminalResult.Complete -> {
                resultIntent.putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_COMPLETE)
                result.orderUuid?.let { resultIntent.putExtra(SezzleCheckoutContract.RESULT_ORDER_UUID_KEY, it) }
                result.callbackUrl?.let { resultIntent.putExtra(SezzleCheckoutContract.RESULT_CALLBACK_URL_KEY, it.toString()) }
            }
            TerminalResult.Cancel -> {
                resultIntent.putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_CANCEL)
            }
            is TerminalResult.Error -> {
                resultIntent.putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_ERROR)
                resultIntent.putExtra(SezzleCheckoutContract.RESULT_ERROR_CODE_KEY, errorCodeFor(result.error))
                resultIntent.putExtra(SezzleCheckoutContract.RESULT_ERROR_MESSAGE_KEY, result.error.message)
            }
        }
        setResult(RESULT_OK, resultIntent)

        // 2. Legacy listener — best-effort delivery for callers using the listener-based
        //    SezzleSDK.startCheckout overloads. If the launching activity was destroyed,
        //    this callback may reference dead state — that's the original bug. Path 1
        //    above is the recommended migration.
        val l = listener
        listener = null
        if (l != null) {
            when (result) {
                is TerminalResult.Complete -> l.onCheckoutComplete(
                    SezzleCheckoutResult(orderUUID = result.orderUuid, callbackURL = result.callbackUrl)
                )
                TerminalResult.Cancel -> l.onCheckoutCancel()
                is TerminalResult.Error -> l.onCheckoutError(result.error)
            }
        }
    }

    private fun errorCodeFor(error: SezzleError): String = when (error) {
        is SezzleError.BrowserDismissed -> SezzleCheckoutContract.ErrorCode.BROWSER_DISMISSED
        is SezzleError.InvalidResponse -> SezzleCheckoutContract.ErrorCode.INVALID_RESPONSE
        is SezzleError.NetworkError -> SezzleCheckoutContract.ErrorCode.NETWORK_ERROR
        is SezzleError.NotConfigured -> SezzleCheckoutContract.ErrorCode.NOT_CONFIGURED
        // ApiError shouldn't reach this activity (only emitted by SessionService during
        // session creation, which is upstream of WebView presentation). Map to
        // INVALID_RESPONSE for safety so the merchant still gets a meaningful code if
        // the surface ever changes.
        is SezzleError.ApiError -> SezzleCheckoutContract.ErrorCode.INVALID_RESPONSE
    }

    private fun isCallback(uri: Uri): Boolean {
        return CheckoutHandler.matches(uri, completeUrl) || CheckoutHandler.matches(uri, cancelUrl)
    }

    private inner class SezzleWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (isCallback(url)) {
                handleCallback(url)
                finish()
                return true
            }
            return false
        }

        // Deprecated but catches JS-based redirects (window.location.href)
        // that the newer WebResourceRequest version may miss
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url != null) {
                val parsed = Uri.parse(url)
                if (isCallback(parsed)) {
                    handleCallback(parsed)
                    finish()
                    return true
                }
            }
            @Suppress("DEPRECATION")
            return super.shouldOverrideUrlLoading(view, url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            loadingSpinner.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            loadingSpinner.visibility = View.GONE
            if (url != null) {
                val parsed = Uri.parse(url)
                if (isCallback(parsed)) {
                    handleCallback(parsed)
                    finish()
                }
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // The WebView fails to load custom-scheme redirects (sezzle-sdk://, poshmark://...);
            // treat those errors as the callback firing.
            val url = request?.url
            if (url != null && isCallback(url)) {
                handleCallback(url)
                finish()
                return
            }
            if (request?.isForMainFrame == true) {
                loadingSpinner.visibility = View.GONE
                deliverResult(
                    TerminalResult.Error(
                        SezzleError.NetworkError(
                            RuntimeException("WebView error: ${error?.description}")
                        )
                    )
                )
                finish()
            }
        }
    }

    private fun handleCallback(uri: Uri) {
        when {
            CheckoutHandler.matches(uri, completeUrl) -> {
                deliverResult(
                    TerminalResult.Complete(
                        orderUuid = orderUUID,
                        callbackUrl = if (orderUUID == null) uri else null,
                    )
                )
            }
            CheckoutHandler.matches(uri, cancelUrl) -> {
                deliverResult(TerminalResult.Cancel)
            }
            else -> {
                deliverResult(TerminalResult.Error(SezzleError.InvalidResponse))
            }
        }
    }
}
