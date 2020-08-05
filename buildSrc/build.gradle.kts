plugins {
    `kotlin-dsl`
}

val jpsVersion = "2020.2"

repositories {
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    jcenter()
}

configurations {
    create("jps") {
        isVisible = false
        withDependencies {
            val jpsZip = downloadJps()
            val jspDir = unzip(jpsZip, jpsZip.parentFile, project)
            dependencies.add(project.dependencies.create("org.apache.maven:maven-embedder:3.6.0"))
            dependencies.add(project.dependencies.create(
                    project.fileTree("dir" to jspDir, "include" to listOf("*.jar"))
            ))
        }
        configurations["compile"].extendsFrom(configurations["jps"])
    }
}


gradlePlugin {
    plugins {
        register("jps-plugin") {
            id = "jps-plugin"
            implementationClass = "fleet.bootstrap.JpsPlugin"
        }
    }
}

fun unzip(zipFile: File,
          cacheDirectory: File,
          project: Project) : File{
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

fun downloadJps(): File {
    val dependency = project.dependencies.create("com.jetbrains.intellij.idea:jps-standalone:$jpsVersion@zip")
    val configuration = project.configurations.detachedConfiguration(dependency)
    val jpsZip = configuration.singleFile
    return jpsZip
}