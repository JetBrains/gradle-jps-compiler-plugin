package jps.wrapper

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.api.BuildType
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.ResourcesTargetType
import org.jetbrains.jps.cmdline.BuildRunner
import org.jetbrains.jps.cmdline.JpsModelLoader
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes.RESOURCES
import org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE
import org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE
import org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.File

fun runBuild(
    loader: JpsModelLoader,
    dataStorageRoot: File,
    modulesSet: Set<String>,
    filePaths: List<String>,
    forceBuild: Boolean,
    allModules: Boolean,
    includeTests: Boolean,
    messageHandler: MessageHandler
) {
    val scopes: MutableList<TargetTypeBuildScope> = ArrayList()
    if (filePaths.isNotEmpty()) {
        val jpsModel = loader.loadModel()
        val targetsByType = HashMap<BuildTargetType<*>, MutableSet<String>>()

        val jpsModules = jpsModel.project.modules.toMutableList()
        for (filePath in filePaths) {
            findBuildTarget(filePath, jpsModules)?.let { (targetType, moduleName) ->
                targetsByType.getOrPut(targetType, ::HashSet).add(moduleName)
            }
        }
        for ((buildTargetType, modules) in targetsByType) {
            val builder = TargetTypeBuildScope.newBuilder().setTypeId(buildTargetType.typeId).setForceBuild(forceBuild)
            for (moduleName in modules) {
                builder.addTargetId(moduleName)
            }
            scopes.add(builder.build())
        }
    }
    if (modulesSet.isNotEmpty() || allModules) {
        for (type in JavaModuleBuildTargetType.ALL_TYPES) {
            if (includeTests || !type.isTests) {
                val builder = TargetTypeBuildScope.newBuilder().setTypeId(type.typeId).setForceBuild(forceBuild)
                if (allModules) {
                    scopes.add(builder.setAllTargets(true).build())
                } else if (modulesSet.isNotEmpty()) {
                    scopes.add(builder.addAllTargetId(modulesSet).build())
                }
            }
        }
    }
    val buildRunner = BuildRunner(loader)
    if (filePaths.isNotEmpty()) {
        buildRunner.setFilePaths(filePaths)
    }
    val descriptor = buildRunner.load(messageHandler, dataStorageRoot, BuildFSState(true))
    try {
        buildRunner.runBuild(
            descriptor,
            CanceledStatus.NULL,
            messageHandler,
            BuildType.BUILD,
            scopes,
            false
        )
    } finally {
        descriptor.release()
    }
}

private fun findBuildTarget(
    filePath: String,
    jpsModules: MutableList<JpsModule>,
) : Pair<BuildTargetType<*>, String>? {
    jpsModules.forEachIndexed { index, module ->
        for (sourceRoot in module.sourceRoots) {
            val isAncestor = FileUtil.isAncestor(sourceRoot.file.path, filePath, true)
            if (isAncestor) {
                val targetType = toTargetType(sourceRoot) ?: break
                if (jpsModules.size > 1 && index > 0) {
                    jpsModules.removeAt(index)
                    jpsModules.add(0, module)
                }
                return targetType to module.name
            }
        }
    }
    return null
}

private fun toTargetType(sourceRoot: JpsModuleSourceRoot): BuildTargetType<*>? {
    return when (sourceRoot.rootType) {
        TEST_SOURCE -> JavaModuleBuildTargetType.TEST
        SOURCE -> JavaModuleBuildTargetType.PRODUCTION
        RESOURCES -> ResourcesTargetType.PRODUCTION
        TEST_RESOURCE -> ResourcesTargetType.TEST
        else -> null
    }
}

