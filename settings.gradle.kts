rootProject.name = "jps-compiler-plugin"
includeBuild("jps-wrapper")

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}