package dev.akashic.voice.voice.client.playback;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.Fixture;
import static dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.*;

/** Full production crypto/client/decoder/queues; only the physical audio sink is fake. */
public final class IntegratedListeningSmoke {
    interface Test { void run() throws Exception; }
    static int passed;
    static void test(String name, Test action) throws Exception {
        action.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        String previous = AkashicVoiceConfig.opusBackend();
        AkashicVoiceConfig.setOpusBackend("java");
        try {
            test("deafen_keeps_live_decoder_sequence", IntegratedListeningSmoke::continuity);
            test("epoch_and_delayed_packet_while_deafened", IntegratedListeningSmoke::epochs);
            test("rapid_toggle_rejects_queued_pcm", IntegratedListeningSmoke::rapid);
            test("full_task_queue_does_not_block_controls", IntegratedListeningSmoke::saturation);
            test("rekey_while_deafened", IntegratedListeningSmoke::rekeyWhileDeafened);
            test("two_speakers_resume_independently", IntegratedListeningSmoke::twoSpeakers);
            test("inflight_frame_cannot_cross_toggle", IntegratedListeningSmoke::inflight);
        } finally { AkashicVoiceConfig.setOpusBackend(previous); }
        System.out.println("INTEGRATED_LISTENING_OK scenarios=" + passed);
    }
    static void state(Fixture f) throws Exception {
        call(f.output, "reconcileListeningState", new Class<?>[0]);
    }
    static void continuity() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,100); f.flush();
            f.output.setDeafened(true); state(f);
            f.burst(A,1,103); f.flush(); f.pump();
            check(f.frames()==0 && f.output.acquireFrame()==null,"deafened audio escaped");
            f.output.setDeafened(false); f.burst(A,1,106); f.flush(); f.pump();
            check(f.frames()==3 && f.streams().get(A).lateCount()==0,"decoder sequence was rewound");
        }
    }
    static void epochs() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,0); f.flush(); f.pump();
            byte[] delayed=f.sealFrame(A,1,3);
            f.output.setDeafened(true); state(f);
            f.deliver(f.sealReset(A,1)); f.burst(A,2,0); f.flush(); f.pump();
            f.output.setDeafened(false); f.burst(A,2,3); f.deliver(delayed); f.flush(); f.pump();
            check(f.frames()==6 && f.made.get(0).closed,"old voice resumed");
            check(f.streams().get(A).lateCount()==0,"stale epoch entered decoder");
        }
    }
    static void rapid() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,0); f.flush();
            check(f.pending().size()==3,"missing queued baseline");
            f.output.setDeafened(true); f.output.setDeafened(false); f.pump();
            check(f.frames()==0,"stale queued PCM crossed a toggle");
            f.burst(A,1,3); f.flush(); f.pump(); check(f.frames()==3,"fresh audio did not resume");
        }
    }
    @SuppressWarnings("unchecked")
    static void saturation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,0); f.flush(); f.pump();
            BlockingQueue<Runnable> tasks=(BlockingQueue<Runnable>)get(f.output,"tasks");
            while(tasks.offer(() -> {})) {}
            f.output.setSpatial(false);
            call(f.output,"reconcileOutputControls",new Class<?>[0]);
            check(!f.made.get(0).spatial,"spatial control lost under pressure");
            f.output.setDeafened(true); state(f);
            check(f.made.get(0).closed && tasks.size()==64,"deafen used a lossy task");
        }
    }
    static void rekeyWhileDeafened() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,8,100); f.flush(); f.pump();
            f.output.setDeafened(true); state(f); f.rekey(2); f.offer();
            f.burst(A,1,0); f.flush(); f.pump();
            f.output.setDeafened(false); f.burst(A,1,3); f.flush(); f.pump();
            check(f.frames()==6 && f.streams().get(A).lateCount()==0,"rekey revived old state");
        }
    }
    static void twoSpeakers() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,0); f.burst(B,1,0); f.flush(); f.pump();
            f.output.setDeafened(true); state(f);
            f.burst(A,1,3); f.burst(B,1,3); f.flush(); f.pump();
            check(f.frames()==6,"silenced speakers remained audible");
            f.output.setDeafened(false);
            f.burst(A,1,6); f.burst(B,1,6); f.flush(); f.pump();
            check(f.frames()==12,"one speaker failed to resume");
            check(get(f.streams().get(A),"decoder")!=get(f.streams().get(B),"decoder"),"shared decoder state");
        }
    }
    @SuppressWarnings("unchecked")
    static void inflight() throws Exception {
        try (Fixture f = new Fixture()) {
            PlaybackFrame old=f.output.acquireFrame(); check(old!=null,"empty initial pool");
            f.output.setDeafened(true); f.output.setDeafened(false);
            check(!f.output.submitFrame(old,A,true,0,0,0),"inflight old PCM accepted");
            check(f.pending().isEmpty(),"old frame entered queue");
            BlockingQueue<PlaybackFrame> pool=(BlockingQueue<PlaybackFrame>)get(f.output,"freeFrames");
            check(pool.size()==128,"frame leaked during toggle");
        }
    }
}
