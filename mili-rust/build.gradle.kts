plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.10.1")
}

// --- Cargo cross-compile for Rust JNI library (all platforms) ---

// Resolve the target list: prefer the RUST_TARGETS env var, otherwise pick sane defaults per host OS
// (MSVC/macOS targets cannot be cross-compiled from a Linux runner)
fun rustTargets(): List<String> {
    val env = System.getenv("RUST_TARGETS")
    if (!env.isNullOrBlank()) {
        return env.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val host = System.getProperty("os.name").lowercase()
    return when {
        host.contains("win") -> listOf("x86_64-pc-windows-msvc")
        host.contains("mac") -> listOf("aarch64-apple-darwin", "x86_64-apple-darwin")
        else -> listOf("x86_64-pc-windows-gnu", "x86_64-unknown-linux-gnu", "aarch64-unknown-linux-gnu")
    }
}

// cdylib output file name and staged name for a given target
fun rustLibNames(target: String): Pair<String, String> {
    val built = when {
        target.contains("windows") -> "mili_optimizer.dll"
        target.contains("darwin") -> "libmili_optimizer.dylib"
        else -> "libmili_optimizer.so"
    }
    val staged = when {
        target.contains("windows") -> "mili_optimizer.dll"
        target == "aarch64-unknown-linux-gnu" -> "libmili_optimizer_aarch64.so"
        target.contains("linux") -> "libmili_optimizer.so"
        target == "aarch64-apple-darwin" -> "libmili_optimizer.dylib"
        target.contains("darwin") -> "libmili_optimizer_x86_64.dylib"
        else -> "libmili_optimizer_${target}.so"
    }
    return built to staged
}

tasks.register("addRustTargets") {
    group = "build"
    description = "Adds Rust cross-compilation targets via rustup"
    doLast {
        val targets = rustTargets()
        for (target in targets) {
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

tasks.register("buildRustBinariesAll") {
    group = "build"
    description = "Cross-compiles Rust JNI library for all platforms"
    dependsOn("addRustTargets")

    val rustSrcDir = layout.projectDirectory.dir("src/rust").asFile
    val cargoTargetDir = layout.buildDirectory.dir("cargo-target").get().asFile

    inputs.files(fileTree(rustSrcDir) { include("**/*") })
    outputs.dir(cargoTargetDir)

    doLast {
        val nativeTargets = rustTargets()

        // Detect available cargo subcommand: prefer zigbuild, fall back to build
        val useZigbuild = try {
            val probe = ProcessBuilder("cargo", "zigbuild", "--version").apply {
                redirectErrorStream(true)
                directory(rustSrcDir)
            }.start()
            probe.inputStream.bufferedReader().readText()
            probe.waitFor() == 0
        } catch (e: Exception) {
            false
        }

        // If cargo-zigbuild not on PATH, try to find it in ~/.cargo/bin
        var cargoCmd = "cargo"
        val subcommand = if (useZigbuild) "zigbuild" else "build"
        if (!useZigbuild) {
            // Check common cargo bin locations
            val homeDir = System.getProperty("user.home")
            val cargoBin = File(homeDir, ".cargo/bin/cargo-zigbuild")
            if (cargoBin.exists() && cargoBin.canExecute()) {
                logger.lifecycle("Found cargo-zigbuild at ${cargoBin.absolutePath}")
                // Use cargo with explicit zigbuild subcommand - cargo finds installed extensions
            }
            // Also check if PATH has cargo bin
            val pathEnv = System.getenv("PATH") ?: ""
            if (!pathEnv.contains(".cargo/bin")) {
                val cargoBinDir = File(homeDir, ".cargo/bin")
                if (cargoBinDir.isDirectory) {
                    // Prepend cargo bin to PATH for subsequent processes
                    val newPath = cargoBinDir.absolutePath + File.pathSeparator + pathEnv
                    // We can't easily modify the process environment for all subsequent calls,
                    // but we can set it per-process
                }
            }
        }

        logger.lifecycle("Using cargo $subcommand for cross-compilation (zigbuild available: $useZigbuild)")

        for (target in nativeTargets) {
            logger.lifecycle("Building Rust target: $target")

            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val isMsvcTarget = target.contains("windows-msvc")

            val pb = if (isWindows && isMsvcTarget) {
                // Use vcvarsall.bat to set up MSVC environment for windows-msvc target
                val vcvarsall = File("C:/Program Files/Microsoft Visual Studio/2022/Community/VC/Auxiliary/Build/vcvarsall.bat")
                if (vcvarsall.exists()) {
                    val cmd = "call \"${vcvarsall.absolutePath}\" x64 >nul 2>&1 && $cargoCmd $subcommand --release --lib --target $target"
                    ProcessBuilder("cmd", "/c", cmd).apply {
                        redirectErrorStream(true)
                        directory(rustSrcDir)
                        environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                    }
                } else {
                    ProcessBuilder(cargoCmd, subcommand, "--release", "--lib", "--target", target).apply {
                        redirectErrorStream(true)
                        directory(rustSrcDir)
                        environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                    }
                }
            } else {
                ProcessBuilder(cargoCmd, subcommand, "--release", "--lib", "--target", target).apply {
                    redirectErrorStream(true)
                    directory(rustSrcDir)
                    environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                }
            }

            // Ensure ~/.cargo/bin is in PATH for cargo subcommands
            val homeDir = System.getProperty("user.home")
            val cargoBinDir = File(homeDir, ".cargo/bin")
            if (cargoBinDir.isDirectory) {
                val currentPath = pb.environment().get("PATH") ?: ""
                if (!currentPath.contains(cargoBinDir.absolutePath)) {
                    pb.environment()["PATH"] = cargoBinDir.absolutePath + File.pathSeparator + currentPath
                }
            }

            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                logger.warn("Cargo $subcommand failed for $target (skipping):\n$output")
            } else {
                logger.lifecycle("Cargo $subcommand succeeded for $target")
            }
        }
    }
}

tasks.register("stageRustBinary") {
    group = "build"
    description = "Stages all Rust JNI libraries into build directory"
    dependsOn("buildRustBinariesAll")

    val cargoTargetDir = layout.buildDirectory.dir("cargo-target").get().asFile
    val rustBuildDir = layout.buildDirectory.dir("rust").get().asFile

    outputs.dir(rustBuildDir)

    doLast {
        rustBuildDir.mkdirs()

        for (target in rustTargets()) {
            val (builtName, stagedName) = rustLibNames(target)
            val builtLib = File(cargoTargetDir, "$target/release/$builtName")
            if (builtLib.exists()) {
                builtLib.copyTo(File(rustBuildDir, stagedName), overwrite = true)
                logger.lifecycle("Staged: $stagedName (${builtLib.length()} bytes) from $target")
            } else {
                logger.warn("Native library not found for $target: ${builtLib.absolutePath}")
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
    dependsOn("stageRustBinary")
    from(sourceSets.main.get().output)
    from(layout.buildDirectory.dir("rust")) {
        into("rust")
        includeEmptyDirs = false
    }
}

tasks.named("processResources") {
    dependsOn("stageRustBinary")
}
