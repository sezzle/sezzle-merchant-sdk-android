# Changelog

All notable changes to the Sezzle Merchant SDK for Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

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
