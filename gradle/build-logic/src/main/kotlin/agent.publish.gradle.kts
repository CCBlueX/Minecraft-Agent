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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)
            artifactId = "minecraft-agent-${projectDir.name}"
            pom {
                name = "Minecraft Agent ${projectDir.name}"
                url = "https://github.com/CCBlueX/Minecraft-Agent"
                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
                scm {
                    url = "https://github.com/CCBlueX/Minecraft-Agent"
                }
            }
        }
    }
    repositories {
        maven {
            name = "CCBlueX"
            url = uri("https://maven.ccbluex.net/" + if (snapshot) "snapshots" else "releases")
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").orNull
                password = providers.environmentVariable("MAVEN_PASSWORD").orNull
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_minecraft-agent" }
    }
}
