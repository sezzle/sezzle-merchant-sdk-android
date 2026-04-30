package com.sezzle.sdk.networking

import android.os.Build
import com.sezzle.sdk.models.SezzleAddress
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
            customer.dob?.let { put("dob", it) }
            customer.billingAddress?.let { put("billing_address", addressJson(it)) }
            customer.shippingAddress?.let { put("shipping_address", addressJson(it)) }
            customer.tokenize?.let { put("tokenize", it) }
            customer.recurring?.let { put("recurring", it) }
            customer.recurringMetadata?.let { meta ->
                put("recurring_metadata", JSONObject().apply {
                    for ((key, value) in meta) put(key, value)
                })
            }
        }
    }

    private fun addressJson(address: SezzleAddress): JSONObject {
        return JSONObject().apply {
            address.name?.let { put("name", it) }
            address.street?.let { put("street", it) }
            address.street2?.let { put("street2", it) }
            address.city?.let { put("city", it) }
            address.state?.let { put("state", it) }
            address.postalCode?.let { put("postal_code", it) }
            address.countryCode?.let { put("country_code", it) }
            address.phone?.let { put("phone", it) }
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
                        item.brand?.let { put("brand", it) }
                        item.imageUrl?.let { put("image_url", it) }
                        item.productUrl?.let { put("product_url", it) }
                        item.globalTradeItemNumber?.let { put("global_trade_item_number", it) }
                        item.manufacturerPartNumber?.let { put("manufacturer_part_number", it) }
                        item.categoryPath?.let { put("category_path", it) }
                    })
                }
                put("items", itemsArray)
            }

            order.discounts?.let { discounts ->
                val arr = JSONArray()
                for (d in discounts) {
                    arr.put(JSONObject().apply {
                        put("name", d.name)
                        put("amount", amountJson(d.amount.amountInCents, d.amount.currency))
                    })
                }
                put("discounts", arr)
            }

            order.taxAmount?.let { put("tax_amount", amountJson(it.amountInCents, it.currency)) }
            order.shippingAmount?.let { put("shipping_amount", amountJson(it.amountInCents, it.currency)) }

            // Merge SDK metadata with user metadata
            val mergedMeta = JSONObject().apply {
                put("_sdk_platform", "android")
                put("_sdk_version", HttpClient.SDK_VERSION)
                put("_device_model", Build.MODEL)
                put("_os_version", Build.VERSION.RELEASE)
            }
            order.metadata?.let { userMeta ->
                for ((key, value) in userMeta) mergedMeta.put(key, value)
            }
            put("metadata", mergedMeta)

            order.requiresShippingInfo?.let { put("requires_shipping_info", it) }
            order.locale?.let { put("locale", it.value) }
            order.checkoutFinancingOptions?.let { options ->
                val arr = JSONArray()
                for (opt in options) arr.put(opt.value)
                put("checkout_financing_options", arr)
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
