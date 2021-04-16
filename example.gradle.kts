import jps.plugin.JpsCompile

plugins {
    id("com.jetbrains.intellij.jps-plugin")
}

jdkTable {
    jdk("corretto-11", project.ext["jps.jdk"]!!.toString())
}

val jpsCompile = task<JpsCompile>("jpsCompile") {
    incremental = true
    moduleName = "fleet.app"
    projectPath = "/Users/zolotov/dev/intellij"
    kotlinVersion = "1.4.10-release-IJ2020.2-1"
}

task<JavaExec>("run") {
    //todo: extract to a separate task with @Option for targetModule
    dependsOn(jpsCompile)
    //todo: get classpath content from @Output of jpsCompile
     classpath(jpsCompile.classpathOutputFilePath.split(File.pathSeparatorChar))
    //todo: get main class from command line argument (@Option)
    main = "fleet.app.MainKt"
    //todo: not sure how to get this
    jvmArgs("-ea",
            "-XstartOnFirstThread",
            "-Djava.awt.headless=true",
            "-Dfleet.debug.mode=true",
            "-Dfleet.config.path=../config/fleet-frontend")
}

tasks {
    withType<Wrapper> {
        gradleVersion = "7.0"
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }
}
