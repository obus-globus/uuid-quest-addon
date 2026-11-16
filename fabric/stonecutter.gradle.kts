// The version the shared src/ is switched to. Commit with the VCS version (26.3) active.
stonecutter active "26.3"

tasks.register("collectJars") {
    group = "build"
    description = "Copies every node's mod jar to the root build/libs."
    dependsOn(stonecutter.tasks.names("collectJar"))
}
