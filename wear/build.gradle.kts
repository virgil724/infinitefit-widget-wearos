plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.gymqrdisplayer.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gymqrdisplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Horologist layout helpers
    implementation(libs.horologist.compose.layout)

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
    implementation(libs.androidx.wear.tiles.material)

    // WearOS 文字輸入（RemoteInputIntentHelper）
    implementation(libs.wear.input)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation("com.google.guava:guava:33.1.0-android")
}
