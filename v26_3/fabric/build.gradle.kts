plugins {
    id("agent.kotlin")
    id("agent.fabric")
    id("agent.publish")
}

dependencies {
    api(project(":common"))
}
