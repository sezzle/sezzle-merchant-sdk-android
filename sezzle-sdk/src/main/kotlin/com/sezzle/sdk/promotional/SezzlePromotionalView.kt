package com.sezzle.sdk.promotional

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.SpannableString
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * A drop-in view that displays Sezzle installment messaging with brand styling.
 *
 * Shows different messages based on the price:
 * - Under $50: "or 4 payments of $X with Sezzle"
 * - $50+: "or 5 payments of $X with Sezzle" (when PI5 enabled)
 * - Long-term eligible: "or monthly payments as low as $X with Sezzle"
 * - Below min or above max: hidden
 */
class SezzlePromotionalView @JvmOverloads constructor(
    context: Context,
    private var amountInCents: Int,
    private var currency: String = "USD",
    private var style: SezzlePromotionalStyle = run {
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) SezzlePromotionalStyle.DARK else SezzlePromotionalStyle.LIGHT
    },
    private var widgetConfig: SezzleWidgetConfig = SezzleWidgetConfig.DEFAULT
) : FrameLayout(context) {

    private val messageView: TextView
    private var presenterRef: WeakReference<Activity>? = null
    // Mirrors SezzleSDK's startCheckout overlap protection (1.2.1): rapid double-taps
    // on the widget used to open multiple stacked info modals. Track in-progress state
    // and reject overlapping taps until the current modal dismisses.
    private var isModalShowing = false

    init {
        messageView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            typeface = style.typeface
            setLineSpacing(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics),
                1f
            )
        }
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
        ).toInt()
        messageView.setPadding(0, padding, 0, padding)
        addView(messageView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START))

        setOnClickListener {
            if (isModalShowing) return@setOnClickListener
            presenterRef?.get()?.let { activity ->
                val type = InstallmentCalculator.widgetType(amountInCents, widgetConfig)
                isModalShowing = true
                SezzleInfoModal.present(
                    amountInCents,
                    currency,
                    activity,
                    type,
                    widgetConfig,
                    onDismiss = { isModalShowing = false },
                )
            }
        }

        render()
    }

    /** Set the activity used to present the info modal on tap. */
    fun setPresenter(activity: Activity) {
        presenterRef = WeakReference(activity)
    }

    /** Update the displayed amount. */
    fun update(amountInCents: Int) {
        this.amountInCents = amountInCents
        render()
    }

    private fun render() {
        val type = InstallmentCalculator.widgetType(amountInCents, widgetConfig)

        if (type == SezzleWidgetType.HIDDEN) {
            visibility = GONE
            return
        }

        visibility = VISIBLE
        SezzlePromoDataHandler.getMessage(context, amountInCents, currency, style, widgetConfig) { spanned ->
            messageView.text = spanned
        }
    }
}
