rootProject.name = "jps-compiler-plugin"

include("jps-wrapper")
include("jps-gradle-plugin")

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}
