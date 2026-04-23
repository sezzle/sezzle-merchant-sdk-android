package com.sezzle.sdk

import android.net.Uri
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.checkout.CheckoutState
import com.sezzle.sdk.models.SezzleError
import org.junit.After
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
        CheckoutState.listener = listener
        CheckoutState.orderUUID = "test-order-uuid"
        CheckoutState.checkoutInProgress = true
        completedUUID = null
        cancelled = false
        error = null
    }

    @After
    fun tearDown() {
        CheckoutState.clear()
    }

    @Test
    fun `confirmed URL triggers onCheckoutComplete`() {
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        val handled = CheckoutHandler.handleCallbackUrl(uri)
        assertTrue(handled)
    }

    @Test
    fun `cancelled URL triggers onCheckoutCancel`() {
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        val handled = CheckoutHandler.handleCallbackUrl(uri)
        assertTrue(handled)
    }

    @Test
    fun `unknown path returns handled but triggers error`() {
        val uri = Uri.parse("sezzle-sdk://checkout/unknown")
        val handled = CheckoutHandler.handleCallbackUrl(uri)
        assertTrue(handled)
    }

    @Test
    fun `wrong scheme returns false`() {
        val uri = Uri.parse("https://checkout/confirmed")
        val handled = CheckoutHandler.handleCallbackUrl(uri)
        assertFalse(handled)
    }

    @Test
    fun `wrong host returns false`() {
        val uri = Uri.parse("sezzle-sdk://unknown/confirmed")
        val handled = CheckoutHandler.handleCallbackUrl(uri)
        assertFalse(handled)
    }

    @Test
    fun `clears checkout state after handling`() {
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        CheckoutHandler.handleCallbackUrl(uri)
        assertNull(CheckoutState.listener)
        assertNull(CheckoutState.orderUUID)
        assertFalse(CheckoutState.checkoutInProgress)
    }
}
