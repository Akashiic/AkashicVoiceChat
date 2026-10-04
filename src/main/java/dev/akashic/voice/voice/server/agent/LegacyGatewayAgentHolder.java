package dev.akashic.voice.voice.server.agent;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;

public final class LegacyGatewayAgentHolder {

    private static volatile LegacyGatewayAgent instance;

    private LegacyGatewayAgentHolder() {}

    public static synchronized boolean startConfigured() {
        stop();
        byte[] psk = null;
        try {
            String encoded = AkashicVoiceConfig.agentPskBase64();
            if (encoded == null || encoded.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("agent.pskBase64 is required in agent mode");
            }
            psk = Base64.getDecoder()
                .decode(encoded.trim());
            if (psk.length != LegacyAgentWire.PSK_BYTES) {
                throw new IllegalArgumentException("agent.pskBase64 must decode to exactly 32 bytes");
            }
            LegacyGatewayAgent created = new LegacyGatewayAgent(
                AkashicVoiceConfig.agentBackendId(),
                AkashicVoiceConfig.agentGatewayHost(),
                AkashicVoiceConfig.agentGatewayPort(),
                psk);
            instance = created;
            created.start();
            return true;
        } catch (RuntimeException failure) {
            instance = null;
            AkashicVoice.LOG.error(
                "Akashic Voice agent mode is fail-closed: direct UDP was not started because Agent configuration is invalid",
                failure);
            return false;
        } finally {
            if (psk != null) Arrays.fill(psk, (byte) 0);
        }
    }

    public static synchronized void stop() {
        LegacyGatewayAgent current = instance;
        instance = null;
        if (current != null) current.close();
    }

    public static boolean isConnected() {
        LegacyGatewayAgent current = instance;
        return current != null && current.isConnected();
    }

    public static int managedPlayers() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.managedPlayers() : 0;
    }

    public static long reconnectCount() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.reconnectCount() : 0L;
    }

    public static long deliveryCount() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.deliveryCount() : 0L;
    }

    public static long resetCount() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.resetCount() : 0L;
    }

    public static long droppedMediaFrames() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.droppedMediaFrames() : 0L;
    }

    public static int queuedMediaMessages() {
        LegacyGatewayAgent current = instance;
        return current != null ? current.queuedMediaMessages() : 0;
    }

    public static void updateLocalIdentity(String playerName, UUID localId) {
        LegacyGatewayAgent current = instance;
        if (current != null) current.updateLocalIdentity(playerName, localId);
    }

    public static void removeLocalIdentity(String playerName, UUID localId) {
        LegacyGatewayAgent current = instance;
        if (current != null) current.removeLocalIdentity(playerName, localId);
    }
}
