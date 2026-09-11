package dev.akashic.voice.voice.server;

public final class MediaRateLimiterSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MediaRateLimiter limiter = new MediaRateLimiter();
        long base = System.nanoTime();

        for (int i = 0; i < 120; i++) {
            require(limiter.allow(200, base), "burst packet rejected at " + i);
        }
        require(!limiter.allow(200, base), "packet burst limit not enforced");
        require(!limiter.allow(0, base), "zero byte datagram accepted");
        require(!limiter.allow(1501, base), "oversized datagram accepted");

        long afterOneSecond = base + 1_000_000_000L;
        for (int i = 0; i < 80; i++) {
            require(limiter.allow(200, afterOneSecond), "refilled packet rejected at " + i);
        }
        require(!limiter.allow(200, afterOneSecond), "sustained packet rate not enforced");
        System.out.println("media-rate-limiter-smoke-ok");
    }
}
