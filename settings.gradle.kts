rootProject.name = "gradle-jps-compiler-plugin"

include("jps-wrapper")
include("jps-gradle-plugin")

// Maintaining old project name so that artefacts retain their former names.
// Ensures that Gradle plugin automation still considers this plugin as
// https://plugins.gradle.org/plugin/com.jetbrains.intellij.jps-compiler-plugin
project(":jps-gradle-plugin").name = "jps-compiler-plugin"

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}
