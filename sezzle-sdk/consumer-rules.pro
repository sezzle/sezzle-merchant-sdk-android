# Sezzle Merchant SDK — ProGuard rules shipped with the AAR.
# These are automatically applied to the consumer's build.

# Keep all public API classes
-keep class com.sezzle.sdk.SezzleSDK { *; }
-keep class com.sezzle.sdk.SezzleCheckoutListener { *; }
-keep class com.sezzle.sdk.models.** { *; }
-keep class com.sezzle.sdk.promotional.SezzlePromotionalView { *; }
-keep class com.sezzle.sdk.promotional.SezzleInfoModal { *; }
-keep class com.sezzle.sdk.promotional.SezzlePromoDataHandler { *; }
-keep class com.sezzle.sdk.promotional.SezzlePromotionalStyle { *; }
-keep class com.sezzle.sdk.promotional.InstallmentCalculator { *; }

# Keep the redirect activity (launched by system intent)
-keep class com.sezzle.sdk.checkout.SezzleRedirectActivity { *; }
