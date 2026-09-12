dependencies {
    implementation(project(":bridge-core"))
    implementation(project(":client-protocol"))
    compileOnly("com.velocitypowered:velocity-api:4.0.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.jar {
    dependsOn(project(":bridge-core").tasks.named("jar"))
    dependsOn(project(":client-protocol").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("bridge-core") || it.name.startsWith("client-protocol") }
            .map { zipTree(it) }
    })
}
