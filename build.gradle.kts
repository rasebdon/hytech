plugins {
    `maven-publish`
    pmd
    id("hytale-mod") version "0.+"
    id("com.diffplug.spotless") version "7.0.3"
}

group = "at.rasebdon"
version = "0.1.0"
val javaVersion = 25

val appData = System.getenv("APPDATA") ?: (System.getenv("HOME") + "/.var/app/com.hypixel.HytaleLauncher/data")
val hytaleAssets = file("$appData/Hytale/install/release/package/game/latest/Assets.zip")


repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    if (hytaleAssets.exists()) {
        compileOnly(files(hytaleAssets))
    } else {
        // Optional: Print a warning so you know why it's missing
        logger.warn("Hytale Assets.zip not found at: ${hytaleAssets.absolutePath}")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
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

hytale {

}

// The decompiler runs in a forked JVM, so org.gradle.jvmargs does not reach it. Without a
// decent heap it spends its time in GC rather than decompiling.
tasks.named<JavaExec>("decompileServer") {
    maxHeapSize = "4g"
}

tasks.withType<Jar> {
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

publishing {
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

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

tasks.register<Exec>("runDevServer") {
    group = "hytale"
    description = "Runs the hytale server in offline mode"

    
}

val syncAssets = tasks.register<Copy>("syncAssets") {
    group = "hytale"
    description = "Automatically syncs assets from Build back to Source after server stops."

    // Take from the temporary build folder (Where the game saved changes)
    from(layout.buildDirectory.dir("resources/main"))

    // Copy into your actual project source (Where your code lives)
    into("src/main/resources")

    // IMPORTANT: Protect the manifest template from being overwritten
    exclude("manifest.json")

    // If a file exists, overwrite it with the new version from the game
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Assets successfully synced from Game to Source Code!")
    }
}

afterEvaluate {
    // Now Gradle will find it, because the plugin has finished working
    val targetTask = tasks.findByName("runServer") ?: tasks.findByName("server")

    if (targetTask != null) {
        targetTask.finalizedBy(syncAssets)
        logger.lifecycle("✅ specific task '${targetTask.name}' hooked for auto-sync.")
    } else {
        logger.warn("⚠️ Could not find 'runServer' or 'server' task to hook auto-sync into.")
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting and dead-code checks
// ---------------------------------------------------------------------------------------------

/// Deliberately *not* a wholesale reformatter.
///
/// A full google-java-format/palantir pass would rewrite every file in the repo and flatten the
/// hand-aligned constant blocks and the `///` markdown doc comments that carry most of the
/// reasoning here. These steps are the mechanical ones instead -- the edits nobody would ever make
/// on purpose -- so `spotlessApply` is safe to run on a whole tree without reviewing the diff.
spotless {
    java {
        target("src/main/java/**/*.java")

        removeUnusedImports()
        // No `importOrder` on purpose: Spotless separates its groups with blank lines, which
        // reflows the import block of every file in the tree for no gain. IntelliJ already keeps
        // the order; this only deletes what nothing uses.
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
/// Nothing in the Java ecosystem *deletes* an unused method for you, and that is not a gap in the
/// tooling: `public` is an API surface the compiler cannot see past, and this plugin's components,
/// interactions and systems are instantiated by the server's asset loader by name, so "nothing
/// calls it" is not the same as "nothing uses it". PMD is therefore scoped to the cases that are
/// decidable from one file -- private members, locals, parameters, assignments -- where the fix is
/// unambiguous.
///
/// Anything auto-fixable is already handled: Spotless deletes unused imports on `spotlessApply`.
/// What is left needs the judgement call between deleting the member and wiring it up, so this
/// fails the build rather than filing a report nobody reads. The tree is at zero as of this commit.
///
/// PMD needs 7.26 or newer here. Older releases bundle an ASM that cannot read class file major
/// version 69, so on a Java 25 toolchain they fall back to unresolved types and drown the log in
/// parse-failure stack traces.
pmd {
    toolVersion = "7.26.0"
    ruleSetConfig = resources.text.fromFile("config/pmd/dead-code.xml")
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
