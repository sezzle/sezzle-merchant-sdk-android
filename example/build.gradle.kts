import java.util.Properties

plugins {
    id("com.android.application")
}

// Read the Sezzle public key from local.properties (not checked into git)
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}
val sezzlePublicKey = localProps.getProperty("sezzle.publicKey", "YOUR_SANDBOX_PUBLIC_KEY")

android {
    namespace = "com.sezzle.example"
    // Pinned to 35 (intentionally one below the latest) as a regression check that the
    // published SDK stays consumable from compileSdk-35 merchant apps. If you bump this,
    // also verify the SDK's own compileSdk in `sezzle-sdk/build.gradle.kts` doesn't drift
    // higher than what merchants on 35 can support.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sezzle.example"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Inject the key into BuildConfig so it's never in source code
        buildConfigField("String", "SEZZLE_PUBLIC_KEY", "\"$sezzlePublicKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":sezzle-sdk"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
}
