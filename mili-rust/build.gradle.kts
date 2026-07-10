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

val stageRustBinary = tasks.register("stageRustBinary") {
    group = "build"
    description = "Stages Rust binaries into build directory"

    dependsOn(cargoBuild)

    val osName = System.getProperty("os.name").lowercase()
    val (cliBinary, libExt) = when {
        osName.contains("win") -> "optimizer.exe" to "dll"
        osName.contains("mac") -> "optimizer" to "dylib"
        else -> "optimizer" to "so"
    }

    val cargoTargetDir = layout.buildDirectory.dir("cargo-target/release").get().asFile
    val rustBuildDir = layout.buildDirectory.dir("rust").get().asFile

    outputs.dir(rustBuildDir)

    doLast {
        rustBuildDir.mkdirs()

        val optimizerFile = cargoTargetDir.resolve(cliBinary)
        val libFile = cargoTargetDir.resolve("mili_optimizer.$libExt")

        if (optimizerFile.exists()) {
            optimizerFile.copyTo(rustBuildDir.resolve(cliBinary), overwrite = true)
        }
        if (libFile.exists()) {
            libFile.copyTo(rustBuildDir.resolve("mili_optimizer.$libExt"), overwrite = true)
        }
    }
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