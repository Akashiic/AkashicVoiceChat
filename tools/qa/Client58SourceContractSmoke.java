package dev.akashic.voice.voice.client.playback;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Client58SourceContractSmoke {

    private static int passed;

    public static void main(String[] args) throws Exception {
        nametagContract();
        pttContract();
        acousticsContract();
        outputRecoveryContract();
        System.out.println("CLIENT58_SOURCE_CONTRACT_OK scenarios=" + passed);
    }

    private static void nametagContract() throws Exception {
        String source = read("src/main/java/dev/akashic/voice/voice/client/render/SpeakerOverlay.java");
        require(source, "RenderLivingEvent.Specials.Post");
        require(source, "RendererLivingEntity.NAME_TAG_RANGE_SNEAK");
        require(source, "RendererLivingEntity.NAME_TAG_RANGE");
        require(source, "player.riddenByEntity != null");
        require(source, "(float) event.x");
        require(source, "player.isPlayerSleeping() ? 1.5D : 0.0D");
        require(source, "0.25F / NAMEPLATE_SCALE");
        require(source, "\"deadmau5\".equals(label)");
        passed++;
    }

    private static void pttContract() throws Exception {
        String bridge = read("src/main/java/dev/akashic/voice/voice/client/keybinds/PttInputBridge.java");
        String proxy = read("src/main/java/dev/akashic/voice/ClientProxy.java");
        String hud = read("src/main/java/dev/akashic/voice/voice/client/render/MicHudOverlay.java");
        require(bridge, "InputEvent.KeyInputEvent");
        require(bridge, "MouseEvent");
        require(proxy, "MinecraftForge.EVENT_BUS.register(pttInput)");
        require(proxy, "FMLCommonHandler.instance()");
        require(hud, "MicGate.isPttEngaged()");
        require(hud, "ACTIVE_WINDOW_MILLIS = 120L");
        passed++;
    }

    private static void acousticsContract() throws Exception {
        String effects = read("src/main/java/dev/akashic/voice/voice/client/playback/VoiceEnvironmentEffects.java");
        String bootstrap = read("src/main/java/dev/akashic/voice/voice/client/ClientVoiceBootstrap.java");
        require(effects, "EFX10.AL_EFFECT_REVERB");
        require(effects, "EFX10.AL_FILTER_LOWPASS");
        String playback = read("src/main/java/dev/akashic/voice/voice/client/playback/AudioPlayback.java");
        String channel = read("src/main/java/dev/akashic/voice/voice/client/playback/PlaybackChannel.java");
        require(playback, "AcousticRoutingPolicy.effectsEnabled");
        require(channel, "EFX10.AL_EFFECTSLOT_NULL");
        require(channel, "EFX10.AL_FILTER_NULL");
        require(bootstrap, "AcousticEnvironmentSampler.sample(player)");
        require(bootstrap, "acousticTracker.update");
        passed++;
    }

    private static void outputRecoveryContract() throws Exception {
        String health = read("src/main/java/dev/akashic/voice/voice/client/playback/OpenAlOutputHealth.java");
        String playback = read("src/main/java/dev/akashic/voice/voice/client/playback/AudioPlayback.java");
        String bootstrap = read("src/main/java/dev/akashic/voice/voice/client/ClientVoiceBootstrap.java");
        require(health, "\"ALC_EXT_disconnect\"");
        require(health, "Status.DEFAULT_CHANGED");
        require(health, "equalsIgnoreCase");
        require(health, "same(currentBound, currentDefault)");
        require(playback, "OUTPUT_UNAVAILABLE_CONFIRMATIONS = 3");
        require(playback, "outputQuiesced && !weCreatedAl && outputRecovery.claimSharedReload()");
        require(playback, "if (weCreatedAl && outputRecovery.ownedRestartDue(now))");
        require(playback, "if (AL.isCreated()) AL.destroy()");
        require(bootstrap, "mc.getSoundHandler()");
        require(bootstrap, ".onResourceManagerReload(");
        require(bootstrap, "mc.getResourceManager()");
        passed++;
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void require(String source, String needle) {
        if (!source.contains(needle)) throw new AssertionError("Missing source contract: " + needle);
    }
}
