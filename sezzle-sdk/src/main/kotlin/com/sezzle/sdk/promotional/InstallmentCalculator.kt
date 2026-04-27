package com.sezzle.sdk.promotional

import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** The type of promotional widget to display based on price and config. */
enum class SezzleWidgetType {
    /** Price below minimum — don't show widget. */
    HIDDEN,
    /** Standard 4-payment plan. */
    PI4,
    /** 5-payment plan (price >= $50 and PI5 enabled). */
    PI5,
    /** Long-term monthly payments with interest. */
    LONG_TERM
}

/** Calculates installment amounts, payment dates, and determines widget type. */
object InstallmentCalculator {

    // -- Widget Type --

    /** Determine which widget type to show for a given price. */
    fun widgetType(amountInCents: Int, config: SezzleWidgetConfig): SezzleWidgetType {
        if (amountInCents < config.minPriceInCents) return SezzleWidgetType.HIDDEN

        // Long-term takes priority
        config.longTermConfig?.let { lt ->
            if (amountInCents >= lt.minPriceInCents && amountInCents <= lt.maxPriceInCents) {
                return SezzleWidgetType.LONG_TERM
            }
        }

        if (amountInCents > config.maxPriceInCents) return SezzleWidgetType.HIDDEN

        if (config.enablePayIn5 && amountInCents >= config.pi5MinPriceInCents) return SezzleWidgetType.PI5

        return SezzleWidgetType.PI4
    }

    /** Number of payments for the given widget type. */
    fun numberOfPayments(type: SezzleWidgetType): Int = when (type) {
        SezzleWidgetType.PI4 -> 4
        SezzleWidgetType.PI5 -> 5
        else -> 0
    }

    // -- Short-Term (PI4/PI5) --

    /** Calculate installment amounts. Last payment absorbs remainder. */
    fun installments(amountInCents: Int, numberOfPayments: Int): List<Int> {
        if (amountInCents <= 0 || numberOfPayments <= 0) return emptyList()
        val base = amountInCents / numberOfPayments
        val remainder = amountInCents - (base * (numberOfPayments - 1))
        return List(numberOfPayments - 1) { base } + remainder
    }

    /** Payment dates (biweekly — every 2 weeks). */
    fun paymentDates(numberOfPayments: Int, startDate: Date = Date()): List<Date> {
        val calendar = Calendar.getInstance()
        return (0 until numberOfPayments).map { index ->
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, 14 * index)
            calendar.time
        }
    }

    // -- Long-Term (Monthly with APR) --

    /** Calculate monthly payment with amortization formula. */
    fun monthlyPayment(principalInDollars: Double, months: Int, apr: Double): Double {
        if (months <= 0) return 0.0
        if (apr > 0) {
            val monthlyRate = apr / 100.0 / 12.0
            val interest = (1 + monthlyRate).pow(months.toDouble())
            return (principalInDollars * monthlyRate * interest) / (interest - 1)
        }
        return principalInDollars / months
    }

    /** Get available long-term options sorted by monthly payment ascending. */
    fun longTermOptions(amountInCents: Int, config: SezzleLongTermConfig): List<LongTermOption> {
        val priceInDollars = amountInCents / 100.0
        val terms = paymentTerms(priceInDollars, config.paymentTerms)
        return terms.map { (months, apr) ->
            val monthly = monthlyPayment(priceInDollars, months, apr)
            val total = (monthly * months * 100).roundToInt() / 100.0
            LongTermOption(months, apr, monthly, total, total - priceInDollars)
        }.sortedBy { it.monthlyPayment }
    }

    /** Get the lowest monthly payment for the widget text. */
    fun lowestMonthlyPayment(amountInCents: Int, config: SezzleLongTermConfig): Double {
        val priceInDollars = amountInCents / 100.0
        val terms = paymentTerms(priceInDollars, config.paymentTerms)
        val longest = terms.firstOrNull() ?: return priceInDollars
        return monthlyPayment(priceInDollars, longest.first, longest.second)
    }

    private fun paymentTerms(priceInDollars: Double, tiers: List<SezzleLongTermTier>): List<Pair<Int, Double>> {
        for (tier in tiers) {
            if (priceInDollars > tier.priceThreshold) return tier.options
        }
        return tiers.lastOrNull()?.options ?: emptyList()
    }

    // -- Formatting --

    fun formatCents(cents: Int, currency: String = "USD"): String =
        formatDollars(cents / 100.0, currency)

    fun formatDollars(dollars: Double, currency: String = "USD"): String {
        return try {
            val formatter = NumberFormat.getCurrencyInstance(Locale.US)
            formatter.currency = Currency.getInstance(currency)
            formatter.format(dollars)
        } catch (_: Exception) {
            "$${String.format(Locale.US, "%.2f", dollars)}"
        }
    }
}

/** A long-term payment option with calculated values. */
data class LongTermOption(
    val months: Int,
    val apr: Double,
    val monthlyPayment: Double,
    val totalAmount: Double,
    val totalInterest: Double
)
