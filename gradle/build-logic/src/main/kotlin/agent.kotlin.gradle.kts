import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import net.ccbluex.agent.build.LicenseHeaderCheck

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jdk = libs.findVersion("jdk").get().requiredVersion.toInt()

// Here rather than in settings: Loom and paperweight add repositories to the project, which makes Gradle ignore
// the ones from settings
repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.ccbluex.net/releases")
    maven("https://maven.ccbluex.net/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(jdk)

tasks.withType<JavaCompile>().configureEach {
    options.release = jdk
}

kotlin {
    jvmToolchain(jdk)
    explicitApi()
    coreLibrariesVersion = libs.findVersion("kotlin").get().requiredVersion
    // Java implementors get real default methods; the 1.0 API needs no DefaultImpls bridges
    compilerOptions.jvmDefault = JvmDefaultMode.NO_COMPATIBILITY

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters.exclude.annotatedWith.add("net.ccbluex.agent.InternalAgentApi")
    }
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(kotlin("test"))
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "1g"
    // Minecraft's bootstrap writes logs/ into the working directory
    val runDirectory = layout.buildDirectory.dir("test-run")
    workingDir(runDirectory)
    doFirst { runDirectory.get().asFile.mkdirs() }
}

val checkLicenseHeaders = tasks.register<LicenseHeaderCheck>("checkLicenseHeaders") {
    description = "Fails when a source file does not start with the Minecraft Agent license header."
    sources.from(fileTree("src") { include("**/*.kt", "**/*.java") })
}

tasks.named("check") {
    @OptIn(ExperimentalAbiValidation::class)
    dependsOn(kotlin.abiValidation.checkTaskProvider, checkLicenseHeaders)
}

// Lets `updateKotlinAbi check` run in one invocation.
@OptIn(ExperimentalAbiValidation::class)
kotlin.abiValidation.checkTaskProvider.configure {
    mustRunAfter(tasks.named("updateKotlinAbi"))
}

// Only the published library modules keep an ABI dump
afterEvaluate {
    if (!pluginManager.hasPlugin("agent.publish")) {
        tasks.named("checkKotlinAbi") { enabled = false }
        tasks.named("updateKotlinAbi") { enabled = false }
    }
}
