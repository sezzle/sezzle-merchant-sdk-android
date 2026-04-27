package com.sezzle.sdk.promotional

/**
 * Configuration for Sezzle promotional messaging.
 *
 * Controls which widget template is shown based on the product price:
 * - Below [minPriceInCents]: widget hidden
 * - Below PI5 threshold ($50): "or 4 payments of $X with Sezzle"
 * - At/above PI5 threshold: "or 5 payments of $X with Sezzle" (when [enablePayIn5] is true)
 * - At/above long-term min: "or monthly payments as low as $X with Sezzle"
 * - Above [maxPriceInCents] (and no LT): widget hidden
 */
data class SezzleWidgetConfig(
    /** Minimum price in cents for widget to display. Default: 3500 ($35). */
    val minPriceInCents: Int = 3500,
    /** Maximum price in cents for PI4/PI5 eligibility. Default: 250000 ($2,500). */
    val maxPriceInCents: Int = 250_000,
    /** Enable 5-payment option for prices >= $50. Default: true (matches US widget). */
    val enablePayIn5: Boolean = true,
    /** PI5 price threshold in cents. Default: 5000 ($50). */
    val pi5MinPriceInCents: Int = 5000,
    /** Long-term config. Null = disabled. */
    val longTermConfig: SezzleLongTermConfig? = null,
    /** Currency code. Default: "USD". */
    val currency: String = "USD"
) {
    companion object {
        val DEFAULT = SezzleWidgetConfig()
    }
}

/**
 * Long-term (monthly) payment configuration.
 */
data class SezzleLongTermConfig(
    /** Minimum price in cents for long-term eligibility. Default: 10000 ($100). */
    val minPriceInCents: Int = 10_000,
    /** Maximum price in cents for long-term eligibility. Default: 4000000 ($40,000). */
    val maxPriceInCents: Int = 4_000_000,
    /** Payment term tiers sorted by price descending. */
    val paymentTerms: List<SezzleLongTermTier> = SezzleLongTermTier.DEFAULTS,
    /** Minimum APR for disclosure. Default: "9.99". */
    val minAPR: String = "9.99",
    /** Maximum APR for disclosure. Default: "34.99". */
    val maxAPR: String = "34.99"
)

/**
 * A price tier for long-term payment options.
 */
data class SezzleLongTermTier(
    /** Price threshold in dollars (products above this use these options). */
    val priceThreshold: Double,
    /** Available options: list of (months, APR%). */
    val options: List<Pair<Int, Double>>
) {
    companion object {
        val DEFAULTS = listOf(
            SezzleLongTermTier(1000.0, listOf(48 to 8.99, 36 to 7.99, 3 to 0.0)),
            SezzleLongTermTier(500.0, listOf(24 to 7.99, 12 to 6.99, 3 to 0.0)),
            SezzleLongTermTier(250.0, listOf(12 to 6.99, 9 to 5.99, 3 to 0.0)),
            SezzleLongTermTier(100.0, listOf(9 to 5.99, 6 to 5.99, 3 to 0.0)),
        )
    }
}
