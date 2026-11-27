package dev.akashic.voice.voice.client;

/** Issue #4: client-side backend-transfer signal. Pure logic; no game startup. */
public final class BackendTransferDetectorSmoke {
    static int passed;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        BackendTransferDetector d = new BackendTransferDetector();
        check(!d.observe(41), "first observation must not trigger"); passed++;
        check(!d.observe(41), "same entity (respawn / dimension change) must not trigger"); passed++;
        check(d.observe(907), "new entity id while in world must trigger"); passed++;
        check(!d.observe(907), "trigger must be edge, not level"); passed++;
        d.reset();
        check(!d.observe(12), "first observation after leaving the world must not trigger"); passed++;
        check(d.observe(Integer.MAX_VALUE) && d.observe(Integer.MIN_VALUE + 1), "extreme ids"); passed++;
        System.out.println("BACKEND_TRANSFER_DETECTOR_OK checks=" + passed);
    }
}
