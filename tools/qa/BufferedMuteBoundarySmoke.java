package dev.akashic.voice.voice.client.playback;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.Fixture;
import static dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.*;

/** Compressed jitter backlog, not just queued PCM, must respect listening boundaries. */
public final class BufferedMuteBoundarySmoke {
    public static void main(String[] args) throws Exception {
        String previous=AkashicVoiceConfig.opusBackend();AkashicVoiceConfig.setOpusBackend("java");
        int failures=0;
        try {
            for(boolean initiallyDeafened:new boolean[]{false,true}) {
                try(Fixture f=new Fixture()) {
                    f.offer();f.output.setDeafened(initiallyDeafened);
                    f.deliver(f.sealFrame(A,1,100));
                    f.output.setDeafened(true);f.output.setDeafened(false);f.flush();f.pump();
                    if(f.frames()!=0){failures++;System.out.println("FAIL compressed backlog replayed, initiallyDeafened="+initiallyDeafened);continue;}
                    f.burst(A,1,101);f.flush();f.pump();
                    check(f.frames()==3&&f.streams().get(A).lateCount()==0,"resume sequence changed");
                    System.out.println("PASS compressed boundary, initiallyDeafened="+initiallyDeafened);
                }
            }
        } finally {AkashicVoiceConfig.setOpusBackend(previous);}
        if(failures!=0)throw new AssertionError("compressed global-mute failures="+failures);
        System.out.println("BUFFERED_MUTE_OK scenarios=2");
    }
}
