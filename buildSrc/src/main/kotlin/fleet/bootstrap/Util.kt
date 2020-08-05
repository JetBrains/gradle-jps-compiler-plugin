package fleet.bootstrap

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.fileTree
import org.gradle.kotlin.dsl.maven
import java.io.File

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

fun downloadKotlin(project: Project, version: String, channel: String): File {
    val groupId = if (channel.isEmpty()) "com.jetbrains.plugins" else "${channel}.com.jetbrains.plugins"
    val repository = project.repositories.maven(url = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven")
    val kotlinZip = try {
        project.repositories.add(repository)
        val dependency = project.dependencies.create("$groupId:org.jetbrains.kotlin:$version@zip")
        project.configurations.detachedConfiguration(dependency).singleFile
    } finally {
        project.repositories.remove(repository)
    }
    return unzip(kotlinZip, kotlinZip.parentFile, project)
}