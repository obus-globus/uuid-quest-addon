pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "KikuGie Releases"
            url = uri("https://maven.kikugie.dev/releases")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10"
}

rootProject.name = "uuid-quest"

include("core", "liquidbounce")

// The standalone Fabric mod: one node per range of Minecraft versions sharing the same API.
// Each node's supported range is mod.mc_range in fabric/versions/<node>/gradle.properties.
stonecutter {
    create("fabric") {
        versions("1.20.1", "1.20.3", "1.21.5", "1.21.9", "26.1", "26.3")
        vcsVersion = "26.3"
    }
}
