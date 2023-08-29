plugins {
    conventions.`kotlin-jvm`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.0"
}

version = "0.3.10"
group = "gradle.plugin.com.jetbrains.intellij"

gradlePlugin {
    plugins {
        register("jps-compiler-plugin") {
            id = "com.jetbrains.intellij.jps-compiler-plugin"
            displayName = "JPS Compiler Plugin"
            implementationClass = "jps.plugin.JpsPlugin"
            description = "Plugin for building JPS-based projects, created in IntelliJ IDEA"
            website.set("https://github.com/JetBrains/gradle-jps-compiler-plugin")
            vcsUrl.set("https://github.com/JetBrains/gradle-jps-compiler-plugin")
            tags.set(listOf("intellij", "jetbrains", "idea", "jps"))
        }
    }
}
