package dev.akashic.voice.platform.agent;

public final class AgentProtocol {
    public static final int MAGIC = 0x41564147; // "AVAG"
    public static final byte VERSION = 1;

    public static final byte HELLO = 1;
    public static final byte CHALLENGE = 2;
    public static final byte AUTH_RESPONSE = 3;

    public static final byte READY = 16;
    public static final byte MANAGED_ATTACH = 17;
    public static final byte MANAGED_DETACH = 18;
    public static final byte AKASHIC_FRAME = 19;
    public static final byte AKASHIC_RESET = 20;
    public static final byte SVC_DELIVERY = 21;
    public static final byte SVC_RESET = 22;
    public static final byte PING = 23;
    public static final byte PONG = 24;

    public static final int MAX_BACKEND_BYTES = 64;
    public static final int MAX_STRING_BYTES = 256;
    public static final int MAX_PLAINTEXT_BYTES = 4096;
    public static final int MAX_ENCRYPTED_FRAME_BYTES = 8192;

    private AgentProtocol() {}
}
