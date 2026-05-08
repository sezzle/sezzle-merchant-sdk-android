# Changelog

All notable changes to the Sezzle Merchant SDK for Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [1.2.1] - 2026-05-08

### Fixed
- **WebView checkout: close button now visible on Android 14+.** `SezzleCheckoutWebViewActivity` was rendering edge-to-edge with the white close-button header positioned behind the system status bar (`Theme.NoTitleBar` doesn't apply window insets on modern Android). Added `OnApplyWindowInsetsListener` on the root view that pads top + bottom by `systemBars` insets so the header is fully visible.
- **Reject overlapping `startCheckout` calls.** Rapid double-taps on a checkout button used to fire a second `startCheckout` while the first was still presenting, which could confuse Custom Tabs / WebView presentation and surface a bogus `onCheckoutError` to the merchant. `SezzleSDK` now tracks an in-progress flag and silently ignores overlapping calls until the first delivers its terminal callback. The flag is cleared via a `ProgressTrackingListener` wrapper.

### Example app
- **Back to Product** routes through an explicit `Intent(ProductActivity)` with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` instead of bare `finish()`. Defends against Android killing the paused `ProductActivity` during checkout under memory pressure (a real intermittent reproduction on Pixel 8a emulator), which would otherwise close the entire task and look like a crash to the user. Doesn't affect merchant integrations — Poshmark and others manage their own back-stack — but improves the demo experience.

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
