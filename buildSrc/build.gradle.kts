plugins {
    kotlin("jvm") version "1.4.10"
    `kotlin-dsl`
}

repositories {
    jcenter()
}

gradlePlugin {
    plugins {
        register("jps-plugin") {
            id = "jps-plugin"
            implementationClass = "fleet.bootstrap.JpsPlugin"
        }
    }
}
