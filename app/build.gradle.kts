plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.avinash.yatramitra"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.avinash.yatramitra"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // A debug keystore committed to the repo (app/debug.keystore) rather than relying on Android
    // tooling's auto-generated default. That default's location has moved between Android SDK
    // versions (historically ~/.android/debug.keystore, more recently ~/.config/.android on some
    // environments), and CI runners get fresh images with no prior state -- either way, an
    // implicit default means the signing key silently changes whenever the environment changes or
    // a CI cache lapses, breaking in-place updates of a sideloaded APK ("App not installed") with
    // no warning. A committed keystore makes the signing key a fixed, known fact forever, matching
    // one already-registered Firebase phone-auth SHA-1 fingerprint.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Firebase (BoM manages versions for all Firebase libraries below).
    // Since BoM 32+, Kotlin extensions ship inside the main artifacts, so no "-ktx" suffix is needed.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // Coroutines <-> Play Services task interop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Plain HTTP client for the free OpenStreetMap-based place search / routing / pitstop lookups.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ProviderInstaller (com.google.android.gms.security) -- patches a device's TLS/crypto
    // provider at runtime via Play Services. Some Android devices carry an outdated security
    // provider that fails the TLS handshake against certain modern servers ("Handshake failed")
    // even though the server itself is fine; this is Google's documented fix.
    implementation("com.google.android.gms:play-services-base:18.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM unit tests (app/src/test) -- pure-logic tests, no device/emulator needed.
    testImplementation("junit:junit:4.13.2")
}

// Prints the committed debug keystore's SHA-1/SHA-256 fingerprint to the build log, purely as a
// convenience -- register it once in Firebase Console (Project Settings -> your Android app ->
// Add fingerprint) to enable Phone/OTP sign-in. Since the keystore itself is now a fixed file
// committed to the repo rather than an environment-dependent default, this fingerprint is a
// permanent fact and this task will print the same value on every future build.
tasks.register("printDebugSha1") {
    doLast {
        val keystoreFile = file("debug.keystore")
        val process = ProcessBuilder(
            "keytool", "-list", "-v",
            "-keystore", keystoreFile.absolutePath,
            "-alias", "androiddebugkey",
            "-storepass", "android",
            "-keypass", "android"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        println("===== DEBUG KEYSTORE FINGERPRINTS (for Firebase phone auth) =====")
        println(output)
        println("===================================================================")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("printDebugSha1")
    // Run the JVM unit test suite as part of the same `./gradlew assembleDebug` the CI workflow
    // already runs, so a broken test fails the build (and blocks a broken APK from shipping)
    // without needing a separate CI workflow step.
    dependsOn("testDebugUnitTest")
}
