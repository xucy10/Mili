import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java // TODO java launcher tasks
    id("moe.luminolmc.hyacinthusweight.patcher")
}

paperweight {
    upstreams.register("luminol") {
        repo = github("LuminolMC", "Luminol")
        ref = providers.gradleProperty("luminolRef")

        patchFile {
            path = "luminol-server/build.gradle.kts"
            outputFile = file("mili-server/build.gradle.kts")
            patchFile = file("mili-server/build.gradle.kts.empty.patch")
        }
        patchFile {
            path = "luminol-api/build.gradle.kts"
            outputFile = file("mili-api/build.gradle.kts")
            patchFile = file("mili-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("mili-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchRepo("foliaApi") {
            upstreamPath = "folia-api"
            patchesDir = file("mili-api/folia-patches")
            outputDir = file("folia-api")
        }
        patchDir("luminolApi") {
            upstreamPath = "luminol-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("mili-api/luminol-patches")
            outputDir = file("luminol-api")
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val menthaMavenPublicUrl = "https://repo.menthamc.org/repository/maven-public/";

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven(menthaMavenPublicUrl)
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        implementation(platform("net.kyori:adventure-bom:4.26.1"))
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
        options.isFork = true
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://repo.menthamc.org/repository/maven-snapshots/") {
                name = "MenthaMC"
                credentials(PasswordCredentials::class) {
                    username = System.getenv("PRIVATE_MAVEN_REPO_USERNAME")
                    password = System.getenv("PRIVATE_MAVEN_REPO_PASSWORD")
                }
            }
        }
    }

    tasks.withType<Javadoc>().configureEach {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("-add-modules", "jdk.incubator.vector")
                addStringOption("Xdoclint:none", "-quiet")
            }
        }
    }
}

val activeForkReplacement = """
    val mili = forks.register("mili") {
        forks = luminol
        upstream.patchRepo("paperServer") {
            upstreamRepo = luminol.patchedRepo("paperServer")
            patchesDir = rootDirectory.dir("mili-server/paper-patches")
            outputDir = rootDirectory.dir("paper-server")
        }

        upstream.patchDir("luminolServer") {
            upstreamPath = "luminol-server"
            excludes = setOf("src/minecraft", "paper-patches", "minecraft-patches", "build.gradle.kts", "build.gradle.kts.patch")
            patchesDir = rootDirectory.dir("mili-server/luminol-patches")
            outputDir = rootDirectory.dir("luminol-server")
        }
    }

    activeFork = mili
""".trimIndent()

tasks.register("fixMiliFork") {
    dependsOn(":applyLuminolSingleFilePatches")
    doLast {
        val file = file("mili-server/build.gradle.kts")
        var content = file.readText()
        // Replace activeFork = luminol with mili fork registration
        if (content.contains("activeFork = luminol")) {
            content = content.replace("activeFork = luminol", activeForkReplacement)
        }
        // Replace project(":luminol-api") with project(":mili-api")
        content = content.replace(
            """implementation(project(":luminol-api")) // Luminol""",
            """implementation(project(":mili-api")) // Mili"""
        )
        if (content != file.readText()) {
            file.writeText(content)
            logger.lifecycle("Applied fixes to mili-server/build.gradle.kts")
        }
    }
    outputs.upToDateWhen { false }
}

tasks.matching { it.name.startsWith("compile") && it.project.name == "mili-server" }.configureEach {
    dependsOn(":fixMiliFork")
}
tasks.matching { it.name == "generateReobfMappings" && it.project.name == "mili-server" }.configureEach {
    dependsOn(":fixMiliFork")
}
