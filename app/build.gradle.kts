plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.darkness.vpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.darkness.vpn"
        minSdk = 26
        targetSdk = 33
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("gecit") {
            storeFile = file("release.jks")
            storePassword = "gecitvpn"
            keyAlias = "gecit"
            keyPassword = "gecitvpn"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("gecit")
        }
        debug {
            signingConfig = signingConfigs.getByName("gecit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        // ciadpi ve xray çalıştırılabilir dosya olarak lib*.so adıyla gelir,
        // kurulumda diske çıkarılmaları gerekir.
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf("**/libxray.so", "**/libciadpi.so")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
