package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.voice.common.*;
import dev.akashic.voice.platform.velocity.GatewayGroupMediaSmoke.Fixture;
import dev.akashic.voice.platform.velocity.GatewayGroupMediaSmoke.Peer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real gateway, secure UDP, agent sockets, persistent directory and group controller. */
final class GatewayEpoch06Test {
    @BeforeAll static void fixture(){GatewayGroupMediaIntegrationTest.fixture();}
    @Test void negotiationIsPerReceiverAndKeepsLegacyPeerWorking()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);
            assertEquals(1,negotiate(b,1));a.mic(40);SoundFrameV3Packet p=epoch(b);
            assertEquals(a.id,p.frame.senderId);assertEquals(40,p.frame.sequence);assertFalse(p.frame.isSpatial());
            GatewayGroupMediaSmoke.sameOpus(p.frame.opusData,p.frame.opusOffset,p.frame.opusLength);
            b.mic(60);assertEquals(60,a.group().sequence);f.healthy();
        }
    }
    @Test void backendHandoffChangesEpochWithoutChangingSpeakerIdentityOrOpus()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);negotiate(b,1);
            a.mic(100);SoundFrameV3Packet before=epoch(b);
            f.controller.unbind(a.id,a.token);f.runtime.disconnectProxyPlayer(a.id);
            Peer replacement=f.peer(a.id,"A","two");replacement.mic(0);SoundFrameV3Packet after=epoch(b);
            assertEquals(before.frame.senderId,after.frame.senderId);assertTrue(after.epoch>before.epoch);
            assertEquals(0,after.frame.sequence);
            GatewayGroupMediaSmoke.sameOpus(after.frame.opusData,after.frame.opusOffset,after.frame.opusLength);
            a.mic(101);b.silence();f.healthy();
        }
    }
    @Test void repeatedNegotiationCannotRewindAnEpoch()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);negotiate(b,1);
            a.mic(1);long e=epoch(b).epoch;assertEquals(1,negotiate(b,0));a.mic(2);
            assertEquals(e,epoch(b).epoch);f.healthy();
        }
    }
    @Test void unknownCapabilitiesAreIgnoredAndMalformedOnesProduceNoAck()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");f.join(a,b);assertEquals(0,negotiate(b,2));
            a.mic(1);assertEquals(1,b.group().sequence);
            ByteBuffer invalid=ByteBuffer.allocate(5).put(WirePacketType.CAPABILITIES).putInt(-1);send(b,invalid);b.silence();
            ByteBuffer shortPacket=ByteBuffer.allocate(3).put(WirePacketType.CAPABILITIES).putShort((short)1);send(b,shortPacket);b.silence();
            assertEquals(1,negotiate(b,3));a.mic(2);assertEquals(2,epoch(b).frame.sequence);f.healthy();
        }
    }
    @Test void spatialDistanceWhisperAndOpusArePreserved()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");negotiate(b,1);
            f.deliver("two",a,b,33);SoundFrameV3Packet p=epoch(b);
            assertTrue(p.frame.isSpatial());assertTrue(p.frame.isWhispering());assertEquals(12.5F,p.frame.maxDistance);
            assertEquals(11F,p.frame.x);assertEquals(64F,p.frame.y);assertEquals(22F,p.frame.z);
            GatewayGroupMediaSmoke.sameOpus(p.frame.opusData,p.frame.opusOffset,p.frame.opusLength);f.healthy();
        }
    }
    @Test void resetRefersToItsStreamAndNextAudioReceivesSuccessorEpoch()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");negotiate(b,1);
            f.deliver("two",a,b,33);SoundFrameV3Packet first=epoch(b);
            f.reset("two",a,b);ByteBuffer reset=b.plain();assertEquals(WirePacketType.SOUND_RESET_V3,reset.get());
            SoundResetV3Packet p=SoundResetV3Packet.read(reset);assertNotNull(p);assertEquals(first.epoch,p.epoch);
            f.deliver("two",a,b,0);SoundFrameV3Packet next=epoch(b);assertTrue(next.epoch>first.epoch);f.healthy();
        }
    }
    @Test void changingBetweenGroupAndProximityCreatesNewEpochs()throws Exception{
        try(Fixture f=new Fixture()){
            Peer a=f.peer("A","one"),b=f.peer("B","two");UUID group=f.join(a,b);negotiate(b,1);
            a.mic(0);SoundFrameV3Packet grouped=epoch(b);
            f.controller.request(b.id,b.token,GatewayGroupMediaSmoke.idPacket(5,group));
            f.deliver("two",a,b,0);SoundFrameV3Packet spatial=epoch(b);
            assertTrue(spatial.epoch>grouped.epoch&&spatial.frame.isSpatial());
            f.controller.request(a.id,a.token,GatewayGroupMediaSmoke.invite(group,b.id));
            f.controller.request(b.id,b.token,GatewayGroupMediaSmoke.respond(group));
            a.mic(1);SoundFrameV3Packet rejoined=epoch(b);
            assertTrue(rejoined.epoch>spatial.epoch&&!rejoined.frame.isSpatial());f.healthy();
        }
    }
    @Test void outboundTrackerRetiresPreciselyAndNeverOverflowsCapacity(){
        OutboundStreamEpochs t=new OutboundStreamEpochs();UUID speaker=new UUID(1,1),source=new UUID(2,2);
        long e=t.frame(speaker,source,false);assertTrue(e>0);assertEquals(e,t.frame(speaker,source,false));
        assertEquals(0,t.retire(speaker,new UUID(3,3),false));assertEquals(e,t.retire(speaker,source,false));
        assertEquals(0,t.retire(speaker,source,false));assertTrue(t.frame(speaker,source,false)>e);
        for(int i=1;i<OutboundStreamEpochs.CAPACITY;i++)assertTrue(t.frame(new UUID(4,i),source,true)>0);
        assertEquals(0,t.frame(new UUID(5,5),source,true));assertEquals(OutboundStreamEpochs.CAPACITY,t.size());
    }
    private static int negotiate(Peer p,int features)throws Exception{
        ByteBuffer b=ByteBuffer.allocate(5);VoiceCapabilities.write(b,false,features);send(p,b);
        ByteBuffer ack=p.plain();assertEquals(WirePacketType.CAPABILITIES_ACK,ack.get());return VoiceCapabilities.read(ack);
    }
    private static void send(Peer p,ByteBuffer b)throws Exception{
        ByteBuffer wire=ByteBuffer.allocate(1500);assertTrue(p.crypto.seal(b,wire));p.raw(GatewayGroupMediaSmoke.written(wire));
    }
    private static SoundFrameV3Packet epoch(Peer p)throws Exception{
        ByteBuffer b=p.plain();assertEquals(WirePacketType.SOUND_FRAME_V3,b.get());
        SoundFrameV3Packet frame=SoundFrameV3Packet.read(b);assertNotNull(frame);return frame;
    }
}
