package dev.jm.proxychat.voice.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.config.ProxyChatConfig;
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

    private ClientVoiceBootstrap() {}

    public static void install() {
        ClientSecretCache.setOnAvailable(ClientVoiceBootstrap::onSecretAvailable);
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

    private static void onSecretAvailable(UUID secret, Integer udpPort) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            ProxyChat.LOG.warn("Voice secret arrived but player is null; skipping connect");
            return;
        }
        InetAddress host = resolveServerHost(mc);
        if (host == null) {
            ProxyChat.LOG.warn("Could not resolve voice server host; skipping connect");
            return;
        }
        VoiceClientHolder.connect(player.getUniqueID(), secret, host, udpPort, PLAYER_POS);
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

        // Mute toggle: edge-triggered via KeyBinding.isPressed() (true once per press).
        while (ProxyChatKeybinds.MUTE_TOGGLE.isPressed()) {
            MicGate.toggleMute();
            EntityPlayer notifyTarget = mc.thePlayer;
            if (notifyTarget != null) {
                notifyTarget.addChatMessage(
                    new ChatComponentText("ProxyChat: microphone " + (MicGate.isMuted() ? "muted" : "unmuted")));
            }
        }

        // PTT: continuous state. Poll the keybind's bound key directly so it works
        // even when a chat box has focus only when allowed by the user.
        boolean pttDown = isKeyDown(ProxyChatKeybinds.PTT);
        if (pttDown != MicGate.isPttPressed()) {
            MicGate.setPttPressed(pttDown);
        }

        AudioPlayback p = AudioPlayback.get();
        if (p == null) return;
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        p.setMaxDistance(ProxyChatConfig.voiceRadius());
        float pitchRad = (float) Math.toRadians(player.rotationPitch);
        float yawRad = (float) Math.toRadians(player.rotationYaw);
        p.updateListener((float) player.posX, (float) player.posY, (float) player.posZ, pitchRad, yawRad);
    }

    private static boolean isKeyDown(net.minecraft.client.settings.KeyBinding kb) {
        try {
            int code = kb.getKeyCode();
            if (code == 0) return false;
            if (code < 0) {
                // Mouse button — Mouse.isButtonDown takes 0..n
                return org.lwjgl.input.Mouse.isButtonDown(code + 100);
            }
            return org.lwjgl.input.Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return false;
        }
    }
}
