plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.10.1")
}

// --- Cargo cross-compile for Rust JNI library (all platforms) ---

// Target triples for cross-compilation
val rustTargets = listOf(
    "x86_64-pc-windows-gnu",   // Windows x86_64
    "x86_64-unknown-linux-gnu", // Linux x86_64
    "aarch64-unknown-linux-gnu",// Linux aarch64
    "aarch64-apple-darwin",     // macOS aarch64 (Apple Silicon)
    "x86_64-apple-darwin"       // macOS x86_64
)

// Map target triple to (libPrefix, libExt, stagedFileName)
data class NativeTarget(val target: String, val libPrefix: String, val libExt: String, val stagedName: String)

val nativeTargets = listOf(
    NativeTarget("x86_64-pc-windows-gnu", "", "dll", "mili_optimizer.dll"),
    NativeTarget("x86_64-unknown-linux-gnu", "lib", "so", "libmili_optimizer.so"),
    NativeTarget("aarch64-unknown-linux-gnu", "lib", "so", "libmili_optimizer_aarch64.so"),
    NativeTarget("aarch64-apple-darwin", "lib", "dylib", "libmili_optimizer.dylib"),
    NativeTarget("x86_64-apple-darwin", "lib", "dylib", "libmili_optimizer_x86_64.dylib")
)

val cargoTargetDir = layout.buildDirectory.dir("cargo-target").get().asFile
val rustBuildDir = layout.buildDirectory.dir("rust").get().asFile

// Add Rust targets via rustup (best-effort, continues if it fails)
val addRustTargets = tasks.register("addRustTargets") {
    group = "build"
    description = "Adds Rust cross-compilation targets via rustup"
    doLast {
        for (target in rustTargets) {
            val proc = ProcessBuilder("rustup", "target", "add", target).apply {
                redirectErrorStream(true)
                directory(layout.projectDirectory.dir("src/rust").asFile)
            }.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (proc.exitValue() != 0) {
                logger.warn("Failed to add rust target $target (may already be installed): $output")
            } else {
                logger.lifecycle("Rust target $target ready")
            }
        }
    }
}

// Cross-compile for each target using cargo-zigbuild (falls back to cargo on failure)
val cargoBuildAll = tasks.register("buildRustBinariesAll") {
    group = "build"
    description = "Cross-compiles Rust JNI library for all platforms"
    dependsOn(addRustTargets)

    inputs.files(fileTree(layout.projectDirectory.dir("src/rust")) { include("**/*") })
    outputs.dir(cargoTargetDir)

    doLast {
        // Detect available cargo subcommand: prefer zigbuild, fall back to build
        val useZigbuild = try {
            val probe = ProcessBuilder("cargo", "zigbuild", "--version").apply {
                redirectErrorStream(true)
                directory(layout.projectDirectory.dir("src/rust").asFile)
            }.start()
            probe.inputStream.bufferedReader().readText()
            probe.waitFor() == 0
        } catch (e: Exception) {
            false
        }
        val subcommand = if (useZigbuild) "zigbuild" else "build"
        logger.lifecycle("Using cargo $subcommand for cross-compilation (zigbuild available: $useZigbuild)")

        for (nt in nativeTargets) {
            logger.lifecycle("Building Rust target: ${nt.target}")
            val proc = ProcessBuilder(
                "cargo", subcommand, "--release", "--lib", "--target", nt.target
            ).apply {
                redirectErrorStream(true)
                directory(layout.projectDirectory.dir("src/rust").asFile)
                environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
            }.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                logger.warn("Cargo $subcommand failed for ${nt.target} (skipping):\n$output")
            } else {
                logger.lifecycle("Cargo $subcommand succeeded for ${nt.target}")
            }
        }
    }
}

val stageRustBinary = tasks.register("stageRustBinary") {
    group = "build"
    description = "Stages all Rust JNI libraries into build directory"

    dependsOn(cargoBuildAll)

    outputs.dir(rustBuildDir)

    doLast {
        rustBuildDir.mkdirs()

        for (nt in nativeTargets) {
            val builtLib = File(cargoTargetDir, "${nt.target}/release/${nt.libPrefix}mili_optimizer.${nt.libExt}")
            if (builtLib.exists()) {
                builtLib.copyTo(File(rustBuildDir, nt.stagedName), overwrite = true)
                logger.lifecycle("Staged: ${nt.stagedName} (${builtLib.length()} bytes) from ${nt.target}")
            } else {
                logger.warn("Native library not found for ${nt.target}: ${builtLib.absolutePath}")
            }
        }

        // Fallback: also build for host platform if cross-compile didn't cover it
        val hostOs = System.getProperty("os.name").lowercase()
        val hostExt = when {
            hostOs.contains("win") -> "dll"
            hostOs.contains("mac") -> "dylib"
            else -> "so"
        }
        val hostPrefix = if (hostOs.contains("win")) "" else "lib"
        val hostLib = File(cargoTargetDir, "release/${hostPrefix}mili_optimizer.$hostExt")
        val hostStaged = if (hostOs.contains("win")) {
            File(rustBuildDir, "mili_optimizer.dll")
        } else if (hostOs.contains("mac")) {
            File(rustBuildDir, "libmili_optimizer.dylib")
        } else {
            File(rustBuildDir, "libmili_optimizer.so")
        }
        if (!hostStaged.exists() && hostLib.exists()) {
            hostLib.copyTo(hostStaged, overwrite = true)
            logger.lifecycle("Staged host fallback: ${hostStaged.name} (${hostLib.length()} bytes)")
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(stageRustBinary)
    // Include compiled Java classes
    from(sourceSets.main.get().output)
    // Include all Rust binaries in the jar alongside compiled Java classes
    from(layout.buildDirectory.dir("rust")) {
        into("rust")
        includeEmptyDirs = false
    }
}

tasks.named("processResources") {
    dependsOn(stageRustBinary)
}
