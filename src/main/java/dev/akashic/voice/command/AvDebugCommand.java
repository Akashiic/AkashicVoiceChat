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
        VoiceServer server = VoiceServerHolder.get();
        int connected = server != null ? server.connectionCount() : 0;
        sender.addChatMessage(
            new ChatComponentText(
                "Akashic Voice: UDP " + AkashicVoiceConfig
                    .voicePort() + ", authenticated=" + connected + ", bootstrapTickets=" + SecretRegistry.size()));
    }
}
