package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class SessionIdentitySmoke {
    private static int passed;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void pass(String name) { passed++; System.out.println("PASS " + name); }

    public static void main(String[] args) throws Exception {
        handshakeRoundTrip(); pass("session_handshake_roundtrip");
        keyDomainSeparation(); pass("session_key_domain_separation");
        boundCrypto(); pass("session_bound_crypto");
        tamperDoesNotAdvanceReplay(); pass("session_tamper_replay");
        legacyVectorUnchanged(); pass("legacy_vector_unchanged");
        mtuExact(); pass("session_v3_exact_1200");
        System.out.println("SESSION_IDENTITY_OK scenarios=" + passed);
    }

    private static void handshakeRoundTrip() {
        UUID sid = new UUID(11L, 12L), pid = new UUID(21L, 22L);
        byte[] cn = filled(16, 3), sn = filled(16, 4), proof = filled(32, 5);
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, sid, pid, cn);
        check(hello.position() == SessionHandshakePackets.HELLO_BYTES, "hello size");
        SessionHandshakePackets.Hello readHello = SessionHandshakePackets.readHello(flip(hello));
        check(readHello != null && sid.equals(readHello.sessionId) && pid.equals(readHello.playerId), "hello ids");
        check(Arrays.equals(cn, readHello.clientNonce), "hello nonce");

        ByteBuffer challenge = ByteBuffer.allocate(SessionHandshakePackets.CHALLENGE_BYTES);
        SessionHandshakePackets.writeChallenge(challenge, sid, sn, proof);
        SessionHandshakePackets.Challenge readChallenge = SessionHandshakePackets.readChallenge(flip(challenge));
        check(readChallenge != null && sid.equals(readChallenge.sessionId), "challenge session");
        check(Arrays.equals(sn, readChallenge.serverNonce) && Arrays.equals(proof, readChallenge.serverProof), "challenge body");

        ByteBuffer auth = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
        SessionHandshakePackets.writeAuthResponse(auth, sid, proof);
        SessionHandshakePackets.AuthResponse readAuth = SessionHandshakePackets.readAuthResponse(flip(auth));
        check(readAuth != null && sid.equals(readAuth.sessionId) && Arrays.equals(proof, readAuth.clientProof), "auth body");
    }

    private static void keyDomainSeparation() {
        byte[] master=filled(32,7), cn=filled(16,8), sn=filled(16,9);
        UUID a=new UUID(31L,32L), b=new UUID(31L,33L), player=new UUID(41L,42L);
        SessionKeys ka=SessionKeys.derive(master,cn,sn,a), kb=SessionKeys.derive(master,cn,sn,b);
        byte[] pa=HandshakeProof.computeServer(ka,player,cn,sn);
        byte[] pb=HandshakeProof.computeServer(kb,player,cn,sn);
        check(!Arrays.equals(pa,pb), "session ids produced same proof key");
        check(!HandshakeProof.verifyServer(kb,player,cn,sn,pa), "wrong session key verified proof");
        ka.close(); kb.close(); Arrays.fill(pa,(byte)0); Arrays.fill(pb,(byte)0);
    }

    private static void boundCrypto() throws Exception {
        UUID sid=new UUID(51L,52L), wrong=new UUID(51L,53L);
        Pair good=new Pair(sid), bad=new Pair(wrong);
        byte[] wire=good.seal("bound-one");
        check(wire.length == WireProtocol.SESSION_ENCRYPTED_OVERHEAD_BYTES + 9, "bound overhead");
        check(WireProtocol.peekHeader(ByteBuffer.wrap(wire)) == WireProtocol.OUTER_SESSION_ENCRYPTED, "outer type");
        check(!bad.server.open(ByteBuffer.wrap(wire),ByteBuffer.allocate(64)), "wrong session accepted");
        check("bound-one".equals(good.open(wire)), "correct session rejected");
        good.close(); bad.close();
    }

    private static void tamperDoesNotAdvanceReplay() throws Exception {
        Pair pair=new Pair(new UUID(61L,62L));
        byte[] wire=pair.seal("replay-safe");
        byte[] tampered=Arrays.copyOf(wire,wire.length);
        tampered[4] ^= 1; // session UUID is fixed-width AAD and checked before decrypt/replay acceptance.
        check(!pair.server.open(ByteBuffer.wrap(tampered),ByteBuffer.allocate(64)), "tampered session accepted");
        check("replay-safe".equals(pair.open(wire)), "tamper consumed replay counter");
        check(!pair.server.open(ByteBuffer.wrap(wire),ByteBuffer.allocate(64)), "duplicate accepted");
        pair.close();
    }

    private static void legacyVectorUnchanged() throws Exception {
        byte[] master=new byte[32],cn=new byte[16],sn=new byte[16];
        for(int i=0;i<master.length;i++) master[i]=(byte)(i*3+1);
        for(int i=0;i<cn.length;i++) cn[i]=(byte)(0x40+i);
        for(int i=0;i<sn.length;i++) sn[i]=(byte)(0x70+i);
        SessionKeys keys=SessionKeys.derive(master,cn,sn); SessionCrypto crypto=SessionCrypto.forClient(keys); keys.close();
        ByteBuffer plain=ByteBuffer.allocate(64); plain.put("legacy-vector".getBytes("UTF-8"));
        ByteBuffer wire=ByteBuffer.allocate(128); check(crypto.seal(plain,wire),"legacy seal");
        check(hex(Arrays.copyOf(wire.array(),wire.position())).equals(
            "41560104000000000000000086d6d0adcd26ee39e0deeac88045725c028d8639c45dc68d3c7ef63d57"),
            "legacy crypto bytes changed");
        crypto.close();
    }

    private static void mtuExact() throws Exception {
        byte[] opus=new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        SoundFrameV2Packet v2=new SoundFrameV2Packet(new UUID(71L,72L),true,false,1F,2F,3F,48F,7,opus);
        SoundFrameV3Packet v3=new SoundFrameV3Packet(1L,v2);
        check(AudioFormatSpec.MAX_OPUS_PAYLOAD==1108,"session Opus budget");
        check(v3.byteSize()==WireProtocol.MAX_SESSION_ENCRYPTED_PLAINTEXT_BYTES,"session plaintext budget");
        ByteBuffer plain=ByteBuffer.allocate(1500); v3.write(plain);
        Pair pair=new Pair(new UUID(73L,74L)); ByteBuffer wire=ByteBuffer.allocate(1500);
        check(pair.client.seal(plain,wire),"max session v3 seal");
        check(wire.position()==1200,"session v3 wire="+wire.position());
        ByteBuffer opened=ByteBuffer.allocate(1500);
        check(pair.server.open(ByteBuffer.wrap(Arrays.copyOf(wire.array(),wire.position())),opened),"max session v3 open");
        check(opened.remaining()==WireProtocol.MAX_SESSION_ENCRYPTED_PLAINTEXT_BYTES,"opened size");
        pair.close();
    }

    private static byte[] filled(int n,int value) { byte[] out=new byte[n]; Arrays.fill(out,(byte)value); return out; }
    private static ByteBuffer flip(ByteBuffer b) { b.flip(); return b; }
    private static String hex(byte[] data) {
        StringBuilder out=new StringBuilder(); for(byte b:data) out.append(String.format("%02x",b&255)); return out.toString();
    }

    private static final class Pair implements AutoCloseable {
        final UUID sessionId; final SessionCrypto client; final SessionCrypto server;
        Pair(UUID sessionId) {
            this.sessionId=sessionId; byte[] master=filled(32,17),cn=filled(16,31),sn=filled(16,47);
            SessionKeys ck=SessionKeys.derive(master,cn,sn,sessionId), sk=SessionKeys.derive(master,cn,sn,sessionId);
            try { client=SessionCrypto.forClient(ck,sessionId); server=SessionCrypto.forServer(sk,sessionId); }
            finally { ck.close(); sk.close(); Arrays.fill(master,(byte)0); Arrays.fill(cn,(byte)0); Arrays.fill(sn,(byte)0); }
        }
        byte[] seal(String value) throws Exception {
            ByteBuffer plain=ByteBuffer.allocate(64); plain.put(value.getBytes("UTF-8"));
            ByteBuffer wire=ByteBuffer.allocate(128); check(client.seal(plain,wire),"session seal");
            return Arrays.copyOf(wire.array(),wire.position());
        }
        String open(byte[] wire) throws Exception {
            ByteBuffer plain=ByteBuffer.allocate(64); check(server.open(ByteBuffer.wrap(wire),plain),"session open");
            byte[] out=new byte[plain.remaining()]; plain.get(out); return new String(out,"UTF-8");
        }
        public void close() { client.close(); server.close(); }
    }
}
