plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Lit une variable du fichier `.env` (racine du projet, gitignoré). Sert de repli aux clés API IA
 * (Gemini, OpenRouter) quand aucune clé n'est saisie dans l'app. Retourne "" si absent.
 */
fun readEnv(key: String): String {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return ""
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val idx = trimmed.indexOf('=')
        if (idx <= 0) return@forEach
        if (trimmed.substring(0, idx).trim() == key) {
            return trimmed.substring(idx + 1).trim().trim('"', '\'')
        }
    }
    return ""
}

android {
    namespace = "com.kairos.app"
    // compileSdk 37 requis par Compose 1.12.0-alpha03 (tiré par material3 1.5.0-alpha — M3 Expressive).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kairos.app"
        // Adaptive icons + Material You : minSdk 26 évite les bitmaps de secours.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Clés API IA de repli : local.properties prioritaire, sinon .env. Absente -> "" -> repli
        // inactif (l'utilisateur peut toujours saisir une clé dans les réglages, prioritaire).
        val geminiKey = (project.findProperty("gemini.api.key") as String?)
            ?.takeIf { it.isNotBlank() }
            ?: readEnv("GEMINI_API_KEY")
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        val openRouterKey = (project.findProperty("openrouter.api.key") as String?)
            ?.takeIf { it.isNotBlank() }
            ?: readEnv("OPENROUTER_API_KEY")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${openRouterKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            // R8 : retrait du code mort + obfuscation, et élagage des ressources inutilisées.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Expose GEMINI_API_KEY / OPENROUTER_API_KEY (repli .env) via BuildConfig.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.graphics.shapes)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // Rappels d'échéance : planification différée résiliente au redémarrage.
    implementation(libs.androidx.work.runtime.ktx)

    // Widget écran d'accueil (matrice 2×2) via Glance (Compose pour App Widgets).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Reformulation IA des notes (optionnelle) : HTTP + stockage chiffré de la clé API.
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    debugImplementation(libs.androidx.ui.tooling)
}
