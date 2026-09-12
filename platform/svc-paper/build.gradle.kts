dependencies {
    implementation(project(":bridge-core"))
    implementation(project(":client-protocol"))
    implementation(project(":agent-protocol"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.20")
    testImplementation("de.maxhenkel.voicechat:voicechat-api:2.6.20")
}

tasks.jar {
    dependsOn(project(":bridge-core").tasks.named("jar"))
    dependsOn(project(":client-protocol").tasks.named("jar"))
    dependsOn(project(":agent-protocol").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter {
                it.name.startsWith("bridge-core")
                    || it.name.startsWith("client-protocol")
                    || it.name.startsWith("agent-protocol")
            }
            .map { zipTree(it) }
    })
}
