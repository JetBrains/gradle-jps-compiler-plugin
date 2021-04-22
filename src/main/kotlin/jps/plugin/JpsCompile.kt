package jps.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.findByType
import java.io.File
import java.nio.file.Files

@Suppress("unused")
open class JpsCompile : DefaultTask() {
    companion object {
        const val PROPERTY_PREFIX = "build"
        const val DEFAULT_JPS_WRAPPER_VERSION = "0.5"
        const val DEFAULT_JPS_VERSION = "2021.1"
    }

    init {
        outputs.upToDateWhen { false }
    }

    @Input
    var jpsVersion: String = DEFAULT_JPS_VERSION

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

    @Input
    var parallel: Boolean = true

    @Optional
    @Input
    var kotlinVersion: String? = null

    @Optional
    @InputFile
    var jpsWrapper: File? = null

    @Optional
    @Input
    var systemProperties: Map<String, String> = emptyMap()

    @Input
    var outputPath: String = "${project.buildDir}/jps"

    private val jdkTable = File(project.buildDir, "jdkTable.txt")

    @TaskAction
    fun compile() {
        val jpsWrapper = jpsWrapper ?: project.downloadJpsWrapper(jpsWrapperVersion)
        val jpsStandaloneDirectory = project.downloadJpsStandalone(jpsVersion)
        val jpsClasspath = project.fileTree(jpsStandaloneDirectory).files
        val kotlinDirectory = kotlinVersion?.let { pluginVersion ->
            val channel = pluginVersion.substringAfter(":", "")
            val version = pluginVersion.substringBefore(":")
            project.downloadKotlin(version, channel)
        }
        val kotlinClasspath = kotlinDirectory?.let {
            project.fileTree("$it") {
                include(
                    listOf(
                        "lib/jps/kotlin-jps-plugin.jar",
                        "lib/kotlin-plugin.jar",
                        "lib/kotlin-reflect.jar",
                        "lib/kotlin-common.jar",
                        "kotlinc/lib/kotlin-stdlib.jar"
                    )
                )
            }.files
        } ?: emptySet()

        val jdkTableContent = project.extensions.findByType(JdkTableExtension::class)?.jdkTable ?: emptyMap()
        project.buildDir.mkdirs()
        jdkTable.writeText(jdkTableContent.map { (k, v) -> "$k=$v" }.joinToString("\n"))

        val extraProperties = systemProperties
        project.javaexec {
            classpath = project.files(jpsWrapper, jpsClasspath, kotlinClasspath)
            main = "jps.wrapper.MainKt"

            listOf(
                JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                JpsCompile::incremental, JpsCompile::parallel, JpsCompile::jdkTable, JpsCompile::outputPath
            ).forEach { property ->
                systemProperty(property.name.withPrefix(), property.get(this@JpsCompile)?.toString())
            }

            systemProperties(extraProperties)
            kotlinDirectory?.let {
                systemProperty("kotlinHome".withPrefix(), kotlinDirectory)
            }
        }
    }

    private fun String.withPrefix() = "$PROPERTY_PREFIX.$this"
}
