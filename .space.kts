/**
 * JetBrains Space Automation
 * This Kotlin-script file lets you automate build activities
 * For more info, see https://www.jetbrains.com/help/space/automation.html
 */

job("Build") {
    gradlew("openjdk:11", "build")
}

job("Publish jps-wrapper") {
    startOn {} // disable trigger on push

    gradlew("openjdk:11", ":jps-wrapper:publish", "--info")
}
