package fleet.bootstrap

import kotlin.reflect.KProperty

object Properties {
    val kotlinHome by Properties

    val project by Properties
    val module by Properties

    private val incremental by Properties
    val forceRebuild = !incremental.toBoolean()

    val dataStorageRoot by Properties

    val classpathOut by Properties

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String =
            System.getProperty("build.${property.name}") ?: error("Property ${property.name} not found")
}