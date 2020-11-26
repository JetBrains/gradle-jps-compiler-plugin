plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.12.0"
}

repositories {
    jcenter()
}

gradlePlugin {
    plugins.create("jps-compiler-plugin") {
        id = "com.jetbrains.intellij.jps-compiler-plugin"
        displayName = "JPS Compiler Plugin"
        implementationClass = "jps.plugin.JpsPlugin"
    }
}

version = "0.0.5"
group = "com.jetbrains.intellij"

pluginBundle {
    description = "Plugin for building JPS-based projects, created in IntelliJ IDEA"
    website = "https://github.com/JetBrains/gradle-jps-compiler-plugin"
    vcsUrl = "https://github.com/JetBrains/gradle-jps-compiler-plugin"
    tags = listOf("intellij", "jetbrains", "idea", "jps")
}