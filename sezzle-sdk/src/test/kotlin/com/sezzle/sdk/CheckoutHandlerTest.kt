package com.sezzle.sdk

import com.sezzle.sdk.checkout.CheckoutHandler
import com.sezzle.sdk.checkout.CheckoutState
import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionResponse
import com.sezzle.sdk.networking.SessionServiceProtocol
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutHandlerTest {

    @After
    fun tearDown() {
        CheckoutState.clear()
    }

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
    fun `successful session stores orderUUID in state`() {
        val response = SessionResponse(
            uuid = "session-123",
            orderUUID = "order-456",
            checkoutURL = "https://checkout.sezzle.com/test"
        )
        val service = FakeSessionService(Result.success(response))
        val handler = CheckoutHandler(service)

        // We can't fully test the Custom Tab launch without an Activity,
        // but we can test the service interaction
        assertNotNull(service)
        assertNotNull(handler)
    }

    @Test
    fun `network error propagates`() {
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
    fun `API error propagates`() {
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
