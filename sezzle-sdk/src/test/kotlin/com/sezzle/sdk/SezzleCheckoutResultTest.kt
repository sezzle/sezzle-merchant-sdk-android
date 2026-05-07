package com.sezzle.sdk

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SezzleCheckoutResultTest {

    @Test
    fun `default constructor has both fields null`() {
        val result = SezzleCheckoutResult()
        assertNull(result.orderUUID)
        assertNull(result.callbackURL)
    }

    @Test
    fun `SDK-creates-session flow populates orderUUID only`() {
        val result = SezzleCheckoutResult(orderUUID = "ord-123")
        assertEquals("ord-123", result.orderUUID)
        assertNull(result.callbackURL)
    }

    @Test
    fun `server-driven flow populates callbackURL only`() {
        val url = Uri.parse("poshmark-sezzle://checkout/done?orderRef=12345")
        val result = SezzleCheckoutResult(callbackURL = url)
        assertNull(result.orderUUID)
        assertEquals(url, result.callbackURL)
    }

    @Test
    fun `query params recoverable from callbackURL`() {
        val url = Uri.parse("poshmark-sezzle://checkout/done?orderRef=12345&promo=summer")
        val result = SezzleCheckoutResult(callbackURL = url)

        assertEquals("12345", result.callbackURL?.getQueryParameter("orderRef"))
        assertEquals("summer", result.callbackURL?.getQueryParameter("promo"))
    }
}
