package jps.plugin

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import java.io.File
import kotlin.collections.set

const val DEFAULT_JPS_WRAPPER_VERSION = "0.24"
const val DEFAULT_JPS_VERSION = "2022.1"

@Suppress("unused")
class JpsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val jdkTableExtension = project.extensions.create<JdkTableExtension>(JdkTableExtension.EXTENSION_NAME)
        val jpsVersionConfiguration = project.extensions.create<JpsVersionConfiguration>("jpsConfiguration")

        jpsVersionConfiguration.jpsWrapperVersions.add(DEFAULT_JPS_WRAPPER_VERSION)
        jpsVersionConfiguration.jpsVersions.add(DEFAULT_JPS_VERSION)

        val jpsWrapperConfigurations = project.registerConfigurations(
            jpsVersionConfiguration.jpsWrapperVersions,
            "jpsWrapper",
            { "https://cache-redirector.jetbrains.com/intellij-dependencies" },
            { "com.jetbrains.intellij.idea:jps-wrapper:$it" },
        )
        val jpsStandaloneConfigurations = project.registerConfigurations(
            jpsVersionConfiguration.jpsVersions,
            "jpsStandalone",
            repository = ::findJpsStandaloneRepository,
            dependencyNotation = { "com.jetbrains.intellij.idea:jps-standalone:$it@zip" },
        )
        val kotlinDistConfigurations = project.registerConfigurations(
            jpsVersionConfiguration.kotlinVersions,
            "kotlinDist",
            { "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies" },
            { "org.jetbrains.kotlin:kotlin-dist-for-ide:$it@jar" },
        )
        val kotlinJpsPluginConfigurations = project.registerConfigurations(
            jpsVersionConfiguration.kotlinVersions,
            "kotlinJpsPlugin",
            { "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies" },
            { "org.jetbrains.kotlin:kotlin-jps-plugin-classpath:$it@jar" },
        )

        project.tasks.withType<JpsCompile> {
            // for each declaration of JpsCompile, we record the version the user specified so that we can be prepared
            // to download it when it will be required
            jpsVersionConfiguration.jpsWrapperVersions.add(jpsWrapperVersion)
            jpsVersionConfiguration.jpsVersions.add(jpsVersion)
            jpsVersionConfiguration.kotlinVersions.add(kotlinVersion)

            jdkTableContent.set(project.provider { jdkTableExtension.jdkTable })

            // all these wiring are lazy, configurations will not be resolved, dependencies not downloaded until
            // the JpsCompile task is about to be executed.
            // This is the laziest we can be while being compliant to the Gradle configuration cache.
            jpsWrapper.set(project.layout.file(jpsWrapperConfigurations.dependencyFile(jpsWrapperVersion)))
            jpsStandaloneZip.set(project.layout.file(jpsStandaloneConfigurations.dependencyFile(jpsVersion)))
            kotlinDistZip.set(project.layout.file(kotlinDistConfigurations.dependencyFile(kotlinVersion)))
            kotlinJpsPlugin.set(project.layout.file(kotlinJpsPluginConfigurations.dependencyFile(kotlinVersion)))
        }
    }
}

interface JpsVersionConfiguration {
    val kotlinVersions: SetProperty<String>
    val jpsWrapperVersions: SetProperty<String>
    val jpsVersions: SetProperty<String>
}

open class JdkTableExtension {
    companion object {
        const val EXTENSION_NAME = "jdkTable"
    }

    val jdkTable = mutableMapOf<String, String>()

    @Suppress("unused")
    fun jdk(name: String, path: String) {
        jdkTable[name] = path
    }
}

private fun findJpsStandaloneRepository(version: String): String {
    val intellijRepositoryPrefix = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/"

    return when {
        // Like 233.3956
        version.matches("\\d{3}\\.\\d+".toRegex()) -> "https://cache-redirector.jetbrains.com/intellij-dependencies"

        // Like 232.8660.142-EAP-SNAPSHOT, 232.9559.10-CUSTOM-SNAPSHOT, 232.8660-EAP-CANDIDATE-SNAPSHOT,
        version.endsWith("-EAP-SNAPSHOT")
                || version.endsWith("-EAP-CANDIDATE-SNAPSHOT")
                || version.endsWith("-CUSTOM-SNAPSHOT") -> "${intellijRepositoryPrefix}snapshots"

        // Like 233-SNAPSHOT, 222-SNAPSHOT, 202.2793-SNAPSHOT
        version.endsWith("-SNAPSHOT") -> "${intellijRepositoryPrefix}nightly"

        // Like 2022.3, 2022.2.5 or 222.4554.10
        version.matches("[\\d.]+".toRegex()) -> "${intellijRepositoryPrefix}releases"

        else -> error("Unable to guess repository for provided version $version")
    }
}

/**
 * Lazily register one configuration per version of [versions] that contains the dependency [dependencyNotation] in
 * repository [repository] for that version.
 *
 * This maintains compatibility with Gradle configuration cache.
 *
 * This is meant to prevent downloading using `project` and detached configurations at execution phase, while still
 * being maintaining laziness.
 *
 * Meaning that configuration of a specific version will not be resolved (dependency not downloaded) if no [JpsCompile]
 * task is requesting that version, and they will not be resolved if a [JpsCompile] task is specifying the version but
 * not part of the task execution graph for the given run.
 */
private fun Project.registerConfigurations(
    versions: Provider<Set<String>>,
    name: String,
    repository: (version: String) -> String,
    dependencyNotation: (version: String) -> String
): Provider<Map<String, NamedDomainObjectProvider<Configuration>>> = versions.map {
    it.associateWith { version ->
        val fullName = "${name}_$version"
        try {
            configurations.named(fullName)
        } catch (e: UnknownDomainObjectException) {
            configurations.register(fullName) {
                repositories { maven(repository.invoke(version)) }
                dependencies.add(project.dependencies.create(dependencyNotation.invoke(version)))
            }
        }
    }
}

/**
 * Returns the dependency file provider of the specified [version].
 *
 * Configuration will be resolved (and thus dependency downloaded) only when resulting [Provider]'s value is asked.
 */
private fun Provider<Map<String, NamedDomainObjectProvider<Configuration>>>.dependencyFile(
    version: Provider<String>,
): Provider<File> = map { configByVersion ->
    val v = version.get()
    val selected = configByVersion[v]?.get()
        ?: unexpectedError("could not select version $v, available versions ${configByVersion.keys}")
    val resolved = selected.resolve()
    resolved.singleOrNull()
        ?: unexpectedError("configuration '${selected.name}' must have only one file, found $resolved, this is unexpected contact plugin maintainers")
}

fun unexpectedError(message: Any): Nothing = error(
    "$message\n\nThis is unexpected, please file an issue at https://github.com/JetBrains/gradle-jps-compiler-plugin/issues"
)
