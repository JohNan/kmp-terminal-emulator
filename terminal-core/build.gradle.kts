plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
    signing
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
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "TerminalCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(compose.runtime)
            implementation(compose.ui)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.junit)
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

val signingKey = providers.environmentVariable("GPG_SIGNING_KEY").orNull
    ?: providers.gradleProperty("signingInMemoryKey").orNull
val signingPassword = providers.environmentVariable("GPG_PASSPHRASE").orNull
    ?: providers.gradleProperty("signingInMemoryKeyPassword").orNull

if (!signingKey.isNullOrBlank()) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword ?: "")
        sign(publishing.publications)
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)
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
            name = "LocalStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repo"))
        }
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
