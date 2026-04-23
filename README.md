# Sezzle Merchant SDK for Android

Let your customers pay with Sezzle directly in your Android app. The SDK handles checkout session creation, secure browser presentation, and promotional messaging — all with a single public key.

## Requirements

- Android API 23+ (Android 6.0 Marshmallow)
- Android Gradle Plugin 8.0+

## Installation

### Gradle (Maven Central)

Add to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sezzle:sezzle-merchant-sdk:1.0.0")
}
```

### Manual AAR

1. Download `sezzle-sdk-release.aar` from the [Releases](https://github.com/sezzle/sezzle-merchant-sdk-android/releases) page
2. Copy it to your app's `libs/` directory
3. Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/sezzle-sdk-release.aar"))
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

Show "or 4 interest-free payments of $X.XX with Sezzle" on your product or cart pages.

```kotlin
import com.sezzle.sdk.promotional.SezzlePromotionalView

val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 4999   // $49.99
)
promoView.setPresenter(this)  // for info modal on tap
linearLayout.addView(promoView)
```

Update when the price changes:

```kotlin
promoView.update(amountInCents = newTotalInCents)
```

The view auto-hides if the amount is below the $35 minimum or above the $2,500 maximum.

Tapping the view automatically opens an info modal showing how Sezzle works, with a 4-payment schedule and due dates.

### Styling

```kotlin
// Dark theme (light text for dark backgrounds)
val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 4999,
    style = SezzlePromotionalStyle.DARK
)

// Custom style
val customStyle = SezzlePromotionalStyle(
    textColor = Color.GRAY,
    textSizeSp = 13f
)
val promoView = SezzlePromotionalView(
    context = this,
    amountInCents = 4999,
    style = customStyle
)
```

### Custom Promo UI

If you want full control over the promotional message, use `SezzlePromoDataHandler` to get a raw `SpannableString`:

```kotlin
SezzlePromoDataHandler.getMessage(context, amountInCents = 4999) { spanned ->
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

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `customer.email` | Yes | Customer's email address |
| `customer.firstName` | No | Customer's first name |
| `customer.lastName` | No | Customer's last name |
| `customer.phone` | No | Customer's phone number |
| `order.referenceId` | Yes | Your internal order ID |
| `order.description` | No | Order description (defaults to "Mobile SDK Order") |
| `order.amount.amountInCents` | Yes | Total amount in cents (e.g., 4999 = $49.99) |
| `order.amount.currency` | Yes | ISO 4217 currency code ("USD", "CAD") |
| `order.intent` | No | `AUTH` (default) or `CAPTURE` |
| `order.items` | No | Line items in the order |

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
