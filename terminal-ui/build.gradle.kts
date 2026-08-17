plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

group = "com.johnan.terminal.emulator"
version = providers.gradleProperty("version").orNull.takeIf { !it.isNullOrBlank() && it != "unspecified" } ?: "0.1.0"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    sourceSets {
        commonMain.dependencies {
            api(project(":terminal-core"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Compose Multiplatform UI, foundation, & components
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.johnan.terminal.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_21
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_21
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/JohNan/kmp-terminal-emulator")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
