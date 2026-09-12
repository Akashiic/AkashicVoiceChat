package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;

public final class WireProtocol {

    public static final short MAGIC = (short) 0x4156; // "AV"
    public static final byte VERSION = 1;

    public static final byte OUTER_HELLO = 1;
    public static final byte OUTER_CHALLENGE = 2;
    public static final byte OUTER_AUTH_RESPONSE = 3;
    public static final byte OUTER_ENCRYPTED = 4;

    public static final int OUTER_HEADER_BYTES = 4;
    public static final int ENCRYPTED_COUNTER_BYTES = 8;
    public static final int ENCRYPTED_AAD_BYTES = OUTER_HEADER_BYTES + ENCRYPTED_COUNTER_BYTES;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int GCM_TAG_BYTES = GCM_TAG_BITS / 8;

    private WireProtocol() {}

    public static void writeHeader(ByteBuffer buf, byte outerType) {
        buf.putShort(MAGIC);
        buf.put(VERSION);
        buf.put(outerType);
    }

    public static byte readHeader(ByteBuffer buf) {
        if (buf.remaining() < OUTER_HEADER_BYTES) return -1;
        if (buf.getShort() != MAGIC) return -1;
        if (buf.get() != VERSION) return -1;
        byte type = buf.get();
        return isKnownOuterType(type) ? type : -1;
    }

    public static byte peekHeader(ByteBuffer buf) {
        if (buf.remaining() < OUTER_HEADER_BYTES) return -1;
        int pos = buf.position();
        if (buf.getShort(pos) != MAGIC) return -1;
        if (buf.get(pos + 2) != VERSION) return -1;
        byte type = buf.get(pos + 3);
        return isKnownOuterType(type) ? type : -1;
    }

    public static boolean isKnownOuterType(byte type) {
        return type == OUTER_HELLO || type == OUTER_CHALLENGE || type == OUTER_AUTH_RESPONSE || type == OUTER_ENCRYPTED;
    }
}
