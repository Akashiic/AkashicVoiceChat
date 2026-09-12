import java.security.MessageDigest

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

repositories {
    maven("https://repo.plasmoverse.com/releases")
}

val nativeOpusBundle by configurations.creating

val verifyNativeOpusBundle by tasks.registering {
    inputs.files(nativeOpusBundle)
    inputs.property("expectedSha256", "e4d67fb9aa3158ed5bd17536b78cea20f85862144e7c1cdb1d6f5972cf031c30")
    doLast {
        val files = inputs.files.files
        val expectedSha256 = inputs.properties["expectedSha256"] as String
        check(files.size == 1) { "Expected exactly one native Opus artifact, got ${files.size}" }
        val file = files.single()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        check(actual == expectedSha256) {
            "Native Opus SHA-256 mismatch: expected=$expectedSha256 actual=$actual file=${file.name}"
        }
    }
}

dependencies {
    // JNI classes must retain their original package names: native symbols bind to them.
    compileOnly("com.plasmoverse:opus-jni-rust:1.0.4")
    nativeOpusBundle("com.plasmoverse:opus-jni-rust:1.0.4")
}

tasks.named<org.gradle.jvm.tasks.Jar>("shadowJar") {
    dependsOn(verifyNativeOpusBundle)
    // Add the native codec after Shadow's dependency relocation pipeline so JNI names stay intact.
    from({ nativeOpusBundle.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}

// Shared Java 8 wire/crypto source consumed by both the legacy mod and the Java 21/25 platform.
sourceSets {
    main {
        java.srcDir("shared/client-protocol/src/main/java")
    }
}
