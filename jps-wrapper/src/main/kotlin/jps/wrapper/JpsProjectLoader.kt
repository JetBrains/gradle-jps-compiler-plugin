package jps.wrapper

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.jps.TimingLog
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.*
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl
import org.jetbrains.jps.model.serialization.impl.JpsSerializationFormatException
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerConfigurationSerializer
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationSerializer
import org.jetbrains.jps.service.SharedThreadPool
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Future

// Based on org.jetbrains.jps.model.serialization.JpsProjectLoader.
// Should be removed when the original api will allow to specify customized project baseDir.
class JpsProjectLoader private constructor(
    private val myProject: JpsProject,
    private val myPathVariables: Map<String?, String?>,
    private val myPathMapper: JpsPathMapper,
    baseDir: Path,
    loadUnloadedModules: Boolean
) : JpsLoaderBase(
    createProjectMacroExpander(
        myPathVariables, baseDir
    )
) {
    private val myLoadUnloadedModules: Boolean

    init {
        myProject.container.setChild(
            JpsProjectSerializationDataExtensionImpl.ROLE,
            JpsProjectSerializationDataExtensionImpl(baseDir)
        )
        myLoadUnloadedModules = loadUnloadedModules
    }

    override fun <E : JpsElement?> loadComponentData(
        serializer: JpsElementExtensionSerializerBase<E>,
        configFile: Path
    ): Element? {
        val externalConfigDir = resolveExternalProjectConfig("project")
        val data = super.loadComponentData(serializer, configFile)
        val componentName = serializer.componentName
        if (externalConfigDir == null || componentName != "CompilerConfiguration") {
            return data
        }
        val prefixedComponentName = "External$componentName"
        var externalData: Element? = null
        for (child in JDOMUtil.getChildren(loadRootElement(externalConfigDir.resolve(configFile.fileName)))) {
            // be ready to handle both original name and prefixed
            if (child.name == prefixedComponentName || JDomSerializationUtil.isComponent(
                    prefixedComponentName,
                    child
                ) || child.name == componentName || JDomSerializationUtil.isComponent(componentName, child)
            ) {
                externalData = child
                break
            }
        }
        return deepMergeCompilerConfigurations(data, externalData)
    }

    private fun loadFromDirectory(dir: Path) {
        myProject.name = getDirectoryBaseProjectName(dir)
        val defaultConfigFile = dir.resolve("misc.xml")
        val projectSdkType = loadProjectRoot(loadRootElement(defaultConfigFile))
        for (extension in JpsModelSerializerExtension.getExtensions()) {
            for (serializer in extension.projectExtensionSerializers) {
                loadComponents(dir, defaultConfigFile, serializer, myProject)
            }
        }
        val externalConfigDir = resolveExternalProjectConfig("project")
        if (externalConfigDir != null) {
            LOG.info("External project config dir is used: $externalConfigDir")
        }
        var moduleData =
            JDomSerializationUtil.findComponent(loadRootElement(dir.resolve("modules.xml")), MODULE_MANAGER_COMPONENT)
        var externalModuleData: Element?
        if (externalConfigDir == null) {
            externalModuleData = null
        } else {
            val rootElement = loadRootElement(externalConfigDir.resolve("modules.xml"))
            if (rootElement == null) {
                externalModuleData = null
            } else {
                externalModuleData = JDomSerializationUtil.findComponent(rootElement, "ExternalProjectModuleManager")
                if (externalModuleData == null) {
                    externalModuleData = JDomSerializationUtil.findComponent(rootElement, "ExternalModuleListStorage")
                }
                // old format (root tag is "component")
                if (externalModuleData == null && rootElement.name == JDomSerializationUtil.COMPONENT_ELEMENT) {
                    externalModuleData = rootElement
                }
            }
        }
        if (externalModuleData != null) {
            val componentName = externalModuleData.getAttributeValue("name")
            LOG.assertTrue(componentName != null && componentName.startsWith("External"))
            externalModuleData.setAttribute("name", componentName!!.substring("External".length))
            if (moduleData == null) {
                moduleData = externalModuleData
            } else {
                JDOMUtil.deepMerge(moduleData, externalModuleData)
            }
        }
        val workspaceFile = dir.resolve("workspace.xml")
        loadModules(moduleData, projectSdkType, workspaceFile)
        val timingLog = TimingLog.startActivity("loading project libraries")
        for (libraryFile in listXmlFiles(dir.resolve("libraries"))) {
            loadProjectLibraries(loadRootElement(libraryFile))
        }
        if (externalConfigDir != null) {
            loadProjectLibraries(loadRootElement(externalConfigDir.resolve("libraries.xml")))
        }
        timingLog.run()
        val artifactsTimingLog = TimingLog.startActivity("loading artifacts")
        for (artifactFile in listXmlFiles(dir.resolve("artifacts"))) {
            loadArtifacts(loadRootElement(artifactFile))
        }
        if (externalConfigDir != null) {
            loadArtifacts(loadRootElement(externalConfigDir.resolve("artifacts.xml")))
        }
        artifactsTimingLog.run()
        if (hasRunConfigurationSerializers()) {
            val runConfTimingLog = TimingLog.startActivity("loading run configurations")
            for (configurationFile in listXmlFiles(dir.resolve("runConfigurations"))) {
                JpsRunConfigurationSerializer.loadRunConfigurations(myProject, loadRootElement(configurationFile))
            }
            JpsRunConfigurationSerializer.loadRunConfigurations(
                myProject,
                JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "RunManager")
            )
            runConfTimingLog.run()
        }
    }

    private fun loadFromIpr(iprFile: Path) {
        val iprRoot = loadRootElement(iprFile)
        val projectName = FileUtilRt.getNameWithoutExtension(iprFile.fileName.toString())
        myProject.name = projectName
        val iwsFile = iprFile.parent.resolve("$projectName.iws")
        val iwsRoot = loadRootElement(iwsFile)
        val projectSdkType = loadProjectRoot(iprRoot)
        for (extension in JpsModelSerializerExtension.getExtensions()) {
            for (serializer in extension.projectExtensionSerializers) {
                val rootTag =
                    if (JpsProjectExtensionSerializer.WORKSPACE_FILE == serializer.configFileName) iwsRoot else iprRoot
                val component = JDomSerializationUtil.findComponent(rootTag, serializer.componentName)
                if (component != null) {
                    serializer.loadExtension(myProject, component)
                } else {
                    serializer.loadExtensionWithDefaultSettings(myProject)
                }
            }
        }
        loadModules(JDomSerializationUtil.findComponent(iprRoot, "ProjectModuleManager"), projectSdkType, iwsFile)
        loadProjectLibraries(JDomSerializationUtil.findComponent(iprRoot, "libraryTable"))
        loadArtifacts(JDomSerializationUtil.findComponent(iprRoot, "ArtifactManager"))
        if (hasRunConfigurationSerializers()) {
            JpsRunConfigurationSerializer.loadRunConfigurations(
                myProject,
                JDomSerializationUtil.findComponent(iprRoot, "ProjectRunConfigurationManager")
            )
            JpsRunConfigurationSerializer.loadRunConfigurations(
                myProject,
                JDomSerializationUtil.findComponent(iwsRoot, "RunManager")
            )
        }
    }

    private fun loadArtifacts(artifactManagerComponent: Element?) {
        JpsArtifactSerializer.loadArtifacts(myProject, artifactManagerComponent)
    }

    private fun loadProjectRoot(root: Element?): JpsSdkType<*>? {
        var sdkType: JpsSdkType<*>? = null
        val rootManagerElement = JDomSerializationUtil.findComponent(root, "ProjectRootManager")
        if (rootManagerElement != null) {
            val sdkName = rootManagerElement.getAttributeValue("project-jdk-name")
            val sdkTypeId = rootManagerElement.getAttributeValue("project-jdk-type")
            if (sdkName != null) {
                sdkType = JpsSdkTableSerializer.getSdkType(sdkTypeId)
                JpsSdkTableSerializer.setSdkReference(myProject.sdkReferencesTable, sdkName, sdkType)
            }
        }
        return sdkType
    }

    private fun loadProjectLibraries(libraryTableElement: Element?) {
        JpsLibraryTableSerializer.loadLibraries(libraryTableElement, myPathMapper, myProject.libraryCollection)
    }

    private fun loadModules(componentElement: Element?, projectSdkType: JpsSdkType<*>?, workspaceFile: Path) {
        val timingLog = TimingLog.startActivity("loading modules")
        if (componentElement == null) {
            return
        }
        val unloadedModules: MutableSet<String> = HashSet()
        if (!myLoadUnloadedModules && workspaceFile.toFile().exists()) {
            val unloadedModulesList =
                JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "UnloadedModulesList")
            for (element in JDOMUtil.getChildren(unloadedModulesList, "module")) {
                unloadedModules.add(element.getAttributeValue("name"))
            }
        }
        val foundFiles = CollectionFactory.createSmallMemoryFootprintSet<Path>()
        val moduleFiles: MutableList<Path> = ArrayList()
        for (moduleElement in JDOMUtil.getChildren(componentElement.getChild(MODULES_TAG), MODULE_TAG)) {
            val path = moduleElement.getAttributeValue(FILE_PATH_ATTRIBUTE)
            if (path != null) {
                val file = Paths.get(path)
                if (foundFiles.add(file) && !unloadedModules.contains(getModuleName(file))) {
                    moduleFiles.add(file)
                }
            }
        }
        val modules = loadModules(moduleFiles, projectSdkType, myPathVariables, myPathMapper)
        for (module in modules) {
            myProject.addModule(module)
        }
        timingLog.run()
    }

    companion object {
        const val MODULE_MANAGER_COMPONENT = "ProjectModuleManager"
        const val MODULES_TAG = "modules"
        const val MODULE_TAG = "module"
        const val FILE_PATH_ATTRIBUTE = "filepath"
        private const val CLASSPATH_ATTRIBUTE = "classpath"
        private const val CLASSPATH_DIR_ATTRIBUTE = "classpath-dir"
        private val LOG = Logger.getInstance(
            org.jetbrains.jps.model.serialization.JpsProjectLoader::class.java
        )
        private val ourThreadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "JpsProjectLoader Pool", SharedThreadPool.getInstance(), Runtime.getRuntime().availableProcessors()
        )

        fun createProjectMacroExpander(pathVariables: Map<String?, String?>?, baseDir: Path): JpsMacroExpander {
            val expander = JpsMacroExpander(pathVariables)
            expander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, baseDir.toFile())
            return expander
        }

        @Throws(IOException::class)
        fun loadProject(project: JpsProject, pathVariables: Map<String?, String?>, projectPath: String) {
            loadProject(project, pathVariables, JpsPathMapper.IDENTITY, projectPath, null, false)
        }

        @Throws(IOException::class)
        fun loadProject(
            project: JpsProject,
            pathVariables: Map<String?, String?>,
            pathMapper: JpsPathMapper,
            projectPath: String,
            projectBasePath: String?,
            loadUnloadedModules: Boolean
        ) {
            val file = Paths.get(FileUtil.toCanonicalPath(projectPath))
            if (Files.isRegularFile(file) && projectPath.endsWith(".ipr")) {
                JpsProjectLoader(project, pathVariables, pathMapper, file.parent, loadUnloadedModules).loadFromIpr(file)
            } else {
                val dotIdea = file.resolve(PathMacroUtil.DIRECTORY_STORE_NAME)
                val directory = if (Files.isDirectory(dotIdea)) {
                    dotIdea
                } else if (Files.isDirectory(file) && file.endsWith(PathMacroUtil.DIRECTORY_STORE_NAME)) {
                    file
                } else {
                    throw IOException("Cannot find IntelliJ IDEA project files at $projectPath")
                }
                val baseDir = if (projectBasePath != null) {
                    Paths.get(FileUtil.toCanonicalPath(projectBasePath))
                } else {
                    directory.parent
                }
                JpsProjectLoader(project, pathVariables, pathMapper, baseDir, loadUnloadedModules).loadFromDirectory(
                    directory
                )
            }
        }

        fun getDirectoryBaseProjectName(dir: Path): String {
            val name = JpsPathUtil.readProjectName(dir)
            return name ?: JpsPathUtil.getDefaultProjectName(dir)
        }

        private fun deepMergeCompilerConfigurations(data: Element?, externalData: Element?): Element? {
            if (data == null) return externalData
            if (externalData == null) return data
            JDOMUtil.deepMerge(data, externalData)
            JDOMUtil.reduceChildren(JpsJavaCompilerConfigurationSerializer.BYTECODE_TARGET_LEVEL, data)
            return data
        }

        private fun hasRunConfigurationSerializers(): Boolean {
            for (extension in JpsModelSerializerExtension.getExtensions()) {
                if (!extension.runConfigurationPropertiesSerializers.isEmpty()) {
                    return true
                }
            }
            return false
        }

        private fun listXmlFiles(dir: Path): List<Path> {
            try {
                Files.newDirectoryStream(dir) { it: Path ->
                    it.fileName.toString().endsWith(".xml") && Files.isRegularFile(it)
                }
                    .use { stream -> return ContainerUtil.collect(stream.iterator()) }
            } catch (e: IOException) {
                return emptyList()
            }
        }

        private fun resolveExternalProjectConfig(subDirName: String): Path? {
            val externalProjectConfigDir = System.getProperty("external.project.config")
            return if (StringUtil.isEmptyOrSpaces(externalProjectConfigDir)) null else Paths.get(
                externalProjectConfigDir,
                subDirName
            )
        }

        fun loadModules(
            moduleFiles: List<Path>,
            projectSdkType: JpsSdkType<*>?,
            pathVariables: Map<String?, String?>,
            pathMapper: JpsPathMapper
        ): List<JpsModule> {
            val modules: MutableList<JpsModule> = ArrayList()
            val futureModuleFilesContents: MutableList<Future<Pair<Path, Element?>>> = ArrayList()
            val externalModuleDir = resolveExternalProjectConfig("modules")
            if (externalModuleDir != null) {
                LOG.info("External project config dir is used for modules: $externalModuleDir")
            }
            for (file in moduleFiles) {
                futureModuleFilesContents.add(ourThreadPool.submit<Pair<Path, Element?>> {
                    val expander = createModuleMacroExpander(pathVariables, file)
                    var data = loadRootElement(file, expander)
                    if (externalModuleDir != null) {
                        val externalName = FileUtilRt.getNameWithoutExtension(file.fileName.toString()) + ".xml"
                        val externalData = loadRootElement(externalModuleDir.resolve(externalName), expander)
                        if (externalData != null) {
                            if (data == null) {
                                data = externalData
                            } else {
                                JDOMUtil.merge(data, externalData)
                            }
                        }
                    }
                    if (data == null) {
                        LOG.info("Module '" + getModuleName(file) + "' is skipped: " + file.toAbsolutePath() + " doesn't exist")
                    }
                    Pair.create(file, data)
                })
            }
            return try {
                val classpathDirs: MutableList<String> = ArrayList()
                for (moduleFile in futureModuleFilesContents) {
                    val rootElement = moduleFile.get().getSecond()
                    if (rootElement != null) {
                        val classpathDir = rootElement.getAttributeValue(CLASSPATH_DIR_ATTRIBUTE)
                        if (classpathDir != null) {
                            classpathDirs.add(classpathDir)
                        }
                    }
                }
                val futures: MutableList<Future<JpsModule>> = ArrayList()
                for (futureModuleFile in futureModuleFilesContents) {
                    val moduleFile = futureModuleFile.get()
                    if (moduleFile.getSecond() != null) {
                        futures.add(ourThreadPool.submit<JpsModule> {
                            loadModule(
                                moduleFile.getFirst(),
                                moduleFile.getSecond()!!,
                                classpathDirs,
                                projectSdkType,
                                pathVariables,
                                pathMapper
                            )
                        })
                    }
                }
                for (future in futures) {
                    val module = future.get()
                    if (module != null) {
                        modules.add(module)
                    }
                }
                modules
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        private fun loadModule(
            file: Path, moduleRoot: Element, paths: List<String>,
            projectSdkType: JpsSdkType<*>?, pathVariables: Map<String?, String?>, pathMapper: JpsPathMapper
        ): JpsModule {
            val name = getModuleName(file)
            val typeId = moduleRoot.getAttributeValue("type")
            val serializer = getModulePropertiesSerializer(typeId)
            val module = createModule(name, moduleRoot, serializer)
            module.container.setChild(
                JpsModuleSerializationDataExtensionImpl.ROLE,
                JpsModuleSerializationDataExtensionImpl(file.parent)
            )
            for (extension in JpsModelSerializerExtension.getExtensions()) {
                extension.loadModuleOptions(module, moduleRoot)
            }
            val baseModulePath = FileUtil.toSystemIndependentName(file.parent.toString())
            val classpath = moduleRoot.getAttributeValue(CLASSPATH_ATTRIBUTE)
            if (classpath == null) {
                try {
                    JpsModuleRootModelSerializer.loadRootModel(
                        module, JDomSerializationUtil.findComponent(moduleRoot, "NewModuleRootManager"),
                        projectSdkType, pathMapper
                    )
                } catch (e: JpsSerializationFormatException) {
                    LOG.warn("Failed to load module configuration from " + file.toString() + ": " + e.message, e)
                }
            } else {
                for (extension in JpsModelSerializerExtension.getExtensions()) {
                    val classpathSerializer = extension.classpathSerializer
                    if (classpathSerializer != null && classpathSerializer.classpathId == classpath) {
                        val classpathDir = moduleRoot.getAttributeValue(CLASSPATH_DIR_ATTRIBUTE)
                        val expander = createModuleMacroExpander(pathVariables, file)
                        classpathSerializer.loadClasspath(
                            module,
                            classpathDir,
                            baseModulePath,
                            expander,
                            paths,
                            projectSdkType
                        )
                    }
                }
            }
            val facetsTag =
                JDomSerializationUtil.findComponent(moduleRoot, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME)
            val externalFacetsTag = JDomSerializationUtil.findComponent(moduleRoot, "ExternalFacetManager")
            val mergedFacetsTag: Element?
            mergedFacetsTag = if (facetsTag == null) {
                externalFacetsTag
            } else if (externalFacetsTag != null) {
                JDOMUtil.deepMerge(facetsTag, externalFacetsTag)
            } else {
                facetsTag
            }
            JpsFacetSerializer.loadFacets(module, mergedFacetsTag)
            return module
        }

        private fun getModuleName(file: Path): String {
            return FileUtilRt.getNameWithoutExtension(file.fileName.toString())
        }

        fun createModuleMacroExpander(pathVariables: Map<String?, String?>?, moduleFile: Path): JpsMacroExpander {
            val expander = JpsMacroExpander(pathVariables)
            val moduleDirPath = PathMacroUtil.getModuleDir(moduleFile.toAbsolutePath().toString())
            if (moduleDirPath != null) {
                expander.addFileHierarchyReplacements(
                    PathMacroUtil.MODULE_DIR_MACRO_NAME,
                    File(FileUtil.toSystemDependentName(moduleDirPath))
                )
            }
            return expander
        }

        private fun <P : JpsElement?> createModule(
            name: String,
            moduleRoot: Element,
            loader: JpsModulePropertiesSerializer<P>
        ): JpsModule {
            val componentName = loader.componentName
            val component =
                if (componentName != null) JDomSerializationUtil.findComponent(moduleRoot, componentName) else null
            return JpsElementFactory.getInstance().createModule(name, loader.type, loader.loadProperties(component))
        }

        private fun getModulePropertiesSerializer(typeId: String?): JpsModulePropertiesSerializer<*> {
            for (extension in JpsModelSerializerExtension.getExtensions()) {
                for (loader in extension.modulePropertiesSerializers) {
                    if (loader.typeId == typeId) {
                        return loader
                    }
                }
            }
            return object :
                JpsModulePropertiesSerializer<JpsDummyElement>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE", null) {
                override fun loadProperties(componentElement: Element?): JpsDummyElement {
                    return JpsElementFactory.getInstance().createDummyElement()
                }
            }
        }
    }
}