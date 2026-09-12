package dev.akashic.voice.voice.client.audio.capture;

public final class AudioCaptureDeviceInfo {

    private final String id;
    private final String displayName;

    public AudioCaptureDeviceInfo(String id, String displayName) {
        if (id == null) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isEmpty()) throw new IllegalArgumentException("displayName");
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
