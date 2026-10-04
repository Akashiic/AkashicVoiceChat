package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.akashic.voice.api.VoiceEventListener;
import dev.akashic.voice.api.VoiceModerationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GatewayAddonModerationCommandTest {
    @TempDir Path temp;

    @Test
    void commandPublishesOnlyPersistedModerationChanges() throws Exception {
        GatewayModerationService service = new GatewayModerationService(
            new GatewayModerationStore(temp.resolve("moderation.dat")));
        GatewayAddonApi api = new GatewayAddonApi(failure -> fail(failure));
        api.activate(service::isMuted, service::revision);
        ArrayList<VoiceModerationEvent> events = new ArrayList<>();
        api.listen(new VoiceEventListener() {
            @Override public void onModeration(VoiceModerationEvent event) { events.add(event); }
        });

        GatewayModerationCommand command = new GatewayModerationCommand(
            proxy(), () -> service, () -> null, api);
        UUID target = UUID.randomUUID();

        command.execute(invocation("mute", target.toString()));
        assertTrue(service.isMuted(target));
        assertEquals(1, events.size());
        assertEquals(target, events.get(0).playerId());
        assertTrue(events.get(0).muted());
        assertEquals(1L, events.get(0).revision());

        command.execute(invocation("unmute", target.toString()));
        assertFalse(service.isMuted(target));
        assertEquals(2, events.size());
        assertFalse(events.get(1).muted());
        assertEquals(2L, events.get(1).revision());
    }

    @Test
    void failedPersistenceDoesNotPublishAddonEvent() throws Exception {
        GatewayModerationStore failing = new GatewayModerationStore(
            temp.resolve("failing.dat"), (source, destination) -> {
                throw new java.io.IOException("synthetic persistence failure");
            });
        GatewayModerationService service = new GatewayModerationService(failing);
        GatewayAddonApi api = new GatewayAddonApi(failure -> fail(failure));
        api.activate(service::isMuted, service::revision);
        ArrayList<VoiceModerationEvent> events = new ArrayList<>();
        api.listen(new VoiceEventListener() {
            @Override public void onModeration(VoiceModerationEvent event) { events.add(event); }
        });
        GatewayModerationCommand command = new GatewayModerationCommand(
            proxy(), () -> service, () -> null, api);
        UUID target = UUID.randomUUID();

        command.execute(invocation("mute", target.toString()));
        assertFalse(service.isMuted(target));
        assertTrue(events.isEmpty());
        assertEquals(0L, service.revision());
    }

    private static ProxyServer proxy() {
        return (ProxyServer) Proxy.newProxyInstance(
            GatewayAddonModerationCommandTest.class.getClassLoader(),
            new Class<?>[]{ProxyServer.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getPlayer")) return Optional.empty();
                if (method.getName().equals("toString")) return "AddonApiTestProxy";
                return defaultValue(method.getReturnType());
            });
    }

    private static SimpleCommand.Invocation invocation(String... arguments) {
        CommandSource source = (CommandSource) Proxy.newProxyInstance(
            GatewayAddonModerationCommandTest.class.getClassLoader(),
            new Class<?>[]{CommandSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("hasPermission")) return true;
                if (method.getName().equals("toString")) return "AddonApiTestSource";
                return defaultValue(method.getReturnType());
            });
        return new SimpleCommand.Invocation() {
            @Override public CommandSource source() { return source; }
            @Override public String[] arguments() { return arguments.clone(); }
            @Override public String alias() { return "avvoice"; }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unhandled primitive: " + type);
    }
}
