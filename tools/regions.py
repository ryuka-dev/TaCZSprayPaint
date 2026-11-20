#!/usr/bin/env python3
"""每把枪的两套上漆区域：外壳（机匣、护木、枪托、握把）与整把，近处与远处模型各一份。

    python tools/regions.py propose          # 给 guns.json 里还没有的枪按骨骼名提一版外壳（不覆盖已有的）
    python tools/regions.py sheet ak47 m4a1  # 出检查图：近处的外壳、整把，远处的外壳、整把 → out/regions/<枪>.png
    python tools/regions.py sheet --all      # 全部枪

区域写在 tools/guns.json（提交）：{"<枪>": {"shell": {"include": [...], "exclude": [...]}, "note": "..."}}。
整把是根骨骼扣掉看不见与不上漆的（skingen.HIDDEN、UNPAINTED），不用写。外壳先由 propose 按名字提，再逐把看图改。

远处模型的骨骼另起名字、位置也和近处的对不齐（ak47 差好几个单位），所以不另选：把两个模型按可见面的包围盒对齐，
远处的每个面看它中心离近处哪个面最近，那个面上不上漆，它就上不上漆。
"""
import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import render  # noqa: E402
import skingen  # noqa: E402

SPEC = Path(__file__).with_name("guns.json")
OUT = skingen.ROOT / "build/regions"

# 外壳不要的部件（骨骼或它的上级名字含这些）：枪管与枪口、弹匣、瞄具与导轨、枪机与拉机柄、扳机与快慢机、各种小件
NOT_SHELL = re.compile(r"barrel|muzzle|mag|sight|scope|rail|bolt|trigger|safety|selector|pull|charg|hammer|eject"
                       r"|bullet|mount|laser|flash|bipod|spring|screw|chamber|cylinder|drum|belt|rocket|grenade"
                       r"|suppress|silencer|foresight|iron|front_post|rear", re.I)


def guns():
    """The guns that have a shell in guns.json."""
    return sorted(load_spec())


def load_spec():
    return json.loads(SPEC.read_text(encoding="utf-8")) if SPEC.exists() else {}


def save_spec(spec):
    SPEC.write_text(json.dumps(dict(sorted(spec.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def propose_shell(geo):
    """从根骨骼起，扣掉名字像"不是外壳"的最上层骨骼。"""
    parent = {b["name"]: b.get("parent") for b in geo["bones"]}
    excluded = []
    for b in geo["bones"]:
        name = b["name"]
        if not NOT_SHELL.search(name):
            continue
        p = parent.get(name)
        if any(NOT_SHELL.search(a) for a in ancestors(p, parent)):
            continue   # 上级已经扣掉了
        excluded.append(name)
    root = "root" if "root" in parent else next(n for n, p in parent.items() if not p)
    return {"include": [root], "exclude": sorted(excluded)}


def ancestors(name, parent):
    out = []
    while name:
        out.append(name)
        name = parent.get(name)
    return out


def root_of(geo):
    names = {b["name"] for b in geo["bones"]}
    return ["root"] if "root" in names else [b["name"] for b in geo["bones"] if not b.get("parent")]


def painted_bones(geo, region):
    """一个区域实际上漆的骨骼（与 skingen.paint_region 的选法一致：减去看不见、不上漆与 exclude）。"""
    out = skingen.subtree(geo, region["include"]) - skingen.hidden_bones(geo) - skingen.hidden_bones(geo, skingen.UNPAINTED)
    if region.get("exclude"):
        out -= skingen.subtree(geo, region["exclude"])
    return out


def visible_faces(geo):
    hide = render.hidden_bones(geo)
    return [f for f in skingen.load_faces(geo) if f.bone not in hide]


def lod_paint(main_geo, lod_geo, region):
    """远处模型上该上漆的面，从近处模型的区域按位置映射过来。返回（序号集合，面表）：序号是 skingen.load_faces(lod_geo)
    里的位置（skingen.paint_region 的 only 收它），面表就是那份 load_faces。

    两个模型的可见面按包围盒对齐；远处每个面上取 5×5 个点，各找离它最近的近处面（点到矩形的距离），一半以上落在
    上漆的面上就上漆。"""
    chosen = painted_bones(main_geo, region)
    main = visible_faces(main_geo)
    lod = skingen.load_faces(lod_geo)
    hide = render.hidden_bones(lod_geo)
    shown = [f for f in lod if f.bone not in hide]
    mp = np.array([v for f in main for v in f.v])
    lp = np.array([v for f in shown for v in f.v])
    lo_m, hi_m, lo_l, hi_l = mp.min(0), mp.max(0), lp.min(0), lp.max(0)
    scale = (hi_m - lo_m) / np.maximum(1e-6, hi_l - lo_l)
    v1 = np.array([f.v[1] for f in main])
    ea = np.array([f.ea for f in main])
    eb = np.array([f.eb for f in main])
    la2 = np.maximum(1e-9, (ea * ea).sum(1))
    lb2 = np.maximum(1e-9, (eb * eb).sum(1))
    painted = np.array([f.bone in chosen for f in main])
    grid = [(i + 0.5) / 5 for i in range(5)]
    out = set()
    for i, f in enumerate(lod):
        if f.bone in hide:
            continue
        votes = 0
        for ga in grid:
            for gb in grid:
                c = (f.point(ga, gb) - lo_l) * scale + lo_m
                d = c - v1
                a = np.clip((d * ea).sum(1) / la2, 0, 1)
                b = np.clip((d * eb).sum(1) / lb2, 0, 1)
                q = v1 + a[:, None] * ea + b[:, None] * eb
                votes += bool(painted[int(np.argmin(((c - q) ** 2).sum(1)))])
        if votes * 2 >= len(grid) ** 2:
            out.add(i)
    return out, lod


def sheet(gun, spec):
    region = spec[gun].get(f"shell@{skingen.TACZ_PACK.name}", spec[gun]["shell"])
    full = {"include": None}
    main_geo, main_tex = render.load(gun)
    full["include"] = root_of(main_geo)
    imgs, labels = [], []
    for name, reg in (("外壳", region), ("整把", full)):
        chosen = painted_bones(main_geo, reg)
        tint = (lambda c: (lambda bone: (255, 40, 200) if bone in c else None))(chosen)
        imgs.append(render.render(main_geo, main_tex, "right", 7, tint))
        labels.append(f"{gun} 近处 · {name}（右）")
        imgs.append(render.render(main_geo, main_tex, "left", 7, tint))
        labels.append(f"{gun} 近处 · {name}（左）")
    try:
        lod_geo, lod_tex = render.load(gun, lod=True)
    except (KeyError, FileNotFoundError, SystemExit):
        lod_geo = None
    if lod_geo is not None:
        for name, reg in (("外壳", region), ("整把", full)):
            idx, lod_faces = lod_paint(main_geo, lod_geo, reg)
            imgs.append(render_faces(lod_geo, lod_tex, lod_faces, {id(lod_faces[i]) for i in idx}))
            labels.append(f"{gun} 远处 · {name}（右，按位置从近处映射）")
    out = render.sheet(imgs, labels)
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{gun}.png"
    out.save(path)
    return path


def render_faces(geo, tex, faces, ids):
    """远处模型：faces 里 id 在 ids 中的面涂品红。render.render 按骨骼上色、自己读面，这里把这批面交给它，
    要涂的面骨骼名加一个记号。"""
    for f in faces:
        if id(f) in ids:
            f.bone = f.bone + "\u0000paint"
    orig = skingen.load_faces
    skingen.load_faces = lambda g: faces
    try:
        return render.render(geo, tex, "right", 14, lambda bone: (255, 40, 200) if bone.endswith("\u0000paint") else None)
    finally:
        skingen.load_faces = orig


def review(names, per=6):
    """一张图看几把枪：每把一行，近处外壳、近处整把、远处外壳（都是右侧），缩小拼在一起 → out/regions/review_N.png。"""
    spec = load_spec()
    rows = []
    for gun in names:
        main_geo, main_tex = render.load(gun)
        shell = spec[gun]["shell"]
        cells = []
        for reg in (shell, {"include": root_of(main_geo)}):
            chosen = painted_bones(main_geo, reg)
            cells.append(render.render(main_geo, main_tex, "right", 5,
                                       (lambda c: (lambda bone: (255, 40, 200) if bone in c else None))(chosen)))
        try:
            lod_geo, lod_tex = render.load(gun, lod=True)
            idx, faces = lod_paint(main_geo, lod_geo, shell)
            cells.append(render_faces(lod_geo, lod_tex, faces, {id(faces[i]) for i in idx})
                         .resize((cells[0].width, cells[0].height)))
        except (KeyError, FileNotFoundError, SystemExit):
            pass
        rows.append((gun, cells))
        print(gun)
    font = render.label_font(12)
    paths = []
    for k in range(0, len(rows), per):
        chunk = rows[k:k + per]
        cw = max(c.width for _, cs in chunk for c in cs)
        ch = max(c.height for _, cs in chunk for c in cs)
        img = Image.new("RGB", (90 + 3 * (cw + 8), len(chunk) * (ch + 8) + 8), (36, 36, 40))
        d = ImageDraw.Draw(img)
        for r, (gun, cs) in enumerate(chunk):
            y = 8 + r * (ch + 8)
            d.text((6, y + ch // 2 - 6), gun, font=font, fill=(220, 220, 220))
            for i, c in enumerate(cs):
                img.paste(c, (90 + i * (cw + 8), y), c)
        OUT.mkdir(parents=True, exist_ok=True)
        path = OUT / f"review_{k // per + 1}.png"
        img.save(path)
        paths.append(path)
    return paths


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("propose")
    rv = sub.add_parser("review")
    rv.add_argument("guns", nargs="*")
    s = sub.add_parser("sheet")
    s.add_argument("guns", nargs="*")
    s.add_argument("--all", action="store_true")
    args = ap.parse_args()
    spec = load_spec()
    if args.cmd == "propose":
        added = 0
        for gun in guns():
            if gun in spec:
                continue
            geo, _ = render.load(gun)
            spec[gun] = {"shell": propose_shell(geo), "note": "按骨骼名自动提的，没看过"}
            added += 1
        save_spec(spec)
        print(f"新提 {added} 把，共 {len(spec)} 把 → {SPEC}")
    elif args.cmd == "review":
        for path in review(args.guns or guns()):
            print(path)
    else:
        for gun in (guns() if args.all else args.guns):
            print(sheet(gun, spec))


if __name__ == "__main__":
    main()
