plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "akashic-voice-platform"

include("client-protocol")
include("bridge-core")
include("svc-paper")
include("gateway-velocity")
