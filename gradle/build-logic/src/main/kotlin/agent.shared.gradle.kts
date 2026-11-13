import net.ccbluex.agent.build.ClientClassCheck

// Simulation, pathfinder, navigation and skills: compiled against the vanilla server jar in Mojang's names, so
// the same classes link on the Fabric client and on Paper.
plugins {
    id("net.fabricmc.fabric-loom")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

loom {
    serverOnlyMinecraftJar()
}

dependencies {
    "minecraft"(libs.findLibrary("minecraft").get())
}

val checkClientClasses = tasks.register<ClientClassCheck>("checkClientClasses") {
    description = "Fails when the sources or classes reference client-only Minecraft packages."
    sources.from(fileTree("src") { include("**/*.kt", "**/*.java") })
    classes.from(layout.buildDirectory.dir("classes"))
    mustRunAfter(tasks.matching { it.name.startsWith("compile") })
}

tasks.named("check") {
    dependsOn(checkClientClasses)
}
