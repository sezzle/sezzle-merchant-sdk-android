package com.sezzle.sdk.promotional

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import com.sezzle.sdk.R

/**
 * Provides raw promotional message data for custom UI implementations.
 */
object SezzlePromoDataHandler {

    /**
     * Get a promotional message as a [SpannableString] with the Sezzle logo inline.
     *
     * @param context Android context for loading resources.
     * @param amountInCents The total order amount in cents.
     * @param currency ISO 4217 currency code.
     * @param style Visual style.
     * @param widgetConfig Widget configuration.
     * @param callback Called with the styled text. Empty if not eligible.
     */
    fun getMessage(
        context: Context,
        amountInCents: Int,
        currency: String = "USD",
        style: SezzlePromotionalStyle = SezzlePromotionalStyle.LIGHT,
        widgetConfig: SezzleWidgetConfig = SezzleWidgetConfig.DEFAULT,
        callback: (SpannableString) -> Unit
    ) {
        val type = InstallmentCalculator.widgetType(amountInCents, widgetConfig)
        if (type == SezzleWidgetType.HIDDEN) {
            callback(SpannableString(""))
            return
        }

        val message = buildAttributedMessage(context, amountInCents, type, currency, style, widgetConfig)
        callback(message)
    }

    internal fun buildAttributedMessage(
        context: Context,
        amountInCents: Int,
        widgetType: SezzleWidgetType,
        currency: String,
        style: SezzlePromotionalStyle,
        widgetConfig: SezzleWidgetConfig
    ): SpannableString {
        val builder = SpannableStringBuilder()

        when (widgetType) {
            SezzleWidgetType.PI4, SezzleWidgetType.PI5 -> {
                val numPayments = InstallmentCalculator.numberOfPayments(widgetType)
                val installments = InstallmentCalculator.installments(amountInCents, numPayments)
                val formatted = InstallmentCalculator.formatCents(installments[0], currency)

                // "or X payments of "
                val prefix = "or\u00A0${numPayments}\u00A0payments\u00A0of "
                builder.append(prefix)
                builder.setSpan(
                    ForegroundColorSpan(style.textColor),
                    0, prefix.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // "$XX.XX" in purple bold
                val amountStart = builder.length
                builder.append(formatted)
                builder.setSpan(
                    ForegroundColorSpan(SezzleBrand.PURPLE),
                    amountStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    amountStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // " with "
                val withStart = builder.length
                builder.append("\u00A0with ")
                builder.setSpan(
                    ForegroundColorSpan(style.textColor),
                    withStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            SezzleWidgetType.LONG_TERM -> {
                val ltConfig = widgetConfig.longTermConfig ?: return SpannableString("")
                val lowestPayment = InstallmentCalculator.lowestMonthlyPayment(amountInCents, ltConfig)
                val formatted = InstallmentCalculator.formatDollars(lowestPayment, currency)

                // "or monthly payments as low as "
                val prefix = "or\u00A0monthly\u00A0payments\u00A0as\u00A0low\u00A0as "
                builder.append(prefix)
                builder.setSpan(
                    ForegroundColorSpan(style.textColor),
                    0, prefix.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // "$XX.XX" in purple bold
                val amountStart = builder.length
                builder.append(formatted)
                builder.setSpan(
                    ForegroundColorSpan(SezzleBrand.PURPLE),
                    amountStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    amountStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // " with "
                val withStart = builder.length
                builder.append("\u00A0with ")
                builder.setSpan(
                    ForegroundColorSpan(style.textColor),
                    withStart, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            SezzleWidgetType.HIDDEN -> return SpannableString("")
        }

        // Sezzle logo inline
        val logo = loadLogo(context)
        if (logo != null) {
            val logoHeight = (style.textSizeSp * context.resources.displayMetrics.scaledDensity * 1.3f).toInt()
            val logoWidth = (logoHeight * (logo.width.toFloat() / logo.height)).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, true)
            val imageSpan = CenteredImageSpan(context, scaledLogo)
            val logoStart = builder.length
            builder.append(" ")
            builder.setSpan(imageSpan, logoStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            val fallbackStart = builder.length
            builder.append("Sezzle")
            builder.setSpan(ForegroundColorSpan(SezzleBrand.PURPLE), fallbackStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), fallbackStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Info icon
        val infoStart = builder.length
        builder.append("\u00A0\u24D8")
        builder.setSpan(ForegroundColorSpan(SezzleBrand.PURPLE), infoStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return SpannableString(builder)
    }

    private var cachedLogo: Bitmap? = null

    private fun loadLogo(context: Context): Bitmap? {
        cachedLogo?.let { return it }
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sezzle_logo)
            cachedLogo = bitmap
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}

/** An ImageSpan that vertically centers the image on the text line. */
private class CenteredImageSpan(context: Context, bitmap: Bitmap) : ReplacementSpan() {
    private val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap).apply {
        setBounds(0, 0, bitmap.width, bitmap.height)
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val bounds = drawable.bounds
        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            val imageHeight = bounds.height()
            val textHeight = fontMetrics.descent - fontMetrics.ascent
            val offset = (imageHeight - textHeight) / 2
            fm.ascent = fontMetrics.ascent - offset
            fm.top = fontMetrics.ascent - offset
            fm.descent = fontMetrics.descent + offset
            fm.bottom = fontMetrics.descent + offset
        }
        return bounds.right
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val fm = paint.fontMetricsInt
        val transY = y + fm.ascent + (fm.descent - fm.ascent - drawable.bounds.height()) / 2
        canvas.save()
        canvas.translate(x, transY.toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}
