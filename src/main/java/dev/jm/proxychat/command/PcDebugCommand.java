package dev.jm.proxychat.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.server.SecretRegistry;

public class PcDebugCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "pcdebug";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pcdebug — list ProxyChat voice secrets";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Map<UUID, UUID> snapshot = SecretRegistry.snapshot();
        sender.addChatMessage(
            new ChatComponentText(
                "ProxyChat: " + snapshot.size() + " active secret(s), voicePort=" + ProxyChatConfig.voicePort()));
        for (Map.Entry<UUID, UUID> e : snapshot.entrySet()) {
            sender.addChatMessage(new ChatComponentText("  " + e.getKey() + " -> " + e.getValue()));
        }
    }
}
