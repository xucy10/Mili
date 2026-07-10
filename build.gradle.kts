import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java // TODO java launcher tasks
    id("moe.luminolmc.hyacinthusweight.patcher")
}

paperweight {
    upstreams.register("lophine") {
        repo = github("LuminolMC", "Lophine")
        ref = providers.gradleProperty("lophineRef")

        patchFile {
            path = "lophine-api/build.gradle.kts"
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
        patchDir("lophineApi") {
            upstreamPath = "lophine-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("mili-api/lophine-patches")
            outputDir = file("lophine-api")
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

tasks.register("fixMiliFork") {
    dependsOn(":applyLophineSingleFilePatches")
    doLast {
        val file = file("mili-server/build.gradle.kts")
        var content = file.readText()
        // Replace activeFork = lophine with mili fork registration
        if (content.contains("activeFork = lophine")) {
            content = content.replace("activeFork = lophine", activeForkReplacement)
        }
        // Replace project(":lophine-api") with project(":mili-api")
        content = content.replace(
            """implementation(project(":lophine-api")) // Lophine""",
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