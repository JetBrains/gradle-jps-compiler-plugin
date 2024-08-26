[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# Gradle JPS Compiler Plugin

> [!CAUTION]
Support removal notice:
> External support for `com.jetbrains.intellij.jps-compiler-plugin` Gradle plugin has been dropped by the team building it.
>
> JetBrains employees, see [here](https://code.jetbrains.team/p/fleet/repositories/ultimate/files/fleet/build/fleet-jps/build-jps-compile-plugin/build.gradle.kts).
>
> JPS wrapper will soon also be moved and that repository archived.

The plugin for Gradle that is able to build JPS-based project.

The plugin doesn't interpret JPS project model and doesn't convert it to gradle. Basically it's an interface for
JPS-standalone tool.
