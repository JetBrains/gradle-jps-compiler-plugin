plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

project.version = "0.33"

kotlin {
    jvmToolchain(11)
}

repositories {
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
}

dependencies {
    compileOnly(libs.intellij.tools.jps.build.standalone) {
        // `com.jetbrains.intellij.tools:jps-build-standalone` is incorrectly published,
        // some of its `compile`-scope dependencies are declared in the `runtime` scope insteaed
        // so we request them here instead.
        targetConfiguration = "runtime"
    }
    implementation(libs.intellij.platform.model.serialization) {
        // `com.jetbrains.intellij.platform:jps-model-serialization` is incorrectly published,
        // some of its `compile`-scope dependencies are declared in the `runtime` scope insteaed
        // so we request them here instead.
        targetConfiguration = "runtime"
    }
    implementation(libs.intellij.platform.util.xml.dom)
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "jps.wrapper.MainKt"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            credentials {
                username = System.getenv("JB_SPACE_CLIENT_ID")
                password = System.getenv("JB_SPACE_CLIENT_SECRET")
            }
        }
    }
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