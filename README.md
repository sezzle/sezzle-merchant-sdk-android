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
    implementation("com.sezzle:sezzle-merchant-sdk:1.2.6")
}
```

### Manual AAR

1. Download `sezzle-merchant-sdk-{version}.aar` from the [Releases](https://github.com/sezzle/sezzle-merchant-sdk-android/releases) page
2. Copy it to your app's `libs/` directory
3. Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/sezzle-merchant-sdk-1.2.6.aar"))
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

This opens the Sezzle checkout in Chrome Custom Tabs — a secure browser tab that runs in its own process and shares cookies with Chrome (faster login for returning users). No WebView, no manifest changes — it just works.

### WebView Mode

To keep the user inside your app during checkout, use `WEB_VIEW` mode:

```kotlin
SezzleSDK.startCheckout(checkout, this, listener, mode = SezzleCheckoutMode.WEB_VIEW)
```

The checkout opens in an embedded WebView with a clean header. Trade-off: no cookie sharing with Chrome (user logs in every time).

#### Lifecycle-safe variant (recommended)

For `WEB_VIEW` mode, prefer the `startCheckoutForResult` overload — it delivers the result through Android's [Activity Result API](https://developer.android.com/training/basics/intents/result), which survives host-activity destruction. The listener-based path above stores your callback in a static field; if Android destroys and recreates your activity mid-checkout (developer options like "Don't keep activities", low-memory conditions, etc.), the callback can be lost. The Activity Result path is bound to the registry, which Android re-attaches on recreation:

```kotlin
class MyActivity : ComponentActivity() {
    private val sezzleLauncher = registerForActivityResult(SezzleCheckoutContract()) { result ->
        when (result) {
            is SezzleCheckoutContract.Output.Complete -> {
                // result.orderUuid for SDK-creates-session flow
                // result.callbackUrl for server-driven flow
            }
            SezzleCheckoutContract.Output.Cancel -> { /* user dismissed */ }
            is SezzleCheckoutContract.Output.Error -> {
                // result.code (one of SezzleCheckoutContract.ErrorCode constants)
                // result.message (human-readable)
            }
        }
    }

    // Later, at checkout time:
    SezzleSDK.startCheckoutForResult(
        launcher = sezzleLauncher,
        checkout = checkout,
        onError = { /* pre-launch failure: NotConfigured / NetworkError / ApiError */ },
    )
    // ...or, for the server-driven flow:
    SezzleSDK.startCheckoutForResult(
        launcher = sezzleLauncher,
        checkoutUrl = checkoutUrl,
        completeUrl = completeUrl,
        cancelUrl = cancelUrl,
        onError = { /* launcher.launch threw — host activity gone */ },
    )
}
```

The `SYSTEM_BROWSER` (Custom Tabs) mode has a different recreation profile and continues to use the listener-based `startCheckout`.

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
    override fun onCheckoutComplete(result: SezzleCheckoutResult) {
        // Checkout succeeded!
        result.orderUUID?.let {
            // SDK-creates-session flow — send to your backend for capture
            Log.d("Sezzle", "Order UUID: $it")
        }
        result.callbackURL?.let {
            // Server-driven flow — read query params you encoded
            Log.d("Sezzle", "Callback URL: $it")
        }
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

## Server-Driven Integration (BYO Session)

For larger merchants who prefer a fully server-driven integration — no public key on-device, the backend owns session creation, capture, and refunds — use the pass-URL `startCheckout` overload. The SDK opens the URL, intercepts your chosen callback URLs, and reports back via `SezzleCheckoutListener`.

### Step 1 — Backend creates the session

Your backend creates a Sezzle session — see the [`POST /v2/session` reference](https://docs.sezzle.com/docs/api/core/sessions/postv2session) for the full request contract. Two SDK-specific notes:

- **Choose your own `complete_url` / `cancel_url`.** Any URL works — pick a custom scheme like `yourapp-sezzle://...` or HTTPS deep links to a domain you control. You can encode state in the query string (e.g. `yourapp-sezzle://done?orderRef=12345`) and recover it in the SDK callback.
- **Persist `order.uuid` server-side** before responding to the app — the app only needs `order.checkout_url` plus the two callback URLs.

### Step 2 — App presents checkout

```kotlin
SezzleSDK.startCheckout(
    checkoutUrl = checkoutUrl,                                      // from order.checkout_url
    completeUrl = Uri.parse("yourapp-sezzle://checkout/done"),       // same as your server's complete_url.href
    cancelUrl = Uri.parse("yourapp-sezzle://checkout/cancelled"),    // same as your server's cancel_url.href
    activity = this,
    listener = this,
    mode = SezzleCheckoutMode.WEB_VIEW   // or SYSTEM_BROWSER
)
```

`SezzleSDK.configure(...)` is **not** required for this flow — there's nothing for the SDK to authenticate.

### Step 3 — Read the result

```kotlin
override fun onCheckoutComplete(result: SezzleCheckoutResult) {
    val callbackURL = result.callbackURL ?: return
    val orderRef = callbackURL.getQueryParameter("orderRef")
    // Look up `orderRef` in your backend, then call /v2/order/{order.uuid}/capture
}
```

### Manifest note for `SYSTEM_BROWSER` mode

`SYSTEM_BROWSER` mode opens checkout in Chrome Custom Tabs and routes the redirect back to your app via the OS intent system. The SDK ships an intent-filter for `sezzle-sdk://checkout` only — if you use a custom callback scheme, register an intent-filter for your scheme in your own `AndroidManifest.xml`, pointing at `SezzleRedirectActivity`:

```xml
<activity
    android:name="com.sezzle.sdk.checkout.SezzleRedirectActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    tools:replace="android:exported">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="yourapp-sezzle" android:host="checkout" />
    </intent-filter>
</activity>
```

`WEB_VIEW` mode needs no manifest work — any scheme is intercepted by the WebView client directly.

### Notes

- **Match your URLs.** Whatever your backend passed as `complete_url.href` / `cancel_url.href`, pass the same `Uri` to `startCheckout`. The SDK matches on scheme + host + path; query params on the inbound URL are read by you.
- **`order.uuid` lives on your server.** It's not in the `checkout_url` and isn't echoed back — your backend already has it from the session-creation response.

### Working example

The bundled example app (`example/`) has a **Server-driven flow** card at the top of the product list with two buttons — **System Browser** (uses `sezzle-example://`, with the matching intent-filter in `example/src/main/AndroidManifest.xml`) and **WebView** (uses HTTPS callbacks) — that exercise both modes end-to-end against the sandbox API. Use it as a copy-pastable reference: see `example/src/main/kotlin/com/sezzle/example/ProductActivity.kt` for the request shape, callback URL choices, and `result.callbackURL` parsing.

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
