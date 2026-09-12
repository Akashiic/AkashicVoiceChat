dependencies {
    implementation(project(":bridge-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.20")
    testImplementation("de.maxhenkel.voicechat:voicechat-api:2.6.20")
}

tasks.jar {
    dependsOn(project(":bridge-core").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("bridge-core") }
            .map { zipTree(it) }
    })
}
