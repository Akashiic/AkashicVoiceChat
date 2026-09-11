import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoPrimitiveSmoke {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        byte[] aad = new byte[] {0x41, 0x56, 1, 4};
        byte[] plain = "akashic-voice-java8".getBytes("UTF-8");
        random.nextBytes(key);
        random.nextBytes(iv);

        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        enc.updateAAD(aad);
        byte[] sealed = enc.doFinal(plain);

        Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        dec.updateAAD(aad);
        byte[] opened = dec.doFinal(sealed);
        if (!Arrays.equals(plain, opened)) throw new AssertionError("AES-GCM round trip failed");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        byte[] digest = mac.doFinal(plain);
        if (digest.length != 32) throw new AssertionError("HMAC-SHA256 length");

        sealed[sealed.length - 1] ^= 1;
        try {
            dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            dec.updateAAD(aad);
            dec.doFinal(sealed);
            throw new AssertionError("tampered GCM tag accepted");
        } catch (AEADBadTagException expected) {
            // expected
        } catch (GeneralSecurityException expected) {
            // Some Java 8 providers surface a generic tag failure.
        }
        System.out.println("crypto-primitive-smoke-ok provider=" + enc.getProvider().getName());
    }
}
