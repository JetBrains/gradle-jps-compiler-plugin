package fleet.bootstrap

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType
import java.io.File

open class JpsCompile : DefaultTask() {
    companion object {
        const val PROPERTY_PREFIX = "build"
    }

    // FIXME remove
    @Input
    var jpsWrapperPath: String? = null

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

    private val jdkTable = File(project.buildDir, "jdkTable")

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

        val jdkTableContent = project.extensions.findByType(JdkTableExtension::class)?.jdkTable ?: emptyMap()
        jdkTable.writeText(jdkTableContent.map { (k, v) -> "$k=$v" }.joinToString("\n"))

        project.javaexec {
            classpath(jpsWrapperPath)
            classpath(kotlinClasspath)

            systemProperties = listOf(
                    JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot, JpsCompile::jdkTable).map { property ->
                property.name.withPrefix() to property.get(this@JpsCompile)?.toString()
            }.toMap()

            if (kotlinDirectory != null) {
                systemProperty("kotlinHome".withPrefix(), "$kotlinDirectory/Kotlin")
            }
        }
    }

    internal fun String.withPrefix() = "$PROPERTY_PREFIX.$this"
}
