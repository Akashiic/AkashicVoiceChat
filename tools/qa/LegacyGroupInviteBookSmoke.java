package dev.akashic.voice.voice.server.groups;

import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class LegacyGroupInviteBookSmoke {
    private static final class Clock implements LongSupplier {
        long now;
        @Override public long getAsLong() { return now; }
    }

    public static void main(String[] args) {
        lifecycleAndDuplicateTtl();
        limitsAreIndependent();
        cleanupRemovesAllReferences();
        signedNanoWrapExpires();
        invalidLimitsRejected();
        deterministicStress();
        System.out.println("LEGACY_GROUP_INVITES_OK scenarios=6 stress=20000");
    }

    private static void lifecycleAndDuplicateTtl() {
        Clock c = new Clock();
        LegacyGroupInviteBook b = new LegacyGroupInviteBook(c, 100, 4, 4, 8);
        UUID g = id(1), inviter = id(2), target = id(3);
        check(b.offer(g, inviter, target), "first invite");
        c.now = 90;
        check(!b.offer(g, inviter, target), "duplicate must not refresh");
        c.now = 100;
        check(b.consume(g, target) == null, "invite must expire at ttl boundary");
        check(b.pendingCount() == 0, "expired invite removed");
    }
    private static void limitsAreIndependent() {
        Clock c = new Clock();
        LegacyGroupInviteBook group = new LegacyGroupInviteBook(c, 1000, 2, 8, 8);
        check(group.offer(id(10), id(11), id(12)), "group invite 1");
        check(group.offer(id(10), id(11), id(13)), "group invite 2");
        check(!group.offer(id(10), id(11), id(14)), "per-group limit");

        LegacyGroupInviteBook recipient = new LegacyGroupInviteBook(c, 1000, 8, 2, 8);
        UUID target = id(20);
        check(recipient.offer(id(21), id(22), target), "recipient invite 1");
        check(recipient.offer(id(23), id(24), target), "recipient invite 2");
        check(!recipient.offer(id(25), id(26), target), "per-recipient limit");

        LegacyGroupInviteBook global = new LegacyGroupInviteBook(c, 1000, 3, 3, 3);
        check(global.offer(id(30), id(31), id(32)), "global 1");
        check(global.offer(id(33), id(34), id(35)), "global 2");
        check(global.offer(id(36), id(37), id(38)), "global 3");
        check(!global.offer(id(39), id(40), id(41)), "global limit");
        LegacyGroupInviteBook self = new LegacyGroupInviteBook(c, 1000, 2, 2, 4);
        check(!self.offer(id(42), id(43), id(43)), "self invite rejected");
    }

    private static void cleanupRemovesAllReferences() {
        Clock c = new Clock();
        LegacyGroupInviteBook b = new LegacyGroupInviteBook(c, 1000, 8, 8, 16);
        UUID inviter = id(50), target = id(51);
        check(b.offer(id(52), inviter, target), "cleanup a");
        check(b.offer(id(53), inviter, id(54)), "cleanup b");
        check(b.offer(id(55), id(56), target), "cleanup c");
        b.removePlayer(inviter);
        check(b.pendingCount() == 1, "inviter cleanup");
        b.removePlayer(target);
        check(b.pendingCount() == 0, "recipient cleanup");

        check(b.offer(id(60), id(61), id(62)), "group cleanup a");
        check(b.offer(id(60), id(63), id(64)), "group cleanup b");
        b.removeGroup(id(60));
        check(b.pendingCount() == 0, "group cleanup");
    }

    private static void signedNanoWrapExpires() {
        Clock c = new Clock();
        LegacyGroupInviteBook b = new LegacyGroupInviteBook(c, 10, 4, 4, 8);
        c.now = Long.MAX_VALUE - 5;
        UUID g = id(70), target = id(71);
        check(b.offer(g, id(72), target), "wrap offer");
        c.now = Long.MIN_VALUE + 5;
        check(b.consume(g, target) == null, "signed nano wrap expiry");
        check(b.pendingCount() == 0, "wrap cleanup");
    }

    private static void invalidLimitsRejected() {
        Clock c = new Clock();
        expectInvalid(c, 0, 1, 1, 1);
        expectInvalid(c, 1, 0, 1, 1);
        expectInvalid(c, 1, 2, 1, 1);
        expectInvalid(c, 1, 1, 2, 1);
    }

    private static void expectInvalid(Clock c, long ttl, int perGroup, int perRecipient, int max) {
        try {
            new LegacyGroupInviteBook(c, ttl, perGroup, perRecipient, max);
            throw new AssertionError("invalid limits accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
    private static void deterministicStress() {
        Clock c = new Clock();
        LegacyGroupInviteBook b = new LegacyGroupInviteBook(c, 50, 8, 4, 64);
        Random r = new Random(0xA5C11C21L);
        for (int i = 0; i < 20_000; i++) {
            c.now += r.nextInt(5);
            UUID group = id(100 + r.nextInt(16));
            UUID inviter = id(1_000 + r.nextInt(32));
            UUID target = id(2_000 + r.nextInt(64));
            switch (r.nextInt(5)) {
                case 0: b.offer(group, inviter, target); break;
                case 1: b.consume(group, target); break;
                case 2: b.removePlayer(target); break;
                case 3: b.removeGroup(group); break;
                default: b.expire(); break;
            }
            int pending = b.pendingCount();
            check(pending >= 0 && pending <= 64, "bounded pending count");
        }
        c.now += 100;
        b.expire();
        check(b.pendingCount() == 0, "stress expires cleanly");
    }

    private static UUID id(long value) { return new UUID(0L, value); }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
