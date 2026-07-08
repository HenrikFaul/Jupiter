plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jupiter.filemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jupiter.filemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.37.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Google Drive OAuth Web client id. Supply your own (Google Cloud Console →
        // OAuth client of type "Web application") via a Gradle property, e.g. in
        // ~/.gradle/gradle.properties or -PJUPITER_GDRIVE_WEB_CLIENT_ID=... . When empty
        // the app shows a "set up Google Drive" notice instead of attempting sign-in.
        val gdriveWebClientId = (project.findProperty("JUPITER_GDRIVE_WEB_CLIENT_ID") as String?).orEmpty()
        buildConfigField("String", "GDRIVE_WEB_CLIENT_ID", "\"$gdriveWebClientId\"")
    }

    // Fixed debug keystore committed to the repo so the signing certificate (and thus
    // its SHA-1) is STABLE across machines/CI — required for Google Sign-In, whose
    // Android OAuth client is keyed to (package + SHA-1). Standard debug credentials.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/jupiter-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug signing so CI can produce an installable release APK out of the box.
            // Replace with a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Lint still runs and reports, but a finding won't abort assembleRelease,
        // so the release APK artifact is always produced. Run lint as its own gate.
        abortOnError = false
    }

    testOptions {
        unitTests {
            // Robolectric needs Android resources + real framework return values so Room
            // and Context-backed code run under the JVM unit-test task.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // View-based Material Components: provides the XML launch theme parent (Theme.Material3.*)
    implementation(libs.material)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Storage / preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Room — persistent file index (fast browse/search/duplicate reuse)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Security / vault
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // Image / video thumbnails
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Video transcoding / compression (device-aware resolution + bitrate)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    // Home-screen widget (favorite folders / files)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Remote / network protocols (LAN + transfer). Resolved from Maven Central on CI.
    implementation(libs.smbj)          // SMB2/3
    implementation(libs.commons.net)   // FTP/FTPS
    implementation(libs.jsch)          // SFTP
    implementation(libs.okhttp)        // WebDAV + cloud REST (incl. Google Drive v3)
    implementation(libs.nanohttpd)     // embedded HTTP server for Wi-Fi desktop transfer

    // Google account sign-in + Drive authorization (Credential Manager + Identity).
    // The Drive REST v3 calls go through OkHttp above; no heavyweight google-api client.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Extended archive formats
    implementation(libs.commons.compress)  // tar / gz / bzip2 / 7z
    implementation(libs.xz)                 // 7z LZMA support for commons-compress
    implementation(libs.junrar)             // RAR extraction

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric lets Room (in-memory) and Android-framework code run under the JVM
    // `testDebugUnitTest` task, so the index state-machine / generation / stale-sweep
    // behavior is actually EXERCISED in CI (this project's CI has no emulator).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
