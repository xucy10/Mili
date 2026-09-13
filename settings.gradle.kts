pluginManagement {
    val weightVersion: String by settings

    repositories {
        mavenLocal()  // ← 优先使用本地 Maven
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.menthamc.org/repository/maven-public/")
    }

    plugins {
        // Milihyacinthus (paperweight fork) has been rebranded to the vanilla
        // io.papermc.paperweight plugin ids as of commit 5418e3d
        id("io.papermc.paperweight.patcher") version weightVersion
        id("io.papermc.paperweight.core") version weightVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mili"

include("mili-api")
include("mili-server")
include("mili-rust")
