package com.sezzle.sdk.promotional

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * An educational modal that explains how Sezzle works.
 *
 * Shows different content based on widget type:
 * - PI4: 4-payment biweekly breakdown
 * - PI5: 5-payment biweekly breakdown
 * - Long-term: Monthly payment options with APR terms
 */
object SezzleInfoModal {

    fun present(
        amountInCents: Int,
        currency: String = "USD",
        activity: Activity,
        widgetType: SezzleWidgetType? = null,
        widgetConfig: SezzleWidgetConfig = SezzleWidgetConfig.DEFAULT
    ) {
        val type = widgetType ?: InstallmentCalculator.widgetType(amountInCents, widgetConfig)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(buildContent(activity, amountInCents, currency, type, widgetConfig))
        dialog.show()
    }

    private fun buildContent(
        activity: Activity,
        amountInCents: Int,
        currency: String,
        widgetType: SezzleWidgetType,
        widgetConfig: SezzleWidgetConfig
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(16), dp(24), dp(24))
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            background = bg
        }

        // Grab handle
        val handle = View(activity).apply {
            val handleBg = GradientDrawable().apply {
                setColor(Color.parseColor("#DDDDDD"))
                cornerRadius = dp(2).toFloat()
            }
            background = handleBg
        }
        container.addView(handle, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })

        val scrollView = ScrollView(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Sezzle logo
        content.addView(TextView(activity).apply {
            text = "\u2726 sezzle"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(SezzleBrand.DARK_PURPLE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        when (widgetType) {
            SezzleWidgetType.PI4, SezzleWidgetType.PI5 ->
                buildShortTermContent(activity, content, amountInCents, currency, widgetType, ::dp)
            SezzleWidgetType.LONG_TERM ->
                buildLongTermContent(activity, content, amountInCents, currency, widgetConfig, ::dp)
            SezzleWidgetType.HIDDEN -> {}
        }

        scrollView.addView(content)
        container.addView(scrollView)
        return container
    }

    private fun buildShortTermContent(
        activity: Activity,
        content: LinearLayout,
        amountInCents: Int,
        currency: String,
        widgetType: SezzleWidgetType,
        dp: (Int) -> Int
    ) {
        val numPayments = InstallmentCalculator.numberOfPayments(widgetType)
        val density = activity.resources.displayMetrics.density

        // Title
        content.addView(TextView(activity).apply {
            text = "$numPayments easy payments"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(SezzleBrand.DARK_PURPLE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Subtitle
        content.addView(TextView(activity).apply {
            text = "Split your purchase into $numPayments payments,\nevery 2 weeks. No hidden fees."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // Payment schedule card
        content.addView(
            buildScheduleCard(activity, amountInCents, currency, numPayments),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        )

        // Footer
        content.addView(TextView(activity).apply {
            text = "No hidden fees \u00B7 No impact to your credit score"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
        })
    }

    private fun buildScheduleCard(
        activity: Activity,
        amountInCents: Int,
        currency: String,
        numberOfPayments: Int
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val installments = InstallmentCalculator.installments(amountInCents, numberOfPayments)
        val dates = InstallmentCalculator.paymentDates(numberOfPayments)
        val dateFormat = SimpleDateFormat("MMM d", Locale.US)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(16), dp(8), dp(16))
            val bg = GradientDrawable().apply {
                setColor(SezzleBrand.LIGHT_PURPLE_BG)
                cornerRadius = dp(12).toFloat()
            }
            background = bg
        }

        for ((index, pair) in installments.zip(dates).withIndex()) {
            val (amount, date) = pair
            val formatted = InstallmentCalculator.formatCents(amount, currency)
            val dateString = if (index == 0) "Today" else dateFormat.format(date)

            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val pieSize = if (numberOfPayments <= 4) dp(36) else dp(28)
            column.addView(
                SezzleBrand.pieChartView(activity, index + 1, numberOfPayments, pieSize),
                LinearLayout.LayoutParams(pieSize, pieSize).apply { bottomMargin = dp(4) }
            )

            column.addView(TextView(activity).apply {
                text = formatted
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (numberOfPayments <= 4) 13f else 11f)
                setTextColor(SezzleBrand.PURPLE)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) })

            column.addView(TextView(activity).apply {
                text = dateString
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(if (index == 0) SezzleBrand.GREEN else SezzleBrand.GRAY)
                gravity = Gravity.CENTER
            })

            card.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        return card
    }

    private fun buildLongTermContent(
        activity: Activity,
        content: LinearLayout,
        amountInCents: Int,
        currency: String,
        widgetConfig: SezzleWidgetConfig,
        dp: (Int) -> Int
    ) {
        val ltConfig = widgetConfig.longTermConfig ?: return
        val density = activity.resources.displayMetrics.density

        // Title
        content.addView(TextView(activity).apply {
            text = "Flexible monthly payments"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(SezzleBrand.DARK_PURPLE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Subtitle
        content.addView(TextView(activity).apply {
            text = "Choose a payment plan that works for you."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Price header
        content.addView(TextView(activity).apply {
            text = "Sample payments for ${InstallmentCalculator.formatCents(amountInCents, currency)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(SezzleBrand.DARK_PURPLE)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Option cards
        val options = InstallmentCalculator.longTermOptions(amountInCents, ltConfig)
        for (option in options) {
            content.addView(
                buildLongTermOptionCard(activity, option, currency),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            )
        }

        // APR disclosure
        content.addView(TextView(activity).apply {
            text = "Rates from ${ltConfig.minAPR}% \u2013 ${ltConfig.maxAPR}% APR. Subject to credit approval."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun buildLongTermOptionCard(activity: Activity, option: LongTermOption, currency: String): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val bg = GradientDrawable().apply {
                setColor(SezzleBrand.LIGHT_PURPLE_BG)
                cornerRadius = dp(10).toFloat()
            }
            background = bg
        }

        // Left: term + APR
        val termLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        termLayout.addView(TextView(activity).apply {
            text = "${option.months} months"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(SezzleBrand.DARK_PURPLE)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        })
        termLayout.addView(TextView(activity).apply {
            text = if (option.apr > 0) "${String.format("%.2f", option.apr)}% APR" else "0% APR"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(SezzleBrand.GRAY)
        })
        card.addView(termLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Right: monthly amount
        val paymentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        paymentLayout.addView(TextView(activity).apply {
            text = InstallmentCalculator.formatDollars(option.monthlyPayment, currency)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(SezzleBrand.PURPLE)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        })
        paymentLayout.addView(TextView(activity).apply {
            text = "per month"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.END
        })
        card.addView(paymentLayout)

        return card
    }
}
