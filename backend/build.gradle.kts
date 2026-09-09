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

    // Spring Boot 4 only auto-configures Flyway when the dedicated module is present
    // (flyway-core on its own is a silent no-op).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 moved the slice-test auto-configurations out of the core test
    // starter into dedicated starters; @WebMvcTest lives in this one.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit.junit5)
}

springBoot {
    // Emits META-INF/build-info.properties so BuildProperties is available at runtime
    // (used by the /api/v1/ping "version" field).
    buildInfo()
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
