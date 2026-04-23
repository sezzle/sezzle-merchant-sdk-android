package com.sezzle.sdk

import android.net.Uri
import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionResponse
import com.sezzle.sdk.networking.SessionServiceProtocol
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutHandlerTest {

    /** Fake session service that immediately calls back. */
    private class FakeSessionService(
        private val result: Result<SessionResponse>
    ) : SessionServiceProtocol {
        override fun createSession(
            checkout: SezzleCheckout,
            onSuccess: (SessionResponse) -> Unit,
            onError: (SezzleError) -> Unit
        ) {
            result.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it as SezzleError) }
            )
        }
    }

    @Test
    fun `network error propagates via callback`() {
        val service = FakeSessionService(
            Result.failure(SezzleError.NetworkError(RuntimeException("no internet")))
        )
        var receivedError: SezzleError? = null

        service.createSession(
            checkout = makeCheckout(),
            onSuccess = { fail("Should not succeed") },
            onError = { receivedError = it }
        )

        assertTrue(receivedError is SezzleError.NetworkError)
    }

    @Test
    fun `API error propagates via callback`() {
        val service = FakeSessionService(
            Result.failure(SezzleError.ApiError(401, "Unauthorized"))
        )
        var receivedError: SezzleError? = null

        service.createSession(
            checkout = makeCheckout(),
            onSuccess = { fail("Should not succeed") },
            onError = { receivedError = it }
        )

        assertTrue(receivedError is SezzleError.ApiError)
        assertEquals(401, (receivedError as SezzleError.ApiError).statusCode)
    }

    @Test
    fun `successful session returns orderUUID and checkoutURL`() {
        val response = SessionResponse(
            uuid = "session-123",
            orderUUID = "order-456",
            checkoutURL = "https://checkout.sezzle.com/test"
        )
        val service = FakeSessionService(Result.success(response))
        var receivedResponse: SessionResponse? = null

        service.createSession(
            checkout = makeCheckout(),
            onSuccess = { receivedResponse = it },
            onError = { fail("Should not error") }
        )

        assertNotNull(receivedResponse)
        assertEquals("order-456", receivedResponse!!.orderUUID)
        assertEquals("https://checkout.sezzle.com/test", receivedResponse!!.checkoutURL)
    }

    @Test
    fun `handleCallbackUri dispatches confirmed correctly`() {
        var completedUUID: String? = null
        val listener = object : SezzleCheckoutListener {
            override fun onCheckoutComplete(orderUUID: String) { completedUUID = orderUUID }
            override fun onCheckoutCancel() { fail("Should not cancel") }
            override fun onCheckoutError(error: SezzleError) { fail("Should not error") }
        }

        CheckoutHandler.handleCallbackUri(
            Uri.parse("sezzle-sdk://checkout/confirmed"),
            "order-789",
            listener
        )

        assertEquals("order-789", completedUUID)
    }

    @Test
    fun `handleCallbackUri dispatches cancelled correctly`() {
        var cancelled = false
        val listener = object : SezzleCheckoutListener {
            override fun onCheckoutComplete(orderUUID: String) { fail("Should not complete") }
            override fun onCheckoutCancel() { cancelled = true }
            override fun onCheckoutError(error: SezzleError) { fail("Should not error") }
        }

        CheckoutHandler.handleCallbackUri(
            Uri.parse("sezzle-sdk://checkout/cancelled"),
            "order-789",
            listener
        )

        assertTrue(cancelled)
    }

    private fun makeCheckout(): SezzleCheckout {
        return SezzleCheckout(
            customer = com.sezzle.sdk.models.SezzleCustomer(email = "test@example.com"),
            order = com.sezzle.sdk.models.SezzleOrder(
                referenceId = "order-123",
                amount = com.sezzle.sdk.models.SezzleAmount(amountInCents = 4999)
            )
        )
    }
}
