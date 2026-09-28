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

tasks.register("addRustTargets") {
    group = "build"
    description = "Adds Rust cross-compilation targets via rustup"

    doLast {
        val targets = listOf(
            "x86_64-pc-windows-gnu",
            "x86_64-unknown-linux-gnu",
            "aarch64-unknown-linux-gnu",
            "aarch64-apple-darwin",
            "x86_64-apple-darwin"
        )

        for (target in targets) {
            val proc = ProcessBuilder(
                "rustup",
                "target",
                "add",
                target
            ).apply {
                redirectErrorStream(true)
                directory(
                    layout.projectDirectory
                        .dir("src/rust")
                        .asFile
                )
            }.start()

            val output =
                proc.inputStream
                    .bufferedReader()
                    .readText()

            proc.waitFor()

            if (proc.exitValue() != 0) {
                logger.warn(
                    "Failed to add rust target $target " +
                        "(may already be installed): $output"
                )
            } else {
                logger.lifecycle(
                    "Rust target $target ready"
                )
            }
        }
    }
}

tasks.register("buildRustBinariesAll") {
    group = "build"
    description = "Cross-compiles Rust JNI library for all platforms"

    dependsOn("addRustTargets")

    val rustSrcDir =
        layout.projectDirectory
            .dir("src/rust")
            .asFile

    val cargoTargetDir =
        layout.buildDirectory
            .dir("cargo-target")
            .get()
            .asFile

    inputs.files(
        fileTree(rustSrcDir) {
            include("**/*")
        }
    )

    outputs.dir(cargoTargetDir)

    doLast {
        data class NativeTarget(
            val target: String,
            val libPrefix: String,
            val libExt: String,
            val stagedName: String
        )

        val nativeTargets = listOf(
            NativeTarget(
                "x86_64-pc-windows-gnu",
                "",
                "dll",
                "mili_optimizer.dll"
            ),
            NativeTarget(
                "x86_64-unknown-linux-gnu",
                "lib",
                "so",
                "libmili_optimizer.so"
            ),
            NativeTarget(
                "aarch64-unknown-linux-gnu",
                "lib",
                "so",
                "libmili_optimizer_aarch64.so"
            ),
            NativeTarget(
                "aarch64-apple-darwin",
                "lib",
                "dylib",
                "libmili_optimizer.dylib"
            ),
            NativeTarget(
                "x86_64-apple-darwin",
                "lib",
                "dylib",
                "libmili_optimizer_x86_64.dylib"
            )
        )

        val homeDir =
            System.getProperty("user.home")

        val cargoBinDir =
            File(homeDir, ".cargo/bin")

        val cargoZigbuild =
            File(cargoBinDir, "cargo-zigbuild")

        val currentPath =
            System.getenv("PATH") ?: ""

        val processPath =
            if (
                cargoBinDir.isDirectory &&
                cargoBinDir.absolutePath !in
                    currentPath.split(
                        File.pathSeparator
                    )
            ) {
                cargoBinDir.absolutePath +
                    File.pathSeparator +
                    currentPath
            } else {
                currentPath
            }

        val hasCargoZigbuild =
            cargoZigbuild.isFile &&
                cargoZigbuild.canExecute()

        logger.lifecycle(
            "cargo-zigbuild: ${
                if (hasCargoZigbuild) {
                    cargoZigbuild.absolutePath
                } else {
                    "not found"
                }
            }"
        )

        for (nt in nativeTargets) {
            logger.lifecycle(
                "Building Rust target: ${nt.target}"
            )

            val isLinuxX64 =
                nt.target ==
                    "x86_64-unknown-linux-gnu"

            /*
             * IMPORTANT:
             *
             * x86_64 Linux MUST use cargo-zigbuild
             * with glibc 2.28 compatibility.
             */
            val command: List<String>

            if (isLinuxX64) {
                if (!hasCargoZigbuild) {
                    throw GradleException(
                        "cargo-zigbuild is required for " +
                            "x86_64 Linux builds to provide " +
                            "GLIBC 2.28 compatibility, but it " +
                            "was not found at " +
                            "${cargoZigbuild.absolutePath}"
                    )
                }

                command = listOf(
                    cargoZigbuild.absolutePath,
                    "zigbuild",
                    "--release",
                    "--lib",
                    "--bin",
                    "rustd",
                    "--target",
                    "x86_64-unknown-linux-gnu.2.28"
                )

                logger.lifecycle(
                    "Using cargo-zigbuild for x86_64 Linux " +
                        "with GLIBC 2.28 compatibility"
                )
            } else {
                command = listOf(
                    "cargo",
                    "build",
                    "--release",
                    "--lib",
                    "--bin",
                    "rustd",
                    "--target",
                    nt.target
                )
            }

            val pb =
                ProcessBuilder(command).apply {
                    redirectErrorStream(true)
                    directory(rustSrcDir)

                    environment()["CARGO_TARGET_DIR"] =
                        cargoTargetDir.absolutePath

                    environment()["PATH"] =
                        processPath
                }

            val proc = pb.start()

            val output =
                proc.inputStream
                    .bufferedReader()
                    .readText()

            val exitCode =
                proc.waitFor()

            if (exitCode != 0) {
                if (isLinuxX64) {
                    throw GradleException(
                        "cargo-zigbuild failed for " +
                            "${nt.target}:\n$output"
                    )
                }

                /*
                 * Preserve original behavior:
                 *
                 * non-Linux-x64 cross targets are allowed
                 * to fail because GitHub Ubuntu cannot
                 * actually provide a macOS SDK and some
                 * AArch64 linkers may be unavailable.
                 */
                logger.warn(
                    "Cargo build failed for " +
                        "${nt.target} (skipping):\n$output"
                )
            } else {
                logger.lifecycle(
                    "Rust build succeeded for ${nt.target}"
                )

                /*
                 * Verify the actual Linux x86_64 output.
                 */
                if (isLinuxX64) {
                    val linuxCandidates = listOf(
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu/" +
                                "release/libmili_optimizer.so"
                        ),
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu.2.28/" +
                                "release/libmili_optimizer.so"
                        )
                    )

                    val linuxSo =
                        linuxCandidates
                            .firstOrNull { it.isFile }
                            ?: throw GradleException(
                                "cargo-zigbuild succeeded but " +
                                    "libmili_optimizer.so was not " +
                                    "found.\n" +
                                    "Checked:\n" +
                                    linuxCandidates.joinToString(
                                        "\n"
                                    )
                            )

                    logger.lifecycle(
                        "Linux native library generated: " +
                            "${linuxSo.absolutePath} " +
                            "(${linuxSo.length()} bytes)"
                    )

                    /*
                     * Verify actual GLIBC requirements.
                     */
                    val verifyProc =
                        ProcessBuilder(
                            "objdump",
                            "-T",
                            linuxSo.absolutePath
                        ).apply {
                            redirectErrorStream(true)
                        }.start()

                    val verifyOutput =
                        verifyProc.inputStream
                            .bufferedReader()
                            .readText()

                    val verifyExitCode =
                        verifyProc.waitFor()

                    if (verifyExitCode != 0) {
                        throw GradleException(
                            "Failed to inspect GLIBC " +
                                "requirements of " +
                                "${linuxSo.absolutePath}:\n" +
                                verifyOutput
                        )
                    }

                    val glibcVersions =
                        Regex(
                            """GLIBC_[0-9.]+"""
                        )
                            .findAll(verifyOutput)
                            .map { it.value }
                            .distinct()
                            .sortedWith(
                                Comparator { a, b ->
                                    compareVersion(
                                        a.substringAfter(
                                            "GLIBC_"
                                        ),
                                        b.substringAfter(
                                            "GLIBC_"
                                        )
                                    )
                                }
                            )
                            .toList()

                    logger.lifecycle(
                        "Linux GLIBC requirements: " +
                            glibcVersions.joinToString(", ")
                    )

                    val maxGlibc =
                        glibcVersions.lastOrNull()

                    if (maxGlibc != null) {
                        val version =
                            maxGlibc.substringAfter(
                                "GLIBC_"
                            )

                        if (
                            compareVersion(
                                version,
                                "2.28"
                            ) > 0
                        ) {
                            throw GradleException(
                                "INCOMPATIBLE Linux native " +
                                    "library: $maxGlibc detected. " +
                                    "Required maximum is GLIBC_2.28."
                            )
                        }
                    }
                }
            }
        }
    }
}

tasks.register("stageRustBinary") {
    group = "build"

    description =
        "Stages all Rust JNI libraries into build directory"

    dependsOn("buildRustBinariesAll")

    val cargoTargetDir =
        layout.buildDirectory
            .dir("cargo-target")
            .get()
            .asFile

    val rustBuildDir =
        layout.buildDirectory
            .dir("rust")
            .get()
            .asFile

    outputs.dir(rustBuildDir)

    doLast {
        data class NativeTarget(
            val target: String,
            val libPrefix: String,
            val libExt: String,
            val stagedName: String
        )

        val nativeTargets = listOf(
            NativeTarget(
                "x86_64-pc-windows-gnu",
                "",
                "dll",
                "mili_optimizer.dll"
            ),
            NativeTarget(
                "x86_64-unknown-linux-gnu",
                "lib",
                "so",
                "libmili_optimizer.so"
            ),
            NativeTarget(
                "aarch64-unknown-linux-gnu",
                "lib",
                "so",
                "libmili_optimizer_aarch64.so"
            ),
            NativeTarget(
                "aarch64-apple-darwin",
                "lib",
                "dylib",
                "libmili_optimizer.dylib"
            ),
            NativeTarget(
                "x86_64-apple-darwin",
                "lib",
                "dylib",
                "libmili_optimizer_x86_64.dylib"
            )
        )

        rustBuildDir.mkdirs()

        /*
         * Stage native JNI libraries.
         */
        for (nt in nativeTargets) {
            val candidates =
                if (
                    nt.target ==
                        "x86_64-unknown-linux-gnu"
                ) {
                    listOf(
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu/" +
                                "release/libmili_optimizer.so"
                        ),
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu.2.28/" +
                                "release/libmili_optimizer.so"
                        )
                    )
                } else {
                    listOf(
                        File(
                            cargoTargetDir,
                            "${nt.target}/release/" +
                                "${nt.libPrefix}" +
                                "mili_optimizer." +
                                nt.libExt
                        )
                    )
                }

            val builtLib =
                candidates.firstOrNull { it.isFile }

            if (builtLib != null) {
                val destination =
                    File(
                        rustBuildDir,
                        nt.stagedName
                    )

                builtLib.copyTo(
                    destination,
                    overwrite = true
                )

                logger.lifecycle(
                    "Staged: ${destination.name} " +
                        "(${builtLib.length()} bytes) " +
                        "from ${builtLib.absolutePath}"
                )

                /*
                 * Final verification of Linux x86_64.
                 */
                if (
                    nt.target ==
                        "x86_64-unknown-linux-gnu" &&
                    nt.stagedName ==
                        "libmili_optimizer.so"
                ) {
                    val verifyProc =
                        ProcessBuilder(
                            "objdump",
                            "-T",
                            destination.absolutePath
                        ).apply {
                            redirectErrorStream(true)
                        }.start()

                    val verifyOutput =
                        verifyProc.inputStream
                            .bufferedReader()
                            .readText()

                    val verifyExitCode =
                        verifyProc.waitFor()

                    if (verifyExitCode != 0) {
                        throw GradleException(
                            "Failed to inspect staged Linux " +
                                "native library: " +
                                "${destination.absolutePath}\n" +
                                verifyOutput
                        )
                    }

                    val glibcVersions =
                        Regex(
                            """GLIBC_[0-9.]+"""
                        )
                            .findAll(verifyOutput)
                            .map { it.value }
                            .distinct()
                            .sortedWith(
                                Comparator { a, b ->
                                    compareVersion(
                                        a.substringAfter(
                                            "GLIBC_"
                                        ),
                                        b.substringAfter(
                                            "GLIBC_"
                                        )
                                    )
                                }
                            )
                            .toList()

                    logger.lifecycle(
                        "FINAL staged Linux GLIBC " +
                            "requirements: " +
                            glibcVersions.joinToString(", ")
                    )

                    val maxGlibc =
                        glibcVersions.lastOrNull()

                    if (maxGlibc != null) {
                        val version =
                            maxGlibc.substringAfter(
                                "GLIBC_"
                            )

                        if (
                            compareVersion(
                                version,
                                "2.28"
                            ) > 0
                        ) {
                            throw GradleException(
                                "FINAL staged library is " +
                                    "invalid: $maxGlibc > " +
                                    "GLIBC_2.28"
                            )
                        }
                    }

                    /*
                     * Keep both names pointing to the exact
                     * same compatible binary.
                     */
                    destination.copyTo(
                        File(
                            rustBuildDir,
                            "mili_optimizer.so"
                        ),
                        overwrite = true
                    )

                    logger.lifecycle(
                        "Created compatible alias: " +
                            "${rustBuildDir}/mili_optimizer.so"
                    )
                }
            } else {
                logger.warn(
                    "Native library not found for " +
                        "${nt.target}: " +
                        candidates.joinToString()
                )
            }
        }

        /*
         * Host fallback.
         *
         * Never allow ordinary Linux host build to overwrite
         * the GLIBC-compatible x86_64 Linux library.
         */
        val hostOs =
            System.getProperty("os.name")
                .lowercase()

        val hostExt =
            when {
                hostOs.contains("win") ->
                    "dll"

                hostOs.contains("mac") ->
                    "dylib"

                else ->
                    "so"
            }

        val hostPrefix =
            if (
                hostOs.contains("win")
            ) {
                ""
            } else {
                "lib"
            }

        val hostLib =
            File(
                cargoTargetDir,
                "release/" +
                    "${hostPrefix}" +
                    "mili_optimizer." +
                    hostExt
            )

        val hostStaged =
            if (
                hostOs.contains("win")
            ) {
                File(
                    rustBuildDir,
                    "mili_optimizer.dll"
                )
            } else if (
                hostOs.contains("mac")
            ) {
                File(
                    rustBuildDir,
                    "libmili_optimizer.dylib"
                )
            } else {
                File(
                    rustBuildDir,
                    "libmili_optimizer.so"
                )
            }

        if (
            !hostOs.contains("linux") &&
            !hostStaged.exists() &&
            hostLib.exists()
        ) {
            hostLib.copyTo(
                hostStaged,
                overwrite = true
            )

            logger.lifecycle(
                "Staged host fallback: " +
                    "${hostStaged.name} " +
                    "(${hostLib.length()} bytes)"
            )
        } else if (
            hostOs.contains("linux")
        ) {
            logger.lifecycle(
                "Skipping Linux host fallback so the " +
                    "GLIBC-compatible native library " +
                    "is preserved"
            )
        }

        /*
         * ---------------------------------------------------------
         * Mili rustd daemon staging
         * ---------------------------------------------------------
         *
         * IMPORTANT:
         * This MUST remain inside doLast.
         *
         * buildRustBinariesAll runs before stageRustBinary,
         * so rustd binaries do not exist during Gradle
         * configuration time.
         */
        val rustdStageDir =
            File(
                rustBuildDir,
                "rustd"
            )

        rustdStageDir.mkdirs()

        val rustdBinaries = mapOf(
            "x86_64-pc-windows-gnu" to
                "rustd-x86_64-pc-windows-gnu.exe",

            "x86_64-unknown-linux-gnu" to
                "rustd-x86_64-unknown-linux-gnu",

            "aarch64-unknown-linux-gnu" to
                "rustd-aarch64-unknown-linux-gnu",

            "x86_64-apple-darwin" to
                "rustd-x86_64-apple-darwin",

            "aarch64-apple-darwin" to
                "rustd-aarch64-apple-darwin"
        )

        for (
            (target, stagedName)
            in rustdBinaries
        ) {
            val binName =
                if (
                    target.startsWith(
                        "x86_64-pc-windows"
                    )
                ) {
                    "rustd.exe"
                } else {
                    "rustd"
                }

            /*
             * Linux x86_64 may be generated under either
             * target directory depending on cargo-zigbuild.
             */
            val candidates =
                if (
                    target ==
                        "x86_64-unknown-linux-gnu"
                ) {
                    listOf(
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu/" +
                                "release/rustd"
                        ),
                        File(
                            cargoTargetDir,
                            "x86_64-unknown-linux-gnu.2.28/" +
                                "release/rustd"
                        )
                    )
                } else {
                    listOf(
                        File(
                            cargoTargetDir,
                            "$target/release/$binName"
                        )
                    )
                }

            val srcBin =
                candidates.firstOrNull { it.isFile }

            if (srcBin != null) {
                val dst =
                    File(
                        rustdStageDir,
                        stagedName
                    )

                srcBin.copyTo(
                    dst,
                    overwrite = true
                )

                logger.lifecycle(
                    "Staged rustd: $stagedName " +
                        "(${srcBin.length()} bytes) " +
                        "from ${srcBin.absolutePath}"
                )
            } else {
                if (
                    target ==
                        "x86_64-unknown-linux-gnu"
                ) {
                    throw GradleException(
                        "rustd binary missing for required " +
                            "target $target.\n" +
                            "Checked:\n" +
                            candidates.joinToString("\n")
                    )
                }

                logger.lifecycle(
                    "Skipping rustd for $target " +
                        "(not built)"
                )
            }
        }

        /*
         * Mili end - stage rustd daemon binaries
         */
    }
}

tasks.named<Jar>("jar") {
    dependsOn("stageRustBinary")

    from(
        sourceSets.main
            .get()
            .output
    )

    from(
        layout.buildDirectory
            .dir("rust")
    ) {
        into("rust")
        includeEmptyDirs = false
    }
}

tasks.named("processResources") {
    dependsOn("stageRustBinary")
}

/*
 * Compare dotted numeric versions such as:
 *
 *   2.28
 *   2.30
 *   2.34
 *
 * Returns:
 *
 *   < 0 when a < b
 *   = 0 when a == b
 *   > 0 when a > b
 */
fun compareVersion(
    a: String,
    b: String
): Int {
    val left =
        a.split(".")
            .mapNotNull {
                it.toIntOrNull()
            }

    val right =
        b.split(".")
            .mapNotNull {
                it.toIntOrNull()
            }

    val maxSize =
        maxOf(
            left.size,
            right.size
        )

    for (i in 0 until maxSize) {
        val l =
            left.getOrElse(i) {
                0
            }

        val r =
            right.getOrElse(i) {
                0
            }

        if (l != r) {
            return l.compareTo(r)
        }
    }

    return 0
}