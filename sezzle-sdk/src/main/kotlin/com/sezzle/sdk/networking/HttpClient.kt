package com.sezzle.sdk.networking

import android.util.Base64
import com.sezzle.sdk.models.SezzleEnvironment
import com.sezzle.sdk.models.SezzleError
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Internal HTTP client for Sezzle API requests.
 *
 * Handles Basic auth, Sezzle-Platform header, and JSON encoding/decoding.
 */
internal class HttpClient(
    private val publicKey: String,
    private val environment: SezzleEnvironment
) {
    companion object {
        internal const val SDK_VERSION = "1.1.0"
    }

    /** `Basic {base64(publicKey)}` — no colon. */
    internal val authorizationHeader: String
        get() {
            val encoded = Base64.encodeToString(
                publicKey.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            return "Basic $encoded"
        }

    /** Base64-encoded JSON with id, version, plugin_version. */
    internal val platformHeader: String
        get() {
            val json = JSONObject().apply {
                put("id", "mobile-sdk-android")
                put("version", SDK_VERSION)
                put("plugin_version", SDK_VERSION)
            }
            return Base64.encodeToString(
                json.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
        }

    /** Send a POST request with JSON body and return the raw response JSON string. */
    @Throws(SezzleError::class)
    fun post(path: String, body: JSONObject): JSONObject {
        val url = URL("${environment.gatewayUrl}$path")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.setRequestProperty("Sezzle-Platform", platformHeader)
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            if (responseCode !in 200..299) {
                val message = parseErrorMessage(responseBody) ?: "HTTP $responseCode"
                throw SezzleError.ApiError(responseCode, message)
            }

            return try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                throw SezzleError.InvalidResponse
            }
        } catch (e: SezzleError) {
            throw e
        } catch (e: Exception) {
            throw SezzleError.NetworkError(e)
        } finally {
            connection.disconnect()
        }
    }

    /** Gateway returns errors as array `[{"code":"...", "message":"..."}]` or dict. */
    private fun parseErrorMessage(body: String): String? {
        try {
            val array = JSONArray(body)
            if (array.length() > 0) {
                return array.getJSONObject(0).optString("message")
            }
        } catch (_: Exception) { }

        try {
            val obj = JSONObject(body)
            return obj.optString("message").ifEmpty { null }
        } catch (_: Exception) { }

        return null
    }
}
