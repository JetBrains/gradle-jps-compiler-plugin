package fleet.bootstrap

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.fileTree
import org.gradle.kotlin.dsl.maven
import java.io.File

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
            repository = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven",
            dependencyNotation = "$groupId:org.jetbrains.kotlin:$version@zip")
    return unzip(kotlinZip, kotlinZip.parentFile, project).resolve("Kotlin")
}

fun Project.downloadJpsWrapper(version: String): File {
    return downloadDependency(
            "https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-third-party-dependencies",
            "com.jetbrains.intellij.idea:jps-wrapper:$version"
    )
}

private fun Project.downloadDependency(repository: String, dependencyNotation: String): File {
    val repository = repositories.maven(url = repository)
    return try {
        repositories.add(repository)
        val dependency = dependencies.create(dependencyNotation)
        configurations.detachedConfiguration(dependency).singleFile
    } finally {
        repositories.remove(repository)
    }
}