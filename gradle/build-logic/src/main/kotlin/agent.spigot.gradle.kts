// The 1.8.8 plugin: plain Kotlin JVM against the stock Spigot 1.8.8 server jar (v1_8_R3). The plugin jar bundles the
// library modules with Kotlin, coroutines, fastutil and SLF4J, since 1.8 has no plugin libraries and none of them.
// `./gradlew :v1_8_8-spigot:runServer -PserverJar=<jar>` starts a headless 1.8.8 server with it.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // Only the server jar, which bundles the API: spigot-api names the int health overloads `_INVALID_*`, which the
    // server jar's bridge methods implement under their real names, so Kotlin finds CraftPlayer abstract against it
    "compileOnly"("org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
    "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())
    "implementation"(libs.findLibrary("fastutil").get())
    "implementation"(libs.findLibrary("slf4j-api").get())
    // The library's SLF4J logging goes to the server log through java.util.logging
    "runtimeOnly"("org.slf4j:slf4j-jdk14:${libs.findLibrary("slf4j-api").get().get().version}")
}

val sourceSets = extensions.getByType<SourceSetContainer>()

tasks.named<ProcessResources>("processResources") {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching("plugin.yml") { expand(properties) }
}

val pluginJar = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("pluginJar") {
    description = "The 1.8.8 plugin jar: this module with everything on its runtime classpath, Kotlin included."
    group = "build"
    archiveClassifier = "plugin"
    from(sourceSets["main"].output)
    configurations = project.configurations.named("runtimeClasspath").map { listOf(it) }
    // Servers that ship SLF4J 1.7 would load their API, which never sees the bundled 2.x provider, and log nowhere
    relocate("org.slf4j", "net.ccbluex.agent.v1_8_8.spigot.slf4j")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    // Signatures and module descriptors of the bundled jars do not hold for the merged jar
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class",
        "module-info.class")
}

tasks.named("assemble") {
    dependsOn(pluginJar)
}

fun javaLauncher() = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.findVersion("jdk").get().requiredVersion.toInt())
}

/**
 * A headless 1.8.8 server in build/<directory> with [plugins] (plugin jar tasks), from the jar named by
 * -PserverJar, which is not published anywhere. A [fresh] server generates its worlds anew.
 */
fun registerServer(name: String, directory: String, description: String, plugins: List<TaskProvider<out Jar>>, fresh: Boolean) =
    tasks.register<Exec>(name) {
        this.description = description
        group = "run"
        val serverJar = providers.gradleProperty("serverJar")
        val runDir = layout.buildDirectory.dir(directory)
        val jars = plugins.map { task -> task.flatMap { it.archiveFile } }
        jars.forEach { inputs.file(it) }
        timeout = java.time.Duration.ofMinutes(10)
        standardInput = System.`in`
        doFirst {
            val dir = runDir.get().asFile
            dir.resolve("plugins").mkdirs()
            if (fresh) dir.listFiles { file -> file.name.startsWith("world") }?.forEach { it.deleteRecursively() }
            jars.forEach { jar -> jar.get().asFile.let { it.copyTo(dir.resolve("plugins/${it.name}"), overwrite = true) } }
            dir.resolve("eula.txt").writeText("eula=true\n")
            // The bundled 1.8.8 generator of some forks calls Guava's removed Objects.firstNonNull; flat worlds avoid it
            val properties = dir.resolve("server.properties")
            if (!properties.exists()) {
                properties.writeText(
                    "level-type=FLAT\nonline-mode=false\nserver-port=25599\nspawn-protection=0\n" +
                        "spawn-monsters=false\nspawn-animals=false\n",
                )
            }
            workingDir = dir
            commandLine(
                javaLauncher().get().executablePath.asFile.absolutePath,
                "-Xmx1g", "-Dcom.mojang.eula.agree=true", "-Djava.awt.headless=true", "-Dterminal.jline=false",
                "-jar", file(serverJar.get()).absolutePath, "nogui",
            )
        }
    }

registerServer("runServer", "run", "Runs a headless 1.8.8 server with the plugin jar.", listOf(pluginJar), false)

// The smoke plugin: a scripted run on a 1.8.8 server that records bot ticks, walks a course and fights a dummy
val smoke = sourceSets.create("smoke") {
    compileClasspath += sourceSets["main"].output + configurations.getByName("compileClasspath")
}

tasks.named<ProcessResources>("processSmokeResources") {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching("plugin.yml") { expand(properties) }
}

val smokeJar = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("smokeJar") {
    description = "The smoke plugin jar, which runs next to the plugin jar."
    group = "build"
    archiveClassifier = "smoke"
    from(smoke.output)
    configurations = provider { emptyList() }
    relocate("org.slf4j", "net.ccbluex.agent.v1_8_8.spigot.slf4j")
}

registerServer(
    "runSmoke", "smoke", "Runs the smoke plugin on a headless 1.8.8 server, which stops once it is done.",
    listOf(pluginJar, smokeJar), true,
)
