dependencies {
    api(project(":addon-api"))
    implementation(project(":bridge-core"))
    implementation(project(":client-protocol"))
    implementation(project(":agent-protocol"))
    compileOnly("com.velocitypowered:velocity-api:4.0.0")
    testImplementation("com.velocitypowered:velocity-api:4.0.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.add("-Xlint:-processing")
}
tasks.jar {
    dependsOn(project(":addon-api").tasks.named("jar"))
    dependsOn(project(":bridge-core").tasks.named("jar"))
    dependsOn(project(":client-protocol").tasks.named("jar"))
    dependsOn(project(":agent-protocol").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter {
                it.name.startsWith("addon-api") || it.name.startsWith("bridge-core") || it.name.startsWith("client-protocol")
                    || it.name.startsWith("agent-protocol")
            }
            .map { zipTree(it) }
    })
}
