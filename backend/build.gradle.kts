plugins {
    java
    alias(libs.plugins.spring.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Spring Boot's BOM (which itself imports the Testcontainers, JUnit, etc. BOMs)
    // supplies every version below. Gradle's native platform support resolves the
    // nested import-scope BOMs correctly, so the io.spring.dependency-management
    // plugin is not used.
    val bom = platform(libs.spring.boot.dependencies)
    implementation(bom)
    annotationProcessor(bom)
    testImplementation(bom)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // M7 D11: dependency only — `prometheus` is deliberately left out of
    // management.endpoints.web.exposure.include until a real scraper exists
    // (M12), and SecurityConfig's actuator deny (D10) keeps the endpoint closed
    // even if a future config change tries to expose it.
    implementation("io.micrometer:micrometer-registry-prometheus")
    // M7 D13: Swagger UI + the raw OpenAPI spec, made deliberately public
    // (SecurityConfig) — see the class Javadoc there.
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Auth (M1): Spring Security + a JWT resource server. The oauth2-resource-server
    // starter pulls spring-security-oauth2-jose (Nimbus), which supplies both the
    // JwtDecoder used to validate access tokens and the JwtEncoder used to mint them.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Argon2id password hashing needs BouncyCastle on the classpath.
    implementation(libs.bouncycastle.prov)
    implementation(libs.bucket4j.core)

    // Spring Boot 4 only auto-configures Flyway when the dedicated module is present
    // (flyway-core on its own is a silent no-op).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 moved the slice-test auto-configurations out of the core test
    // starter into dedicated starters; @WebMvcTest lives in this one, @DataJpaTest
    // in the data-jpa one.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit.junit5)
}

// The commit the running jar was built from. `docker/Dockerfile` passes this in as
// -PkaironGitCommit=... (from a build arg — .dockerignore excludes .git, so the
// image build can't ask git itself); falls back to asking the local repo, which
// covers `./gradlew build`/`bootRun` and `task jar` run straight from the working
// tree.
val kaironGitCommit: String = providers.gradleProperty("kaironGitCommit").orNull
    ?.takeIf { it.isNotBlank() }
    ?: runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

springBoot {
    // Emits META-INF/build-info.properties so BuildProperties is available at runtime
    // (used by the /api/v1/ping "version" field, and surfaced in full — plus the
    // deploy-time facts in DeployInfoContributor — at /actuator/info for the web
    // About page; docs/DESIGN.md §11).
    buildInfo {
        properties {
            additional.set(mapOf("commit" to kaironGitCommit))
        }
    }
}

// One predictable artifact: kairon.jar, and no extra "-plain" library jar.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "kairon.jar"
}

// The SPA is not a separate deployable: the :web Vite build output is packaged into
// this jar as static resources, so the jar serves both the API and the app.
// See docs/DESIGN.md §3.3.
val webDist = project(":web").layout.projectDirectory.dir("dist")

tasks.named<ProcessResources>("processResources") {
    dependsOn(":web:buildWeb")
    from(webDist) {
        into("static")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
