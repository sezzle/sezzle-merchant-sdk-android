package com.sezzle.sdk.networking

import com.sezzle.sdk.models.SezzleError
import org.json.JSONObject

/** Parsed response from `POST /v2/session`. */
internal data class SessionResponse(
    val uuid: String,
    val orderUUID: String,
    val checkoutURL: String
) {
    companion object {
        /** Parse the JSON response. Throws [SezzleError.InvalidResponse] on failure. */
        @Throws(SezzleError::class)
        fun fromJson(json: JSONObject): SessionResponse {
            try {
                val orderObj = json.getJSONObject("order")
                return SessionResponse(
                    uuid = json.getString("uuid"),
                    orderUUID = orderObj.getString("uuid"),
                    checkoutURL = orderObj.getString("checkout_url")
                )
            } catch (_: Exception) {
                throw SezzleError.InvalidResponse
            }
        }
    }
}
