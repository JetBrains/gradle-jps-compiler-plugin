plugins {
    `embedded-kotlin`
    id("com.github.johnrengelman.shadow") version "6.0.0"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
}

val jpsVersion = "211.2735"

project.version = "0.3-$jpsVersion"

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-third-party-dependencies")
}

dependencies {
    implementation("com.jetbrains.intellij.tools:jps-build-standalone:$jpsVersion") {
        targetConfiguration = "runtime"
    }
    implementation("com.jetbrains.intellij.tools:jps-build-script-dependencies:$jpsVersion") {
        targetConfiguration = "runtime"
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    jar {
        manifest {
            attributes["Main-Class"] = "jps.wrapper.MainKt"
        }
    }
    shadowJar {
        mergeServiceFiles()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.jetbrains.intellij.idea"
            artifactId = "jps-wrapper"
            artifact(tasks.shadowJar.get().outputs.files.singleFile) {
                builtBy(tasks.shadowJar)
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