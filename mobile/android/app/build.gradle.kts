import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// M11.4 — one API base URL per product flavor (environment dimension, below),
// each read as kairon.apiBaseUrl.<flavor>: checked in local.properties first
// (gitignored, per-machine — e.g. a physical device needs its own LAN IP for
// the "local" flavor instead of the emulator-loopback default), falling back
// to the committed default in gradle.properties. local.properties isn't
// auto-loaded into Gradle's property system the way gradle.properties is, so
// it's read by hand here.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
fun apiBaseUrlFor(flavor: String): String {
    val key = "kairon.apiBaseUrl.$flavor"
    return localProperties.getProperty(key) ?: providers.gradleProperty(key).get()
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.kairon.android"
    compileSdk = 37
    // Only build-tools 36.0.0 is installed locally; it still compiles against
    // the newer platform 37 SDK jar fine (aapt2/d8 aren't platform-API-coupled).
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.kairon.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // M11.4 follow-up — "which backend does this build talk to" is a build
    // variant, not a hand-edited property file: `local` is the Docker
    // Desktop k8s deployment (ingress-nginx on the host's :80, reached via
    // the emulator-loopback alias); `prod` is the real xbmc deploy over
    // Tailscale Funnel (HTTPS). applicationIdSuffix on `local` lets both be
    // installed side by side on one device/emulator.
    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            resValue("string", "app_name", "Kairon (Local)")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrlFor("local")}\"")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "Kairon")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrlFor("prod")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // AppLog (core.logging) calls plain android.util.Log directly, which
    // throws "not mocked" under the default unit-test android.jar stub —
    // this app has no Robolectric. isReturnDefaultValues makes every
    // unmocked Android SDK call (Log included) return its default instead.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":openapi-client"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.security.crypto)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
