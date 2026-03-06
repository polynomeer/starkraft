plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.0.0"
    application
}

group = "starkraft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    implementation(project(":sim"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("starkraft.tools.ToolsCliKt")
}

tasks.register<Exec>("smoke") {
    group = "verification"
    description = "Run tools JSON contract smoke checks"
    workingDir = rootDir
    commandLine("bash", "scripts/tools_health.sh")
}
