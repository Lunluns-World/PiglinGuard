import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm") version "2.4.10"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "zip.tw527e"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT"

dependencies {
    compileOnly(paperApi)
    // Kotlin stdlib is not shaded; Paper downloads it via plugin.yml `libraries:`.
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "kotlinVersion" to getKotlinPluginVersion(),
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.runServer {
    minecraftVersion("1.21.8")
}

tasks.jar {
    // No NMS: tell Paper the jar needs no remapping.
    manifest.attributes("paperweight-mappings-namespace" to "mojang")
}
