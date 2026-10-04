package dev.akashic.voice.voice.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.jitter.DecoderStream;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Bounded expensive decoder-state contract; opens no socket, Minecraft window or audio device. */
public final class DecoderStreamCapacitySmoke {
    private DecoderStreamCapacitySmoke() {}

    public static void main(String[] args) throws Exception {
        String backend = AkashicVoiceConfig.opusBackend();
        AkashicVoiceConfig.setOpusBackend("java");
        try {
            boundedAndRecoverable();
            concurrentCreationNeverExceedsCap();
        } finally {
            AkashicVoiceConfig.setOpusBackend(backend);
        }
        System.out.println("DECODER_STREAM_CAPACITY_OK cap=" + VoiceClient.MAX_DECODER_STREAMS);
    }

    private static void boundedAndRecoverable() throws Exception {
        VoiceClient client = client();
        Method streamFor = method("streamFor", UUID.class);
        Method reset = method("handleSoundReset", UUID.class);
        Method clear = method("clearDecoderStreams");
        Map<UUID, DecoderStream> streams = streams(client);
        Set<Object> decoders = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        UUID first = null;
        DecoderStream firstStream = null;
        try {
            for (int i = 0; i < VoiceClient.MAX_DECODER_STREAMS; i++) {
                UUID id = new UUID(1L, i + 1L);
                DecoderStream stream = (DecoderStream) streamFor.invoke(client, id);
                check(stream != null, "capacity rejected slot " + i);
                check(decoders.add(field(stream, "decoder")), "decoder state shared at " + i);
                if (i == 0) { first = id; firstStream = stream; }
            }
            check(streams.size() == VoiceClient.MAX_DECODER_STREAMS, "wrong map size at cap");
            check(decoders.size() == VoiceClient.MAX_DECODER_STREAMS, "decoder identities not isolated");

            UUID overflow = new UUID(2L, 1L);
            check(streamFor.invoke(client, overflow) == null, "overflow stream allocated");
            check(client.decoderStreamCapacityDropCount() == 1L, "overflow not counted");
            check(streamFor.invoke(client, first) == firstStream, "existing stream rejected at cap");
            check(client.decoderStreamCapacityDropCount() == 1L, "existing stream counted as drop");

            for (int i = 0; i < 4096; i++) {
                check(streamFor.invoke(client, new UUID(3L, i + 1L)) == null, "flood exceeded decoder cap");
            }
            check(streams.size() == VoiceClient.MAX_DECODER_STREAMS, "flood grew decoder map");
            check(client.decoderStreamCapacityDropCount() == 4097L, "flood drop count mismatch");

            reset.invoke(client, first);
            check(streams.size() == VoiceClient.MAX_DECODER_STREAMS - 1, "reset did not release decoder slot");
            DecoderStream replacement = (DecoderStream) streamFor.invoke(client, overflow);
            check(replacement != null, "released slot was not reusable");
            check(streams.size() == VoiceClient.MAX_DECODER_STREAMS, "replacement cardinality wrong");
            check(field(replacement, "decoder") != field(firstStream, "decoder"), "reset reused retired decoder state");
        } finally {
            clear.invoke(client);
            check(streams.isEmpty(), "clear left decoder streams behind");
        }
    }

    private static void concurrentCreationNeverExceedsCap() throws Exception {
        final VoiceClient client = client();
        final Method streamFor = method("streamFor", UUID.class);
        final Method clear = method("clearDecoderStreams");
        final Map<UUID, DecoderStream> streams = streams(client);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        java.util.List<Thread> threads = new java.util.ArrayList<Thread>();
        try {
            for (int threadIndex = 0; threadIndex < 8; threadIndex++) {
                final int owner = threadIndex;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 128; i++) {
                            streamFor.invoke(client, new UUID(100L + owner, i + 1L));
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    }
                }, "AkashicVoice-QA-DecoderCap-" + threadIndex);
                thread.setDaemon(true);
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join(30_000L);
                check(!thread.isAlive(), "concurrent creator stuck");
            }
            if (failure.get() != null) throw new AssertionError("creator failed", failure.get());
            check(streams.size() == VoiceClient.MAX_DECODER_STREAMS,
                "concurrency exceeded/undershot cap: " + streams.size());
            check(client.decoderStreamCapacityDropCount() == 8L * 128L - VoiceClient.MAX_DECODER_STREAMS,
                "concurrent drop accounting mismatch");
        } finally {
            clear.invoke(client);
        }
    }

    private static VoiceClient client() throws Exception {
        byte[] secret = new byte[SessionKeys.MASTER_SECRET_BYTES];
        ArraysFill.fill(secret, (byte) 17);
        return new VoiceClient(new UUID(9L, 9L), secret, InetAddress.getLoopbackAddress(), 9);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DecoderStream> streams(VoiceClient client) throws Exception {
        return (Map<UUID, DecoderStream>) field(client, "decoderStreams");
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = VoiceClient.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class ArraysFill {
        static void fill(byte[] value, byte fill) { java.util.Arrays.fill(value, fill); }
    }
}
