plugins {
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

java {
    withSourcesJar()
}

dependencies {
    // Nightconfig for CommentedFileConfig
    implementation("com.electronwill.night-config:toml:3.8.3")
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
