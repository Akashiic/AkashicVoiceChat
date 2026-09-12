plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "akashic-voice-platform"

include("bridge-core")
include("svc-paper")
include("gateway-velocity")
