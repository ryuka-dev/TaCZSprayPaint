#!/usr/bin/env python3
"""Unpacks what the generator reads from each TaCZ release jar in libs/ into build/tacz-pack/<version>/:

    pack/        the default gun pack's assets/tacz (models, textures, displays, animations)
    animations/  TaCZ's own assets/tacz/animations (the default rifle and pistol animations)

    python tools/extract.py

Nothing here is committed or shipped: the default gun pack is CC BY-NC-ND.
"""
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JARS = {
    "1.1.8": "tacz-1.20.1-1.1.8-hotfix.jar",
    "1.2.0": "tacz-forge-1.20.1-1.2.0-hotfix.jar",
}
PACK_PREFIX = "assets/tacz/custom/tacz_default_gun/assets/tacz/"
ANIM_PREFIX = "assets/tacz/animations/"


def extract(version, jar):
    out = ROOT / "build/tacz-pack" / version
    shutil.rmtree(out, ignore_errors=True)
    counts = {"pack": 0, "animations": 0}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith("/"):
                continue
            for prefix, sub in ((PACK_PREFIX, "pack"), (ANIM_PREFIX, "animations")):
                if name.startswith(prefix):
                    rel = name[len(prefix):]
                    if ".." in Path(rel).parts:
                        raise SystemExit(f"{jar.name}: refusing path {name}")
                    target = out / sub / rel
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(name))
                    counts[sub] += 1
    if not counts["pack"]:
        raise SystemExit(f"{jar.name}: no default gun pack under {PACK_PREFIX}")
    print(f"{version}: {counts['pack']} pack files, {counts['animations']} animations -> {out.relative_to(ROOT)}")


def main():
    for version, name in JARS.items():
        jar = ROOT / "libs" / name
        if not jar.exists():
            raise SystemExit(f"missing {jar} (download the unmodified release from Modrinth)")
        extract(version, jar)
    return 0


if __name__ == "__main__":
    sys.exit(main())
