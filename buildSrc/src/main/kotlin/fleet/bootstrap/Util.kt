package fleet.bootstrap

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.fileTree
import org.gradle.kotlin.dsl.maven
import java.io.File

const val jpsVersion = "2020.2"
const val jpsRuntimeConfiguration = "jpsRuntime"

fun createJpsConfiguration(project: Project): Configuration {
    return project.configurations.create("jpsRuntime") {
        isVisible = false
        withDependencies {
            val jpsZip = downloadJps(project)
            val jspDir = unzip(jpsZip, jpsZip.parentFile, project)
            dependencies.add(project.dependencies.create("org.apache.maven:maven-embedder:3.6.0"))
            dependencies.add(project.dependencies.create(
                    project.fileTree("dir" to jspDir, "include" to listOf("*.jar"))
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
        val dependency = project.dependencies.create("com.jetbrains.intellij.idea:jps-standalone:$jpsVersion@zip")
        return project.configurations.detachedConfiguration(dependency).singleFile
    } finally {
        project.repositories.remove(repository)
    }
}