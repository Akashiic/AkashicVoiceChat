import struct
import sys
import zipfile
from pathlib import PurePosixPath

if len(sys.argv) != 2:
    raise SystemExit("usage: DistributionJarSmoke.py <akashicvoice.jar>")

jar = sys.argv[1]
expected_jni = {
    "com/plasmoverse/opus/OpusDecoder.class",
    "com/plasmoverse/opus/OpusEncoder.class",
    "com/plasmoverse/opus/OpusException.class",
    "com/plasmoverse/opus/OpusLibrary.class",
    "com/plasmoverse/opus/OpusMode.class",
}
expected_natives = {
    "natives/linux-aarch64/libopus_jni_rust.so",
    "natives/linux-x86/libopus_jni_rust.so",
    "natives/linux-x86_64/libopus_jni_rust.so",
    "natives/mac-aarch64/libopus_jni_rust.dylib",
    "natives/mac-x86_64/libopus_jni_rust.dylib",
    "natives/win-x86/opus_jni_rust.dll",
    "natives/win-x86_64/opus_jni_rust.dll",
}
required_notices = {
    "META-INF/licenses/Concentus-LICENSE.txt",
    "META-INF/licenses/native-opus/THIRD-PARTY.txt",
    "META-INF/licenses/native-opus/LICENSE-PROVENANCE.txt",
    "META-INF/licenses/native-opus/opus-jni-rust-1.0.4/LICENSE",
    "META-INF/licenses/native-opus/crates/opusic-sys-0.7.3/opus/COPYING",
}

with zipfile.ZipFile(jar) as zf:
    names = set(zf.namelist())
    jni = {n for n in names if n.startswith("com/plasmoverse/opus/") and n.endswith(".class")}
    natives = {n for n in names if n.startswith("natives/") and n.endswith((".dll", ".so", ".dylib"))}
    if jni != expected_jni:
        raise SystemExit("unexpected JNI class set: %r" % sorted(jni))
    if any("shadow/com/plasmoverse/opus" in n for n in names):
        raise SystemExit("JNI classes were relocated; native symbols will not bind")
    if natives != expected_natives:
        raise SystemExit("unexpected native binary set: %r" % sorted(natives))
    missing_notices = required_notices - names
    if missing_notices:
        raise SystemExit("missing license notices: %r" % sorted(missing_notices))
    majors = set()
    for name in names:
        if not name.endswith(".class"):
            continue
        data = zf.read(name)[:8]
        if len(data) != 8 or data[:4] != b"\xca\xfe\xba\xbe":
            raise SystemExit("invalid class header: " + name)
        majors.add(struct.unpack(">H", data[6:8])[0])
    if majors != {52}:
        raise SystemExit("distribution is not exclusively Java 8 bytecode: %r" % sorted(majors))

    inventory = zf.read("META-INF/licenses/native-opus/THIRD-PARTY.txt").decode("utf-8")
    checked = 0
    for line in inventory.splitlines():
        if " | " not in line or line.startswith("Package") or line.startswith("---"):
            continue
        cols = [part.strip() for part in line.split("|")]
        if len(cols) < 4:
            continue
        name, version = cols[0], cols[1]
        if name in ("opus", "opus-jni-rust"):
            continue
        prefix = "META-INF/licenses/native-opus/crates/%s-%s/" % (name, version)
        notices = [n for n in names if n.startswith(prefix) and PurePosixPath(n).name.upper().startswith(("LICENSE", "COPYING"))]
        if not notices:
            raise SystemExit("no bundled license notice for %s %s" % (name, version))
        checked += 1

print(
    "distribution-jar-smoke-ok classes_java8=%d jni=%d natives=%d notices=%d inventory=%d"
    % (sum(1 for n in names if n.endswith(".class")), len(expected_jni), len(expected_natives), len(required_notices), checked)
)
