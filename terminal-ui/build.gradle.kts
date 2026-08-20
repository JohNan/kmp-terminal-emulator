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
            baseName = "TerminalUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":terminal-core"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(libs.junit)
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

publishing {
    publications.withType<MavenPublication> {
        val javadocTask = tasks.register("${this@withType.name}JavadocJar", Jar::class) {
            archiveClassifier.set("javadoc")
            archiveBaseName.set("${project.name}-${this@withType.name}")
        }
        artifact(javadocTask)

        pom {
            name.set("kmp-terminal-ui")
            description.set("Compose Multiplatform Terminal Rendering Canvas and UI components")
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

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}
