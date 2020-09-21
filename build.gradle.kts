import fleet.bootstrap.JpsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version "1.4.10"
    id("jps-plugin")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

jdkTable {
    jdk("corretto-11", project.ext["jps.jdk"]!!.toString())
}

val jpsCompile = task<JpsCompile>("jpsCompile") {
    incremental = true
    moduleName = "fleet.app"
    projectPath = "/Users/Ilia.Shulgin/Documents/projects/intellij"
    classpathOutputFilePath = "kek.txt"
    kotlinVersion = "1.4.10-release-IJ2020.2-1"
    jpsWrapperPath = "buildSrc/jps-wrapper/build/libs/jps-wrapper-all.jar"
}

task<JavaExec>("run") {
    //todo: extract to a separate task with @Option for targetModule
    dependsOn(jpsCompile)
    //todo: get classpath content from @Output of jpsCompile
//    classpath(File(System.getProperties().getProperty("build.classpathOut")).readText().split(File.pathSeparatorChar))
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
        gradleVersion = "6.5.1"
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }
}
