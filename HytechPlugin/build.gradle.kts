/// Hytech itself: the content built on HytechCore.
///
/// Batteries, tanks, generators, the burner, the crusher, the smelter, twelve metals and the Tech
/// Bench. The framework, the five resource types and the machine engine all come from the library.

ext["plugin_main_entrypoint"] = "at.rasebdon.hytech.HytechPlugin"
ext["plugin_description"] = "An energy automation plugin for Hytale"

dependencies {
    // `compileOnly`, like the Hytale API itself: the library is provided by the server at runtime,
    // declared in this plugin's manifest `Dependencies` rather than bundled.
    compileOnly(project(":HytechCore"))
}

// The IDEA run configuration hytale-mod generates. Both projects get one, and the library's boots
// the library *alone* -- which looks exactly like Hytech being broken: the wrench and the debug
// pipes are there and not one battery, pipe or machine. Naming them for what they do is cheaper
// than explaining it twice.
configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
    runConfigName = "Hytech (library + content)"
}

// A dev run has to hand the server the library's *directories*, not its jar.
//
// `PluginManager` discovers a classpath plugin by `getResources("manifest.json")` and takes the
// URL that manifest came from as the plugin's classloader URL. From a jar that is a
// `JarURLConnection`, so the plugin gets a child-first `PluginClassLoader` over the whole jar and
// loads its own private copy of every class in it -- and then `EnergyModule.INSTANCE`, set during
// the library's own setup, is invisible here: this plugin's classes come from the app classloader
// and see a second, uninitialised copy. That is exactly the "Not initialized" crash.
//
// From a directory the URL is the resources folder, which holds no classes, so child-first finds
// nothing there and every class resolves from the one app classloader -- the single copy the two
// plugins shared back when they were one jar.
//
// Real jars in `run/mods/` do not have this problem: the library's classes are then in nobody
// else's URLs, so this plugin's loader falls through to `PluginBridgeClassLoader`, which resolves
// them from the plugin it declares as a dependency. Dev is the odd case, not production.
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
