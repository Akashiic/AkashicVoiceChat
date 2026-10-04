package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.FederatedGroup;
import dev.akashic.voice.platform.bridge.FederatedGroupDirectory;
import dev.akashic.voice.platform.bridge.FederatedGroupPersistence;
import dev.akashic.voice.platform.bridge.FederatedGroupStore;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Runs production controller + real persistence + exact wire codec; no Minecraft clients. */
final class GatewayGroupControlSmoke {
    @FunctionalInterface interface Scenario { void run() throws Exception; }
    static final Map<String, Scenario> SCENARIOS = new java.util.LinkedHashMap<>();
    static {
        SCENARIOS.put("loading_gate", GatewayGroupControlSmoke::loadingGate);
        SCENARIOS.put("create_invite_accept_leave", GatewayGroupControlSmoke::crud);
        SCENARIOS.put("personalized_state", GatewayGroupControlSmoke::personalized);
        SCENARIOS.put("stale_connection", GatewayGroupControlSmoke::staleConnection);
        SCENARIOS.put("old_disconnect_cannot_remove_replacement", GatewayGroupControlSmoke::replacement);
        SCENARIOS.put("use_permission", () -> denied(GatewayGroupController.USE));
        SCENARIOS.put("create_permission", () -> denied(GatewayGroupController.CREATE));
        SCENARIOS.put("invite_permission", GatewayGroupControlSmoke::invitePermission);
        SCENARIOS.put("inviter_permission_rechecked_on_accept", GatewayGroupControlSmoke::revokeInviter);
        SCENARIOS.put("leave_after_permission_revocation", GatewayGroupControlSmoke::exitAfterRevoke);
        SCENARIOS.put("disconnect_revokes_invitation", GatewayGroupControlSmoke::disconnectInvite);
        SCENARIOS.put("failed_delivery_revokes_invitation", GatewayGroupControlSmoke::deliveryFailure);
        SCENARIOS.put("malformed_requests_consume_budget", GatewayGroupControlSmoke::requestBudget);
        SCENARIOS.put("bootstrap_does_not_reset_budget", GatewayGroupControlSmoke::bootstrapBudget);
        SCENARIOS.put("media_permission_snapshot", GatewayGroupControlSmoke::audioEligibility);
        SCENARIOS.put("persistent_membership_roundtrip", GatewayGroupControlSmoke::roundTrip);
        SCENARIOS.put("corrupt_load_preserves_file", GatewayGroupControlSmoke::corruptLoad);
        SCENARIOS.put("write_failure_visible_and_retryable", GatewayGroupControlSmoke::retryWrite);
        SCENARIOS.put("close_write_failure_is_not_success", GatewayGroupControlSmoke::failedClose);
        SCENARIOS.put("close_mutation_serialization", GatewayGroupControlSmoke::closeRace);
        SCENARIOS.put("oversized_roster_is_bounded", GatewayGroupControlSmoke::rosterBudget);
        SCENARIOS.put("managed_binding_capacity", GatewayGroupControlSmoke::bindingCapacity);
        SCENARIOS.put("invalid_binding_rejected", GatewayGroupControlSmoke::badBinding);
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (var entry : SCENARIOS.entrySet()) {
            entry.getValue().run(); passed++;
            System.out.println("PASS " + entry.getKey());
        }
        System.out.println("SUMMARY gateway_group_control passed=" + passed + " failed=0");
    }

    static void run(String name) throws Exception {
        Scenario scenario = SCENARIOS.get(name);
        if (scenario == null) throw new IllegalArgumentException(name);
        scenario.run();
    }

    private static void loadingGate() throws Exception {
        try (Fixture f = new Fixture(false)) {
            Peer a = f.peer("A"); f.bind(a); f.request(a, create("One"));
            require(f.directory.groupCount() == 0, "command admitted before READY");
            require(!f.controller.allowsGroupAudio(a.id), "media enabled before READY");
            f.start(); f.request(a, create("One"));
            require(f.directory.groupCount() == 1, "READY did not admit create");
        }
    }
    private static void crud() throws Exception {
        try (Fixture f = new Fixture(true)) {
            Peer a=f.peer("A"), b=f.peer("B"); f.bind(a); f.bind(b);
            f.request(a, create(" Squad ")); FederatedGroup g=f.group(a);
            require(g.name().equals("Squad"), "name changed");
            f.request(a, invite(g.id(), b.id)); require(b.last.containsKey(6), "invite not delivered");
            f.request(b, respond(g.id(), true));
            require(f.directory.haveCommonGroup(a.id,b.id), "join not routed to shared directory");
            f.request(a, leave(g.id())); require(f.group(b).ownerId().equals(b.id), "owner not transferred");
            require(groupCount(a)==0 && groupCount(b)==1, "leave did not synchronize both sides");
            require(f.persistence.status().requestedRevision()==3, "persistent mutation receipts wrong");
        }
    }
    private static void personalized() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A"), b=f.peer("B"); f.bind(a);f.bind(b);
            f.request(a,create("Secret"));f.controller.tick();
            require(groupCount(a)==1 && groupCount(b)==0,"sent another player's groups");
            try (DataInputStream in=input(a.last.get(7))) {
                require(in.readUnsignedByte()==7 && in.readUnsignedShort()==1,"bad personal packet");
                UUID group=uuid(in);text(in);UUID owner=uuid(in);
                require(group.equals(f.group(a).id()) && owner.equals(a.id),"not authoritative proxy UUID");
            }
        }
    }
    private static void staleConnection() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);Object old=a.connection;a.connection=new Object();
            f.controller.request(a.id,old,create("Forged"));
            require(f.directory.groupCount()==0,"stale route mutated groups");
            f.controller.tick();require(f.controller.status().bindings()==0,"stale route not pruned");
        }
    }
    private static void replacement() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);Object old=a.connection;a.connection=new Object();f.bind(a);
            f.controller.unbind(a.id,old);f.request(a,create("New"));
            require(f.controller.isBound(a.id,a.connection)&&f.directory.groupCount()==1,"old disconnect removed new binding");
        }
    }
    private static void denied(String permission) throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");a.denied.add(permission);f.bind(a);f.request(a,create("No"));
            require(f.directory.groupCount()==0,"denied command mutated");
            require(f.controller.status().rejected()==1,"denial not counted");
        }
    }
    private static void invitePermission() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A"),b=f.peer("B");f.bind(a);f.bind(b);f.request(a,create("G"));
            a.denied.add(GatewayGroupController.INVITE);f.request(a,invite(f.group(a).id(),b.id));
            require(f.directory.pendingInviteCount()==0,"denied invitation stored");
        }
    }
    private static void revokeInviter() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A"),b=f.peer("B");f.bind(a);f.bind(b);f.request(a,create("G"));
            UUID id=f.group(a).id();f.request(a,invite(id,b.id));a.denied.add(GatewayGroupController.INVITE);
            f.request(b,respond(id,true));require(f.directory.groupFor(b.id).isEmpty(),"revoked inviter still authorized acceptance");
            require(f.directory.pendingInviteCount()==0,"revoked grant left usable");
        }
    }
    private static void exitAfterRevoke() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);f.request(a,create("G"));UUID id=f.group(a).id();
            a.denied.add(GatewayGroupController.USE);f.request(a,leave(id));
            require(f.directory.groupCount()==0,"revoked user trapped in group");
        }
    }
    private static void disconnectInvite() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A"),b=f.peer("B");f.bind(a);f.bind(b);f.request(a,create("G"));
            UUID id=f.group(a).id();f.request(a,invite(id,b.id));f.controller.disconnected(a.id);
            f.request(b,respond(id,true));require(f.directory.groupFor(b.id).isEmpty(),"disconnect retained grant");
            require(f.directory.groupFor(a.id).isPresent(),"disconnect erased persistent membership");
        }
    }
    private static void deliveryFailure() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A"),b=f.peer("B");f.bind(a);f.bind(b);f.request(a,create("G"));
            b.failTag=6;f.request(a,invite(f.group(a).id(),b.id));
            require(f.directory.pendingInviteCount()==0,"undelivered grant remained active");
            require(f.controller.status().deliveryFailures()==1,"failure not counted");
        }
    }
    private static void requestBudget() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);
            for(int i=0;i<1000;i++)f.request(a,new byte[]{2});
            require(f.controller.status().rejected()==16&&f.controller.status().rateLimited()==984,"unbounded malformed path");
            f.clock.addAndGet(GatewayGroupController.TOKEN_NANOS);f.request(a,create("G"));
            require(f.directory.groupCount()==1,"quota failed to refill");
        }
    }
    private static void bootstrapBudget() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);for(int i=0;i<16;i++)f.request(a,new byte[]{2});
            for(int i=0;i<100;i++)f.bind(a);
            f.request(a,create("Abuse"));require(f.directory.groupCount()==0,"bootstrap reset control quota");
        }
    }
    private static void audioEligibility() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);require(f.controller.allowsGroupAudio(a.id),"not enabled");
            a.denied.add(GatewayGroupController.USE);f.controller.tick();
            require(!f.controller.allowsGroupAudio(a.id),"permission snapshot not revoked");
            a.denied.clear();f.controller.tick();require(f.controller.allowsGroupAudio(a.id),"permission not restored");
            f.controller.requestClose();require(!f.controller.allowsGroupAudio(a.id),"media remained enabled after close");
        }
    }
    private static void roundTrip() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);f.request(a,create("Persistent"));
            f.controller.requestClose();require(f.controller.awaitStopped(Duration.ofSeconds(5)),"save did not stop");
            FederatedGroupDirectory loaded=new FederatedGroupDirectory();
            require(new FederatedGroupStore(f.file).loadInto(loaded),"no saved file");
            require(loaded.snapshot().equals(f.directory.snapshot()),"persisted directory differs");
        }
    }
    private static void corruptLoad() throws Exception {
        try (Fixture f=new Fixture(false)) {
            byte[] broken={1,2,3,4};Files.write(f.file,broken);f.controller.start();
            await(()->f.persistence.status().workerStopped());Peer a=f.peer("A");f.bind(a);f.request(a,create("Erase"));
            require(f.directory.groupCount()==0,"corrupt load admitted mutation");
            require(Arrays.equals(broken,Files.readAllBytes(f.file)),"corrupt file overwritten");
        }
    }
    private static void retryWrite() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Files.createDirectory(f.file);Peer a=f.peer("A");f.bind(a);f.request(a,create("G"));
            await(()->f.persistence.status().failedWrites()>0&&!f.persistence.status().saving());
            require(f.persistence.status().dirty(),"failed save acknowledged");Files.delete(f.file);
            require(f.controller.retrySave(),"failed save not retryable");
            await(()->!f.persistence.status().dirty());
            require(f.persistence.status().successfulWrites()==1,"retry did not save");
        }
    }
    private static void failedClose() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Files.createDirectory(f.file);Peer a=f.peer("A");f.bind(a);f.request(a,create("G"));
            f.controller.requestClose();require(f.controller.awaitStopped(Duration.ofSeconds(5)),"failure did not terminate");
            require(f.persistence.status().state()==FederatedGroupPersistence.State.FAILED,"false successful shutdown");
            require(f.persistence.status().dirty(),"failed shutdown lost dirty receipt");
        }
    }
    private static void closeRace() throws Exception {
        for(int repeat=0;repeat<12;repeat++)try(Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");f.bind(a);CountDownLatch start=new CountDownLatch(1);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread mutate=new Thread(()->{try{start.await();f.request(a,create("Race"));}catch(Throwable e){failure.set(e);}});
            Thread close=new Thread(()->{try{start.await();f.controller.requestClose();}catch(Throwable e){failure.set(e);}});
            mutate.start();close.start();start.countDown();mutate.join(5000);close.join(5000);
            require(!mutate.isAlive()&&!close.isAlive()&&failure.get()==null,"close race failed");
            require(f.controller.awaitStopped(Duration.ofSeconds(5)),"close failed");
            FederatedGroupDirectory restored=new FederatedGroupDirectory();new FederatedGroupStore(f.file).loadInto(restored);
            require(restored.snapshot().equals(f.directory.snapshot()),"accepted mutation lost during shutdown");
        }
    }
    private static void rosterBudget() throws Exception {
        try (Fixture f=new Fixture(true)) {
            for(int i=0;i<180;i++)f.bind(f.peer("界".repeat(64)));
            f.controller.tick();require(f.controller.status().rosterTruncations()>0,"no oversized roster exercised");
            for(Peer peer:f.peers.values())require(peer.last.get(8).length<=30000,"roster exceeds wire budget");
        }
    }
    private static void bindingCapacity() throws Exception {
        try (Fixture f=new Fixture(false)) {
            for(int i=0;i<GatewayGroupController.MAX_BINDINGS;i++)f.bind(f.peer("P"+i));
            Peer extra=f.peer("Extra");require(!f.controller.bind(extra.id,extra.name,extra.connection),"unbounded binding map");
            require(f.controller.status().bindings()==GatewayGroupController.MAX_BINDINGS,"binding cap differs");
        }
    }
    private static void badBinding() throws Exception {
        try (Fixture f=new Fixture(true)) {
            Peer a=f.peer("A");require(!f.controller.bind(a.id,"x".repeat(65),a.connection),"invalid name accepted");
            require(!f.controller.bind(a.id,a.name,new Object()),"stale binding accepted");
            require(f.controller.status().bindings()==0,"invalid binding changed state");
        }
    }

    private static final class Peer {
        final UUID id=UUID.randomUUID(); final String name; Object connection=new Object();
        final Set<String> denied=new HashSet<>(); final Map<Integer,byte[]> last=new HashMap<>();
        final List<GatewayGroupController.Notice> notices=new ArrayList<>(); int failTag=-1;
        Peer(String name){this.name=name;}
    }
    private static final class Fixture implements AutoCloseable,GatewayGroupController.Transport {
        final Path folder=Files.createTempDirectory("av-control-"); final Path file=folder.resolve("groups.dat");
        final FederatedGroupDirectory directory=new FederatedGroupDirectory();
        final FederatedGroupPersistence persistence=new FederatedGroupPersistence(directory,new FederatedGroupStore(file));
        final Map<UUID,Peer> peers=new HashMap<>();final AtomicLong clock=new AtomicLong(1);
        final GatewayGroupController controller=new GatewayGroupController(directory,persistence,this,clock::get);
        Fixture(boolean start)throws Exception{if(start)start();}
        void start()throws Exception{controller.start();await(()->persistence.status().state()==FederatedGroupPersistence.State.READY);controller.tick();}
        Peer peer(String name){Peer p=new Peer(name);peers.put(p.id,p);return p;}
        void bind(Peer peer){require(controller.bind(peer.id,peer.name,peer.connection),"bind failed");}
        void request(Peer peer,byte[]data){controller.request(peer.id,peer.connection,data);}
        FederatedGroup group(Peer peer){return directory.groupFor(peer.id).orElseThrow();}
        @Override public boolean isCurrent(UUID id,Object connection){Peer p=peers.get(id);return p!=null&&p.connection==connection;}
        @Override public boolean allowed(UUID id,Object connection,String permission){return isCurrent(id,connection)&&!peers.get(id).denied.contains(permission);}
        @Override public boolean send(UUID id,Object connection,byte[]payload){if(!isCurrent(id,connection))return false;Peer p=peers.get(id);if(p.failTag==(payload[0]&255))return false;p.last.put(payload[0]&255,payload.clone());return true;}
        @Override public void notice(UUID id,Object connection,GatewayGroupController.Notice notice){if(isCurrent(id,connection))peers.get(id).notices.add(notice);}
        @Override public void close() throws java.io.IOException {
            controller.requestClose();
            try { require(controller.awaitStopped(Duration.ofSeconds(5)), "worker leaked"); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Interrupted waiting for QA worker", interrupted);
            }
            try (var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(ComparatorReverse.INSTANCE).toList()) Files.delete(path);
            }
        }
    }
    private enum ComparatorReverse implements java.util.Comparator<Path> { INSTANCE; @Override public int compare(Path a,Path b){return b.compareTo(a);} }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+5_000_000_000L;while(!condition.getAsBoolean()){require(System.nanoTime()<end,"timeout");Thread.sleep(2);}}
    private static byte[] create(String name){byte[]text=name.getBytes(StandardCharsets.UTF_8);return ByteBuffer.allocate(3+text.length).put((byte)2).putShort((short)text.length).put(text).array();}
    private static byte[] invite(UUID group,UUID target){return ByteBuffer.allocate(33).put((byte)3).putLong(group.getMostSignificantBits()).putLong(group.getLeastSignificantBits()).putLong(target.getMostSignificantBits()).putLong(target.getLeastSignificantBits()).array();}
    private static byte[] respond(UUID group,boolean yes){return ByteBuffer.allocate(18).put((byte)4).putLong(group.getMostSignificantBits()).putLong(group.getLeastSignificantBits()).put((byte)(yes?1:0)).array();}
    private static byte[] leave(UUID group){return ByteBuffer.allocate(17).put((byte)5).putLong(group.getMostSignificantBits()).putLong(group.getLeastSignificantBits()).array();}
    private static DataInputStream input(byte[]data){return new DataInputStream(new ByteArrayInputStream(data));}
    private static UUID uuid(DataInputStream in)throws Exception{return new UUID(in.readLong(),in.readLong());}
    private static String text(DataInputStream in)throws Exception{return new String(in.readNBytes(in.readUnsignedShort()),StandardCharsets.UTF_8);}
    private static int groupCount(Peer peer)throws Exception{try(var in=input(peer.last.get(7))){require(in.readUnsignedByte()==7,"bad discriminator");return in.readUnsignedShort();}}
}
