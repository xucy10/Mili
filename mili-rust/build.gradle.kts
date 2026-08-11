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

tasks.register("addRustTargets") {
    group = "build"
    description = "Adds Rust cross-compilation targets via rustup"
    doLast {
        val targets = listOf(
            "x86_64-pc-windows-msvc",
            "x86_64-unknown-linux-gnu",
            "aarch64-unknown-linux-gnu",
            "aarch64-apple-darwin",
            "x86_64-apple-darwin"
        )
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
        data class NativeTarget(val target: String, val libPrefix: String, val libExt: String, val stagedName: String)

        val nativeTargets = listOf(
            NativeTarget("x86_64-pc-windows-msvc", "", "dll", "mili_optimizer.dll"),
            NativeTarget("x86_64-unknown-linux-gnu", "lib", "so", "libmili_optimizer.so"),
            NativeTarget("aarch64-unknown-linux-gnu", "lib", "so", "libmili_optimizer_aarch64.so"),
            NativeTarget("aarch64-apple-darwin", "lib", "dylib", "libmili_optimizer.dylib"),
            NativeTarget("x86_64-apple-darwin", "lib", "dylib", "libmili_optimizer_x86_64.dylib")
        )

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

        for (nt in nativeTargets) {
            logger.lifecycle("Building Rust target: ${nt.target}")
            
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val isMsvcTarget = nt.target.contains("windows-msvc")
            
            val pb = if (isWindows && isMsvcTarget) {
                // Use vcvarsall.bat to set up MSVC environment for windows-msvc target
                val vcvarsall = File("C:/Program Files/Microsoft Visual Studio/2022/Community/VC/Auxiliary/Build/vcvarsall.bat")
                if (vcvarsall.exists()) {
                    val cmd = "call \"${vcvarsall.absolutePath}\" x64 >nul 2>&1 && $cargoCmd $subcommand --release --lib --target ${nt.target}"
                    ProcessBuilder("cmd", "/c", cmd).apply {
                        redirectErrorStream(true)
                        directory(rustSrcDir)
                        environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                    }
                } else {
                    ProcessBuilder(cargoCmd, subcommand, "--release", "--lib", "--target", nt.target).apply {
                        redirectErrorStream(true)
                        directory(rustSrcDir)
                        environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                    }
                }
            } else {
                ProcessBuilder(cargoCmd, subcommand, "--release", "--lib", "--target", nt.target).apply {
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
                logger.warn("Cargo $subcommand failed for ${nt.target} (skipping):\n$output")
            } else {
                logger.lifecycle("Cargo $subcommand succeeded for ${nt.target}")
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
        data class NativeTarget(val target: String, val libPrefix: String, val libExt: String, val stagedName: String)

        val nativeTargets = listOf(
            NativeTarget("x86_64-pc-windows-msvc", "", "dll", "mili_optimizer.dll"),
            NativeTarget("x86_64-unknown-linux-gnu", "lib", "so", "libmili_optimizer.so"),
            NativeTarget("aarch64-unknown-linux-gnu", "lib", "so", "libmili_optimizer_aarch64.so"),
            NativeTarget("aarch64-apple-darwin", "lib", "dylib", "libmili_optimizer.dylib"),
            NativeTarget("x86_64-apple-darwin", "lib", "dylib", "libmili_optimizer_x86_64.dylib")
        )

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
