package fleet.bootstrap

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

open class JpsCompile : DefaultTask() {
    @Input
    var module: String? = null
    @Input
    var project: String? = null
    @InputFile
    var classpathOut: String? = null
    @Input
    var incremental: Boolean = true

    private val dataStorageRoot = "${getProject().buildDir}/out"
    private val kotlinHome = "${getProject().buildDir}/${JpsPlugin.KOTLIN_PLUGIN_DIR}/Kotlin"

    @TaskAction
    fun compile() {
        getProject().javaexec {
            // TODO add JPS to classpath
            // TODO add kotlin plugin to classpath
            // TODO setup kotlin plugin and jps before execution
            classpath(JpsPlugin::class.java.protectionDomain.codeSource?.location?.toURI()?.path)
            main = "fleet.bootstrap.runner.MainKt"
            systemProperties = listOf(
                    JpsCompile::module, JpsCompile::project, JpsCompile::classpathOut,
                    JpsCompile::incremental, JpsCompile::dataStorageRoot, JpsCompile::kotlinHome).map {
                val value = it.get(this@JpsCompile)
                assert(value != null) { "Property ${it.name} should be specified" }

                it.name to value.toString()
            }.toMap()
        }
    }
}