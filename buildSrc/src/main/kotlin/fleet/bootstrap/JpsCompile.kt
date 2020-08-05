package fleet.bootstrap

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get

open class JpsCompile : DefaultTask() {
    @Input
    var moduleName: String? = null

    @Input
    var projectPath: String? = null

    @Input
    var classpathOut: String? = null

    @Input
    var incremental: Boolean = true

    private val dataStorageRoot = "${project.buildDir}/out"
    private val kotlinHome = "${project.buildDir}/${JpsPlugin.KOTLIN_PLUGIN_DIR}/Kotlin"

    @TaskAction
    fun compile() {
        project.javaexec {
            // TODO add kotlin plugin to classpath
            // TODO setup kotlin plugin and jps before execution
            classpath(JpsPlugin::class.java.protectionDomain.codeSource?.location?.toURI()?.path)
            classpath(project.configurations[jpsRuntimeConfiguration].files) // make it configurable?
            main = "fleet.bootstrap.runner.MainKt"
            systemProperties = listOf(
                    JpsCompile::moduleName, JpsCompile::projectPath, JpsCompile::classpathOut,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot, JpsCompile::kotlinHome).map {
                val value = it.get(this@JpsCompile)
                assert(value != null) { "Property ${it.name} should be specified" }

                it.name to value.toString()
            }.toMap()
        }
    }
}