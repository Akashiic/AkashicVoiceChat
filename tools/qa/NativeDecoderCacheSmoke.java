import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.akashic.voice.voice.common.codec.NativeOpusDecoder;

public final class NativeDecoderCacheSmoke {
    public static void main(String[] args) throws Exception {
        NativeOpusDecoder decoder = new NativeOpusDecoder();
        try {
            Method exact = NativeOpusDecoder.class
                .getDeclaredMethod("exactPacket", byte[].class, int.class, int.class);
            exact.setAccessible(true);
            byte[] source = new byte[1300];
            for (int length = 1; length <= 128; length++) {
                exact.invoke(decoder, source, 1, length);
            }

            Field field = NativeOpusDecoder.class.getDeclaredField("packetScratch");
            field.setAccessible(true);
            byte[][] cache = (byte[][]) field.get(decoder);
            int populated = 0;
            int retained = 0;
            for (byte[] packet : cache) {
                if (packet == null) continue;
                populated++;
                retained += packet.length;
            }
            if (cache.length != 8) throw new AssertionError("unexpected cache slots: " + cache.length);
            if (populated > 8) throw new AssertionError("unbounded packet cache");
            if (retained > 8 * 1275) throw new AssertionError("retained packet bytes too high: " + retained);
            System.out.println("native-decoder-cache-smoke-ok slots=" + populated + " retainedBytes=" + retained);
        } finally {
            decoder.close();
        }
    }
}
