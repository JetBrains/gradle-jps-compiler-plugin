import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version "1.3.70"
    id("jps-plugin")
}

val jpsDepsDir = "$buildDir/jps"
val kotlinHome = "$buildDir/kotlin/Kotlin"

dependencies {
    implementation("org.apache.maven:maven-embedder:3.6.0")
    implementation(fileTree("dir" to jpsDepsDir, "include" to listOf("*.jar")))

    implementation(fileTree("dir" to "$kotlinHome/lib",
            "include" to listOf("jps/kotlin-jps-plugin.jar", "kotlin-plugin.jar", "kotlin-reflect.jar")))
    implementation(fileTree("dir" to "$kotlinHome/kotlinc/lib",
            "include" to listOf("kotlin-stdlib.jar")))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn(tasks.getByName("setupKotlinPlugin"), tasks.getByName("setupJpsDeps"))
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val jpsCompile = task<JavaExec>("jpsCompile") {
    //todo: extract to a separate task with @Option for targetModule and @Output for classpath result
    dependsOn(tasks.getByName("build"))
    classpath = sourceSets["main"].runtimeClasspath
    main = "fleet.bootstrap.MainKt"
    val systemProps = System.getProperties()
            .filterKeys { it is String && it.startsWith("build.") }
            .map { it.key as String to it.value }
            .toMap().toMutableMap()
    systemProps.putIfAbsent("build.kotlinHome", kotlinHome)
    systemProperties = systemProps
}

task<JavaExec>("run") {
    //todo: extract to a separate task with @Option for targetModule
    dependsOn(jpsCompile)
    //todo: get classpath content from @Output of jpsCompile
    classpath(File(System.getProperties().getProperty("build.classpathOut")).readText().split(File.pathSeparatorChar))
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
