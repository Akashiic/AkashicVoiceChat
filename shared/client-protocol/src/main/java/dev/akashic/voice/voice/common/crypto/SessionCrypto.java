package dev.akashic.voice.voice.common.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dev.akashic.voice.voice.common.WireProtocol;

public final class SessionCrypto implements AutoCloseable {

    private final SecretKeySpec outboundKey;
    private final SecretKeySpec inboundKey;
    private final byte[] outboundNoncePrefix;
    private final byte[] inboundNoncePrefix;
    private final byte[] outboundNonce = new byte[WireProtocol.GCM_NONCE_BYTES];
    private final byte[] inboundNonce = new byte[WireProtocol.GCM_NONCE_BYTES];
    private final Cipher sealCipher;
    private final Cipher openCipher;
    private final ReplayWindow replayWindow = new ReplayWindow();
    private long sendCounter;
    private boolean closed;

    private SessionCrypto(byte[] outboundKey, byte[] inboundKey, byte[] outboundPrefix, byte[] inboundPrefix) {
        this.outboundKey = new SecretKeySpec(Arrays.copyOf(outboundKey, outboundKey.length), "AES");
        this.inboundKey = new SecretKeySpec(Arrays.copyOf(inboundKey, inboundKey.length), "AES");
        this.outboundNoncePrefix = Arrays.copyOf(outboundPrefix, outboundPrefix.length);
        this.inboundNoncePrefix = Arrays.copyOf(inboundPrefix, inboundPrefix.length);
        try {
            this.sealCipher = Cipher.getInstance("AES/GCM/NoPadding");
            this.openCipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES/GCM/NoPadding unavailable", e);
        }
    }

    public static SessionCrypto forClient(SessionKeys keys) {
        return new SessionCrypto(keys.c2sKey(), keys.s2cKey(), keys.c2sNoncePrefix(), keys.s2cNoncePrefix());
    }

    public static SessionCrypto forServer(SessionKeys keys) {
        return new SessionCrypto(keys.s2cKey(), keys.c2sKey(), keys.s2cNoncePrefix(), keys.c2sNoncePrefix());
    }

    public synchronized boolean seal(ByteBuffer plaintext, ByteBuffer datagram) {
        requireOpen();
        if (!plaintext.hasArray() || !datagram.hasArray()) throw new IllegalArgumentException("heap buffers required");
        int plainLength = plaintext.position();
        int required = WireProtocol.ENCRYPTED_AAD_BYTES + plainLength + WireProtocol.GCM_TAG_BYTES;
        if (plainLength <= 0 || datagram.capacity() < required) return false;
        if (sendCounter == Long.MAX_VALUE) throw new IllegalStateException("AEAD send counter exhausted");

        long counter = sendCounter;
        datagram.clear();
        WireProtocol.writeHeader(datagram, WireProtocol.OUTER_ENCRYPTED);
        datagram.putLong(counter);
        fillNonce(outboundNonce, outboundNoncePrefix, counter);
        try {
            sealCipher
                .init(Cipher.ENCRYPT_MODE, outboundKey, new GCMParameterSpec(WireProtocol.GCM_TAG_BITS, outboundNonce));
            int aadOffset = datagram.arrayOffset();
            sealCipher.updateAAD(datagram.array(), aadOffset, WireProtocol.ENCRYPTED_AAD_BYTES);
            int outputOffset = aadOffset + WireProtocol.ENCRYPTED_AAD_BYTES;
            int written = sealCipher
                .doFinal(plaintext.array(), plaintext.arrayOffset(), plainLength, datagram.array(), outputOffset);
            datagram.position(WireProtocol.ENCRYPTED_AAD_BYTES + written);
            sendCounter = counter + 1L;
            return true;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public synchronized boolean open(ByteBuffer datagram, ByteBuffer plaintext) {
        requireOpen();
        if (!datagram.hasArray() || !plaintext.hasArray()) throw new IllegalArgumentException("heap buffers required");
        int start = datagram.position();
        if (datagram.remaining() < WireProtocol.ENCRYPTED_AAD_BYTES + WireProtocol.GCM_TAG_BYTES + 1) return false;
        if (WireProtocol.readHeader(datagram) != WireProtocol.OUTER_ENCRYPTED) return false;
        if (datagram.remaining() < WireProtocol.ENCRYPTED_COUNTER_BYTES + WireProtocol.GCM_TAG_BYTES + 1) return false;
        long counter = datagram.getLong();
        if (!replayWindow.canAccept(counter)) return false;
        int cipherLength = datagram.remaining();
        int maxPlain = cipherLength - WireProtocol.GCM_TAG_BYTES;
        if (maxPlain <= 0 || plaintext.capacity() < maxPlain) return false;

        fillNonce(inboundNonce, inboundNoncePrefix, counter);
        try {
            openCipher
                .init(Cipher.DECRYPT_MODE, inboundKey, new GCMParameterSpec(WireProtocol.GCM_TAG_BITS, inboundNonce));
            int aadOffset = datagram.arrayOffset() + start;
            openCipher.updateAAD(datagram.array(), aadOffset, WireProtocol.ENCRYPTED_AAD_BYTES);
            int inputOffset = datagram.arrayOffset() + datagram.position();
            plaintext.clear();
            int written = openCipher
                .doFinal(datagram.array(), inputOffset, cipherLength, plaintext.array(), plaintext.arrayOffset());
            if (!replayWindow.accept(counter)) return false;
            plaintext.position(0);
            plaintext.limit(written);
            return true;
        } catch (BadPaddingException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    public synchronized long nextSendCounter() {
        return sendCounter;
    }

    public long highestReceivedCounter() {
        return replayWindow.highestAccepted();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(outboundNoncePrefix, (byte) 0);
        Arrays.fill(inboundNoncePrefix, (byte) 0);
        Arrays.fill(outboundNonce, (byte) 0);
        Arrays.fill(inboundNonce, (byte) 0);
        replayWindow.reset();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("SessionCrypto closed");
    }

    private static void fillNonce(byte[] target, byte[] prefix, long counter) {
        if (prefix.length != SessionKeys.NONCE_PREFIX_BYTES) {
            throw new IllegalArgumentException("invalid nonce prefix");
        }
        System.arraycopy(prefix, 0, target, 0, prefix.length);
        int offset = prefix.length;
        for (int i = 7; i >= 0; i--) {
            target[offset + i] = (byte) counter;
            counter >>>= 8;
        }
    }
}
