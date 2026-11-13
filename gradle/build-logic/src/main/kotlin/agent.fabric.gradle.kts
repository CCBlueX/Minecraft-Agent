import java.time.Duration

// The Fabric mod of the client: `agent.client` with Fabric API and Fabric Language Kotlin. Mods using the
// library bring both, so they stay out of the published POM. `./gradlew runClientGameTest` runs src/gametest.
plugins {
    id("agent.client")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    for (library in listOf("fabric-loader", "fabric-api", "fabric-kotlin")) {
        "compileOnly"(libs.findLibrary(library).get())
        "localRuntime"(libs.findLibrary(library).get())
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "minecraft-agent-gametest"
        enableGameTests = false
    }
}

dependencies {
    "gametestImplementation"(libs.findLibrary("gametest-harness").get())
}

loom.runs.named("clientGameTest") {
    jvmArguments.add("-Xmx2g")
    // A loader error would otherwise wait on a dialog nobody sees
    systemProperties.put("fabric.noGui", "true")
    // Xvfb has no sRGB GLX visual, so SDL has to take EGL
    environmentVars.put("SDL_VIDEO_FORCE_EGL", "1")
    // -PgameTestOnly=WalkSneak,WalkSwim runs only those classes
    providers.gradleProperty("gameTestOnly").orNull?.let {
        systemProperties.put("gametest.only", it)
    }
}

tasks.named("runClientGameTest") {
    // -PgameTestTimeoutMinutes for local runs of the whole suite; CI keeps 10
    timeout = Duration.ofMinutes(providers.gradleProperty("gameTestTimeoutMinutes").map(String::toLong).getOrElse(10))
}
