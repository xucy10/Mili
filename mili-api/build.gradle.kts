import paper.libs.com.google.gson.Gson

plugins {
    `java-library`
    `maven-publish`
    idea
    kotlin("jvm") version "2.3.21"
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}


val annotationsVersion = "26.0.2"
val adventureVersion = "4.26.1"
val bungeeCordChatVersion = "1.21-R0.2-deprecated+build.21"
val slf4jVersion = "2.0.16"
val log4jVersion = "2.24.1"


val apiAndDocs: Configuration = configurations.create("apiAndDocs") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

configurations.api {
    extendsFrom(apiAndDocs)
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

    api("com.google.guava:guava:33.3.1-jre")
    api("com.google.code.gson:gson:2.11.0")
    api("org.yaml:snakeyaml:2.2")

    api("org.joml:joml:1.10.8") {
        isTransitive = false
    }

    api("it.unimi.dsi:fastutil:8.5.15")

    api("org.apache.logging.log4j:log4j-api:$log4jVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")

    api("com.mojang:brigadier:1.3.10")

    api("io.sentry:sentry:8.0.0-rc.2")


    api("net.md-5:bungeecord-chat:$bungeeCordChatVersion") {
        exclude("com.google.guava", "guava")
    }


    apiAndDocs(platform("net.kyori:adventure-bom:$adventureVersion"))
    apiAndDocs("net.kyori:adventure-api")
    apiAndDocs("net.kyori:adventure-text-minimessage")
    apiAndDocs("net.kyori:adventure-text-serializer-gson")
    apiAndDocs("net.kyori:adventure-text-serializer-legacy")
    apiAndDocs("net.kyori:adventure-text-serializer-plain")
    apiAndDocs("net.kyori:adventure-text-logger-slf4j")


    api("org.apache.maven:maven-resolver-provider:3.9.6")

    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")


    compileOnly("org.jetbrains:annotations:$annotationsVersion")

    compileOnlyApi("org.checkerframework:checker-qual:3.49.2")

    api("org.jspecify:jspecify:1.0.0")


    testImplementation("org.apache.commons:commons-lang3:3.17.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.mockito:mockito-core:5.14.1")
    testImplementation("org.ow2.asm:asm-tree:9.8")

    mockitoAgent("org.mockito:mockito-core:5.14.1") {
        isTransitive = false
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


/*
 * 修复 API 编译：
 * Bukkit / Paper / Folia API 源码来自 sourceSets，
 * 但生成源码需要加入编译路径
 */

val generatedDir =
    rootProject.layout.projectDirectory
        .dir("paper-api/src/generated/java")
        .asFile
        .toPath()


sourceSets {

    main {

        java {

            srcDir(generatedDir)

            srcDir("../paper-api/src/main/java")

            srcDir("../folia-api/src/main/java")

            srcDir("src/main/java")
        }


        resources {

            srcDir("../paper-api/src/main/resources")

            srcDir("../folia-api/src/main/resources")

            srcDir("src/main/resources")
        }
    }
}


/*
 * GitHub Packages
 */

publishing {

    publications {

        create<MavenPublication>("maven") {

            from(components["java"])


            groupId = "com.xucy10.mili"

            artifactId = "mili-api"

            version = project.version.toString()
        }
    }


    repositories {

        maven {

            name = "GitHubPackages"


            url = uri(
                "https://maven.pkg.github.com/xucy10/Mili"
            )


            credentials {

                username =
                    System.getenv("GITHUB_ACTOR")


                password =
                    System.getenv("GITHUB_TOKEN")
            }
        }
    }
}



/*
 * The API source set aggregates paper-api, folia-api and mili-api sources.
 * The upstream fork chain materializes files such as
 * gg/pufferfish/pufferfish/sentry/SentryContext.java in more than one of those
 * roots, so jar packaging tasks must tolerate duplicates. compileJava is
 * unaffected; without this, :mili-api:sourcesJar fails during publish with
 * "Entry ... is a duplicate but no duplicate handling strategy has been set".
 */
tasks.withType<Jar>().configureEach {

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {

    manifest {

        attributes(
            "Automatic-Module-Name" to "org.bukkit"
        )
    }
}



tasks.test {

    useJUnitPlatform()
}



tasks.withType<JavaCompile> {

    options.compilerArgs.add(
        "--add-modules=jdk.incubator.vector"
    )

    options.compilerArgs.add("-Xlint:-module")
    options.compilerArgs.add("-Xlint:-removal")
    options.compilerArgs.add("-Xlint:-dep-ann")
}