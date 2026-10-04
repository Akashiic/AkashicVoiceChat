package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayLogicalSessionIdentityTest {
    @Test
    void sessionBoundRebindRequiresSameAuthenticatedSessionId() throws Exception {
        UUID player=UUID.randomUUID(), voice=UUID.randomUUID(), session=UUID.randomUUID();
        GatewayVoiceConnection first=connection(player,"lobby",voice,session,31001);
        GatewayVoiceConnection rebound=connection(player,"lobby",voice,session,31002);
        assertTrue(sameLogicalSession(first,rebound),"same grant/address change must be a logical rebind");

        GatewayVoiceConnection rotated=connection(player,"lobby",voice,UUID.randomUUID(),31003);
        assertFalse(sameLogicalSession(first,rotated),
            "different sessionId must not suppress lifecycle callbacks");
    }

    @Test
    void legacyRebindStillUsesLegacyIdentityTuple() throws Exception {
        UUID player=UUID.randomUUID(), voice=UUID.randomUUID();
        assertTrue(sameLogicalSession(connection(player,"lobby",voice,null,32001),
            connection(player,"lobby",voice,null,32002)));
        assertFalse(sameLogicalSession(connection(player,"forbidden",voice,null,32003),
            connection(player,"lobby",voice,null,32004)));
        assertFalse(sameLogicalSession(connection(player,"lobby",UUID.randomUUID(),null,32005),
            connection(player,"lobby",voice,null,32006)));
    }

    private static GatewayVoiceConnection connection(UUID player,String backend,UUID voice,UUID session,int port) {
        return new GatewayVoiceConnection(player,"Tester",backend,voice,session,
            new InetSocketAddress("127.0.0.1",port),null);
    }

    private static boolean sameLogicalSession(GatewayVoiceConnection first,
                                              GatewayVoiceConnection second) throws Exception {
        Method method=GatewayUdpServer.class.getDeclaredMethod("sameLogicalSession",
            GatewayVoiceConnection.class,GatewayVoiceConnection.class);
        method.setAccessible(true);
        return (boolean)method.invoke(null,first,second);
    }
}
