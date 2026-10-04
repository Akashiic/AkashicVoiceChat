package dev.akashic.voice.voice.server.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import cpw.mods.fml.relauncher.QualityTestEnvironment;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.server.VoiceServerHolder;

public final class LegacyAgentConfigSmoke {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Paths.get(".").toAbsolutePath(), "agent46-config-");
        try (QualityTestEnvironment environment = new QualityTestEnvironment(dir.toFile())) {
            Path config = dir.resolve("agent.cfg");
            Files.write(config, Arrays.asList(
                "voice {", " S:serverMode=agent", " I:voicePort=24454", "}",
                "agent {", " S:backendId=forbidden-1", " S:gatewayHost=127.0.0.1",
                " I:gatewayPort=10503", " S:pskBase64=", "}"), StandardCharsets.UTF_8);
            AkashicVoiceConfig.load(config.toFile());
            check(AkashicVoiceConfig.agentMode(), "agent mode not loaded");
            check("forbidden-1".equals(AkashicVoiceConfig.agentBackendId()), "backend id mismatch");
            check(AkashicVoiceConfig.agentGatewayPort() == 10503, "agent port mismatch");
            VoiceServerHolder.stop();
            check(!LegacyGatewayAgentHolder.startConfigured(), "blank PSK unexpectedly started Agent");
            check(VoiceServerHolder.get() == null, "fail-closed Agent mode opened direct UDP");

            Path direct = dir.resolve("direct.cfg");
            Files.write(direct, Arrays.asList("voice {", " S:serverMode=direct", "}"), StandardCharsets.UTF_8);
            AkashicVoiceConfig.load(direct.toFile());
            check(!AkashicVoiceConfig.agentMode(), "direct mode not preserved");
            check("direct".equals(AkashicVoiceConfig.serverMode()), "direct mode string mismatch");
        } finally {
            LegacyGatewayAgentHolder.stop();
            VoiceServerHolder.stop();
        }
        System.out.println("LEGACY_AGENT_CONFIG_OK failClosed=true directCompatible=true");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
