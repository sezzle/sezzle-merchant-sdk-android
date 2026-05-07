package com.sezzle.sdk

import android.net.Uri
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.models.SezzleError
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class URLParsingTest {

    private var completedResult: SezzleCheckoutResult? = null
    private var cancelled = false
    private var error: SezzleError? = null

    private val completeUrl = CheckoutHandler.DEFAULT_COMPLETE_URL
    private val cancelUrl = CheckoutHandler.DEFAULT_CANCEL_URL

    private val listener = object : SezzleCheckoutListener {
        override fun onCheckoutComplete(result: SezzleCheckoutResult) { completedResult = result }
        override fun onCheckoutCancel() { cancelled = true }
        override fun onCheckoutError(error: SezzleError) { this@URLParsingTest.error = error }
    }

    @Before
    fun setUp() {
        completedResult = null
        cancelled = false
        error = null
    }

    @Test
    fun `confirmed URL triggers onCheckoutComplete with orderUUID`() {
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, "test-order-uuid", listener)
        assertEquals("test-order-uuid", completedResult?.orderUUID)
        assertNull(completedResult?.callbackURL)
    }

    @Test
    fun `cancelled URL triggers onCheckoutCancel`() {
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, "test-order-uuid", listener)
        assertTrue(cancelled)
    }

    @Test
    fun `unknown path triggers error`() {
        val uri = Uri.parse("sezzle-sdk://checkout/unknown")
        CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, "test-order-uuid", listener)
        assertTrue(error is SezzleError.InvalidResponse)
    }

    @Test
    fun `wrong host triggers error`() {
        val uri = Uri.parse("sezzle-sdk://unknown/confirmed")
        CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, "test-order-uuid", listener)
        assertTrue(error is SezzleError.InvalidResponse)
    }

    @Test
    fun `cancelled URL does not return orderUUID`() {
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        CheckoutHandler.handleCallbackUri(uri, completeUrl, cancelUrl, "test-order-uuid", listener)
        assertNull(completedResult)
        assertTrue(cancelled)
    }

    @Test
    fun `server-driven flow returns callbackURL not orderUUID`() {
        val merchantComplete = Uri.parse("poshmark-sezzle://checkout/done")
        val merchantCancel = Uri.parse("poshmark-sezzle://checkout/cancelled")
        val landed = Uri.parse("poshmark-sezzle://checkout/done?orderRef=12345")

        CheckoutHandler.handleCallbackUri(landed, merchantComplete, merchantCancel, orderUUID = null, listener)

        assertNull(completedResult?.orderUUID)
        assertEquals(landed, completedResult?.callbackURL)
        assertEquals("12345", completedResult?.callbackURL?.getQueryParameter("orderRef"))
    }

    @Test
    fun `match works for custom merchant scheme cancel URL`() {
        val merchantComplete = Uri.parse("yourapp://done")
        val merchantCancel = Uri.parse("yourapp://cancelled")
        val landed = Uri.parse("yourapp://cancelled")

        CheckoutHandler.handleCallbackUri(landed, merchantComplete, merchantCancel, orderUUID = null, listener)

        assertTrue(cancelled)
    }
}
