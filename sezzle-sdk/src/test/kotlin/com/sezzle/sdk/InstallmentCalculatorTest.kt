package com.sezzle.sdk

import com.sezzle.sdk.promotional.InstallmentCalculator
import com.sezzle.sdk.promotional.SezzleLongTermConfig
import com.sezzle.sdk.promotional.SezzleWidgetConfig
import com.sezzle.sdk.promotional.SezzleWidgetType
import org.junit.Assert.*
import org.junit.Test

class InstallmentCalculatorTest {

    private val defaultConfig = SezzleWidgetConfig.DEFAULT
    private val pi5Config = SezzleWidgetConfig(enablePayIn5 = true)
    private val ltConfig = SezzleWidgetConfig(
        enablePayIn5 = true,
        longTermConfig = SezzleLongTermConfig(minPriceInCents = 25_000)
    )

    // -- Widget Type --

    @Test
    fun `below minPrice is HIDDEN`() {
        assertEquals(SezzleWidgetType.HIDDEN, InstallmentCalculator.widgetType(3499, defaultConfig))
    }

    @Test
    fun `at minPrice is PI4`() {
        assertEquals(SezzleWidgetType.PI4, InstallmentCalculator.widgetType(3500, defaultConfig))
    }

    @Test
    fun `under PI5 threshold is PI4`() {
        assertEquals(SezzleWidgetType.PI4, InstallmentCalculator.widgetType(4999, pi5Config))
    }

    @Test
    fun `at PI5 threshold is PI5`() {
        assertEquals(SezzleWidgetType.PI5, InstallmentCalculator.widgetType(5000, pi5Config))
    }

    @Test
    fun `PI5 disabled stays PI4`() {
        val noPi5 = SezzleWidgetConfig(enablePayIn5 = false)
        assertEquals(SezzleWidgetType.PI4, InstallmentCalculator.widgetType(10000, noPi5))
    }

    @Test
    fun `above maxPrice is HIDDEN`() {
        assertEquals(SezzleWidgetType.HIDDEN, InstallmentCalculator.widgetType(250001, defaultConfig))
    }

    @Test
    fun `long-term eligible`() {
        assertEquals(SezzleWidgetType.LONG_TERM, InstallmentCalculator.widgetType(30000, ltConfig))
    }

    @Test
    fun `long-term takes priority over PI5`() {
        assertEquals(SezzleWidgetType.LONG_TERM, InstallmentCalculator.widgetType(30000, ltConfig))
    }

    @Test
    fun `above maxPrice but LT eligible is LONG_TERM`() {
        val config = SezzleWidgetConfig(
            maxPriceInCents = 250_000,
            longTermConfig = SezzleLongTermConfig(minPriceInCents = 200_000, maxPriceInCents = 4_000_000)
        )
        assertEquals(SezzleWidgetType.LONG_TERM, InstallmentCalculator.widgetType(300_000, config))
    }

    // -- PI4 Installments --

    @Test
    fun `PI4 installments for 4999 cents`() {
        val result = InstallmentCalculator.installments(4999, 4)
        assertEquals(listOf(1249, 1249, 1249, 1252), result)
        assertEquals(4999, result.sum())
    }

    @Test
    fun `PI4 installments for even amount`() {
        val result = InstallmentCalculator.installments(10000, 4)
        assertEquals(listOf(2500, 2500, 2500, 2500), result)
    }

    // -- PI5 Installments --

    @Test
    fun `PI5 installments for 14999 cents`() {
        val result = InstallmentCalculator.installments(14999, 5)
        assertEquals(5, result.size)
        assertEquals(14999, result.sum())
        assertEquals(2999, result[0]) // 14999/5 = 2999
        assertEquals(3003, result[4]) // remainder
    }

    @Test
    fun `PI5 installments for even amount`() {
        val result = InstallmentCalculator.installments(10000, 5)
        assertEquals(listOf(2000, 2000, 2000, 2000, 2000), result)
    }

    // -- Long-Term --

    @Test
    fun `monthly payment with APR`() {
        // $500, 12 months, 6.99% APR
        val monthly = InstallmentCalculator.monthlyPayment(500.0, 12, 6.99)
        assertTrue(monthly > 43.0 && monthly < 44.0) // ~$43.26
    }

    @Test
    fun `monthly payment with 0 APR`() {
        val monthly = InstallmentCalculator.monthlyPayment(500.0, 12, 0.0)
        assertEquals(41.67, monthly, 0.01) // 500/12
    }

    @Test
    fun `long-term options sorted by monthly payment`() {
        val ltCfg = SezzleLongTermConfig()
        val options = InstallmentCalculator.longTermOptions(80000, ltCfg) // $800
        assertTrue(options.isNotEmpty())
        // Sorted ascending by monthly
        for (i in 1 until options.size) {
            assertTrue(options[i].monthlyPayment >= options[i - 1].monthlyPayment)
        }
    }

    @Test
    fun `lowest monthly payment uses longest term`() {
        val ltCfg = SezzleLongTermConfig()
        val lowest = InstallmentCalculator.lowestMonthlyPayment(80000, ltCfg) // $800
        val options = InstallmentCalculator.longTermOptions(80000, ltCfg)
        assertEquals(options.first().monthlyPayment, lowest, 0.001)
    }

    // -- Formatting --

    @Test
    fun `formatCents formats correctly`() {
        assertEquals("$12.50", InstallmentCalculator.formatCents(1250, "USD"))
    }

    @Test
    fun `formatCents handles zero`() {
        assertEquals("$0.00", InstallmentCalculator.formatCents(0, "USD"))
    }

    // -- Payment Dates --

    @Test
    fun `payment dates returns correct count`() {
        assertEquals(4, InstallmentCalculator.paymentDates(4).size)
        assertEquals(5, InstallmentCalculator.paymentDates(5).size)
    }

    @Test
    fun `payment dates are 14 days apart`() {
        val dates = InstallmentCalculator.paymentDates(5)
        for (i in 1 until dates.size) {
            val diff = dates[i].time - dates[i - 1].time
            val daysDiff = diff / (1000 * 60 * 60 * 24)
            assertEquals(14L, daysDiff)
        }
    }
}
