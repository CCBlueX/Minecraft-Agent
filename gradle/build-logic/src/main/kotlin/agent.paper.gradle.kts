// The Paper plugin: compiled with paperweight against Paper in Mojang's names. The plugin jar bundles the
// library modules; Paper downloads the Kotlin libraries listed in plugin.yml. `./gradlew runServer` starts a
// headless dev server with it.
plugins {
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(name: String) = libs.findVersion(name).get().requiredVersion

dependencies {
    paperweight.paperDevBundle(version("paper"))
}

val sourceSets = extensions.getByType<SourceSetContainer>()

// Coordinates Paper's library loader fetches from Maven Central instead of the plugin jar bundling them
val pluginLibraries = listOf(
    "org.jetbrains.kotlin:kotlin-stdlib:${version("kotlin")}",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${version("kotlinx-coroutines")}",
)

tasks.named<ProcessResources>("processResources") {
    val properties = mapOf(
        "version" to project.version.toString(),
        "libraries" to pluginLibraries.joinToString(", ", "[", "]") { "\"$it\"" },
    )
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

val pluginJar = tasks.register<Jar>("pluginJar") {
    description = "The plugin jar: this module with the library modules it needs."
    group = "build"
    archiveClassifier = "plugin"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets["main"].output)
    val libraryModules = configurations.getByName("runtimeClasspath").incoming.artifactView {
        componentFilter { it is ProjectComponentIdentifier }
    }.files
    dependsOn(libraryModules)
    from(libraryModules.elements.map { modules -> modules.map { zipTree(it.asFile) } })
}

tasks.named("assemble") {
    dependsOn(pluginJar)
}

// Only the plugin jar; the plain jar is the library artifact
runPaper {
    disablePluginJarDetection()
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion(version("minecraft"))
    pluginJars.from(pluginJar.flatMap { it.archiveFile })
    jvmArgs("-Xmx2g", "-Dcom.mojang.eula.agree=true")
    args("--nogui")
}
