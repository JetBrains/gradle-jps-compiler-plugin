package jps.plugin

import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven
import java.io.File
import java.util.regex.Pattern

fun Project.unzip(zipFile: File, cacheDirectory: File): File {
    val targetDirectory = File(cacheDirectory, zipFile.name.removeSuffix(".zip"))
    val markerFile = File(targetDirectory, "markerFile")
    if (markerFile.exists()) {
        return targetDirectory
    }

    if (targetDirectory.exists()) {
        targetDirectory.deleteRecursively()
    }
    targetDirectory.mkdir()

    copy {
        from(zipTree(zipFile))
        into(targetDirectory)
    }

    markerFile.createNewFile()
    return targetDirectory
}

fun Project.downloadKotlin(version: String, channel: String): File {
    val groupId = if (channel.isEmpty()) "com.jetbrains.plugins" else "${channel}.com.jetbrains.plugins"
    val kotlinZip = downloadDependency(
        repositoryUrl = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven",
        dependencyNotation = "$groupId:org.jetbrains.kotlin:$version@zip"
    )
    return project.unzip(kotlinZip, kotlinZip.parentFile).resolve("Kotlin")
}

fun Project.downloadJpsWrapper(version: String): File {
    return downloadDependency(
        "https://cache-redirector.jetbrains.com/intellij-dependencies",
        "com.jetbrains.intellij.idea:jps-wrapper:$version"
    )
}

fun Project.downloadJpsStandalone(version: String): File {
    val repositoryType = intellijRepositoryType(version)
    val jpsZip = downloadDependency(
        repositoryUrl = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/$repositoryType",
        dependencyNotation = "com.jetbrains.intellij.idea:jps-standalone:$version@zip"
    )
    return project.unzip(jpsZip, jpsZip.parentFile)
}

private val MAJOR_VERSION_PATTERN = Pattern.compile("\\d{4}\\.\\d-SNAPSHOT")
private fun intellijRepositoryType(version: String): String {
    return when {
        version.endsWith("-EAP-SNAPSHOT") ||
                version.endsWith("-EAP-CANDIDATE-SNAPSHOT") ||
                version.endsWith("-CUSTOM-SNAPSHOT") ||
                MAJOR_VERSION_PATTERN.matcher(version).matches() -> "snapshots"
        version.endsWith("-SNAPSHOT") -> "nightly"
        else -> "releases"
    }
}

private fun Project.downloadDependency(repositoryUrl: String, dependencyNotation: String): File {
    val repository = repositories.maven(url = repositoryUrl)
    return try {
        repositories.add(repository)
        val dependency = dependencies.create(dependencyNotation)
        configurations.detachedConfiguration(dependency).singleFile
    } finally {
        repositories.remove(repository)
    }
}