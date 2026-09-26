pluginManagement {
    val weightVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.menthamc.org/repository/maven-public/")
    }

    plugins {
        // Milihyacinthus (paperweight fork) has been rebranded to the vanilla
        // io.papermc.paperweight plugin ids as of commit 5418e3d, and the fork now
        // publishes io.papermc.paperweight:*:2.0.0-SNAPSHOT. repo.menthamc.org,
        // the old Maven home of the moe.luminolmc.* ids, is offline and parked, so
        // those ids can no longer be resolved from any repository. CI publishes the
        // fork to mavenLocal from source before the first Gradle invocation.
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
