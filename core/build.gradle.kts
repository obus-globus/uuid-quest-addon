plugins {
    `java-library`
}

base {
    archivesName = "uuid-quest-core"
}

repositories {
    mavenCentral()
}

dependencies {
    // Minecraft ships Gson on every version.
    compileOnly(libs.gson)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Minecraft 1.20.1 runs on Java 17.
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
