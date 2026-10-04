package dev.akashic.voice.voice.client.playback;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.Fixture;
import static dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.*;

/** Actual playback/control classes plus encrypted client flow; no physical audio. */
public final class SpeakerControlsSmoke {
    interface Test { void run() throws Exception; }
    static int passed;
    static void test(String name, Test action) throws Exception {
        action.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        test("defaults_and_invalid_values", SpeakerControlsSmoke::defaults);
        test("immutable_snapshots_and_noop_publication", SpeakerControlsSmoke::snapshots);
        test("mute_tokens_do_not_reappear", SpeakerControlsSmoke::tokens);
        test("positive_volume_preserves_pcm_identity", SpeakerControlsSmoke::positive);
        test("bounded_directory_keeps_existing_settings_editable", SpeakerControlsSmoke::bounded);
        test("retired_default_speaker_reclaims_capacity", SpeakerControlsSmoke::reclaimDefaultCapacity);
        test("customized_speaker_survives_stream_retire", SpeakerControlsSmoke::preserveCustomRetire);
        test("stored_speaker_never_uses_unknown_default_token", SpeakerControlsSmoke::storedTokenUnique);
        test("legacy_drop_reclaims_default_even_with_full_task_queue", SpeakerControlsSmoke::legacyReclaim);
        test("legacy_reclaim_rejects_inflight_pcm_after_reobserve", SpeakerControlsSmoke::legacyStaleBarrier);
        test("concurrent_updates_keep_all_speakers", SpeakerControlsSmoke::concurrent);
        test("gain_composes_with_master_without_touching_peers", SpeakerControlsSmoke::gains);
        test("muted_speaker_does_not_allocate_pcm", SpeakerControlsSmoke::noPcm);
        test("rapid_mute_rejects_queued_pcm", SpeakerControlsSmoke::queued);
        test("rapid_mute_rejects_inflight_pcm", SpeakerControlsSmoke::inflight);
        test("wrong_sender_cannot_relabel_acquired_pcm", SpeakerControlsSmoke::wrongSender);
        test("mute_applies_without_pcm_and_with_full_tasks", SpeakerControlsSmoke::saturation);
        test("zero_percent_has_the_same_stale_audio_barrier", SpeakerControlsSmoke::zero);
        test("gain_failure_retries_after_revert", SpeakerControlsSmoke::gainFailure);
        test("producer_never_calls_native_channel", SpeakerControlsSmoke::producer);
        test("group_audio_keeps_nonspatial_semantics", SpeakerControlsSmoke::group);
        test("deafen_preserves_individual_settings", SpeakerControlsSmoke::deafen);
        test("fresh_session_does_not_inherit_another_servers_controls", SpeakerControlsSmoke::fresh);
        String previous=AkashicVoiceConfig.opusBackend(); AkashicVoiceConfig.setOpusBackend("java");
        try {
            test("encrypted_two_speakers_mute_independently", SpeakerControlsSmoke::encrypted);
            test("encrypted_muted_decoder_keeps_sequence", SpeakerControlsSmoke::decodeContinuity);
            test("epoch_changes_do_not_forget_individual_mute", SpeakerControlsSmoke::epochs);
            test("queued_opus_cannot_cross_rapid_mute", SpeakerControlsSmoke::queuedOpus);
            test("muted_opus_cannot_reappear_after_resume", SpeakerControlsSmoke::mutedOpus);
            test("positive_volume_change_keeps_buffered_opus", SpeakerControlsSmoke::bufferedPositive);
        } finally { AkashicVoiceConfig.setOpusBackend(previous); }
        System.out.println("SPEAKER_CONTROLS_OK scenarios="+passed);
    }
    static void defaults() {
        SpeakerControls c=new SpeakerControls();
        check(c.state(A).percent()==100 && !c.state(A).muted(),"wrong defaults");
        check(!c.update(A,-1,false) && !c.update(A,101,false) && !c.update(null,40,false),"invalid accepted");
        check(c.snapshot().speakers().isEmpty(),"invalid input changed directory");
        check(c.update(A,25,true) && c.state(A).muted() && c.state(B).audible(),"isolation broken");
    }
    static void snapshots() {
        SpeakerControls c=new SpeakerControls(); c.observe(A);
        SpeakerControls.Snapshot before=c.snapshot(); c.observe(A); c.update(A,100,false);
        check(c.snapshot()==before,"noop allocated snapshot");
        c.update(A,40,false); check(before.state(A).percent()==100,"old snapshot mutated");
        try { before.speakers().clear(); throw new AssertionError("mutable snapshot"); }
        catch(UnsupportedOperationException expected) { }
    }
    static void tokens() {
        SpeakerControls c=new SpeakerControls(); Object old=c.observe(A).token;
        c.update(A,100,true); Object muted=c.state(A).token;
        c.update(A,100,false);
        check(c.state(A).token!=old && c.state(A).token!=muted,"token resurrected");
        c.update(A,0,false); Object zero=c.state(A).token;
        c.update(A,100,false); check(c.state(A).token!=zero,"zero-volume token reused");
    }
    static void positive() {
        SpeakerControls c=new SpeakerControls(); Object old=c.observe(A).token;
        for(int i=1;i<=100;i++) { c.update(A,i,false); check(c.state(A).token==old,"volume retired valid PCM"); }
    }
    static void bounded() {
        SpeakerControls c=new SpeakerControls();
        for(int i=0;i<SpeakerControls.MAX_SPEAKERS;i++) c.observe(new UUID(3,i));
        UUID overflow=new UUID(4,4); c.observe(overflow);
        check(c.snapshot().speakers().size()==SpeakerControls.MAX_SPEAKERS,"unbounded speaker state");
        check(!c.update(overflow,50,true),"overflow setting reported success");
        check(c.update(new UUID(3,0),50,true),"existing setting locked at capacity");
    }
    static void reclaimDefaultCapacity() throws Exception { try(Harness h=new Harness()) {
        UUID first=new UUID(30,1); Object oldToken=h.settings.observe(first).token;
        for(int i=1;i<SpeakerControls.MAX_SPEAKERS;i++) h.settings.observe(new UUID(30,i+1));
        UUID overflow=new UUID(31,1); h.settings.observe(overflow);
        check(!h.settings.snapshot().speakers().contains(overflow),"baseline did not reach capacity");
        h.output.retireStream(first,1L);
        check(!h.settings.snapshot().speakers().contains(first),"retired default speaker kept capacity");
        Object overflowToken=h.settings.observe(overflow).token;
        check(h.settings.snapshot().speakers().contains(overflow)&&overflowToken!=oldToken,"capacity was not reusable");
        Object reentered=h.settings.observe(first).token;
        check(reentered!=oldToken,"retired speaker reused stale token");
    }}
    static void preserveCustomRetire() throws Exception { try(Harness h=new Harness()) {
        h.settings.observe(A); h.settings.update(A,37,true); Object token=h.settings.state(A).token;
        h.output.retireStream(A,1L);
        check(h.settings.snapshot().speakers().contains(A),"custom control was reclaimed");
        check(h.settings.state(A).percent()==37&&h.settings.state(A).muted()&&h.settings.state(A).token==token,"custom control changed");
    }}
    static void storedTokenUnique() {
        SpeakerControls c=new SpeakerControls(); Object unknown=c.state(A).token;
        check(c.update(A,50,false),"custom insert rejected");
        check(c.snapshot().speakers().contains(A)&&c.state(A).token!=unknown,"stored speaker reused unknown default token");
    }
    @SuppressWarnings("unchecked") static void legacyReclaim() throws Exception { try(Harness h=new Harness()) {
        h.settings.observe(A); BlockingQueue<Runnable> tasks=(BlockingQueue<Runnable>)get(h.output,"tasks");
        while(tasks.offer(()->{})){} h.output.dropChannel(A);
        check(!h.settings.snapshot().speakers().contains(A),"legacy drop depended on lossy task queue for control reclamation");
        check(tasks.size()==64,"legacy drop unexpectedly changed saturated task queue");
    }}
    static void legacyStaleBarrier() throws Exception { try(Harness h=new Harness()) {
        PlaybackFrame stale=h.output.acquireFrame(A); check(stale!=null,"stale frame unavailable");
        Object oldToken=h.settings.state(A).token; h.output.dropChannel(A);
        Object fresh=h.settings.observe(A).token; check(fresh!=oldToken,"reobserve reused old token");
        check(!h.output.submitFrame(stale,A,true,0,0,0),"pre-retire legacy PCM survived control reclamation");
        check(h.pool()==128,"rejected stale frame leaked pool entry");
    }}

    static void concurrent() throws Exception {
        SpeakerControls c=new SpeakerControls(); AtomicReference<Throwable> failure=new AtomicReference<>();
        CountDownLatch start=new CountDownLatch(1); List<Thread> threads=new ArrayList<>();
        for(int i=0;i<4;i++) { final UUID id=new UUID(5,i);
            Thread t=new Thread(()->{try { start.await(); for(int n=0;n<200;n++) c.update(id,n%101,(n&1)==0);
                c.update(id,37,true); } catch(Throwable x){failure.set(x);} }); t.setDaemon(true);threads.add(t);t.start(); }
        start.countDown(); for(Thread t:threads){t.join(5000);check(!t.isAlive(),"writer hung");}
        check(failure.get()==null && c.snapshot().speakers().size()==4,"concurrent state lost");
        for(UUID id:c.snapshot().speakers())check(c.state(id).percent()==37&&c.state(id).muted(),"torn state");
    }
    static void gains() throws Exception { try(Harness h=new Harness()) {
        h.output.setListenerGain(2f); h.settings.update(A,25,false);
        h.frame(A,true);h.frame(B,true); check(h.made.get(0).gain==0.5f&&h.made.get(1).gain==2f,"gain multiplication wrong");
        h.settings.update(A,50,false);h.controls(); check(h.made.get(0).gain==1f&&h.made.get(1).gain==2f,"peer gain changed");
    }}
    static void noPcm() throws Exception { try(Harness h=new Harness()) {
        h.settings.update(A,75,true); check(h.output.acquireFrame(A)==null,"muted PCM allocated");
        h.frame(B,true);check(h.made.size()==1,"muted sink created");check(h.pool()==128,"pool leaked");
    }}
    static void queued() throws Exception { try(Harness h=new Harness()) {
        h.queue(A,true);h.queue(B,true);h.settings.update(A,100,true);h.settings.update(A,100,false);h.pump();
        check(h.frames()==1&&h.output.droppedFrameCount()==1,"queued old PCM replayed");
        h.frame(A,true);check(h.frames()==2,"fresh PCM did not resume");
    }}
    static void inflight() throws Exception { try(Harness h=new Harness()) {
        PlaybackFrame old=h.output.acquireFrame(A);h.settings.update(A,100,true);h.settings.update(A,100,false);
        check(!h.output.submitFrame(old,A,true,0,0,0),"inflight PCM crossed mute");check(h.pool()==128,"inflight frame leaked");
    }}
    static void wrongSender() throws Exception { try(Harness h=new Harness()) {
        PlaybackFrame f=h.output.acquireFrame(A);
        check(!h.output.submitFrame(f,B,true,0,0,0)&&h.pool()==128,"frame was reassigned to another UUID");
    }}
    @SuppressWarnings("unchecked") static void saturation() throws Exception { try(Harness h=new Harness()) {
        h.frame(A,true);h.frame(B,true);BlockingQueue<Runnable> tasks=(BlockingQueue<Runnable>)get(h.output,"tasks");
        while(tasks.offer(()->{})){} h.settings.update(A,100,true);h.controls();
        check(h.made.get(0).closed&&!h.made.get(1).closed&&tasks.size()==64,"mute used lossy queue or closed peer");
    }}
    static void zero() throws Exception { try(Harness h=new Harness()) {
        h.queue(A,true);h.settings.update(A,0,false);h.settings.update(A,100,false);h.pump();
        check(h.frames()==0&&h.pool()==128,"zero-volume stale PCM replayed");
    }}
    static void gainFailure() throws Exception { try(Harness h=new Harness()) {
        h.frame(A,true);h.frame(B,true);h.made.get(1).failGain=true;
        h.settings.update(A,40,false);h.settings.update(B,40,false);h.controls();
        check(h.made.get(0).gain==0.4f,"healthy peer was blocked");
        h.settings.update(A,100,false);h.settings.update(B,100,false);h.made.get(1).failGain=false;h.controls();
        check(h.made.get(0).gain==1f&&h.made.get(1).gain==1f,"partial revert left stale volume");
    }}
    static void producer() throws Exception { try(Harness h=new Harness()) {
        h.frame(A,true);int n=h.made.get(0).gainCalls;h.settings.update(A,20,false);
        check(h.made.get(0).gainCalls==n&&!h.made.get(0).closed,"UI called native sink");
        h.controls();check(h.made.get(0).gain==0.2f,"worker ignored publication");
    }}
    static void group() throws Exception { try(Harness h=new Harness()) {
        h.settings.update(A,50,false);h.frame(A,false);
        check(!h.made.get(0).spatial&&h.made.get(0).gain==0.5f,"group semantics changed");
    }}
    static void deafen() throws Exception { try(Harness h=new Harness()) {
        h.settings.update(A,30,false);h.frame(A,true);h.output.setDeafened(true);
        call(h.output,"reconcileListeningState",new Class<?>[0]);h.output.setDeafened(false);h.frame(A,true);
        check(h.made.get(0).closed&&h.made.get(1).gain==0.3f,"global deafen lost preferences");
    }}
    static void fresh() throws Exception { try(Harness a=new Harness();Harness b=new Harness()) {
        a.settings.update(A,30,true);check(b.settings.state(A).audible()&&b.settings.state(A).percent()==100,"session settings leaked");
        PlaybackFrame old=a.output.acquireFrame(B);
        check(!b.output.submitFrame(old,B,true,0,0,0),"cross-session PCM accepted");
    }}
    static void encrypted() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.burst(A,1,0);f.burst(B,1,0);f.flush();f.pump();
        Object decoder=get(f.streams().get(A),"decoder");f.output.speakerControls().update(A,100,true);
        call(f.output,"reconcileOutputControls",new Class<?>[0]);
        f.burst(A,1,3);f.burst(B,1,3);f.flush();f.pump();check(f.frames()==9,"muted voice or peer isolation failed");
        f.output.speakerControls().update(A,100,false);f.burst(A,1,6);f.burst(B,1,6);f.flush();f.pump();
        check(f.frames()==15&&get(f.streams().get(A),"decoder")==decoder,"mute reset the codec");
        check(f.streams().get(A).lateCount()==0,"mute rewound sequence");
    }}
    static void decodeContinuity() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.output.speakerControls().update(A,100,true);f.burst(A,1,100);f.flush();f.pump();
        check(f.frames()==0&&f.output.speakerControls().snapshot().speakers().contains(A),"muted stream lost identity");
        f.output.speakerControls().update(A,100,false);f.burst(A,1,103);f.flush();f.pump();
        check(f.frames()==3&&f.streams().get(A).lateCount()==0,"silent decoder failed to resume");
    }}
    static void epochs() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.burst(A,1,0);f.flush();f.pump();byte[] old=f.sealFrame(A,1,3);
        f.output.speakerControls().update(A,100,true);call(f.output,"reconcileOutputControls",new Class<?>[0]);
        f.burst(A,2,0);f.deliver(old);f.flush();f.pump();check(f.frames()==3,"new epoch bypassed mute");
        f.output.speakerControls().update(A,100,false);f.burst(A,2,3);f.flush();f.pump();
        check(f.frames()==6&&f.streams().get(A).lateCount()==0,"retired audio revived");
    }}
    static void queuedOpus() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.deliver(f.sealFrame(A,1,100));check(f.pending().isEmpty(),"expected encoded jitter only");
        f.output.speakerControls().update(A,100,true);f.output.speakerControls().update(A,100,false);
        f.flush();f.pump();check(f.frames()==0,"old compressed audio crossed the mute boundary");
        f.burst(A,1,101);f.flush();f.pump();check(f.frames()==3&&f.streams().get(A).lateCount()==0,"resume lost sequence");
    }}
    static void mutedOpus() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.output.speakerControls().update(A,100,true);f.deliver(f.sealFrame(A,1,200));
        f.output.speakerControls().update(A,100,false);f.flush();f.pump();
        check(f.frames()==0,"audio received while muted was replayed");
    }}
    static void bufferedPositive() throws Exception { try(Fixture f=new Fixture()) {
        f.offer();f.deliver(f.sealFrame(A,1,300));f.output.speakerControls().update(A,50,false);
        f.output.speakerControls().update(A,100,false);f.flush();f.pump();
        check(f.frames()==1,"ordinary positive volume change discarded audio");
    }}
    static final class Sink implements AudioPlayback.Channel {
        boolean closed,spatial,failGain;int frames,gainCalls;float gain=1;
        public void setSpatial(boolean v){spatial=v;}
        public void setPosition(float x,float y,float z){}
        public void setMaxDistance(float v){}
        public void setGain(float v){gainCalls++;if(failGain)throw new IllegalStateException("synthetic gain failure");gain=v;}
        public void enqueue(short[] pcm){check(!closed&&pcm.length==960,"invalid PCM sink");frames++;}
        public void close(){closed=true;}
    }
    static final class Harness implements AutoCloseable {
        final List<Sink> made=new ArrayList<>();
        final AudioPlayback output=new AudioPlayback(distance->{Sink s=new Sink();made.add(s);return s;});
        final SpeakerControls settings=output.speakerControls();
        Harness() throws Exception {set(output,"running",true);}
        @SuppressWarnings("unchecked") BlockingQueue<PlaybackFrame> pending()throws Exception {
            return (BlockingQueue<PlaybackFrame>)get(output,"pendingFrames");
        }
        @SuppressWarnings("unchecked") int pool()throws Exception {return ((BlockingQueue<PlaybackFrame>)get(output,"freeFrames")).size();}
        void controls()throws Exception {call(output,"reconcileOutputControls",new Class<?>[0]);}
        void queue(UUID id,boolean spatial)throws Exception {
            PlaybackFrame frame=output.acquireFrame(id);check(frame!=null,"frame unavailable");
            check(output.submitFrame(frame,id,spatial,12,64,-9,48,false,1),"frame rejected");
        }
        void pump()throws Exception {
            controls();PlaybackFrame frame;
            while((frame=pending().poll())!=null){try{call(output,"processFrame",new Class<?>[]{PlaybackFrame.class},frame);}
                finally{output.releaseFrame(frame);}}
        }
        void frame(UUID id,boolean spatial)throws Exception {queue(id,spatial);pump();}
        int frames(){int n=0;for(Sink s:made)n+=s.frames;return n;}
        public void close()throws Exception {call(output,"shutdownOpenAl",new Class<?>[0]);}
    }
}
