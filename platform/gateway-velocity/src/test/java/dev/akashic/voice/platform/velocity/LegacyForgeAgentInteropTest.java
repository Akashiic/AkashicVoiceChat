package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LegacyForgeAgentInteropTest {
    @Test
    void java8ForgeAgentSpeaksRealGatewayProtocolBidirectionally() throws Exception {
        Path forgeClasses = Path.of("..", "..", "build", "classes", "java", "main").toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(forgeClasses.resolve(
            "dev/akashic/voice/voice/server/agent/LegacyAgentChannel.class")), "root Forge compileJava must run first");
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        BackendRouteRegistry routes = new BackendRouteRegistry();
        RecordingListener listener = new RecordingListener();
        URL[] urls = { forgeClasses.toUri().toURL() };
        try (URLClassLoader legacy = new URLClassLoader(urls, getClass().getClassLoader());
             AgentHub hub = new AgentHub(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("forbidden-1", psk), routes, listener)) {
            hub.start();
            Class<?> channelType = Class.forName(
                "dev.akashic.voice.voice.server.agent.LegacyAgentChannel", true, legacy);
            Method connect = channelType.getDeclaredMethod(
                "connect", String.class, int.class, String.class, byte[].class, SecureRandom.class);
            connect.setAccessible(true);
            Object channel = connect.invoke(null, "127.0.0.1", hub.localPort(), "forbidden-1", psk, new SecureRandom());
            try {
                assertTrue(listener.up.await(2, TimeUnit.SECONDS));
                BackendRouteRegistry.Route route = routes.readyRoute("forbidden-1");
                assertNotNull(route);

                UUID player = UUID.fromString("11111111-1111-4111-8111-111111111111");
                assertTrue(route.sendAttach(new ManagedPlayer(player, "forbidden-1", "Alice")));
                Object attach = receive(channelType, channel);
                assertEquals(17, byteField(attach, "type"));
                assertEquals(player, field(attach, "playerId"));
                assertEquals("forbidden-1", field(attach, "backendId"));
                assertEquals("Alice", field(attach, "playerName"));
                byte[] opus = { 8, 6, 7, 5, 3, 0, 9 };
                assertTrue(route.sendFrame(new AkashicSpeakerFrame(player, 42L, true, opus)));
                Object frame = receive(channelType, channel);
                assertEquals(19, byteField(frame, "type"));
                assertEquals(player, field(frame, "playerId"));
                assertEquals(42L, longField(frame, "sequence"));
                assertEquals(true, field(frame, "whispering"));
                assertArrayEquals(opus, (byte[]) field(frame, "opus"));

                UUID recipient = UUID.fromString("22222222-2222-4222-8222-222222222222");
                UUID channelId = UUID.fromString("33333333-3333-4333-8333-333333333333");
                Class<?> wireType = Class.forName(
                    "dev.akashic.voice.voice.server.agent.LegacyAgentWire", true, legacy);
                Method deliveryFactory = wireType.getDeclaredMethod("delivery",
                    UUID.class, UUID.class, UUID.class, long.class, boolean.class,
                    float.class, String.class, double.class, double.class, double.class, byte[].class);
                deliveryFactory.setAccessible(true);
                Object delivery = deliveryFactory.invoke(null, recipient, player, channelId, 43L, false,
                    48F, "legacy-dim:0", 1D, 2D, 3D, opus);
                Method send = findSingleArgumentMethod(channelType, "send");
                send.setAccessible(true);
                assertEquals(Boolean.TRUE, send.invoke(channel, delivery));
                assertTrue(listener.delivery.await(2, TimeUnit.SECONDS));
                BridgeDelivery actual = listener.lastDelivery.get();
                assertNotNull(actual);
                assertEquals(recipient, actual.recipientId());
                assertEquals(player, actual.senderId());
                assertEquals(channelId, actual.channelId());
                assertEquals(43L, actual.sequence());
                assertArrayEquals(opus, actual.opusUnsafe());
            } finally {
                Method close = channelType.getDeclaredMethod("close");
                close.setAccessible(true);
                close.invoke(channel);
            }
        } finally {
            routes.close();
            java.util.Arrays.fill(psk, (byte) 0);
        }
    }

    private static Object receive(Class<?> channelType, Object channel) throws Exception {
        Method receive = channelType.getDeclaredMethod("receive");
        receive.setAccessible(true);
        return receive.invoke(channel);
    }

    private static Method findSingleArgumentMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        }
        throw new AssertionError("missing method " + name);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static byte byteField(Object target, String name) throws Exception {
        return ((Byte) field(target, name)).byteValue();
    }

    private static long longField(Object target, String name) throws Exception {
        return ((Long) field(target, name)).longValue();
    }

    private static final class RecordingListener implements AgentHub.Listener {
        final CountDownLatch up = new CountDownLatch(1);
        final CountDownLatch delivery = new CountDownLatch(1);
        final AtomicReference<BridgeDelivery> lastDelivery = new AtomicReference<>();

        @Override
        public void onRouteUp(String backendName) {
            assertEquals("forbidden-1", backendName);
            up.countDown();
        }

        @Override
        public void onRouteDown(String backendName) {
            assertEquals("forbidden-1", backendName);
        }

        @Override
        public void onSvcDelivery(String backendName, BridgeDelivery value) {
            assertEquals("forbidden-1", backendName);
            lastDelivery.set(value);
            delivery.countDown();
        }
        @Override
        public void onSvcReset(String backendName, BridgeStreamReset reset) {
            fail("unexpected reset");
        }

        @Override
        public void onError(String backendName, Throwable error) {
            if (error instanceof java.io.EOFException) return;
            if (error instanceof java.net.SocketException) return;
            fail("unexpected AgentHub error for " + backendName, error);
        }
    }
}
