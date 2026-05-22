# Changelog

All notable changes to the Sezzle Merchant SDK for Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [1.2.5] - 2026-05-22

### Fixed
- **WebView checkout no longer leaks Sezzle session cookies across users on the same device.** `SezzleCheckoutWebViewActivity` now scrubs `*.sezzle.com` cookies and per-origin Web storage via `CookieManager` + `WebStorage.deleteOrigin(...)` immediately before each `WebView.loadUrl()`. Previously the activity inherited `android.webkit.CookieManager`'s app-wide persistent singleton, so Sezzle cookies set during one user's checkout persisted across merchant-app logouts and surfaced to the next user's first BNPL attempt — even though the SDK was given a different `SezzleCustomer.email` and a fresh `POST /v2/session` UUID. (Poshmark integration report — User A's credit-limit decline showing for User B after a logout/login.)

  The scrub is **scoped to Sezzle's own domains** (`checkout.sezzle.com`, `sandbox.checkout.sezzle.com`, `api.sezzle.com`, `sandbox.api.sezzle.com`, `sezzle.com`, `www.sezzle.com`, `sandbox.sezzle.com`). The merchant app's other cookies and storage are not touched — `CookieManager.removeAllCookies()` would also wipe merchant state and is intentionally avoided.

  Trade-off: returning Sezzle users now re-authenticate to Sezzle on each `WEB_VIEW` checkout in the same app. `SYSTEM_BROWSER` mode (Chrome Custom Tabs) is unaffected and continues to provide persistent Sezzle login via Chrome's cookie store. Use System Browser mode if cookie persistence matters for your UX.

### Compatibility
- No public API change. No new permissions. No new dependencies. Existing integrations recompile without modification.

## [1.2.4] - 2026-05-21

### Added
- **Lifecycle-safe WebView checkout via `ActivityResultLauncher`.** New `SezzleCheckoutContract` + `SezzleSDK.startCheckoutForResult(...)` overloads deliver the checkout result through the Android Activity Result API instead of the static `SezzleCheckoutListener` reference. The merchant's launcher callback is bound to `ActivityResultRegistry`, which Android re-binds when the host activity is destroyed and recreated — so the result still reaches the live host activity instance. Recommended path for merchants whose host activity may be destroyed mid-checkout under "Don't keep activities" / "Background process limit" / low-memory conditions. (MOBILE → Poshmark integration report)

  ```kotlin
  private val sezzleLauncher = registerForActivityResult(SezzleCheckoutContract()) { result ->
      when (result) {
          is SezzleCheckoutContract.Output.Complete -> { /* result.orderUuid */ }
          is SezzleCheckoutContract.Output.Cancel -> { /* user cancelled */ }
          is SezzleCheckoutContract.Output.Error -> { /* result.code, result.message */ }
      }
  }
  // Then:
  SezzleSDK.startCheckoutForResult(sezzleLauncher, checkout)
  ```

### Fixed
- **Listener-based `startCheckout` also delivers via Activity Result Intent for forward-compat.** `SezzleCheckoutWebViewActivity` now calls `setResult(...)` with the same Intent extras that `SezzleCheckoutContract` parses, regardless of which API was used to launch it. Existing listener-based integrations remain unchanged (the static listener path still fires), but the lifecycle-safe Intent path is always populated — merchants can migrate at their own pace.

### Notes
- **Scope:** the new API is `WEB_VIEW` mode only. The system-browser (Custom Tabs) flow has a different recreation profile — its result arrives via the SDK's bundled `SezzleRedirectActivity` intent-filter, not via `setResult`. Listener-based `startCheckout` continues to be the correct entrypoint for `SYSTEM_BROWSER` mode; a future release will address Custom Tabs lifecycle safety separately if needed.
- **No public API removals.** `SezzleCheckoutListener`-based `startCheckout` overloads are unchanged and continue to work — the new entrypoints are additions, not replacements.
- **User-dismissal semantics on the new API.** The close X, hardware back, and system-destroy paths all surface as `Output.Cancel` on the launcher path (more intuitive than `Error(BROWSER_DISMISSED)`). The legacy listener continues to receive `onCheckoutError(BrowserDismissed)` for the same events — no behavior change for existing integrations.
- **Static listener gated by launch source.** The legacy static listener is only invoked when the launch originated from a listener-based `startCheckout`; the new launcher path never touches it, so a stale listener from a prior legacy checkout cannot accidentally fire on a new-API call.
- **Overlap protection on the new API.** `startCheckoutForResult` holds the SDK's in-flight gate until the launcher's callback fires (via `parseResult`) — matching the listener path's debounce. Double-tapping the merchant's checkout button during in-flight checkout is a no-op, as it was on the legacy path.

### Compatibility
- No public API removals. No new permissions. No manifest changes required of merchants. Existing integrations recompile without modification. Merchants opting into the new API only need to add the launcher registration + swap their `startCheckout` call to `startCheckoutForResult`.

## [1.2.3] - 2026-05-14

### Security
- **`FLAG_SECURE` extended to `SezzleRedirectActivity`.** Completes the FLAG_SECURE coverage of every SDK-owned activity. The redirect activity is `Theme.Translucent.NoTitleBar` and renders nothing on screen, so the practical screenshot/Recent-Apps protection is unchanged from 1.2.2 — but the flag is now uniformly present across the SDK's activity surface, removing the inconsistency a posture scanner would flag.

### Compatibility
- No public API change. No new permissions. No manifest changes required of merchants. No behavior change for either checkout flow (SDK-creates-session, server-driven) or either presentation mode (`SYSTEM_BROWSER`, `WEB_VIEW`). Existing integrations recompile without modification.

## [1.2.2] - 2026-05-13

### Security
- **`FLAG_SECURE` enabled on `SezzleCheckoutWebViewActivity`.** Blocks manual screenshots, screen recording, the Recent Apps switcher live preview, and mirroring to external displays / casting while the WebView checkout is on screen. Scoped to this activity only — `SYSTEM_BROWSER` mode is unchanged (Chrome Custom Tabs owns its own window and applies its own screenshot policy on payment domains).
- **Checkout activities isolated into their own task affinity.** Added `android:taskAffinity=""` to both `SezzleRedirectActivity` (exported, `singleTask` — the classic StrandHogg target shape) and `SezzleCheckoutWebViewActivity` (defensive against StrandHogg 2.0 reparenting) in the SDK's bundled `AndroidManifest.xml`. Mitigates StrandHogg 1.0 and StrandHogg 2.0 / CVE-2020-0096 task-hijacking attacks where a malicious app overlays a spoofed UI by abusing default task affinity. Belt-and-suspenders posture for the SDK's `minSdk = 23` range, since Google's CVE-2020-0096 patches don't cover older OS versions still in the field.

### Example app
- `android:allowBackup="false"` on `example/src/main/AndroidManifest.xml` (was `true`, the historical AGP default). The example stores nothing, so this is a posture change only — but it nudges merchants copying from the canonical reference integration toward the correct payment-app default and clears the corresponding security-scanner finding pre-emptively.

### Compatibility
- No public API change. No new permissions. No manifest changes required of merchants. No behavior change for either checkout flow (SDK-creates-session, server-driven) or either presentation mode (`SYSTEM_BROWSER`, `WEB_VIEW`). Existing integrations recompile without modification.

## [1.2.1] - 2026-05-08

### Fixed
- **WebView checkout: close button now visible on Android 14+.** `SezzleCheckoutWebViewActivity` was rendering edge-to-edge with the white close-button header positioned behind the system status bar (`Theme.NoTitleBar` doesn't apply window insets on modern Android). Added `OnApplyWindowInsetsListener` on the root view that pads top + bottom by `systemBars` insets so the header is fully visible.
- **Reject overlapping `startCheckout` calls.** Rapid double-taps on a checkout button used to fire a second `startCheckout` while the first was still presenting, which could confuse Custom Tabs / WebView presentation and surface a bogus `onCheckoutError` to the merchant. `SezzleSDK` now tracks an in-progress flag and silently ignores overlapping calls until the first delivers its terminal callback. The flag is cleared via a `ProgressTrackingListener` wrapper.

### Example app
- **Back to Product** routes through an explicit `Intent(ProductActivity)` with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` instead of bare `finish()`. Defends against Android killing the paused `ProductActivity` during checkout under memory pressure (a real intermittent reproduction on Pixel 8a emulator), which would otherwise close the entire task and look like a crash to the user. Doesn't affect SDK consumers — merchants manage their own back-stack — but improves the demo experience.

## [1.2.0] - 2026-05-06

### Added
- Server-driven checkout entrypoint — `SezzleSDK.startCheckout(checkoutUrl, completeUrl, cancelUrl, activity, listener, mode)` for merchants whose backend creates the session via `POST /v2/session` directly. No public key on-device, no `SezzleSDK.configure()` required. Merchants supply their own callback URLs (any scheme) and the SDK intercepts navigation to them.
- `SezzleCheckoutResult` — unified result class exposing `orderUUID` (SDK-creates-session flow) or `callbackURL` (server-driven flow). The full callback URL is delivered so merchants can encode their own state in query params (e.g. `yourapp-sezzle://done?orderRef=12345`) and recover it on completion.

### Changed
- `SezzleCheckoutListener.onCheckoutComplete` now receives a `SezzleCheckoutResult` instead of a bare `orderUUID` string, unifying both flows behind a single listener method.
- `CheckoutHandler` URL match logic is now dynamic — compares scheme + host + path against the merchant's callback URLs (case-insensitive on scheme/host). Existing flow continues to use the hardcoded `sezzle-sdk://checkout/(confirmed|cancelled)` URLs.
- `WebViewActivity` URL interception (`shouldOverrideUrlLoading` modern + deprecated, `onPageFinished`, `onReceivedError`) now matches against the merchant-supplied complete/cancel URLs.
- `SezzleRedirectActivity` reads complete/cancel URLs from `CheckoutState` for the Custom Tabs fallback path.
- `SezzleEventLogger` no-ops gracefully on the server-driven flow (no public key = no events).

### Notes
- The SDK's bundled `AndroidManifest.xml` intent-filter still covers only `sezzle-sdk://checkout`. Merchants using a custom callback scheme with `SYSTEM_BROWSER` mode must register an intent-filter for their scheme in their own manifest. See README → Server-Driven Integration for details.

### Fixed
- Pinned `androidx.browser:browser` to `1.8.0` (was `1.10.0`) so consumer apps on `compileSdk 35` are no longer forced onto `compileSdk 36`. Removed the `AuthTabIntent` path (which required 1.10.0) — `SYSTEM_BROWSER` mode now always uses Chrome Custom Tabs, which is fully supported in 1.8.0 and works across all browser versions.
- Lowered the SDK module's own `compileSdk` from `36` to `35`. Combined with the `androidx.browser` pin above, consumer apps on `compileSdk 35+` can now consume this SDK without AAR-metadata errors. The example app is also pinned to `compileSdk 35` as a regression check.

## [1.1.0] - 2026-04-30

### Added
- Full POST /v2/session API support — all fields from the Sezzle API are now available:
  - `SezzleAddress` — billing and shipping addresses on `SezzleCustomer`
  - `SezzleDiscount` — order discount line items
  - `SezzleLocale` — checkout locale (`EN_US`, `EN_CA`, `FR_CA`)
  - `SezzleFinancingOption` — restrict to specific financing plans
  - `SezzleItem` gains `brand`, `imageUrl`, `productUrl`, `globalTradeItemNumber`, `manufacturerPartNumber`, `categoryPath`
  - `SezzleCustomer` gains `dob`, `billingAddress`, `shippingAddress`, `tokenize`, `recurring`, `recurringMetadata`
  - `SezzleOrder` gains `discounts`, `taxAmount`, `shippingAmount`, `metadata`, `requiresShippingInfo`, `locale`, `checkoutFinancingOptions`
- SDK event logging — fire-and-forget telemetry to Sezzle's event pipeline (`/sdk-event-logging`)
  - Events: `popup_created`, `loaded`, `success`, `cancel`, `failure`
  - Includes SDK version, platform, device model, OS version in user agent
  - Enables checkout funnel analytics and SDK attribution
- SDK metadata in order — `_sdk_platform`, `_sdk_version`, `_device_model`, `_os_version` automatically included in `order.metadata` for attribution tracking

### Changed
- `isWebView=true` now appended to checkout URL for both system browser and WebView modes (moved from WebView activity to CheckoutHandler)
- SDK version bumped to 1.1.0

## [1.0.4] - 2026-04-28

### Added
- Dark mode logo variant — white wordmark (`Sezzle_Logo_FullColor_WhiteWM`) for dark backgrounds
- `SezzleLogoVariant` enum for explicit light/dark logo control in `SezzlePromotionalStyle`
- Centralized modal colors: `MODAL_BG`, `MODAL_BG_DARK_MODE`, `HANDLE`, `HANDLE_DARK_MODE` in `SezzleBrand`

### Changed
- Info modal header now shows the official Sezzle logo image instead of "✦ sezzle" text
- High-quality logo PNGs (2394×599 @ 3x) converted from official CDN SVGs via cairosvg
- BottomSheetDialog background matches modal color to eliminate white corner artifacts

### Fixed
- Inline promo logo was always dark variant (checked `Color.WHITE` but dark style uses `#F9F5FD`)
- Footer text in short-term modal was hardcoded gray, ignoring dark mode `textSecondary`
- Removed non-functional WebView back button (Sezzle checkout SPA doesn't support browser history navigation)
- Removed "sezzle.com" title from WebView header — clean close-button-only design
- Handle `window.open` / `target="_blank"` links in WebView (TILA documents) — opens in system browser
- Handle file downloads in WebView — opens in system browser

## [1.0.1] - 2026-04-27

### Added
- `SezzleWidgetConfig` — configurable widget with PI4/PI5/long-term support matching sezzle-js source of truth
- PI4: "or 4 payments of $X" (default, under $50)
- PI5: "or 5 payments of $X" (enabled, $50+)
- Long-term: "or monthly payments as low as $X" (configurable threshold, APR amortization)
- Removed "interest-free" from all messaging (matches sezzle-js)
- `SezzleInfoModal` now shows PI4, PI5, or long-term modal based on price
- `SezzleCheckoutMode` — choose between `SYSTEM_BROWSER` (default) or `WEB_VIEW`
- WebView mode: loading spinner, white header with "sezzle.com", `isWebView=true` query param
- Example app shows all 4 widget variants: hidden, PI4, PI5, and long-term

### Fixed
- WebView checkout redirect: deprecated `shouldOverrideUrlLoading(String)` + `onPageFinished` + `onReceivedError` URL checks
- Lifecycle observer only fires `BrowserDismissed` for the checkout-launching activity
- `SezzleRedirectActivity` dispatches result on next frame after CLEAR_TOP navigation
- Auth Tab callback guarded with `resultDelivered` flag

## [1.0.0] - 2026-04-23

### Added
- `SezzleSDK.configure()` — configure the SDK with your public key and environment
- `SezzleSDK.startCheckout()` — start a Sezzle checkout via secure browser tab
- `SezzleCheckoutListener` — receive checkout completion, cancellation, and error callbacks
- `SezzlePromotionalView` — drop-in view showing installment messaging with Sezzle branding
- `SezzleInfoModal` — educational modal explaining how Sezzle works with payment schedule
- `SezzlePromoDataHandler` — raw `SpannableString` for custom promotional UI
- Full model types: `SezzleCheckout`, `SezzleCustomer`, `SezzleOrder`, `SezzleItem`, `SezzleAmount`, `SezzleIntent`
- Typed error handling via `SezzleError` sealed class
- Auth Tab support (Chrome 137+) for secure checkout with session persistence — the Android equivalent of iOS `ASWebAuthenticationSession`
- Automatic fallback to Chrome Custom Tabs on older browsers (Chrome < 137)
- Browser dismiss detection for both Auth Tab and Custom Tab paths
- Edge-to-edge display support for Android 16 (API 36)
- ProGuard consumer rules — zero manual configuration needed
- Example app with product page and result screen
- API key loaded from `local.properties` (gitignored) — never in source code
