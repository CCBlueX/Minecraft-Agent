plugins {
    id("agent.kotlin")
    id("agent.fabric")
    id("agent.publish")
}

description = "The local player as a body, for Fabric mods"

dependencies {
    api(project(":common"))
    api(project(":v26_3-adapter"))
    // Optional: read only while Litematica is installed
    compileOnly(libs.litematica)
    compileOnly(libs.malilib)
    "gametestCompileOnly"(libs.litematica)
    "gametestCompileOnly"(libs.malilib)
    // -PgameTestLitematica runs the game tests with Litematica installed
    if (providers.gradleProperty("gameTestLitematica").isPresent) {
        "gametestRuntimeOnly"(libs.litematica)
        "gametestRuntimeOnly"(libs.malilib)
    }
}
