plugins {
    `java-library`
}

dependencies {
    api("com.google.guava:guava:33.3.1-jre")
    api("com.google.code.gson:gson:2.11.0")
    api("org.yaml:snakeyaml:2.2")
    api("com.mojang:brigadier:1.3.10")
    compileOnly("org.jetbrains:annotations:26.0.2")
    api(platform("net.kyori:adventure-bom:4.26.1"))
    api("net.kyori:adventure-api")
}

sourceSets {
    main {
        java {
            srcDir("../paper-api/src/main/java")
            srcDir("../folia-api/src/main/java")
            srcDir("src/main/java")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = 21
}
