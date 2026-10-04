package dev.akashic.voice.voice.client.playback;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import dev.akashic.voice.voice.client.VoiceClient;

/** Runs production queue/reset logic without starting OpenAL or microphone hardware. */
public final class EpochBoundarySmoke {
    private static AudioPlayback output() throws Exception {
        Constructor<AudioPlayback> c = AudioPlayback.class.getDeclaredConstructor();
        c.setAccessible(true);
        AudioPlayback output = c.newInstance();
        Field running = AudioPlayback.class.getDeclaredField("running");
        running.setAccessible(true);
        running.set(output, true);
        return output;
    }
    public static void main(String[] args) throws Exception {
        int failures = 0;
        AudioPlayback output = output();
        UUID sender = new UUID(1L, 2L);
        Field tasks = AudioPlayback.class.getDeclaredField("tasks");
        tasks.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<Runnable> queue = (BlockingQueue<Runnable>) tasks.get(output);
        while (queue.offer(() -> {})) { /* saturate only the general control queue */ }
        PlaybackFrame frame = output.acquireFrame();
        if (!output.submitFrame(frame, sender, false, 0, 0, 0, 0, false, 1L)) {
            throw new AssertionError("fresh frame was not queued");
        }
        output.retireStream(sender, 1L);
        Method accept = AudioPlayback.class.getDeclaredMethod("acceptFrameEpoch", PlaybackFrame.class);
        accept.setAccessible(true);
        if ((Boolean) accept.invoke(output, frame)) {
            failures++;
            System.out.println("FAIL retired frame accepted after control-queue saturation");
        } else System.out.println("PASS retirement survives saturated task queue");
        VoiceClient client = new VoiceClient(new UUID(3L, 4L), new byte[32],
            InetAddress.getLoopbackAddress(), 24454);
        Field playback = VoiceClient.class.getDeclaredField("playback");
        playback.setAccessible(true);
        playback.set(client, output);
        Method reset = VoiceClient.class.getDeclaredMethod("handleSoundReset", UUID.class);
        reset.setAccessible(true);
        try {
            reset.invoke(client, new UUID(5L, 6L));
            System.out.println("PASS unknown legacy reset is harmless");
        } catch (java.lang.reflect.InvocationTargetException error) {
            failures++;
            System.out.println("FAIL unknown legacy reset: " + error.getCause());
        }
        if (failures != 0) throw new AssertionError("epoch boundary failures=" + failures);
        System.out.println("EPOCH_BOUNDARY_OK scenarios=2");
    }
}
