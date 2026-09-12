package dev.akashic.voice.voice.client.audio.capture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCdevice;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class OpenALCaptureBackend implements AudioCaptureBackend {

    public static final OpenALCaptureBackend INSTANCE = new OpenALCaptureBackend();
    public static final String ID_PREFIX = "openal:";

    private static final int CAPTURE_BUFFER_SAMPLES = AudioFormatSpec.FRAME_SAMPLES * 8;
    private static final long EMPTY_POLL_NANOS = 1_000_000L;

    private OpenALCaptureBackend() {}

    @Override
    public AudioCaptureDevice open(String deviceId) throws AudioCaptureException {
        if (!isOpenAlReady()) {
            throw new AudioCaptureException("OpenAL is not initialized or unavailable");
        }
        String deviceName = deviceName(deviceId);
        ALCdevice device = null;
        try {
            device = ALC11.alcCaptureOpenDevice(
                deviceName.isEmpty() ? null : deviceName,
                AudioFormatSpec.SAMPLE_RATE,
                AL10.AL_FORMAT_MONO16,
                CAPTURE_BUFFER_SAMPLES);
            if (device == null) {
                throw new AudioCaptureException("OpenAL capture device could not be opened");
            }
            ALC11.alcCaptureStart(device);
            int error = ALC10.alcGetError(device);
            if (error != ALC10.ALC_NO_ERROR) {
                throw new AudioCaptureException("OpenAL capture start failed with ALC error " + error);
            }
            String description = deviceName.isEmpty() ? defaultDeviceName() : deviceName;
            return new Device(device, description);
        } catch (AudioCaptureException e) {
            safeClose(device);
            throw e;
        } catch (Throwable t) {
            rethrowFatal(t);
            safeClose(device);
            throw new AudioCaptureException("OpenAL capture unavailable", t);
        }
    }

    @Override
    public List<AudioCaptureDeviceInfo> listDevices() {
        String name = "OpenAL default microphone";
        if (isOpenAlReady()) {
            try {
                String detected = defaultDeviceName();
                if (!detected.isEmpty()) name = "OpenAL: " + detected;
            } catch (Throwable failure) {
                rethrowFatal(failure);
                // Keep the synthetic default. Enumeration must never break the GUI.
            }
        }
        return Collections.singletonList(new AudioCaptureDeviceInfo(ID_PREFIX, name));
    }

    @Override
    public String name() {
        return "OpenAL Capture";
    }

    private static boolean isOpenAlReady() {
        try {
            return AL.isCreated();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return false;
        }
    }

    private static String deviceName(String deviceId) throws AudioCaptureException {
        if (deviceId == null || deviceId.isEmpty() || ID_PREFIX.equals(deviceId)) return "";
        if (!deviceId.startsWith(ID_PREFIX)) {
            throw new AudioCaptureException("Selected microphone does not belong to the OpenAL backend: " + deviceId);
        }
        return deviceId.substring(ID_PREFIX.length());
    }

    private static String defaultDeviceName() {
        String name = ALC10.alcGetString(null, ALC11.ALC_CAPTURE_DEFAULT_DEVICE_SPECIFIER);
        return name != null ? name : "";
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }

    private static void safeClose(ALCdevice device) {
        if (device == null) return;
        try {
            ALC11.alcCaptureStop(device);
        } catch (Throwable ignored) {
            // best effort
        }
        try {
            ALC11.alcCaptureCloseDevice(device);
        } catch (Throwable ignored) {
            // best effort
        }
    }

    private static final class Device implements AudioCaptureDevice {

        private final Object lock = new Object();
        private final ALCdevice device;
        private final String description;
        private final IntBuffer availableScratch = BufferUtils.createIntBuffer(1);
        private final ByteBuffer captureScratch = BufferUtils.createByteBuffer(AudioFormatSpec.FRAME_BYTES_PCM_S16);
        private volatile boolean closed;

        private Device(ALCdevice device, String description) {
            this.device = device;
            this.description = description == null || description.isEmpty() ? "<OpenAL default>" : description;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (buffer == null) throw new IllegalArgumentException("buffer");
            if (offset < 0 || length < 0 || offset + length > buffer.length) {
                throw new IndexOutOfBoundsException();
            }
            if (length < 2) return 0;

            while (!closed) {
                synchronized (lock) {
                    if (closed) return -1;
                    int available = availableSamples();
                    if (available > 0) {
                        int samples = Math.min(available, Math.min(length / 2, AudioFormatSpec.FRAME_SAMPLES));
                        int bytes = samples * 2;
                        captureScratch.clear();
                        captureScratch.limit(bytes);
                        ALC11.alcCaptureSamples(device, captureScratch, samples);
                        captureScratch.position(0);
                        captureScratch.limit(bytes);
                        captureScratch.get(buffer, offset, bytes);
                        return bytes;
                    }
                }
                if (Thread.currentThread()
                    .isInterrupted()) return 0;
                LockSupport.parkNanos(EMPTY_POLL_NANOS);
            }
            return -1;
        }

        private int availableSamples() {
            availableScratch.clear();
            availableScratch.limit(1);
            ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES, availableScratch);
            return Math.max(0, availableScratch.get(0));
        }

        @Override
        public String backendName() {
            return "OpenAL Capture";
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            synchronized (lock) {
                safeClose(device);
            }
        }
    }
}
