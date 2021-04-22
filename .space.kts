/**
 * JetBrains Space Automation
 * This Kotlin-script file lets you automate build activities
 * For more info, see https://www.jetbrains.com/help/space/automation.html
 */

job("Build") {
    gradlew("openjdk:11", "build")
}

job("Publish plugin") {
    gradlew("openjdk:11", "publishPlugins") {
        env["GRADLE_PUBLISH_KEY"] = Secrets("gradle_plugins_publish_key")
        env["GRADLE_PUBLISH_SECRET"] = Secrets("gradle_plugins_publish_secret")
    }
    startOn {
        gitPush { enabled = false }
    }
}

job("Publish jps-wrapper") {
    gradlew("openjdk:11", "publish") {
        workDir = "jps-wrapper"
    }
    startOn {
        gitPush { enabled = false }
    }
}
