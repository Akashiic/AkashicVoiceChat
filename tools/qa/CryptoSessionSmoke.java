import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.HkdfSha256;
import dev.akashic.voice.voice.common.crypto.ReplayWindow;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class CryptoSessionSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static byte[] hex(String value) {
        int n = value.length();
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return out;
    }

    private static void testHkdfVector() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = hex("000102030405060708090a0b0c");
        byte[] info = hex("f0f1f2f3f4f5f6f7f8f9");
        byte[] expectedPrk = hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");
        byte[] expectedOkm = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
        byte[] prk = HkdfSha256.extract(salt, ikm);
        byte[] okm = HkdfSha256.expand(prk, info, 42);
        require(Arrays.equals(expectedPrk, prk), "RFC5869 PRK mismatch");
        require(Arrays.equals(expectedOkm, okm), "RFC5869 OKM mismatch");
    }

    private static SessionKeys deriveKeys() {
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] cn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] sn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < master.length; i++) master[i] = (byte) (i * 3 + 1);
        for (int i = 0; i < cn.length; i++) cn[i] = (byte) (0x40 + i);
        for (int i = 0; i < sn.length; i++) sn[i] = (byte) (0x70 + i);
        return SessionKeys.derive(master, cn, sn);
    }

    private static void testReplayWindow() {
        ReplayWindow rw = new ReplayWindow();
        require(rw.accept(10), "accept first");
        require(rw.accept(12), "accept forward");
        require(rw.accept(11), "accept out of order");
        require(!rw.accept(11), "reject duplicate");
        require(!rw.accept(10), "reject duplicate old");
        require(rw.accept(100), "accept large jump");
        require(!rw.accept(12), "reject outside replay window");
    }

    private static void testProof() {
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] cn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] sn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        Arrays.fill(master, (byte) 7);
        Arrays.fill(cn, (byte) 3);
        Arrays.fill(sn, (byte) 9);
        SessionKeys keys = SessionKeys.derive(master, cn, sn);
        UUID id = new UUID(1L, 2L);
        byte[] proof = HandshakeProof.computeServer(keys, id, cn, sn);
        require(HandshakeProof.verifyServer(keys, id, cn, sn, proof), "valid server proof");
        require(!HandshakeProof.verifyClient(keys, id, cn, sn, proof), "server proof accepted as client proof");
        proof[0] ^= 1;
        require(!HandshakeProof.verifyServer(keys, id, cn, sn, proof), "tampered proof accepted");
        keys.close();
    }

    private static byte[] seal(SessionCrypto crypto, String value) throws Exception {
        ByteBuffer plain = ByteBuffer.allocate(256);
        plain.put(value.getBytes("UTF-8"));
        ByteBuffer wire = ByteBuffer.allocate(512);
        require(crypto.seal(plain, wire), "seal failed");
        return Arrays.copyOf(wire.array(), wire.position());
    }

    private static String open(SessionCrypto crypto, byte[] wire) throws Exception {
        ByteBuffer input = ByteBuffer.wrap(wire);
        ByteBuffer plain = ByteBuffer.allocate(256);
        require(crypto.open(input, plain), "open failed");
        byte[] out = new byte[plain.remaining()];
        plain.get(out);
        return new String(out, "UTF-8");
    }

    private static void testDirectionalCrypto() throws Exception {
        SessionKeys clientKeys = deriveKeys();
        SessionKeys serverKeys = deriveKeys();
        SessionCrypto client = SessionCrypto.forClient(clientKeys);
        SessionCrypto server = SessionCrypto.forServer(serverKeys);
        clientKeys.close();
        serverKeys.close();

        byte[] c2s = seal(client, "client-to-server");
        require("client-to-server".equals(open(server, c2s)), "C2S mismatch");
        require(!server.open(ByteBuffer.wrap(c2s), ByteBuffer.allocate(256)), "replay accepted");
        byte[] second = seal(client, "tamper-check");
        byte[] tampered = Arrays.copyOf(second, second.length);
        tampered[tampered.length - 1] ^= 1;
        require(!server.open(ByteBuffer.wrap(tampered), ByteBuffer.allocate(256)), "tampered packet accepted");
        require("tamper-check".equals(open(server, second)), "bad tag advanced replay window");

        byte[] s2c = seal(server, "server-to-client");
        require("server-to-client".equals(open(client, s2c)), "S2C mismatch");
        require(client.highestReceivedCounter() == 0L, "unexpected client receive counter");
        require(server.highestReceivedCounter() == 1L, "unexpected server receive counter");

        client.close();
        server.close();
    }

    public static void main(String[] args) throws Exception {
        testHkdfVector();
        testReplayWindow();
        testProof();
        testDirectionalCrypto();
        System.out.println("crypto-session-smoke-ok");
    }
}
