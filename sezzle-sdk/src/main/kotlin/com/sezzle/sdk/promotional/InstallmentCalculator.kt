package com.sezzle.sdk.promotional

import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

/** Calculates installment amounts and payment dates for Sezzle's 4-payment model. */
object InstallmentCalculator {
    /** Minimum order amount in cents ($35.00). */
    const val MINIMUM_AMOUNT_IN_CENTS = 3500

    /** Maximum order amount in cents ($2,500.00). */
    const val MAXIMUM_AMOUNT_IN_CENTS = 250_000

    /** Whether the given amount is eligible for installment messaging. */
    fun isEligible(amountInCents: Int): Boolean =
        amountInCents in MINIMUM_AMOUNT_IN_CENTS..MAXIMUM_AMOUNT_IN_CENTS

    /**
     * Calculate the four installment amounts in cents.
     *
     * The first three payments are equal (rounded down). The fourth absorbs the remainder.
     * For example, $49.99 (4999 cents) -> [1249, 1249, 1249, 1252].
     */
    fun installments(amountInCents: Int): List<Int> {
        if (amountInCents <= 0) return listOf(0, 0, 0, 0)
        val base = amountInCents / 4
        val remainder = amountInCents - (base * 3)
        return listOf(base, base, base, remainder)
    }

    /** Format cents as a currency string (e.g., 1250 -> "$12.50"). */
    fun formatCents(cents: Int, currency: String = "USD"): String {
        val dollars = cents / 100.0
        return try {
            val formatter = NumberFormat.getCurrencyInstance(Locale.US)
            formatter.currency = Currency.getInstance(currency)
            formatter.format(dollars)
        } catch (_: Exception) {
            "$${String.format(Locale.US, "%.2f", dollars)}"
        }
    }

    /**
     * Calculate payment due dates starting from today.
     *
     * US/CA uses biweekly (14-day) intervals. Other regions use monthly (30-day) intervals.
     */
    fun paymentDates(startDate: Date = Date(), biweekly: Boolean = true): List<Date> {
        val interval = if (biweekly) 14 else 30
        val calendar = Calendar.getInstance()
        return (0 until 4).map { index ->
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, interval * index)
            calendar.time
        }
    }
}
