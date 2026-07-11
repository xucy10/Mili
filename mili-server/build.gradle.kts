import io.papermc.fill.model.BuildChannel
import io.papermc.paperweight.attribute.DevBundleOutput
import io.papermc.paperweight.util.*
import java.time.Instant

plugins {
    `java-library`
    `maven-publish`
    idea
    id("moe.luminolmc.hyacinthusweight.core")
    id("io.papermc.fill.gradle") version "1.0.10"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

dependencies {
    mache("io.papermc:mache:1.21.11+build.1")
    hyacinthusclip("moe.luminolmc:hyacinthusclip:${providers.gradleProperty("clipVersion").get()}")
    api(project(":mili-rust"))
}

paperweight {
    minecraftVersion = providers.gradleProperty("mcVersion")
    gitFilePatches = false
    
    val fork = forks.register("lophine") {
        rootDirectory = upstreamsDirectory().map { it.dir("lophine") }
        upstream.patchDir("paperServer") {
            upstreamPath = "paper-server"
            excludes = setOf("src/minecraft", "patches", "build.gradle.kts")
            patchesDir = rootDirectory.dir("lophine-server/paper-patches")
            outputDir = rootDirectory.dir("paper-server")
        }
        upstream.patchDir("lophineServer") {
            upstreamPath = "lophine-server"
            excludes = setOf(
                "src/minecraft",
                "paper-patches",
                "minecraft-patches/features/0001-Add-config-to-disable-some-check-for-operators.patch",
                "build.gradle.kts",
                "build.gradle.kts.patch"
            )
            patchesDir = rootDirectory.dir("lophine-server/lophine-patches")
            outputDir = rootDirectory.dir("lophine-server")
        }
    }

    val mili = forks.register("mili") {
        forks = fork
        upstream.patchRepo("paperServer") {
            upstreamRepo = fork.patchedRepo("paperServer")
            patchesDir = rootDirectory.dir("mili-server/paper-patches")
            outputDir = rootDirectory.dir("paper-server")
        }

        upstream.patchDir("lophineServer") {
            upstreamPath = "lophine-server"
            excludes = setOf(
                "src/minecraft", 
                "paper-patches", 
                "minecraft-patches/features/0001-Add-config-to-disable-some-check-for-operators.patch",
                "build.gradle.kts", 
                "build.gradle.kts.patch"
            )
            patchesDir = rootDirectory.dir("mili-server/lophine-patches")
            outputDir = rootDirectory.dir("lophine-server")
        }
    }

    activeFork = mili

    spigot {
        enabled = true
        buildDataRef = "17f77cee7117ab9d6175f088ae8962bfd04e61a9"
        packageVersion = "v1_21_R7"
    }

    reobfPackagesToFix.addAll(
        "co.aikar.timings",
        "com.destroystokyo.paper",
        "com.mojang",
        "io.papermc.paper",
        "ca.spottedleaf",
        "net.kyori.adventure.bossbar",
        "net.minecraft",
        "org.bukkit.craftbukkit",
        "org.spigotmc",
    )
}

tasks.generateDevelopmentBundle {
    libraryRepositories.addAll(
        "https://repo.maven.apache.org/maven2/",
        paperMavenPublicUrl,
    )
}

abstract class Services {
    @get:Inject
    abstract val archiveOperations: ArchiveOperations
}
val services = objects.newInstance<Services>()

if (project.providers.gradleProperty("publishDevBundle").isPresent) {
    val devBundleComponent = publishing.softwareComponentFactory.adhoc("devBundle")
    components.add(devBundleComponent)

    val devBundle = configurations.consumable("devBundle") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.ZIP))
        outgoing.artifact(tasks.generateDevelopmentBundle.flatMap { it.devBundleFile })
    }
    devBundleComponent.addVariantsFromConfiguration(devBundle) {}

    val runtime = configurations.consumable("serverRuntimeClasspath") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.SERVER_DEPENDENCIES))
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        extendsFrom(configurations.runtimeClasspath.get())
    }
    devBundleComponent.addVariantsFromConfiguration(runtime) {
        mapToMavenScope("runtime")
    }

    val compile = configurations.consumable("serverCompileClasspath") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.SERVER_DEPENDENCIES))
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        extendsFrom(configurations.compileClasspath.get())
    }
    devBundleComponent.addVariantsFromConfiguration(compile) {
        mapToMavenScope("compile")
    }

    tasks.withType(GenerateMavenPom::class).configureEach {
        doLast {
            val text = destination.readText()
            destination.writeText(
                text.substringBefore("<dependencies>") + text.substringAfter("</dependencies>")
            )
        }
    }

    publishing {
        publications.create<MavenPublication>("devBundle") {
            artifactId = "dev-bundle"
            from(devBundleComponent)
        }
    }
}

sourceSets {
    main {
        java { srcDir("../paper-server/src/main/java"); srcDir("../paper-server/src/generated/java") }
        resources { srcDir("../paper-server/src/main/resources") }
        java { srcDir("../folia-server/src/main/java") }
        resources { srcDir("../folia-server/src/main/resources") }
    }
    test {
        java { srcDir("../paper-server/src/test/java") }
        resources { srcDir("../paper-server/src/test/resources") }
        java { srcDir("../folia-server/src/test/java") }
        resources { srcDir("../folia-server/src/test/resources") }
    }
}

val log4jPlugins = sourceSets.create("log4jPlugins") {
    java { srcDir("../paper-server/src/log4jPlugins/java") }
}
configurations.named(log4jPlugins.compileClasspathConfigurationName) {
    extendsFrom(configurations.compileClasspath.get())
}
val alsoShade: Configuration by configurations.creating

val runtimeConfiguration by configurations.consumable("runtimeConfiguration") {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    extendsFrom(configurations.getByName(sourceSets.main.get().runtimeElementsConfigurationName))
}

val mockitoAgent = configurations.register("mockitoAgent")
abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:CompileClasspath
    abstract val fileCollection: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        return listOf("-javaagent:" + fileCollection.files.single().absolutePath)
    }
}

dependencies {
    implementation(project(":mili-api"))
    
    implementation("net.objecthunter:exp4j:0.4.8")
    implementation("io.netty:netty-all:4.2.9.Final")
    implementation("com.electronwill.night-config:toml:3.8.3")
    implementation("com.github.luben:zstd-jni:1.5.4-1")
    implementation("net.openhft:zero-allocation-hashing:0.16")
    implementation("io.github.classgraph:classgraph:4.8.158")
    implementation("net.openhft:affinity:3.23.3")
    implementation("org.spongepowered:configurate-gson:4.2.0-SNAPSHOT") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("ca.spottedleaf:concurrentutil:0.0.8")
    implementation("org.jline:jline-terminal-ffm:3.27.1")
    implementation("org.jline:jline-terminal-jni:3.27.1")
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("net.kyori:adventure-text-serializer-ansi")
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    log4jPlugins.annotationProcessorConfigurationName("org.apache.logging.log4j:log4j-core:2.24.1")
    runtimeOnly(log4jPlugins.output)
    alsoShade(log4jPlugins.output)
    implementation("one.pkg.velocity_rc:velocity-native:3.4.0-SNAPSHOT") {
        isTransitive = false
    }
    implementation("io.netty:netty-codec-haproxy:4.2.7.Final")
    implementation("org.apache.logging.log4j:log4j-iostreams:2.24.1")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    runtimeOnly("commons-lang:commons-lang:2.6")
    runtimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.2.0")
    runtimeOnly("com.lmax:disruptor:3.4.4")
    implementation("com.googlecode.json-simple:json-simple:1.1.1") {
        isTransitive = false
    }

    testImplementation("io.github.classgraph:classgraph:4.8.179")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.12.2")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.mockito:mockito-core:5.14.1")
    mockitoAgent("org.mockito:mockito-core:5.14.1") { isTransitive = false }
    testImplementation("org.ow2.asm:asm-tree:9.8")
    testImplementation("org.junit-pioneer:junit-pioneer:2.2.0")

    implementation("net.neoforged:srgutils:1.0.9")
    implementation("net.neoforged:AutoRenamingTool:2.0.3")

    val reflectionRewriterVersion = "0.0.3"
    implementation("io.papermc:reflection-rewriter:$reflectionRewriterVersion")
    implementation("io.papermc:reflection-rewriter-runtime:$reflectionRewriterVersion")
    implementation("io.papermc:reflection-rewriter-proxy-generator:$reflectionRewriterVersion")

    implementation("me.lucko:spark-api:0.1-20240720.200737-2")
    implementation("me.lucko:spark-paper:1.10.152")
}

tasks.withType<JavaCompile> {
    val compilerArgs = options.compilerArgs
    compilerArgs.add("--add-modules=jdk.incubator.vector")
    compilerArgs.add("-Xlint:-module")
    compilerArgs.add("-Xlint:-removal")
    compilerArgs.add("-Xlint:-dep-ann")
}

tasks.jar {
    manifest {
        val git = Git(rootProject.layout.projectDirectory.path)
        val mcVersion = rootProject.providers.gradleProperty("mcVersion").get()
        val build = System.getenv("BUILD_NUMBER") ?: null
        val buildTime = Instant.now()
        val gitHash = git.exec(providers, "rev-parse", "--short=7", "HEAD").get().trim()
        val implementationVersion = "$mcVersion-${build ?: "DEV"}-$gitHash"
        val date = git.exec(providers, "show", "-s", "--format=%ci", gitHash).get().trim()
        val gitBranch = git.exec(providers, "rev-parse", "--abbrev-ref", "HEAD").get().trim()
        attributes(
            "Main-Class" to "org.bukkit.craftbukkit.Main",
            "Implementation-Title" to "Mili",
            "Implementation-Version" to implementationVersion,
            "Implementation-Vendor" to date,
            "Specification-Title" to "Mili",
            "Specification-Version" to project.version,
            "Specification-Vendor" to "xucy10",
            "Brand-Id" to "xucy10:mili",
            "Brand-Name" to "Mili",
            "Build-Number" to (build ?: ""),
            "Build-Time" to buildTime.toString(),
            "Git-Branch" to gitBranch,
            "Git-Commit" to gitHash,
        )
        for (tld in setOf("net", "com", "org")) {
            attributes("$tld/bukkit", "Sealed" to true)
        }
    }
}

tasks.compileTestJava {
    options.compilerArgs.add("-parameters")
}

tasks.withType<JavaCompile>().configureEach {
    options.forkOptions.memoryMaximumSize = "1G"
}

val scanJarForBadCalls by tasks.registering(io.papermc.paperweight.tasks.ScanJarForBadCalls::class) {
    badAnnotations.add("Lio/papermc/paper/annotation/DoNotUse;")
    jarToScan.set(tasks.jar.flatMap { it.archiveFile })
    classpath.from(configurations.compileClasspath)
}
tasks.check {
    dependsOn(scanJarForBadCalls)
}

tasks.jar {
    val archiveOperations = services.archiveOperations
    from(alsoShade.elements.map {
        it.map { f ->
            if (f.asFile.isFile) {
                archiveOperations.zipTree(f.asFile)
            } else {
                f.asFile
            }
        }
    })
}

tasks.test {
    include("**/**TestSuite.class")
    workingDir = temporaryDir
    useJUnitPlatform {
        forkEvery = 1
        excludeTags("Slow")
    }

    val provider = objects.newInstance<MockitoAgentProvider>()
    provider.fileCollection.from(mockitoAgent)
    jvmArgumentProviders.add(provider)
}

val generatedDir: java.nio.file.Path = layout.projectDirectory.dir("src/generated/java").asFile.toPath()
idea {
    module {
        generatedSourceDirs.add(generatedDir.toFile())
    }
}
sourceSets {
    main {
        java {
            srcDir(generatedDir)
        }
    }
}

fun TaskContainer.registerRunTask(
    name: String,
    block: JavaExec.() -> Unit
): TaskProvider<JavaExec> = register<JavaExec>(name) {
    group = "runs"
    mainClass.set("org.bukkit.craftbukkit.Main")
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory
        .dir(providers.gradleProperty("paper.runWorkDir").getOrElse("run"))
        .asFile
    javaLauncher.set(project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")

    if (rootProject.childProjects["test-plugin"] != null) {
        val testPluginJar = rootProject.project(":test-plugin").tasks.jar.flatMap { it.archiveFile }
        inputs.file(testPluginJar)
        args("-add-plugin=${testPluginJar.get().asFile.absolutePath}")
    }

    args("--nogui")
    systemProperty("net.kyori.adventure.text.warnWhenLegacyFormattingDetected", true)
    if (providers.gradleProperty("paper.runDisableWatchdog").getOrElse("false") == "true") {
        systemProperty("disable.watchdog", true)
    }
    systemProperty("io.papermc.paper.suppress.sout.nags", true)

    val memoryGb = providers.gradleProperty("paper.runMemoryGb").getOrElse("2")
    minHeapSize = "${memoryGb}G"
    maxHeapSize = "${memoryGb}G"

    doFirst {
        workingDir.mkdirs()
    }

    block(this)
}

tasks.registerRunTask("runServer") {
    description = "Spin up a test server from the Mojang mapped server jar"
    classpath(tasks.includeMappings.flatMap { it.outputJar })
    classpath(configurations.runtimeClasspath)
}

tasks.registerRunTask("runReobfServer") {
    description = "Spin up a test server from the reobfJar output jar"
    classpath(tasks.reobfJar.flatMap { it.outputJar })
    classpath(configurations.runtimeClasspath)
}

tasks.registerRunTask("runDevServer") {
    description = "Spin up a test server without assembling a jar"
    classpath(sourceSets.main.map { it.runtimeClasspath })
}

tasks.registerRunTask("runBundler") {
    description = "Spin up a test server from the Mojang mapped bundler jar"
    classpath(tasks.createMojmapBundlerJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runReobfBundler") {
    description = "Spin up a test server from the reobf bundler jar"
    classpath(tasks.createReobfBundlerJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runPaperclip") {
    description = "Spin up a test server from the Mojang mapped Paperclip jar"
    classpath(tasks.createMojmapPaperclipJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runReobfPaperclip") {
    description = "Spin up a test server from the reobf Paperclip jar"
    classpath(tasks.createReobfPaperclipJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}

fill {
    project("mili")
    versionFamily(paperweight.minecraftVersion.map { it.split(".", "-").takeWhile { part -> part.toIntOrNull() != null }.take(2).joinToString(".") })
    version(paperweight.minecraftVersion)

    build {
        channel = BuildChannel.STABLE

        downloads {
            register("server:default") {
                file = tasks.createMojmapPaperclipJar.flatMap { it.outputZip }
                nameResolver.set { project, _, version, build -> "$project-$version-$build.jar" }
            }
        }
    }
}