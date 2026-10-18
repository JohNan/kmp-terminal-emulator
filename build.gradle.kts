buildscript {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "com.google.protobuf" -> if (
                    requested.name == "protobuf-java" ||
                    requested.name == "protobuf-java-util" ||
                    requested.name == "protobuf-kotlin"
                ) {
                    useVersion("3.25.8")
                    because("Patch transitive AGP buildscript protobuf vulnerabilities")
                }
                "org.jdom" -> if (requested.name == "jdom2") {
                    useVersion("2.0.6.1")
                    because("Patch CVE-2021-33813 in transitive AGP buildscript JDOM2")
                }
                "org.bitbucket.b_c" -> if (requested.name == "jose4j") {
                    useVersion("0.9.6")
                    because("Patch transitive AGP buildscript jose4j")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
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
