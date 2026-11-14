// The published library modules. They use each other's @InternalAgentApi.
plugins {
    id("agent.kotlin")
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

kotlin {
    compilerOptions.optIn.add("net.ccbluex.agent.InternalAgentApi")
}

// Modules of one game version live in v<version>/ (v26_3/fabric) and name it in their version: 1.0.0+26.3
val gameVersion = Regex("v(\\d+(?:_\\d+)+)").matchEntire(projectDir.parentFile.name)?.groupValues?.get(1)
    ?.replace('_', '.')
val snapshot = version.toString().endsWith("-SNAPSHOT")
if (gameVersion != null) {
    version = version.toString().removeSuffix("-SNAPSHOT") + "+$gameVersion" + if (snapshot) "-SNAPSHOT" else ""
}

base {
    archivesName = "minecraft-agent-${projectDir.name}"
}

java {
    withSourcesJar()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

val mavenUrl = providers.gradleProperty("mavenUrl")

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)
            artifactId = "minecraft-agent-${projectDir.name}"
            pom {
                name = "Minecraft Agent ${projectDir.name}"
                description = provider { project.description }
                url = "https://github.com/CCBlueX/Minecraft-Agent"
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "GNU General Public License v3.0 or later"
                        url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
                organization {
                    name = "CCBlueX"
                    url = "https://ccbluex.net"
                }
                developers {
                    developer {
                        id = "ccbluex"
                        name = "CCBlueX"
                        url = "https://github.com/CCBlueX"
                    }
                }
                scm {
                    url = "https://github.com/CCBlueX/Minecraft-Agent"
                    connection = "scm:git:https://github.com/CCBlueX/Minecraft-Agent.git"
                    developerConnection = "scm:git:git@github.com:CCBlueX/Minecraft-Agent.git"
                }
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/CCBlueX/Minecraft-Agent/issues"
                }
            }
        }
    }
    // `publish` uploads to -PmavenUrl with -PmavenUsername and -PmavenPassword (or ORG_GRADLE_PROJECT_mavenUrl, ...)
    repositories {
        if (mavenUrl.isPresent) {
            maven {
                name = "maven"
                url = uri(mavenUrl.get())
                credentials(PasswordCredentials::class)
            }
        }
    }
}

tasks.named("publish") {
    doFirst {
        check(mavenUrl.isPresent) { "publish needs the repository: -PmavenUrl=<url> -PmavenUsername=... -PmavenPassword=..." }
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_minecraft-agent" }
    }
}
