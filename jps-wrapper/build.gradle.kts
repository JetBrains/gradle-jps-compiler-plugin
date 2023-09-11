plugins {
    conventions.`kotlin-jvm`
    `maven-publish`
}

val jpsVersion = "221.3598"

project.version = "0.31"

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