package dev.akashic.voice.platform.agent;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeConstants;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AgentMessageCodec {
    private AgentMessageCodec() {}

    public static void write(ByteBuffer out, AgentMessage message) {
        if (message instanceof AgentMessage.Ready) {
            out.put(AgentProtocol.READY);
        } else if (message instanceof AgentMessage.Attach attach) {
            out.put(AgentProtocol.MANAGED_ATTACH);
            writeUuid(out, attach.player().playerId());
            writeString(out, attach.player().backendId(), false);
            writeString(out, attach.player().playerName(), false);
        } else if (message instanceof AgentMessage.Detach detach) {
            out.put(AgentProtocol.MANAGED_DETACH);
            writeUuid(out, detach.playerId());
        } else if (message instanceof AgentMessage.SpeakerFrame speaker) {
            writeSpeakerFrame(out, speaker.frame());
        } else if (message instanceof AgentMessage.SpeakerReset reset) {
            out.put(AgentProtocol.AKASHIC_RESET);
            writeUuid(out, reset.speakerId());
        } else if (message instanceof AgentMessage.SvcDelivery svc) {
            writeSvcDelivery(out, svc.delivery());
        } else if (message instanceof AgentMessage.SvcReset reset) {
            writeSvcReset(out, reset.reset());
        } else if (message instanceof AgentMessage.Ping ping) {
            out.put(AgentProtocol.PING).putLong(ping.timestamp());
        } else if (message instanceof AgentMessage.Pong pong) {
            out.put(AgentProtocol.PONG).putLong(pong.timestamp());
        } else {
            throw new IllegalArgumentException("unknown agent message");
        }
        if (out.position() > AgentProtocol.MAX_PLAINTEXT_BYTES) throw new IllegalArgumentException("agent frame too large");
    }
    public static AgentMessage read(ByteBuffer in) {
        if (in.remaining() < 1 || in.remaining() > AgentProtocol.MAX_PLAINTEXT_BYTES) return null;
        byte type = in.get();
        AgentMessage message;
        try {
            message = switch (type) {
                case AgentProtocol.READY -> new AgentMessage.Ready();
                case AgentProtocol.MANAGED_ATTACH -> readAttach(in);
                case AgentProtocol.MANAGED_DETACH -> new AgentMessage.Detach(readUuid(in));
                case AgentProtocol.AKASHIC_FRAME -> new AgentMessage.SpeakerFrame(readSpeakerFrame(in));
                case AgentProtocol.AKASHIC_RESET -> new AgentMessage.SpeakerReset(readUuid(in));
                case AgentProtocol.SVC_DELIVERY -> new AgentMessage.SvcDelivery(readSvcDelivery(in));
                case AgentProtocol.SVC_RESET -> new AgentMessage.SvcReset(readSvcReset(in));
                case AgentProtocol.PING -> new AgentMessage.Ping(in.getLong());
                case AgentProtocol.PONG -> new AgentMessage.Pong(in.getLong());
                default -> null;
            };
        } catch (RuntimeException malformed) {
            return null;
        }
        return message != null && !in.hasRemaining() ? message : null;
    }

    private static AgentMessage.Attach readAttach(ByteBuffer in) {
        UUID playerId = readUuid(in);
        String backendId = readString(in, false);
        if (!AgentHandshake.isValidBackendId(backendId)) throw new IllegalArgumentException("backendId");
        String playerName = readString(in, false);
        return new AgentMessage.Attach(new ManagedPlayer(playerId, backendId, playerName));
    }

    private static void writeSpeakerFrame(ByteBuffer out, AkashicSpeakerFrame frame) {
        out.put(AgentProtocol.AKASHIC_FRAME);
        writeUuid(out, frame.speakerId());
        out.putLong(frame.sequence());
        out.put((byte) (frame.whispering() ? 1 : 0));
        writeOpus(out, frame.opusUnsafe());
    }
    private static AkashicSpeakerFrame readSpeakerFrame(ByteBuffer in) {
        UUID speakerId = readUuid(in);
        long sequence = in.getLong();
        if (sequence < 0L) throw new IllegalArgumentException("sequence");
        byte flags = in.get();
        if ((flags & ~1) != 0) throw new IllegalArgumentException("flags");
        byte[] opus = readOpus(in);
        return new AkashicSpeakerFrame(speakerId, sequence, (flags & 1) != 0, opus);
    }

    private static void writeSvcDelivery(ByteBuffer out, BridgeDelivery delivery) {
        out.put(AgentProtocol.SVC_DELIVERY);
        writeUuid(out, delivery.recipientId());
        writeUuid(out, delivery.senderId());
        writeUuid(out, delivery.channelId());
        out.putLong(delivery.sequence());
        out.put((byte) delivery.spatialKind().ordinal());
        out.put((byte) (delivery.whispering() ? 1 : 0));
        out.putFloat(delivery.distance());
        writeString(out, delivery.category(), true);
        if (delivery.spatialKind() == SpatialKind.ENTITY) writeUuid(out, delivery.entityId());
        writePosition(out, delivery.position());
        writeOpus(out, delivery.opusUnsafe());
    }

    private static BridgeDelivery readSvcDelivery(ByteBuffer in) {
        UUID recipient = readUuid(in);
        UUID sender = readUuid(in);
        UUID channel = readUuid(in);
        long sequence = in.getLong();
        if (sequence < 0L) throw new IllegalArgumentException("sequence");
        int kindOrdinal = in.get() & 0xff;
        if (kindOrdinal >= SpatialKind.values().length) throw new IllegalArgumentException("kind");
        SpatialKind kind = SpatialKind.values()[kindOrdinal];
        byte flags = in.get();
        if ((flags & ~1) != 0) throw new IllegalArgumentException("flags");
        float distance = in.getFloat();
        String category = readString(in, true);
        UUID entity = kind == SpatialKind.ENTITY ? readUuid(in) : null;
        PositionSnapshot position = readPosition(in);
        byte[] opus = readOpus(in);
        return new BridgeDelivery(
            recipient, sender, channel, sequence, kind, entity, position,
            distance, (flags & 1) != 0, category, opus);
    }

    private static void writeSvcReset(ByteBuffer out, BridgeStreamReset reset) {
        out.put(AgentProtocol.SVC_RESET);
        writeUuid(out, reset.recipientId());
        writeUuid(out, reset.senderId());
        writeUuid(out, reset.channelId());
    }

    private static BridgeStreamReset readSvcReset(ByteBuffer in) {
        return new BridgeStreamReset(readUuid(in), readUuid(in), readUuid(in));
    }

    private static void writePosition(ByteBuffer out, PositionSnapshot position) {
        out.put((byte) (position == null ? 0 : 1));
        if (position == null) return;
        writeString(out, position.worldKey(), false);
        out.putDouble(position.x()).putDouble(position.y()).putDouble(position.z());
    }

    private static PositionSnapshot readPosition(ByteBuffer in) {
        byte present = in.get();
        if (present == 0) return null;
        if (present != 1) throw new IllegalArgumentException("position flag");
        String world = readString(in, false);
        return new PositionSnapshot(world, in.getDouble(), in.getDouble(), in.getDouble());
    }
    private static void writeUuid(ByteBuffer out, UUID id) {
        out.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer in) {
        if (in.remaining() < 16) throw new IllegalArgumentException("uuid");
        return new UUID(in.getLong(), in.getLong());
    }

    private static void writeOpus(ByteBuffer out, byte[] opus) {
        if (opus == null || opus.length == 0 || opus.length > BridgeConstants.MAX_OPUS_PAYLOAD) {
            throw new IllegalArgumentException("opus");
        }
        out.putShort((short) opus.length).put(opus);
    }

    private static byte[] readOpus(ByteBuffer in) {
        if (in.remaining() < 2) throw new IllegalArgumentException("opus length");
        int length = in.getShort() & 0xffff;
        if (length == 0 || length > BridgeConstants.MAX_OPUS_PAYLOAD || in.remaining() < length) {
            throw new IllegalArgumentException("opus");
        }
        byte[] opus = new byte[length];
        in.get(opus);
        return opus;
    }
    private static void writeString(ByteBuffer out, String value, boolean nullable) {
        if (value == null) {
            if (!nullable) throw new IllegalArgumentException("string");
            out.putShort((short) 0xffff);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > AgentProtocol.MAX_STRING_BYTES) throw new IllegalArgumentException("string too long");
        out.putShort((short) bytes.length).put(bytes);
    }

    private static String readString(ByteBuffer in, boolean nullable) {
        if (in.remaining() < 2) throw new IllegalArgumentException("string length");
        int length = in.getShort() & 0xffff;
        if (length == 0xffff) {
            if (!nullable) throw new IllegalArgumentException("null string");
            return null;
        }
        if (length > AgentProtocol.MAX_STRING_BYTES || in.remaining() < length) {
            throw new IllegalArgumentException("string length");
        }
        byte[] bytes = new byte[length];
        in.get(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("invalid UTF-8");
        }
        return value;
    }
}
