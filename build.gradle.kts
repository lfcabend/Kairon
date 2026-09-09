// Root build. Configuration lives in the :backend and :web subprojects; this file
// only carries settings that are genuinely shared across every project.

group = "com.kairon"
version = providers.gradleProperty("kaironVersion").getOrElse("0.0.1-SNAPSHOT")

subprojects {
    version = rootProject.version
}
