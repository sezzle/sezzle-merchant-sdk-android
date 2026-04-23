package com.sezzle.sdk.promotional

import android.app.Activity
import android.content.Context
import android.text.SpannableString
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * A drop-in view that displays Sezzle installment messaging with brand styling.
 *
 * Place this on product pages, cart pages, or anywhere you want to show
 * "or 4 interest-free payments of $X.XX with Sezzle". Tapping the view
 * opens [SezzleInfoModal] automatically.
 *
 * ```kotlin
 * val promoView = SezzlePromotionalView(context, amountInCents = 4999)
 * promoView.setPresenter(activity)
 * linearLayout.addView(promoView)
 * ```
 */
class SezzlePromotionalView(
    context: Context,
    private var amountInCents: Int,
    private var currency: String = "USD",
    private var style: SezzlePromotionalStyle = SezzlePromotionalStyle.LIGHT
) : FrameLayout(context) {

    private val messageView: TextView
    private var presenterRef: WeakReference<Activity>? = null

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
            presenterRef?.get()?.let { activity ->
                SezzleInfoModal.present(amountInCents, currency, activity)
            }
        }

        render()
    }

    /** Set the activity used to present the info modal on tap. */
    fun setPresenter(activity: Activity) {
        presenterRef = WeakReference(activity)
    }

    /**
     * Update the displayed amount.
     *
     * Call this when the cart total or product price changes.
     * The view auto-hides if the amount falls outside the eligible range.
     */
    fun update(amountInCents: Int) {
        this.amountInCents = amountInCents
        render()
    }

    private fun render() {
        if (!InstallmentCalculator.isEligible(amountInCents)) {
            visibility = GONE
            return
        }

        visibility = VISIBLE
        SezzlePromoDataHandler.getMessage(context, amountInCents, currency, style) { spanned ->
            messageView.text = spanned
        }
    }
}
