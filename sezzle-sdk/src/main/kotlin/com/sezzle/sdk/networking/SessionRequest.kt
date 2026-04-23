package com.sezzle.sdk.networking

import com.sezzle.sdk.models.SezzleCheckout
import org.json.JSONArray
import org.json.JSONObject

/** Builds the JSON body for `POST /v2/session`. */
internal object SessionRequest {

    fun fromCheckout(checkout: SezzleCheckout): JSONObject {
        return JSONObject().apply {
            put("cancel_url", urlRef("sezzle-sdk://checkout/cancelled"))
            put("complete_url", urlRef("sezzle-sdk://checkout/confirmed"))
            put("customer", customerJson(checkout))
            put("order", orderJson(checkout))
        }
    }

    private fun urlRef(href: String): JSONObject {
        return JSONObject().apply {
            put("href", href)
            put("method", "GET")
        }
    }

    private fun customerJson(checkout: SezzleCheckout): JSONObject {
        val customer = checkout.customer
        return JSONObject().apply {
            put("email", customer.email)
            customer.firstName?.let { put("first_name", it) }
            customer.lastName?.let { put("last_name", it) }
            customer.phone?.let { put("phone", it) }
        }
    }

    private fun orderJson(checkout: SezzleCheckout): JSONObject {
        val order = checkout.order
        return JSONObject().apply {
            put("intent", order.intent.value)
            put("reference_id", order.referenceId)
            put("description", order.description ?: "Mobile SDK Order")
            put("order_amount", amountJson(order.amount.amountInCents, order.amount.currency))

            order.items?.let { items ->
                val itemsArray = JSONArray()
                for (item in items) {
                    itemsArray.put(JSONObject().apply {
                        put("name", item.name)
                        item.sku?.let { put("sku", it) }
                        put("quantity", item.quantity)
                        put("price", amountJson(item.price.amountInCents, item.price.currency))
                    })
                }
                put("items", itemsArray)
            }
        }
    }

    private fun amountJson(amountInCents: Int, currency: String): JSONObject {
        return JSONObject().apply {
            put("amount_in_cents", amountInCents)
            put("currency", currency)
        }
    }
}
