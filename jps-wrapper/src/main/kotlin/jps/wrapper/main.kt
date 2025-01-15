package jps.wrapper

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil.*
import org.jetbrains.jps.cmdline.LogSetup
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsPathMapper
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    setupLogging()

    val jpsModel = jpsModel()
    if (args.contains("buildOnlyClasspathFile")) {
        buildOnlyClasspathFile(jpsModel)
    } else {
        runBuild(jpsModel)
    }
}

private fun jpsModel(): JpsModel {
    val kotlinHome = Properties.kotlinHome
    if (kotlinHome != null) {
        System.setProperty("jps.kotlin.home", kotlinHome)
    }

    System.setProperty("compile.parallel", Properties.parallel)

    val model = initializeModel()
    val jdkTable = Properties.jdkTable?.let { jdkTable ->
        val file = File(jdkTable)
        if (file.exists()) {
            file.readLines().associate {
                val (name, path) = it.split("=")
                name to path
            }
        } else {
            null
        }
    } ?: emptyMap()

    val currentJdk = getCurrentJdk()
    model.project.modules
        .mapNotNull { it.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName }.distinct()
        .forEach { sdkName ->
            val jdkHomePath = jdkTable[sdkName]
            if (jdkHomePath == null) {
                println("SDK '$sdkName' not specified. Using current JDK ($currentJdk) as fallback.")
            } else {
                println("Using $jdkHomePath for '$sdkName' jdk.")
            }
            addJdk(model.global, sdkName, jdkHomePath ?: currentJdk)
            readModulesFromReleaseFile(model, sdkName, jdkHomePath ?: currentJdk)
        }
    return model
}

private fun buildOnlyClasspathFile(model: JpsModel) {
    val moduleName = Properties.moduleName ?: return
    val mainJpsModule = model.project.modules.find { module -> module.name == moduleName }
        ?: error("Module $moduleName not found.")

    if (Properties.classpathOutputFilePath == null) {
        error("`classpathOutputFilePath` must be set when `buildOnlyClasspathFile` argument used")
    }
    saveRuntimeClasspath(mainJpsModule, File(Properties.classpathOutputFilePath))
}

private fun traverseDependenciesRecursively(module: JpsModule, modulesToBuild: MutableSet<String>) {
    listOfNotNull(
        dependencyEnumerator(module, JpsJavaClasspathKind.PRODUCTION_COMPILE),
        dependencyEnumerator(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME),
        dependencyEnumerator(module, JpsJavaClasspathKind.TEST_RUNTIME).takeIf { Properties.includeTests.toBoolean() },
        dependencyEnumerator(module, JpsJavaClasspathKind.TEST_COMPILE).takeIf { Properties.includeTests.toBoolean() },
    ).forEach {
        it.processModules { dependency ->
            if (modulesToBuild.add(dependency.name)) {
                traverseDependenciesRecursively(dependency, modulesToBuild)
            }
        }
    }
}

private fun dependencyEnumerator(
    module: JpsModule,
    javaClasspathKind: JpsJavaClasspathKind
): JpsJavaDependenciesEnumerator =
    JpsJavaExtensionService.dependencies(module)
        .withoutLibraries()
        .withoutSdk()
        .includedIn(javaClasspathKind)

private fun runBuild(model: JpsModel) {
    var exitCode = 0
    val errors = mutableListOf<BuildMessage>()
    val withProgress = Properties.withProgress.toBoolean()
    try {
        val mainJpsModule = Properties.moduleName?.let { moduleName ->
            model.project.modules.find { it.name == moduleName } ?: error("Module $moduleName not found.")
        }
        val modulesToBuild = mainJpsModule?.let { getModulesToBuild(it) } ?: emptySet()
        val generatedFilesFilePath = Properties.generatedFilesFilePath
        val generatedFiles: MutableSet<Pair<String, String>>? = generatedFilesFilePath?.let { LinkedHashSet() }
        val filePaths = Properties.filePaths?.run { lines() } ?: emptyList()
        val allModules = mainJpsModule == null && filePaths.isEmpty()
        runBuild(
            loader = { model },
            dataStorageRoot = Path(Properties.dataStorageRoot!!),
            modulesSet = modulesToBuild,
            filePaths = filePaths,
            forceBuild = Properties.forceRebuild,
            allModules = allModules,
            includeTests = Properties.includeTests.toBoolean(),
            messageHandler = { msg ->
                when (msg) {
                    is ProgressMessage -> {
                        if (withProgress) {
                            println("[Progress = ${msg.done}]:$msg")
                        }
                    }

                    is FileGeneratedEvent -> {
                        if (generatedFiles != null) {
                            for (path in msg.paths) {
                                generatedFiles += path.first to path.second
                            }
                        }
                    }

                    else -> {
                        println(msg)
                        if (msg.kind == BuildMessage.Kind.ERROR || msg.kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR) {
                            errors.add(msg)
                            exitCode = 1
                        }
                    }
                }
            })

        if (generatedFilesFilePath != null && generatedFiles != null) {
            saveGeneratedFiles(generatedFiles, File(generatedFilesFilePath))
        }
        val classpathOutputFilePath = Properties.classpathOutputFilePath
        if (mainJpsModule != null && classpathOutputFilePath != null) {
            saveRuntimeClasspath(mainJpsModule, File(classpathOutputFilePath))
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        exitCode = 1
    } finally {
        if (errors.isNotEmpty()) {
            System.err.println("\n## JPS Errors Summary")
            errors.forEach { error -> System.err.println(error) }
        }
        exitProcess(exitCode)
    }
}

private fun getModulesToBuild(mainJpsModule: JpsModule): Set<String> {
    val modulesToBuild = mutableSetOf(mainJpsModule.name)
    if (Properties.includeRuntimeDependencies.toBoolean()) {
        traverseDependenciesRecursively(mainJpsModule, modulesToBuild)
    }
    return modulesToBuild
}

private fun saveRuntimeClasspath(mainJpsModule: JpsModule, classpathOutputFile: File) {
    val enumerator = JpsJavaExtensionService.dependencies(mainJpsModule)
        .recursively()
        .withoutSdk()

    if (Properties.includeTests.toBoolean()) {
        enumerator.includedIn(JpsJavaClasspathKind.TEST_RUNTIME)
    } else {
        enumerator.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
    }

    val m2Dependencies = enumerator.libraries
        .flatMapTo(mutableSetOf()) { library -> library.getFiles(JpsOrderRootType.COMPILED) }
        .filter { it.name.endsWith(".jar") }

    val compilationOutputs = enumerator.satisfying { it !is JpsLibraryDependency }.classes().roots
    classpathOutputFile.writeText((compilationOutputs + m2Dependencies).joinToString(File.pathSeparator))
}

private fun saveGeneratedFiles(generatedFiles: Set<Pair<String, String>>, file: File) {
    for ((root, relativePath) in generatedFiles) {
        file.appendText("root:$root\n")
        file.appendText("path:$relativePath\n")
    }
}

private fun initializeModel(): JpsModel {
    val model: JpsModel = JpsElementFactory.getInstance().createModel()

    val pathVariablesConfiguration =
        JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)

    val kotlinHome = Properties.kotlinHome
    if (kotlinHome != null) {
        pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinHome)
    }
    pathVariablesConfiguration.addPathVariable(
        "MAVEN_REPOSITORY",
        File(System.getProperty("user.home"), ".m2/repository").absolutePath
    )

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    val projectPath = Path(Properties.projectPath)
    val projectBasePath = Properties.projectBasePath?.let { Path(it) }
    val externalProjectConfigDir = System.getProperty("external.project.config")?.let { Path(it) }
    JpsProjectLoader.loadProject(model.project, pathVariables, JpsPathMapper.IDENTITY, projectPath, projectBasePath, false, externalProjectConfigDir)
    val projectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.project)
    if (Properties.outputPath != null) {
        projectExtension.outputUrl = "file://${Properties.outputPath}"
    }
    if (Properties.dataStorageRoot == null) {
        val outputPath = urlToPath(projectExtension.outputUrl)
        if (outputPath.isEmpty()) {
            error("Either project `outputPath` or `dataStorageRoot` must be set")
        }
        Properties.dataStorageRoot = Paths.get(outputPath, "cache").toString()
    }
    return model
}

private fun addJdk(global: JpsGlobal, jdkName: String, jdkHomePath: String) {
    val sdk = JpsJavaExtensionService.getInstance().addJavaSdk(global, jdkName, jdkHomePath)
    val toolsJar = File(jdkHomePath, "lib/tools.jar")
    if (toolsJar.exists()) {
        sdk.addRoot(toolsJar, JpsOrderRootType.COMPILED)
    }
}

private fun readModulesFromReleaseFile(model: JpsModel, sdkName: String, sdkHome: String) {
    val additionalSdk = model.global.libraryCollection.findLibrary(sdkName)!!
    val urls = additionalSdk.getRoots(JpsOrderRootType.COMPILED).map { it.url }
    readModulesFromReleaseFile(File(sdkHome)).forEach {
        if (!urls.contains(it)) {
            additionalSdk.addRoot(it, JpsOrderRootType.COMPILED)
        }
    }
}

/**
 * Code is copied from com.intellij.openapi.projectRoots.impl.JavaSdkImpl#findClasses(java.io.File, boolean)
 */
private fun readModulesFromReleaseFile(jbrBaseDir: File): List<String> {
    val releaseFile = File(jbrBaseDir, "release")
    if (!releaseFile.exists()) return emptyList()
    releaseFile.bufferedReader().use { stream ->
        val p = java.util.Properties()
        p.load(stream)
        val jbrBaseUrl = JRT_PROTOCOL + SCHEME_SEPARATOR +
                FileUtil.toSystemIndependentName(jbrBaseDir.absolutePath) +
                JAR_SEPARATOR
        val modules = p.getProperty("MODULES")
        return if (modules != null) {
            StringUtil.split(StringUtil.unquoteString(modules), " ").map { jbrBaseUrl + it }
        } else {
            emptyList()
        }
    }
}

private fun getCurrentJdk(): String {
    val javaHome = System.getProperty("java.home")
    if (File(javaHome).name == "jre") {
        return File(javaHome).parent
    }

    return javaHome
}

private fun setupLogging() {
    Properties.buildLogPath.let { logPath ->
        if (logPath.isNullOrBlank()) {
            System.setProperty("jps.use.default.file.logging", "false")
        }
        else {
            println("buildLogPath=${logPath}")
            System.setProperty("jps.use.default.file.logging", "true")
            System.setProperty("jps.log.dir", logPath)
            LogSetup.initLoggers()
        }
    }
}
