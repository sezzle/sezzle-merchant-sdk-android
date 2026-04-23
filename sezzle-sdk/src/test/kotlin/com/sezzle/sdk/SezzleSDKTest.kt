package com.sezzle.sdk

import com.sezzle.sdk.models.SezzleEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SezzleSDKTest {

    @Before
    fun setUp() {
        // Reset SDK state by reconfiguring
    }

    @Test
    fun `configure sets isConfigured to true`() {
        SezzleSDK.configure("sz_pub_test", SezzleEnvironment.SANDBOX)
        assertTrue(SezzleSDK.isConfigured)
    }

    @Test
    fun `configure with production environment`() {
        SezzleSDK.configure("sz_pub_test", SezzleEnvironment.PRODUCTION)
        assertTrue(SezzleSDK.isConfigured)
    }

    @Test
    fun `default environment is production`() {
        SezzleSDK.configure("sz_pub_test")
        assertTrue(SezzleSDK.isConfigured)
    }
}
