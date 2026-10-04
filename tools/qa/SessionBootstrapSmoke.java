package dev.akashic.voice.voice.common;

import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.net.PacketSecret;
import dev.akashic.voice.voice.server.SecretRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class SessionBootstrapSmoke {
    private static int passed;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pass(String name) { passed++; System.out.println("PASS " + name); }

    public static void main(String[] args) {
        legacyBootstrapStillParses(); pass("legacy_bootstrap");
        sessionBootstrapPreservesPrefix(); pass("session_bootstrap_prefix");
        malformedSuffixRejected(); pass("malformed_suffix");
        oversizedSuffixRejected(); pass("oversized_suffix");
        legacyTicketRotates(); pass("legacy_ticket_rotation");
        System.out.println("SESSION_BOOTSTRAP_OK scenarios=" + passed);
    }

    private static byte[] secret() {
        byte[] out = new byte[32]; for (int i=0;i<out.length;i++) out[i]=(byte)(i*7+3); return out;
    }

    private static void legacyBootstrapStillParses() {
        UUID player = new UUID(1L,2L); byte[] secret=secret();
        PacketSecret packet = new PacketSecret(player, secret, 24454);
        ByteBuf wire=Unpooled.buffer(); packet.toBytes(wire);
        check(wire.readableBytes()==52,"legacy payload length");
        PacketSecret decoded=new PacketSecret(); decoded.fromBytes(wire.copy());
        check(player.equals(decoded.getPlayerId()),"legacy player");
        check(Arrays.equals(secret,decoded.getMasterSecret()),"legacy secret");
        check(decoded.getUdpPort()==24454,"legacy port");
        check(decoded.getSessionId()==null,"legacy unexpectedly session-bound");
    }

    private static void sessionBootstrapPreservesPrefix() {
        UUID player=new UUID(3L,4L), session=new UUID(5L,6L); byte[] secret=secret();
        ByteBuf legacy=Unpooled.buffer(); new PacketSecret(player,secret,24454).toBytes(legacy);
        ByteBuf modern=Unpooled.buffer(); new PacketSecret(player,secret,24454,session).toBytes(modern);
        check(modern.readableBytes()==68,"session payload length");
        byte[] a=new byte[52],b=new byte[52]; legacy.getBytes(0,a); modern.getBytes(0,b);
        check(Arrays.equals(a,b),"legacy prefix changed");
        // Simulate the old decoder: it consumes exactly the legacy fields and leaves the additive suffix unread.
        ByteBuf old=modern.copy(); old.readLong(); old.readLong(); old.skipBytes(32); old.readInt();
        check(old.readableBytes()==16,"old decoder cannot ignore suffix");
        PacketSecret decoded=new PacketSecret(); decoded.fromBytes(modern.copy());
        check(session.equals(decoded.getSessionId()),"new decoder lost session id");
    }

    private static void malformedSuffixRejected() {
        UUID player=new UUID(7L,8L); byte[] secret=secret();
        ByteBuf wire=Unpooled.buffer(); new PacketSecret(player,secret,24454).toBytes(wire); wire.writeByte(1);
        try { new PacketSecret().fromBytes(wire.copy()); throw new AssertionError("malformed suffix accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void oversizedSuffixRejected() {
        UUID player=new UUID(11L,12L), session=new UUID(13L,14L); byte[] secret=secret();
        ByteBuf wire=Unpooled.buffer(); new PacketSecret(player,secret,24454,session).toBytes(wire); wire.writeByte(99);
        try { new PacketSecret().fromBytes(wire.copy()); throw new AssertionError("oversized suffix accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void legacyTicketRotates() {
        UUID player=new UUID(9L,10L); SecretRegistry.invalidate(player);
        UUID a=SecretRegistry.getOrCreateSessionId(player), b=SecretRegistry.getOrCreateSessionId(player);
        check(a.equals(b),"ticket not idempotent");
        byte[] first=SecretRegistry.getOrCreate(player); check(first.length==32,"ticket secret");
        SecretRegistry.invalidate(player);
        UUID c=SecretRegistry.getOrCreateSessionId(player);
        check(!a.equals(c),"session id did not rotate");
        SecretRegistry.invalidate(player); Arrays.fill(first,(byte)0);
    }
}
