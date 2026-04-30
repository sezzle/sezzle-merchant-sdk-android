package com.sezzle.sdk.models

/** Available financing plan options to restrict checkout to specific plans. */
enum class SezzleFinancingOption(val value: String) {
    FOUR_PAY_BIWEEKLY("4-pay-biweekly"),
    FOUR_PAY_MONTHLY("4-pay-monthly"),
    SIX_PAY_MONTHLY("6-pay-monthly")
}
