plugins {
    id("agent.kotlin")
    id("agent.shared")
    id("agent.publish")
    `java-test-fixtures`
}

description = "The library on Minecraft 26.3: the game's blocks, entities and items as the agents see them"

dependencies {
    api(project(":common"))
    testFixturesImplementation(kotlin("test"))
}

// Loom puts Minecraft on main and test only, from a repository only this project has; the shared test world and the
// version-neutral tests using it get the files.
fun external(classpath: String) = files(
    configurations.getByName(classpath).incoming.artifactView {
        componentFilter { it !is ProjectComponentIdentifier }
    }.files,
)

dependencies {
    testFixturesApi(external("compileClasspath"))
    testFixturesRuntimeOnly(external("runtimeClasspath"))
}

// The test world is for tests only; it never goes into the published module.
components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("testFixturesApiElements")) { skip() }
    withVariantsFromConfiguration(configurations.getByName("testFixturesRuntimeElements")) { skip() }
}
