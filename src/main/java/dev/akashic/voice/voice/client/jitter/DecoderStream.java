package dev.akashic.voice.voice.client.jitter;

import java.util.UUID;

import dev.akashic.voice.voice.client.playback.AudioPlayback;
import dev.akashic.voice.voice.client.playback.PlaybackFrame;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusDecoder;

public final class DecoderStream implements AutoCloseable {

    private static final int MAX_DRAIN_PER_CALL = 16;

    private final UUID senderId;
    private long playbackEpoch;
    private final OpusDecoder decoder;
    private final OpusJitterBuffer jitter;
    private final short[] discardPcm = new short[AudioFormatSpec.FRAME_SAMPLES];
    private boolean haveMetadata;
    private boolean lastSpatial;
    private float lastX;
    private float lastY;
    private float lastZ;
    private float lastMaxDistance = Float.NaN;
    private boolean lastWhispering;
    private long lastPacketNanos;
    private long decodeErrorCount;
    private boolean closed;
    private Object listeningIdentity;
    private Object speakerListeningIdentity;

    public DecoderStream(UUID senderId) {
        this(senderId, 0L);
    }

    public DecoderStream(UUID senderId, long playbackEpoch) {
        this(senderId, OpusCodecFactory.createDecoder(), new OpusJitterBuffer());
        if (playbackEpoch < 0) throw new IllegalArgumentException("Invalid playback epoch");
        this.playbackEpoch = playbackEpoch;
    }

    public long playbackEpoch() {
        return playbackEpoch;
    }

    DecoderStream(UUID senderId, OpusDecoder decoder, OpusJitterBuffer jitter) {
        this.senderId = senderId;
        this.decoder = decoder;
        this.jitter = jitter;
    }

    public synchronized void offer(SoundFramePacket packet, long nowNanos, AudioPlayback playback) {
        if (closed || !senderId.equals(packet.senderId)) return;
        offer(
            packet.sequence,
            packet.isSpatial(),
            packet.x,
            packet.y,
            packet.z,
            Float.NaN,
            false,
            packet.opusData,
            packet.opusOffset,
            packet.opusLength,
            nowNanos,
            playback);
    }

    public synchronized void offer(SoundFrameV2Packet packet, long nowNanos, AudioPlayback playback) {
        if (closed || !senderId.equals(packet.senderId)) return;
        offer(
            packet.sequence,
            packet.isSpatial(),
            packet.x,
            packet.y,
            packet.z,
            packet.maxDistance,
            packet.isWhispering(),
            packet.opusData,
            packet.opusOffset,
            packet.opusLength,
            nowNanos,
            playback);
    }

    private void offer(int sequence, boolean spatial, float x, float y, float z, float maxDistance, boolean whispering,
        byte[] opus, int offset, int length, long nowNanos, AudioPlayback playback) {
        reconcilePlaybackBoundary(playback, nowNanos);
        boolean accepted = jitter
            .offer(sequence, spatial, x, y, z, maxDistance, whispering, opus, offset, length, nowNanos);
        if (accepted) lastPacketNanos = nowNanos;
        drain(nowNanos, playback);
    }

    public synchronized void drain(long nowNanos, AudioPlayback playback) {
        if (closed) return;
        reconcilePlaybackBoundary(playback, nowNanos);
        drainBuffered(nowNanos, playback);
    }

    private void reconcilePlaybackBoundary(AudioPlayback playback, long nowNanos) {
        if (playback == null) return;
        Object nextListening = playback.listeningIdentity();
        Object nextSpeaker = playback.speakerListeningIdentity(senderId);
        if (listeningIdentity != null
            && (listeningIdentity != nextListening || speakerListeningIdentity != nextSpeaker)) {
            // Drain the bounded encoded backlog into discard PCM; keep decoder and sequence state.
            // The future timestamp only releases held jitter slots. This does not sleep or reschedule audio.
            drainBuffered(nowNanos + 1_000_000_000L, null);
        }
        listeningIdentity = nextListening;
        speakerListeningIdentity = nextSpeaker;
    }

    private void drainBuffered(long nowNanos, AudioPlayback playback) {
        for (int i = 0; i < MAX_DRAIN_PER_CALL; i++) {
            OpusJitterBuffer.ReadyKind kind = jitter.next(nowNanos);
            if (kind == OpusJitterBuffer.ReadyKind.NONE) return;
            decodeExpected(kind, playback);
            jitter.consumeExpected();
        }
    }

    private void decodeExpected(OpusJitterBuffer.ReadyKind kind, AudioPlayback playback) {
        PlaybackFrame frame = playback != null ? playback.acquireFrame(senderId) : null;
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
                    lastMaxDistance = slot.maxDistance();
                    lastWhispering = slot.isWhispering();
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
        playback.submitFrame(
            frame,
            senderId,
            lastSpatial,
            lastX,
            lastY,
            lastZ,
            lastMaxDistance,
            lastWhispering,
            playbackEpoch);
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
        if (closed) return;
        closed = true;
        jitter.reset();
        decoder.close();
    }
}
