package com.sezzle.sdk.checkout

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
import com.sezzle.sdk.models.SezzleError

/**
 * Presents the Sezzle checkout in a WebView inside the app.
 *
 * Used when [SezzleCheckoutMode.WEB_VIEW][com.sezzle.sdk.models.SezzleCheckoutMode.WEB_VIEW]
 * is specified. Intercepts the `sezzle-sdk://` callback URL via [WebViewClient] to detect
 * checkout completion, cancellation, or errors.
 */
class SezzleCheckoutWebViewActivity : Activity() {

    companion object {
        internal const val EXTRA_CHECKOUT_URL = "checkout_url"
        internal const val EXTRA_ORDER_UUID = "order_uuid"

        // Static listener reference — set before launching, cleared after result
        internal var listener: SezzleCheckoutListener? = null
    }

    private var resultDelivered = false
    private var orderUUID: String = ""
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL)
        orderUUID = intent.getStringExtra(EXTRA_ORDER_UUID) ?: ""

        if (checkoutUrl == null) {
            deliverResult { it.onCheckoutError(SezzleError.InvalidResponse) }
            finish()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // Root layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Header bar
        val header = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#8333D4"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val title = TextView(this).apply {
            text = "Sezzle Checkout"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.START
        ))

        val closeButton = TextView(this).apply {
            text = "\u2715"
            setTextColor(Color.WHITE)
            textSize = 20f
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

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = android.view.View.VISIBLE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(4)
        ))

        // WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = SezzleWebViewClient()
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        webView.loadUrl(checkoutUrl)
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
        // If the activity is destroyed without delivering a result, treat as dismissed
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

    private inner class SezzleWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.scheme == CheckoutHandler.CALLBACK_SCHEME) {
                handleCallback(url)
                finish()
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            progressBar.visibility = android.view.View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progressBar.visibility = android.view.View.GONE
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // Only handle main frame errors
            if (request?.isForMainFrame == true) {
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
        if (uri.host != "checkout") {
            deliverResult { it.onCheckoutError(SezzleError.InvalidResponse) }
            return
        }

        when (uri.path?.trimStart('/')) {
            "confirmed" -> deliverResult { it.onCheckoutComplete(orderUUID) }
            "cancelled" -> deliverResult { it.onCheckoutCancel() }
            else -> deliverResult { it.onCheckoutError(SezzleError.InvalidResponse) }
        }
    }
}
