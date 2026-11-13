pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "minecraft-agent"

include("common", "v26_3-fabric")
project(":v26_3-fabric").projectDir = file("v26_3/fabric")
