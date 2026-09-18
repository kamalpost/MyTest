import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.focusphone"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.focusphone"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    signingConfigs {
        create("release") {
            // Throwaway sideload/testing key committed with the repo (same convention as
            // the VoxReader app in this repository) so every CI build installs as an
            // update over the previous one. Replace before any store distribution.
            storeFile = file("../release.keystore")
            storePassword = "focusphone1"
            keyAlias = "focusphone"
            keyPassword = "focusphone1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Keep the report, but a lint finding must not block the sideload build.
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Intentionally no AndroidX: the app uses only framework APIs (minSdk 26).
    testImplementation("junit:junit:4.13.2")
}
