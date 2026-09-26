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

// cdylib file name cargo emits for a given target.
fun rustBuiltName(target: String): String = when {
    target.contains("windows") -> "mili_optimizer.dll"
    target.contains("darwin") -> "libmili_optimizer.dylib"
    else -> "libmili_optimizer.so"
}

// Name the library is staged under inside the jar. RustBridge looks these up by exact name,
// so this mapping and its candidate lists must stay in lockstep:
//   Windows       -> mili_optimizer.dll
//   Linux x86_64  -> libmili_optimizer.so
//   Linux aarch64 -> libmili_optimizer_aarch64.so
//   macOS aarch64 -> libmili_optimizer.dylib
//   macOS x86_64  -> libmili_optimizer_x86_64.dylib
//
// Matching on the architecture rather than on one exact triple matters. Matching only
// "aarch64-unknown-linux-gnu" meant aarch64-unknown-linux-musl fell through to the generic
// name: the two Linux builds then overwrote each other, and aarch64 could never find its file.
fun rustStagedName(target: String): String = when {
    target.contains("windows") -> "mili_optimizer.dll"
    target.contains("aarch64") && target.contains("linux") -> "libmili_optimizer_aarch64.so"
    target.contains("linux") -> "libmili_optimizer.so"
    target.contains("aarch64") && target.contains("darwin") -> "libmili_optimizer.dylib"
    target.contains("darwin") -> "libmili_optimizer_x86_64.dylib"
    else -> "libmili_optimizer_${target}.so"
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

        // Detect available cargo subcommand: prefer zigbuild, fall back to build.
        // The reason for falling back is logged: for a long time the probe failed on CI
        // while cargo-zigbuild was in fact installed, and nothing said so.
        val zigbuildProbe = try {
            val probe = ProcessBuilder("cargo", "zigbuild", "--version").apply {
                redirectErrorStream(true)
                directory(rustSrcDir)
            }.start()
            val probeOut = probe.inputStream.bufferedReader().readText().trim()
            val probeExit = probe.waitFor()
            if (probeExit == 0) null else "`cargo zigbuild --version` exited $probeExit: $probeOut"
        } catch (e: Exception) {
            "`cargo zigbuild --version` could not start: ${e.message}"
        }
        val useZigbuild = zigbuildProbe == null

        val cargoCmd = "cargo"
        val subcommand = if (useZigbuild) "zigbuild" else "build"
        logger.lifecycle("Using cargo $subcommand for cross-compilation (zigbuild available: $useZigbuild)")
        if (!useZigbuild) {
            logger.lifecycle(
                "  reason: $zigbuildProbe" +
                    "\n  plain `cargo build` needs a working linker per target; the *-gnu " +
                    "triplets used here rely on the runner's cross gcc toolchain."
            )
        }

        // A target whose artifact never appears must fail the build. Treating it as a
        // warning is how this project shipped a jar containing only the Windows dll while
        // CI stayed green - cargo can report success and still emit nothing.
        val failures = mutableListOf<String>()

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
            val expected = File(cargoTargetDir, "$target/release/${rustBuiltName(target)}")
            when {
                exitCode != 0 -> {
                    failures += "$target: cargo exited $exitCode\n${output.trim()}"
                    logger.error("Cargo $subcommand failed for $target:\n$output")
                }
                !expected.isFile -> {
                    // Seen with the *-musl triplets: cargo exits 0 and emits nothing.
                    // Without this check the target is silently absent from the jar.
                    failures += "$target: cargo exited 0 but produced no artifact at " +
                        "${expected.absolutePath}\n${output.trim()}"
                    logger.error(
                        "Cargo $subcommand reported success for $target but " +
                            "${expected.absolutePath} does not exist:\n$output"
                    )
                }
                else -> {
                    logger.lifecycle(
                        "Cargo $subcommand succeeded for $target -> ${expected.name} " +
                            "(${expected.length()} bytes)"
                    )
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                "Failed to build the native library for ${failures.size} target(s):\n\n" +
                    failures.joinToString("\n\n")
            )
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

        val missing = mutableListOf<String>()
        val takenNames = mutableMapOf<String, String>()

        for (target in rustTargets()) {
            val builtName = rustBuiltName(target)
            val stagedName = rustStagedName(target)

            // Two targets sharing a staged name means one silently overwrites the other,
            // and the jar ends up missing a platform. Fail instead.
            val previous = takenNames.put(stagedName, target)
            if (previous != null && previous != target) {
                throw GradleException(
                    "Targets $previous and $target both stage to '$stagedName'; " +
                        "rustStagedName() must map every target to a unique name."
                )
            }

            val builtLib = File(cargoTargetDir, "$target/release/$builtName")
            if (builtLib.isFile) {
                builtLib.copyTo(File(rustBuildDir, stagedName), overwrite = true)
                logger.lifecycle("Staged: $stagedName (${builtLib.length()} bytes) from $target")
            } else {
                missing += "$target (expected ${builtLib.absolutePath})"
            }
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Native library missing for ${missing.size} target(s):\n  " +
                    missing.joinToString("\n  ") +
                    "\nThe jar would ship without them. See the buildRustBinariesAll output " +
                    "above for the cargo error."
            )
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
