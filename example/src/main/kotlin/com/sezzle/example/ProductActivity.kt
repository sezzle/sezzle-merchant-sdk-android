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
import com.sezzle.sdk.SezzleCheckoutListener
import com.sezzle.sdk.SezzleSDK
import com.sezzle.sdk.models.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
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

    private fun createProductCard(product: Product, index: Int): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(1, Color.parseColor("#E5E5EA"))
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
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(2) })

        // Description
        card.addView(TextView(this).apply {
            text = product.description
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
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

        // Checkout button
        val button = Button(this).apply {
            text = "Pay with Sezzle"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#8333D4"))
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            minimumHeight = dp(44)
            tag = index
            setOnClickListener { startCheckout(it.tag as Int) }
        }
        card.addView(button, LinearLayout.LayoutParams(
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

    private fun startCheckout(index: Int) {
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

        SezzleSDK.startCheckout(checkout, this, this, mode = SezzleCheckoutMode.WEB_VIEW)
    }

    override fun onCheckoutComplete(orderUUID: String) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_SUCCESS)
            putExtra(ResultActivity.EXTRA_ORDER_UUID, orderUUID)
        })
    }

    override fun onCheckoutCancel() {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_CANCELLED)
        })
    }

    override fun onCheckoutError(error: SezzleError) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TYPE, ResultActivity.TYPE_ERROR)
            putExtra(ResultActivity.EXTRA_ERROR_MESSAGE, error.message)
        })
    }
}
