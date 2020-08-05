package fleet.bootstrap

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import java.io.File

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

        val jdkTable = project.extensions.findByType(JdkTableExtension::class)?.jdkTable ?: emptyMap()
        val configDirectory = prepareConfigDirectory(jdkTable)

        val task = this
        project.javaexec {
            // FIXME add fat jar
            classpath(kotlinClasspath)

            systemProperties = listOf(
                    JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOutputFilePath,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot).map { property ->
                "build.${property.name}" to property.get(task)?.toString()
            }.toMap()
            systemProperty("idea.config.path", configDirectory.absolutePath)
            if (kotlinDirectory != null) {
                systemProperty("kotlinHome", "$kotlinDirectory/Kotlin")
            }
        }
    }
}

fun prepareConfigDirectory(jdkTable: Map<String, String>): File {
    val builder = StringBuilder("<application>\n  <component name=\"ProjectJdkTable\">")
    jdkTable.forEach { (name, path) ->
        builder.append("    <jdk version=\"2\">\n")
                .append("      <name value=\"$name\" />\n")
                .append("      <type value=\"JavaSDK\" />\n")
                .append("      <homePath value=\"$path\" />\n")
                .append("      <roots />\n")
                .append("      <additional />\n")
//                .append("      <version value=\"java version &quot;1.8.0_201&quot;\" />\n")
                .append("    </jdk>\n")
    }
    builder.append("  <component>\n</application>")
    val config = File.createTempFile("gradle", "config")
    val options = File(config, "options")
    options.mkdir()
    File(options, "jdk.table.xml").writeText(builder.toString())
    return config
}