package com.sezzle.sdk

import com.sezzle.sdk.models.SezzleError
import com.sezzle.sdk.networking.SessionResponse
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionResponseTest {

    @Test
    fun `parses success response`() {
        val json = JSONObject("""
            {
                "uuid": "session-uuid-123",
                "order": {
                    "uuid": "order-uuid-456",
                    "checkout_url": "https://checkout.sezzle.com/checkout/abc",
                    "links": []
                },
                "tokenize": {}
            }
        """)

        val response = SessionResponse.fromJson(json)
        assertEquals("session-uuid-123", response.uuid)
        assertEquals("order-uuid-456", response.orderUUID)
        assertEquals("https://checkout.sezzle.com/checkout/abc", response.checkoutURL)
    }

    @Test
    fun `throws InvalidResponse for missing order`() {
        val json = JSONObject("""{ "uuid": "session-uuid" }""")
        try {
            SessionResponse.fromJson(json)
            fail("Expected SezzleError.InvalidResponse")
        } catch (e: SezzleError.InvalidResponse) {
            // expected
        }
    }

    @Test
    fun `throws InvalidResponse for missing checkout_url`() {
        val json = JSONObject("""
            {
                "uuid": "session-uuid",
                "order": {
                    "uuid": "order-uuid"
                }
            }
        """)
        try {
            SessionResponse.fromJson(json)
            fail("Expected SezzleError.InvalidResponse")
        } catch (e: SezzleError.InvalidResponse) {
            // expected
        }
    }

    @Test
    fun `throws InvalidResponse for empty JSON`() {
        val json = JSONObject("{}")
        try {
            SessionResponse.fromJson(json)
            fail("Expected SezzleError.InvalidResponse")
        } catch (e: SezzleError.InvalidResponse) {
            // expected
        }
    }
}
