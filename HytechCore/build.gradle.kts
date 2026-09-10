/// The library: the logistics framework, the five resource types and the machine engine.
///
/// Nothing in here is a concrete block. A battery, a generator, a crusher and a cable are all
/// content -- they are specialized implementations of a `LogisticContainer` and differ between
/// mods. This project ships the containers, networks, transfer systems and engines they are built
/// from, plus the debug blocks that make a resource type observable in-world.
///
/// Everything shared is configured in the root build script. Only what is specific to this plugin
/// lives here: the manifest values the template expands.

ext["plugin_main_entrypoint"] = "at.rasebdon.hytech.core.HytechCorePlugin"
ext["plugin_description"] = "Logistics framework for Hytale tech mods: networks, pipes and machines"

// See the note in HytechPlugin's build script: this run boots the library on its own, with no
// content mod at all. That is a real test -- the debug pipes and the wrench are enough to exercise
// the framework -- but it is not the one you want when you are working on Hytech.
configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
    runConfigName = "HytechCore only (no content)"
}
