package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.akashic.voice.platform.bridge.FederatedGroupDirectory;
import dev.akashic.voice.platform.bridge.FederatedGroupPersistence;
import dev.akashic.voice.platform.bridge.FederatedGroupStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayControlRouterTest {
    @TempDir Path folder;
    private static final LegacyChannelIdentifier CHANNEL = new LegacyChannelIdentifier("AkashicVoice");

    @Test void nativeBootstrapIsForwarded() throws Exception {
        try (Fixture f=new Fixture(folder)) {
            f.backendReady=false;var event=f.client(new byte[]{0});f.router.handle(event);
            assertTrue(event.getResult().isAllowed());assertEquals(0,f.deliveries);
            assertTrue(f.known.contains(f.peer.id));assertFalse(f.groups.isBound(f.peer.id,f.peer.current));
        }
    }
    @Test void managedBootstrapIsConsumedAndBound() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var event=f.client(new byte[]{0});f.router.handle(event);
            assertFalse(event.getResult().isAllowed());assertEquals(1,f.deliveries);
            assertTrue(f.groups.isBound(f.peer.id,f.peer.current));
            assertTrue(f.peer.sent.stream().anyMatch(data->data[0]==7));
        }
    }
    @Test void failedBootstrapDoesNotOwnNativeVoice() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.deliver=false;var event=f.client(new byte[]{0});f.router.handle(event);
            assertTrue(event.getResult().isAllowed());assertEquals(1,f.abandons);
            assertFalse(f.groups.isBound(f.peer.id,f.peer.current));
        }
    }
    @Test void transferDuringBootstrapFailureIsNotForwardedToOldBackend() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.deliver=false;f.transferInDelivery=true;var event=f.client(new byte[]{0});f.router.handle(event);
            assertFalse(event.getResult().isAllowed());assertFalse(f.groups.isBound(f.peer.id,f.peer.current));
        }
    }
    @Test void clientCannotSpoofServerControls() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.backendReady=false;
            for(byte tag:new byte[]{1,6,7,8,9,42}) {
                var event=f.client(new byte[]{tag});f.router.handle(event);assertFalse(event.getResult().isAllowed());
            }
            assertEquals(0,f.deliveries);assertTrue(f.known.isEmpty());
        }
    }
    @Test void oldConnectionWithSameBackendNameIsRejected() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var event=f.client(new byte[]{0});f.peer.current=f.connection(f.peer,"lobby");f.router.handle(event);
            assertFalse(event.getResult().isAllowed());assertEquals(0,f.deliveries);
        }
    }
    @Test void anotherPlayersConnectionIsRejected() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            Peer other=new Peer();other.current=f.connection(other,"lobby");
            var event=new PluginMessageEvent(f.peer.player,other.current,CHANNEL,new byte[]{0});
            f.router.handle(event);assertFalse(event.getResult().isAllowed());assertEquals(0,f.deliveries);
        }
    }
    @Test void disconnectedPlayerCannotBootstrap() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.peer.active=false;var event=f.client(new byte[]{0});f.router.handle(event);
            assertFalse(event.getResult().isAllowed());assertEquals(0,f.deliveries);
        }
    }
    @Test void managedGroupRequestRequiresSuccessfulBootstrap() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var early=f.client(create());f.router.handle(early);
            assertFalse(early.getResult().isAllowed());assertEquals(0,f.directory.groupCount());
            f.router.handle(f.client(new byte[]{0}));f.ready();
            var request=f.client(create());f.router.handle(request);
            assertFalse(request.getResult().isAllowed());assertEquals(1,f.directory.groupCount());
            assertTrue(f.directory.groupFor(f.peer.id).isPresent());
        }
    }
    @Test void nativeGroupRequestRetainsBackendAuthority() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.backendReady=false;var request=f.client(create());f.router.handle(request);
            assertTrue(request.getResult().isAllowed());assertEquals(0,f.directory.groupCount());
        }
    }
    @Test void managedBackendCannotOverwriteProxyState() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.router.handle(f.client(new byte[]{0}));
            for(byte tag:new byte[]{1,6,7,8,9}) {
                var event=f.backend(new byte[]{tag});f.router.handle(event);assertFalse(event.getResult().isAllowed());
            }
        }
    }
    @Test void healthyManagedBackendCannotRaceBootstrapBeforeGroupBinding() throws Exception {
        try (Fixture f=new Fixture(folder)) {
            var event=f.backend(new byte[]{1});f.router.handle(event);
            assertFalse(event.getResult().isAllowed());
        }
    }
    @Test void nativeServerControlsAreForwarded() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.backendReady=false;
            for(byte tag:new byte[]{1,6,7,8,9}) {
                var event=f.backend(new byte[]{tag});f.router.handle(event);assertTrue(event.getResult().isAllowed());
            }
            var forged=f.backend(new byte[]{2});f.router.handle(forged);assertFalse(forged.getResult().isAllowed());
        }
    }
    @Test void staleBackendCannotOverwriteNewRoute() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var old=f.backend(new byte[]{7,0,0});f.peer.current=f.connection(f.peer,"forbidden-1");
            f.router.handle(old);assertFalse(old.getResult().isAllowed());
        }
    }
    @Test void anotherPluginHandledResultIsRespected() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var event=f.client(new byte[]{0});event.setResult(PluginMessageEvent.ForwardResult.handled());
            f.router.handle(event);assertEquals(0,f.deliveries);assertTrue(f.known.isEmpty());
        }
    }
    @Test void unrelatedChannelIsUntouched() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            var event=new PluginMessageEvent(f.peer.player,f.peer.current,new LegacyChannelIdentifier("Other"),new byte[]{0});
            f.router.handle(event);assertTrue(event.getResult().isAllowed());assertEquals(0,f.deliveries);
        }
    }
    @Test void nativeFallbackClearsPriorManagedAuthority() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            f.router.handle(f.client(new byte[]{0}));f.backendReady=false;
            var event=f.client(new byte[]{0});f.router.handle(event);
            assertTrue(event.getResult().isAllowed());assertEquals(1,f.abandons);
            assertFalse(f.groups.isBound(f.peer.id,f.peer.current));
        }
    }
    @Test void ordinaryPermissionsDefaultAllowedButExplicitDenialWins() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            assertTrue(f.transport.allowed(f.peer.id,f.peer.current,GatewayGroupController.USE));
            f.peer.permissions.put(GatewayGroupController.USE,Tristate.FALSE);
            assertFalse(f.transport.allowed(f.peer.id,f.peer.current,GatewayGroupController.USE));
            f.router.handle(f.client(new byte[]{0}));f.ready();f.router.handle(f.client(create()));
            assertEquals(0,f.directory.groupCount());
        }
    }
    @Test void transportRejectsOldTokensAndUUIDMismatch() throws Exception {
        try(Fixture f=new Fixture(folder)) {
            assertFalse(f.transport.send(UUID.randomUUID(),f.peer.current,new byte[]{7,0,0}));
            Object old=f.peer.current;f.peer.current=f.connection(f.peer,"lobby");
            assertFalse(f.transport.send(f.peer.id,old,new byte[]{7,0,0}));
        }
    }

    private static byte[] create() {
        byte[] name="Group".getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(3+name.length).put((byte)2).putShort((short)name.length).put(name).array();
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static Object identity(Object object,String method,Object[]args) {
        return switch(method) {
            case "equals" -> object==args[0];
            case "hashCode" -> System.identityHashCode(object);
            case "toString" -> "QA-"+object.getClass().getInterfaces()[0].getSimpleName();
            default -> throw new UnsupportedOperationException(method);
        };
    }
    private static final class Peer {
        final UUID id=UUID.randomUUID();boolean active=true;
        final Map<String,Tristate> permissions=new HashMap<>();final List<byte[]> sent=new ArrayList<>();
        ServerConnection current;
        final Player player=proxy(Player.class,(object,method,args)->switch(method.getName()) {
            case "getUniqueId" -> id;
            case "getUsername" -> "AkashicQA";
            case "isActive" -> active;
            case "getCurrentServer" -> Optional.ofNullable(current);
            case "getPermissionValue" -> permissions.getOrDefault((String)args[0],Tristate.UNDEFINED);
            case "hasPermission" -> permissions.getOrDefault((String)args[0],Tristate.UNDEFINED)==Tristate.TRUE;
            case "sendPluginMessage" -> {sent.add(((byte[])args[1]).clone());yield true;}
            case "sendMessage" -> null;
            default -> identity(object,method.getName(),args);
        });
    }
    private static final class Fixture implements AutoCloseable,GatewayControlRouter.Bootstrap {
        final Peer peer=new Peer();final Set<UUID> known=new HashSet<>();
        final FederatedGroupDirectory directory=new FederatedGroupDirectory();
        final FederatedGroupPersistence persistence;final GatewayGroupController groups;
        final VelocityGroupTransport transport;final GatewayControlRouter router;
        boolean backendReady=true,deliver=true,transferInDelivery;int deliveries,abandons;
        Fixture(Path folder) {
            peer.current=connection(peer,"lobby");
            ProxyServer server=proxy(ProxyServer.class,(object,method,args)-> {
                if(method.getName().equals("getPlayer"))return peer.id.equals(args[0])?Optional.of(peer.player):Optional.empty();
                return identity(object,method.getName(),args);
            });
            transport=new VelocityGroupTransport(server,CHANNEL);
            persistence=new FederatedGroupPersistence(directory,new FederatedGroupStore(folder.resolve("groups.dat")));
            groups=new GatewayGroupController(directory,persistence,transport);groups.start();
            router=new GatewayControlRouter(CHANNEL,groups,this,known);
        }
        ServerConnection connection(Peer owner,String name) {
            return proxy(ServerConnection.class,(object,method,args)->switch(method.getName()) {
                case "getPlayer" -> owner.player;
                case "getServerInfo" -> new ServerInfo(name,new InetSocketAddress("127.0.0.1",25565));
                default -> identity(object,method.getName(),args);
            });
        }
        void ready()throws Exception {
            long end=System.nanoTime()+5_000_000_000L;
            while(persistence.status().state()!=FederatedGroupPersistence.State.READY) {
                assertTrue(System.nanoTime()<end,"persistence load timeout");Thread.sleep(2);
            }
            groups.tick();
        }
        PluginMessageEvent client(byte[]data){return new PluginMessageEvent(peer.player,peer.current,CHANNEL,data);}
        PluginMessageEvent backend(byte[]data){return new PluginMessageEvent(peer.current,peer.player,CHANNEL,data);}
        @Override public boolean backendReady(String name){return backendReady;}
        @Override public boolean deliver(Player player,ServerConnection connection){deliveries++;if(transferInDelivery)peer.current=connection(peer,"forbidden-1");return deliver;}
        @Override public void abandon(UUID id){abandons++;}
        @Override public void close() {
            groups.requestClose();
            try {assertTrue(groups.awaitStopped(Duration.ofSeconds(5)),"group worker leaked");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
        }
    }
}
