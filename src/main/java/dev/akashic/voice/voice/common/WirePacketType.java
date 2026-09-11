package dev.akashic.voice.voice.common;

public final class WirePacketType {

    public static final byte AUTHENTICATE = 0x01;
    public static final byte AUTH_ACK = 0x02;
    public static final byte KEEPALIVE = 0x03;
    public static final byte MIC_FRAME = 0x04;
    public static final byte SOUND_FRAME = 0x05;
    public static final byte DISCONNECT = 0x06;

    private WirePacketType() {}
}
