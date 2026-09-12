sourceSets {
    main {
        java.setSrcDirs(listOf("../../shared/client-protocol/src/main/java"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}
