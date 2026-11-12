plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.gymqrdisplayer.wear"
    compileSdk = 37 // wear compose 1.7 要求

    defaultConfig {
        applicationId = "io.github.virgil724.gymqr"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // Release 簽名：CI 由 GitHub Secrets 提供；本機沒設環境變數時 release 不簽名
    signingConfigs {
        create("release") {
            System.getenv("GYMQR_KEYSTORE")?.let { ks ->
                storeFile = file(ks)
                storePassword = System.getenv("GYMQR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GYMQR_KEY_ALIAS")
                keyPassword = System.getenv("GYMQR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("GYMQR_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Wear OS Compose
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // 網路 & QR（與 app/ 相同版本）
    implementation(project(":core"))
    implementation(libs.zxing)

    // 憑證儲存
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Data Layer（手錶端接收）
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Wear Tiles
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.protolayout.material3)

    // WearOS 文字輸入（RemoteInputIntentHelper）
    implementation(libs.wear.input)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation("com.google.guava:guava:33.1.0-android")
}
