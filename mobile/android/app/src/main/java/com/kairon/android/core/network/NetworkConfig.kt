package com.kairon.android.core.network

import com.kairon.android.BuildConfig

/**
 * `BuildConfig.API_BASE_URL` is set at build time per product flavor (M11.4
 * follow-up — `app/build.gradle.kts`'s "environment" flavor dimension):
 * "local" targets the Docker Desktop k8s deployment's ingress-nginx
 * (`http://10.0.2.2/kairon/` by default — `10.0.2.2` is the Android
 * emulator's alias for the host loopback interface), "prod" targets the
 * xbmc deploy over Tailscale Funnel (HTTPS). Each flavor's own
 * `kairon.apiBaseUrl.<flavor>` Gradle property can still be overridden
 * per-machine in the gitignored `local.properties`.
 */
object NetworkConfig {
    val BASE_URL: String = BuildConfig.API_BASE_URL
}
