package dev.akashic.voice.voice.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class HandshakeRateLimiterSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static InetSocketAddress remote(int a, int b, int c, int d, int port) throws Exception {
        byte[] raw = new byte[] {(byte) a, (byte) b, (byte) c, (byte) d};
        return new InetSocketAddress(InetAddress.getByAddress(raw), port);
    }

    public static void main(String[] args) throws Exception {
        HandshakeRateLimiter perIp = new HandshakeRateLimiter();
        InetSocketAddress first = remote(10, 20, 30, 40, 20000);
        for (int i = 0; i < 20; i++) {
            require(perIp.allow(first), "per-IP burst rejected early at " + i);
        }
        require(!perIp.allow(first), "per-IP burst limit not enforced");
        require(perIp.allow(remote(10, 20, 30, 41, 20001)), "independent IP incorrectly blocked");

        HandshakeRateLimiter global = new HandshakeRateLimiter();
        int rejected = 0;
        for (int i = 0; i < 400; i++) {
            int third = (i / 250) + 1;
            int fourth = (i % 250) + 1;
            if (!global.allow(remote(172, 16, third, fourth, 30000 + i))) rejected++;
        }
        require(rejected > 0, "global burst limit not enforced");
        require(global.bucketCount() <= 4096, "bucket map exceeded hard cap");

        System.out.println("handshake-rate-limiter-smoke-ok rejected=" + rejected);
    }
}
