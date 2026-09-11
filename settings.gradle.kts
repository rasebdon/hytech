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

// Two plugins: HytechCore (framework) and HytechPlugin (content).
// Directory names are load-bearing: processResources maps them to the manifest's pack name, and
// AssetModule.registerPack shuts the server down on a duplicate.
include("HytechCore", "HytechPlugin")
