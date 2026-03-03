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
    mainClass.set("starkraft.sim.AppKt")
}

tasks.register<JavaExec>("benchmark") {
    group = "application"
    description = "Run headless simulation benchmark"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("starkraft.sim.bench.Benchmark")
}

tasks.register<JavaExec>("pathfindingBenchmark") {
    group = "application"
    description = "Run pathfinding micro-benchmark"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("starkraft.sim.bench.PathfindingMicroBenchmark")
}

tasks.register<JavaExec>("consumeSnapshotStream") {
    group = "application"
    description = "Consume a snapshot NDJSON stream and print a summary"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("starkraft.sim.client.SnapshotStreamConsumerKt")
}

tasks.register<JavaExec>("graphicalClient") {
    group = "application"
    description = "Run the minimal snapshot-driven graphical client"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("starkraft.sim.client.GraphicalClientKt")
}

tasks.register<JavaExec>("consoleClient") {
    group = "application"
    description = "Run the text client over stdin or a snapshot stream file"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("starkraft.sim.client.ConsoleClientKt")
}
