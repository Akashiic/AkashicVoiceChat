package dev.akashic.voice.voice.common.codec;

public interface OpusDecoder extends AutoCloseable {

    int decode(byte[] opus, int opusOffset, int opusLength, short[] outPcm);

    default int decode(byte[] opus, int opusLength, short[] outPcm) {
        return decode(opus, 0, opusLength, outPcm);
    }

    int decodeMissing(short[] outPcm);

    @Override
    void close();
}
