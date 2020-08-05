package fleet.bootstrap

import org.jetbrains.jps.api.CmdlineRemoteProto
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import kotlin.system.exitProcess

fun main() {
    println(System.getProperty("java.home"))
    val model = initializeModel()

    val scopes =
            mutableListOf<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope>()

    val mainModule = Properties.module

    JavaModuleBuildTargetType.ALL_TYPES.forEach { moduleType ->
        val builder =
                CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder()
                        .setTypeId(moduleType.typeId).setForceBuild(Properties.forceRebuild)
        scopes.add(builder.addAllTargetId(listOf(mainModule)).build())
    }

    model.project.modules.mapNotNull { it.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName }.distinct()
            .forEach { sdkName ->
                addJdk(model.global, sdkName, getCurrentJdk())
            }

    try {
        Standalone.runBuild({ model }, File(Properties.dataStorageRoot), { msg ->
            println(msg)
            if (msg.kind == BuildMessage.Kind.ERROR || msg.kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR) exitProcess(1)
        }, scopes, true)
        saveRuntimeClasspath(model, mainModule)
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    } finally {
        exitProcess(0)
    }
}

private fun saveRuntimeClasspath(model: JpsModel, mainModule: String) {
    val mainJpsModule = model.project.modules.find { it.name == mainModule } ?: error("Module $mainModule not found.")
    val enumerator = JpsJavaExtensionService.dependencies(mainJpsModule)
            .recursively()
            .withoutSdk()
            .includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)

    val m2Deps = enumerator.libraries
            .flatMapTo(mutableSetOf()) { library -> library.getFiles(JpsOrderRootType.COMPILED) }
            .filter { it.name.endsWith(".jar") }

    val compilationOutputs = enumerator.satisfying { it !is JpsLibraryDependency }.classes().roots

    File(Properties.classpathOut).writeText((compilationOutputs + m2Deps).joinToString(":"))
}

private fun initializeModel(): JpsModel {
    val model: JpsModel = JpsElementFactory.getInstance().createModel()

    val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", "${Properties.kotlinHome}/kotlinc")
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", File(System.getProperty("user.home"), ".m2/repository").absolutePath)

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, Properties.project)
    return model
}

private fun addJdk(global: JpsGlobal, jdkName: String, jdkHomePath: String) {
    val sdk = JpsJavaExtensionService.getInstance().addJavaSdk(global, jdkName, jdkHomePath)
    val toolsJar = File(jdkHomePath, "lib/tools.jar")
    if (toolsJar.exists()) {
        sdk.addRoot(toolsJar, JpsOrderRootType.COMPILED)
    }
}

private fun getCurrentJdk(): String {
    val javaHome = System.getProperty("java.home")
    if (File(javaHome).name == "jre") {
        return File(javaHome).parent
    }

    return javaHome
}