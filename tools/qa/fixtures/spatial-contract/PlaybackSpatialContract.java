package dev.akashic.voice.voice.client.playback;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import org.lwjgl.openal.AL10;

/** Production classes compiled against real LWJGL; only AL10 calls are recorded. */
public final class PlaybackSpatialContract {
    interface Scenario { void run() throws Exception; }
    static int passed,failed;
    static final UUID A=new UUID(1,1),B=new UUID(2,2);
    static void check(boolean ok,String text) { if(!ok)throw new AssertionError(text); }
    static void run(String name,Scenario test) {
        try { test.run();passed++;System.out.println("PASS "+name); }
        catch(Throwable error) { failed++;System.out.println("FAIL "+name+": "+error); }
        finally { AL10.sources.clear();AL10.buffers.clear(); }
    }
    public static void main(String[] args) throws Exception {
        File expected=new File(args[0]).getCanonicalFile();
        for(Class<?> c:new Class<?>[]{PlaybackChannel.class,AudioPlayback.class}) {
            File origin=new File(c.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile();
            check(expected.equals(origin),"Production class came from unexpected origin: "+c+" "+origin);
        }
        run("restore_cached_position_immediately",PlaybackSpatialContract::roundtrip);
        run("stationary_packet_after_reenable",PlaybackSpatialContract::stationary);
        run("movement_while_flat_stays_listener_centered",PlaybackSpatialContract::flatMovement);
        run("latest_flat_position_restored",PlaybackSpatialContract::latest);
        run("default_origin_is_valid",PlaybackSpatialContract::origin);
        run("unchanged_state_does_not_add_native_calls",PlaybackSpatialContract::idempotent);
        run("repeated_toggle_cycles",PlaybackSpatialContract::cycles);
        run("speaker_sources_remain_independent",PlaybackSpatialContract::independent);
        run("attenuation_parameters_preserved",PlaybackSpatialContract::attenuation);
        run("pcm_bytes_and_source_identity_preserved",PlaybackSpatialContract::pcm);
        run("native_queue_remains_bounded",PlaybackSpatialContract::bounded);
        run("playback_global_toggle_stationary_speaker",PlaybackSpatialContract::globalStationary);
        run("playback_caches_movement_while_disabled",PlaybackSpatialContract::globalMovement);
        run("group_stays_flat_across_global_toggles",PlaybackSpatialContract::group);
        run("seeded_movement_toggle_model",PlaybackSpatialContract::model);
        System.out.println("SPATIAL_CONTRACT_RESULT passed="+passed+" failed="+failed);
        if(failed!=0)throw new AssertionError("Spatial regressions="+failed);
    }
    static AL10.Source source(PlaybackChannel channel) throws Exception {
        return AL10.sources.get((Integer)get(channel,"sourceId"));
    }
    static void position(AL10.Source s,boolean relative,float x,float y,float z) {
        check(s.relative==relative,"Relative mode mismatch");
        check(s.x==x&&s.y==y&&s.z==z,"expected=("+x+","+y+","+z+") actual=("+s.x+","+s.y+","+s.z+")");
    }
    static void roundtrip() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setPosition(12,64,-9);c.setSpatial(false);
        position(source(c),true,0,0,0);c.setSpatial(true);position(source(c),false,12,64,-9);c.close();
    }
    static void stationary() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setPosition(-80,33,91);c.setSpatial(false);
        c.setSpatial(true);c.setPosition(-80,33,91);position(source(c),false,-80,33,91);c.close();
    }
    static void flatMovement() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setSpatial(false);c.setPosition(90,50,40);
        position(source(c),true,0,0,0);c.close();
    }
    static void latest() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setPosition(1,2,3);c.setSpatial(false);
        c.setPosition(7,8,9);c.setPosition(-14,12,16);c.setSpatial(true);
        position(source(c),false,-14,12,16);c.close();
    }
    static void origin() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setSpatial(false);c.setSpatial(true);
        c.setPosition(0,0,0);position(source(c),false,0,0,0);c.close();
    }
    static void idempotent() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setPosition(1,2,3);
        int calls=source(c).positionCalls,relative=source(c).relativeCalls;
        for(int i=0;i<100;i++){c.setPosition(1,2,3);c.setSpatial(true);}
        check(calls==source(c).positionCalls&&relative==source(c).relativeCalls,"Unchanged state adds native work");
        c.setSpatial(false);calls=source(c).positionCalls;relative=source(c).relativeCalls;
        for(int i=0;i<100;i++)c.setSpatial(false);
        check(calls==source(c).positionCalls&&relative==source(c).relativeCalls,"Flat no-op adds work");c.close();
    }
    static void cycles() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setPosition(-128,256,512);
        for(int i=0;i<256;i++) {
            c.setSpatial(false);position(source(c),true,0,0,0);
            c.setSpatial(true);position(source(c),false,-128,256,512);
        }
        c.close();
    }
    static void independent() throws Exception {
        PlaybackChannel a=new PlaybackChannel(4),b=new PlaybackChannel(4);
        a.setPosition(1,2,3);b.setPosition(8,9,10);a.setSpatial(false);a.setSpatial(true);
        position(source(a),false,1,2,3);position(source(b),false,8,9,10);a.close();b.close();
    }
    static void attenuation() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);c.setMaxDistance(12.5f);
        c.setPosition(50,60,70);c.setSpatial(false);c.setSpatial(true);
        AL10.Source s=source(c);check(s.reference==4&&s.maxDistance==12.5f&&s.rolloff==1,"Attenuation changed");c.close();
    }
    static void pcm() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);AL10.Source s=source(c);
        short[] pcm=new short[960];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(i*37);
        c.enqueue(pcm);c.setPosition(1,2,3);c.setSpatial(false);c.setSpatial(true);c.enqueue(pcm);
        check(source(c)==s&&AL10.sources.size()==1,"Toggle recreated source");
        for(int id:s.queued)check(Arrays.equals(pcm,AL10.buffers.get(id)),"PCM changed");
        check(s.queued.size()==2,"Unexpected PCM queue changes");c.close();
        check(AL10.buffers.isEmpty()&&AL10.sources.isEmpty(),"Close leaked fixture resources");
    }
    static void bounded() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);for(int i=0;i<40;i++)c.enqueue(new short[960]);
        check(source(c).queued.size()==8&&AL10.buffers.size()==8,"Queue cap changed");c.close();
    }
    static void globalStationary() throws Exception {
        try(Output f=new Output()) {
            f.frame(A,true,12,64,-9);f.toggle(false);position(f.source(A),true,0,0,0);
            f.toggle(true);position(f.source(A),false,12,64,-9);
        }
    }
    static void globalMovement() throws Exception {
        try(Output f=new Output()) {
            f.frame(A,true,1,2,3);f.toggle(false);f.frame(A,true,50,70,-30);
            position(f.source(A),true,0,0,0);f.toggle(true);position(f.source(A),false,50,70,-30);
        }
    }
    static void group() throws Exception {
        try(Output f=new Output()) {
            f.frame(A,false,500,600,700);f.frame(B,true,5,6,7);
            position(f.source(A),true,0,0,0);f.toggle(false);f.toggle(true);
            position(f.source(A),true,0,0,0);position(f.source(B),false,5,6,7);
        }
    }
    static void model() throws Exception {
        PlaybackChannel c=new PlaybackChannel(4);Random random=new Random(0x41565350L);
        boolean spatial=true;float x=0,y=0,z=0;
        for(int i=0;i<10000;i++) {
            if(random.nextBoolean()) {
                spatial=random.nextBoolean();c.setSpatial(spatial);
            } else {
                x=random.nextInt(60000)-30000;y=random.nextInt(1024)-512;z=random.nextInt(60000)-30000;
                c.setPosition(x,y,z);
            }
            position(source(c),!spatial,spatial?x:0,spatial?y:0,spatial?z:0);
        }
        c.close();
    }
    static Object get(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static Object call(Object object,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try {return method.invoke(object,args);}
        catch(InvocationTargetException error) {
            Throwable cause=error.getCause();if(cause instanceof Error)throw (Error)cause;throw (Exception)cause;
        }
    }
    static final class Output implements AutoCloseable {
        final AudioPlayback output=new AudioPlayback(PlaybackChannel::new);
        Output() throws Exception {
            Field f=AudioPlayback.class.getDeclaredField("running");f.setAccessible(true);f.setBoolean(output,true);
        }
        @SuppressWarnings("unchecked") void toggle(boolean enabled) throws Exception {
            output.setSpatial(enabled);BlockingQueue<Runnable> tasks=(BlockingQueue<Runnable>)get(output,"tasks");
            Runnable task;while((task=tasks.poll())!=null)task.run();
        }
        @SuppressWarnings("unchecked") void frame(UUID sender,boolean spatial,float x,float y,float z) throws Exception {
            PlaybackFrame frame=output.acquireFrame();check(frame!=null,"PCM pool exhausted");
            check(output.submitFrame(frame,sender,spatial,x,y,z,48,false,1),"Frame rejected");
            frame=((BlockingQueue<PlaybackFrame>)get(output,"pendingFrames")).poll();
            try {call(output,"processFrame",new Class<?>[]{PlaybackFrame.class},frame);}
            finally {output.releaseFrame(frame);}
        }
        @SuppressWarnings("unchecked") AL10.Source source(UUID sender) throws Exception {
            Map<UUID,AudioPlayback.Channel> channels=(Map<UUID,AudioPlayback.Channel>)get(output,"channels");
            return PlaybackSpatialContract.source((PlaybackChannel)channels.get(sender));
        }
        public void close() throws Exception {call(output,"shutdownOpenAl",new Class<?>[0]);}
    }
}
