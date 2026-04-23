package com.sezzle.sdk

import com.sezzle.sdk.promotional.InstallmentCalculator
import org.junit.Assert.*
import org.junit.Test

class InstallmentCalculatorTest {

    @Test
    fun `installments for 4999 cents`() {
        val result = InstallmentCalculator.installments(4999)
        assertEquals(listOf(1249, 1249, 1249, 1252), result)
        assertEquals(4999, result.sum())
    }

    @Test
    fun `installments for even amount`() {
        val result = InstallmentCalculator.installments(10000)
        assertEquals(listOf(2500, 2500, 2500, 2500), result)
        assertEquals(10000, result.sum())
    }

    @Test
    fun `installments for 100 cents`() {
        val result = InstallmentCalculator.installments(100)
        assertEquals(listOf(25, 25, 25, 25), result)
        assertEquals(100, result.sum())
    }

    @Test
    fun `installments for 1 cent`() {
        val result = InstallmentCalculator.installments(1)
        assertEquals(listOf(0, 0, 0, 1), result)
        assertEquals(1, result.sum())
    }

    @Test
    fun `installments for zero`() {
        val result = InstallmentCalculator.installments(0)
        assertEquals(listOf(0, 0, 0, 0), result)
    }

    @Test
    fun `installments for negative`() {
        val result = InstallmentCalculator.installments(-100)
        assertEquals(listOf(0, 0, 0, 0), result)
    }

    @Test
    fun `isEligible below minimum`() {
        assertFalse(InstallmentCalculator.isEligible(3499))
    }

    @Test
    fun `isEligible at minimum`() {
        assertTrue(InstallmentCalculator.isEligible(3500))
    }

    @Test
    fun `isEligible at maximum`() {
        assertTrue(InstallmentCalculator.isEligible(250_000))
    }

    @Test
    fun `isEligible above maximum`() {
        assertFalse(InstallmentCalculator.isEligible(250_001))
    }

    @Test
    fun `formatCents formats correctly`() {
        val result = InstallmentCalculator.formatCents(1250, "USD")
        assertEquals("$12.50", result)
    }

    @Test
    fun `formatCents handles zero`() {
        val result = InstallmentCalculator.formatCents(0, "USD")
        assertEquals("$0.00", result)
    }

    @Test
    fun `payment dates returns 4 dates`() {
        val dates = InstallmentCalculator.paymentDates()
        assertEquals(4, dates.size)
    }

    @Test
    fun `biweekly dates are 14 days apart`() {
        val dates = InstallmentCalculator.paymentDates(biweekly = true)
        for (i in 1 until dates.size) {
            val diff = dates[i].time - dates[i - 1].time
            val daysDiff = diff / (1000 * 60 * 60 * 24)
            assertEquals(14L, daysDiff)
        }
    }

    @Test
    fun `monthly dates are 30 days apart`() {
        val dates = InstallmentCalculator.paymentDates(biweekly = false)
        for (i in 1 until dates.size) {
            val diff = dates[i].time - dates[i - 1].time
            val daysDiff = diff / (1000 * 60 * 60 * 24)
            assertEquals(30L, daysDiff)
        }
    }

    @Test
    fun `first payment date is today`() {
        val dates = InstallmentCalculator.paymentDates()
        val now = System.currentTimeMillis()
        val diff = Math.abs(dates[0].time - now)
        assertTrue("First date should be close to now", diff < 1000)
    }
}
