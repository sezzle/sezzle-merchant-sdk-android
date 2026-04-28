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
        internal var listener: SezzleCheckoutListener? = null
    }

    private var resultDelivered = false
    private var orderUUID: String = ""
    private lateinit var webView: WebView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var backButton: View

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

        // Append isWebView=true so sezzle-checkout hides its own header
        val urlWithParam = appendWebViewParam(checkoutUrl)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Header — white background, "sezzle.com" title, close button
        val header = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val title = TextView(this).apply {
            text = "sezzle.com"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 16f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        header.addView(title, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        // Back button (chevron left) — hidden until there's a page to go back to
        val backIcon = android.widget.ImageView(this).apply {
            val size = dp(24)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#333333")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            val path = android.graphics.Path().apply {
                moveTo(size * 0.6f, size * 0.2f)
                lineTo(size * 0.3f, size * 0.5f)
                lineTo(size * 0.6f, size * 0.8f)
            }
            canvas.drawPath(path, paint)
            setImageBitmap(bitmap)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            visibility = View.GONE
            setOnClickListener {
                if (webView.canGoBack()) webView.goBack()
            }
        }
        backButton = backIcon
        header.addView(backIcon, FrameLayout.LayoutParams(
            dp(32), dp(32),
            Gravity.CENTER_VERTICAL or Gravity.START
        ))

        // Close button (X)
        val closeButton = TextView(this).apply {
            text = "\u2715"
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

        // Separator line
        val separator = View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E5EA"))
        }
        root.addView(separator, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (0.5f * density).toInt()
        ))

        // Content area with WebView + centered loading spinner
        val content = FrameLayout(this)

        // WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = SezzleWebViewClient()
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
            0,
            1f
        ))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        webView.loadUrl(urlWithParam)
    }

    private fun appendWebViewParam(url: String): String {
        val uri = Uri.parse(url)
        return uri.buildUpon()
            .appendQueryParameter("isWebView", "true")
            .build()
            .toString()
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

        // Deprecated but catches JS-based redirects (window.location.href)
        // that the newer WebResourceRequest version may miss
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url != null && url.startsWith("${CheckoutHandler.CALLBACK_SCHEME}://")) {
                handleCallback(Uri.parse(url))
                finish()
                return true
            }
            @Suppress("DEPRECATION")
            return super.shouldOverrideUrlLoading(view, url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            loadingSpinner.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            loadingSpinner.visibility = View.GONE
            backButton.visibility = if (view?.canGoBack() == true) View.VISIBLE else View.GONE
            // Also check if the page ended up at our callback URL
            if (url != null && url.startsWith("${CheckoutHandler.CALLBACK_SCHEME}://")) {
                handleCallback(Uri.parse(url))
                finish()
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // Check if this is our sezzle-sdk:// redirect that the WebView couldn't load
            val url = request?.url
            if (url != null && url.scheme == CheckoutHandler.CALLBACK_SCHEME) {
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
