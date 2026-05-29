package com.sezzle.sdk.checkout

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.sezzle.sdk.models.SezzleEnvironment
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Ends Sezzle's session and wipes Sezzle-domain cookies + per-origin Web storage from the
 * app-wide WebView state.
 *
 * Android's [CookieManager] is an app-wide persistent singleton — any cookie set during a
 * previous Sezzle checkout (auth tokens, session identifiers) persists across users on the
 * same device, even after the merchant logs the user out of their own app and a different
 * user signs in. Wiping locally is necessary but not sufficient: Sezzle's backend also
 * keeps a refresh-token-bound session that can re-recognize the device on the next checkout
 * and pre-bind it to the prior user's account. Calling `/v4/users/logout` with the WebView's
 * cookies makes the backend forget that binding so the next user starts truly fresh.
 *
 * The local scrub is **scoped to Sezzle's own domains** — the merchant app's other cookies are
 * untouched. Calling `removeAllCookies()` would wipe the merchant's state too and is unsafe here.
 *
 * Invoked by the public [com.sezzle.sdk.SezzleSDK.clearWebViewData] API, which merchants are
 * expected to call from their logout flow. Not called automatically by the SDK.
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

    /**
     * Auth cookie names set by Sezzle's checkout backend. `/v4/users/logout` accepts these as
     * the `Cookie` header. Other Sezzle cookies are wiped locally but not forwarded.
     */
    private val AUTH_COOKIE_NAMES: Set<String> = setOf("access_token", "refresh_token")

    /** Short timeout so a slow / unreachable logout endpoint never blocks the merchant's logout flow. */
    private const val LOGOUT_TIMEOUT_MS = 5000

    /**
     * Test seam: defaults to firing a real HTTP POST. Unit tests swap this to a capture-only
     * spy (or no-op) to avoid hitting `api.sezzle.com` during Robolectric runs.
     */
    internal var serverLogout: (SezzleEnvironment, String) -> Unit = ::defaultServerLogout

    fun clear(environment: SezzleEnvironment? = null) {
        // Step 1 (best-effort): ask Sezzle's backend to invalidate the refresh token tied to
        // this device's WebView session. Errors / timeouts are swallowed by design — the local
        // scrub below always runs even if the network call fails.
        invalidateServerSession(environment ?: SezzleEnvironment.PRODUCTION)

        // Step 2: local scrub (cookies + Web storage for Sezzle origins).
        clearLocalData()
    }

    private fun invalidateServerSession(environment: SezzleEnvironment) {
        val cookieManager = CookieManager.getInstance()
        val authCookies = collectAuthCookies(cookieManager)
        if (authCookies.isEmpty()) return
        val cookieHeader = authCookies.entries.joinToString(separator = "; ") { "${it.key}=${it.value}" }
        serverLogout(environment, cookieHeader)
    }

    private fun defaultServerLogout(environment: SezzleEnvironment, cookieHeader: String) {
        // Network on the main thread would crash with NetworkOnMainThreadException. Spin up a
        // throwaway thread, fire-and-forget. We don't propagate result/errors — the local scrub
        // is the merchant-facing guarantee; the server call is defense-in-depth.
        thread(start = true, isDaemon = true, name = "sezzle-logout") {
            try {
                val url = URL("${environment.apiUrl}/v4/users/logout")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = LOGOUT_TIMEOUT_MS
                    readTimeout = LOGOUT_TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Cookie", cookieHeader)
                    // Don't let the JVM's app-wide CookieHandler attach unrelated cookies.
                    instanceFollowRedirects = false
                    doOutput = true
                }
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                conn.responseCode // drain
                conn.disconnect()
            } catch (_: Throwable) {
                // best-effort
            }
        }
    }

    /**
     * Reads `access_token` + `refresh_token` cookies from [CookieManager] across the known
     * Sezzle origins. The first non-blank value wins per cookie name; duplicates across host
     * variants (e.g. cookie scoped to `.sezzle.com` visible at both `sezzle.com` and
     * `checkout.sezzle.com`) just collapse to a single entry.
     */
    private fun collectAuthCookies(cookieManager: CookieManager): Map<String, String> {
        val found = linkedMapOf<String, String>()
        SEZZLE_COOKIE_DOMAINS.keys.forEach { url ->
            val cookieString = cookieManager.getCookie(url) ?: return@forEach
            cookieString.split(";").forEach { rawPair ->
                val pair = rawPair.trim()
                val eqIdx = pair.indexOf('=')
                if (eqIdx <= 0) return@forEach
                val name = pair.substring(0, eqIdx).trim()
                val value = pair.substring(eqIdx + 1).trim()
                if (name in AUTH_COOKIE_NAMES && value.isNotEmpty() && name !in found) {
                    found[name] = value
                }
            }
        }
        return found
    }

    private fun clearLocalData() {
        val cookieManager = CookieManager.getInstance()

        // CookieManager is an app-wide singleton. If the merchant has set `acceptCookie=false`
        // process-wide (GDPR / privacy / DNT toggle), `setCookie(...)` is a no-op and our scrub
        // can't delete anything. Temporarily enable acceptance so the scrub can run, then
        // restore the original value in `finally` — never leave it flipped, or we'd silently
        // break the merchant's privacy posture for the rest of the process lifetime.
        val originalAcceptCookie = cookieManager.acceptCookie()
        if (!originalAcceptCookie) cookieManager.setAcceptCookie(true)
        try {
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
        } finally {
            if (!originalAcceptCookie) cookieManager.setAcceptCookie(false)
        }

        // Also clear localStorage / sessionStorage / IndexedDB for the same origins —
        // Sezzle's checkout page caches auth state there in addition to cookies on some flows.
        val storage = WebStorage.getInstance()
        SEZZLE_COOKIE_DOMAINS.keys.forEach { storage.deleteOrigin(it) }
    }
}
