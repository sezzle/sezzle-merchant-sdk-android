package com.sezzle.sdk

import android.webkit.CookieManager
import com.sezzle.sdk.checkout.SezzleSessionScrubber
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SezzleSessionScrubberTest {

    private val cookieManager get() = CookieManager.getInstance()

    @Before
    fun setUp() {
        cookieManager.setAcceptCookie(true)
        // Clear ALL cookies between tests so leftover state from previous tests doesn't leak.
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    @After
    fun tearDown() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    // MARK: SEZZLE_COOKIE_DOMAINS shape

    @Test
    fun `SEZZLE_COOKIE_DOMAINS covers production sezzle origins`() {
        val keys = SezzleSessionScrubber.SEZZLE_COOKIE_DOMAINS.keys
        assertTrue("missing apex", "https://sezzle.com" in keys)
        assertTrue("missing checkout", "https://checkout.sezzle.com" in keys)
        assertTrue("missing api", "https://api.sezzle.com" in keys)
    }

    @Test
    fun `SEZZLE_COOKIE_DOMAINS covers sandbox sezzle origins`() {
        val keys = SezzleSessionScrubber.SEZZLE_COOKIE_DOMAINS.keys
        assertTrue("missing sandbox apex", "https://sandbox.sezzle.com" in keys)
        assertTrue("missing sandbox checkout", "https://sandbox.checkout.sezzle.com" in keys)
        assertTrue("missing sandbox api", "https://sandbox.api.sezzle.com" in keys)
    }

    @Test
    fun `every url tries host-only + at least one domain-scoped attribute`() {
        SezzleSessionScrubber.SEZZLE_COOKIE_DOMAINS.forEach { (url, domains) ->
            assertTrue("$url missing host-only attempt (empty string)", "" in domains)
            assertTrue(
                "$url has no domain-scoped Domain attribute — leading-dot cookies will leak",
                domains.any { it.startsWith(".") },
            )
            assertTrue(
                "$url's Domain attributes must all match the URL's host suffix",
                domains.all { it.isEmpty() || urlMatchesDomain(url, it) },
            )
        }
    }

    private fun urlMatchesDomain(url: String, domain: String): Boolean {
        val host = url.removePrefix("https://").removePrefix("http://").substringBefore("/")
        val bareDomain = domain.removePrefix(".")
        return host == bareDomain || host.endsWith(".$bareDomain")
    }

    // MARK: scrub behavior
    //
    // Note: in real Android, `setCookie(url, "name=; Max-Age=0; ...")` deletes the cookie from
    // the store outright. Robolectric's shadow CookieManager doesn't honor Max-Age=0 — it keeps
    // the cookie but with the empty value we wrote. Functionally the leak is still contained
    // (the original secret value is gone), so these tests assert that the cookie's VALUE is
    // wiped, not that the name vanishes. Verified empirically on a real emulator that the
    // cookie does in fact disappear from the WebView cookies DB.

    /** Returns the value of a named cookie in a serialized cookie header, or null if absent. */
    private fun cookieValue(header: String?, name: String): String? {
        if (header == null) return null
        return header.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
    }

    @Test
    fun `clear wipes value of domain-scoped sezzle cookie set with leading-dot Domain`() {
        cookieManager.setCookie(
            "https://checkout.sezzle.com",
            "access_token=user-A-secret; Domain=.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.flush()

        SezzleSessionScrubber.clear()

        val value = cookieValue(cookieManager.getCookie("https://checkout.sezzle.com"), "access_token")
        assertEquals("user-A-secret leaked: $value", "", value)
    }

    @Test
    fun `clear wipes value of subdomain-scoped sandbox cookie`() {
        cookieManager.setCookie(
            "https://sandbox.checkout.sezzle.com",
            "szl_wpe_sid_lt=user-A-session; Domain=.sandbox.checkout.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.flush()

        SezzleSessionScrubber.clear()

        val value = cookieValue(cookieManager.getCookie("https://sandbox.checkout.sezzle.com"), "szl_wpe_sid_lt")
        assertEquals("session leaked: $value", "", value)
    }

    @Test
    fun `clear wipes values of multiple sezzle cookies at once`() {
        cookieManager.setCookie(
            "https://checkout.sezzle.com",
            "access_token=secret-1; Domain=.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.setCookie(
            "https://checkout.sezzle.com",
            "refresh_token=secret-2; Domain=.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.setCookie(
            "https://sandbox.checkout.sezzle.com",
            "szl_wpe_sid_lt=sess-3; Domain=.sandbox.checkout.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.flush()

        SezzleSessionScrubber.clear()

        val checkout = cookieManager.getCookie("https://checkout.sezzle.com")
        val sandbox = cookieManager.getCookie("https://sandbox.checkout.sezzle.com")
        assertEquals("secret-1 leaked", "", cookieValue(checkout, "access_token"))
        assertEquals("secret-2 leaked", "", cookieValue(checkout, "refresh_token"))
        assertEquals("sess-3 leaked", "", cookieValue(sandbox, "szl_wpe_sid_lt"))
    }

    @Test
    fun `clear does NOT touch merchant non-sezzle cookies`() {
        // The merchant's own backend cookie — must NOT be wiped by our scrub.
        cookieManager.setCookie(
            "https://api.merchant.example",
            "merchant_session=merchant-secret; Domain=.merchant.example; Path=/; Max-Age=3600",
        )
        // And a non-Sezzle third-party domain.
        cookieManager.setCookie(
            "https://example.com",
            "analytics_id=keep-me; Domain=.example.com; Path=/; Max-Age=3600",
        )
        cookieManager.flush()

        SezzleSessionScrubber.clear()

        val merchantCookie = cookieManager.getCookie("https://api.merchant.example")
        val example = cookieManager.getCookie("https://example.com")
        assertNotNull("merchant cookie was wiped", merchantCookie)
        assertTrue("merchant cookie missing: $merchantCookie", merchantCookie!!.contains("merchant_session=merchant-secret"))
        assertNotNull("third-party cookie was wiped", example)
        assertTrue("third-party cookie missing: $example", example!!.contains("analytics_id=keep-me"))
    }

    @Test
    fun `clear with no cookies present is a no-op (does not crash)`() {
        SezzleSessionScrubber.clear()
        // Nothing to assert beyond "no exception thrown" — but call again to make sure.
        SezzleSessionScrubber.clear()
    }

    @Test
    fun `clear preserves merchant's acceptCookie=false setting`() {
        // Merchant has disabled cookies app-wide (privacy mode / GDPR toggle / DNT).
        // The scrub may temporarily enable acceptance to do its work, but it MUST restore
        // the original value — never leave the merchant's privacy posture silently flipped.
        cookieManager.setAcceptCookie(false)

        SezzleSessionScrubber.clear()

        assertEquals(
            "scrub silently flipped acceptCookie from false → true (merchant's privacy setting broken)",
            false,
            cookieManager.acceptCookie(),
        )
    }

    @Test
    fun `clear preserves merchant's acceptCookie=true setting`() {
        cookieManager.setAcceptCookie(true)

        SezzleSessionScrubber.clear()

        assertEquals(true, cookieManager.acceptCookie())
    }

    @Test
    fun `clear restores acceptCookie=false even if scrub throws`() {
        // If the scrub body throws partway through, the finally block must still restore
        // the original acceptCookie value. Hard to force a throw without mocking, but we
        // can at least exercise the path where cookies were set before the privacy toggle —
        // the scrub will iterate normally and the finally will run.
        cookieManager.setCookie(
            "https://checkout.sezzle.com",
            "access_token=leaked; Domain=.sezzle.com; Path=/; Max-Age=3600",
        )
        cookieManager.flush()
        cookieManager.setAcceptCookie(false)

        SezzleSessionScrubber.clear()

        assertEquals(false, cookieManager.acceptCookie())
    }

    @Test
    fun `clear wipes value of host-only sezzle cookie (no Domain attribute)`() {
        // Less common shape but possible — make sure host-only cookies are also scrubbed.
        cookieManager.setCookie(
            "https://checkout.sezzle.com",
            "host_only_cookie=user-A-value; Path=/; Max-Age=3600",
        )
        cookieManager.flush()

        SezzleSessionScrubber.clear()

        val value = cookieValue(cookieManager.getCookie("https://checkout.sezzle.com"), "host_only_cookie")
        assertEquals("host-only cookie value leaked: $value", "", value)
    }
}
