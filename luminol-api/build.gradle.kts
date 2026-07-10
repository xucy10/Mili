plugins {
    `java-library`
}

dependencies {
    // bukkit api classes from mili-api
    implementation(project(":mili-api"))
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = 21
}
