import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Secrets live in the gitignored local.properties and reach the code only through BuildConfig.
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "io.github.whatwhatwhoa.portallauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.whatwhatwhoa.portallauncher"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        // Empty when the key is missing: the art lookup is then switched off.
        buildConfigField("String", "STEAMGRIDDB_KEY", "\"${local.getProperty("steamgriddb.apiKey", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // No Material: every surface is drawn by us, so only foundation and ui.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.13.0")
}
