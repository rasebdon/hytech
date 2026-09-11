/// The library project: framework, five resource types, machine engine -- no concrete blocks.
/// Shared build config lives in the root script; only this plugin's manifest values live here.

ext["plugin_main_entrypoint"] = "at.rasebdon.hytech.core.HytechCorePlugin"
ext["plugin_description"] = "Logistics framework for Hytale tech mods: networks, pipes and machines"

// Boots the library alone, with no content mod -- useful for testing the framework itself, not
// for working on Hytech.
configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
    runConfigName = "HytechCore only (no content)"
}
