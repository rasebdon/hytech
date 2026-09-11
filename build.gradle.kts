plugins {
    // apply false: keeps them off the sourceless root project; each subproject applies them below.
    id("hytale-mod") version "0.+" apply false
    id("com.diffplug.spotless") version "7.0.3" apply false

    // hytale-mod writes IDEA run config through the root project's `idea`/idea-ext extensions but
    // applies the plugins to itself (now a subproject), so they must be applied at root too.
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
}

val javaVersion = 25

val appData = System.getenv("APPDATA") ?: (System.getenv("HOME") + "/.var/app/com.hypixel.HytaleLauncher/data")
val hytaleAssets = file("$appData/Hytale/install/release/package/game/latest/Assets.zip")

// Resolved here, not inside `subprojects`: `libs` reads the catalog off the project it's evaluated
// against, and a subproject has no such extension.
val jetbrainsAnnotations = libs.jetbrains.annotations
val jspecify = libs.jspecify

// ---------------------------------------------------------------------------------------------
// Shared mod configuration
// ---------------------------------------------------------------------------------------------

/// Every subproject is a Hytale plugin, configured identically apart from its manifest entrypoint.
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

    // Manifest is a template: per-plugin values live in each project's manifest.json, shared
    // metadata comes from gradle.properties.
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

    // Brings the game's asset edits in the build folder back to the source tree; one per project.
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

    /// Not a reformatter: a full google-java-format pass would flatten the aligned constant blocks
    /// and doc comments here. Only mechanical edits, safe to run unreviewed.
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/main/java/**/*.java")

            removeUnusedImports()
            // No `importOrder`: it'd insert blank lines and reflow every file's import block for
            // no gain.
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

    /// PMD is scoped to file-local dead code (private members/locals/params) because components,
    /// interactions and systems are instantiated by name from assets, so "nothing calls it" doesn't
    /// mean "nothing uses it". Requires PMD 7.26+ -- older releases can't read class file version
    /// 69 (Java 25) and silently lose type resolution.
    configure<PmdExtension> {
        toolVersion = "7.26.0"
        // rootProject.file, not relative: `resources.text.fromFile` resolves against the
        // subproject dir.
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

    // hytale.runDir resolves against the project dir; without this each subproject would get its
    // own fresh run/, orphaning the real world/config in the repo root.
    configure<dev.hytalemods.gradle.hytalemod.HytaleExtension> {
        runDir = rootProject.layout.projectDirectory.dir("run").asFile.absolutePath
    }

    // Forked JVM: org.gradle.jvmargs doesn't reach it, hence maxHeapSize here. Enabled only in
    // HytechCore -- both projects would race writing the same HytaleServer-sources.jar otherwise.
    tasks.named<JavaExec>("decompileServer") {
        maxHeapSize = "4g"
        enabled = project.name == "HytechCore"
        if (!enabled) {
            description = "Disabled -- run :HytechCore:decompileServer instead (one shared output)."
        }
    }

    // Syncs assets back after the server stops; the content project's runServer loads both packs.
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
