package dev.akashic.voice.voice.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.akashic.voice.voice.client.MicSequenceRecoverySmoke.Fixture;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.crypto.*;

/** Actual capture sink, VoiceClient, AES-GCM and loopback UDP. Never opens a microphone. */
public final class TransmitBoundarySmoke {
    interface Scenario { void run() throws Exception; }
    interface FixtureAction { void run(Fixture f) throws Exception; }
    static int passed;
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static void test(String name,Scenario scenario)throws Exception {
        scenario.run();passed++;System.out.println("PASS "+name);
    }
    public static void main(String[] args)throws Exception {
        MicGate.Mode mode=MicGate.mode();boolean muted=MicGate.isMuted(),ptt=MicGate.isPttPressed(),deaf=MicGate.isDeafened();
        try {
            test("capture_to_encrypted_udp_cancels_old_and_resumes",TransmitBoundarySmoke::captureNetwork);
            test("capture_waiting_udp_lock_keeps_original_authorization",TransmitBoundarySmoke::captureNetworkBlocked);
            test("authorized_udp_preserves_payload_and_sequence",TransmitBoundarySmoke::normal);
            test("mute_revokes_acquired_authorization",()->revoked(()->MicGate.setMuted(true)));
            test("rapid_mute_unmute_cannot_reauthorize",()->revoked(()->{MicGate.setMuted(true);MicGate.setMuted(false);}));
            test("mode_roundtrip_revokes_old_authorization",()->revoked(()->{MicGate.setMode(MicGate.Mode.PTT);MicGate.setMode(MicGate.Mode.ALWAYS);}));
            test("waiting_send_lock_observes_mute",()->blocked(()->MicGate.setMuted(true)));
            test("waiting_send_lock_observes_rapid_mute",()->blocked(()->{MicGate.setMuted(true);MicGate.setMuted(false);}));
            test("blocked_sender_rechecks_capture_identity",()->blockedWith(f->newSink(f,f.clientCrypto)));
            test("blocked_sender_rechecks_crypto_identity",()->blockedWith(f->f.set("crypto",f.serverCrypto)));
            test("blocked_sender_rechecks_authentication",()->blockedWith(f->f.set("authenticated",false)));
            test("blocked_sender_rechecks_stopped_capture",()->blockedWith(f->{
                Method stop=VoiceClient.class.getDeclaredMethod("stopMicCapture");stop.setAccessible(true);stop.invoke(f.client);
            }));
            test("replaced_capture_sink_cannot_send",TransmitBoundarySmoke::replaceCapture);
            test("stopped_capture_sink_cannot_send",TransmitBoundarySmoke::stopCapture);
            test("rekey_rejects_old_capture_callback",TransmitBoundarySmoke::rekey);
            test("unauthenticated_callback_has_no_cipher_effect",TransmitBoundarySmoke::unauthenticated);
            test("ptt_release_repress_revokes_ticket",TransmitBoundarySmoke::ptt);
            test("unchanged_controls_reuse_state",TransmitBoundarySmoke::unchanged);
            test("ptt_key_does_not_interrupt_always_mode",TransmitBoundarySmoke::irrelevantPtt);
            test("deafen_keeps_transmit_semantics",TransmitBoundarySmoke::deafen);
            test("null_mode_rejected_without_state_change",TransmitBoundarySmoke::nullMode);
            test("voice_threshold_rule_preserved",TransmitBoundarySmoke::voiceThreshold);
            test("repeated_cancel_resume_remains_contiguous",TransmitBoundarySmoke::repeat);
        } finally {MicGate.setMode(mode);MicGate.setMuted(muted);MicGate.setPttPressed(ptt);MicGate.setDeafened(deaf);}
        System.out.println("TRANSMIT_BOUNDARY_OK scenarios="+passed);
    }
    static void captureNetwork()throws Exception {
        try(Fixture f=new Fixture();CaptureAuthorizationSmoke.Fixture capture=
            new CaptureAuthorizationSmoke.Fixture(2,false,true,f.sink)) {
            capture.start();capture.encoder.awaitEntered();MicGate.setMuted(true);MicGate.setMuted(false);
            capture.encoder.release.countDown();capture.await();
            java.net.DatagramPacket wire=new java.net.DatagramPacket(new byte[1500],1500);
            f.receiver.receive(wire);java.nio.ByteBuffer plain=java.nio.ByteBuffer.allocate(1500);
            check(f.serverCrypto.open(java.nio.ByteBuffer.wrap(wire.getData(),0,wire.getLength()),plain),"capture ciphertext rejected");
            check(plain.get()==dev.akashic.voice.voice.common.WirePacketType.MIC_FRAME,"wrong capture packet type");
            MicFramePacket packet=MicFramePacket.read(plain);
            check(packet!=null && packet.sequence==0 && packet.opusLength==1 && packet.opusData[packet.opusOffset]==7,
                "fresh captured payload or sequence changed");
            check(f.clientCrypto.nextSendCounter()==1,"cancelled capture reached encryption");
            f.expectSilence();capture.checkClosed();
        }
    }
    static void captureNetworkBlocked()throws Exception {
        try(Fixture f=new Fixture();CaptureAuthorizationSmoke.Fixture capture=
            new CaptureAuthorizationSmoke.Fixture(1,false,true,f.sink)) {
            capture.start();capture.encoder.awaitEntered();
            synchronized(get(f.client,"sendLock")) {
                capture.encoder.release.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(capture.worker.getState()!=Thread.State.BLOCKED && capture.worker.isAlive() && System.nanoTime()<deadline)Thread.yield();
                check(capture.worker.getState()==Thread.State.BLOCKED,"capture did not reach network boundary");
                MicGate.setMuted(true);MicGate.setMuted(false);
            }
            capture.await();noSend(f);capture.checkClosed();
        }
    }
    static boolean send(Fixture f,MicCapture.FrameSink sink,MicGate.TransmissionState token) {
        return sink.sendAuthorizedFrame(999,f.opus,f.opus.length,token);
    }
    static Object get(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static MicCapture.FrameSink newSink(Fixture f,SessionCrypto crypto)throws Exception {
        Method method=VoiceClient.class.getDeclaredMethod("createCaptureSink",SessionCrypto.class);
        method.setAccessible(true);return (MicCapture.FrameSink)method.invoke(f.client,crypto);
    }
    static void noSend(Fixture f)throws Exception {
        check(f.clientCrypto.nextSendCounter()==0,"cancelled frame consumed cipher counter");
        check(((Integer)get(f.client,"nextMicSequence"))==0,"cancelled frame consumed media sequence");
        f.expectSilence();
    }
    static void normal()throws Exception {
        try(Fixture f=new Fixture()) {
            check(send(f,f.sink,MicGate.transmissionState()),"authorized frame blocked");
            MicFramePacket p=f.receiveFrame();check(p.sequence==0,"capture-local sequence reached network");
            check(Arrays.equals(f.opus,Arrays.copyOfRange(p.opusData,p.opusOffset,p.opusOffset+p.opusLength)),"payload changed");
        }
    }
    static void revoked(Runnable action)throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState before=MicGate.transmissionState();action.run();
            check(!send(f,f.sink,before),"invalid authorization accepted");noSend(f);
        }
    }
    static void blocked(Runnable action)throws Exception { blockedWith(f->action.run()); }
    static void blockedWith(FixtureAction action)throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState token=MicGate.transmissionState();
            AtomicReference<Throwable> failure=new AtomicReference<Throwable>();AtomicBoolean result=new AtomicBoolean(true);
            Thread thread=new Thread(()->{
                try{result.set(send(f,f.sink,token));}catch(Throwable t){failure.set(t);}
            },"AkashicVoice-QA-SendBarrier");thread.setDaemon(true);
            synchronized(get(f.client,"sendLock")) {
                thread.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(thread.getState()!=Thread.State.BLOCKED && thread.isAlive() && System.nanoTime()<deadline)Thread.yield();
                check(thread.getState()==Thread.State.BLOCKED,"sender did not reach send monitor");
                action.run(f); // Control mutation returns without waiting for the network lock.
            }
            thread.join(10000);check(!thread.isAlive(),"send thread stuck");
            if(failure.get()!=null)throw new AssertionError(failure.get());
            check(!result.get(),"authorization not rechecked after acquiring send lock");noSend(f);
        }
    }
    static void replaceCapture()throws Exception {
        try(Fixture f=new Fixture()) {
            MicCapture.FrameSink next=newSink(f,f.clientCrypto);MicGate.TransmissionState token=MicGate.transmissionState();
            check(!send(f,f.sink,token),"replaced capture accepted");noSend(f);
            check(send(f,next,token),"replacement capture blocked");check(f.receiveFrame().sequence==0,"replacement lost continuity");
        }
    }
    static void stopCapture()throws Exception {
        try(Fixture f=new Fixture()) {
            Method stop=VoiceClient.class.getDeclaredMethod("stopMicCapture");stop.setAccessible(true);stop.invoke(f.client);
            check(!send(f,f.sink,MicGate.transmissionState()),"stopped capture accepted");noSend(f);
        }
    }
    static void rekey()throws Exception {
        try(Fixture f=new Fixture()) {
            byte[] key=new byte[SessionKeys.MASTER_SECRET_BYTES],nonce=new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            Arrays.fill(key,(byte)42);
            try(SessionKeys keys=SessionKeys.derive(key,nonce,nonce);SessionCrypto next=SessionCrypto.forClient(keys)) {
                f.set("crypto",next);
                check(!send(f,f.sink,MicGate.transmissionState()),"old capture crossed rekey");
                check(next.nextSendCounter()==0,"old callback used new crypto");noSend(f);
            }finally{Arrays.fill(key,(byte)0);f.set("crypto",f.clientCrypto);}
        }
    }
    static void unauthenticated()throws Exception {
        try(Fixture f=new Fixture()) {
            f.set("authenticated",false);check(!send(f,f.sink,MicGate.transmissionState()),"unauthenticated media accepted");noSend(f);
            f.set("authenticated",true);check(send(f,f.sink,MicGate.transmissionState()),"authentication recovery failed");
            check(f.receiveFrame().sequence==0,"unauthenticated gap");
        }
    }
    static void ptt()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.setMode(MicGate.Mode.PTT);MicGate.setPttPressed(true);
            MicGate.TransmissionState token=MicGate.transmissionState();
            MicGate.setPttPressed(false);MicGate.setPttPressed(true);
            check(!send(f,f.sink,token),"PTT roundtrip reused old permit");noSend(f);
            check(send(f,f.sink,MicGate.transmissionState()),"new PTT press blocked");f.receiveFrame();
        }
    }
    static void unchanged()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState before=MicGate.transmissionState();
            for(int i=0;i<100000;i++){MicGate.setMode(MicGate.Mode.ALWAYS);MicGate.setMuted(false);}
            check(before==MicGate.transmissionState(),"unchanged controls churn authorization objects");
            check(send(f,f.sink,before),"unchanged state invalidated frame");f.receiveFrame();
        }
    }
    static void irrelevantPtt()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState before=MicGate.transmissionState();MicGate.setPttPressed(!MicGate.isPttPressed());
            check(before==MicGate.transmissionState(),"PTT key cancelled ALWAYS capture");
            check(send(f,f.sink,before),"irrelevant key interrupted audio");f.receiveFrame();
        }
    }
    static void deafen()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState before=MicGate.transmissionState();MicGate.setDeafened(true);
            check(send(f,f.sink,before),"listen-only toggle changed transmit contract");f.receiveFrame();MicGate.setDeafened(false);
        }
    }
    static void nullMode()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.TransmissionState before=MicGate.transmissionState();
            try{MicGate.setMode(null);throw new AssertionError("null mode accepted");}catch(NullPointerException expected){}
            check(before==MicGate.transmissionState(),"invalid mode changed state");
        }
    }
    static void voiceThreshold()throws Exception {
        try(Fixture f=new Fixture()) {
            MicGate.setMode(MicGate.Mode.VOICE);MicGate.TransmissionState before=MicGate.transmissionState();
            short[] pcm=new short[960];check(!MicGate.shouldTransmit(pcm,before),"silence crossed threshold");
            Arrays.fill(pcm,Short.MAX_VALUE);check(MicGate.shouldTransmit(pcm,before),"loud input rejected");
            MicGate.setMuted(true);check(!MicGate.shouldTransmit(pcm,before),"old VOICE state accepted");
        }
    }
    static void repeat()throws Exception {
        try(Fixture f=new Fixture()) {
            for(int i=0;i<128;i++) {
                MicGate.TransmissionState old=MicGate.transmissionState();MicGate.setMuted(true);MicGate.setMuted(false);
                check(!send(f,f.sink,old),"old state resurrected at iteration="+i);
                check(send(f,f.sink,MicGate.transmissionState()),"fresh send failed");
                check(f.receiveFrame().sequence==i,"cancelled audio consumed sequence");
            }
            check(f.clientCrypto.nextSendCounter()==128,"unexpected encrypted datagrams");
        }
    }
}
