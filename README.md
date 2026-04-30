# Sezzle Merchant SDK for Android

Let your customers pay with Sezzle directly in your Android app. The SDK handles checkout session creation, secure browser presentation, and promotional messaging — all with a single public key.

## Requirements

- Android API 23+ (Android 6.0 Marshmallow)
- Android Gradle Plugin 8.9.1+
- Kotlin 2.2+
- compileSdk 36+

## Installation

### Gradle (Maven Central)

Add to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sezzle:sezzle-merchant-sdk:1.1.0")
}
```

### Manual AAR

1. Download `sezzle-merchant-sdk-{version}.aar` from the [Releases](https://github.com/sezzle/sezzle-merchant-sdk-android/releases) page
2. Copy it to your app's `libs/` directory
3. Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/sezzle-merchant-sdk-1.1.0.aar"))
    implementation("androidx.browser:browser:1.8.0")
}
```

## Get Your API Keys

1. Log into the [Sezzle Merchant Dashboard](https://dashboard.sezzle.com)
2. Go to **Settings > API Keys**
3. Copy your **public key** (starts with `sz_pub_...`)

> Sandbox and production keys are separate. Use your sandbox key during development and testing.

## Step 1: Configure the SDK

Call `configure` once at app startup, before any other SDK calls.

```kotlin
import com.sezzle.sdk.SezzleSDK
import com.sezzle.sdk.models.SezzleEnvironment

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SezzleSDK.configure(
            publicKey = "sz_pub_...",           // your public key from the dashboard
            environment = SezzleEnvironment.SANDBOX  // use PRODUCTION for live transactions
        )
    }
}
```

## Step 2: Add Promotional Messaging

The widget automatically shows the right message based on the product price:

- **Under $50:** "or 4 payments of $X.XX with Sezzle"
- **$50 and over:** "or 5 payments of $X.XX with Sezzle"
- **Long-term eligible:** "or monthly payments as low as $X.XX with Sezzle"
- **Below $35 or above $2,500:** hidden

```kotlin
import com.sezzle.sdk.promotional.SezzlePromotionalView

// Basic — uses default config (PI4 under $50, PI5 at $50+)
val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 4999   // $49.99 → "or 4 payments of $12.49"
)
promoView.setPresenter(this)
linearLayout.addView(promoView)
```

Update when the price changes:

```kotlin
promoView.update(amountInCents = newTotalInCents)
```

Tapping the view opens an info modal showing the payment schedule.

### Widget Configuration

Customize thresholds and enable long-term payments:

```kotlin
import com.sezzle.sdk.promotional.SezzleWidgetConfig
import com.sezzle.sdk.promotional.SezzleLongTermConfig

// Enable long-term monthly payments for orders over $250
val config = SezzleWidgetConfig(
    minPriceInCents = 3500,         // $35 minimum (default)
    maxPriceInCents = 250_000,      // $2,500 PI4/PI5 max (default)
    enablePayIn5 = true,            // 5-pay for $50+ (default: true)
    pi5MinPriceInCents = 5000,      // $50 PI5 threshold (default)
    longTermConfig = SezzleLongTermConfig(
        minPriceInCents = 25_000    // LT kicks in at $250+
    )
)

val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 79900,          // $799 → "or monthly payments as low as $36.13"
    widgetConfig = config
)
```

### Styling

`SezzlePromotionalView` automatically detects dark mode and selects the correct logo variant. You can also override the style manually:

```kotlin
// Explicit dark theme (light text + white logo for dark backgrounds)
val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 4999,
    style = SezzlePromotionalStyle.DARK
)
```

### Custom Promo UI

For full control over the promotional message:

```kotlin
SezzlePromoDataHandler.getMessage(context, amountInCents = 4999, widgetConfig = config) { spanned ->
    myTextView.text = spanned
}
```

### Manual Info Modal

Present the info modal programmatically:

```kotlin
SezzleInfoModal.present(amountInCents = 4999, activity = this)
```

## Step 3: Start Checkout

Build the order and start the checkout flow:

```kotlin
val checkout = SezzleCheckout(
    customer = SezzleCustomer(
        email = "jane@example.com",
        firstName = "Jane",
        lastName = "Doe"
    ),
    order = SezzleOrder(
        referenceId = "order-123",
        description = "Order from MyApp",
        amount = SezzleAmount(amountInCents = 4999, currency = "USD"),
        items = listOf(
            SezzleItem(
                name = "Premium Widget",
                sku = "widget-001",
                quantity = 1,
                price = SezzleAmount(amountInCents = 4999, currency = "USD")
            )
        )
    )
)

SezzleSDK.startCheckout(checkout, this, listener)
```

This opens the Sezzle checkout in a secure browser tab. On Chrome 137+, it uses Auth Tab for better session persistence. On older browsers, it falls back to Chrome Custom Tabs automatically. No WebView, no manifest changes — it just works.

### WebView Mode

To keep the user inside your app during checkout, use `WEB_VIEW` mode:

```kotlin
SezzleSDK.startCheckout(checkout, this, listener, mode = SezzleCheckoutMode.WEB_VIEW)
```

The checkout opens in an embedded WebView with a clean header. Trade-off: no cookie sharing with Chrome (user logs in every time).

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `customer.email` | Yes | Customer's email address |
| `customer.firstName` | No | Customer's first name |
| `customer.lastName` | No | Customer's last name |
| `customer.phone` | No | Customer's phone number |
| `customer.dob` | No | Date of birth (YYYY-MM-DD) |
| `customer.billingAddress` | No | Billing address (`SezzleAddress`) |
| `customer.shippingAddress` | No | Shipping address (`SezzleAddress`) |
| `customer.tokenize` | No | Enable customer tokenization for future orders |
| `order.referenceId` | Yes | Your internal order ID |
| `order.description` | No | Order description (defaults to "Mobile SDK Order") |
| `order.amount.amountInCents` | Yes | Total amount in cents (e.g., 4999 = $49.99) |
| `order.amount.currency` | Yes | ISO 4217 currency code ("USD", "CAD") |
| `order.intent` | No | `AUTH` (default) or `CAPTURE` |
| `order.items` | No | Line items in the order |
| `order.discounts` | No | Discount line items (`List<SezzleDiscount>`) |
| `order.taxAmount` | No | Tax amount breakdown |
| `order.shippingAmount` | No | Shipping amount breakdown |
| `order.metadata` | No | Custom key-value pairs (SDK metadata auto-included) |
| `order.locale` | No | Checkout locale (`EN_US`, `EN_CA`, `FR_CA`) |

## Step 4: Handle the Result

Implement `SezzleCheckoutListener` to receive callbacks:

```kotlin
class MyActivity : AppCompatActivity(), SezzleCheckoutListener {
    override fun onCheckoutComplete(orderUUID: String) {
        // Checkout succeeded!
        // Send orderUUID to your backend to capture the payment.
        Log.d("Sezzle", "Order UUID: $orderUUID")
    }

    override fun onCheckoutCancel() {
        // User cancelled the checkout from within the Sezzle checkout page.
    }

    override fun onCheckoutError(error: SezzleError) {
        // Something went wrong.
        Log.e("Sezzle", "Error: ${error.message}")
    }
}
```

All listener methods are called on the main thread.

## Step 5: Complete the Order (Server-Side)

After `onCheckoutComplete` returns the order UUID, your app sends it to your backend. Your backend then calls the Sezzle API to capture the payment:

```
POST https://gateway.sezzle.com/v2/order/{orderUUID}/capture
Authorization: Bearer {your_bearer_token}
Content-Type: application/json

{
  "capture_amount": {
    "amount_in_cents": 4999,
    "currency": "USD"
  }
}
```

> Capture, refund, release, and order status are always server-to-server calls using your private key. The SDK never handles these operations.

See the [Sezzle API documentation](https://docs.sezzle.com) for full details on server-side operations.

## Error Handling

| Error | When it happens | What to do |
|-------|----------------|------------|
| `NotConfigured` | `startCheckout` called before `configure` | Call `SezzleSDK.configure(...)` at app startup |
| `NetworkError(cause)` | No internet, timeout, DNS failure | Show a retry option to the user |
| `ApiError(statusCode, message)` | Sezzle API returned an error (e.g., invalid key, bad request) | Check the status code and message for details |
| `BrowserDismissed` | User pressed back in the checkout browser | Return to the previous screen |
| `InvalidResponse` | API response couldn't be parsed | Retry, or contact support if persistent |

## ProGuard

The SDK ships its own ProGuard rules in `consumer-rules.pro`. No manual configuration is needed — rules are automatically applied when your app's ProGuard/R8 runs.

## Example App

The repository includes a working example app that demonstrates the full integration.

To run it:

1. Clone the repository
2. Open the project in Android Studio
3. Add your sandbox public key to `local.properties` (this file is gitignored):
   ```properties
   sezzle.publicKey=sz_pub_your_key_here
   ```
4. Select an emulator or device and run the `example` module

The example app shows:
- **Product screen** with `SezzlePromotionalView` and a "Pay with Sezzle" button
- **Checkout flow** opening a Chrome Custom Tab and handling the result
- **Result screen** showing the order UUID on success, or the error on failure

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Checkout immediately fails with 401 | Wrong environment — sandbox key with `PRODUCTION` or vice versa | Match the environment to your key type |
| "Order amount too low" error | Order total is below $35 | Ensure `amountInCents` is at least 3500 |
| Browser opens and closes instantly | Invalid or expired public key | Verify your key in the Merchant Dashboard |
| Promotional view doesn't appear | Amount below $35 or above $2,500 | Check the amount is within the eligible range |
| `NotConfigured` error | `configure` wasn't called before `startCheckout` | Move `configure` to `Application.onCreate()` |

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

## License

MIT License. See [LICENSE](LICENSE) for details.
