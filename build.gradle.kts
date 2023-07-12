plugins {
    conventions.`kotlin-jvm`
}

allprojects {
    repositories {
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}

