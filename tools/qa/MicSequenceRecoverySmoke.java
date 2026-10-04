package dev.akashic.voice.voice.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.client.jitter.OpusJitterBuffer;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Actual VoiceClient -> encrypted loopback UDP; no game, microphone or OpenAL startup. */
public final class MicSequenceRecoverySmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        captureRestartKeepsTransportSequence();
        unauthenticatedCaptureDoesNotConsumeSequence();
        signedSequenceWrapRemainsOrdered();
        System.out.println("mic-sequence-recovery-smoke-ok: 3 scenarios");
    }

    private static void captureRestartKeepsTransportSequence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            int[] captureSequences = {0, 1, 2, 0, 1, 0, 1, 2, 3};
            OpusJitterBuffer jitter = new OpusJitterBuffer(8, 2, 40_000_000L);
            for (int i = 0; i < captureSequences.length; i++) {
                MicFramePacket packet = fixture.frame(captureSequences[i]);
                require(packet.sequence == i, "capture restart reused media sequence: expected "
                    + i + " but got " + packet.sequence);
                long now = 1L + i * 20_000_000L;
                require(jitter.offer(packet.sequence, true, 0, 0, 0,
                    packet.opusData, packet.opusOffset, packet.opusLength, now),
                    "recovered microphone frame rejected by live jitter state");
                while (jitter.next(now) != OpusJitterBuffer.ReadyKind.NONE) jitter.consumeExpected();
            }
            require(jitter.lateCount() == 0, "microphone restart produced late audio");
        }
    }

    private static void unauthenticatedCaptureDoesNotConsumeSequence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.set("authenticated", false);
            fixture.send(99);
            require(fixture.clientCrypto.nextSendCounter() == 0, "unauthenticated frame encrypted");
            fixture.expectSilence();
            fixture.set("authenticated", true);
            require(fixture.frame(123).sequence == 0, "capture-local sequence leaked onto wire");
        }
    }

    private static void signedSequenceWrapRemainsOrdered() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.set("nextMicSequence", Integer.MAX_VALUE - 1);
            int expected = Integer.MAX_VALUE - 1;
            OpusJitterBuffer jitter = new OpusJitterBuffer(8, 2, 40_000_000L);
            for (int i = 0; i < 5; i++) {
                MicFramePacket packet = fixture.frame(0);
                require(packet.sequence == expected++, "signed sequence wrap changed order");
                long now = 1L + i * 20_000_000L;
                require(jitter.offer(packet.sequence, true, 0, 0, 0,
                    packet.opusData, packet.opusOffset, packet.opusLength, now), "wrap rejected");
                while (jitter.next(now) != OpusJitterBuffer.ReadyKind.NONE) jitter.consumeExpected();
            }
        }
    }

    static final class Fixture implements AutoCloseable {
        final DatagramSocket receiver;
        final DatagramSocket sender;
        final VoiceClient client;
        final SessionCrypto clientCrypto;
        final SessionCrypto serverCrypto;
        final MicCapture.FrameSink sink;
        final MicGate.Mode previousMode = MicGate.mode();
        final boolean previousMute = MicGate.isMuted();
        final byte[] opus = {1, 2, 3, 4};

        Fixture() throws Exception {
            MicGate.setMode(MicGate.Mode.ALWAYS);
            MicGate.setMuted(false);
            InetAddress loopback = InetAddress.getLoopbackAddress();
            receiver = new DatagramSocket(0, loopback);
            receiver.setSoTimeout(2_000);
            sender = new DatagramSocket(0, loopback);
            sender.connect(loopback, receiver.getLocalPort());
            byte[] secret = new byte[SessionKeys.MASTER_SECRET_BYTES];
            byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            Arrays.fill(secret, (byte) 17);
            try (SessionKeys keys = SessionKeys.derive(secret, nonce, nonce)) {
                clientCrypto = SessionCrypto.forClient(keys);
                serverCrypto = SessionCrypto.forServer(keys);
            }
            client = new VoiceClient(new UUID(1L, 2L), secret, loopback, receiver.getLocalPort());
            Arrays.fill(secret, (byte) 0);
            set("socket", sender);
            set("crypto", clientCrypto);
            set("authenticated", true);
            Field outbound = VoiceClient.class.getDeclaredField("outboundPacket");
            outbound.setAccessible(true);
            ((DatagramPacket) outbound.get(client)).setSocketAddress(receiver.getLocalSocketAddress());
            Method create = VoiceClient.class.getDeclaredMethod("createCaptureSink", SessionCrypto.class);
            create.setAccessible(true);
            sink = (MicCapture.FrameSink) create.invoke(client, clientCrypto);
        }

        void set(String name, Object value) throws Exception {
            Field field = VoiceClient.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(client, value);
        }

        void send(int captureSequence) throws Exception {
            sink.sendAuthorizedFrame(captureSequence, opus, opus.length, MicGate.transmissionState());
        }

        MicFramePacket frame(int captureSequence) throws Exception {
            send(captureSequence);
            return receiveFrame();
        }

        MicFramePacket receiveFrame() throws Exception {
            DatagramPacket wire = new DatagramPacket(new byte[1500], 1500);
            receiver.receive(wire);
            ByteBuffer plain = ByteBuffer.allocate(1500);
            require(serverCrypto.open(ByteBuffer.wrap(wire.getData(), 0, wire.getLength()), plain),
                "encrypted microphone frame rejected");
            require(plain.get() == WirePacketType.MIC_FRAME, "wrong packet type");
            MicFramePacket packet = MicFramePacket.read(plain);
            require(packet != null && packet.opusLength == opus.length, "corrupt payload");
            return packet;
        }

        void expectSilence() throws Exception {
            receiver.setSoTimeout(100);
            try {
                receiver.receive(new DatagramPacket(new byte[1500], 1500));
                throw new AssertionError("unauthenticated microphone was sent");
            } catch (SocketTimeoutException expected) {
                // Silence is required before UDP authentication.
            } finally {
                receiver.setSoTimeout(2_000);
            }
        }

        @Override
        public void close() {
            MicGate.setMode(previousMode);
            MicGate.setMuted(previousMute);
            sender.close();
            receiver.close();
            clientCrypto.close();
            serverCrypto.close();
        }
    }
}
