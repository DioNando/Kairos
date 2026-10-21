plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kairos.wear"
    compileSdk = 37

    defaultConfig {
        // Même applicationId que le téléphone : requis par la Wearable Data Layer pour appairer les
        // deux apps (même package + même clé de signature).
        applicationId = "com.kairos.app"
        // Wear OS 3 (Galaxy Watch 4 et +) = API 30.
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Pas de minification : évite les règles de conservation Tiles/ProtoLayout ; APK montre léger.
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

dependencies {
    implementation(libs.androidx.core.ktx)

    // Data Layer : réception des compteurs poussés par le téléphone + open-on-phone.
    implementation(libs.play.services.wearable)
    implementation(libs.wear.remote.interactions)

    // Tuile Wear OS (rendu déclaratif ProtoLayout, pas de Compose).
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)

    // ListenableFuture / Futures.immediateFuture pour les callbacks du TileService.
    implementation(libs.guava)
}
