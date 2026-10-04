package dev.akashic.voice.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.server.SecretRegistry;
import dev.akashic.voice.voice.server.VoiceServer;
import dev.akashic.voice.voice.server.VoiceServerHolder;
import dev.akashic.voice.voice.server.VoiceServerMetricsSnapshot;
import dev.akashic.voice.voice.server.agent.LegacyGatewayAgentHolder;

public class AvDebugCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "avdebug";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/avdebug - Akashic Voice status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (AkashicVoiceConfig.agentMode()) {
            sender.addChatMessage(
                new ChatComponentText(
                    "Akashic Voice: mode=agent, backend=" + AkashicVoiceConfig.agentBackendId()
                        + ", gateway="
                        + AkashicVoiceConfig.agentGatewayHost()
                        + ":"
                        + AkashicVoiceConfig.agentGatewayPort()
                        + ", connected="
                        + LegacyGatewayAgentHolder.isConnected()));
            sender.addChatMessage(
                new ChatComponentText(
                    "Agent: managed=" + LegacyGatewayAgentHolder.managedPlayers()
                        + ", deliveries="
                        + LegacyGatewayAgentHolder.deliveryCount()
                        + ", resets="
                        + LegacyGatewayAgentHolder.resetCount()
                        + ", queue="
                        + LegacyGatewayAgentHolder.queuedMediaMessages()
                        + ", dropped="
                        + LegacyGatewayAgentHolder.droppedMediaFrames()
                        + ", connects="
                        + LegacyGatewayAgentHolder.reconnectCount()));
            return;
        }
        VoiceServer server = VoiceServerHolder.get();
        if (server == null) {
            sender.addChatMessage(new ChatComponentText("Akashic Voice: server offline"));
            return;
        }
        VoiceServerMetricsSnapshot m = server.metricsSnapshot();
        sender.addChatMessage(
            new ChatComponentText(
                "Akashic Voice: UDP " + m.udpPort()
                    + ", authenticated="
                    + m.activeSessions()
                    + ", pending="
                    + m.pendingHandshakes()
                    + ", bootstrapTickets="
                    + SecretRegistry.size()));
        sender.addChatMessage(
            new ChatComponentText(
                "Traffic: rx=" + m.datagramsReceived()
                    + "/"
                    + m.bytesReceived()
                    + "B, tx="
                    + m.datagramsSent()
                    + "/"
                    + m.bytesSent()
                    + "B, mic="
                    + m.micFramesAccepted()
                    + "/"
                    + m.micBytesAccepted()
                    + "B"));
        sender.addChatMessage(
            new ChatComponentText(
                "Delivery: spatial=" + m
                    .spatialDeliveries() + ", group=" + m.groupDeliveries() + ", self=" + m.selfEchoDeliveries()));
        sender.addChatMessage(
            new ChatComponentText(
                "Rejected: helloRate=" + m.helloRateLimited()
                    + ", mediaRate="
                    + m.mediaRateLimited()
                    + ", encrypted="
                    + m.encryptedRejected()
                    + ", handshake="
                    + m.handshakeRejected()));
        sender.addChatMessage(
            new ChatComponentText(
                "Sessions: opened=" + m
                    .sessionsOpened() + ", closed=" + m.sessionsClosed() + ", timedOut=" + m.sessionsTimedOut()));
    }
}
