package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** Java 8 executable contracts for reconstructed, unreleased wire extension. */
public final class EpochProtocolSmoke {
    static final UUID ID=new UUID(1,2);
    static int passed;
    interface Scenario { void run() throws Exception; }
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void run(String s,Scenario t)throws Exception{t.run();passed++;System.out.println("PASS "+s);}
    public static void main(String[] args)throws Exception {
        run("v1_v2_discriminators_unchanged",()->check(WirePacketType.SOUND_FRAME==5&&WirePacketType.SOUND_FRAME_V2==8&&WirePacketType.DISCONNECT==6,"wire regression"));
        run("capability_roundtrip_and_unknown_positive_bits",()->{ByteBuffer b=ByteBuffer.allocate(5);VoiceCapabilities.write(b,false,3);b.flip();check(b.get()==WirePacketType.CAPABILITIES&&VoiceCapabilities.read(b)==3,"capabilities changed");});
        run("malformed_capability_lengths",()->{for(int n=0;n<10;n++)if(n!=4)check(VoiceCapabilities.read(ByteBuffer.allocate(n))==-1,"accepted wrong length");});
        run("negative_capabilities_rejected",()->{ByteBuffer b=ByteBuffer.allocate(4);b.putInt(-1);b.flip();check(VoiceCapabilities.read(b)==-1,"negative accepted");});
        run("frame_body_preserves_v2_exactly",()->{SoundFrameV2Packet f=frame();ByteBuffer a=ByteBuffer.allocate(1500),b=ByteBuffer.allocate(1500);f.write(a);new SoundFrameV3Packet(5,f).write(b);check(Arrays.equals(Arrays.copyOfRange(a.array(),1,a.position()),Arrays.copyOfRange(b.array(),9,b.position())),"body changed");});
        run("frame_roundtrip_and_offset",()->{byte[] wire=wire();byte[] padded=new byte[wire.length+9];System.arraycopy(wire,0,padded,7,wire.length);ByteBuffer b=ByteBuffer.wrap(padded,7,wire.length).slice();check(b.get()==WirePacketType.SOUND_FRAME_V3,"type");SoundFrameV3Packet p=SoundFrameV3Packet.read(b);check(p!=null&&p.epoch==5&&p.frame.sequence==77&&p.frame.senderId.equals(ID)&&p.frame.maxDistance==12.5F&&p.frame.isWhispering(),"metadata");check(p.frame.opusData==padded,"parser copied audio");});
        run("all_frame_truncations_and_trailing_data_rejected",()->{byte[] w=wire();for(int n=1;n<w.length;n++){ByteBuffer b=ByteBuffer.wrap(Arrays.copyOf(w,n));b.get();check(SoundFrameV3Packet.read(b)==null,"prefix accepted "+n);}ByteBuffer b=ByteBuffer.wrap(Arrays.copyOf(w,w.length+1));b.get();check(SoundFrameV3Packet.read(b)==null,"trailer accepted");});
        run("invalid_epochs_rejected",()->{for(long e:new long[]{0,-1,Long.MIN_VALUE}){byte[]w=wire();ByteBuffer.wrap(w).putLong(1,e);ByteBuffer b=ByteBuffer.wrap(w);b.get();check(SoundFrameV3Packet.read(b)==null,"invalid epoch");}});
        run("reset_exact_length_and_roundtrip",()->{ByteBuffer b=ByteBuffer.allocate(25);new SoundResetV3Packet(ID,7).write(b);b.flip();check(b.get()==WirePacketType.SOUND_RESET_V3,"reset type");SoundResetV3Packet p=SoundResetV3Packet.read(b);check(p!=null&&p.epoch==7&&p.senderId.equals(ID),"reset metadata");for(int n=0;n<29;n++)if(n!=24)check(SoundResetV3Packet.read(ByteBuffer.allocate(n))==null,"reset length");});
        run("same_generation_and_monotonicity",()->{StreamEpochTracker t=new StreamEpochTracker();check(t.frame(ID,2)==StreamEpochTracker.Decision.NEW,"new");check(t.frame(ID,2)==StreamEpochTracker.Decision.SAME,"same");check(t.frame(ID,1)==StreamEpochTracker.Decision.REJECT,"regression");check(t.frame(ID,3)==StreamEpochTracker.Decision.NEW,"advance");});
        run("reset_before_first_frame_is_a_tombstone",()->{StreamEpochTracker t=new StreamEpochTracker();check(t.reset(ID,5),"reset");check(t.frame(ID,5)==StreamEpochTracker.Decision.REJECT,"resurrect");check(t.frame(ID,4)==StreamEpochTracker.Decision.REJECT,"old");check(t.frame(ID,6)==StreamEpochTracker.Decision.NEW,"next");});
        run("stale_reset_does_not_kill_successor",()->{StreamEpochTracker t=new StreamEpochTracker();t.frame(ID,8);check(!t.reset(ID,7)&&t.isActive(ID,8),"stale reset killed current");});
        run("bounded_tracker_never_evicts_tombstones",()->{StreamEpochTracker t=new StreamEpochTracker(2);t.reset(ID,1);t.frame(new UUID(3,4),1);check(t.frame(new UUID(5,6),1)==StreamEpochTracker.Decision.REJECT&&t.size()==2,"unbounded");check(t.frame(ID,1)==StreamEpochTracker.Decision.REJECT,"tombstone evicted");});
        run("clear_creates_a_new_session_domain",()->{StreamEpochTracker t=new StreamEpochTracker();t.frame(ID,999);t.clear();check(t.frame(ID,1)==StreamEpochTracker.Decision.NEW,"session stuck");});
        run("long_maximum_epoch_supported_without_wrap",()->{StreamEpochTracker t=new StreamEpochTracker();check(t.frame(ID,Long.MAX_VALUE)==StreamEpochTracker.Decision.NEW,"max");t.reset(ID,Long.MAX_VALUE);check(t.frame(ID,1)==StreamEpochTracker.Decision.REJECT,"wrapped");});
        run("concurrent_monotonic_updates",()->{StreamEpochTracker t=new StreamEpochTracker();ExecutorService pool=Executors.newFixedThreadPool(4);try{List<Future<?>> fs=new ArrayList<Future<?>>();for(int i=0;i<4;i++){final int start=i;fs.add(pool.submit(()->{for(int n=start+1;n<=1000;n+=4)t.frame(ID,n);}));}for(Future<?> f:fs)f.get(5,TimeUnit.SECONDS);check(t.isActive(ID,1000)&&t.size()==1,"lost monotonic update");}finally{pool.shutdownNow();}});
        run("seeded_parser_fuzz_is_bounded",()->{Random r=new Random(0x41564506L);for(int i=0;i<20000;i++){byte[]a=new byte[r.nextInt(1501)];r.nextBytes(a);SoundFrameV3Packet.read(ByteBuffer.wrap(a));SoundResetV3Packet.read(ByteBuffer.wrap(a));VoiceCapabilities.read(ByteBuffer.wrap(a));}});
        System.out.println("EPOCH_PROTOCOL_OK scenarios="+passed+" fuzz=20000");
    }
    static SoundFrameV2Packet frame(){return new SoundFrameV2Packet(ID,true,true,1,2,3,12.5F,77,new byte[]{1,2,3,4});}
    static byte[] wire(){SoundFrameV3Packet p=new SoundFrameV3Packet(5,frame());ByteBuffer b=ByteBuffer.allocate(p.byteSize());p.write(b);return b.array();}
}
