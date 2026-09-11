import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class HandshakeProtocolSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID id = new UUID(0x1234L, 0x5678L);
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] proof = new byte[HandshakeProof.PROOF_BYTES];
        for (int i = 0; i < clientNonce.length; i++) clientNonce[i] = (byte) i;
        for (int i = 0; i < serverNonce.length; i++) serverNonce[i] = (byte) (0x40 + i);
        for (int i = 0; i < proof.length; i++) proof[i] = (byte) (0x70 + i);

        ByteBuffer hello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
        HandshakePackets.writeHello(hello, id, clientNonce);
        hello.flip();
        HandshakePackets.Hello parsedHello = HandshakePackets.readHello(hello);
        require(parsedHello != null, "hello parse");
        require(id.equals(parsedHello.playerId), "hello player id");
        require(Arrays.equals(clientNonce, parsedHello.clientNonce), "hello nonce");

        ByteBuffer challenge = ByteBuffer.allocate(HandshakePackets.CHALLENGE_BYTES);
        HandshakePackets.writeChallenge(challenge, serverNonce, proof);
        challenge.flip();
        HandshakePackets.Challenge parsedChallenge = HandshakePackets.readChallenge(challenge);
        require(parsedChallenge != null, "challenge parse");
        require(Arrays.equals(serverNonce, parsedChallenge.serverNonce), "challenge nonce");
        require(Arrays.equals(proof, parsedChallenge.serverProof), "challenge proof");

        ByteBuffer response = ByteBuffer.allocate(HandshakePackets.AUTH_RESPONSE_BYTES);
        HandshakePackets.writeAuthResponse(response, proof);
        response.flip();
        HandshakePackets.AuthResponse parsedResponse = HandshakePackets.readAuthResponse(response);
        require(parsedResponse != null, "auth response parse");
        require(Arrays.equals(proof, parsedResponse.clientProof), "auth response proof");

        ByteBuffer trailing = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES + 1);
        HandshakePackets.writeHello(trailing, id, clientNonce);
        trailing.put((byte) 0).flip();
        require(HandshakePackets.readHello(trailing) == null, "trailing hello accepted");

        byte[] validHello = new byte[HandshakePackets.HELLO_BYTES];
        ByteBuffer validHelloBuf = ByteBuffer.wrap(validHello);
        HandshakePackets.writeHello(validHelloBuf, id, clientNonce);

        byte[] badMagic = Arrays.copyOf(validHello, validHello.length);
        badMagic[0] ^= 1;
        require(HandshakePackets.readHello(ByteBuffer.wrap(badMagic)) == null, "bad magic accepted");

        byte[] badVersion = Arrays.copyOf(validHello, validHello.length);
        badVersion[2] = (byte) (WireProtocol.VERSION + 1);
        require(HandshakePackets.readHello(ByteBuffer.wrap(badVersion)) == null, "bad version accepted");

        byte[] badType = Arrays.copyOf(validHello, validHello.length);
        badType[3] = 99;
        require(WireProtocol.peekHeader(ByteBuffer.wrap(badType)) == -1, "unknown outer type accepted");

        System.out.println("handshake-protocol-smoke-ok");
    }
}
