package dev.akashic.voice.voice.client.playback;

import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.VoiceClient;
import dev.akashic.voice.voice.client.MicCapture;
import dev.akashic.voice.voice.client.jitter.DecoderStream;
import dev.akashic.voice.voice.common.*;
import dev.akashic.voice.voice.common.codec.*;
import dev.akashic.voice.voice.common.crypto.*;

/** Production VoiceClient/crypto/jitter/decoder/playback queue. Only the physical audio sink is fake. */
public final class EpochClientFlowSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static final UUID A = new UUID(1, 101), B = new UUID(2, 202);
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void run(String name, Scenario test) throws Exception {
        test.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        String previous = AkashicVoiceConfig.opusBackend();
        AkashicVoiceConfig.setOpusBackend("java");
        try {
            run("offered_capabilities_use_encrypted_udp", EpochClientFlowSmoke::capabilityOffer);
            run("legacy_server_fallback_remains", EpochClientFlowSmoke::legacy);
            run("epoch_frame_before_ack_is_supported", EpochClientFlowSmoke::earlyFrame);
            run("unsolicited_epoch_frame_is_rejected", EpochClientFlowSmoke::unsolicited);
            run("handoff_loses_first_frame_without_replaying_queued_audio", EpochClientFlowSmoke::handoff);
            run("delayed_authenticated_old_generation_is_rejected", EpochClientFlowSmoke::lateFrame);
            run("reset_before_first_frame_and_stale_reset", EpochClientFlowSmoke::resets);
            run("legacy_downgrade_cannot_revive_old_stream", EpochClientFlowSmoke::downgrade);
            run("speaker_decoders_are_independent", EpochClientFlowSmoke::speakers);
            run("retirement_survives_full_control_queue_and_closes_sink", EpochClientFlowSmoke::fullTasks);
            run("pcm_pool_returns_all_frames_after_retirement", EpochClientFlowSmoke::pool);
            run("closed_decoder_rejects_late_maintenance_work", EpochClientFlowSmoke::closedDecoder);
            run("new_crypto_session_accepts_epoch_one_again", EpochClientFlowSmoke::rekey);
        } finally { AkashicVoiceConfig.setOpusBackend(previous); }
        System.out.println("EPOCH_CLIENT_FLOW_OK scenarios=" + passed);
    }
    static void capabilityOffer() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer();
            check(f.clientCrypto.nextSendCounter() == 1, "offer not encrypted exactly once");
            f.deliver(f.sealCapabilities(VoiceCapabilities.SUPPORTED));
            check((Boolean) get(f.client, "capabilitiesAcknowledged"), "ack ignored");
        }
    }
    static void legacy() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer();
            f.legacyBurst(A, 40);
            f.flush(); f.pump();
            check(f.frames() == 3, "legacy fallback lost voice");
        }
    }
    static void earlyFrame() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 1, 0); f.flush(); f.pump();
            check(f.frames() == 3, "valid epoch requires a non-guaranteed ACK delivery");
            check(f.made.size() == 1, "one stream created multiple sinks");
        }
    }
    static void unsolicited() throws Exception {
        try (Fixture f = new Fixture()) {
            f.burst(A, 1, 0); f.flush(); f.pump();
            check(f.streams().isEmpty() && f.frames() == 0, "unoffered extension was accepted");
        }
    }
    static void handoff() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 1, 100); f.flush();
            check(f.pending().size() == 3, "old frames not queued");
            f.burst(A, 2, 1); f.flush(); f.pump(); // seq 0 and any separate reset are lost
            check(f.frames() == 3, "old queued PCM escaped after incarnation switch");
            check(f.streams().get(A).lateCount() == 0, "new sequence treated as late");
            check(f.output.droppedFrameCount() == 3, "stale frames not accounted");
        }
    }
    static void lateFrame() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 3, 0); f.flush(); f.pump();
            byte[] delayed = f.sealFrame(A, 3, 3);
            f.burst(A, 4, 1); f.deliver(delayed); f.flush(); f.pump();
            check(f.frames() == 6, "old generation replayed after valid AEAD reordering");
            check(f.made.size() == 2 && f.made.get(0).closed, "old playback not retired");
            check(f.streams().get(A).lateCount() == 0, "late epoch reached jitter");
        }
    }
    static void resets() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.deliver(f.sealReset(A, 2)); f.burst(A, 2, 0); f.flush(); f.pump();
            check(f.frames() == 0 && f.streams().isEmpty(), "reset-before-data was forgotten");
            f.burst(A, 3, 0); f.flush(); f.pump();
            f.deliver(f.sealReset(A, 2)); f.deliver(f.sealFrame(A, 3, 3)); f.flush(); f.pump();
            check(f.frames() == 4 && f.made.size() == 1 && !f.made.get(0).closed, "old reset killed successor");
            f.deliver(f.sealReset(A, 3)); f.pump();
            check(f.made.get(0).closed && f.streams().isEmpty(), "matching reset ignored");
        }
    }
    static void downgrade() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 1, 0); f.flush(); f.pump();
            f.legacyBurst(A, 10);
            ByteBuffer b = ByteBuffer.allocate(1500); new SoundResetPacket(A).write(b); f.deliver(f.seal(b));
            f.deliver(f.sealFrame(A, 1, 3)); f.flush(); f.pump();
            check(f.frames() == 4 && f.made.size() == 1, "legacy packets bypassed epoch state");
        }
    }
    static void speakers() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 1, 0); f.burst(B, 1, 0); f.flush(); f.pump();
            DecoderStream a = f.streams().get(A), b = f.streams().get(B);
            check(get(a,"decoder") != get(b,"decoder"), "shared stateful decoder");
            f.deliver(f.sealReset(A, 1)); f.deliver(f.sealFrame(B, 1, 3)); f.flush(); f.pump();
            check(f.streams().get(B) == b && f.streams().size() == 1, "one speaker reset another");
            check(f.frames() == 7, "independent voice lost");
        }
    }
    static void fullTasks() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A, 1, 0); f.flush(); f.pump();
            @SuppressWarnings("unchecked") BlockingQueue<Runnable> tasks=(BlockingQueue<Runnable>)get(f.output,"tasks");
            while(tasks.offer(() -> {})) {}
            int queued=tasks.size();
            f.deliver(f.sealReset(A, 1)); f.pump();
            check(f.made.get(0).closed, "native sink cleanup depended on a droppable task");
            check(tasks.size()==queued, "test accidentally drained control saturation");
            f.deliver(f.sealFrame(A,1,3)); f.flush();f.pump();
            check(f.frames()==3, "retired voice resurrected");
        }
    }
    static void pool() throws Exception {
        try (Fixture f = new Fixture()) {
            for(int i=0;i<128;i++) {
                PlaybackFrame frame=f.output.acquireFrame(); check(frame!=null,"pool exhausted early");
                check(f.output.submitFrame(frame,A,false,0,0,0,0,false,1L),"bounded queue lost capacity");
            }
            check(f.output.acquireFrame()==null,"unbounded PCM allocation");
            f.output.retireStream(A,1L); f.pump();
            @SuppressWarnings("unchecked") BlockingQueue<PlaybackFrame> free=(BlockingQueue<PlaybackFrame>)get(f.output,"freeFrames");
            check(free.size()==128 && f.frames()==0,"retirement leaked/replayed PCM");
        }
    }
    static void closedDecoder() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,1,0); f.flush(); f.pump();
            DecoderStream old=f.streams().get(A); old.close(); old.close();
            int queued=f.pending().size();
            old.offer(f.frame(A,3),System.nanoTime(),f.output);
            old.drain(System.nanoTime()+100_000_000L,f.output);
            check(f.pending().size()==queued,"closed decoder accepted late work");
        }
    }
    static void rekey() throws Exception {
        try (Fixture f = new Fixture()) {
            f.offer(); f.burst(A,200,0); f.flush(); f.pump();
            byte[] obsolete=f.sealFrame(A,200,3);
            f.rekey(2); f.offer(); f.burst(A,1,0); f.deliver(obsolete); f.flush(); f.pump();
            check(f.frames()==6 && f.made.size()==2,"crypto/session epochs were mixed");
        }
    }
    static Object get(Object owner,String name)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static void set(Object owner,String name,Object value)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    static Object call(Object owner,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(owner,args);}catch(InvocationTargetException e){throw new AssertionError(name+" failed",e.getCause());}
    }
    static final class Sink implements AudioPlayback.Channel {
        boolean closed; int frames; boolean spatial; float x,y,z,distance;
        public void setSpatial(boolean v){spatial=v;}
        public void setPosition(float a,float b,float c){x=a;y=b;z=c;}
        public void setMaxDistance(float v){distance=v;}
        public void enqueue(short[] pcm){check(!closed,"PCM reached closed sink");check(pcm.length==960,"wrong PCM length");frames++;}
        public void close(){closed=true;}
    }
    static final class Fixture implements AutoCloseable {
        final List<Sink> made=new ArrayList<Sink>();
        final AudioPlayback output=new AudioPlayback(distance->{Sink sink=new Sink();made.add(sink);return sink;});
        final DatagramSocket server=new DatagramSocket(0,InetAddress.getLoopbackAddress());
        final DatagramSocket receiver=new DatagramSocket(0,InetAddress.getLoopbackAddress());
        final VoiceClient client;
        SessionCrypto serverCrypto,clientCrypto;
        final Map<UUID,OpusEncoder> encoders=new HashMap<UUID,OpusEncoder>();
        final Map<UUID,Long> encoderEpochs=new HashMap<UUID,Long>();
        final short[] pcm=new short[960]; final byte[] encoded=new byte[1275];
        Fixture()throws Exception {
            server.setSoTimeout(1500); receiver.setSoTimeout(1500);
            receiver.connect(InetAddress.getLoopbackAddress(),server.getLocalPort());
            client=new VoiceClient(new UUID(9,9),new byte[32],InetAddress.getLoopbackAddress(),server.getLocalPort());
            set(output,"running",true);set(client,"playback",output);set(client,"socket",receiver);
            set(client,"serverAddress",server.getLocalSocketAddress());
            ((DatagramPacket)get(client,"outboundPacket")).setSocketAddress(server.getLocalSocketAddress());
            for(int i=0;i<pcm.length;i++)pcm[i]=(short)(Math.sin(i*0.07)*9000);
            rekey(1);
        }
        void rekey(int seed)throws Exception {
            if(serverCrypto!=null)serverCrypto.close();if(clientCrypto!=null)clientCrypto.close();
            byte[] secret=new byte[32],nonce=new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];Arrays.fill(secret,(byte)seed);
            try(SessionKeys keys=SessionKeys.derive(secret,nonce,nonce)) {
                clientCrypto=SessionCrypto.forClient(keys);serverCrypto=SessionCrypto.forServer(keys);
            }
            set(client,"crypto",clientCrypto);set(client,"authenticated",true);
        }
        void offer()throws Exception {
            call(client,"sendCapabilities",new Class<?>[]{long.class},System.nanoTime());
            DatagramPacket request=new DatagramPacket(new byte[1500],1500);server.receive(request);
            ByteBuffer plain=ByteBuffer.allocate(1500);
            check(serverCrypto.open(ByteBuffer.wrap(request.getData(),0,request.getLength()),plain),"capability offer failed AEAD");
            check(plain.get()==WirePacketType.CAPABILITIES&&VoiceCapabilities.read(plain)==VoiceCapabilities.SUPPORTED,"wrong capability offer");
        }
        SoundFrameV2Packet frame(UUID sender,int sequence) {
            OpusEncoder encoder=encoders.get(sender);
            if(encoder==null){encoder=OpusCodecFactory.createEncoder();encoders.put(sender,encoder);}
            int n=encoder.encode(pcm,encoded);
            return new SoundFrameV2Packet(sender,true,true,11,64,22,12.5F,sequence,Arrays.copyOf(encoded,n));
        }
        byte[] sealFrame(UUID sender,long epoch,int sequence) {
            Long current=encoderEpochs.get(sender);
            if(current==null||current.longValue()!=epoch){OpusEncoder old=encoders.remove(sender);if(old!=null)old.close();encoderEpochs.put(sender,epoch);}
            ByteBuffer b=ByteBuffer.allocate(1500);new SoundFrameV3Packet(epoch,frame(sender,sequence)).write(b);return seal(b);
        }
        byte[] sealReset(UUID sender,long epoch) {
            ByteBuffer b=ByteBuffer.allocate(1500);new SoundResetV3Packet(sender,epoch).write(b);return seal(b);
        }
        byte[] sealCapabilities(int value) {
            ByteBuffer b=ByteBuffer.allocate(1500);VoiceCapabilities.write(b,true,value);return seal(b);
        }
        byte[] seal(ByteBuffer plain) {
            ByteBuffer wire=ByteBuffer.allocate(1500);check(serverCrypto.seal(plain,wire),"seal failed");
            return Arrays.copyOf(wire.array(),wire.position());
        }
        void deliver(byte[] data)throws Exception {
            server.send(new DatagramPacket(data,data.length,receiver.getLocalSocketAddress()));
            DatagramPacket received=new DatagramPacket(new byte[1500],1500);receiver.receive(received);
            call(client,"handleDatagram",new Class<?>[]{DatagramPacket.class},received);
        }
        void burst(UUID sender,long epoch,int first)throws Exception{for(int i=0;i<3;i++)deliver(sealFrame(sender,epoch,first+i));}
        void legacyBurst(UUID sender,int first)throws Exception {
            for(int i=0;i<3;i++){ByteBuffer b=ByteBuffer.allocate(1500);frame(sender,first+i).write(b);deliver(seal(b));}
        }
        @SuppressWarnings("unchecked") Map<UUID,DecoderStream> streams()throws Exception{return (Map<UUID,DecoderStream>)get(client,"decoderStreams");}
        @SuppressWarnings("unchecked") BlockingQueue<PlaybackFrame> pending()throws Exception{return (BlockingQueue<PlaybackFrame>)get(output,"pendingFrames");}
        void flush()throws Exception {for(DecoderStream stream:streams().values())stream.drain(System.nanoTime()+100_000_000L,output);}
        void pump()throws Exception {
            call(output,"reconcileRetiredChannels",new Class<?>[0]);
            PlaybackFrame frame;
            while((frame=pending().poll())!=null){try{call(output,"processFrame",new Class<?>[]{PlaybackFrame.class},frame);}finally{output.releaseFrame(frame);}}
        }
        int frames(){int n=0;for(Sink sink:made)n+=sink.frames;return n;}
        public void close()throws Exception {
            for(DecoderStream stream:streams().values())stream.close();
            for(OpusEncoder encoder:encoders.values())encoder.close();
            serverCrypto.close();clientCrypto.close();server.close();receiver.close();
            for(Sink sink:made)sink.close();set(output,"running",false);
        }
    }
}
