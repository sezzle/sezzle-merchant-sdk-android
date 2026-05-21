package com.sezzle.example

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import com.sezzle.example.BuildConfig
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.SezzleCheckoutResult
import com.sezzle.sdk.SezzleSDK
import com.sezzle.sdk.checkout.SezzleCheckoutContract
import com.sezzle.sdk.models.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import android.util.Base64
import com.sezzle.sdk.promotional.SezzleLongTermConfig
import com.sezzle.sdk.promotional.SezzlePromotionalView
import com.sezzle.sdk.promotional.SezzleWidgetConfig
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Shows multiple products at different price points demonstrating all widget variants.
 */
class ProductActivity : AppCompatActivity(), SezzleCheckoutListener {

    // Widget config with LT enabled at $250+
    private val widgetConfig = SezzleWidgetConfig(
        enablePayIn5 = true,
        longTermConfig = SezzleLongTermConfig(minPriceInCents = 25_000)
    )

    data class Product(val name: String, val emoji: String, val priceInCents: Int, val description: String)

    private val products = listOf(
        Product("Phone Case", "\uD83D\uDCF1", 1500, "Below \$35 min — widget hidden"),
        Product("Wireless Earbuds", "\uD83C\uDFA7", 3999, "\$39.99 — 4 payments (under PI5 \$50 threshold)"),
        Product("Premium Headphones", "\uD83C\uDFA7", 14999, "\$149.99 — 5 payments (PI5 eligible, over \$50)"),
        Product("Smart Watch", "\u231A", 79900, "\$799 — long-term monthly payments (over \$250)"),
    )

    /**
     * Lifecycle-safe checkout result handler — works even when the activity is destroyed
     * and recreated mid-checkout (e.g. "Don't keep activities" developer option).
     *
     * Register the launcher as a field (Android requires `registerForActivityResult` to be
     * called before the activity reaches STARTED, which field-initialization satisfies).
     */
    private val sezzleCheckoutLauncher = registerForActivityResult(SezzleCheckoutContract()) { result ->
        when (result) {
            is SezzleCheckoutContract.Output.Complete -> startActivity(Intent(this, ResultActivity::class.java).apply {
                if (result.orderUuid != null) {
                    putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_SUCCESS)
                    putExtra(ResultActivity.EXTRA_ORDER_UUID, result.orderUuid)
                } else if (result.callbackUrl != null) {
                    putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_SUCCESS_CALLBACK)
                    putExtra(ResultActivity.EXTRA_CALLBACK_URL, result.callbackUrl.toString())
                } else {
                    putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
                    putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, "No identifier in result")
                }
            })
            SezzleCheckoutContract.Output.Cancel -> startActivity(Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_CANCELLED)
            })
            is SezzleCheckoutContract.Output.Error -> startActivity(Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
                putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, result.message)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private val isDarkMode: Boolean
        get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun buildUI(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val bgColor = if (isDarkMode) Color.parseColor("#120A23") else Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // Toolbar
        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#8333D4"))
            title = "Sezzle Widget Demo"
            setTitleTextColor(Color.WHITE)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        val scrollView = ScrollView(this)
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scrollView.addView(stack, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Server-driven flow demo at the top
        stack.addView(
            createServerDrivenCard(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        )

        for ((index, product) in products.withIndex()) {
            stack.addView(
                createProductCard(product, index),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(16) }
            )
        }

        return root
    }

    private fun createServerDrivenCard(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val cardBg = if (isDarkMode) Color.parseColor("#1C1230") else Color.WHITE
        val cardBorder = if (isDarkMode) Color.parseColor("#2A1F40") else Color.parseColor("#E5E5EA")
        val textPrimary = if (isDarkMode) Color.WHITE else Color.BLACK
        val textSecondary = if (isDarkMode) Color.parseColor("#AAAAAA") else Color.GRAY

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(cardBg)
                setStroke(1, cardBorder)
                cornerRadius = dp(12).toFloat()
            }
        }

        card.addView(TextView(this).apply {
            text = "Server-driven flow"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        card.addView(TextView(this).apply {
            text = "Your backend creates the session via POST /v2/session and supplies its own callback URLs. The SDK opens the URL and reports back via callbackURL. No public key needed on-device."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textSecondary)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // System Browser (Chrome Custom Tabs)
        val browserButton = Button(this).apply {
            text = "System Browser"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8333D4"))
                cornerRadius = dp(10).toFloat()
            }
            minimumHeight = dp(44)
            setOnClickListener { startServerDrivenSystemBrowserDemo() }
        }
        buttonRow.addView(browserButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { rightMargin = dp(4) })

        // WebView (in-app)
        val webViewButton = Button(this).apply {
            text = "WebView"
            setTextColor(Color.parseColor("#8333D4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#8333D4"))
                cornerRadius = dp(10).toFloat()
            }
            minimumHeight = dp(44)
            setOnClickListener { startServerDrivenWebViewDemo() }
        }
        buttonRow.addView(webViewButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { leftMargin = dp(4) })

        card.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return card
    }

    /**
     * WebView mode demo: HTTPS callback URLs (App-Links style). Any URL scheme works
     * in WebView mode — the SDK's WebView intercepts before the URL loads.
     */
    private fun startServerDrivenWebViewDemo() {
        val orderRef = "poshmark-demo-${(1000..9999).random()}"
        val completeUrl = Uri.parse("https://example.com/sezzle-checkout/done?orderRef=$orderRef")
        val cancelUrl = Uri.parse("https://example.com/sezzle-checkout/cancelled")
        runServerDrivenDemo(orderRef, completeUrl, cancelUrl, SezzleCheckoutMode.WEB_VIEW)
    }

    /**
     * System Browser mode demo: custom-scheme callback URLs.
     * The example app's AndroidManifest.xml registers an intent-filter for
     * `sezzle-example://checkout` pointing at SezzleRedirectActivity — without it,
     * Chrome Custom Tabs wouldn't route the redirect back into the app.
     */
    private fun startServerDrivenSystemBrowserDemo() {
        val orderRef = "poshmark-demo-${(1000..9999).random()}"
        val completeUrl = Uri.parse("sezzle-example://checkout/done?orderRef=$orderRef")
        val cancelUrl = Uri.parse("sezzle-example://checkout/cancelled")
        runServerDrivenDemo(orderRef, completeUrl, cancelUrl, SezzleCheckoutMode.SYSTEM_BROWSER)
    }

    /**
     * Simulates a server-driven integration: pretends to be a backend by POSTing
     * to /v2/session directly, then hands the URL to the SDK.
     *
     * **WebView mode** uses the new lifecycle-safe `startCheckoutForResult` API — the
     * result is delivered through `sezzleCheckoutLauncher` above, which is bound to the
     * Activity Result Registry and survives activity recreation.
     *
     * **System Browser mode** still uses the listener-based API (the launcher API
     * supports WebView only — see SezzleCheckoutContract docs).
     */
    private fun runServerDrivenDemo(
        orderRef: String,
        completeUrl: Uri,
        cancelUrl: Uri,
        mode: SezzleCheckoutMode
    ) {
        thread {
            try {
                val checkoutUrl = createSandboxSession(completeUrl, cancelUrl, orderRef)
                runOnUiThread {
                    if (mode == SezzleCheckoutMode.WEB_VIEW) {
                        SezzleSDK.startCheckoutForResult(
                            launcher = sezzleCheckoutLauncher,
                            checkoutUrl = checkoutUrl,
                            completeUrl = completeUrl,
                            cancelUrl = cancelUrl,
                            onError = { onCheckoutError(it) },
                        )
                    } else {
                        SezzleSDK.startCheckout(
                            checkoutUrl = checkoutUrl,
                            completeUrl = completeUrl,
                            cancelUrl = cancelUrl,
                            activity = this,
                            listener = this,
                            mode = mode
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    startActivity(Intent(this, ResultActivity::class.java).apply {
                        putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
                        putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, "Failed to create session: ${e.message}")
                    })
                }
            }
        }
    }

    private fun createSandboxSession(completeUrl: Uri, cancelUrl: Uri, referenceId: String): String {
        val url = URL("https://sandbox.gateway.sezzle.com/v2/session")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        val auth = Base64.encodeToString(
            BuildConfig.SEZZLE_PUBLIC_KEY.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        conn.setRequestProperty("Authorization", "Basic $auth")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("complete_url", JSONObject().apply {
                put("href", completeUrl.toString())
                put("method", "GET")
            })
            put("cancel_url", JSONObject().apply {
                put("href", cancelUrl.toString())
                put("method", "GET")
            })
            put("customer", JSONObject().apply {
                put("email", "demo@example.com")
                put("first_name", "Demo")
                put("last_name", "User")
            })
            put("order", JSONObject().apply {
                put("intent", "AUTH")
                put("reference_id", referenceId)
                put("description", "Server-driven demo")
                put("order_amount", JSONObject().apply {
                    put("amount_in_cents", 4999)
                    put("currency", "USD")
                })
            })
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw RuntimeException("Session creation failed ($responseCode): $responseText")
        }
        val json = JSONObject(responseText)
        return json.getJSONObject("order").getString("checkout_url")
    }

    private fun createProductCard(product: Product, index: Int): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val cardBg = if (isDarkMode) Color.parseColor("#1C1230") else Color.WHITE
        val cardBorder = if (isDarkMode) Color.parseColor("#2A1F40") else Color.parseColor("#E5E5EA")
        val textPrimary = if (isDarkMode) Color.WHITE else Color.BLACK
        val textSecondary = if (isDarkMode) Color.parseColor("#AAAAAA") else Color.GRAY

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val bg = GradientDrawable().apply {
                setColor(cardBg)
                setStroke(1, cardBorder)
                cornerRadius = dp(12).toFloat()
            }
            background = bg
        }

        // Emoji + name
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nameRow.addView(TextView(this).apply {
            text = product.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
        nameRow.addView(TextView(this).apply {
            text = product.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        })
        card.addView(nameRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        // Price
        card.addView(TextView(this).apply {
            text = formatPrice(product.priceInCents)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(2) })

        // Description
        card.addView(TextView(this).apply {
            text = product.description
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textSecondary)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Promo view
        val promoView = SezzlePromotionalView(
            context = this,
            amountInCents = product.priceInCents,
            widgetConfig = widgetConfig
        )
        promoView.setPresenter(this)
        card.addView(promoView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Checkout buttons row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // System Browser button
        val browserButton = Button(this).apply {
            text = "System Browser"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#8333D4"))
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            minimumHeight = dp(44)
            setOnClickListener { startCheckout(index, SezzleCheckoutMode.SYSTEM_BROWSER) }
        }
        buttonRow.addView(browserButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { rightMargin = dp(4) })

        // WebView button
        val webViewButton = Button(this).apply {
            text = "WebView"
            setTextColor(Color.parseColor("#8333D4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#8333D4"))
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            minimumHeight = dp(44)
            setOnClickListener { startCheckout(index, SezzleCheckoutMode.WEB_VIEW) }
        }
        buttonRow.addView(webViewButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { leftMargin = dp(4) })

        card.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return card
    }

    private fun formatPrice(cents: Int): String {
        val dollars = cents / 100.0
        return try {
            val formatter = NumberFormat.getCurrencyInstance(Locale.US)
            formatter.currency = Currency.getInstance("USD")
            formatter.format(dollars)
        } catch (_: Exception) {
            "$${String.format(Locale.US, "%.2f", dollars)}"
        }
    }

    private fun startCheckout(index: Int, mode: SezzleCheckoutMode) {
        val product = products[index]
        val checkout = SezzleCheckout(
            customer = SezzleCustomer(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            ),
            order = SezzleOrder(
                referenceId = "example-order-${(1000..9999).random()}",
                description = product.name,
                amount = SezzleAmount(amountInCents = product.priceInCents, currency = "USD"),
                items = listOf(
                    SezzleItem(
                        name = product.name,
                        sku = "demo-$index",
                        quantity = 1,
                        price = SezzleAmount(amountInCents = product.priceInCents, currency = "USD")
                    )
                )
            )
        )

        // WebView mode uses the lifecycle-safe launcher path (handles activity recreation
        // mid-checkout — "Don't keep activities", low-memory, etc.). System-browser mode
        // continues to use the listener-based API.
        if (mode == SezzleCheckoutMode.WEB_VIEW) {
            SezzleSDK.startCheckoutForResult(
                launcher = sezzleCheckoutLauncher,
                checkout = checkout,
                onError = { onCheckoutError(it) },
            )
        } else {
            SezzleSDK.startCheckout(checkout, this, this, mode = mode)
        }
    }

    override fun onCheckoutComplete(result: SezzleCheckoutResult) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            if (result.orderUUID != null) {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_SUCCESS)
                putExtra(ResultActivity.EXTRA_ORDER_UUID, result.orderUUID)
            } else if (result.callbackURL != null) {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_SUCCESS_CALLBACK)
                putExtra(ResultActivity.EXTRA_CALLBACK_URL, result.callbackURL.toString())
            } else {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
                putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, "No identifier in result")
            }
        })
    }

    override fun onCheckoutCancel() {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_CANCELLED)
        })
    }

    override fun onCheckoutError(error: SezzleError) {
        // The listener-based API surfaces user-dismissal (X tap, back press, Custom Tabs
        // close) as SezzleError.BrowserDismissed for historical reasons — that's not really
        // an "error" from the user's perspective. Route it to the Cancelled screen so the
        // System Browser flow's dismissal UX matches the WebView launcher path's, which
        // already routes dismissal to Output.Cancel.
        if (error is SezzleError.BrowserDismissed) {
            startActivity(Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_CANCELLED)
            })
            return
        }
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
            putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, error.message)
        })
    }
}
