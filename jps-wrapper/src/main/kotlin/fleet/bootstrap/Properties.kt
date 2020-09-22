package fleet.bootstrap

import kotlin.reflect.KProperty

object Properties {
    val kotlinHome by Properties

    val projectPath by Properties

    val moduleName by Properties

    private val incremental by Properties
    val forceRebuild = !incremental.toBoolean()

    val dataStorageRoot by Properties

    val classpathOutputFilePath by Properties

    val jdkTable by Properties

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String =
            System.getProperty("build.${property.name}") ?: error("Property ${property.name} not found")
}