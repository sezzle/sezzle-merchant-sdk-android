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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sezzle.example"
        minSdk = 21
        targetSdk = 36
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
