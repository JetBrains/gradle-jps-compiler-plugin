package jps.wrapper

import kotlin.reflect.KProperty

object Properties {
    val kotlinHome by Properties

    val projectPath by Properties

    val outputPath by Properties

    val moduleName by Properties

    val incremental by Properties

    val includeRuntimeDependencies by Properties

    val includeTests by Properties

    val withProgress by Properties

    val parallel by Properties

    val forceRebuild = !incremental.toBoolean()

    val dataStorageRoot by Properties

    val classpathOutputFilePath by Properties

    val jdkTable = System.getProperty("build.jdkTable")

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String =
            System.getProperty("build.${property.name}") ?: error("Property ${property.name} not found")
}