plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.swingtrader.sp500"
    compileSdk = 30

    defaultConfig {
        applicationId = "com.swingtrader.sp500"
        minSdk = 26
        targetSdk = 29
        versionCode = 3
        versionName = "1.2"
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
