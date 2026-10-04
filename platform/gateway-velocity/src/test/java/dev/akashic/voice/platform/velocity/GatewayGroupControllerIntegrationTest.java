package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.stream.Stream;

final class GatewayGroupControllerIntegrationTest {
    @TestFactory
    Stream<DynamicTest> controlAndPersistence() {
        return GatewayGroupControlSmoke.SCENARIOS.keySet().stream()
            .map(name -> DynamicTest.dynamicTest(name, () -> GatewayGroupControlSmoke.run(name)));
    }
}
