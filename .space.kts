/**
 * JetBrains Space Automation
 * This Kotlin-script file lets you automate build activities
 * For more info, see https://www.jetbrains.com/help/space/automation.html
 */

job("Build") {
    gradlew("openjdk:17", "build")
}

job("Publish plugin") {
    startOn {} // disable trigger on push

    gradlew("openjdk:17", "publishPlugins") {
        env["GRADLE_PUBLISH_KEY"] = "{{ project:gradle_plugins_publish_key }}"
        env["GRADLE_PUBLISH_SECRET"] = "{{ project:gradle_plugins_publish_secret }}"
    }
}

job("Publish jps-wrapper") {
    startOn {} // disable trigger on push

    gradlew("openjdk:17", ":jps-wrapper:publish")
}
