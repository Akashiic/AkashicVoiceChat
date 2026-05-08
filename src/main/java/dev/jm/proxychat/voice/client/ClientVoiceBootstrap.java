package dev.jm.proxychat.voice.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.client.gui.GuiProxyChatSettings;
import dev.jm.proxychat.client.gui.GuiSetupWizard;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.net.PacketRequestSecret;
import dev.jm.proxychat.net.ProxyChatChannel;
import dev.jm.proxychat.voice.client.keybinds.ProxyChatKeybinds;
import dev.jm.proxychat.voice.client.playback.AudioPlayback;

public final class ClientVoiceBootstrap {

    private static final PositionSupplier PLAYER_POS = new PositionSupplier() {

        @Override
        public float x() {
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            return p != null ? (float) p.posX : 0f;
        }

        @Override
        public float y() {
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            return p != null ? (float) p.posY : 0f;
        }

        @Override
        public float z() {
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            return p != null ? (float) p.posZ : 0f;
        }
    };

    private volatile boolean secretRequested = false;
    private volatile boolean wizardShown = false;

    private ClientVoiceBootstrap() {}

    public static void install() {
        applyConfigToGate();
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientVoiceBootstrap());
    }

    private static void applyConfigToGate() {
        String mode = ProxyChatConfig.activationMode();
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
                VoiceClientHolder.stop();
            }
            return;
        }

        // First-run setup wizard: show once after first world join, only if no other GUI is up.
        if (!ProxyChatConfig.firstRunComplete() && !wizardShown && mc.currentScreen == null) {
            wizardShown = true;
            mc.displayGuiScreen(new GuiSetupWizard());
            return;
        }

        // Settings hotkey: edge-triggered, only when no GUI is up.
        while (ProxyChatKeybinds.OPEN_SETTINGS.isPressed()) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiProxyChatSettings());
                return;
            }
        }

        // In a world. Step 1: request the secret if we haven't yet.
        if (!secretRequested) {
            ClientSecretCache.clear();
            VoiceClientHolder.stop();
            ProxyChat.LOG.info("Requesting voice secret from server");
            ProxyChatChannel.get()
                .sendToServer(new PacketRequestSecret());
            secretRequested = true;
        }

        // Step 2: once the secret arrives, open the UDP voice connection.
        if (ClientSecretCache.hasSecret() && VoiceClientHolder.get() == null) {
            InetAddress host = resolveServerHost(mc);
            if (host != null) {
                VoiceClientHolder.connect(
                    player.getUniqueID(),
                    ClientSecretCache.getSecret(),
                    host,
                    ClientSecretCache.getUdpPort(),
                    PLAYER_POS);
            }
        }

        // Mute toggle (edge-triggered).
        while (ProxyChatKeybinds.MUTE_TOGGLE.isPressed()) {
            MicGate.toggleMute();
            player.addChatMessage(
                new ChatComponentText("ProxyChat: microphone " + (MicGate.isMuted() ? "muted" : "unmuted")));
        }

        // PTT (continuous state).
        boolean pttDown = isKeyDown(ProxyChatKeybinds.PTT);
        if (pttDown != MicGate.isPttPressed()) {
            MicGate.setPttPressed(pttDown);
        }

        // Listener position + audio config update.
        AudioPlayback p = AudioPlayback.get();
        if (p == null) return;
        p.setMaxDistance(ProxyChatConfig.voiceRadius());
        p.setReferenceDistance((float) ProxyChatConfig.referenceDistance());
        p.setListenerGain((float) ProxyChatConfig.playbackVolume());
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
