package com.sezzle.sdk

import android.util.Base64
import com.sezzle.sdk.models.SezzleEnvironment
import com.sezzle.sdk.networking.HttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpClientTest {

    @Test
    fun `authorization header is Basic base64 of public key without colon`() {
        val client = HttpClient("sz_pub_test123", SezzleEnvironment.SANDBOX)
        val expected = "Basic " + Base64.encodeToString(
            "sz_pub_test123".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        assertEquals(expected, client.authorizationHeader)
    }

    @Test
    fun `authorization header does not contain colon`() {
        val client = HttpClient("sz_pub_test123", SezzleEnvironment.SANDBOX)
        // The base64 decoded value should be just the key, no colon
        val base64Part = client.authorizationHeader.removePrefix("Basic ")
        val decoded = String(Base64.decode(base64Part, Base64.NO_WRAP))
        assertEquals("sz_pub_test123", decoded)
        assertFalse(decoded.contains(":"))
    }

    @Test
    fun `platform header is valid base64 JSON with 3 fields`() {
        val client = HttpClient("sz_pub_test", SezzleEnvironment.SANDBOX)
        val decoded = String(Base64.decode(client.platformHeader, Base64.NO_WRAP))
        val json = JSONObject(decoded)

        assertEquals("mobile-sdk-android", json.getString("id"))
        assertTrue(json.has("version"))
        assertTrue(json.has("plugin_version"))
        assertEquals(json.getString("version"), json.getString("plugin_version"))
    }

    @Test
    fun `platform header id is mobile-sdk-android`() {
        val client = HttpClient("sz_pub_test", SezzleEnvironment.SANDBOX)
        val decoded = String(Base64.decode(client.platformHeader, Base64.NO_WRAP))
        val json = JSONObject(decoded)
        assertEquals("mobile-sdk-android", json.getString("id"))
    }

    @Test
    fun `sandbox environment uses sandbox gateway URL`() {
        assertEquals(
            "https://sandbox.gateway.sezzle.com",
            SezzleEnvironment.SANDBOX.gatewayUrl
        )
    }

    @Test
    fun `production environment uses production gateway URL`() {
        assertEquals(
            "https://gateway.sezzle.com",
            SezzleEnvironment.PRODUCTION.gatewayUrl
        )
    }
}
