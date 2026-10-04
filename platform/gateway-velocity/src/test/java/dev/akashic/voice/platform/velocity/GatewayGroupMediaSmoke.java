package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.FederatedGroupDirectory;
import dev.akashic.voice.platform.bridge.FederatedGroupPersistence;
import dev.akashic.voice.platform.bridge.FederatedGroupStore;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.SoundResetPacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Real GatewayRuntime/UDP/agent crypto + real controller/store; fake only Minecraft-facing transport. */
public final class GatewayGroupMediaSmoke {
    @FunctionalInterface interface Scenario { void run() throws Exception; }
    static final Map<String, Scenario> SCENARIOS = new LinkedHashMap<>();
    static byte[] opus;
    static {
        SCENARIOS.put("cross_backend_group_opus_passthrough", GatewayGroupMediaSmoke::crossBackend);
        SCENARIOS.put("bidirectional_group_passthrough", GatewayGroupMediaSmoke::bidirectional);
        SCENARIOS.put("encrypted_replay_not_forwarded", GatewayGroupMediaSmoke::replay);
        SCENARIOS.put("svc_metadata_and_backend_isolation", GatewayGroupMediaSmoke::svcMetadata);
        SCENARIOS.put("group_svc_duplicate_and_reset_suppression", GatewayGroupMediaSmoke::suppressDuplicate);
        SCENARIOS.put("revoked_sender_group_audio", () -> revoke(true));
        SCENARIOS.put("revoked_recipient_group_audio", () -> revoke(false));
        SCENARIOS.put("native_spatial_delivery_after_leave", GatewayGroupMediaSmoke::leave);
        SCENARIOS.put("agent_reconnect_preserves_group_sessions", GatewayGroupMediaSmoke::agentReconnect);
        SCENARIOS.put("persistent_groups_route_after_restart", GatewayGroupMediaSmoke::persistenceRoundTrip);
        SCENARIOS.put("disconnected_binding_cannot_transmit_group", GatewayGroupMediaSmoke::unbound);
        SCENARIOS.put("closed_controller_disables_group_audio", GatewayGroupMediaSmoke::shutdown);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("path to valid Opus fixture is required");
        opus=Files.readAllBytes(Path.of(args[0])); require(opus.length>2 && opus.length<=1275,"invalid fixture size");
        int passed=0;
        for(var entry: SCENARIOS.entrySet()){entry.getValue().run();passed++;System.out.println("PASS "+entry.getKey());}
        System.out.println("SUMMARY gateway_group_media passed="+passed+" failed=0");
    }
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void await(BooleanSupplier condition,String reason)throws InterruptedException{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()){if(System.nanoTime()>=until)throw new AssertionError(reason);Thread.sleep(2);}
    }
    private static void crossBackend()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two"),outsider=f.peer("C","two");f.join(a,b);
            byte[] wire=a.mic(7); require(wire.length>opus.length,"not encrypted");
            SoundFramePacket frame=b.group();same(frame,a,7);f.frameAtAgent("one",a,7);
            a.silence();outsider.silence();f.healthy();
        }
    }
    private static void bidirectional()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            a.mic(1);same(b.group(),a,1);f.frameAtAgent("one",a,1);
            b.mic(9);same(a.group(),b,9);f.frameAtAgent("two",b,9);f.healthy();
        }
    }
    private static void replay()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            byte[] encrypted=a.mic(10);same(b.group(),a,10);f.frameAtAgent("one",a,10);
            long rejected=f.runtime.statusSnapshot().encryptedRejected();a.raw(encrypted);
            await(()->f.runtime.statusSnapshot().encryptedRejected()>rejected,"replay not rejected");
            b.silence();require(f.runtime.statusSnapshot().micFramesAccepted()==1,"replay counted as mic");f.healthy();
        }
    }
    private static void svcMetadata()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");
            f.deliver("two",a,b,50);SoundFrameV2Packet packet=b.svc();
            require(packet.senderId.equals(a.id)&&packet.sequence==50&&packet.isSpatial(),"SVC identity/sequence/spatial");
            require(packet.isWhispering()&&packet.maxDistance==12.5F&&packet.x==11F&&packet.y==64F&&packet.z==22F,"SVC metadata");
            sameOpus(packet.opusData,packet.opusOffset,packet.opusLength);
            f.deliver("one",a,b,51);f.agents.get("one").barrier();b.silence();f.healthy();
        }
    }
    private static void suppressDuplicate()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            a.mic(5);same(b.group(),a,5);f.frameAtAgent("one",a,5);
            f.deliver("two",a,b,5);f.reset("two",a,b);f.agents.get("two").barrier();b.silence();
            require(f.runtime.statusSnapshot().soundFramesSent()==0&&f.runtime.statusSnapshot().soundResetsSent()==0,"duplicate/reset leaked");
            a.mic(6);same(b.group(),a,6);f.healthy();
        }
    }
    private static void revoke(boolean sender)throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            f.transport.denied.add(sender?a.id:b.id);f.controller.tick();
            a.mic(20);f.frameAtAgent("one",a,20);b.silence();
            f.deliver("two",a,b,20);require(b.svc().sequence==20,"group permission incorrectly blocked native proximity");
            f.transport.denied.clear();f.controller.tick();a.mic(21);same(b.group(),a,21);f.healthy();
        }
    }
    private static void leave()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");UUID group=f.join(a,b);
            f.controller.request(b.id,b.token,idPacket(5,group));a.mic(25);f.frameAtAgent("one",a,25);b.silence();
            f.deliver("two",a,b,25);require(b.svc().isSpatial(),"leave blocked spatial delivery");
            f.reset("two",a,b);require(b.reset().senderId.equals(a.id),"non-group reset not forwarded");f.healthy();
        }
    }
    private static void agentReconnect()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            f.agents.get("one").close();await(()->!f.runtime.isBackendReady("one"),"route never down");
            // Authenticated group path is independent of a transient Paper-agent interruption.
            a.mic(31);same(b.group(),a,31);
            Agent next=f.connectAgent("one");next.expect(x->x instanceof AgentMessage.Attach p&&p.player().playerId().equals(a.id));
            a.mic(32);same(b.group(),a,32);f.frameAtAgent("one",a,32);
            require(f.runtime.statusSnapshot().activeUdpSessions()==2,"reconnect lost UDP sessions");f.healthy();
        }
    }
    private static void persistenceRoundTrip()throws Exception{
        Path dir=Files.createTempDirectory("av-media-persistence-");Path file=dir.resolve("groups.dat");
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try{
            try(Fixture f=new Fixture(file)){
                Peer a=f.peer(first,"A","one"),b=f.peer(second,"B","two");f.join(a,b);
                f.controller.requestClose();require(f.controller.awaitStopped(Duration.ofSeconds(5)),"flush timeout");
                require(!f.persistence.status().dirty(),"not persisted");
            }
            try(Fixture f=new Fixture(file)){
                Peer a=f.peer(first,"A","one"),b=f.peer(second,"B","two");
                require(f.directory.haveCommonGroup(first,second),"members not restored");
                a.mic(0);same(b.group(),a,0);f.healthy();
            }
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
    }
    private static void unbound()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            f.controller.unbind(a.id,a.token);a.mic(40);f.frameAtAgent("one",a,40);b.silence();
            require(f.directory.haveCommonGroup(a.id,b.id),"unbinding deleted persistent membership");f.healthy();
        }
    }
    private static void shutdown()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            f.controller.requestClose();a.mic(41);f.frameAtAgent("one",a,41);b.silence();
            require(f.controller.awaitStopped(Duration.ofSeconds(5)),"worker not stopped");f.healthy();
        }
    }
    static void same(SoundFramePacket frame,Peer sender,int seq){
        require(!frame.isSpatial()&&frame.senderId.equals(sender.id)&&frame.sequence==seq,"group identity/sequence/mode wrong");
        sameOpus(frame.opusData,frame.opusOffset,frame.opusLength);
    }
    static void sameOpus(byte[] bytes,int offset,int length){require(Arrays.equals(opus,Arrays.copyOfRange(bytes,offset,offset+length)),"raw Opus bytes changed");}
    static byte[] create(String name){byte[] b=name.getBytes(StandardCharsets.UTF_8);return ByteBuffer.allocate(b.length+3).put((byte)2).putShort((short)b.length).put(b).array();}
    static byte[] idPacket(int tag,UUID id){return ByteBuffer.allocate(17).put((byte)tag).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    static byte[] invite(UUID group,UUID who){return ByteBuffer.allocate(33).put((byte)3).putLong(group.getMostSignificantBits()).putLong(group.getLeastSignificantBits()).putLong(who.getMostSignificantBits()).putLong(who.getLeastSignificantBits()).array();}
    static byte[] respond(UUID group){byte[] b=Arrays.copyOf(idPacket(4,group),18);b[17]=1;return b;}

    static final class Transport implements GatewayGroupController.Transport{
        final Map<UUID,Object> current=new HashMap<>();final Set<UUID> denied=new HashSet<>();
        @Override public boolean isCurrent(UUID id,Object token){return current.get(id)==token;}
        @Override public boolean allowed(UUID id,Object token,String permission){return isCurrent(id,token)&&!denied.contains(id);}
        @Override public boolean send(UUID id,Object token,byte[] payload){return isCurrent(id,token);}
        @Override public void notice(UUID id,Object token,GatewayGroupController.Notice notice){}
    }
    static final class Fixture implements AutoCloseable{
        final Path file;final boolean ownDirectory;
        final BootstrapSessionRegistry registry=new BootstrapSessionRegistry();
        final List<String> errors=new CopyOnWriteArrayList<>();
        final Map<String,byte[]> psks=new HashMap<>();final Map<String,Agent> agents=new HashMap<>();
        final List<Peer> peers=new ArrayList<>();final Transport transport=new Transport();
        final GatewayRuntime runtime;final FederatedGroupDirectory directory;
        final FederatedGroupPersistence persistence;final GatewayGroupController controller;
        Fixture()throws Exception{this(Files.createTempDirectory("av-media-").resolve("groups.dat"),true);}
        Fixture(Path file)throws Exception{this(file,false);}
        private Fixture(Path file,boolean own)throws Exception{
            this.file=file;ownDirectory=own;SecureRandom random=new SecureRandom();
            for(String backend:List.of("one","two")){byte[] key=new byte[32];random.nextBytes(key);psks.put(backend,key);}
            int port;try(DatagramSocket unused=new DatagramSocket(0)){port=unused.getLocalPort();}
            runtime=new GatewayRuntime(port,new InetSocketAddress(InetAddress.getLoopbackAddress(),0),psks,registry,new GatewayRuntime.Listener(){
                @Override public void onRouteUp(String name){}
                @Override public void onRouteDown(String name){}
                @Override public void onError(String component,Throwable error){errors.add(component+":"+error.getClass().getSimpleName());}
            });
            directory=runtime.groupDirectory();persistence=new FederatedGroupPersistence(directory,new FederatedGroupStore(file));
            controller=new GatewayGroupController(directory,persistence,transport);runtime.setGroupAudioEligibility(controller::allowsGroupAudio);
            try{controller.start();await(()->persistence.status().state()==FederatedGroupPersistence.State.READY,"persistence not ready");
                controller.tick();runtime.start();connectAgent("one");connectAgent("two");
            }catch(Exception|Error failure){close();throw failure;}
        }
        Agent connectAgent(String backend)throws Exception{
            Agent next=new Agent(backend,AgentSocketHandshake.connect(new Socket(InetAddress.getLoopbackAddress(),runtime.agentPort()),backend,psks.get(backend),new SecureRandom()));
            agents.put(backend,next);await(()->runtime.isBackendReady(backend),"route not ready: "+backend);return next;
        }
        Peer peer(String name,String backend)throws Exception{return peer(UUID.randomUUID(),name,backend);}
        Peer peer(UUID id,String name,String backend)throws Exception{
            Peer p=new Peer(id,backend,runtime);peers.add(p);transport.current.put(id,p.token);
            require(controller.bind(id,name,p.token),"controller bind failed");
            agents.get(backend).expect(x->x instanceof AgentMessage.Attach a&&a.player().playerId().equals(id));return p;
        }
        UUID join(Peer a,Peer b){
            controller.request(a.id,a.token,create("Shared"));UUID id=directory.groupFor(a.id).orElseThrow().id();
            controller.request(a.id,a.token,invite(id,b.id));controller.request(b.id,b.token,respond(id));
            require(directory.haveCommonGroup(a.id,b.id),"group control did not establish membership");return id;
        }
        void frameAtAgent(String backend,Peer p,int seq)throws Exception{
            AgentMessage.SpeakerFrame frame=(AgentMessage.SpeakerFrame)agents.get(backend).expect(x->x instanceof AgentMessage.SpeakerFrame a&&a.frame().speakerId().equals(p.id)&&a.frame().sequence()==seq);
            require(Arrays.equals(opus,frame.frame().opusUnsafe()),"agent altered Opus");
        }
        void deliver(String backend,Peer a,Peer b,long seq){
            BridgeDelivery delivery=new BridgeDelivery(b.id,a.id,new UUID(0,37),seq,SpatialKind.LOCATION,null,
                new PositionSnapshot("minecraft:overworld",11,64,22),12.5F,true,"proximity",opus);
            require(agents.get(backend).channel.send(new AgentMessage.SvcDelivery(delivery)),"agent delivery not queued");
        }
        void reset(String backend,Peer a,Peer b){require(agents.get(backend).channel.send(new AgentMessage.SvcReset(new BridgeStreamReset(b.id,a.id,new UUID(0,37)))),"reset not queued");}
        void healthy(){require(errors.isEmpty(),"runtime errors: "+errors);for(Agent a:agents.values())if(!a.closed)require(a.failure.get()==null,"agent read failure");}
        @Override public void close()throws IOException{
            controller.requestClose();try{require(controller.awaitStopped(Duration.ofSeconds(5)),"persistence cleanup timeout");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
            finally{
                for(Peer p:peers)p.close();for(Agent a:agents.values())a.close();runtime.close();registry.close();
                for(byte[] key:psks.values())Arrays.fill(key,(byte)0);
                if(ownDirectory){Files.deleteIfExists(file);Files.deleteIfExists(file.getParent());}
            }
        }
    }
    static final class Agent implements AutoCloseable{
        final String backend;final AgentSecureChannel channel;final BlockingQueue<AgentMessage> received=new LinkedBlockingQueue<>(256);
        final AtomicReference<Throwable> failure=new AtomicReference<>();final Thread reader;volatile boolean closed;
        Agent(String backend,AgentSecureChannel channel){
            this.backend=backend;this.channel=channel;reader=new Thread(()->{
                try{while(!closed){AgentMessage message=channel.receive();if(message instanceof AgentMessage.Ping ping){channel.send(new AgentMessage.Pong(ping.timestamp()));continue;}
                    if(!received.offer(message))throw new IOException("QA agent queue overflow");}}
                catch(Exception error){if(!closed)failure.set(error);}
            },"AkashicVoice-QA-Agent-"+backend);reader.setDaemon(true);reader.start();
        }
        AgentMessage expect(Predicate<AgentMessage> predicate)throws Exception{
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<until){if(failure.get()!=null)throw new AssertionError("agent failed",failure.get());
                AgentMessage message=received.poll(20,TimeUnit.MILLISECONDS);if(message!=null&&predicate.test(message))return message;}
            throw new AssertionError("missing agent message on "+backend);
        }
        void barrier()throws Exception{
            // Control has priority over media. Wait for media dequeue before queuing
            // the marker, so the single writer sends the in-flight media first.
            await(()->channel.queuedMediaMessages()==0,"agent media did not drain");long nonce=System.nanoTime();require(channel.send(new AgentMessage.Ping(nonce)),"barrier not queued");expect(x->x instanceof AgentMessage.Pong p&&p.timestamp()==nonce);}
        @Override public void close(){closed=true;channel.close();try{reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
    static final class Peer implements AutoCloseable{
        final UUID id;final Object token=new Object();final String backend;final DatagramSocket socket;
        final SessionCrypto crypto;
        Peer(UUID id,String backend,GatewayRuntime runtime)throws Exception{
            this.id=id;this.backend=backend;BootstrapGrant grant=runtime.grantFor(id,"P"+id.toString().substring(0,6),backend);
            require(grant!=null&&!grant.voiceIdentity().equals(id),"missing or non-isolated voice identity");
            socket=new DatagramSocket();socket.connect(InetAddress.getLoopbackAddress(),runtime.udpPort());socket.setSoTimeout(3000);
            byte[] secret=grant.masterSecretCopy(),nonce=new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];new SecureRandom().nextBytes(nonce);
            SessionCrypto created=null;
            try{
                ByteBuffer hello=ByteBuffer.allocate(128);HandshakePackets.writeHello(hello,grant.voiceIdentity(),nonce);raw(written(hello));
                HandshakePackets.Challenge challenge=HandshakePackets.readChallenge(ByteBuffer.wrap(receive()));require(challenge!=null,"no challenge");
                byte[] proof;
                try(SessionKeys keys=SessionKeys.derive(secret,nonce,challenge.serverNonce)){
                    require(HandshakeProof.verifyServer(keys,grant.voiceIdentity(),nonce,challenge.serverNonce,challenge.serverProof),"bad proof");
                    proof=HandshakeProof.computeClient(keys,grant.voiceIdentity(),nonce,challenge.serverNonce);created=SessionCrypto.forClient(keys);
                }
                ByteBuffer response=ByteBuffer.allocate(128);HandshakePackets.writeAuthResponse(response,proof);raw(written(response));Arrays.fill(proof,(byte)0);
                ByteBuffer plain=ByteBuffer.allocate(1500);require(created.open(ByteBuffer.wrap(receive()),plain),"no encrypted auth ACK");
                require(plain.get()==WirePacketType.AUTH_ACK&&AuthAckPacket.read(plain).status==AuthAckPacket.STATUS_OK,"auth rejected");
                crypto=created;
            }catch(Exception|Error error){if(created!=null)created.close();socket.close();throw error;}
            finally{Arrays.fill(secret,(byte)0);Arrays.fill(nonce,(byte)0);}
        }
        byte[] mic(int sequence)throws IOException{
            ByteBuffer plain=ByteBuffer.allocate(1500);MicFramePacket.write(plain,sequence,opus,opus.length);
            ByteBuffer wire=ByteBuffer.allocate(1500);require(crypto.seal(plain,wire),"seal failed");byte[] bytes=written(wire);raw(bytes);return bytes;
        }
        void raw(byte[] bytes)throws IOException{socket.send(new DatagramPacket(bytes,bytes.length));}
        byte[] receive()throws IOException{DatagramPacket packet=new DatagramPacket(new byte[1500],1500);socket.receive(packet);return Arrays.copyOf(packet.getData(),packet.getLength());}
        ByteBuffer plain()throws IOException{ByteBuffer plain=ByteBuffer.allocate(1500);require(crypto.open(ByteBuffer.wrap(receive()),plain),"invalid downlink");return plain;}
        SoundFramePacket group()throws IOException{ByteBuffer p=plain();require(p.get()==WirePacketType.SOUND_FRAME,"expected group v1");SoundFramePacket r=SoundFramePacket.read(p);require(r!=null,"bad group frame");return r;}
        SoundFrameV2Packet svc()throws IOException{ByteBuffer p=plain();require(p.get()==WirePacketType.SOUND_FRAME_V2,"expected svc v2");SoundFrameV2Packet r=SoundFrameV2Packet.read(p);require(r!=null,"bad svc frame");return r;}
        SoundResetPacket reset()throws IOException{ByteBuffer p=plain();require(p.get()==WirePacketType.SOUND_RESET,"expected reset");return SoundResetPacket.read(p);}
        void silence()throws IOException{socket.setSoTimeout(120);try{receive();throw new AssertionError("unexpected UDP delivery");}catch(SocketTimeoutException expected){}finally{socket.setSoTimeout(3000);}}
        @Override public void close(){socket.close();crypto.close();}
    }
    static byte[] written(ByteBuffer buffer){return Arrays.copyOf(buffer.array(),buffer.position());}
}
