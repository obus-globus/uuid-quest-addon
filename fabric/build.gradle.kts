import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Evaluated once per node in versions/; the node name is the Minecraft version it builds against.
val minecraft = sc.current.version

// 26.x ships unobfuscated: it needs the plain Loom plugin, no mappings and no `mod*` configurations.
// Older versions need the remapping plugin and Mojang's official mappings, so the source uses the same
// names on every version.
val unobfuscated = sc.current.parsed >= "26"
apply(plugin = if (unobfuscated) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")

val javaVersion = (property("java") as String).toInt()
val mcRange = property("mod.mc_range") as String
val mcLabel = property("mod.mc_label") as String
val loaderMin = property("mod.loader_min") as String
val modVersion = "${property("mod_version")}+$mcLabel"

version = modVersion
group = property("maven_group") as String

extensions.configure<BasePluginExtension> {
    archivesName = "uuid-quest-fabric"
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
}

// Core's classes are copied into the jar.
val bundled = configurations.create("bundled") {
    isTransitive = false
}

val loom = the<LoomGradleExtensionAPI>()
val modImplementation = if (unobfuscated) "implementation" else "modImplementation"
val modCompileOnly = if (unobfuscated) "compileOnly" else "modCompileOnly"
val modLocalRuntime = if (unobfuscated) "localRuntime" else "modLocalRuntime"

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")
    if (!unobfuscated) "mappings"(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Optional at runtime; the `modmenu` entrypoint is only loaded when Mod Menu is installed.
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }
    if (providers.gradleProperty("modmenu_runtime").orNull != "false") {
        modLocalRuntime("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }
    }

    "implementation"(project(":core"))
    bundled(project(":core"))
}

// The dev client shares the machine; without a cap the JVM may take a quarter of its memory.
loom.runs.configureEach {
    vmArg("-Xmx2G")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.named<ProcessResources>("processResources") {
    val properties = mapOf(
        "version" to modVersion,
        "minecraft_range" to mcRange,
        "loader_min" to loaderMin,
        "java" to javaVersion.toString(),
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.named<Jar>("jar") {
    dependsOn(bundled)
    from({ bundled.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
    from(rootProject.file("LICENSE")) {
        rename { "${it}_uuid-quest" }
    }
}

// The remapping plugin's distributable jar comes from remapJar; the unobfuscated one's from jar.
val modJar = tasks.named<AbstractArchiveTask>(if (unobfuscated) "jar" else "remapJar")

tasks.register<Copy>("collectJar") {
    group = "build"
    description = "Copies this node's mod jar to the root build/libs."
    from(modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}
