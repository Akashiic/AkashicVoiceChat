import java.nio.ByteBuffer;
import java.util.UUID;

import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.AuthenticatePacket;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;

public final class WireParserSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(AuthAckPacket.read(ByteBuffer.allocate(0)) == null, "short auth ack");
        require(KeepAlivePacket.read(ByteBuffer.allocate(7)) == null, "short keepalive");
        require(AuthenticatePacket.read(ByteBuffer.allocate(31)) == null, "short authenticate");

        ByteBuffer oversizedMic = ByteBuffer.allocate(6);
        oversizedMic.putInt(1).putShort((short) 65535).flip();
        require(MicFramePacket.read(oversizedMic) == null, "oversized mic payload must be rejected");

        ByteBuffer validMic = ByteBuffer.allocate(9);
        validMic.putInt(2).putShort((short) 3).put(new byte[] {1, 2, 3}).flip();
        MicFramePacket mic = MicFramePacket.read(validMic);
        require(mic != null && mic.opusPayload.length == 3, "valid mic frame");

        ByteBuffer trailingMic = ByteBuffer.allocate(8);
        trailingMic.putInt(3).putShort((short) 1).put((byte) 1).put((byte) 2).flip();
        require(MicFramePacket.read(trailingMic) == null, "trailing mic bytes must be rejected");

        ByteBuffer oversizedSound = ByteBuffer.allocate(35);
        oversizedSound.putLong(0).putLong(1).put(SoundFramePacket.FLAG_SPATIAL);
        oversizedSound.putFloat(0).putFloat(0).putFloat(0).putInt(1).putShort((short) 65535).flip();
        require(SoundFramePacket.read(oversizedSound) == null, "oversized sound payload must be rejected");

        UUID id = UUID.randomUUID();
        ByteBuffer validSound = ByteBuffer.allocate(37);
        validSound.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        validSound.put(SoundFramePacket.FLAG_SPATIAL);
        validSound.putFloat(1).putFloat(2).putFloat(3).putInt(4).putShort((short) 2).put((byte) 7).put((byte) 8).flip();
        SoundFramePacket sound = SoundFramePacket.read(validSound);
        require(sound != null && sound.isSpatial() && id.equals(sound.senderId), "valid spatial sound frame");

        System.out.println("wire-parser-smoke-ok");
    }
}
