package dev.akashic.voice.voice.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.client.gui.GuiGroupInviteBanner;
import dev.akashic.voice.client.gui.GuiSetupWizard;
import dev.akashic.voice.client.gui.GuiVoiceChatHub;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.net.AkashicVoiceChannel;
import dev.akashic.voice.net.PacketInviteNotify;
import dev.akashic.voice.net.PacketRequestSecret;
import dev.akashic.voice.voice.client.groups.ClientGroupCache;
import dev.akashic.voice.voice.client.groups.ClientPlayerRoster;
import dev.akashic.voice.voice.client.groups.PendingInviteQueue;
import dev.akashic.voice.voice.client.keybinds.AkashicVoiceKeybinds;
import dev.akashic.voice.voice.client.playback.AudioPlayback;

public final class ClientVoiceBootstrap {

    private static final long BOOTSTRAP_RESPONSE_TIMEOUT_NANOS = 10_000_000_000L;
    private static final long VOICE_AUTH_RECOVERY_NANOS = 20_000_000_000L;

    private volatile boolean secretRequested = false;
    private volatile boolean wizardShown = false;
    private volatile long lastBootstrapRequestNanos;
    private volatile long unauthenticatedSinceNanos;
    private static volatile boolean rebootstrapRequested;

    private ClientVoiceBootstrap() {}

    public static void requestRebootstrap() {
        rebootstrapRequested = true;
    }

    public static void install() {
        applyConfigToGate();
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientVoiceBootstrap());
    }

    private static void applyConfigToGate() {
        String mode = AkashicVoiceConfig.activationMode();
        if ("voice".equalsIgnoreCase(mode)) {
            MicGate.setMode(MicGate.Mode.VOICE);
        } else if ("always".equalsIgnoreCase(mode)) {
            MicGate.setMode(MicGate.Mode.ALWAYS);
        } else {
            MicGate.setMode(MicGate.Mode.PTT);
        }
    }

    private static InetAddress resolveServerHost(Minecraft mc) {
        try {
            NetHandlerPlayClient handler = mc.getNetHandler();
            if (handler != null) {
                NetworkManager nm = handler.getNetworkManager();
                if (nm != null) {
                    SocketAddress addr = nm.getSocketAddress();
                    if (addr instanceof InetSocketAddress) {
                        return ((InetSocketAddress) addr).getAddress();
                    }
                }
            }
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        EntityPlayer player = mc.thePlayer;

        if (player == null) {
            // Left world (or not in one yet). Reset so next join re-requests.
            if (secretRequested) {
                secretRequested = false;
                wizardShown = false;
                ClientSecretCache.clear();
                ClientGroupCache.clear();
                ClientPlayerRoster.clear();
                PendingInviteQueue.clear();
                VoiceClientHolder.stop();
            }
            rebootstrapRequested = false;
            lastBootstrapRequestNanos = 0L;
            unauthenticatedSinceNanos = 0L;
            return;
        }

        if (rebootstrapRequested) {
            rebootstrapRequested = false;
            secretRequested = false;
            ClientSecretCache.clear();
            ClientGroupCache.clear();
            ClientPlayerRoster.clear();
            PendingInviteQueue.clear();
            VoiceClientHolder.stop();
            lastBootstrapRequestNanos = 0L;
            unauthenticatedSinceNanos = 0L;
            AkashicVoice.LOG.info("Voice route changed; requesting a fresh bootstrap");
        }

        // Pending group invites: pop one and show the banner if no other GUI is up.
        if (mc.currentScreen == null && !PendingInviteQueue.isEmpty()) {
            PacketInviteNotify n = PendingInviteQueue.pop();
            if (n != null) {
                mc.displayGuiScreen(new GuiGroupInviteBanner(n));
                return;
            }
        }

        // First-run setup wizard: show once after first world join, only if no other GUI is up.
        if (!AkashicVoiceConfig.firstRunComplete() && !wizardShown && mc.currentScreen == null) {
            wizardShown = true;
            mc.displayGuiScreen(new GuiSetupWizard());
            return;
        }

        // Settings hotkey: edge-triggered, only when no GUI is up.
        while (AkashicVoiceKeybinds.OPEN_SETTINGS.isPressed()) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiVoiceChatHub());
                return;
            }
        }

        long nowNanos = System.nanoTime();
        VoiceClient activeVoice = VoiceClientHolder.get();
        if (activeVoice != null && activeVoice.needsFreshBootstrap()) {
            AkashicVoice.LOG.warn("Voice transport requested a fresh bootstrap");
            VoiceClientHolder.stop();
            ClientSecretCache.clear();
            secretRequested = false;
            lastBootstrapRequestNanos = 0L;
            unauthenticatedSinceNanos = 0L;
            activeVoice = null;
        }
        if (activeVoice == null) {
            unauthenticatedSinceNanos = 0L;
            if (secretRequested && !ClientSecretCache.hasSecret()
                && lastBootstrapRequestNanos != 0L
                && nowNanos - lastBootstrapRequestNanos >= BOOTSTRAP_RESPONSE_TIMEOUT_NANOS) {
                secretRequested = false;
                lastBootstrapRequestNanos = 0L;
                AkashicVoice.LOG.debug("Voice bootstrap timed out; retrying");
            }
        } else if (activeVoice.isAuthenticated()) {
            unauthenticatedSinceNanos = 0L;
        } else if (unauthenticatedSinceNanos == 0L) {
            unauthenticatedSinceNanos = nowNanos;
        } else if (nowNanos - unauthenticatedSinceNanos >= VOICE_AUTH_RECOVERY_NANOS) {
            AkashicVoice.LOG.warn("Voice authentication did not recover; requesting a fresh bootstrap");
            VoiceClientHolder.stop();
            ClientSecretCache.clear();
            secretRequested = false;
            lastBootstrapRequestNanos = 0L;
            unauthenticatedSinceNanos = 0L;
        }

        // In a world. Step 1: request the secret if we haven't yet.
        if (!secretRequested) {
            ClientSecretCache.clear();
            VoiceClientHolder.stop();
            AkashicVoice.LOG.info("Requesting voice bootstrap from server");
            AkashicVoiceChannel.get()
                .sendToServer(new PacketRequestSecret());
            secretRequested = true;
            lastBootstrapRequestNanos = System.nanoTime();
        }

        // Step 2: once the secret arrives, open the UDP voice connection.
        if (ClientSecretCache.hasSecret() && VoiceClientHolder.get() == null) {
            InetAddress host = resolveServerHost(mc);
            if (host != null) {
                byte[] secret = ClientSecretCache.getMasterSecret();
                if (secret != null) {
                    try {
                        if (VoiceClientHolder
                            .connect(ClientSecretCache.getPlayerId(), secret, host, ClientSecretCache.getUdpPort())) {
                            ClientSecretCache.clear();
                            lastBootstrapRequestNanos = 0L;
                            unauthenticatedSinceNanos = 0L;
                        }
                    } finally {
                        Arrays.fill(secret, (byte) 0);
                    }
                }
            }
        }

        // Mute toggle (edge-triggered). State change is shown by the HUD icon, no chat spam.
        while (AkashicVoiceKeybinds.MUTE_TOGGLE.isPressed()) {
            MicGate.toggleMute();
        }

        // PTT (continuous state).
        boolean pttDown = isKeyDown(AkashicVoiceKeybinds.PTT);
        if (pttDown != MicGate.isPttPressed()) {
            MicGate.setPttPressed(pttDown);
        }

        // Listener position + audio config update.
        AudioPlayback p = AudioPlayback.get();
        if (p == null) return;
        p.setMaxDistance(AkashicVoiceConfig.voiceRadius());
        p.setReferenceDistance((float) AkashicVoiceConfig.referenceDistance());
        float gain = MicGate.isDeafened() ? 0f : (float) AkashicVoiceConfig.playbackVolume();
        p.setListenerGain(gain);
        p.setSpatial("normal".equals(AkashicVoiceConfig.audio3dMode()));
        float pitchRad = (float) Math.toRadians(player.rotationPitch);
        float yawRad = (float) Math.toRadians(player.rotationYaw);
        p.updateListener((float) player.posX, (float) player.posY, (float) player.posZ, pitchRad, yawRad);
    }

    private static boolean isKeyDown(net.minecraft.client.settings.KeyBinding kb) {
        try {
            int code = kb.getKeyCode();
            if (code == 0) return false;
            if (code < 0) {
                return org.lwjgl.input.Mouse.isButtonDown(code + 100);
            }
            return org.lwjgl.input.Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return false;
        }
    }
}
