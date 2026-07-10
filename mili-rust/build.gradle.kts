plugins {
    `java-library`
}

// --- Cargo build for Rust JNI library (.dll/.so) ---
val cargoBuild = tasks.register<Exec>("buildRustBinary") {
    group = "build"
    description = "Builds the Rust optimization helper (CLI + JNI library)"

    workingDir(layout.projectDirectory.dir("src/rust"))
    commandLine("cargo", "build", "--release")
    environment("CARGO_TARGET_DIR", layout.buildDirectory.dir("cargo-target").get().asFile.absolutePath)

    inputs.files(fileTree(layout.projectDirectory.dir("src/rust")) { include("**/*") })
    outputs.dir(layout.buildDirectory.dir("cargo-target/release"))

    // Allow build to continue even if cargo is not available (e.g. CI without Rust toolchain)
    isIgnoreExitValue = true
}

val stageRustBinary = tasks.register<Copy>("stageRustBinary") {
    dependsOn(cargoBuild)

    val osName = System.getProperty("os.name").lowercase()
    val (cliBinary, libExt) = when {
        osName.contains("win") -> "optimizer.exe" to "dll"
        osName.contains("mac") -> "optimizer" to "dylib"
        else -> "optimizer" to "so"
    }

    from(layout.buildDirectory.file("cargo-target/release/$cliBinary")) {
        into("rust")
    }
    from(layout.buildDirectory.file("cargo-target/release/mili_optimizer.$libExt")) {
        into("rust")
    }
    into(layout.buildDirectory.dir("resources/main"))

    // Only stage if cargo build succeeded
    onlyIf { cargoBuild.get().executionResult.get().exitValue == 0 }
}

tasks.named<Jar>("jar") {
    dependsOn(stageRustBinary)
    from(stageRustBinary.map { it.outputs.files }) {
        into("rust")
        includeEmptyDirs = false
    }
}

tasks.named("processResources") {
    dependsOn(stageRustBinary)
}