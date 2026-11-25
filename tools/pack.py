#!/usr/bin/env python3
"""Generates the mod's finish data from finishes.tsv, skins.json (patterns) and guns.json (each gun's shell), for every
TaCZ release extract.py unpacked:

    python tools/extract.py      # once, and whenever a TaCZ jar in libs/ changes
    python tools/pack.py         # regions are cached in build/regions-cache; the first run takes a while

Writes (and owns) these in src/main/resources:

- assets/taczspray/finishes/<gun>_<cover>[_<release>]/main.bin, lod.bin: the painted texels of one gun's region, shared
  by every pattern on it. Two TaCZ releases with byte-identical results share one directory; otherwise each release
  gets its own and finish.json maps release to directory.
- assets/taczspray/finishes/<finish>/finish.json, pattern.png
- assets/taczspray/lang/en_us.json, zh_cn.json: the pattern names (taczspray.pattern.<pattern>); other keys are kept
- assets/taczspray/finishes/guns.json: the guns that have finishes, so that a client knows when to start spraying
- assets/taczspray/finishes/regions.json: each of those guns' two regions, {gun: {"shell": texels, "full": texels}}
  (texels as in finish.json), where a server owner's own finishes (config/taczspray/finishes/) are painted
- data/taczspray/finish_table.json: which finishes each gun can get, their grades and wear ranges, and the odds; the
  server rolls from it

Only our own data: the default gun pack's textures are never copied (CC BY-NC-ND).
"""
import hashlib
import json
import os
import pickle
import shutil
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
RELEASES = {"1.1.8": "1.1", "1.2.0": "1.2"}   # extracted version -> the family the client matches TaCZ's version to
ASSETS = ROOT / "src/main/resources/assets/taczspray"
DATA = ROOT / "src/main/resources/data/taczspray"
CACHE = ROOT / "build/regions-cache"
STAGE = ROOT / "build/regions-out"
TSV = TOOLS / "finishes.tsv"
CELL = 0.25
# a server owner's own finishes are named custom_<folder>; none of ours may start so
CUSTOM = "custom_"
# bump when skingen.paint_region changes what it computes: every cached region is recomputed
REGION_VERSION = 10
ENGLISH = {"sakura": "Sakura", "celadon": "Celadon", "ice": "Ice Crackle", "rust": "Rust", "lava": "Lava", "neon": "Neon",
           "hazard": "Hazard", "candy": "Candy", "marble": "Marble", "brass": "Brass", "dots": "Polka Dots",
           "sunset": "Sunset", "waves": "Waves", "tartan": "Tartan", "brushed": "Brushed Steel", "chrome": "Chrome",
           "anodized": "Anodized Blue", "gold": "Gold", "titanium": "Heat-Tinted Titanium"}


def rows():
    out = []
    for line in TSV.read_text(encoding="utf-8").splitlines():
        cols = line.split("\t")
        if line.startswith("#") or cols[0] == "finish" or len(cols) < 5:
            continue
        fid, gun_rl, grade, wmin, wmax = cols[:5]
        gun = gun_rl.split(":")[1]
        if not fid.startswith(gun + "_"):
            raise SystemExit(f"finishes.tsv: {fid} is not <gun>_<pattern> (gun {gun})")
        out.append({"id": fid, "gun": gun_rl, "short": gun, "pattern": fid[len(gun) + 1:], "grade": int(grade),
                    "wear_min": float(wmin), "wear_max": float(wmax)})
    return out


def shell_for(shells, gun, release):
    """The gun's shell for this release: guns.json's "shell@<release>" when the models differ, else "shell"."""
    entry = shells[gun]
    return entry.get(f"shell@{release}", entry["shell"])


# ---- step 1, in a process of its own per release (skingen reads TACZ_PACK when it is imported) ----

def _model_files(gun):
    import skingen
    disp = skingen.loose_json(skingen.BASE_PACK / "display/guns" / f"{gun}_display.json")
    files = [skingen.BASE_PACK / "display/guns" / f"{gun}_display.json"]
    for key in ("model", "texture"):
        files.append(skingen.resource_path(skingen.BASE_PACK, disp[key], "geo_models" if key == "model" else "textures",
                                           ".json" if key == "model" else ".png"))
    lod = disp.get("lod") or {}
    for key in ("model", "texture"):
        if lod.get(key):
            files.append(skingen.resource_path(skingen.BASE_PACK, lod[key],
                                               "geo_models" if key == "model" else "textures",
                                               ".json" if key == "model" else ".png"))
    return files


def _cache_path(gun, cover, shell):
    """Keyed by the model and texture files' contents, so releases that ship the same gun share the computation."""
    import skingen
    h = hashlib.sha1(json.dumps([REGION_VERSION, gun, cover, shell, skingen.HIDDEN.pattern,
                                 skingen.UNPAINTED.pattern], sort_keys=True).encode())
    for f in _model_files(gun):
        h.update(f.read_bytes() if f.exists() else b"-")
    return CACHE / f"{gun}_{cover}_{h.hexdigest()[:16]}.pkl"


def _region(job):
    gun, cover, shell_json = job
    sys.path.insert(0, str(TOOLS))
    import regions
    import render
    import skingen
    shell = json.loads(shell_json)
    path = _cache_path(gun, cover, shell)
    if path.exists():
        return job, pickle.loads(path.read_bytes())
    geo, tex = render.load(gun)
    reg = shell if cover == "shell" else {"include": regions.root_of(geo)}
    moving = skingen.animated_bones(gun)
    texels, size, rep = skingen.paint_region(tex, geo, reg["include"], reg.get("exclude", ()), moving=moving)
    main = skingen.pack(texels, tex.size, size, tex.size[0] / geo["description"]["texture_width"])
    lod = None
    try:
        lgeo, ltex = render.load(gun, lod=True)
    except (KeyError, FileNotFoundError, SystemExit):
        lgeo = None
    if lgeo is not None:
        idx, _ = regions.lod_paint(geo, lgeo, reg)
        lt, lsize, _ = skingen.paint_region(ltex, lgeo, [], only=idx, moving=moving)
        lod = skingen.pack(lt, ltex.size, lsize, ltex.size[0] / lgeo["description"]["texture_width"])
    result = (skingen.texels_bytes(main), skingen.texels_bytes(lod) if lod else None)
    CACHE.mkdir(parents=True, exist_ok=True)
    path.write_bytes(pickle.dumps(result))
    print(f"  {gun} {cover}: {rep['painted']} texels" + ("" if lod else ", no lod"), flush=True)
    return job, result


def regions_for(release):
    """Computes every region this release needs into build/regions-out/<release>/<gun>_<cover>/."""
    sys.path.insert(0, str(TOOLS))
    import regions
    spec = json.loads((TOOLS / "skins.json").read_text(encoding="utf-8"))
    covers = {p["id"]: p["cover"] for p in spec["patterns"]}
    shells = regions.load_spec()
    jobs = set()
    for r in rows():
        if r["short"] not in shells:
            raise SystemExit(f"finishes.tsv {r['id']}: no shell for {r['short']} in guns.json")
        if r["pattern"] not in covers:
            raise SystemExit(f"finishes.tsv {r['id']}: no pattern {r['pattern']} in skins.json")
        jobs.add((r["short"], covers[r["pattern"]], json.dumps(shell_for(shells, r["short"], release), sort_keys=True)))
    out = STAGE / release
    shutil.rmtree(out, ignore_errors=True)
    workers = max(1, min(len(jobs), (os.cpu_count() or 2) - 1))
    with ProcessPoolExecutor(max_workers=workers) as pool:
        for (gun, cover, _), (main, lod) in pool.map(_region, sorted(jobs)):
            d = out / f"{gun}_{cover}"
            d.mkdir(parents=True, exist_ok=True)
            (d / "main.bin").write_bytes(main)
            if lod:
                (d / "lod.bin").write_bytes(lod)
    print(f"{release}: {len(jobs)} regions -> {out.relative_to(ROOT)}")


# ---- step 2 and 3, in the parent ----

def run_release(release):
    env = dict(os.environ, TACZ_PACK=str(ROOT / "build/tacz-pack" / release), PYTHONIOENCODING="utf-8")
    if not (ROOT / "build/tacz-pack" / release / "pack").is_dir():
        raise SystemExit(f"build/tacz-pack/{release} is missing: run tools/extract.py first")
    subprocess.run([sys.executable, __file__, "--regions", release], check=True, env=env)


def merge():
    """Region directories, shared when every release computed the same bytes. {region: {family: directory}}."""
    # the licence notice for this data is written by hand; keep it
    notice = ASSETS / "finishes" / "NOTICE.txt"
    kept = notice.read_bytes() if notice.exists() else None
    shutil.rmtree(ASSETS / "finishes", ignore_errors=True)
    if kept is not None:
        notice.parent.mkdir(parents=True)
        notice.write_bytes(kept)
    regions_all = sorted({d.name for rel in RELEASES for d in (STAGE / rel).iterdir()})
    mapping = {}
    total = 0
    for region in regions_all:
        blobs = {}
        for rel, family in RELEASES.items():
            d = STAGE / rel / region
            blobs[family] = tuple((d / f).read_bytes() if (d / f).exists() else None for f in ("main.bin", "lod.bin"))
        distinct = {}
        for family, blob in blobs.items():
            distinct.setdefault(blob, []).append(family)
        mapping[region] = {}
        for blob, families in distinct.items():
            name = region if len(distinct) == 1 else f"{region}_{families[0].replace('.', '')}"
            d = ASSETS / "finishes" / name
            d.mkdir(parents=True, exist_ok=True)
            for f, b in zip(("main.bin", "lod.bin"), blob):
                if b is not None:
                    (d / f).write_bytes(b)
                    total += len(b)
            for family in families:
                mapping[region][family] = name
    split = sum(1 for m in mapping.values() if len(set(m.values())) > 1)
    print(f"{len(mapping)} regions, {split} differ between releases, {total / 1e6:.1f} MB of texels")
    return mapping


def texels_of(mapping, region):
    """finish.json's "texels" for a region: one directory, or {family: directory} when the releases differ."""
    by_family = mapping[region]
    return next(iter(by_family.values())) if len(set(by_family.values())) == 1 else dict(sorted(by_family.items()))


def write(mapping):
    sys.path.insert(0, str(TOOLS))
    import skinalgo as algo
    import skingen
    spec = json.loads((TOOLS / "skins.json").read_text(encoding="utf-8"))
    patterns = {p["id"]: p for p in spec["patterns"]}
    for pid in patterns:
        if pid.startswith(CUSTOM):
            raise SystemExit(f"skins.json: {pid}: {CUSTOM}* is kept for server owners' own finishes")
    tiles = {pid: skingen.pattern_tile(p["pattern"], algo.str_hash(pid)) for pid, p in patterns.items()}
    table = []
    for r in rows():
        pat = patterns[r["pattern"]]
        texels = texels_of(mapping, f"{r['short']}_{pat['cover']}")
        pixel = pat.get("cell", CELL)
        meta = {"v": 1, "pattern": r["pattern"], "pixel": pixel, "lod_pixel": pixel, "texels": texels}
        if pat.get("anim"):
            meta["anim"] = pat["anim"]
        if pat.get("projection", "box") != "box":
            meta["projection"] = pat["projection"]
        if pat.get("material"):
            meta["material"] = pat["material"]
        out = ASSETS / "finishes" / r["id"]
        out.mkdir(parents=True, exist_ok=True)
        (out / "finish.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        tiles[r["pattern"]].save(out / "pattern.png", optimize=True)
        table.append({"id": r["id"], "gun": r["gun"], "look": r["pattern"], "grade": r["grade"],
                      "wear_min": r["wear_min"], "wear_max": r["wear_max"]})
    odds = json.loads((TOOLS / "odds.json").read_text(encoding="utf-8"))
    DATA.mkdir(parents=True, exist_ok=True)
    (DATA / "finish_table.json").write_text(json.dumps(
        {"v": 1, "grades": [g["weight"] for g in odds["grades"]],
         "bands": [{"upper": b["upper"], "weight": b["weight"]} for b in odds["bands"]],
         "finishes": table}, indent=1) + "\n", encoding="utf-8")
    # the guns a spray can works on, for the client to decide whether to start spraying; the server's table decides
    guns = sorted({r["gun"] for r in table})
    (ASSETS / "finishes" / "guns.json").write_text(json.dumps(guns, indent=1) + "\n", encoding="utf-8")
    # where a server owner's own finishes are painted: every gun's two regions
    regions = {}
    for gun in guns:
        short = gun.split(":")[1]
        missing = [c for c in ("shell", "full") if f"{short}_{c}" not in mapping]
        if missing:
            raise SystemExit(f"{gun} has no {missing} region: give it a finish of that cover in finishes.tsv")
        regions[short] = {c: texels_of(mapping, f"{short}_{c}") for c in ("shell", "full")}
    (ASSETS / "finishes" / "regions.json").write_text(json.dumps(regions, indent=1, sort_keys=True) + "\n",
                                                      encoding="utf-8")
    for lang, names in (("zh_cn", {p: patterns[p]["name"] for p in patterns}),
                        ("en_us", {p: ENGLISH.get(p, patterns[p]["name"]) for p in patterns})):
        path = ASSETS / "lang" / f"{lang}.json"
        cur = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        cur = {k: v for k, v in cur.items() if not k.startswith("taczspray.pattern.")}
        cur.update({f"taczspray.pattern.{p}": n for p, n in names.items()})
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(dict(sorted(cur.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(table)} finishes on {len({r['gun'] for r in table})} guns written")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) == 3 and sys.argv[1] == "--regions":
        regions_for(sys.argv[2])
        return 0
    for release in RELEASES:
        run_release(release)
    write(merge())
    return 0


if __name__ == "__main__":
    sys.exit(main())
