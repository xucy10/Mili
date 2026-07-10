plugins {
    `java-library`
}

dependencies {
    api("com.google.guava:guava:33.3.1-jre")
    api("com.google.code.gson:gson:2.11.0")
    api("org.yaml:snakeyaml:2.2")
    api("it.unimi.dsi:fastutil:8.5.15")
    api("com.mojang:brigadier:1.3.10")
    api(platform("net.kyori:adventure-bom:4.26.1"))
    api("net.kyori:adventure-api")
    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.jetbrains:annotations:26.0.2")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
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
