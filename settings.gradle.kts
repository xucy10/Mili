pluginManagement {
    val weightVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.menthamc.org/repository/maven-public/")
    }

    plugins {
        id("moe.luminolmc.hyacinthusweight.patcher") version weightVersion
        id("moe.luminolmc.hyacinthusweight.core") version weightVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mili"

include("mili-api")
include("mili-server")
include("mili-rust")

// Replace lophine-api references and activeFork before project configuration
gradle.settingsEvaluated {
    val file = rootDir.resolve("mili-server/build.gradle.kts")
    if (file.exists()) {
        var content = file.readText()
        if (content.contains("activeFork = lophine")) {
            val forkBlock = """
    val mili = forks.register("mili") {
        forks = lophine
        upstream.patchRepo("paperServer") {
            upstreamRepo = lophine.patchedRepo("paperServer")
            patchesDir = rootDirectory.dir("mili-server/paper-patches")
            outputDir = rootDirectory.dir("paper-server")
        }

        upstream.patchDir("lophineServer") {
            upstreamPath = "lophine-server"
            excludes = setOf("src/minecraft", "paper-patches", "minecraft-patches", "build.gradle.kts", "build.gradle.kts.patch")
            patchesDir = rootDirectory.dir("mili-server/lophine-patches")
            outputDir = rootDirectory.dir("lophine-server")
        }
    }

    activeFork = mili
""".trimIndent()
            content = content.replace("activeFork = lophine", forkBlock)
        }
        content = content.replace(
            """implementation(project(":lophine-api")) // Lophine""",
            """implementation(project(":mili-api")) // Mili"""
        )
        if (content != file.readText()) {
            file.writeText(content)
        }
    }
}