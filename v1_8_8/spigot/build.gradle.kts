plugins {
    id("agent.kotlin")
    id("agent.spigot")
    id("agent.publish")
}

description = "Server bots as a Spigot 1.8.8 plugin"

kotlin {
    compilerOptions.optIn.add("net.ccbluex.agent.ExperimentalAgentApi")
}

dependencies {
    api(project(":common"))
}

// The library artifact is bundled into other plugins, whose own plugin.yml would clash with this one
tasks.jar {
    exclude("plugin.yml")
}

publishing.publications.named<MavenPublication>("maven") {
    artifact(tasks.named("pluginJar"))
}

// The 1.8.8 part of the Bukkit layer in common: it may use its internals
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    friendPaths.from(project(":common").layout.buildDirectory.dir("classes/kotlin/main"))
}
