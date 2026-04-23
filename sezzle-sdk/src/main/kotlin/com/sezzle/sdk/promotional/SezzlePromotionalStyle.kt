package com.sezzle.sdk.promotional

import android.graphics.Color
import android.graphics.Typeface

/**
 * Visual style for [SezzlePromotionalView].
 *
 * @property textColor Color for the message text.
 * @property textSizeSp Font size in SP.
 * @property typeface Typeface for the message text.
 */
data class SezzlePromotionalStyle(
    val textColor: Int = SezzleBrand.DARK_PURPLE,
    val textSizeSp: Float = 14f,
    val typeface: Typeface = Typeface.DEFAULT
) {
    companion object {
        /** Dark text for light backgrounds. */
        val LIGHT = SezzlePromotionalStyle(
            textColor = SezzleBrand.DARK_PURPLE,
            textSizeSp = 14f,
            typeface = Typeface.DEFAULT
        )

        /** Light text for dark backgrounds. */
        val DARK = SezzlePromotionalStyle(
            textColor = Color.WHITE,
            textSizeSp = 14f,
            typeface = Typeface.DEFAULT
        )
    }
}
