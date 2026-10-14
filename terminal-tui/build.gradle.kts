plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
    signing
}

group = "io.github.johnan"
version = providers.gradleProperty("version").orNull.takeIf { !it.isNullOrBlank() && it != "unspecified" } ?: "0.1.0"

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    macosArm64()
    macosX64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":terminal-core"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
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
            name.set("kmp-terminal-tui")
            description.set("Terminal User Interface (TUI) differential renderer and raw input engine")
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
