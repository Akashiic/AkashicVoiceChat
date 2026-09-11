package dev.akashic.voice.voice.common.codec;

public interface OpusDecoder extends AutoCloseable {

    int decode(byte[] opus, int opusLength, short[] outPcm);

    int decodeMissing(short[] outPcm);

    @Override
    void close();
}
