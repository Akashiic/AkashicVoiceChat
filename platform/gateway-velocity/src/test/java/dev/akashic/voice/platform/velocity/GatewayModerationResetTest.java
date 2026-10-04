package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV3Packet;
import dev.akashic.voice.voice.common.SoundResetV3Packet;
import dev.akashic.voice.voice.common.VoiceCapabilities;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayModerationResetTest {
    @Test
    void hardMuteRetiresCurrentEpochAndNextFrameGetsNewEpoch() throws Exception {
        UUID sender = UUID.randomUUID(), source = UUID.randomUUID(), session = UUID.randomUUID();
        try (CryptoPair pair = new CryptoPair(session);
             DatagramSocket transport = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             GatewayVoiceConnection connection = new GatewayVoiceConnection(UUID.randomUUID(), "Receiver", "lobby",
                 UUID.randomUUID(), receiver.getLocalSocketAddress(), pair.server)) {
            receiver.setSoTimeout(700);
            assertTrue(connection.offerCapabilities(transport, VoiceCapabilities.STREAM_EPOCHS));
            ByteBuffer offered = pair.open(receive(receiver));
            assertEquals(WirePacketType.CAPABILITIES_ACK, offered.get());
            SoundFramePacket frame = new SoundFramePacket(sender, false, 0F,0F,0F, 1, new byte[] {1,2,3});
            assertTrue(connection.sendSound(transport, frame, source));
            ByteBuffer firstPlain = pair.open(receive(receiver));
            assertEquals(WirePacketType.SOUND_FRAME_V3, firstPlain.get());
            SoundFrameV3Packet first = SoundFrameV3Packet.read(firstPlain);
            assertNotNull(first);

            assertTrue(connection.sendHardReset(transport, sender));
            ByteBuffer resetPlain = pair.open(receive(receiver));
            assertEquals(WirePacketType.SOUND_RESET_V3, resetPlain.get());
            SoundResetV3Packet reset = SoundResetV3Packet.read(resetPlain);
            assertNotNull(reset);
            assertEquals(sender, reset.senderId);
            assertEquals(first.epoch, reset.epoch);
            assertFalse(connection.sendHardReset(transport, sender), "retired stream reset twice");

            assertTrue(connection.sendSound(transport, frame, source));
            ByteBuffer nextPlain = pair.open(receive(receiver));
            assertEquals(WirePacketType.SOUND_FRAME_V3, nextPlain.get());
            SoundFrameV3Packet next = SoundFrameV3Packet.read(nextPlain);
            assertNotNull(next);
            assertTrue(next.epoch > first.epoch, "unmuted stream reused retired epoch");
        }
    }

    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[1500], 1500);
        socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset()+packet.getLength()));
    }

    private static final class CryptoPair implements AutoCloseable {
        final SessionCrypto client;
        final SessionCrypto server;
        CryptoPair(UUID session) {
            byte[] secret = new byte[32], cn = new byte[16], sn = new byte[16];
            Arrays.fill(secret, (byte) 7); Arrays.fill(cn, (byte) 9); Arrays.fill(sn, (byte) 11);
            SessionKeys keys = SessionKeys.derive(secret, cn, sn, session);
            try {
                client = SessionCrypto.forClient(keys, session);
                server = SessionCrypto.forServer(keys, session);
            } finally {
                keys.close(); Arrays.fill(secret,(byte)0); Arrays.fill(cn,(byte)0); Arrays.fill(sn,(byte)0);
            }
        }
        ByteBuffer open(ByteBuffer wire) {
            ByteBuffer plain = ByteBuffer.allocate(1500);
            assertTrue(client.open(wire, plain), "moderation packet failed AEAD");
            return plain;
        }
        @Override public void close() { client.close(); }
    }
}
