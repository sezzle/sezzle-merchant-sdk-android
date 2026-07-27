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
    fun `appends isMerchantSDK`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "light")
        assertEquals("true", uri.getQueryParameter("isMerchantSDK"))
    }

    /**
     * isWebView is what suppresses checkout's own navigation bar. The SDK draws its own
     * close-button header, so dropping this flag stacks two bars. It must be sent alongside
     * isMerchantSDK, not replaced by it.
     */
    @Test
    fun `appends isWebView alongside isMerchantSDK`() {
        val uri = CheckoutHandler.appendSdkParams("https://checkout.sezzle.com/?id=abc", "light")
        assertEquals("true", uri.getQueryParameter("isWebView"))
        assertEquals("true", uri.getQueryParameter("isMerchantSDK"))
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
        assertEquals("true", uri.getQueryParameter("isMerchantSDK"))
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
        assertEquals("true", uri.getQueryParameter("isWebView"))
        assertEquals("true", uri.getQueryParameter("isMerchantSDK"))
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
        assertEquals("true", reparsed.getQueryParameter("isMerchantSDK"))
    }
}
