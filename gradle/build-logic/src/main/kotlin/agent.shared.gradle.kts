import net.ccbluex.agent.build.PackageCheck

// Vanilla: compiled against the vanilla server jar in Mojang's names, so the same classes link on the Fabric client
// and on Paper.
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

val checkClientClasses = tasks.register<PackageCheck>("checkClientClasses") {
    description = "Client-only classes in a shared module:"
    sources.from(fileTree("src") { include("**/*.kt", "**/*.java") })
    classes.from(layout.buildDirectory.dir("classes"))
    packages = listOf("net.minecraft.client", "com.mojang.blaze3d")
    mustRunAfter(tasks.matching { it.name.startsWith("compile") })
}

tasks.named("check") {
    dependsOn(checkClientClasses)
}
