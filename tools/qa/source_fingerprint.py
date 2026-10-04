"""Hash project build inputs without caches, logs, worlds or runtime credentials."""
from pathlib import Path
import hashlib


def fingerprint(root: Path, platform_only: bool = False):
    roots = list((root / 'platform').glob('*/src')) + [root / 'shared/client-protocol/src']
    if not platform_only:
        roots += [root / 'src']
    files = {path for base in roots for path in base.rglob('*') if path.is_file()}
    files.update((root / 'platform').glob('*.gradle.kts'))
    files.update((root / 'platform').glob('*/*.gradle.kts'))
    if not platform_only:
        files.update(root.glob('*.gradle*'))
        files.update(root.glob('gradle.properties'))
    result = {}
    for path in sorted(files):
        if not path.is_file():
            continue
        path.resolve().relative_to(root.resolve())
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(65536), b''):
                digest.update(chunk)
        result[path.relative_to(root).as_posix()] = digest.hexdigest()
    return result
