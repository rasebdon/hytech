/// Hytech: the content built on HytechCore. Framework, resource types and machine engine come
/// from the library.

ext["plugin_main_entrypoint"] = "at.rasebdon.hytech.HytechPlugin"
ext["plugin_description"] = "An energy automation plugin for Hytale"

dependencies {
    // compileOnly: the library is provided at runtime via the manifest `Dependencies`, not bundled.
    compileOnly(project(":HytechCore"))
}

// Named for what it does: the library's own run config boots alone and looks like Hytech is
// broken if you don't know that.
configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
    runConfigName = "Hytech (library + content)"
}

// runServer must get the library's class/resource directories, not its jar: from a jar this
// plugin's classes would get a second, uninitialised copy on a child-first PluginClassLoader
// (see root CLAUDE.md "Dev run needs directories, not a jar").
afterEvaluate {
    tasks.named<JavaExec>("runServer") {
        val core = project(":HytechCore")
        dependsOn("${core.path}:classes")

        classpath += files(
            core.layout.buildDirectory.dir("classes/java/main"),
            core.layout.buildDirectory.dir("resources/main")
        )

        // The server loads both packs, so a run has to sync both source trees back.
        finalizedBy("${core.path}:syncAssets")
    }
}
