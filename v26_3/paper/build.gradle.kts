plugins {
    id("agent.kotlin")
    id("agent.paper")
    id("agent.publish")
}

description = "Server bots as a Paper plugin"

kotlin {
    compilerOptions.optIn.add("net.ccbluex.agent.ExperimentalAgentApi")
}

dependencies {
    api(project(":common"))
}

// The plugin jar goes out too (classifier `plugin`), so dev servers elsewhere can run MinecraftAgent
publishing.publications.named<MavenPublication>("maven") {
    artifact(tasks.named("pluginJar"))
}

// The Paper part of one module: it may use the internals of common
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    friendPaths.from(project(":common").layout.buildDirectory.dir("classes/kotlin/main"))
}
