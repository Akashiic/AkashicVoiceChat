import java.nio.ByteBuffer;
import java.util.Random;

import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class WireFuzzSmoke {
    public static void main(String[] args) throws Exception {
        Random random = new Random(0xA5A5C0DEL);
        int parserCases = 50000;
        for (int i = 0; i < parserCases; i++) {
            int len = random.nextInt(1501);
            byte[] data = new byte[len];
            random.nextBytes(data);
            ByteBuffer b = ByteBuffer.wrap(data);
            WireProtocol.peekHeader(b);
            HandshakePackets.readHello(ByteBuffer.wrap(data));
            HandshakePackets.readChallenge(ByteBuffer.wrap(data));
            HandshakePackets.readAuthResponse(ByteBuffer.wrap(data));
            AuthAckPacket.read(ByteBuffer.wrap(data));
            KeepAlivePacket.read(ByteBuffer.wrap(data));
            MicFramePacket.read(ByteBuffer.wrap(data));
            SoundFramePacket.read(ByteBuffer.wrap(data));
        }
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] cn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] sn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(master);
        random.nextBytes(cn);
        random.nextBytes(sn);
        SessionKeys keys = SessionKeys.derive(master, cn, sn);
        SessionCrypto server = SessionCrypto.forServer(keys);
        keys.close();

        int cryptoCases = 5000;
        ByteBuffer plain = ByteBuffer.allocate(1500);
        for (int i = 0; i < cryptoCases; i++) {
            int body = 17 + random.nextInt(128);
            byte[] wire = new byte[WireProtocol.ENCRYPTED_AAD_BYTES + body];
            ByteBuffer w = ByteBuffer.wrap(wire);
            WireProtocol.writeHeader(w, WireProtocol.OUTER_ENCRYPTED);
            w.putLong(i);
            while (w.hasRemaining()) w.put((byte) random.nextInt());
            w.flip();
            if (server.open(w, plain)) {
                throw new AssertionError("random forged GCM packet accepted at " + i);
            }
        }
        server.close();
        System.out.println("wire-fuzz-smoke-ok parser=" + parserCases + " crypto=" + cryptoCases);
    }
}
