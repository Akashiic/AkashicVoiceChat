package dev.jm.proxychat.voice.client.playback;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

import dev.jm.proxychat.ProxyChat;

public final class AudioPlayback {

    private static volatile AudioPlayback instance;

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final Map<UUID, PlaybackChannel> channels = new HashMap<>();
    private Thread thread;
    private volatile boolean running;
    private volatile float maxDistance = 48f;
    private volatile float referenceDistance = 4f;
    private volatile float listenerGain = 3f;
    private boolean weCreatedAl;

    private AudioPlayback() {}

    public static synchronized AudioPlayback start() {
        if (instance != null) return instance;
        AudioPlayback p = new AudioPlayback();
        p.startInternal();
        instance = p;
        return p;
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.stopInternal();
        instance = null;
    }

    public static AudioPlayback get() {
        return instance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    public void setReferenceDistance(float referenceDistance) {
        this.referenceDistance = referenceDistance;
    }

    public void setListenerGain(float gain) {
        if (gain == this.listenerGain) return;
        this.listenerGain = gain;
        if (running) {
            tasks.offer(() -> AL10.alListenerf(AL10.AL_GAIN, gain));
        }
    }

    public void submitFrame(UUID senderId, float x, float y, float z, short[] pcm) {
        if (!running) return;
        tasks.offer(() -> {
            PlaybackChannel ch = channels.get(senderId);
            if (ch == null) {
                ch = new PlaybackChannel(referenceDistance);
                ch.setMaxDistance(maxDistance);
                channels.put(senderId, ch);
            }
            ch.setPosition(x, y, z);
            ch.enqueue(pcm);
        });
    }

    public void updateListener(float x, float y, float z, float pitchRad, float yawRad) {
        if (!running) return;
        tasks.offer(() -> {
            AL10.alListener3f(AL10.AL_POSITION, x, y, z);

            float cosPitch = (float) Math.cos(pitchRad);
            float lookX = -(float) Math.sin(yawRad) * cosPitch;
            float lookY = -(float) Math.sin(pitchRad);
            float lookZ = (float) Math.cos(yawRad) * cosPitch;

            FloatBuffer ori = BufferUtils.createFloatBuffer(6);
            ori.put(lookX)
                .put(lookY)
                .put(lookZ)
                .put(0f)
                .put(1f)
                .put(0f)
                .flip();
            AL10.alListener(AL10.AL_ORIENTATION, ori);
        });
    }

    public void dropChannel(UUID senderId) {
        if (!running) return;
        tasks.offer(() -> {
            PlaybackChannel ch = channels.remove(senderId);
            if (ch != null) ch.close();
        });
    }

    private void startInternal() {
        running = true;
        thread = new Thread(this::loop, "ProxyChat-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopInternal() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        try {
            initOpenAl();
        } catch (Throwable t) {
            ProxyChat.LOG.error("OpenAL init failed; voice playback disabled", t);
            running = false;
            return;
        }

        try {
            while (running) {
                Runnable task = tasks.poll(50, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                try {
                    task.run();
                } catch (Throwable t) {
                    ProxyChat.LOG.warn("Audio task failed", t);
                }
            }
        } catch (InterruptedException ignored) {
            // shutting down
        } finally {
            shutdownOpenAl();
        }
    }

    private void initOpenAl() throws Exception {
        if (!AL.isCreated()) {
            AL.create();
            weCreatedAl = true;
            ProxyChat.LOG.info("OpenAL created by ProxyChat");
        } else {
            ProxyChat.LOG.info("OpenAL already initialized; reusing existing context");
        }
        AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
        AL10.alListenerf(AL10.AL_GAIN, listenerGain);
    }

    private void shutdownOpenAl() {
        for (PlaybackChannel ch : channels.values()) {
            try {
                ch.close();
            } catch (Throwable ignored) {
                // ignored
            }
        }
        channels.clear();

        if (weCreatedAl && AL.isCreated()) {
            try {
                AL.destroy();
            } catch (Throwable t) {
                ProxyChat.LOG.debug("AL.destroy failed", t);
            }
        }
    }
}
