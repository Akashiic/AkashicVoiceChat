package dev.akashic.voice.voice.server;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class VoiceServerTelemetrySmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void test(String name, Scenario body) throws Exception {
        body.run();
        passed++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws Exception {
        test("empty_snapshot_has_zero_counters", VoiceServerTelemetrySmoke::empty);
        test("all_counters_accumulate", VoiceServerTelemetrySmoke::accumulate);
        test("concurrent_updates_are_not_lost", VoiceServerTelemetrySmoke::concurrent);
        test("snapshot_is_immutable_value", VoiceServerTelemetrySmoke::immutable);
        test("negative_inputs_are_rejected", VoiceServerTelemetrySmoke::negative);
        test("delivery_total_matches_categories", VoiceServerTelemetrySmoke::deliveryTotal);
        test("snapshot_contains_no_sensitive_field_shapes", VoiceServerTelemetrySmoke::noSensitiveFields);
        test("large_counts_remain_long", VoiceServerTelemetrySmoke::largeCounts);
        System.out.println("VOICE_SERVER_TELEMETRY_OK scenarios=" + passed);
    }

    static void empty() {
        VoiceServerMetricsSnapshot s = new VoiceServerTelemetry().snapshot(false, 24454, 0, 0);
        check(!s.running() && s.udpPort() == 24454 && s.activeSessions() == 0 && s.pendingHandshakes() == 0,
            "bad live fields");
        check(s.datagramsReceived() == 0 && s.bytesReceived() == 0 && s.deliveries() == 0
            && s.sessionsOpened() == 0, "nonzero initial metrics");
    }

    static void accumulate() {
        VoiceServerTelemetry t = new VoiceServerTelemetry();
        t.received(10);
        t.received(20);
        t.sent(7);
        t.helloRateLimited();
        t.mediaRateLimited();
        t.encryptedRejected();
        t.handshakeRejected();
        t.micAccepted(33);
        t.spatialDelivery();
        t.groupDelivery();
        t.selfEchoDelivery();
        t.sessionOpened();
        t.sessionClosed();
        t.sessionTimedOut();
        VoiceServerMetricsSnapshot s = t.snapshot(true, 24454, 1, 2);
        check(s.datagramsReceived() == 2 && s.bytesReceived() == 30 && s.datagramsSent() == 1
            && s.bytesSent() == 7, "traffic counts");
        check(s.helloRateLimited() == 1 && s.mediaRateLimited() == 1 && s.encryptedRejected() == 1
            && s.handshakeRejected() == 1, "reject counts");
        check(s.micFramesAccepted() == 1 && s.micBytesAccepted() == 33 && s.deliveries() == 3,
            "media counts");
        check(s.sessionsOpened() == 1 && s.sessionsClosed() == 1 && s.sessionsTimedOut() == 1,
            "session counts");
    }

    static void concurrent() throws Exception {
        final VoiceServerTelemetry t = new VoiceServerTelemetry();
        final int threads = 8;
        final int iterations = 25_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int id = 0; id < threads; id++) {
            Thread worker = new Thread(() -> {
                try {
                    check(start.await(5, TimeUnit.SECONDS), "start timeout");
                    for (int i = 0; i < iterations; i++) {
                        t.received(3);
                        t.sent(4);
                        t.micAccepted(5);
                        t.spatialDelivery();
                        if ((i & 1) == 0) t.groupDelivery();
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    done.countDown();
                }
            }, "AV-Telemetry-QA");
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        check(done.await(15, TimeUnit.SECONDS), "workers timeout");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
        long count = (long) threads * iterations;
        VoiceServerMetricsSnapshot s = t.snapshot(true, 1, 0, 0);
        check(s.datagramsReceived() == count && s.bytesReceived() == count * 3, "received update loss");
        check(s.datagramsSent() == count && s.bytesSent() == count * 4, "sent update loss");
        check(s.micFramesAccepted() == count && s.micBytesAccepted() == count * 5, "mic update loss");
        check(s.spatialDeliveries() == count && s.groupDeliveries() == (long) threads * ((iterations + 1) / 2),
            "delivery update loss");
    }

    static void immutable() {
        VoiceServerTelemetry t = new VoiceServerTelemetry();
        t.received(9);
        VoiceServerMetricsSnapshot before = t.snapshot(true, 7, 1, 2);
        t.received(10);
        t.sessionOpened();
        check(before.datagramsReceived() == 1 && before.bytesReceived() == 9 && before.sessionsOpened() == 0,
            "snapshot changed after publication");
        VoiceServerMetricsSnapshot after = t.snapshot(true, 7, 2, 3);
        check(after.datagramsReceived() == 2 && after.bytesReceived() == 19 && after.sessionsOpened() == 1,
            "new snapshot stale");
    }

    static void negative() {
        VoiceServerTelemetry t = new VoiceServerTelemetry();
        for (Runnable action : Arrays.<Runnable>asList(
            () -> t.received(-1), () -> t.sent(-1), () -> t.micAccepted(-1))) {
            try {
                action.run();
                throw new AssertionError("negative bytes accepted");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
        check(t.snapshot(false, 0, 0, 0).datagramsReceived() == 0,
            "failed event mutated telemetry");
        try {
            t.snapshot(true, -1, 0, 0);
            throw new AssertionError("negative port accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    static void deliveryTotal() {
        VoiceServerTelemetry t = new VoiceServerTelemetry();
        for (int i = 0; i < 2; i++) t.spatialDelivery();
        for (int i = 0; i < 3; i++) t.groupDelivery();
        for (int i = 0; i < 4; i++) t.selfEchoDelivery();
        VoiceServerMetricsSnapshot s = t.snapshot(true, 0, 0, 0);
        check(s.deliveries() == 9 && s.spatialDeliveries() == 2 && s.groupDeliveries() == 3
            && s.selfEchoDeliveries() == 4, "delivery totals");
    }

    static void noSensitiveFields() {
        for (Field field : VoiceServerMetricsSnapshot.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            check(type == boolean.class || type == int.class || type == long.class,
                "high-cardinality/sensitive field: " + field);
            String name = field.getName().toLowerCase();
            check(!name.contains("uuid") && !name.contains("address") && !name.contains("secret")
                && !name.contains("key"), "sensitive field name: " + name);
        }
    }

    static void largeCounts() throws Exception {
        VoiceServerTelemetry t = new VoiceServerTelemetry();
        Field field = VoiceServerTelemetry.class.getDeclaredField("bytesReceived");
        field.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong value = (java.util.concurrent.atomic.AtomicLong) field.get(t);
        value.set((long) Integer.MAX_VALUE + 123L);
        t.received(1);
        check(t.snapshot(true, 0, 0, 0).bytesReceived() == (long) Integer.MAX_VALUE + 124L,
            "counter truncated to int");
    }
}
