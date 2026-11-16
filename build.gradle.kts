// Loaded once here so every subproject shares the same Loom and Kotlin classes.
plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.fabric.loom.remap) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
