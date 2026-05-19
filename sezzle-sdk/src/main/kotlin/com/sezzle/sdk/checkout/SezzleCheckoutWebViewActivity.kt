package com.sezzle.sdk.checkout

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
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

        // OAuth provider hosts whose `window.open` popups must stay in-app so the
        // popup's JS can postMessage back to the opener (Apple Sign-In's web_message
        // response_mode requires this). Any other popup falls through to the system
        // browser (preserves original behavior for TILA documents, marketplace links).
        private val AUTH_POPUP_HOSTS = setOf(
            "appleid.apple.com",
            "accounts.google.com",
            "www.facebook.com",
            "graph.facebook.com",
        )
    }

    private var authPopup: Dialog? = null

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

        // Close button — sized to Material's 48dp minimum tap target so it stays
        // comfortable to hit on top of small-glyph perception (MOBILE-7956).
        val closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#333333"))
            textSize = 22f
            // Padding inflates the touch target — visual glyph is the textSize, but
            // hit area becomes ~48x48dp.
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
                finish()
            }
            contentDescription = "Close checkout"
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
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

                    @SuppressLint("SetJavaScriptEnabled")
                    val popupWebView = WebView(this@SezzleCheckoutWebViewActivity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(true)
                    }

                    // First-URL-load decision: auth popup (stay in-app) vs doc popup (external).
                    popupWebView.webViewClient = object : WebViewClient() {
                        private var routed = false

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (!routed) {
                                routed = true
                                if (isAuthPopupHost(url)) {
                                    showAuthPopup(popupWebView)
                                    return false // popupWebView loads the URL itself
                                }
                                startActivity(Intent(Intent.ACTION_VIEW, url))
                                popupWebView.destroy()
                                return true
                            }
                            // Subsequent navigations within an already-shown auth popup
                            return false
                        }
                    }

                    transport.webView = popupWebView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    // Apple Sign-In calls `window.close()` on the popup after posting the
                    // auth result back to the opener. Dismiss our overlay.
                    dismissAuthPopup()
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
            deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        dismissAuthPopup()
        if (!resultDelivered) {
            deliverResult { it.onCheckoutError(SezzleError.BrowserDismissed) }
        }
        webView.destroy()
        super.onDestroy()
    }

    private fun isAuthPopupHost(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return AUTH_POPUP_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun showAuthPopup(popupWebView: WebView) {
        authPopup?.dismiss() // Shouldn't happen, but be safe
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val header = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#333333"))
            textSize = 20f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { authPopup?.dismiss() }
        }
        header.addView(close, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END
        ))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(popupWebView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sb.top, 0, sb.bottom)
            insets
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(root)
            setOnDismissListener {
                (popupWebView.parent as? ViewGroup)?.removeView(popupWebView)
                popupWebView.destroy()
                authPopup = null
            }
        }
        // Inherit FLAG_SECURE from the main checkout window so the auth popup is also
        // screenshot-blocked.
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        authPopup = dialog
        dialog.show()
    }

    private fun dismissAuthPopup() {
        authPopup?.dismiss()
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
