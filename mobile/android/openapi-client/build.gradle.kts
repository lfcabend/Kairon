import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generated-only module (D10/D8) — never hand-edited. Pure Kotlin/JVM (no
// Android dependency: the kotlin/jvm-retrofit2 generator output is plain
// OkHttp + Retrofit + kotlinx.serialization), consumed by :app as a project
// dependency.
plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(rootProject.file("openapi.yaml").toURI().toString())
    outputDir.set(generatedDir.get().asFile.path)
    library.set("jvm-retrofit2")
    apiPackage.set("com.kairon.android.client.api")
    modelPackage.set("com.kairon.android.client.model")
    skipValidateSpec.set(true)
    configOptions.set(mapOf(
        "packageName" to "com.kairon.android.client",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "java8",
        "omitGradlePluginSupport" to "true",
        "omitGradleWrapper" to "true",
    ))
}

// The backend's one free-form JSON field (MeResponse.preferences, a
// Map<String, Object>) generates as Map<String, kotlin.Any>, which the
// kotlinx.serialization compiler plugin can't generate a serializer for even
// with @Contextual (that only replaces a property's OWN serializer lookup,
// not a generic type argument buried inside a Map) — a known openapi-generator
// limitation for free-form objects nested in a Map; `typeMappings`/`importMappings`
// (the documented override) don't reach this particular case. JsonElement has
// a real built-in serializer, so this patches just that one generated file's
// field, post-generation, pre-compile — never a hand-edit of checked-in code
// (D10), since everything under build/generated is regenerated every time.
val patchGeneratedClient = tasks.register("patchGeneratedClient") {
    dependsOn("openApiGenerate")
    doLast {
        // Both occurrences come from the same backend field (identity.web's
        // Map<String, Object> preferences, embedded in MeResponse and
        // UpdateMeRequest) via the same generator template, so the same two
        // literal substitutions fix every file that needs it.
        val modelDir = generatedDir.get().asFile.resolve("src/main/kotlin/com/kairon/android/client/model")
        modelDir.listFiles { f -> f.extension == "kt" }?.forEach { file ->
            val text = file.readText()
            if ("kotlin.collections.Map<kotlin.String, kotlin.Any>" in text) {
                val patched = text
                    .replace(
                        "@Contextual @SerialName(value = \"preferences\")",
                        "@SerialName(value = \"preferences\")")
                    .replace(
                        "kotlin.collections.Map<kotlin.String, kotlin.Any>",
                        "kotlin.collections.Map<kotlin.String, kotlinx.serialization.json.JsonElement>")
                file.writeText(patched)
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(patchGeneratedClient)
}

sourceSets {
    main {
        kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
