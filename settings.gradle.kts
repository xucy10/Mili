pluginManagement {
    val weightVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.bacteriawa.com/repository/maven-public/")
    }

    plugins {
        id("fun.bm.comfreyweight.patcher") version weightVersion
        id("fun.bm.comfreyweight.core") version weightVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mili"

include("mili-api")
include("mili-server")
include("mili-rust")
