package com.sezzle.sdk.promotional

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Sezzle brand constants matching the web installment widget. */
object SezzleBrand {
    /** Primary purple — installment amounts, active elements. */
    val PURPLE = Color.parseColor("#8333D4")

    /** Dark purple — headings, body text. Light: #382757, Dark: #F9F5FD */
    val DARK_PURPLE = Color.parseColor("#382757")
    val DARK_PURPLE_DARK_MODE = Color.parseColor("#F9F5FD")

    /** Gray — due dates, secondary text. Light: #767676, Dark: #AEAEAE */
    val GRAY = Color.parseColor("#767676")
    val GRAY_DARK_MODE = Color.parseColor("#AEAEAE")

    /** Light purple background for cards. */
    val LIGHT_PURPLE_BG = Color.argb((255 * 0.05).toInt(), 0x83, 0x33, 0xD4)
    val LIGHT_PURPLE_BG_DARK_MODE = Color.argb((255 * 0.20).toInt(), 0x83, 0x33, 0xD4)

    /** Green for first payment / "today" indicator. */
    val GREEN = Color.parseColor("#00B874")

    /** Modal background. */
    val MODAL_BG = Color.WHITE
    val MODAL_BG_DARK_MODE = Color.parseColor("#1C1230")

    /** Modal grab handle. */
    val HANDLE = Color.parseColor("#DDDDDD")
    val HANDLE_DARK_MODE = Color.parseColor("#444444")

    /** Create a pie chart View for a given payment step. */
    fun pieChartView(context: Context, step: Int, totalSteps: Int = 4, sizePx: Int): View {
        return PieChartView(context, step, totalSteps, sizePx)
    }

    private class PieChartView(
        context: Context,
        private val step: Int,
        private val totalSteps: Int,
        private val sizePx: Int
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIGHT_PURPLE_BG
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PURPLE
            style = Paint.Style.FILL
        }
        private val rect = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(cx, cy)

            // Background circle
            canvas.drawCircle(cx, cy, radius, bgPaint)

            // Filled arc
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            val sweepAngle = (step / totalSteps.toFloat()) * 360f
            canvas.drawArc(rect, -90f, sweepAngle, true, fillPaint)
        }
    }
}
