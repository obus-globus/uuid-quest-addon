// Loaded once here so every subproject shares the same Loom and Kotlin classes.
plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.fabric.loom.remap) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register("collectJars") {
    group = "build"
    description = "Copies the add-on jar and every Fabric jar to build/libs."
    dependsOn(":liquidbounce:collectJar", ":fabric:collectJars")
}
