pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kmp-terminal"
include(":terminal-core")
include(":terminal-ui")
include(":agy-android-ui")
include(":demo-web")
include(":demo-jvm")
