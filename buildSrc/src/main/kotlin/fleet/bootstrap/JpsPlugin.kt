package fleet.bootstrap

import org.gradle.api.Plugin
import org.gradle.api.Project

class JpsPlugin : Plugin<Project> {
    companion object {
        const val DEFAULT_KOTLIN_PLUGIN_VERSION = "1.3.72-release-IJ2020.1-6:ideadev"
    }

    override fun apply(project: Project) {
        // TODO configurable

        createJpsConfiguration(project)
        // create tasks
    }
}