package com.sezzle.sdk.checkout

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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

        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL)
        orderUUID = intent.getStringExtra(EXTRA_ORDER_UUID)
        completeUrl = intent.getStringExtra(EXTRA_COMPLETE_URL)
            ?.let { Uri.parse(it) }
            ?: CheckoutHandler.DEFAULT_COMPLETE_URL
        cancelUrl = intent.getStringExtra(EXTRA_CANCEL_URL)
            ?.let { Uri.parse(it) }
            ?: CheckoutHandler.DEFAULT_CANCEL_URL

        if (checkoutUrl == null) {
            deliverResult { it.onCheckoutError(SezzleError.InvalidResponse) }
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
                deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
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

        webView.loadUrl(urlWithParam)
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (!resultDelivered) {
            deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
        }
        webView.destroy()
        super.onDestroy()
    }

    private fun deliverResult(action: (SezzleCheckoutListener) -> Unit) {
        if (resultDelivered) return
        resultDelivered = true
        val l = listener
        listener = null
        if (l != null) {
            action(l)
        }
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
                deliverResult {
                    it.onCheckoutError(SezzleError.NetworkError(
                        RuntimeException("WebView error: ${error?.description}")
                    ))
                }
                finish()
            }
        }
    }

    private fun handleCallback(uri: Uri) {
        when {
            CheckoutHandler.matches(uri, completeUrl) -> {
                val result = SezzleCheckoutResult(
                    orderUUID = orderUUID,
                    callbackURL = if (orderUUID == null) uri else null
                )
                deliverResult { it.onCheckoutComplete(result) }
            }
            CheckoutHandler.matches(uri, cancelUrl) -> {
                deliverResult { it.onCheckoutCancel() }
            }
            else -> {
                deliverResult { it.onCheckoutError(SezzleError.InvalidResponse) }
            }
        }
    }
}
