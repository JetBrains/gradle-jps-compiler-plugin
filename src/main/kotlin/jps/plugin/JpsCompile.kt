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
        const val DEFAULT_JPS_WRAPPER_VERSION = "0.23"
        const val DEFAULT_JPS_VERSION = "2022.1"
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
    var includeRuntimeDependencies: Boolean = true

    @Input
    var includeTests: Boolean = true

    @Input
    var parallel: Boolean = true

    @Input
    var withProgress: Boolean = false

    @Optional
    @Input
    var kotlinVersion: String? = null

    @Optional
    @InputFile
    var jpsWrapper: File? = null

    @Optional
    @Input
    var systemProperties: Map<String, String> = emptyMap()

    @Optional
    @Input
    var jvmArgs: List<String> = emptyList()

    @Input
    var outputPath: String = "${project.buildDir}/jps/out"

    private val jdkTable = File(project.buildDir, "jdkTable.txt")

    @TaskAction
    fun compile() {
        val jpsWrapper = jpsWrapper ?: project.downloadJpsWrapper(jpsWrapperVersion)
        val jpsStandaloneDirectory = project.downloadJpsStandalone(jpsVersion)
        val jpsClasspath = project.file(jpsStandaloneDirectory).listFiles()?.toList() ?: emptyList()
        val kotlinDirectory = kotlinVersion?.let { pluginVersion ->
            project.downloadKotlin(pluginVersion)
        }
        val kotlinJpsPlugin = kotlinVersion?.let { pluginVersion ->
            project.downloadKotlinJpsPlugin(pluginVersion)
        }

        val jdkTableContent = project.extensions.findByType(JdkTableExtension::class)?.jdkTable ?: emptyMap()
        project.buildDir.mkdirs()
        jdkTable.writeText(jdkTableContent.map { (k, v) -> "$k=$v" }.joinToString("\n"))

        val extraProperties = systemProperties
        val extraJvmArgs = jvmArgs
        project.javaexec {
            classpath = project.files(jpsWrapper, jpsClasspath, kotlinJpsPlugin)
            main = "jps.wrapper.MainKt"

            listOf(
                JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                JpsCompile::includeTests, JpsCompile::includeRuntimeDependencies, JpsCompile::incremental,
                JpsCompile::parallel, JpsCompile::withProgress, JpsCompile::jdkTable, JpsCompile::outputPath
            ).forEach { property ->
                systemProperty(property.name.withPrefix(), property.get(this@JpsCompile)?.toString())
            }

            systemProperties(extraProperties)
            kotlinDirectory?.let {
                systemProperty("kotlinHome".withPrefix(), kotlinDirectory)
            }
            jvmArgs(extraJvmArgs)
        }
    }

    private fun String.withPrefix() = "$PROPERTY_PREFIX.$this"
}
