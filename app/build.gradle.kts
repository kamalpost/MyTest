plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.swingtrader.sp500"
    compileSdk = 30

    defaultConfig {
        // Fresh application id (code stays in com.swingtrader.sp500): early
        // builds were signed with throwaway keys, and any leftover install of
        // them blocks every update. A new id can never conflict.
        applicationId = "com.swingtrader.scanner"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.2.3"
    }

    // Same key as build-apk.sh so Gradle- and script-built APKs can update
    // each other. A committed debug key is fine for a sideloaded personal app.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.p12")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Framework-only app: the sole runtime dependency is the Kotlin stdlib
// (added automatically by the Kotlin Android plugin). No AndroidX on purpose —
// see README ("Building"): this project also compiles without Gradle/AGP via
// build-apk.sh using just kotlinc + aapt2 + d8 + apksig.
dependencies {
}
