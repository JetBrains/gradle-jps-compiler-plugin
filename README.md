# Gradle JPS Compiler Plugin

![](https://camo.githubusercontent.com/f9a78707625c83e4a02e265758bc2d112701ef06c968c116bd6bf91b1df24cf0/68747470733a2f2f6a622e67672f6261646765732f7465616d2d706c61737469632e737667)

The plugin for Gradle that is able to build JPS-based project.

The plugin doesn't interpret JPS project model and doesn't convert it to gradle. Basically it's an interface for
JPS-standalone tool.

# Usage

```kotlin
plugins {
    id("com.jetbrains.intellij.jps-compiler-plugin") version "0.3.1"
}
```

## Configuration

By default, Gradle's java runtime will be used for compilation. 
To customize that, `jdkTable` extension can be filled with the `sdk_name->runtime_path` mapping.

```kotlin
configure<JdkTableExtension> {
  jdk("corretto-14", org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath)
  jdk("corretto-11", project.property("jps_jdk_11")!!.toString())
}
```

## Compilation

Available `jpsVersions` are listed
on [IntelliJ Maven Repository](https://www.jetbrains.com/intellij-repository/releases).

```kotlin
val jpsCompilationTask = tasks.register<JpsCompile>("jpsCompile") {
    moduleName = "my.intellij.project.module.main"

    jpsVersion = "212-SNAPSHOT"
    incremental = true
    parallel = true
    includeTests = false
    projectPath = projectDir.absolutePath
    kotlinVersion = "1.4.10-release-IJ2020.2-1"
}
```

## Running

The result of compilation task is a file with the classpath of compiled module. It might be used for running
pure `JavaExec` on the compilation results.

```kotlin
task<JavaExec>("run") {
    dependsOn(jpsCompilationTask)
    classpath({ jpsCompile.flatMap { it.classpathOutputFilePath }.get().asFile.readText().split(File.pathSeparatorChar) })
    main = "my.intellij.project.MainKt"
    workingDir(projectDir)
    jvmArgs(
        "-ea",
        "-XX:+AllowRedefinitionToAddDeleteMethods",
        "-Djava.awt.headless=true"
    )
}
```
