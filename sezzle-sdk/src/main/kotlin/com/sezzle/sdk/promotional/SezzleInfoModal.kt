package com.sezzle.sdk.promotional

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * An educational modal that explains how Sezzle works.
 *
 * Shows a 4-payment breakdown with pie chart progression, dates, and the Sezzle brand.
 * Automatically presented when a user taps [SezzlePromotionalView], or present manually:
 *
 * ```kotlin
 * SezzleInfoModal.present(amountInCents = 4999, activity = this)
 * ```
 */
object SezzleInfoModal {

    /**
     * Present the Sezzle info modal.
     *
     * @param amountInCents The order amount in cents.
     * @param currency ISO 4217 currency code.
     * @param activity The activity to show the dialog from.
     */
    fun present(amountInCents: Int, currency: String = "USD", activity: Activity) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent(activity, amountInCents, currency, dialog))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun buildContent(
        activity: Activity,
        amountInCents: Int,
        currency: String,
        dialog: Dialog
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(16), dp(24), dp(24))

            // Rounded top corners
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

        // Sezzle logo text
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

        // Title
        content.addView(TextView(activity).apply {
            text = "4 interest-free payments"
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
            text = "Split your purchase and pay over time.\nNo fees. No interest. No surprises."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // Payment schedule card
        content.addView(
            buildScheduleCard(activity, amountInCents, currency),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        )

        // Footer
        content.addView(TextView(activity).apply {
            text = "No interest \u00B7 No hidden fees \u00B7 No impact to your credit score"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(SezzleBrand.GRAY)
            gravity = Gravity.CENTER
        })

        scrollView.addView(content)
        container.addView(scrollView)
        return container
    }

    private fun buildScheduleCard(
        activity: Activity,
        amountInCents: Int,
        currency: String
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val installments = InstallmentCalculator.installments(amountInCents)
        val dates = InstallmentCalculator.paymentDates()
        val dateFormat = SimpleDateFormat("MMM d", Locale.US)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))

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

            // Pie chart
            val pieSize = dp(40)
            column.addView(
                SezzleBrand.pieChartView(activity, index + 1, pieSize),
                LinearLayout.LayoutParams(pieSize, pieSize).apply { bottomMargin = dp(6) }
            )

            // Amount
            column.addView(TextView(activity).apply {
                text = formatted
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(SezzleBrand.PURPLE)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) })

            // Date
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
}
