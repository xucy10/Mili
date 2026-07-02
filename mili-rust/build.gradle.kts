plugins {
    `java-library`
}

val cargoBuild = tasks.register<Exec>("buildRustBinary") {
    group = "build"
    description = "Builds the Rust optimization helper"

    workingDir(layout.projectDirectory.dir("src/rust"))
    commandLine("cargo", "build", "--release")
    environment("CARGO_TARGET_DIR", layout.buildDirectory.dir("cargo-target").get().asFile.absolutePath)

    inputs.files(fileTree(layout.projectDirectory.dir("src/rust")) { include("**/*") })
    outputs.dir(layout.buildDirectory.dir("cargo-target/release"))
}

val stageRustBinary = tasks.register<Copy>("stageRustBinary") {
    dependsOn(cargoBuild)

    val binaryName = if (System.getProperty("os.name").lowercase().contains("win")) "optimizer.exe" else "optimizer"
    from(layout.buildDirectory.file("cargo-target/release/$binaryName"))
    into(layout.buildDirectory.dir("rust"))
}

tasks.named<Jar>("jar") {
    dependsOn(stageRustBinary)
    from(stageRustBinary.map { it.outputs.files.singleFile }) {
        into("rust")
    }
}

tasks.named("processResources") {
    dependsOn(stageRustBinary)
}
