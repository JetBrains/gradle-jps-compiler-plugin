val kotlinVersion = "1.8.10"

plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}