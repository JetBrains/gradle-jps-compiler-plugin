package jps.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType
import java.io.File
import java.nio.file.Files

@Suppress("unused")
open class JpsCompile : DefaultTask() {
    companion object {
        const val PROPERTY_PREFIX = "build"
        const val DEFAULT_JPS_WRAPPER_VERSION = "0.2-202.7319.50"
    }

    init {
        outputs.upToDateWhen { false }
    }

    @Input
    var jpsWrapperVersion: String = DEFAULT_JPS_WRAPPER_VERSION

    @Input
    var moduleName: String? = null

    @Input
    var projectPath: String? = null

    @OutputFile
    var classpathOutputFilePath: String = Files.createTempFile("classpath", "").toString()

    @Input
    var incremental: Boolean = true

    @Optional
    @Input
    var kotlinVersion: String? = null

    private val dataStorageRoot = "${project.buildDir}/out"

    private val jdkTable = File(project.buildDir, "jdkTable.txt")

    @TaskAction
    fun compile() {
        val jpsWrapper = project.downloadJpsWrapper(jpsWrapperVersion)
        val kotlinDirectory = kotlinVersion?.let { pluginVersion ->
            val channel = pluginVersion.substringAfter(":", "")
            val version = pluginVersion.substringBefore(":")
            project.downloadKotlin(version, channel)
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

        val jdkTableContent = project.extensions.findByType(JdkTableExtension::class)?.jdkTable ?: emptyMap()
        project.buildDir.mkdirs()
        jdkTable.writeText(jdkTableContent.map { (k, v) -> "$k=$v" }.joinToString("\n"))

        project.javaexec {
            classpath(jpsWrapper, kotlinClasspath)
            main = "jps.wrapper.MainKt"

            listOf(JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot, JpsCompile::jdkTable).forEach { property ->
                systemProperty(property.name.withPrefix(), property.get(this@JpsCompile)?.toString())
            }

            kotlinDirectory?.let {
                systemProperty("kotlinHome".withPrefix(), kotlinDirectory)
            }
        }
    }

    private fun String.withPrefix() = "$PROPERTY_PREFIX.$this"
}
