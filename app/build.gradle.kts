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

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Prints the debug keystore's SHA-1/SHA-256 fingerprint to the build log. Firebase Phone
// Authentication needs this fingerprint registered (Project Settings -> your Android app -> Add
// fingerprint) before phone/OTP sign-in will work; email/password sign-in does not need it. Since
// CI caches one fixed debug keystore across builds, this fingerprint stays the same for every
// future build from this pipeline -- register it once and it never needs to be redone.
tasks.register("printDebugSha1") {
    doLast {
        val keystoreFile = File("${System.getProperty("user.home")}/.android/debug.keystore")
        if (!keystoreFile.exists()) {
            println("No debug keystore found at ${keystoreFile.absolutePath}")
            return@doLast
        }
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
        println("===== DEBUG KEYSTORE BASE64 (temporary, to commit a permanent one) =====")
        println(java.util.Base64.getEncoder().encodeToString(keystoreFile.readBytes()))
        println("===================================================================")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("printDebugSha1")
}
