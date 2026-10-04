package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayHandshakeRateLimiterTest {
    @Test
    void allowsMassTransferBurstButPreservesTenPerSecondRefill() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayHandshakeRateLimiter limiter = new GatewayHandshakeRateLimiter(now::get);
        InetAddress address = address(10);

        for (int i = 0; i < 64; i++) {
            assertTrue(limiter.allow(new InetSocketAddress(address, 20_000 + i)), "burst rejected at " + i);
        }
        assertFalse(limiter.allow(new InetSocketAddress(address, 21_000)), "65th same-IP HELLO escaped burst");

        now.addAndGet(99_000_000L);
        assertFalse(limiter.allow(new InetSocketAddress(address, 21_001)), "token refilled before 100 ms");
        now.addAndGet(1_000_000L);
        assertTrue(limiter.allow(new InetSocketAddress(address, 21_002)), "10/s refill did not restore one token");
        assertFalse(limiter.allow(new InetSocketAddress(address, 21_003)), "single 100 ms refill restored more than one token");
    }

    @Test
    void sourcePortsShareIpBucketButDifferentIpIsIndependent() throws Exception {
        AtomicLong now = new AtomicLong(2_000_000_000L);
        GatewayHandshakeRateLimiter limiter = new GatewayHandshakeRateLimiter(now::get);
        InetAddress first = address(20);
        InetAddress second = address(21);
        for (int i = 0; i < 64; i++) assertTrue(limiter.allow(new InetSocketAddress(first, 30_000 + i)));
        assertFalse(limiter.allow(new InetSocketAddress(first, 31_000)));
        assertTrue(limiter.allow(new InetSocketAddress(second, 31_001)), "different IP inherited exhausted bucket");
    }

    @Test
    void globalBurstStillCapsAggregateFlood() throws Exception {
        AtomicLong now = new AtomicLong(3_000_000_000L);
        GatewayHandshakeRateLimiter limiter = new GatewayHandshakeRateLimiter(now::get);
        for (int ip = 1; ip <= 4; ip++) {
            InetAddress address = address(40 + ip);
            for (int i = 0; i < 64; i++) assertTrue(limiter.allow(new InetSocketAddress(address, 40_000 + i)));
        }
        assertFalse(limiter.allow(new InetSocketAddress(address(99), 50_000)), "global 256 HELLO burst was not enforced");
    }

    private static InetAddress address(int last) throws Exception {
        return InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, (byte) last});
    }
}
