plugins {
    `embedded-kotlin`
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
}

val jpsVersion = "211.6693.111"

project.version = "0.5"

repositories {
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}


dependencies {
    compileOnly("com.jetbrains.intellij.tools:jps-build-standalone:$jpsVersion") {
        targetConfiguration = "runtime"
    }
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "jps.wrapper.MainKt"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.jetbrains.intellij.idea"
            artifactId = "jps-wrapper"
            artifact(tasks.jar.get().outputs.files.singleFile) {
                builtBy(tasks.jar)
            }
        }
    }
}

if (hasProperty("bintrayUser")) {
    bintray {
        user = property("bintrayUser").toString()
        key = property("bintrayApiKey").toString()
        publish = true
        setPublications("maven")
        pkg.apply {
            userOrg = "jetbrains"
            repo = "intellij-third-party-dependencies"
            name = "jps-wrapper"
            version.name = project.version.toString()
        }
    }
}