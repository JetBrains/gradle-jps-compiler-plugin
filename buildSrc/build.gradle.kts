plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("jps-plugin") {
            id = "jps-plugin"
            implementationClass = "fleet.bootstrap.JpsPlugin"
        }
    }
}

repositories {
    jcenter()
}