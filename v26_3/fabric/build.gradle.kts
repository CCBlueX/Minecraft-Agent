plugins {
    id("agent.kotlin")
    id("agent.fabric")
    id("agent.publish")
}

description = "The local player as a body, for Fabric mods"

dependencies {
    api(project(":common"))
}
