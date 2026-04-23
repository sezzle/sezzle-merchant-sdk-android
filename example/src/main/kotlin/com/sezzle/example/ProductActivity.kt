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
import com.sezzle.sdk.promotional.SezzlePromotionalView
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Shows a product with a Sezzle promotional message and a "Pay with Sezzle" button.
 *
 * Demonstrates how to:
 * - Embed [SezzlePromotionalView] on a product page
 * - Build a [SezzleCheckout] from product data
 * - Start the checkout flow
 */
class ProductActivity : AppCompatActivity(), SezzleCheckoutListener {

    // Sample product data
    private val productName = "Premium Wireless Headphones"
    private val productPriceInCents = 4999 // $49.99
    private val productCurrency = "USD"

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

        // Toolbar that extends behind the status bar
        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#8333D4"))
            title = "Product"
            setTitleTextColor(Color.WHITE)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setSupportActionBar(toolbar)

        // Apply window insets so toolbar gets top padding behind the status bar
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        val scrollView = ScrollView(this)
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        scrollView.addView(stack, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        // Product image placeholder
        val imageContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F2F2F7"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            minimumHeight = dp(200)
        }
        imageContainer.addView(TextView(this).apply {
            text = "\uD83C\uDFA7"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        })
        stack.addView(imageContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(200)
        ).apply { bottomMargin = dp(16) })

        // Product name
        stack.addView(TextView(this).apply {
            text = productName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Product price
        stack.addView(TextView(this).apply {
            text = formatPrice(productPriceInCents)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Sezzle promotional view
        val promoView = SezzlePromotionalView(
            context = this,
            amountInCents = productPriceInCents,
            currency = productCurrency
        )
        promoView.setPresenter(this)
        stack.addView(promoView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // Pay with Sezzle button
        val checkoutButton = Button(this).apply {
            text = "Pay with Sezzle"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#8333D4"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            minimumHeight = dp(50)
            setOnClickListener { startCheckout() }
        }
        stack.addView(checkoutButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun formatPrice(cents: Int): String {
        val dollars = cents / 100.0
        return try {
            val formatter = NumberFormat.getCurrencyInstance(Locale.US)
            formatter.currency = Currency.getInstance(productCurrency)
            formatter.format(dollars)
        } catch (_: Exception) {
            "$${String.format(Locale.US, "%.2f", dollars)}"
        }
    }

    private fun startCheckout() {
        val checkout = SezzleCheckout(
            customer = SezzleCustomer(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            ),
            order = SezzleOrder(
                referenceId = "example-order-${(1000..9999).random()}",
                description = productName,
                amount = SezzleAmount(amountInCents = productPriceInCents, currency = productCurrency),
                items = listOf(
                    SezzleItem(
                        name = productName,
                        sku = "headphones-premium-001",
                        quantity = 1,
                        price = SezzleAmount(amountInCents = productPriceInCents, currency = productCurrency)
                    )
                )
            )
        )

        SezzleSDK.startCheckout(checkout, this, this)
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
