plugins {
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
