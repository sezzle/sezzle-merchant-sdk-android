package com.sezzle.sdk

import com.sezzle.sdk.models.SezzleError
import org.junit.Assert.*
import org.junit.Test

class SezzleErrorTest {

    @Test
    fun `NotConfigured has correct message`() {
        val error = SezzleError.NotConfigured
        assertTrue(error.message.contains("not configured"))
        assertTrue(error.message.contains("SezzleSDK.configure()"))
    }

    @Test
    fun `NetworkError includes underlying message`() {
        val error = SezzleError.NetworkError(RuntimeException("timeout"))
        assertTrue(error.message.contains("timeout"))
    }

    @Test
    fun `ApiError includes status code and message`() {
        val error = SezzleError.ApiError(401, "Unauthorized")
        assertTrue(error.message.contains("401"))
        assertTrue(error.message.contains("Unauthorized"))
    }

    @Test
    fun `BrowserDismissed has correct message`() {
        val error = SezzleError.BrowserDismissed
        assertTrue(error.message.contains("dismissed"))
    }

    @Test
    fun `InvalidResponse has correct message`() {
        val error = SezzleError.InvalidResponse
        assertTrue(error.message.contains("parse"))
    }

    @Test
    fun `all errors extend Exception`() {
        assertTrue(SezzleError.NotConfigured is Exception)
        assertTrue(SezzleError.NetworkError(RuntimeException()) is Exception)
        assertTrue(SezzleError.ApiError(400, "bad") is Exception)
        assertTrue(SezzleError.BrowserDismissed is Exception)
        assertTrue(SezzleError.InvalidResponse is Exception)
    }

    @Test
    fun `all errors are SezzleError`() {
        assertTrue(SezzleError.NotConfigured is SezzleError)
        assertTrue(SezzleError.NetworkError(RuntimeException()) is SezzleError)
        assertTrue(SezzleError.ApiError(400, "bad") is SezzleError)
        assertTrue(SezzleError.BrowserDismissed is SezzleError)
        assertTrue(SezzleError.InvalidResponse is SezzleError)
    }
}
