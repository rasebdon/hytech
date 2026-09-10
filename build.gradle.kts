plugins {
    // Declared here so both plugins land on the buildscript classpath -- `apply false` keeps them
    // off the root project, which owns no sources of its own. Each subproject applies them below.
    id("hytale-mod") version "0.+" apply false
    id("com.diffplug.spotless") version "7.0.3" apply false

    // Applied to the root on purpose. hytale-mod writes its IDEA run configuration through the
    // *root* project's `idea` extension and its idea-ext `ProjectSettings`, but it applies those
    // two plugins to the project it is applied to -- which is now a subproject. Without them here
    // it fails with "Extension with name 'idea' does not exist", then with "Extension of type
    // 'ProjectSettings' does not exist". Version pinned to what hytale-mod 0.8.1 depends on.
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
}

val javaVersion = 25

val appData = System.getenv("APPDATA") ?: (System.getenv("HOME") + "/.var/app/com.hypixel.HytaleLauncher/data")
val hytaleAssets = file("$appData/Hytale/install/release/package/game/latest/Assets.zip")

// Resolved here rather than inside `subprojects`: the generated `libs` accessor reads the version
// catalog off the project it is evaluated against, and a subproject has no such extension.
val jetbrainsAnnotations = libs.jetbrains.annotations
val jspecify = libs.jspecify

// ---------------------------------------------------------------------------------------------
// Shared mod configuration
// ---------------------------------------------------------------------------------------------

/// Every subproject is a Hytale plugin, so they are configured identically apart from their
/// manifest entrypoint. What differs per project lives in the project's own build script.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "pmd")
    apply(plugin = "idea")
    apply(plugin = "hytale-mod")
    apply(plugin = "com.diffplug.spotless")

    group = "at.rasebdon"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://maven.hytale-modding.info/releases") {
            name = "HytaleModdingReleases"
        }
    }

    dependencies {
        "compileOnly"(jetbrainsAnnotations)
        "compileOnly"(jspecify)

        if (hytaleAssets.exists()) {
            "compileOnly"(files(hytaleAssets))
        } else {
            logger.warn("Hytale Assets.zip not found at: ${hytaleAssets.absolutePath}")
        }
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }

        withSourcesJar()
    }

    // The manifest is a template, not generated: `Dependencies` and `Main` differ per plugin and
    // live in each project's own manifest.json, while the shared metadata comes from
    // gradle.properties. `plugin_name` is the project name, which is half of the pack identity.
    tasks.named<ProcessResources>("processResources") {
        val replaceProperties = mapOf(
            "plugin_group" to findProperty("plugin_group"),
            "plugin_maven_group" to project.group,
            "plugin_name" to project.name,
            "plugin_version" to project.version,
            "server_version" to findProperty("server_version"),

            "plugin_description" to findProperty("plugin_description"),
            "plugin_website" to findProperty("plugin_website"),

            "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
            "plugin_author" to findProperty("plugin_author")
        )

        filesMatching("manifest.json") {
            expand(replaceProperties)
        }

        inputs.properties(replaceProperties)
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes["Specification-Title"] = rootProject.name
            attributes["Specification-Version"] = version
            attributes["Implementation-Title"] = project.name
            attributes["Implementation-Version"] =
                providers.environmentVariable("COMMIT_SHA_SHORT")
                    .map { "${version}-${it}" }
                    .getOrElse(version.toString())
        }
    }

    configure<PublishingExtension> {
        repositories {
            // This is where you put repositories that you want to publish to.
            // Do NOT put repositories for your dependencies here.
        }

        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }

    // IDEA no longer downloads sources/javadoc jars for dependencies on its own.
    configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
        module {
            isDownloadSources = true
            isDownloadJavadoc = true
        }
    }

    // The game writes asset edits into the build folder; this brings them back to the source tree.
    // One per project, because each project has its own resources and its own pack root.
    val syncAssets = tasks.register<Copy>("syncAssets") {
        group = "hytale"
        description = "Syncs assets from this project's build output back to its source tree."

        from(layout.buildDirectory.dir("resources/main"))
        into("src/main/resources")

        // The manifest in the build folder has already been expanded; the source one is a template.
        exclude("manifest.json")

        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        doLast {
            println("Assets synced back into ${project.name}/src/main/resources")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Formatting and dead-code checks
    // -----------------------------------------------------------------------------------------

    /// Deliberately *not* a wholesale reformatter.
    ///
    /// A full google-java-format/palantir pass would rewrite every file in the repo and flatten the
    /// hand-aligned constant blocks and the `///` markdown doc comments that carry most of the
    /// reasoning here. These steps are the mechanical ones instead -- the edits nobody would ever
    /// make on purpose -- so `spotlessApply` is safe to run on a whole tree without reviewing it.
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/main/java/**/*.java")

            removeUnusedImports()
            // No `importOrder` on purpose: Spotless separates its groups with blank lines, which
            // reflows the import block of every file in the tree for no gain. IntelliJ already
            // keeps the order; this only deletes what nothing uses.
            trimTrailingWhitespace()
            endWithNewline()
            leadingTabsToSpaces(4)
        }

        format("assets") {
            target("src/main/resources/**/*.ui", "src/main/resources/**/*.json")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    /// Dead code.
    ///
    /// Nothing in the Java ecosystem *deletes* an unused method for you, and that is not a gap in
    /// the tooling: `public` is an API surface the compiler cannot see past, and this plugin's
    /// components, interactions and systems are instantiated by the server's asset loader by name,
    /// so "nothing calls it" is not the same as "nothing uses it". PMD is therefore scoped to the
    /// cases that are decidable from one file -- private members, locals, parameters, assignments --
    /// where the fix is unambiguous.
    ///
    /// Anything auto-fixable is already handled: Spotless deletes unused imports on
    /// `spotlessApply`. What is left needs the judgement call between deleting the member and
    /// wiring it up, so this fails the build rather than filing a report nobody reads.
    ///
    /// PMD needs 7.26 or newer here. Older releases bundle an ASM that cannot read class file major
    /// version 69, so on a Java 25 toolchain they fall back to unresolved types and drown the log
    /// in parse-failure stack traces.
    configure<PmdExtension> {
        toolVersion = "7.26.0"
        // rootProject.file, not a relative path: `resources.text.fromFile` resolves against the
        // *subproject* directory, so a relative path silently looks for HytechCore/config/...
        ruleSetConfig = resources.text.fromFile(rootProject.file("config/pmd/dead-code.xml"))
        ruleSets = emptyList()
        isConsoleOutput = true
        isIgnoreFailures = false
    }

    tasks.named("pmdMain") {
        // Reads the compiled classes for type resolution, so the sources have to compile first.
        dependsOn(tasks.named("compileJava"))
    }

    // PMD only has rules for main sources here; there is no test source set.
    tasks.matching { it.name == "pmdTest" }.configureEach { enabled = false }

    tasks.named("check") {
        dependsOn(tasks.named("spotlessCheck"), tasks.named("pmdMain"))
    }

    tasks.register("tidy") {
        group = "verification"
        description = "Applies the mechanical formatting fixes, then reports what needs a decision."

        dependsOn(tasks.named("spotlessApply"), tasks.named("pmdMain"))
        mustRunAfter(tasks.named("spotlessApply"))
    }

    // The dev server's working directory. `hytale.runDir` is resolved against the *project*
    // directory, so once the sources moved into subprojects each one grew its own `run/` -- and
    // the server came up on a fresh world, with the real 93MB universe, config and permissions
    // still sitting in the repo root. One run directory, shared, is also the only thing that makes
    // sense: there is one dev world, and only one project starts a server on it.
    configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
        runDir = rootProject.layout.projectDirectory.dir("run").asFile.absolutePath
    }

    // The decompiler runs in a forked JVM, so org.gradle.jvmargs does not reach it. Without a
    // decent heap it spends its time in GC rather than decompiling.
    //
    // Only one project may own it: both would write the same HytaleServer-sources.jar next to the
    // server jar, and two tasks racing on one output is a build that fails intermittently.
    tasks.named<JavaExec>("decompileServer") {
        maxHeapSize = "4g"
        enabled = project.name == "HytechCore"
        if (!enabled) {
            description = "Disabled -- run :HytechCore:decompileServer instead (one shared output)."
        }
    }

    // Bring the game's asset edits back after the server stops. `runServer` is the only run task
    // the hytale-mod plugin creates; the content project's is the one you use, and it has to sync
    // both trees because the server loads both packs.
    afterEvaluate {
        tasks.findByName("runServer")?.finalizedBy(syncAssets)
    }
}

// ---------------------------------------------------------------------------------------------
// Root aggregates
// ---------------------------------------------------------------------------------------------

tasks.register("tidy") {
    group = "verification"
    description = "Runs tidy in every mod project."
    dependsOn(subprojects.map { "${it.path}:tidy" })
}

tasks.register("server") {
    group = "hytale"
    description = "Runs the dev server with both plugins on the classpath."
    dependsOn(":HytechPlugin:runServer")
}
