# Akashic Voice - Performance Baseline

This document records measurements, not assumptions. Results are local baselines used to compare patches; they are not universal hardware claims.

## Baseline host

- CPU: Intel Core i7-10510U, 4 cores / 8 logical CPUs.
- RAM: 15.9 GiB visible.
- OS: Windows 10 10.0.19045.
- Client JVM: Oracle JDK 8u411.
- Server comparison JVM: Temurin 21.0.11.
- Audio: 48 kHz mono, 20 ms / 960 samples.

The host was not isolated from normal desktop workloads or other development agents. Decisions use multi-process medians and orders of magnitude rather than one optimistic run.

## Pure-Java Concentus baseline

The earlier 48 kbit/s `PerformanceProbe` established that CPU throughput was usable but Java allocation was high:

| Operation | Median time | Allocation |
|---|---:|---:|
| Concentus encode | ~634 us/frame | ~127.6 KiB/frame |
| Concentus decode | ~144 us/frame | ~30.0 KiB/frame |
| AES-GCM round-trip | ~4.21 us | ~2.4 KiB/round-trip |
| AES-GCM fan-out to 20 | ~26.4 us/source-frame | ~18.9 KiB/source-frame |

At 50 frames/s that codec allocation is several MiB/s while talking plus roughly 1-1.5 MiB/s for each continuously audible speaker. This is undesirable for old collectors, small heaps and mobile runtimes.

## Integrated native libopus backend

`com.plasmoverse:opus-jni-rust:1.0.4` is now bundled without JNI package relocation. `auto` prefers it on supported platforms and falls back to Concentus; `native` is strict; `java` forces Concentus.

Five separate JDK 8 JVM runs of `CodecBackendPerformanceProbe` used the current configured default bitrate (48 kbit/s). Median values:

| Operation | Native median | Java median | Native advantage |
|---|---:|---:|---:|
| encode | ~144.20 us/frame | ~537.59 us/frame | ~3.73x faster |
| decode | ~31.67 us/frame | ~72.41 us/frame | ~2.29x faster |
| encode allocation | ~144 B/frame | ~134,128 B/frame | ~931x lower |
| decode allocation | ~1,936 B/frame | ~29,896 B/frame | ~15.4x lower |

At 50 frames/s, the measured native Java-side allocation is about 7.0 KiB/s for encoding and 94.5 KiB/s per continuously decoded speaker, before surrounding network/playback work.

The native wrapper still allocates its encoded `byte[]` and decoded `short[]`; the Akashic adapter copies those into existing buffers. A future custom `encodeInto/decodeInto` JNI can remove most remaining codec allocation, but is not required for the current desktop performance target.

## Native packaging

The JNI classes must keep the upstream `com.plasmoverse.opus.*` names because native symbols bind to those class names. A normal Shadow relocation was tested and failed with `UnsatisfiedLinkError`, so the native bundle is inserted after the relocation pipeline.

The audited JAR currently contains:

- 5 original `com/plasmoverse/opus/*.class` files;
- 0 relocated copies of those JNI classes;
- 7 native binaries: Windows x86/x86_64, Linux x86/x86_64/aarch64, macOS x86_64/aarch64;
- Concentus in the existing relocated Java fallback path;
- physical third-party license notices and provenance for the native dependency graph.
- build-time SHA-256 verification of the exact `opus-jni-rust:1.0.4` artifact before packaging.

Android is not a listed native target. Pojav/Amethyst therefore remains a separate test gate; `auto` is designed to fall back to Concentus if native loading fails.

## Memory bounding

The native decoder wrapper requires an exact-length encoded array. Akashic Voice keeps at most 8 reusable packet-size scratch arrays per remote decoder instead of retaining one array for every possible Opus length. The stress smoke forced 128 different lengths and still retained exactly 8 slots (996 bytes in that run).

PCM playback remains bounded by the 128-frame preallocated pool and the fixed per-speaker jitter ring.

## Remaining performance work

Gate 5 is not fully closed until measurements exist inside real Minecraft sessions. Required evidence still includes:

- capture cadence and render/tick impact under gameplay load;
- long-session GC/heap behavior with multiple simultaneous speakers;
- real Crucible routing/fan-out with multiple clients;
- packet loss/jitter latency under live network conditions;
- ARM/Pojav thermal, battery and allocation profiles;
- eventual bridge/Gateway measurements when SVC interoperability exists.
