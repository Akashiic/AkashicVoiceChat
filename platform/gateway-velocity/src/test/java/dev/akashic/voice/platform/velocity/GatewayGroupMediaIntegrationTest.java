package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.HexFormat;
import java.util.stream.Stream;

/** Actual UDP/agent/controller/store composition; no fake Velocity API classes. */
final class GatewayGroupMediaIntegrationTest {
    @BeforeAll static void fixture() {
        // Synthetic 48 kHz/mono/20 ms fixture generated and decoded with libopus 1.5.2.
        GatewayGroupMediaSmoke.opus = HexFormat.of().parseHex("f8b4efb3df56f7d6c47cfd7d7e4c908cda230cc25be80e1b21351296bdeef8ee654ba6c08c5763dfdeeb2067390367eb467838b0209782f13444982c7f03305908c3f7338b32525fc84a1958715aa7e7ad1d93f5abbf1ae45ec9a4f062f136d2f136d0c4cbc833fc14439f437c79c4439f49872f5089d671546723a9516fc9ef582b4682af1633e24f0ba6a3316955d2b4a74895396b8524eb90e7bf586cb1843845100dab7240f4722f06fecfb161fc8b0690f11468ec47eda5370b7a2a74bd989156a27f7d6d");
    }
    @TestFactory Stream<DynamicTest> realMediaRoutes() {
        return GatewayGroupMediaSmoke.SCENARIOS.entrySet().stream()
            .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> entry.getValue().run()));
    }
}
