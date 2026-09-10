pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.hytale-modding.info/releases") {
            name = "HytaleModdingReleases"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Hytech"

// Two plugins, not one jar. HytechCore is the library every tech mod builds on -- the logistics
// framework, the five resource types and the machine engine. HytechPlugin is content: the blocks,
// machines and materials that use them.
//
// The directory names are load bearing. `processResources` maps the manifest's `Name` to
// `project.name`, an asset pack is identified by `Group:Name`, and `AssetModule.registerPack`
// shuts the server down on a duplicate pack name.
include("HytechCore", "HytechPlugin")
