package com.sezzle.sdk.promotional

import android.graphics.Color
import android.graphics.Typeface

/**
 * Logo color variant for promotional messaging.
 */
enum class SezzleLogoVariant {
    /** Dark logo for light backgrounds. */
    DARK,
    /** Light logo for dark backgrounds. */
    LIGHT
}

/**
 * Visual style for [SezzlePromotionalView].
 *
 * @property textColor Color for the message text.
 * @property textSizeSp Font size in SP.
 * @property typeface Typeface for the message text.
 * @property logoVariant Which logo variant to display.
 */
data class SezzlePromotionalStyle(
    val textColor: Int = SezzleBrand.DARK_PURPLE,
    val textSizeSp: Float = 14f,
    val typeface: Typeface = Typeface.DEFAULT,
    val logoVariant: SezzleLogoVariant = SezzleLogoVariant.DARK
) {
    companion object {
        /** Dark text for light backgrounds. */
        val LIGHT = SezzlePromotionalStyle(
            textColor = SezzleBrand.DARK_PURPLE,
            textSizeSp = 14f,
            typeface = Typeface.DEFAULT,
            logoVariant = SezzleLogoVariant.DARK
        )

        /** Light text for dark backgrounds. Uses official purpleWhite80 (#F9F5FD). */
        val DARK = SezzlePromotionalStyle(
            textColor = SezzleBrand.DARK_PURPLE_DARK_MODE,
            textSizeSp = 14f,
            typeface = Typeface.DEFAULT,
            logoVariant = SezzleLogoVariant.LIGHT
        )
    }
}
