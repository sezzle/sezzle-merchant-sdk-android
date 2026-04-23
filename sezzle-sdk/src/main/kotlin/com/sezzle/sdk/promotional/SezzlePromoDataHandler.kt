package com.sezzle.sdk.promotional

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
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
 *
 * Use this when you want to build your own promotional UI instead of using
 * [SezzlePromotionalView].
 *
 * ```kotlin
 * SezzlePromoDataHandler.getMessage(context, amountInCents = 4999) { spanned ->
 *     myTextView.text = spanned
 * }
 * ```
 */
object SezzlePromoDataHandler {

    /**
     * Get a promotional message as a [SpannableString] with the Sezzle logo inline.
     *
     * @param context Android context for loading resources.
     * @param amountInCents The total order amount in cents.
     * @param currency ISO 4217 currency code.
     * @param style Visual style.
     * @param callback Called on the calling thread with the styled text.
     *   Returns empty string if the amount is not eligible.
     */
    fun getMessage(
        context: Context,
        amountInCents: Int,
        currency: String = "USD",
        style: SezzlePromotionalStyle = SezzlePromotionalStyle.LIGHT,
        callback: (SpannableString) -> Unit
    ) {
        if (!InstallmentCalculator.isEligible(amountInCents)) {
            callback(SpannableString(""))
            return
        }

        val installments = InstallmentCalculator.installments(amountInCents)
        val formatted = InstallmentCalculator.formatCents(installments[0], currency)
        val message = buildAttributedMessage(context, formatted, style)
        callback(message)
    }

    internal fun buildAttributedMessage(
        context: Context,
        installmentAmount: String,
        style: SezzlePromotionalStyle
    ): SpannableString {
        val builder = SpannableStringBuilder()

        // "or 4 interest-free payments of " with non-breaking spaces
        val prefix = "or\u00A04\u00A0interest-free\u00A0payments\u00A0of "
        builder.append(prefix)
        builder.setSpan(
            ForegroundColorSpan(style.textColor),
            0, prefix.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // "$XX.XX" in Sezzle purple bold
        val amountStart = builder.length
        builder.append(installmentAmount)
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

        // " with " — non-breaking before "with"
        val withText = "\u00A0with "
        val withStart = builder.length
        builder.append(withText)
        builder.setSpan(
            ForegroundColorSpan(style.textColor),
            withStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Sezzle logo inline — use ALIGN_CENTER so it vertically centers on the text line
        val logo = loadLogo(context)
        if (logo != null) {
            val logoHeight = (style.textSizeSp * context.resources.displayMetrics.scaledDensity * 1.3f).toInt()
            val logoWidth = (logoHeight * (logo.width.toFloat() / logo.height)).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, true)
            val imageSpan = CenteredImageSpan(context, scaledLogo)
            val logoPlaceholder = " "
            val logoStart = builder.length
            builder.append(logoPlaceholder)
            builder.setSpan(imageSpan, logoStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            val fallbackStart = builder.length
            builder.append("Sezzle")
            builder.setSpan(
                ForegroundColorSpan(SezzleBrand.PURPLE),
                fallbackStart, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                fallbackStart, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Info icon — non-breaking space so logo and icon stay together
        val infoStart = builder.length
        builder.append("\u00A0\u24D8")
        builder.setSpan(
            ForegroundColorSpan(SezzleBrand.PURPLE),
            infoStart, builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

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
    private val drawable = BitmapDrawable(context.resources, bitmap).apply {
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
