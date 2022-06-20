package jps.wrapper

import kotlin.reflect.KProperty

object Properties {
    val kotlinHome by NullableDelegate

    val projectPath by NotNullDelegate

    val projectBasePath by NullableDelegate

    val outputPath by NullableDelegate

    val moduleName by NullableDelegate

    val incremental by NotNullDelegate

    val includeRuntimeDependencies by NotNullDelegate

    val includeTests by NotNullDelegate

    val withProgress by NotNullDelegate

    val parallel by NotNullDelegate

    val forceRebuild = !incremental.toBoolean()

    var dataStorageRoot by NullableDelegate

    val classpathOutputFilePath by NullableDelegate

    val jdkTable by NullableDelegate

    open class Delegate {
        open operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
            return System.getProperty("build.${property.name}")
        }

        open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            if (value != null) {
                System.setProperty("build.${property.name}", value)
            } else {
                System.clearProperty("build.${property.name}")
            }
        }
    }

    object NullableDelegate : Delegate()

    object NotNullDelegate : Delegate() {
        override operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return super.getValue(thisRef, property) ?: error("Property ${property.name} not found")
        }
    }
}
