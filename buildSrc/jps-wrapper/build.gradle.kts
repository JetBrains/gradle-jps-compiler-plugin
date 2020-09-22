plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "6.0.0"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
}

val jpsVersion = "202.7319.50"

project.version = "0.1-$jpsVersion"

repositories {
    mavenCentral()
    // todo: should be included into jps-standalone
    maven("https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-third-party-dependencies") // for fastutil
}

configurations {
    configurations["compile"].extendsFrom(createJpsConfiguration(project))
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    // todo: should be included into jps-standalone
    implementation("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.3.1-3")

}

tasks {
    build {
        dependsOn(shadowJar)
    }
    jar {
        manifest {
            attributes["Main-Class"] = "fleet.bootstrap.MainKt"
        }
    }
    shadowJar {
        mergeServiceFiles()
    }
}

fun createJpsConfiguration(project: Project): Configuration {
    return project.configurations.create("jpsRuntime") {
        isVisible = false
        withDependencies {
            val jpsZip = downloadJps(project)
            val jpsDir = unzip(jpsZip, jpsZip.parentFile, project)
            dependencies.add(project.dependencies.create(
                    project.fileTree("dir" to jpsDir, "include" to listOf("*.jar"))
            ))
        }
    }
}

fun unzip(zipFile: File, cacheDirectory: File, project: Project): File {
    val targetDirectory = File(cacheDirectory, zipFile.name.removeSuffix(".zip"))
    val markerFile = File(targetDirectory, "markerFile")
    if (markerFile.exists()) {
        return targetDirectory
    }

    if (targetDirectory.exists()) {
        targetDirectory.deleteRecursively()
    }
    targetDirectory.mkdir()

    project.copy {
        from(project.zipTree(zipFile))
        into(targetDirectory)
    }

    markerFile.createNewFile()
    return targetDirectory
}

fun downloadJps(project: Project): File {
    val repository = project.repositories.maven(url = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
    try {
        project.repositories.add(repository)
        val dependency = project.dependencies.create("com.jetbrains.intellij.idea:jps-standalone:${jpsVersion}@zip")
        return project.configurations.detachedConfiguration(dependency).singleFile
    } finally {
        project.repositories.remove(repository)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.jetbrains.intellij.idea"
            artifactId = "jps-wrapper"
            artifact(tasks.shadowJar.get().outputs.files.singleFile)
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