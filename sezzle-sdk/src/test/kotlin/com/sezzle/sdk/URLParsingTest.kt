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

    private var completedUUID: String? = null
    private var cancelled = false
    private var error: SezzleError? = null

    private val listener = object : SezzleCheckoutListener {
        override fun onCheckoutComplete(orderUUID: String) { completedUUID = orderUUID }
        override fun onCheckoutCancel() { cancelled = true }
        override fun onCheckoutError(error: SezzleError) { this@URLParsingTest.error = error }
    }

    @Before
    fun setUp() {
        completedUUID = null
        cancelled = false
        error = null
    }

    @Test
    fun `confirmed URL triggers onCheckoutComplete`() {
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        CheckoutHandler.handleCallbackUri(uri, "test-order-uuid", listener)
        assertEquals("test-order-uuid", completedUUID)
    }

    @Test
    fun `cancelled URL triggers onCheckoutCancel`() {
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        CheckoutHandler.handleCallbackUri(uri, "test-order-uuid", listener)
        assertTrue(cancelled)
    }

    @Test
    fun `unknown path triggers error`() {
        val uri = Uri.parse("sezzle-sdk://checkout/unknown")
        CheckoutHandler.handleCallbackUri(uri, "test-order-uuid", listener)
        assertTrue(error is SezzleError.InvalidResponse)
    }

    @Test
    fun `wrong host triggers error`() {
        val uri = Uri.parse("sezzle-sdk://unknown/confirmed")
        CheckoutHandler.handleCallbackUri(uri, "test-order-uuid", listener)
        assertTrue(error is SezzleError.InvalidResponse)
    }

    @Test
    fun `confirmed URL passes correct orderUUID`() {
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        CheckoutHandler.handleCallbackUri(uri, "my-uuid-123", listener)
        assertEquals("my-uuid-123", completedUUID)
    }

    @Test
    fun `cancelled URL does not return orderUUID`() {
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        CheckoutHandler.handleCallbackUri(uri, "test-order-uuid", listener)
        assertNull(completedUUID)
        assertTrue(cancelled)
    }
}
