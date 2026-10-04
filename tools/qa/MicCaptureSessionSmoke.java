package dev.akashic.voice.voice.client.audio.capture;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.akashic.voice.voice.client.MicCapture;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

/** Runs the actual capture worker with fake hardware, never a physical microphone. */
public final class MicCaptureSessionSmoke {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        MicGate.Mode previous = MicGate.mode();
        boolean muted = MicGate.isMuted();
        MicGate.setMode(MicGate.Mode.ALWAYS);
        MicGate.setMuted(false);
        try {
            stopDuringEncodeDefersDisposal();
            encodingFailureEndsWorker();
            emptyReadsAndEndOfDeviceCloseCleanly();
            oldGenerationCannotCloseReplacement();
            cleanupFailureStillDisposesBothResources();
        } finally {
            MicGate.setMode(previous);
            MicGate.setMuted(muted);
        }
        System.out.println("mic-capture-session-smoke-ok: 5 scenarios");
    }

    private static void stopDuringEncodeDefersDisposal() throws Exception {
        try (Harness harness = new Harness()) {
            harness.encoder.block = true;
            harness.start();
            require(harness.encoder.entered.await(2, TimeUnit.SECONDS), "encoder did not start");
            harness.mic.stop();
            require(harness.encoder.closes.get() == 0, "encoder disposed while encode was in flight");
            require(harness.device.closes.get() == 1, "capture not closed exactly once by stop");
            harness.encoder.release.countDown();
            harness.await();
            require(harness.encoder.closes.get() == 1, "worker did not dispose encoder");
            require(harness.frames.get() == 0, "cancelled frame escaped to transport");
            require(harness.session.isFinished(), "capture generation not finished");
        }
    }

    private static void encodingFailureEndsWorker() throws Exception {
        try (Harness harness = new Harness()) {
            harness.encoder.failEncode = true;
            harness.start();
            harness.await();
            require(!harness.mic.isRunning(), "broken encoder kept capture marked running");
            require(harness.encoder.encodes.get() == 1, "failed encoder retried in a hot loop");
            require(harness.device.closes.get() == 1 && harness.encoder.closes.get() == 1,
                "encode failure leaked capture resources");
        }
    }

    private static void emptyReadsAndEndOfDeviceCloseCleanly() throws Exception {
        try (Harness harness = new Harness()) {
            harness.device.emptyReads = 2;
            harness.start();
            harness.await();
            require(harness.device.reads.get() == 3, "unexpected empty-read/EOF handling");
            require(harness.encoder.encodes.get() == 0, "partial audio frame was encoded");
            require(harness.device.closes.get() == 1 && harness.encoder.closes.get() == 1,
                "EOF leaked microphone resources");
        }
    }

    private static void oldGenerationCannotCloseReplacement() {
        FakeCapture oldDevice = new FakeCapture();
        FakeCapture newDevice = new FakeCapture();
        FakeEncoder oldEncoder = new FakeEncoder();
        FakeEncoder newEncoder = new FakeEncoder();
        MicCaptureSession oldSession = new MicCaptureSession(oldDevice, oldEncoder);
        MicCaptureSession replacement = new MicCaptureSession(newDevice, newEncoder);
        try {
            oldSession.requestStop();
            oldSession.finish();
            oldSession.finish();
            require(replacement.isRunning(), "old cleanup stopped replacement");
            require(newDevice.closes.get() == 0 && newEncoder.closes.get() == 0,
                "old cleanup disposed replacement resources");
            require(oldDevice.closes.get() == 1 && oldEncoder.closes.get() == 1,
                "old generation double-closed resources");
        } finally {
            replacement.finish();
        }
    }

    private static void cleanupFailureStillDisposesBothResources() {
        FakeCapture device = new FakeCapture();
        FakeEncoder encoder = new FakeEncoder();
        device.failClose = true;
        encoder.failClose = true;
        MicCaptureSession session = new MicCaptureSession(device, encoder);
        try {
            session.finish();
            throw new AssertionError("close failures were hidden");
        } catch (IllegalStateException expected) {
            require("capture-close".equals(expected.getMessage()), "lost first cleanup failure");
            require(expected.getSuppressed().length == 1, "lost encoder cleanup failure");
        }
        session.finish();
        session.requestStop();
        require(session.isFinished(), "failed cleanup left generation active");
        require(device.closes.get() == 1 && encoder.closes.get() == 1, "cleanup was not idempotent");
    }

    private static final class Harness implements AutoCloseable {
        final FakeCapture device = new FakeCapture();
        final FakeEncoder encoder = new FakeEncoder();
        final MicCaptureSession session = new MicCaptureSession(device, encoder);
        final AtomicInteger frames = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final MicCapture mic = new MicCapture((sequence, payload, length) -> frames.incrementAndGet());
        Thread worker;

        void start() throws Exception {
            final Method loop = MicCapture.class.getDeclaredMethod("loop", MicCaptureSession.class);
            loop.setAccessible(true);
            worker = new Thread(() -> {
                try {
                    loop.invoke(mic, session);
                } catch (InvocationTargetException error) {
                    failure.set(error.getCause());
                } catch (Throwable error) {
                    failure.set(error);
                }
            }, "AkashicVoice-QA-Capture");
            worker.setDaemon(true);
            Field active = MicCapture.class.getDeclaredField("session");
            active.setAccessible(true);
            active.set(mic, session);
            Field thread = MicCapture.class.getDeclaredField("thread");
            thread.setAccessible(true);
            thread.set(mic, worker);
            worker.start();
        }

        void await() throws Exception {
            worker.join(3_000);
            require(!worker.isAlive(), "capture worker did not terminate");
            if (failure.get() != null) throw new AssertionError("capture worker failed", failure.get());
        }

        @Override
        public void close() throws Exception {
            mic.stop();
            encoder.release.countDown();
            if (worker != null) await();
            else session.finish();
        }
    }

    private static final class FakeCapture implements AudioCaptureDevice {
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        volatile boolean closed;
        int emptyReads = -1;
        boolean failClose;

        @Override
        public int read(byte[] bytes, int offset, int length) {
            reads.incrementAndGet();
            if (closed) return -1;
            if (emptyReads == 0) return -1;
            if (emptyReads > 0) {
                emptyReads--;
                return 0;
            }
            Arrays.fill(bytes, offset, offset + length, (byte) 1);
            return length;
        }

        @Override
        public String backendName() { return "qa"; }
        @Override
        public String description() { return "fake microphone"; }
        @Override
        public void close() {
            closes.incrementAndGet();
            closed = true;
            if (failClose) throw new IllegalStateException("capture-close");
        }
    }

    private static final class FakeEncoder implements OpusEncoder {
        final AtomicInteger encodes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean encoding;
        boolean block;
        boolean failEncode;
        boolean failClose;

        @Override
        public int encode(short[] pcm, byte[] out) {
            encodes.incrementAndGet();
            encoding = true;
            entered.countDown();
            try {
                if (block) {
                    // The harness explicitly releases this native-call simulation.
                    // A clock deadline here would race slow logger/JVM initialization.
                    // Harness cleanup + the outer QA process timeout bound execution.
                    while (release.getCount() != 0) {
                        try {
                            release.await(50, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ignored) {
                            // Deliberately model an in-flight native call that cannot be interrupted.
                        }
                    }
                }
                if (failEncode) throw new IllegalStateException("synthetic encoder failure");
                out[0] = 1;
                return 1;
            } finally {
                encoding = false;
            }
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            require(!encoding, "encoder closed while encode was executing");
            if (failClose) throw new IllegalStateException("encoder-close");
        }
    }
}
