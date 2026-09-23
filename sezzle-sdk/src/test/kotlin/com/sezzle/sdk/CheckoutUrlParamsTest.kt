package com.sezzle.sdk

import android.net.Uri
import com.sezzle.sdk.checkout.CheckoutHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutUrlParamsTest {

    @Test
    fun `appends isNativeSDK`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "light")
        assertEquals("true", uri.getQueryParameter("isNativeSDK"))
    }

    /**
     * isNativeSDK alone suppresses checkout's navigation bar, so isWebView is redundant for
     * chrome. Sending it is actively harmful: checkout reads it as the Sezzle consumer app and
     * routes TILA through a React Native postMessage that no merchant app receives.
     */
    @Test
    fun `does not append isWebView`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "light")
        assertNull(uri.getQueryParameter("isWebView"))
    }

    @Test
    fun `preserves existing query params`() {
        val uri = CheckoutHandler.appendSdkParams(
            "https://checkout.sezzle.com/?id=abc&locale=en-US",
            "light"
        )
        assertEquals("abc", uri.getQueryParameter("id"))
        assertEquals("en-US", uri.getQueryParameter("locale"))
    }

    @Test
    fun `appends dark theme`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "dark")
        assertEquals("dark", uri.getQueryParameter("theme"))
    }

    @Test
    fun `appends light theme`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "light")
        assertEquals("light", uri.getQueryParameter("theme"))
    }

    /**
     * Null theme is the lifecycle-safe launcher path, which has no activity to detect night
     * mode from. SezzleCheckoutWebViewActivity fills the theme in later from its own context.
     */
    @Test
    fun `null theme omits the param`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", null)
        assertNull(uri.getQueryParameter("theme"))
        assertEquals("true", uri.getQueryParameter("isNativeSDK"))
    }

    /**
     * A theme already on the checkout URL is the merchant's explicit choice — the
     * auto-detected app appearance must not clobber it.
     */
    @Test
    fun `existing theme is not overridden`() {
        val uri = CheckoutHandler.appendSdkParams(
            "https://checkout.sezzle.com/?id=abc&theme=dark",
            "light"
        )
        assertEquals(listOf("dark"), uri.getQueryParameters("theme"))
    }

    @Test
    fun `url with no existing query string`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/checkout", "dark")
        assertEquals("true", uri.getQueryParameter("isNativeSDK"))
        assertEquals("dark", uri.getQueryParameter("theme"))
    }

    @Test
    fun `resolveTheme returns light for a non-night context`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals("light", CheckoutHandler.resolveTheme(context))
    }

    @Test
    fun `resolveTheme returns dark when the context reports night mode`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val nightConfig = android.content.res.Configuration(context.resources.configuration)
        nightConfig.uiMode =
            (nightConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val nightContext = context.createConfigurationContext(nightConfig)
        assertEquals("dark", CheckoutHandler.resolveTheme(nightContext))
    }

    @Test
    fun `theme param survives a round trip through Uri parsing`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "dark")
        val reparsed = Uri.parse(uri.toString())
        assertEquals("dark", reparsed.getQueryParameter("theme"))
        assertEquals("true", reparsed.getQueryParameter("isNativeSDK"))
    }
}
