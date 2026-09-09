import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node.gradle)
}

node {
    // Gradle manages its own Node/npm install so neither CI nor the Docker gradle
    // stage needs a system Node toolchain. Local `npm run dev` still uses host Node.
    version = "24.19.0"
    npmVersion = "11.6.2"
    download = true
    nodeProjectDir = layout.projectDirectory
}

val buildWeb = tasks.register<NpmTask>("buildWeb") {
    group = "build"
    description = "Installs web dependencies and builds the production SPA bundle into web/dist."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "build")

    inputs.files(
        "package.json", "package-lock.json", "vite.config.ts",
        "tsconfig.json", "tsconfig.node.json", "index.html",
        "tailwind.config.js", "postcss.config.js",
    )
    inputs.dir("src")
    outputs.dir(layout.projectDirectory.dir("dist"))
    outputs.cacheIf { true }
}

val testWeb = tasks.register<NpmTask>("testWeb") {
    group = "verification"
    description = "Runs the Vitest suite."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "test")
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("dist"))
}

tasks.named("assemble") {
    dependsOn(buildWeb)
}

tasks.named("check") {
    dependsOn(testWeb)
}
