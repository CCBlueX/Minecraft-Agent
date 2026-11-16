plugins {
    id("agent.kotlin")
    id("agent.neutral")
    id("agent.publish")
}

description = "Player movement simulation, route searches and route following, and skills: fighting, eating, looting, gear, shopping, void rescue, and game modes, on the game types of the world package"

kotlin {
    compilerOptions.optIn.add("net.ccbluex.agent.ExperimentalAgentApi")
}

dependencies {
    // The game ships both; the 1.8 plugin bundles them.
    compileOnlyApi(libs.fastutil)
    compileOnlyApi(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.fastutil)
    testImplementation(testFixtures(project(":v26_3-adapter")))
}
