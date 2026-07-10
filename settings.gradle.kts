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

// Replace luminol-api references with mili-api before project configuration
gradle.settingsEvaluated {
    val file = rootDir.resolve("mili-server/build.gradle.kts")
    if (file.exists()) {
        val content = file.readText()
        if (content.contains(":luminol-api")) {
            file.writeText(content.replace(
                """implementation(project(":luminol-api")) // Luminol""",
                """implementation(project(":mili-api")) // Mili"""
            ))
        }
    }
}
