plugins {
    `java-library`
}

dependencies {
    // Bukkit API classes come from mili-api (which includes paper-api/folia-api/luminol-api sources)
    compileOnly(project(":mili-api"))
    // Additional dependencies not covered by mili-api
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = 21
}
