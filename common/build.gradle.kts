plugins {
    id("agent.kotlin")
    id("agent.shared")
    id("agent.publish")
    `java-test-fixtures`
}

kotlin {
    compilerOptions.optIn.add("net.ccbluex.agent.ExperimentalAgentApi")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(kotlin("test"))
}

// Loom puts Minecraft on main and test only; the shared test world needs it as well.
configurations.named("testFixturesCompileClasspath") {
    extendsFrom(configurations.getByName("compileClasspath"))
}
configurations.named("testFixturesRuntimeClasspath") {
    extendsFrom(configurations.getByName("runtimeClasspath"))
}

// The test world is for tests only; it never goes into the published module.
components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("testFixturesApiElements")) { skip() }
    withVariantsFromConfiguration(configurations.getByName("testFixturesRuntimeElements")) { skip() }
}
