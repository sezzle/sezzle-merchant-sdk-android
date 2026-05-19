package com.sezzle.sdk.promotional

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sezzle.sdk.R
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
        widgetConfig: SezzleWidgetConfig = SezzleWidgetConfig.DEFAULT,
        onDismiss: () -> Unit = {}
    ) {
        val type = widgetType ?: InstallmentCalculator.widgetType(amountInCents, widgetConfig)
        val isDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val modalBg = if (isDark) SezzleBrand.MODAL_BG_DARK_MODE else SezzleBrand.MODAL_BG

        val dialog = BottomSheetDialog(activity)
        dialog.setOnDismissListener { onDismiss() }
        dialog.setContentView(buildContent(activity, amountInCents, currency, type, widgetConfig, onClose = { dialog.dismiss() }))
        // Replace default background with matching modal color to fix corner artifacts
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val density = activity.resources.displayMetrics.density
            val r = (16 * density)
            bottomSheet?.background = GradientDrawable().apply {
                setColor(modalBg)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        dialog.show()
    }

    private fun buildContent(
        activity: Activity,
        amountInCents: Int,
        currency: String,
        widgetType: SezzleWidgetType,
        widgetConfig: SezzleWidgetConfig,
        onClose: () -> Unit
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val isDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val modalBg = if (isDark) SezzleBrand.MODAL_BG_DARK_MODE else SezzleBrand.MODAL_BG
        val textPrimary = if (isDark) SezzleBrand.DARK_PURPLE_DARK_MODE else SezzleBrand.DARK_PURPLE
        val textSecondary = if (isDark) SezzleBrand.GRAY_DARK_MODE else SezzleBrand.GRAY
        val cardBg = if (isDark) SezzleBrand.LIGHT_PURPLE_BG_DARK_MODE else SezzleBrand.LIGHT_PURPLE_BG
        val handleColor = if (isDark) SezzleBrand.HANDLE_DARK_MODE else SezzleBrand.HANDLE

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
            val bg = GradientDrawable().apply {
                setColor(modalBg)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            background = bg
        }

        // Top row: drag handle (centered) + close X (top-right). Matches the iOS modal's
        // close affordance (SezzleInfoModal.swift navigationItem.leftBarButtonItem). Tap-out
        // and swipe-down still work — this is an unambiguous additional dismiss path.
        val topRow = FrameLayout(activity)
        val handle = View(activity).apply {
            val handleBg = GradientDrawable().apply {
                setColor(handleColor)
                cornerRadius = dp(2).toFloat()
            }
            background = handleBg
        }
        topRow.addView(handle, FrameLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = dp(6)
        })
        val closeButton = TextView(activity).apply {
            text = "✕"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            // Inflate hit area to ~48dp without enlarging the glyph
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { onClose() }
            contentDescription = "Close"
        }
        topRow.addView(closeButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ))
        container.addView(topRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(16)
        })

        val scrollView = ScrollView(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Sezzle logo
        val logoResId = if (isDark) R.drawable.sezzle_logo_dark else R.drawable.sezzle_logo
        val logoBitmap = android.graphics.BitmapFactory.decodeResource(activity.resources, logoResId)
        if (logoBitmap != null) {
            val logoView = android.widget.ImageView(activity).apply {
                setImageBitmap(logoBitmap)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            val logoHeight = dp(28)
            val logoWidth = (logoHeight * (logoBitmap.width.toFloat() / logoBitmap.height)).toInt()
            content.addView(logoView, LinearLayout.LayoutParams(logoWidth, logoHeight).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            })
        }

        when (widgetType) {
            SezzleWidgetType.PI4, SezzleWidgetType.PI5 ->
                buildShortTermContent(activity, content, amountInCents, currency, widgetType, ::dp, textPrimary, textSecondary, cardBg)
            SezzleWidgetType.LONG_TERM ->
                buildLongTermContent(activity, content, amountInCents, currency, widgetConfig, ::dp, textPrimary, textSecondary, cardBg)
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
        dp: (Int) -> Int,
        textPrimary: Int,
        textSecondary: Int,
        cardBg: Int
    ) {
        val numPayments = InstallmentCalculator.numberOfPayments(widgetType)
        val density = activity.resources.displayMetrics.density

        // Title
        content.addView(TextView(activity).apply {
            text = "$numPayments easy payments"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(textPrimary)
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
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // Payment schedule card
        content.addView(
            buildScheduleCard(activity, amountInCents, currency, numPayments, cardBg, textSecondary),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        )

        // Footer
        content.addView(TextView(activity).apply {
            text = "No hidden fees \u00B7 No impact to your credit score"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
        })
    }

    private fun buildScheduleCard(
        activity: Activity,
        amountInCents: Int,
        currency: String,
        numberOfPayments: Int,
        cardBgColor: Int = SezzleBrand.LIGHT_PURPLE_BG,
        dateColor: Int = SezzleBrand.GRAY
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
                setColor(cardBgColor)
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
                setTextColor(if (index == 0) SezzleBrand.GREEN else dateColor)
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
        dp: (Int) -> Int,
        textPrimary: Int,
        textSecondary: Int,
        cardBg: Int
    ) {
        val ltConfig = widgetConfig.longTermConfig ?: return
        val density = activity.resources.displayMetrics.density

        // Title
        content.addView(TextView(activity).apply {
            text = "Flexible monthly payments"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(textPrimary)
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
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Price header
        content.addView(TextView(activity).apply {
            text = "Sample payments for ${InstallmentCalculator.formatCents(amountInCents, currency)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(textPrimary)
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
                buildLongTermOptionCard(activity, option, currency, textPrimary, textSecondary, cardBg),
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
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun buildLongTermOptionCard(activity: Activity, option: LongTermOption, currency: String, textPrimary: Int = SezzleBrand.DARK_PURPLE, textSecondary: Int = SezzleBrand.GRAY, cardBgColor: Int = SezzleBrand.LIGHT_PURPLE_BG): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val bg = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dp(10).toFloat()
            }
            background = bg
        }

        // Left: term + APR
        val termLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        termLayout.addView(TextView(activity).apply {
            text = "${option.months} months"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(textPrimary)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        })
        termLayout.addView(TextView(activity).apply {
            text = if (option.apr > 0) "${String.format("%.2f", option.apr)}% APR" else "0% APR"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textSecondary)
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
            setTextColor(textSecondary)
            gravity = Gravity.END
        })
        card.addView(paymentLayout)

        return card
    }
}
