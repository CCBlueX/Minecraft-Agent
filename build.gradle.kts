plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.commonmark:commonmark:0.32.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.32.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.32.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.32.0")
    implementation("org.commonmark:commonmark-ext-yaml-front-matter:0.32.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "net.ccbluex.agent.site.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}
