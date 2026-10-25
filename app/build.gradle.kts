import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Secrets live in the gitignored local.properties and reach the code only through BuildConfig.
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val GITHUB_LATEST = "https://api.github.com/repos/whatwhatwhoa/PortalLauncher/releases/latest"

android {
    namespace = "io.github.whatwhatwhoa.portallauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.whatwhatwhoa.portallauncher"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        // Where Updates asks for the latest release (F18).
        buildConfigField("String", "UPDATE_URL", "\"$GITHUB_LATEST\"")
    }

    // The release key stays outside the repo; local.properties says where it is. Without it a
    // release build is left unsigned.
    val releaseStore = local.getProperty("release.storeFile")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = local.getProperty("release.storePassword")
                keyAlias = local.getProperty("release.keyAlias")
                keyPassword = local.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // The owner's own builds may carry their SteamGridDB key; empty, it is given in
            // Launcher Settings like everyone else's.
            buildConfigField("String", "STEAMGRIDDB_KEY", "\"${local.getProperty("steamgriddb.apiKey", "")}\"")
            // An update can be tried before it is on GitHub: update.url names a stand-in for the
            // releases API, e.g. a laptop server reached through adb reverse (gotchas.md *Updates*).
            buildConfigField("String", "UPDATE_URL", "\"${local.getProperty("update.url", GITHUB_LATEST)}\"")
        }
        release {
            // A published APK never carries a key: users bring their own.
            buildConfigField("String", "STEAMGRIDDB_KEY", "\"\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // The release build under its own package, so the R8 output can run beside the owner's
        // debug install without wiping it. The owner's key lets art search be checked; never shipped.
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".check"
            versionNameSuffix = "-check"
            buildConfigField("String", "STEAMGRIDDB_KEY", "\"${local.getProperty("steamgriddb.apiKey", "")}\"")
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
