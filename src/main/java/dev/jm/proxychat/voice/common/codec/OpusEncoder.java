package dev.jm.proxychat.voice.common.codec;

public interface OpusEncoder extends AutoCloseable {

    int encode(short[] pcm, byte[] out);

    @Override
    void close();
}
