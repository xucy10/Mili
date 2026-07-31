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

// --- Cargo build for Rust JNI library (.dll/.so) ---
val cargoBuild = tasks.register<Exec>("buildRustBinary") {
    group = "build"
    description = "Builds the Rust JNI optimization library"

    workingDir(layout.projectDirectory.dir("src/rust"))
    commandLine("cargo", "build", "--release", "--lib")
    environment("CARGO_TARGET_DIR", layout.buildDirectory.dir("cargo-target").get().asFile.absolutePath)

    inputs.files(fileTree(layout.projectDirectory.dir("src/rust")) { include("**/*") })
    outputs.dir(layout.buildDirectory.dir("cargo-target/release"))

    // Allow build to continue even if cargo is not available (e.g. CI without Rust toolchain)
    isIgnoreExitValue = true
}

val stageRustBinary = tasks.register("stageRustBinary") {
    group = "build"
    description = "Stages Rust JNI library into build directory"

    dependsOn(cargoBuild)

    val libExt = when {
        System.getProperty("os.name").lowercase().contains("win") -> "dll"
        System.getProperty("os.name").lowercase().contains("mac") -> "dylib"
        else -> "so"
    }

    val libPrefix = if (System.getProperty("os.name").lowercase().contains("win")) "" else "lib"

    val cargoTargetDir = layout.buildDirectory.dir("cargo-target/release").get().asFile
    val rustBuildDir = layout.buildDirectory.dir("rust").get().asFile

    outputs.dir(rustBuildDir)

    doLast {
        rustBuildDir.mkdirs()

        val libFile = cargoTargetDir.resolve("${libPrefix}mili_optimizer.$libExt")

        if (libFile.exists()) {
            libFile.copyTo(rustBuildDir.resolve("mili_optimizer.$libExt"), overwrite = true)
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(stageRustBinary)
    // Include compiled Java classes
    from(sourceSets.main.get().output)
    // Include Rust binaries in the jar alongside compiled Java classes
    from(layout.buildDirectory.dir("rust")) {
        into("rust")
        includeEmptyDirs = false
    }
}

tasks.named("processResources") {
    dependsOn(stageRustBinary)
}