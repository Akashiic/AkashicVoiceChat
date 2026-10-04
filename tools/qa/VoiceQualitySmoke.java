package dev.akashic.voice.voice.common.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.config.VoiceQualityPreset;

/** Real config and codecs, plus deterministic control-failure doubles; no audio hardware. */
public final class VoiceQualitySmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static final short[] PCM = signal();
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static void test(String name,Scenario body)throws Exception {
        body.run();passed++;System.out.println("PASS "+name);
    }
    public static void main(String[] args)throws Exception {
        int previous=AkashicVoiceConfig.opusBitrate();String backend=AkashicVoiceConfig.opusBackend();
        try {
            test("preset_values_and_48k_default",VoiceQualitySmoke::presets);
            test("custom_bitrate_is_not_coerced",VoiceQualitySmoke::custom);
            test("invalid_updates_are_atomic",VoiceQualitySmoke::invalid);
            test("all_named_presets_select_exact_rate",VoiceQualitySmoke::selection);
            test("unchanged_frames_do_not_reconfigure",VoiceQualitySmoke::unchanged);
            test("latest_requested_rate_wins",VoiceQualitySmoke::coalesces);
            test("setter_runs_on_encoding_caller",VoiceQualitySmoke::caller);
            test("failed_control_does_not_encode_or_confirm",VoiceQualitySmoke::failure);
            test("partial_failure_then_revert_is_reapplied",VoiceQualitySmoke::partialRevert);
            test("update_during_control_applies_next_frame",VoiceQualitySmoke::duringControl);
            test("closed_encoder_rejects_work_and_closes_once",VoiceQualitySmoke::closed);
            test("invalid_supplier_rejected_before_native_call",VoiceQualitySmoke::invalidSupplier);
            test("independent_encoders_have_independent_state",VoiceQualitySmoke::independent);
            test("legacy_custom_config_and_filter_roundtrip",VoiceQualitySmoke::configRoundtrip);
            test("legacy_48k_native_packets_unchanged",()->golden("native"));
            test("legacy_48k_java_packets_unchanged",()->golden("java"));
            test("live_native_to_java_decode_all_rates",()->realStream("native"));
            test("live_java_to_native_decode_all_rates",()->realStream("java"));
        } finally { AkashicVoiceConfig.setOpusBitrate(previous);AkashicVoiceConfig.setOpusBackend(backend); }
        System.out.println("VOICE_QUALITY_OK scenarios="+passed);
    }
    static void presets() {
        check(AkashicVoiceConfig.DEFAULT_OPUS_BITRATE==48000,"default bitrate changed");
        check(VoiceQualityPreset.HD.bitrate()==48000,"HD no longer matches baseline");
        check(Arrays.equals(new int[]{24000,32000,48000,64000},
            Arrays.stream(VoiceQualityPreset.values()).mapToInt(VoiceQualityPreset::bitrate).toArray()),"unexpected matrix");
    }
    static void custom() {
        AkashicVoiceConfig.setOpusBitrate(44123);
        check(AkashicVoiceConfig.voiceQuality()==null && AkashicVoiceConfig.opusBitrate()==44123,"custom overwritten");
    }
    static void invalid() {
        AkashicVoiceConfig.setOpusBitrate(32000);
        for(int rate:new int[]{-1,0,5999,128001,Integer.MAX_VALUE}) {
            try{AkashicVoiceConfig.setOpusBitrate(rate);throw new AssertionError("invalid rate accepted");}
            catch(IllegalArgumentException expected){}
            check(AkashicVoiceConfig.opusBitrate()==32000,"invalid update changed setting");
        }
        try{AkashicVoiceConfig.setVoiceQuality(null);throw new AssertionError("null accepted");}
        catch(IllegalArgumentException expected){}
    }
    static void selection() {
        for(VoiceQualityPreset preset:VoiceQualityPreset.values()) {
            AkashicVoiceConfig.setVoiceQuality(preset);
            check(AkashicVoiceConfig.opusBitrate()==preset.bitrate() && AkashicVoiceConfig.voiceQuality()==preset,"wrong preset");
        }
    }
    static final class Fake implements OpusEncoder {
        int rate=48000,sets,encodes,closes;boolean fail;Runnable onSet;long setterThread;
        final AtomicInteger request=new AtomicInteger(48000);
        void set(int value) {
            sets++;rate=value;setterThread=Thread.currentThread().getId();
            if(onSet!=null)onSet.run();if(fail)throw new IllegalStateException("synthetic control failure");
        }
        public int encode(short[] pcm,byte[] out){encodes++;out[0]=42;return 1;}
        public void close(){closes++;}
        LiveBitrateEncoder wrap(){return new LiveBitrateEncoder(this,this::set,request::get,48000);}
    }
    static void unchanged() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            byte[] out=new byte[8];for(int i=0;i<10000;i++)e.encode(PCM,out);
            check(f.sets==0 && f.encodes==10000,"unchanged frames reconfigured backend");
        }
    }
    static void coalesces() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            f.request.set(24000);f.request.set(64000);e.encode(PCM,new byte[8]);
            check(f.sets==1 && f.rate==64000 && e.appliedBitrate()==64000,"did not apply latest request");
        }
    }
    static void caller() throws Exception {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            Thread writer=new Thread(()->f.request.set(24000));writer.start();writer.join(10000);
            check(!writer.isAlive() && f.sets==0,"publishing config called backend");
            e.encode(PCM,new byte[8]);check(f.setterThread==Thread.currentThread().getId(),"wrong setter thread");
        }
    }
    static void failure() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            f.request.set(32000);f.fail=true;
            try{e.encode(PCM,new byte[8]);throw new AssertionError("failure swallowed");}catch(IllegalStateException expected){}
            check(!e.isBitrateConfirmed() && e.appliedBitrate()==48000 && f.encodes==0,"false confirmation");
            f.fail=false;e.encode(PCM,new byte[8]);
            check(f.sets==2 && e.appliedBitrate()==32000 && e.isBitrateConfirmed(),"retry failed");
        }
    }
    static void partialRevert() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            f.request.set(32000);f.fail=true;
            try{e.encode(PCM,new byte[8]);throw new AssertionError("failure swallowed");}catch(IllegalStateException expected){}
            f.fail=false;f.request.set(48000);e.encode(PCM,new byte[8]);
            check(f.sets==2 && f.rate==48000 && e.isBitrateConfirmed(),"old cached value hid partial change");
        }
    }
    static void duringControl() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            f.request.set(24000);f.onSet=()->f.request.set(64000);e.encode(PCM,new byte[8]);
            check(e.appliedBitrate()==24000,"confirmed a request not yet applied");
            f.onSet=null;e.encode(PCM,new byte[8]);check(e.appliedBitrate()==64000 && f.sets==2,"lost concurrent update");
        }
    }
    static void closed() {
        Fake f=new Fake();LiveBitrateEncoder e=f.wrap();e.close();e.close();
        try{e.encode(PCM,new byte[8]);throw new AssertionError("encoded after close");}catch(IllegalStateException expected){}
        check(f.closes==1 && f.encodes==0 && f.sets==0,"close was not idempotent");
    }
    static void invalidSupplier() {
        Fake f=new Fake();try(LiveBitrateEncoder e=f.wrap()) {
            f.request.set(-1);try{e.encode(PCM,new byte[8]);throw new AssertionError("invalid supplier accepted");}
            catch(IllegalArgumentException expected){}
            check(f.sets==0 && f.encodes==0 && e.appliedBitrate()==48000,"invalid input reached codec");
        }
    }
    static void independent() {
        Fake a=new Fake(),b=new Fake();try(LiveBitrateEncoder x=a.wrap();LiveBitrateEncoder y=b.wrap()) {
            a.request.set(24000);x.encode(PCM,new byte[8]);y.encode(PCM,new byte[8]);
            check(a.rate==24000 && b.rate==48000 && b.sets==0,"encoder state leaked");
        }
    }
    static void configRoundtrip() throws Exception {
        Path dir=Files.createTempDirectory(Paths.get(".").toAbsolutePath(),"quality15-config-");
        Path path=dir.resolve("voice.cfg");
        Files.write(path,Arrays.asList("voice {"," I:opusBitrate=44123","}",
            "client {"," B:noiseSuppression=true","}"),StandardCharsets.UTF_8);
        try (cpw.mods.fml.relauncher.QualityTestEnvironment environment = new cpw.mods.fml.relauncher.QualityTestEnvironment(dir.toFile())) {
        AkashicVoiceConfig.load(path.toFile());
        check(AkashicVoiceConfig.opusBitrate()==44123 && AkashicVoiceConfig.voiceQuality()==null,"legacy custom lost");
        check(AkashicVoiceConfig.noiseSuppression(),"legacy filter key lost");
        AkashicVoiceConfig.save();AkashicVoiceConfig.load(path.toFile());
        check(AkashicVoiceConfig.opusBitrate()==44123,"roundtrip coerced custom");
        AkashicVoiceConfig.setVoiceQuality(VoiceQualityPreset.ECONOMY);AkashicVoiceConfig.save();
        AkashicVoiceConfig.load(path.toFile());
        check(AkashicVoiceConfig.opusBitrate()==24000 && AkashicVoiceConfig.noiseSuppression(),"preset roundtrip lost state");
        String written=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
        check(!written.contains("S:voiceQuality="),"redundant conflicting preset field introduced");
        }
    }
    static short[] signal() {
        short[] pcm=new short[AudioFormatSpec.FRAME_SAMPLES];
        for(int i=0;i<pcm.length;i++)pcm[i]=(short)(10000*Math.sin(2*Math.PI*330*i/48000.0));
        return pcm;
    }
    static void golden(String backend) {
        AkashicVoiceConfig.setOpusBackend(backend);AkashicVoiceConfig.setOpusBitrate(48000);
        OpusCodecFactory.resetBackendProbeForTests();
        try(OpusEncoder live=OpusCodecFactory.createEncoder();
            OpusEncoder fixed="native".equals(backend)?new NativeOpusEncoder(48000):new ConcentusOpusEncoder(48000)) {
            byte[] a=new byte[1275],b=new byte[1275];short[] original=PCM.clone();
            for(int i=0;i<16;i++) {
                int na=live.encode(PCM,a),nb=fixed.encode(PCM,b);
                check(na==nb && Arrays.equals(Arrays.copyOf(a,na),Arrays.copyOf(b,nb)),"48k baseline packets changed");
            }
            check(Arrays.equals(PCM,original),"encoder modified input PCM");
        }
    }
    static void realStream(String backend) {
        AkashicVoiceConfig.setOpusBackend(backend);AkashicVoiceConfig.setOpusBitrate(48000);
        OpusCodecFactory.resetBackendProbeForTests();
        try(OpusEncoder raw=OpusCodecFactory.createEncoder();
            OpusDecoder decoder="native".equals(backend)?new ConcentusOpusDecoder():new NativeOpusDecoder()) {
            check(raw instanceof LiveBitrateEncoder,"factory did not wire live controls");
            LiveBitrateEncoder encoder=(LiveBitrateEncoder)raw;
            byte[] packet=new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];short[] decoded=new short[960];
            int frames=0, lowBytes=0, highBytes=0;
            for(int bitrate:new int[]{24000,32000,48000,64000,44123,6000,128000,48000}) {
                AkashicVoiceConfig.setOpusBitrate(bitrate);
                for(int i=0;i<30;i++) {
                    int count=encoder.encode(PCM,packet);
                    check(count>0 && count<=packet.length,"invalid Opus packet length");
                    if (bitrate==6000) lowBytes+=count;
                    if (bitrate==128000) highBytes+=count;
                    check(encoder.appliedBitrate()==bitrate && encoder.isBitrateConfirmed(),"requested rate not applied");
                    check(decoder.decode(packet,0,count,decoded)==960,"decoder continuity/frame size broken");frames++;
                }
            }
            check(highBytes>lowBytes,"backend output did not respond to bitrate range");
            System.out.println("LIVE_RATE_STREAM backend="+backend+" frames="+frames
                +" decoder_recreated=false bytes_6k="+lowBytes+" bytes_128k="+highBytes);
        }
    }
}
