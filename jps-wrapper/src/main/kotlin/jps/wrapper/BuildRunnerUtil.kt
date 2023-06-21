package jps.wrapper

import org.jetbrains.jps.api.BuildType
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.BuildRunner
import org.jetbrains.jps.cmdline.JpsModelLoader
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.fs.BuildFSState
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
        val targetsByType = HashMap<BuildTargetType<*>, MutableSet<String>>()
        for (set in modulesSet) {
            val targetType = JavaModuleBuildTargetType.PRODUCTION
            targetsByType.getOrPut(targetType, ::HashSet).add(set)
        }
        for ((buildTargetType, set) in targetsByType) {
            val builder = TargetTypeBuildScope.newBuilder().setTypeId(buildTargetType.typeId).setForceBuild(forceBuild)
            for (targetId in set) {
                builder.addTargetId(targetId)
            }
            scopes.add(builder.build())
        }
    } else {
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

