// The client side every mod loader shares: Loom on the client jar, with Fabric Loader only for the Mixin annotations.
// `agent.fabric` adds Fabric API and Fabric Language Kotlin on top.
plugins {
    id("net.fabricmc.fabric-loom")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "minecraft"(libs.findLibrary("minecraft").get())
    "compileOnly"(libs.findLibrary("fabric-loader").get())
}
