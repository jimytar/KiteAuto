plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jimytar.kiteauto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jimytar.kiteauto"
        minSdk = 29
        targetSdk = 36
        // -PappVersion=X.Y.Z (build.sh; CI derives it from Conventional Commits). versionCode = XXYYZZ.
        val version = (findProperty("appVersion") as String?) ?: "0.0.0-dev"
        val (major, minor, patch) = (version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + 0 + 0)
        versionName = version
        versionCode = maxOf(1, major * 10000 + minor * 100 + patch)
    }

    // One stable key for debug and release, so every build (adb, KingInstaller, GitHub release) upgrades in
    // place. Path from KITEAUTO_KEYSTORE (Docker build secret) or the local keystore/ created by build.sh.
    val keyFile = System.getenv("KITEAUTO_KEYSTORE")?.let(::file) ?: rootProject.file("keystore/debug.keystore")
    signingConfigs {
        create("kite") {
            storeFile = keyFile
            storePassword = System.getenv("KITEAUTO_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KITEAUTO_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("KITEAUTO_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        getByName("debug") {
            if (keyFile.exists()) signingConfig = signingConfigs.getByName("kite")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (keyFile.exists()) signingConfig = signingConfigs.getByName("kite")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Unofficial Android Auto SDK (CarActivity), not redistributed: fetched by tools/fetch-aauto.sh.
    implementation(files("libs/aauto.aar"))
    implementation(libs.fragment) // CarActivity hosts a FragmentController
}
