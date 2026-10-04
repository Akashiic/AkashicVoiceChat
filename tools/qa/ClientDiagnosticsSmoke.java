package dev.akashic.voice.voice.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Aggregate diagnostics contract; opens no socket, GUI, microphone or audio device. */
public final class ClientDiagnosticsSmoke {
    private ClientDiagnosticsSmoke() {}

    public static void main(String[] args) throws Exception {
        disconnectedIsNonSensitive();
        saturatedDecoderStateIsVisibleAndImmutable();
        System.out.println("CLIENT_DIAGNOSTICS_OK cap=" + VoiceClient.MAX_DECODER_STREAMS);
    }

    private static void disconnectedIsNonSensitive() {
        VoiceClientStatusSnapshot status = VoiceClientStatusSnapshot.disconnected();
        check(!status.isPresent(), "disconnected snapshot claims client presence");
        check(!status.isAuthenticated(), "disconnected snapshot claims authentication");
        check(status.receivedFrames() == 0L && status.decoderCapacityDrops() == 0L, "non-zero empty counters");
        for (Field field : VoiceClientStatusSnapshot.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            check(type != UUID.class && type != byte[].class, "snapshot exposes sensitive identity/material: " + field);
        }
    }

    private static void saturatedDecoderStateIsVisibleAndImmutable() throws Exception {
        byte[] secret = new byte[SessionKeys.MASTER_SECRET_BYTES];
        java.util.Arrays.fill(secret, (byte) 23);
        VoiceClient client = new VoiceClient(new UUID(7L, 7L), secret, InetAddress.getLoopbackAddress(), 9);
        Method streamFor = method("streamFor", UUID.class);
        Method reset = method("handleSoundReset", UUID.class);
        try {
            VoiceClientStatusSnapshot initial = client.statusSnapshot();
            check(initial.isPresent() && !initial.isAuthenticated(), "initial status state wrong");
            check(initial.activeDecoderStreams() == 0 && initial.maxDecoderStreams() == 256, "initial stream counts wrong");

            UUID first = null;
            for (int i = 0; i < VoiceClient.MAX_DECODER_STREAMS; i++) {
                UUID id = new UUID(30L, i + 1L);
                if (i == 0) first = id;
                check(streamFor.invoke(client, id) != null, "stream allocation failed at " + i);
            }
            check(streamFor.invoke(client, new UUID(31L, 1L)) == null, "overflow stream accepted");
            VoiceClientStatusSnapshot saturated = client.statusSnapshot();
            check(saturated.activeDecoderStreams() == 256, "saturated stream count wrong");
            check(saturated.decoderCapacityDrops() == 1L, "drop counter not surfaced");

            reset.invoke(client, first);
            VoiceClientStatusSnapshot afterReset = client.statusSnapshot();
            check(afterReset.activeDecoderStreams() == 255, "reset not reflected");
            check(saturated.activeDecoderStreams() == 256, "published snapshot mutated after reset");
        } finally {
            client.stop();
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = VoiceClient.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
