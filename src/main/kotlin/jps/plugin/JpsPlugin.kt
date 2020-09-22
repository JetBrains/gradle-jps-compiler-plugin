package jps.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class JpsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(JdkTableExtension.EXTENSION_NAME, JdkTableExtension::class)
    }
}

open class JdkTableExtension {
    companion object {
        const val EXTENSION_NAME = "jdkTable"
    }

    val jdkTable = mutableMapOf<String, String>()

    fun jdk(name: String, path: String) {
        jdkTable[name] = path
    }
}