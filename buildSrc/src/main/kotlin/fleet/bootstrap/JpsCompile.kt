package fleet.bootstrap

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get

open class JpsCompile : DefaultTask() {
    @Input
    var moduleName: String? = null

    @Input
    var projectPath: String? = null

    @Input
    var classpathOutputFilePath: String? = null

    @Input
    var incremental: Boolean = true

    @Optional
    @Input
    var kotlinVersion: String? = null

    private val dataStorageRoot = "${project.buildDir}/out"

    @TaskAction
    fun compile() {
        val kotlinDirectory = kotlinVersion?.let { pluginVersion ->
            val channel = pluginVersion.substringAfter(":", "")
            val version = pluginVersion.substringBefore(":")
            downloadKotlin(project, version, channel)
        }
        val kotlinClasspath = kotlinDirectory?.let {
            project.fileTree("$it") {
                include(listOf(
                        "lib/jps/kotlin-jps-plugin.jar",
                        "lib/kotlin-plugin.jar",
                        "lib/kotlin-reflect.jar",
                        "kotlinc/lib/kotlin-stdlib.jar"
                ))
            }.files
        } ?: emptySet()

        val task = this
        project.javaexec {
            classpath(JpsPlugin::class.java.protectionDomain.codeSource?.location?.toURI()?.path)
            classpath(project.configurations[jpsRuntimeConfiguration].files) // make it configurable?
            classpath(kotlinClasspath)

            main = "fleet.bootstrap.runner.MainKt"
            systemProperties = listOf(
                    JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot).map { property ->
                "build.${property.name}" to property.get(task)?.toString()
            }.toMap()

            if (kotlinDirectory != null) {
                systemProperty("kotlinHome", "$kotlinDirectory/Kotlin")
            }
        }
    }
}