package com.kairon.android.core.network

import com.kairon.android.BuildConfig

/**
 * `BuildConfig.API_BASE_URL` is set at build time from the `kairon.apiBaseUrl`
 * Gradle property (M11.4) — `local.properties` (gitignored, per-machine) first,
 * falling back to the emulator-dev default `gradle.properties` commits.
 * `10.0.2.2` is the Android emulator's alias for the host machine's loopback
 * interface, matching a `./gradlew :backend:bootRun`-backed backend on
 * `localhost:8080` with the fixed `/kairon` context path (docs/DESIGN.md §3.3).
 */
object NetworkConfig {
    val BASE_URL: String = BuildConfig.API_BASE_URL
}
