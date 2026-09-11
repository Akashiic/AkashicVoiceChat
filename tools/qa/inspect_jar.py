import struct
import sys
import zipfile
from collections import Counter
from pathlib import Path

jar = Path(sys.argv[1])
majors = Counter()
project_majors = Counter()
codec_present = False
license_present = False
with zipfile.ZipFile(jar) as zf:
    for info in zf.infolist():
        name = info.filename
        if name == "META-INF/licenses/Concentus-LICENSE.txt":
            license_present = True
        if "concentus" in name.lower() and name.endswith(".class"):
            codec_present = True
        if not name.endswith(".class"):
            continue
        data = zf.read(info)[:8]
        if len(data) != 8 or data[:4] != b"\xca\xfe\xba\xbe":
            raise SystemExit("invalid class header: " + name)
        major = struct.unpack(">H", data[6:8])[0]
        majors[major] += 1
        if name.startswith("dev/akashic/voice/"):
            project_majors[major] += 1

print("jar=", jar)
print("allClassMajors=", dict(sorted(majors.items())))
print("akashicClassMajors=", dict(sorted(project_majors.items())))
print("concentusClassesPresent=", codec_present)
print("concentusLicensePresent=", license_present)
if set(project_majors) != {52}:
    raise SystemExit("Akashic classes are not exclusively Java 8 bytecode")
if not codec_present or not license_present:
    raise SystemExit("shaded codec or its license is missing")
