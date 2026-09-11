package dev.akashic.voice.voice.common.codec;

public interface OpusEncoder extends AutoCloseable {

    int encode(short[] pcm, byte[] out);

    @Override
    void close();
}
