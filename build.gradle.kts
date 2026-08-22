plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
            exclude { element ->
                val path = element.file.path
                path.contains("generated") || path.contains("/build/") || path.contains("\\build\\")
            }
        }
    }

    tasks.matching { it.name.startsWith("runKtlint") || it.name.startsWith("ktlint") }.configureEach {
        if (this is org.gradle.api.tasks.SourceTask) {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}
