plugins {
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

java {
    withSourcesJar()
}

dependencies {
    // Compile-time access to mili-server classes (RootNode, CommandContext, etc.)
    // compileOnly avoids runtime circular dependency
    compileOnly(project(":mili-server"))

    // Luminol config annotations + types
    // Luminol config flags come from luminol-api source set, see sourceSets below

    // Nightconfig for CommentedFileConfig
    implementation("com.electronwill.night-config:toml:3.8.3")

    // For Component (Adventure) used in some event classes
    implementation("net.kyori:adventure-api")
}

sourceSets {
    main {
        java {
            srcDir("../luminol-api/src/main/java")
            srcDir("../paper-api/src/main/java")
            srcDir("../folia-api/src/main/java")
        }
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

configure<org.gradle.api.publish.PublishingExtension> {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
