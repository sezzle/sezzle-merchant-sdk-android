package com.sezzle.example

import android.app.Application
import com.sezzle.sdk.SezzleSDK
import com.sezzle.sdk.models.SezzleEnvironment

class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // The public key is read from local.properties at build time.
        // Add this line to your local.properties (not checked into git):
        //   sezzle.publicKey=sz_pub_your_key_here
        SezzleSDK.configure(
            publicKey = BuildConfig.SEZZLE_PUBLIC_KEY,
            environment = SezzleEnvironment.SANDBOX
        )
    }
}
