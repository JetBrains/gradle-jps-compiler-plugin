plugins {
    `embedded-kotlin`
    `maven-publish`
}

val jpsVersion = "211.6693.111"

project.version = "0.8"

repositories {
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
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