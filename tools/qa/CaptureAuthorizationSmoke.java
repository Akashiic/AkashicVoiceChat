package dev.akashic.voice.voice.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.akashic.voice.voice.client.audio.capture.*;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

/** Production capture worker, deterministic barriers; no physical audio or game startup. */
public final class CaptureAuthorizationSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed, failed;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) {
        try { scenario.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }
    public static void main(String[] args) throws Exception {
        MicGate.Mode mode = MicGate.mode(); boolean muted = MicGate.isMuted();
        boolean ptt = MicGate.isPttPressed(), deaf = MicGate.isDeafened();
        try {
            test("mute_during_encode_cancels_frame", () -> changeDuringEncode(() -> MicGate.setMuted(true), 0));
            test("rapid_mute_unmute_does_not_revive_frame", () -> changeDuringEncode(() -> {
                MicGate.setMuted(true); MicGate.setMuted(false);
            }, 0));
            test("mode_roundtrip_cancels_old_frame", () -> changeDuringEncode(() -> {
                MicGate.setMode(MicGate.Mode.PTT); MicGate.setMode(MicGate.Mode.ALWAYS);
            }, 0));
            test("ptt_release_cancels_frame", () -> pttChange(false));
            test("ptt_release_repress_cancels_frame", () -> pttChange(true));
            test("unchanged_settings_keep_frame", () -> changeDuringEncode(() -> {
                MicGate.setMode(MicGate.Mode.ALWAYS); MicGate.setMuted(false); MicGate.setPttPressed(false);
            }, 1));
            test("deafen_does_not_implicitly_mute_microphone", () -> changeDuringEncode(() -> MicGate.setDeafened(true), 1));
            test("mute_during_device_read_cancels_frame", () -> changeDuringRead(false));
            test("fast_mute_during_device_read_cancels_frame", () -> changeDuringRead(true));
            test("capture_started_muted_does_not_publish_old_frame", CaptureAuthorizationSmoke::startedMuted);
            test("fresh_frame_recovers_after_cancellation", CaptureAuthorizationSmoke::recovers);
            test("normal_capture_sequence_unchanged", CaptureAuthorizationSmoke::normal);
        } finally {
            MicGate.setMode(mode); MicGate.setMuted(muted);
            MicGate.setPttPressed(ptt); MicGate.setDeafened(deaf);
        }
        System.out.println("CAPTURE_AUTH_RESULT passed=" + passed + " failed=" + failed);
        if (failed != 0) throw new AssertionError("capture authorization failures=" + failed);
    }
    static void defaults() {
        MicGate.setMode(MicGate.Mode.ALWAYS); MicGate.setMuted(false);
        MicGate.setPttPressed(false); MicGate.setDeafened(false);
    }
    static void changeDuringEncode(Runnable change, int expected) throws Exception {
        defaults();
        try (Fixture f = new Fixture(1, false, true)) {
            f.start(); f.encoder.awaitEntered(); change.run(); f.encoder.release.countDown(); f.await();
            check(f.sequences.size() == expected, "unexpected sent frames=" + f.sequences.size());
            f.checkClosed();
        }
    }
    static void pttChange(boolean pressAgain) throws Exception {
        defaults(); MicGate.setMode(MicGate.Mode.PTT); MicGate.setPttPressed(true);
        try (Fixture f = new Fixture(1, false, true)) {
            f.start(); f.encoder.awaitEntered(); MicGate.setPttPressed(false);
            if (pressAgain) MicGate.setPttPressed(true);
            f.encoder.release.countDown(); f.await();
            check(f.sequences.isEmpty(), "released PTT frame escaped");
        }
    }
    static void changeDuringRead(boolean unmute) throws Exception {
        defaults();
        try (Fixture f = new Fixture(1, true, false)) {
            f.start(); f.device.awaitEntered(); MicGate.setMuted(true);
            if (unmute) MicGate.setMuted(false);
            f.device.release.countDown(); f.await();
            check(f.sequences.isEmpty(), "frame crossing read authorization escaped");
        }
    }
    static void startedMuted() throws Exception {
        defaults(); MicGate.setMuted(true);
        try (Fixture f = new Fixture(1, true, false)) {
            f.start(); f.device.awaitEntered(); MicGate.setMuted(false);
            f.device.release.countDown(); f.await();
            check(f.sequences.isEmpty(), "frame acquired while muted was later authorized");
        }
    }
    static void recovers() throws Exception {
        defaults();
        try (Fixture f = new Fixture(2, false, true)) {
            f.start(); f.encoder.awaitEntered(); MicGate.setMuted(true); MicGate.setMuted(false);
            f.encoder.release.countDown(); f.await();
            check(f.sequences.equals(Arrays.asList(0)), "cancelled frame consumed sequence or resumed incorrectly: " + f.sequences);
            check(f.encoder.calls.get() == 2, "fresh frame not encoded after cancellation");
        }
    }
    static void normal() throws Exception {
        defaults();
        try (Fixture f = new Fixture(3, false, false)) {
            f.start(); f.await(); check(f.sequences.equals(Arrays.asList(0,1,2)), "normal capture sequence changed");
            f.checkClosed();
        }
    }
    static void barrier(CountDownLatch latch) {
        try { check(latch.await(10, TimeUnit.SECONDS), "test barrier timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    static final class Device implements AudioCaptureDevice {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger closes = new AtomicInteger();
        final int frames; final boolean blocked; int reads;
        Device(int frames, boolean blocked) { this.frames=frames; this.blocked=blocked; }
        public int read(byte[] bytes, int offset, int length) {
            if (reads++ == 0 && blocked) { entered.countDown(); barrier(release); }
            if (reads > frames) return -1;
            Arrays.fill(bytes,offset,offset+length,(byte)31); return length;
        }
        void awaitEntered() { barrier(entered); }
        public String backendName() { return "qa"; }
        public String description() { return "barrier device"; }
        public void close() { closes.incrementAndGet(); release.countDown(); }
    }
    static final class Encoder implements OpusEncoder {
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        final AtomicInteger calls=new AtomicInteger(), closes=new AtomicInteger();
        final boolean blocked;
        Encoder(boolean blocked) { this.blocked=blocked; }
        public int encode(short[] pcm,byte[] out) {
            if (calls.incrementAndGet()==1 && blocked) { entered.countDown(); barrier(release); }
            out[0]=7; return 1;
        }
        void awaitEntered() { barrier(entered); }
        public void close() { closes.incrementAndGet(); }
    }
    static final class Fixture implements AutoCloseable {
        final Device device; final Encoder encoder; final MicCaptureSession session;
        final List<Integer> sequences=new CopyOnWriteArrayList<Integer>();
        final MicCapture mic;
        final AtomicReference<Throwable> failure=new AtomicReference<Throwable>(); Thread worker;
        Fixture(int count, boolean readBlock, boolean encodeBlock) {
            this(count, readBlock, encodeBlock, null);
        }
        Fixture(int count, boolean readBlock, boolean encodeBlock, MicCapture.FrameSink target) {
            mic=new MicCapture(target != null ? target : (sequence,payload,length)->sequences.add(sequence));
            device=new Device(count,readBlock);encoder=new Encoder(encodeBlock);
            session=new MicCaptureSession(device,encoder);
        }
        void start() throws Exception {
            final Method loop=MicCapture.class.getDeclaredMethod("loop",MicCaptureSession.class);
            loop.setAccessible(true);
            worker=new Thread(()->{
                try { loop.invoke(mic,session); }
                catch (InvocationTargetException e) { failure.set(e.getCause()); }
                catch (Throwable e) { failure.set(e); }
            },"AkashicVoice-QA-Authorization");worker.setDaemon(true);worker.start();
        }
        void await() throws Exception {
            worker.join(10000);check(!worker.isAlive(),"capture worker stuck");
            if (failure.get()!=null)throw new AssertionError("worker failed",failure.get());
        }
        void checkClosed() {
            check(device.closes.get()==1 && encoder.closes.get()==1,"capture resources not closed once");
        }
        public void close() throws Exception {
            device.release.countDown();encoder.release.countDown();
            if(worker!=null)await();else session.finish();
        }
    }
}
