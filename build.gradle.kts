plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.14.0"
}

repositories {
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

gradlePlugin {
    plugins.create("jps-compiler-plugin") {
        id = "com.jetbrains.intellij.jps-compiler-plugin"
        displayName = "JPS Compiler Plugin"
        implementationClass = "jps.plugin.JpsPlugin"
    }
}

version = "0.1.1"
group = "com.jetbrains.intellij"

pluginBundle {
    description = "Plugin for building JPS-based projects, created in IntelliJ IDEA"
    website = "https://github.com/JetBrains/gradle-jps-compiler-plugin"
    vcsUrl = "https://github.com/JetBrains/gradle-jps-compiler-plugin"
    tags = listOf("intellij", "jetbrains", "idea", "jps")
}