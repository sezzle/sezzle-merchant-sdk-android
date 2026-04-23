package com.sezzle.sdk

import com.sezzle.sdk.models.*
import com.sezzle.sdk.networking.SessionRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRequestTest {

    private fun makeCheckout(
        description: String? = null,
        items: List<SezzleItem>? = null
    ): SezzleCheckout {
        return SezzleCheckout(
            customer = SezzleCustomer(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            ),
            order = SezzleOrder(
                referenceId = "order-123",
                description = description,
                amount = SezzleAmount(amountInCents = 4999, currency = "USD"),
                items = items
            )
        )
    }

    @Test
    fun `cancel_url uses sezzle-sdk scheme`() {
        val json = SessionRequest.fromCheckout(makeCheckout())
        val cancelUrl = json.getJSONObject("cancel_url")
        assertEquals("sezzle-sdk://checkout/cancelled", cancelUrl.getString("href"))
        assertEquals("GET", cancelUrl.getString("method"))
    }

    @Test
    fun `complete_url uses sezzle-sdk scheme`() {
        val json = SessionRequest.fromCheckout(makeCheckout())
        val completeUrl = json.getJSONObject("complete_url")
        assertEquals("sezzle-sdk://checkout/confirmed", completeUrl.getString("href"))
        assertEquals("GET", completeUrl.getString("method"))
    }

    @Test
    fun `order fields use snake_case`() {
        val json = SessionRequest.fromCheckout(makeCheckout())
        val order = json.getJSONObject("order")
        assertEquals("order-123", order.getString("reference_id"))
        assertTrue(order.has("order_amount"))
        val amount = order.getJSONObject("order_amount")
        assertEquals(4999, amount.getInt("amount_in_cents"))
        assertEquals("USD", amount.getString("currency"))
    }

    @Test
    fun `intent defaults to AUTH`() {
        val json = SessionRequest.fromCheckout(makeCheckout())
        assertEquals("AUTH", json.getJSONObject("order").getString("intent"))
    }

    @Test
    fun `description defaults to Mobile SDK Order when null`() {
        val json = SessionRequest.fromCheckout(makeCheckout(description = null))
        assertEquals("Mobile SDK Order", json.getJSONObject("order").getString("description"))
    }

    @Test
    fun `description uses provided value`() {
        val json = SessionRequest.fromCheckout(makeCheckout(description = "My Order"))
        assertEquals("My Order", json.getJSONObject("order").getString("description"))
    }

    @Test
    fun `customer fields use snake_case`() {
        val json = SessionRequest.fromCheckout(makeCheckout())
        val customer = json.getJSONObject("customer")
        assertEquals("test@example.com", customer.getString("email"))
        assertEquals("Test", customer.getString("first_name"))
        assertEquals("User", customer.getString("last_name"))
    }

    @Test
    fun `optional customer fields omitted when null`() {
        val checkout = SezzleCheckout(
            customer = SezzleCustomer(email = "test@example.com"),
            order = SezzleOrder(
                referenceId = "order-123",
                amount = SezzleAmount(amountInCents = 4999)
            )
        )
        val json = SessionRequest.fromCheckout(checkout)
        val customer = json.getJSONObject("customer")
        assertFalse(customer.has("first_name"))
        assertFalse(customer.has("last_name"))
        assertFalse(customer.has("phone"))
    }

    @Test
    fun `items included when provided`() {
        val items = listOf(
            SezzleItem(
                name = "Widget",
                sku = "W-001",
                quantity = 2,
                price = SezzleAmount(amountInCents = 2499, currency = "USD")
            )
        )
        val json = SessionRequest.fromCheckout(makeCheckout(items = items))
        val order = json.getJSONObject("order")
        val itemsArray = order.getJSONArray("items")
        assertEquals(1, itemsArray.length())

        val item = itemsArray.getJSONObject(0)
        assertEquals("Widget", item.getString("name"))
        assertEquals("W-001", item.getString("sku"))
        assertEquals(2, item.getInt("quantity"))
        assertEquals(2499, item.getJSONObject("price").getInt("amount_in_cents"))
    }

    @Test
    fun `items omitted when null`() {
        val json = SessionRequest.fromCheckout(makeCheckout(items = null))
        assertFalse(json.getJSONObject("order").has("items"))
    }
}
