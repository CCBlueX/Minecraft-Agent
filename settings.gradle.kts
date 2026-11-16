pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "minecraft-agent"

include("common", "v26_3-adapter", "v26_3-fabric", "v26_3-paper", "v1_8_8-spigot")
project(":v26_3-adapter").projectDir = file("v26_3/adapter")
project(":v26_3-fabric").projectDir = file("v26_3/fabric")
project(":v26_3-paper").projectDir = file("v26_3/paper")
project(":v1_8_8-spigot").projectDir = file("v1_8_8/spigot")
