plugins {
    val kotlinVersion = "1.8.10"
    `kotlin-dsl`
    kotlin("jvm") version kotlinVersion apply false
}

allprojects {
    repositories {
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
