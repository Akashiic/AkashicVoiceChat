package dev.akashic.voice.voice.client.playback;

import org.lwjgl.openal.AL;

public final class OpenAlOutputHealthSmoke {

    public static void main(String[] args) throws Exception {
        boolean created = false;
        try {
            if (!AL.isCreated()) {
                AL.create();
                created = true;
            }
            OpenAlOutputHealth health = OpenAlOutputHealth.capture();
            OpenAlOutputHealth.Status status = health.poll(true);
            if (status != OpenAlOutputHealth.Status.HEALTHY) {
                throw new AssertionError("Unexpected OpenAL output health: " + status);
            }
            String name = health.expectedDefaultDevice();
            System.out.println("OPENAL_OUTPUT_HEALTH_OK default=" + (name == null ? "<unknown>" : name));
        } finally {
            if (created && AL.isCreated()) AL.destroy();
        }
    }
}
