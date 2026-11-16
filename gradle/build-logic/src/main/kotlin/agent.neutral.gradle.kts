import net.ccbluex.agent.build.PackageCheck

// World, simulation, pathfinder, navigation, skills and the Bukkit layer: no game classes, so the same jar runs on
// every game version an adapter (26.3, 1.8.8) supports.
val checkGameClasses = tasks.register<PackageCheck>("checkGameClasses") {
    description = "Game or server internals in a version-neutral module:"
    sources.from(fileTree("src/main") { include("**/*.kt", "**/*.java") })
    classes.from(layout.buildDirectory.dir("classes/kotlin/main"), layout.buildDirectory.dir("classes/java/main"))
    packages = listOf("net.minecraft", "com.mojang", "org.bukkit.craftbukkit")
    mustRunAfter(tasks.matching { it.name.startsWith("compile") })
}

tasks.named("check") {
    dependsOn(checkGameClasses)
}
