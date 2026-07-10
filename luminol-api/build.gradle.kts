plugins {
    java-library
}

dependencies {
    implementation(project(":mili-api"))
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.jetbrains:annotations:26.0.2")
}

sourceSets {
    main {
        java { srcDir("src/main/java") }
    }
}

tasks.withType<JavaCompile> {
    options.release = 21
    options.encoding = Charsets.UTF_8.name()
}
