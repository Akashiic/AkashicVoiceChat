package dev.akashic.voice.voice.common.crypto;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HkdfSha256 {

    public static final int HASH_BYTES = 32;

    private HkdfSha256() {}

    public static byte[] extract(byte[] salt, byte[] ikm) {
        if (ikm == null) throw new IllegalArgumentException("ikm");
        byte[] actualSalt = salt != null ? salt : new byte[HASH_BYTES];
        return hmac(actualSalt, ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) {
        if (prk == null || prk.length < HASH_BYTES) throw new IllegalArgumentException("prk");
        if (length < 0 || length > 255 * HASH_BYTES) throw new IllegalArgumentException("length");
        byte[] actualInfo = info != null ? info : new byte[0];
        byte[] out = new byte[length];
        byte[] previous = new byte[0];
        int outOffset = 0;
        int counter = 1;
        while (outOffset < length) {
            Mac mac = newMac(prk);
            mac.update(previous);
            mac.update(actualInfo);
            mac.update((byte) counter);
            previous = mac.doFinal();
            int copy = Math.min(previous.length, length - outOffset);
            System.arraycopy(previous, 0, out, outOffset, copy);
            outOffset += copy;
            counter++;
        }
        return out;
    }

    static byte[] hmac(byte[] key, byte[] data) {
        Mac mac = newMac(key);
        return mac.doFinal(data);
    }

    static Mac newMac(byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
