package dev.akashic.voice.voice.common.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;

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
    private final boolean sessionBound;
    private final long sessionMost;
    private final long sessionLeast;
    private final byte outerType;
    private final int aadBytes;
    private final ReplayWindow replayWindow = new ReplayWindow();
    private long sendCounter;
    private boolean closed;

    private SessionCrypto(byte[] outboundKey, byte[] inboundKey, byte[] outboundPrefix, byte[] inboundPrefix,
        UUID sessionId) {
        this.outboundKey = new SecretKeySpec(Arrays.copyOf(outboundKey, outboundKey.length), "AES");
        this.inboundKey = new SecretKeySpec(Arrays.copyOf(inboundKey, inboundKey.length), "AES");
        this.outboundNoncePrefix = Arrays.copyOf(outboundPrefix, outboundPrefix.length);
        this.inboundNoncePrefix = Arrays.copyOf(inboundPrefix, inboundPrefix.length);
        this.sessionBound = sessionId != null;
        this.sessionMost = sessionId != null ? sessionId.getMostSignificantBits() : 0L;
        this.sessionLeast = sessionId != null ? sessionId.getLeastSignificantBits() : 0L;
        this.outerType = sessionBound ? WireProtocol.OUTER_SESSION_ENCRYPTED : WireProtocol.OUTER_ENCRYPTED;
        this.aadBytes = sessionBound ? WireProtocol.SESSION_ENCRYPTED_AAD_BYTES : WireProtocol.ENCRYPTED_AAD_BYTES;
        try {
            this.sealCipher = Cipher.getInstance("AES/GCM/NoPadding");
            this.openCipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES/GCM/NoPadding unavailable", e);
        }
    }

    public static SessionCrypto forClient(SessionKeys keys) {
        return new SessionCrypto(keys.c2sKey(), keys.s2cKey(), keys.c2sNoncePrefix(), keys.s2cNoncePrefix(), null);
    }

    public static SessionCrypto forServer(SessionKeys keys) {
        return new SessionCrypto(keys.s2cKey(), keys.c2sKey(), keys.s2cNoncePrefix(), keys.c2sNoncePrefix(), null);
    }

    public static SessionCrypto forClient(SessionKeys keys, UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        return new SessionCrypto(keys.c2sKey(), keys.s2cKey(), keys.c2sNoncePrefix(), keys.s2cNoncePrefix(), sessionId);
    }

    public static SessionCrypto forServer(SessionKeys keys, UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        return new SessionCrypto(keys.s2cKey(), keys.c2sKey(), keys.s2cNoncePrefix(), keys.c2sNoncePrefix(), sessionId);
    }

    public synchronized boolean seal(ByteBuffer plaintext, ByteBuffer datagram) {
        requireOpen();
        if (!plaintext.hasArray() || !datagram.hasArray()) throw new IllegalArgumentException("heap buffers required");
        int plainLength = plaintext.position();
        int required = aadBytes + plainLength + WireProtocol.GCM_TAG_BYTES;
        if (plainLength <= 0 || datagram.capacity() < required) return false;
        if (sendCounter == Long.MAX_VALUE) throw new IllegalStateException("AEAD send counter exhausted");

        long counter = sendCounter;
        datagram.clear();
        WireProtocol.writeHeader(datagram, outerType);
        if (sessionBound) {
            datagram.putLong(sessionMost);
            datagram.putLong(sessionLeast);
        }
        datagram.putLong(counter);
        fillNonce(outboundNonce, outboundNoncePrefix, counter);
        try {
            sealCipher
                .init(Cipher.ENCRYPT_MODE, outboundKey, new GCMParameterSpec(WireProtocol.GCM_TAG_BITS, outboundNonce));
            int aadOffset = datagram.arrayOffset();
            sealCipher.updateAAD(datagram.array(), aadOffset, aadBytes);
            int outputOffset = aadOffset + aadBytes;
            int written = sealCipher
                .doFinal(plaintext.array(), plaintext.arrayOffset(), plainLength, datagram.array(), outputOffset);
            datagram.position(aadBytes + written);
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
        if (datagram.remaining() < aadBytes + WireProtocol.GCM_TAG_BYTES + 1) return false;
        if (WireProtocol.readHeader(datagram) != outerType) return false;
        if (sessionBound) {
            if (datagram.getLong() != sessionMost || datagram.getLong() != sessionLeast) return false;
        }
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
            openCipher.updateAAD(datagram.array(), aadOffset, aadBytes);
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
