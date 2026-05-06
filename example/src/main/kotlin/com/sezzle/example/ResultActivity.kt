package com.sezzle.example

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

/** Shows the checkout result: success (with orderUUID), cancelled, or error. */
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "result_type"
        const val EXTRA_ORDER_UUID = "order_uuid"
        const val EXTRA_CALLBACK_URL = "callback_url"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val TYPE_SUCCESS = "success"
        const val TYPE_SUCCESS_CALLBACK = "success_callback"
        const val TYPE_CANCELLED = "cancelled"
        const val TYPE_ERROR = "error"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_ERROR
        setContentView(buildUI(type))
    }

    private fun buildUI(type: String): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Toolbar
        val toolbarTitle = when (type) {
            TYPE_SUCCESS, TYPE_SUCCESS_CALLBACK -> "Checkout Complete"
            TYPE_CANCELLED -> "Checkout Cancelled"
            else -> "Checkout Error"
        }
        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#8333D4"))
            title = toolbarTitle
            setTitleTextColor(Color.WHITE)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        val scrollView = ScrollView(this)
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
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

        when (type) {
            TYPE_SUCCESS -> buildSuccess(stack)
            TYPE_SUCCESS_CALLBACK -> buildSuccessCallback(stack)
            TYPE_CANCELLED -> buildCancelled(stack)
            TYPE_ERROR -> buildError(stack)
        }

        // Back button
        val backButton = Button(this).apply {
            text = "Back to Product"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        stack.addView(backButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (24 * density).toInt() })

        return root
    }

    private fun buildSuccess(stack: LinearLayout) {
        val density = resources.displayMetrics.density

        stack.addView(TextView(this).apply {
            text = "\u2705"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Checkout Complete!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00B874"))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * density).toInt() })

        val orderUUID = intent.getStringExtra(EXTRA_ORDER_UUID) ?: "N/A"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F2F2F7"))
                cornerRadius = 12 * density
            }
            background = bg
        }

        card.addView(TextView(this).apply {
            text = "Order UUID"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (4 * density).toInt() })

        card.addView(TextView(this).apply {
            text = orderUUID
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })

        stack.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Send this UUID to your backend to capture the payment via POST /v2/order/{uuid}/capture"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
    }

    private fun buildSuccessCallback(stack: LinearLayout) {
        val density = resources.displayMetrics.density
        val callbackUrl = intent.getStringExtra(EXTRA_CALLBACK_URL) ?: "N/A"
        val parsed = android.net.Uri.parse(callbackUrl)
        val params = parsed.queryParameterNames.joinToString("\n") { name ->
            "$name = ${parsed.getQueryParameter(name)}"
        }.ifEmpty { "(none)" }

        stack.addView(TextView(this).apply {
            text = "✅"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Server-Driven Checkout Complete!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00B874"))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * density).toInt() })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F2F2F7"))
                cornerRadius = 12 * density
            }
            background = bg
        }

        card.addView(TextView(this).apply {
            text = "Callback URL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (4 * density).toInt() })

        card.addView(TextView(this).apply {
            text = callbackUrl
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * density).toInt() })

        card.addView(TextView(this).apply {
            text = "Query Parameters"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (4 * density).toInt() })

        card.addView(TextView(this).apply {
            text = params
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })

        stack.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Look up the order in your backend (you encoded its ID in your complete_url) and call POST /v2/order/{uuid}/capture."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
    }

    private fun buildCancelled(stack: LinearLayout) {
        val density = resources.displayMetrics.density

        stack.addView(TextView(this).apply {
            text = "\u274C"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Checkout Cancelled"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (8 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "The customer cancelled the checkout."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
    }

    private fun buildError(stack: LinearLayout) {
        val density = resources.displayMetrics.density
        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Unknown error"

        stack.addView(TextView(this).apply {
            text = "\u26A0\uFE0F"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        stack.addView(TextView(this).apply {
            text = "Checkout Failed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF3B30"))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * density).toInt() })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF0F0"))
                cornerRadius = 12 * density
            }
            background = bg
        }

        card.addView(TextView(this).apply {
            text = "Error"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#FF3B30"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (4 * density).toInt() })

        card.addView(TextView(this).apply {
            text = errorMessage
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextIsSelectable(true)
        })

        stack.addView(card)
    }
}
