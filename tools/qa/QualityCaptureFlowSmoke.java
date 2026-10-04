package dev.akashic.voice.voice.client;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.MicSequenceRecoverySmoke.Fixture;
import dev.akashic.voice.voice.client.audio.capture.*;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.codec.*;

/** Production capture loop, factory, live controls and encrypted UDP; only the microphone is fake. */
public final class QualityCaptureFlowSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        int before=AkashicVoiceConfig.opusBitrate();String backend=AkashicVoiceConfig.opusBackend();
        try { run("native");run("java"); }
        finally {AkashicVoiceConfig.setOpusBitrate(before);AkashicVoiceConfig.setOpusBackend(backend);}
        System.out.println("QUALITY_CAPTURE_FLOW_OK scenarios=2 frames=16");
    }
    static void run(String backend)throws Exception {
        AkashicVoiceConfig.setOpusBackend(backend);AkashicVoiceConfig.setOpusBitrate(48000);
        OpusCodecFactory.resetBackendProbeForTests();
        int[] rates={48000,24000,32000,64000,44123,6000,128000,48000};
        AtomicInteger transmitted=new AtomicInteger();Device device=new Device(rates);
        try(Fixture network=new Fixture();OpusDecoder decoder="native".equals(backend)
            ?new ConcentusOpusDecoder():new NativeOpusDecoder()) {
            OpusEncoder raw=OpusCodecFactory.createEncoder();
            check(raw instanceof LiveBitrateEncoder,"factory not live-configured");
            LiveBitrateEncoder encoder=(LiveBitrateEncoder)raw;
            MicCaptureSession session=new MicCaptureSession(device,encoder);
            MicCapture.FrameSink sink=new MicCapture.FrameSink() {
                public void onFrame(int sequence,byte[] payload,int length){throw new AssertionError("authorization bypass");}
                public boolean sendAuthorizedFrame(int sequence,byte[] payload,int length,MicGate.TransmissionState auth) {
                    try {
                        int index=transmitted.get();check(sequence==index,"capture sequence reset");
                        check(encoder.appliedBitrate()==rates[index],"frame used wrong target rate");
                        boolean sent=network.sink.sendAuthorizedFrame(sequence,payload,length,auth);
                        check(sent,"authorized capture frame not accepted");
                        MicFramePacket packet=receive(network);
                        check(packet.sequence==index,"wire sequence discontinuity");
                        check(Arrays.equals(Arrays.copyOf(payload,length),Arrays.copyOfRange(packet.opusData,
                            packet.opusOffset,packet.opusOffset+packet.opusLength)),"UDP changed Opus bytes");
                        check(decoder.decode(packet.opusData,packet.opusOffset,packet.opusLength,new short[960])==960,
                            "cross-backend decode failed");transmitted.incrementAndGet();return true;
                    } catch(Exception error){throw new AssertionError("capture network flow failed",error);}
                }
            };
            MicCapture capture=new MicCapture(sink);
            Method loop=MicCapture.class.getDeclaredMethod("loop",MicCaptureSession.class);loop.setAccessible(true);
            try{loop.invoke(capture,session);}catch(InvocationTargetException error){throw new AssertionError(error.getCause());}
            finally{session.finish();}
            check(transmitted.get()==rates.length && device.closed==1 && session.isFinished(),"lost frames or resources");
            check(network.clientCrypto.nextSendCounter()==rates.length,"extra cryptographic packet");
            System.out.println("PASS actual_capture_live_rates backend="+backend+" frames="+transmitted.get());
        }
    }
    static MicFramePacket receive(Fixture network)throws Exception {
        java.net.DatagramPacket wire=new java.net.DatagramPacket(new byte[1500],1500);
        network.receiver.receive(wire);
        java.nio.ByteBuffer plain=java.nio.ByteBuffer.allocate(1500);
        check(network.serverCrypto.open(java.nio.ByteBuffer.wrap(wire.getData(),0,wire.getLength()),plain),
            "encrypted real-codec packet rejected");
        check(plain.get()==dev.akashic.voice.voice.common.WirePacketType.MIC_FRAME,"wrong packet type");
        MicFramePacket packet=MicFramePacket.read(plain);
        check(packet!=null && packet.opusLength>0 && packet.opusLength<=1275,"invalid real Opus payload");
        return packet;
    }
    static final class Device implements AudioCaptureDevice {
        final int[] rates;int index,closed;
        Device(int[] rates){this.rates=rates;}
        public int read(byte[] bytes,int offset,int length) {
            if(index==rates.length)return -1;
            AkashicVoiceConfig.setOpusBitrate(rates[index++]);
            for(int i=0;i<length/2;i++) {
                short sample=(short)(8000*Math.sin(2*Math.PI*330*i/48000.0));
                bytes[offset+i*2]=(byte)sample;bytes[offset+i*2+1]=(byte)(sample>>8);
            }
            return length;
        }
        public String backendName(){return "quality-qa";}
        public String description(){return "synthetic input; no physical microphone";}
        public void close(){closed++;}
    }
}
