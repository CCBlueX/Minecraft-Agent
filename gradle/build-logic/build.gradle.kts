plugins {
    `kotlin-dsl`
}

// The plugins the convention plugins apply, by their marker artifacts
fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.jvm))
    implementation(plugin(libs.plugins.dokka.base))
    implementation(plugin(libs.plugins.dokka.javadoc))
    implementation(plugin(libs.plugins.fabric.loom))
    implementation(plugin(libs.plugins.paperweight.userdev))
    implementation(plugin(libs.plugins.run.paper))
    implementation(plugin(libs.plugins.shadow))
}
