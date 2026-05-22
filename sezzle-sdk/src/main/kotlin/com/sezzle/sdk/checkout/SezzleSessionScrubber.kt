package com.sezzle.sdk.checkout

import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Wipes Sezzle-domain cookies + per-origin Web storage from the app-wide WebView state.
 *
 * Android's [CookieManager] is an app-wide persistent singleton — any cookie set during a
 * previous Sezzle checkout (auth tokens, session identifiers) persists across users on the
 * same device, even after the merchant logs the user out of their own app and a different
 * user signs in. Without this scrub, User A's Sezzle session can satisfy the checkout page's
 * "returning customer" cookie check on User B's first attempt, surfacing A's credit-limit /
 * decline state to B. (Reported by Poshmark — iOS has the same bug, fixed there via
 * `WKWebsiteDataStore.nonPersistent()`; Android has no per-WebView ephemeral store equivalent
 * on minSdk 23 without pulling in `androidx.webkit` multi-profile, so we scrub by domain instead.)
 *
 * The scrub is **scoped to Sezzle's own domains** — the merchant app's other cookies are
 * untouched. Calling `removeAllCookies()` would wipe the merchant's state too and is unsafe
 * here.
 *
 * Called from [SezzleCheckoutWebViewActivity.onCreate] immediately before `webView.loadUrl(...)`.
 */
internal object SezzleSessionScrubber {

    /**
     * Maps a candidate URL to the list of `Domain` attributes to try when expiring each
     * cookie name found at that URL.
     *
     * Sezzle sets most cookies with `Domain=.sezzle.com` (leading dot = domain-scoped, applies
     * to all subdomains), but some are scoped to specific subdomains like
     * `.sandbox.checkout.sezzle.com`. We have to enumerate because `CookieManager.getCookie`
     * returns names but not the Domain attribute they were set with, and `setCookie` only
     * expires a cookie when `(url, name, domain, path)` all match the existing entry — a call
     * without the right Domain attribute creates a new host-only cookie instead of expiring
     * the existing domain-scoped one.
     *
     * Both sandbox and production hosts are listed because a single SDK build can be pointed
     * at either via [com.sezzle.sdk.models.SezzleEnvironment]. `Path=/` is the only path
     * Sezzle's auth cookies use, verified by inspecting the WebView cookies DB during testing.
     */
    val SEZZLE_COOKIE_DOMAINS: Map<String, List<String>> = mapOf(
        "https://sezzle.com" to listOf("", ".sezzle.com"),
        "https://www.sezzle.com" to listOf("", ".sezzle.com"),
        "https://checkout.sezzle.com" to listOf("", ".sezzle.com", ".checkout.sezzle.com"),
        "https://api.sezzle.com" to listOf("", ".sezzle.com", ".api.sezzle.com"),
        "https://sandbox.sezzle.com" to listOf("", ".sezzle.com", ".sandbox.sezzle.com"),
        "https://sandbox.checkout.sezzle.com" to listOf(
            "",
            ".sezzle.com",
            ".checkout.sezzle.com",
            ".sandbox.checkout.sezzle.com",
        ),
        "https://sandbox.api.sezzle.com" to listOf(
            "",
            ".sezzle.com",
            ".api.sezzle.com",
            ".sandbox.api.sezzle.com",
        ),
    )

    fun clear() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        SEZZLE_COOKIE_DOMAINS.forEach { (url, domains) ->
            val cookies = cookieManager.getCookie(url) ?: return@forEach
            val names = cookies.split(";")
                .map { it.substringBefore("=").trim() }
                .filter { it.isNotEmpty() }
            names.forEach { name ->
                domains.forEach { domain ->
                    val cookieStr = buildString {
                        append(name).append("=; Path=/; Max-Age=0")
                        if (domain.isNotEmpty()) append("; Domain=").append(domain)
                    }
                    cookieManager.setCookie(url, cookieStr)
                }
            }
        }
        cookieManager.flush()

        // Also clear localStorage / sessionStorage / IndexedDB for the same origins —
        // Sezzle's checkout page caches auth state there in addition to cookies on some flows.
        val storage = WebStorage.getInstance()
        SEZZLE_COOKIE_DOMAINS.keys.forEach { storage.deleteOrigin(it) }
    }
}
