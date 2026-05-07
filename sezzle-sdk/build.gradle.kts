plugins {
    id("com.android.library")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.sezzle.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Pinned to 1.8.0: 1.10.0 requires compileSdk 36, which forces consumer apps onto 36+
    // and breaks merchants still on 35. Custom Tabs (used here) is fully supported in 1.8.0;
    // we lose the AuthTab path (Chrome ≥137) but Custom Tabs covers all browsers.
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.sezzle"
                artifactId = "sezzle-merchant-sdk"
                version = "1.2.0"
                from(components["release"])

                pom {
                    name = "Sezzle Merchant SDK"
                    description = "Native Android SDK for merchant apps to offer Sezzle BNPL at checkout"
                    url = "https://github.com/sezzle/sezzle-merchant-sdk-android"

                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/licenses/MIT"
                        }
                    }

                    developers {
                        developer {
                            id = "sezzle"
                            name = "Sezzle"
                            email = "sezzle-app-owner@sezzle.com"
                        }
                    }

                    scm {
                        connection = "scm:git:github.com/sezzle/sezzle-merchant-sdk-android.git"
                        developerConnection = "scm:git:ssh://github.com/sezzle/sezzle-merchant-sdk-android.git"
                        url = "https://github.com/sezzle/sezzle-merchant-sdk-android/tree/main"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "Local"
                url = uri(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    signing {
        sign(publishing.publications["release"])
    }
}
