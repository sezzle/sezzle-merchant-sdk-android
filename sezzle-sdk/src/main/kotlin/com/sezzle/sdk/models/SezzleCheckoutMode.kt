package com.sezzle.sdk.models

/**
 * How the Sezzle checkout is presented to the user.
 */
enum class SezzleCheckoutMode {
    /**
     * Opens checkout in the system browser (Auth Tab on Chrome 137+, Custom Tab on older).
     *
     * This is the **recommended** mode:
     * - Secure: runs in a separate browser process
     * - Shares cookies with Chrome (faster login if already signed into Sezzle)
     * - Cannot be manipulated by the merchant app
     */
    SYSTEM_BROWSER,

    /**
     * Opens checkout in a WebView embedded inside the app.
     *
     * Use this when you want the user to stay inside your app during checkout.
     * Trade-offs vs system browser:
     * - No cookie sharing with Chrome (user logs in every time)
     * - Runs inside the app's process
     */
    WEB_VIEW
}
