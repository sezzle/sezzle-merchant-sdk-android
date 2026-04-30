package com.sezzle.sdk.networking

import android.os.Build
import android.util.Base64
import com.sezzle.sdk.models.SezzleEnvironment
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fire-and-forget event logging to `{gateway}/sdk-event-logging`.
 */
internal class SezzleEventLogger(
    private val publicKey: String,
    private val environment: SezzleEnvironment
) {
    enum class Event(val value: String) {
        POPUP_CREATED("popup_created"),
        RENDER_POPUP("render_popup"),
        LOADED("loaded"),
        SUCCESS("success"),
        CANCEL("cancel"),
        FAILURE("failure"),
        LOG_ERROR("log_error")
    }

    private val userAgent: String = "SezzleMerchantSDK-Android/${HttpClient.SDK_VERSION} (${Build.MODEL}; Android ${Build.VERSION.RELEASE})"

    fun log(
        event: Event,
        sessionUUID: String = "",
        orderUUID: String = "",
        checkoutUUID: String = "",
        mode: String = "",
        message: String = "",
        payloadSupplied: Boolean = false
    ) {
        val body = JSONObject().apply {
            put("version", HttpClient.SDK_VERSION)
            put("event", event.value)
            put("user_agent", userAgent)
            put("session_uuid", sessionUUID)
            put("order_uuid", orderUUID)
            put("payload_supplied", payloadSupplied)
            put("checkout_uuid", checkoutUUID)
            put("mode", mode)
            put("message", message)
        }

        // Fire-and-forget on a background thread
        Thread {
            try {
                val url = URL("${environment.gatewayUrl}/sdk-event-logging")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                val encoded = Base64.encodeToString(publicKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $encoded")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                conn.responseCode // trigger the request
                conn.disconnect()
            } catch (_: Exception) {
                // Silently ignore — event logging should never crash the app
            }
        }.start()
    }
}
