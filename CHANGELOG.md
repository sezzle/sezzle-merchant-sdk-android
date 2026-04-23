# Changelog

All notable changes to the Sezzle Merchant SDK for Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [1.0.0] - 2026-04-22

### Added
- `SezzleSDK.configure()` — configure the SDK with your public key and environment
- `SezzleSDK.startCheckout()` — start a Sezzle checkout via Chrome Custom Tab
- `SezzleCheckoutListener` — receive checkout completion, cancellation, and error callbacks
- `SezzlePromotionalView` — drop-in view showing installment messaging with Sezzle branding
- `SezzleInfoModal` — educational modal explaining how Sezzle works with payment schedule
- `SezzlePromoDataHandler` — raw `SpannableString` for custom promotional UI
- Full model types: `SezzleCheckout`, `SezzleCustomer`, `SezzleOrder`, `SezzleItem`, `SezzleAmount`, `SezzleIntent`
- Typed error handling via `SezzleError` sealed class
- Browser dismiss detection via Activity lifecycle observer
- ProGuard consumer rules — zero manual configuration needed
- Example app with product page and result screen
