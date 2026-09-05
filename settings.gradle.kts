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
