plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

group = "io.github.johnan"
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // Compose Multiplatform (needed for graphics/Color in TerminalCell)
            implementation(compose.runtime)
            implementation(compose.ui)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.johnan.terminal.core"
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
    publications.withType<MavenPublication> {
        pom {
            name.set("kmp-terminal-core")
            description.set("Pure Kotlin Multiplatform Terminal Emulator Core (Decoupled, zero UI dependencies)")
            url.set("https://github.com/JohNan/kmp-terminal-emulator")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("JohNan")
                    name.set("Johan")
                    url.set("https://github.com/JohNan")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/JohNan/kmp-terminal-emulator.git")
                developerConnection.set("scm:git:ssh://github.com:JohNan/kmp-terminal-emulator.git")
                url.set("https://github.com/JohNan/kmp-terminal-emulator")
            }
        }
    }
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
