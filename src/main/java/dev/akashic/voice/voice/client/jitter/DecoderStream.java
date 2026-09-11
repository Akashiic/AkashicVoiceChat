package dev.akashic.voice.voice.client.jitter;

import java.util.UUID;

import dev.akashic.voice.voice.client.playback.AudioPlayback;
import dev.akashic.voice.voice.client.playback.PlaybackFrame;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusDecoder;

public final class DecoderStream implements AutoCloseable {

    private static final int MAX_DRAIN_PER_CALL = 16;

    private final UUID senderId;
    private final OpusDecoder decoder;
    private final OpusJitterBuffer jitter;
    private final short[] discardPcm = new short[AudioFormatSpec.FRAME_SAMPLES];
    private boolean haveMetadata;
    private boolean lastSpatial;
    private float lastX;
    private float lastY;
    private float lastZ;
    private long lastPacketNanos;
    private long decodeErrorCount;

    public DecoderStream(UUID senderId) {
        this(senderId, OpusCodecFactory.createDecoder(), new OpusJitterBuffer());
    }

    DecoderStream(UUID senderId, OpusDecoder decoder, OpusJitterBuffer jitter) {
        this.senderId = senderId;
        this.decoder = decoder;
        this.jitter = jitter;
    }

    public synchronized void offer(SoundFramePacket packet, long nowNanos, AudioPlayback playback) {
        if (!senderId.equals(packet.senderId)) return;
        boolean accepted = jitter.offer(
            packet.sequence,
            packet.isSpatial(),
            packet.x,
            packet.y,
            packet.z,
            packet.opusData,
            packet.opusOffset,
            packet.opusLength,
            nowNanos);
        if (accepted) lastPacketNanos = nowNanos;
        drain(nowNanos, playback);
    }

    public synchronized void drain(long nowNanos, AudioPlayback playback) {
        for (int i = 0; i < MAX_DRAIN_PER_CALL; i++) {
            OpusJitterBuffer.ReadyKind kind = jitter.next(nowNanos);
            if (kind == OpusJitterBuffer.ReadyKind.NONE) return;
            decodeExpected(kind, playback);
            jitter.consumeExpected();
        }
    }

    private void decodeExpected(OpusJitterBuffer.ReadyKind kind, AudioPlayback playback) {
        PlaybackFrame frame = playback != null ? playback.acquireFrame() : null;
        short[] pcm = frame != null ? frame.pcm() : discardPcm;
        int decoded;
        try {
            if (kind == OpusJitterBuffer.ReadyKind.PACKET) {
                OpusJitterBuffer.Slot slot = jitter.expectedSlot();
                if (slot == null) {
                    if (frame != null) playback.releaseFrame(frame);
                    return;
                }
                decoded = decoder.decode(slot.payload(), 0, slot.length(), pcm);
                if (decoded > 0) {
                    haveMetadata = true;
                    lastSpatial = slot.isSpatial();
                    lastX = slot.x();
                    lastY = slot.y();
                    lastZ = slot.z();
                }
            } else {
                decoded = decoder.decodeMissing(pcm);
            }
        } catch (RuntimeException e) {
            decodeErrorCount++;
            if (frame != null) playback.releaseFrame(frame);
            return;
        }

        if (frame == null) return;
        if (decoded <= 0 || !haveMetadata) {
            playback.releaseFrame(frame);
            return;
        }
        playback.submitFrame(frame, senderId, lastSpatial, lastX, lastY, lastZ);
    }

    public synchronized boolean isIdle(long nowNanos, long timeoutNanos) {
        return lastPacketNanos != 0L && nowNanos - lastPacketNanos >= timeoutNanos;
    }

    public synchronized long decodeErrorCount() {
        return decodeErrorCount;
    }

    public synchronized long duplicateCount() {
        return jitter.duplicateCount();
    }

    public synchronized long lateCount() {
        return jitter.lateCount();
    }

    public synchronized long missingCount() {
        return jitter.missingCount();
    }

    public synchronized long resyncCount() {
        return jitter.resyncCount();
    }

    @Override
    public synchronized void close() {
        jitter.reset();
        decoder.close();
    }
}
