package dev.akashic.voice.voice.common;

public final class WirePacketType {
    // Preserve every established v1/v2 discriminator; the extension is opt-in.
    public static final byte AUTH_ACK = 0x02;
    public static final byte KEEPALIVE = 0x03;
    public static final byte MIC_FRAME = 0x04;
    public static final byte SOUND_FRAME = 0x05;
    public static final byte DISCONNECT = 0x06;
    public static final byte SOUND_RESET = 0x07;
    public static final byte SOUND_FRAME_V2 = 0x08;
    public static final byte CAPABILITIES = 0x09;
    public static final byte CAPABILITIES_ACK = 0x0A;
    public static final byte SOUND_FRAME_V3 = 0x0B;
    public static final byte SOUND_RESET_V3 = 0x0C;
    private WirePacketType() {}
}
