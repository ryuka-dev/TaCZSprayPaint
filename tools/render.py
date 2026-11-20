#!/usr/bin/env python3
"""把默认枪包里的一把枪按生成器还原的 3D 正交渲染成图：选上漆区域时看哪块是哪块、样张里看外观铺上去的样子。

用法：
    python tools/render.py ak47                     # 原枪，左右两侧 → build/render/ak47.png
    python tools/render.py ak47 --bones             # 每根有面的顶层骨骼一种颜色，图例写骨骼名
    python tools/render.py ak47 --paint body,stock  # 这几根骨骼（连子骨骼）涂成品红，看选中的是哪块

只在本机用：渲染含默认枪包的贴图（CC BY-NC-ND），图不提交、不分发。
模型的读法与 skingen.py 同一份（load_faces），看到的就是生成器认为的样子。
"""
import argparse
import colorsys
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import skingen  # noqa: E402

OUT = skingen.ROOT / "build/render"


def label_font(size):
    """A font for the labels on review sheets: a CJK system font when there is one, else PIL's own."""
    for name in ("msyh.ttc", "simhei.ttf", "NotoSansCJK-Regular.ttc", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()

# 视角：屏幕的横轴、竖轴与朝向观者的轴（模型空间，TaCZ 的枪身沿 z）
VIEWS = {
    "right": (np.array([0, 0, -1.0]), np.array([0, 1.0, 0]), np.array([1.0, 0, 0])),
    "left": (np.array([0, 0, 1.0]), np.array([0, 1.0, 0]), np.array([-1.0, 0, 0])),
    "top": (np.array([0, 0, -1.0]), np.array([1.0, 0, 0]), np.array([0, -1.0, 0])),
}


def load(gun, lod=False):
    base_display = skingen.loose_json(skingen.BASE_PACK / "display/guns" / f"{gun}_display.json")
    src = base_display["lod"] if lod else base_display
    geo = skingen.loose_json(skingen.resource_path(skingen.BASE_PACK, src["model"], "geo_models", ".json"))
    geo = geo["minecraft:geometry"][0]
    tex = Image.open(skingen.resource_path(skingen.BASE_PACK, src["texture"], "textures", ".png")).convert("RGBA")
    return geo, tex


# 渲染画默认状态：永远看不见的（skingen.HIDDEN）与装了配件才显示的（skingen.WITH_ATTACHMENT）都不画


def hidden_bones(geo):
    return skingen.hidden_bones(geo) | skingen.hidden_bones(geo, skingen.WITH_ATTACHMENT)


def groups_under(geo, faces, under=None):
    """每根骨骼归到 under（默认：全部可见面的最近公共祖先）的哪一根直接子骨骼下；under 自己的面归它自己。"""
    parent = {b["name"]: b.get("parent") for b in geo["bones"]}

    def chain(name):
        out = [name]
        while parent.get(out[-1]):
            out.append(parent[out[-1]])
        return out

    if under is None:
        common = None
        for f in faces:
            c = chain(f.bone)
            common = c if common is None else [n for n in common if n in c]
        under = common[0]
    out = {}
    for b in parent:
        c = chain(b)
        if under not in c:
            continue
        i = c.index(under)
        out[b] = c[i - 1] if i > 0 else under
    return under, out


def render(geo, tex, view, px=6, tint=None, texture_colour=None, ids=None, uv=None):
    """正交渲染。tint(bone) -> (r,g,b) 或 None：把那根骨骼的面画成纯色（仍带一点原图的明暗）。
    texture_colour：烘好的贴图（默认取 tex），样张用。ids：传一个 dict 进来，填上 {(x, y): 骨骼名}。
    uv：传一个 dict 进来，填上 tu、tv（每个屏幕像素取的贴图像素，没有为 -1）与 shade（明暗），会动的样张在页面里按它重画。
    返回 RGBA 图。"""
    faces = skingen.load_faces(geo)
    hide = hidden_bones(geo)
    faces = [f for f in faces if f.bone not in hide]
    right, down, toward = VIEWS[view]
    pts = np.array([v for f in faces for v in f.v])
    us, vs = pts @ right, pts @ down
    u0, v0 = us.min() - 1, vs.min() - 1
    W, H = int((us.max() - u0 + 1) * px) + 1, int((vs.max() - v0 + 1) * px) + 1
    img = np.zeros((H, W, 4), float)
    depth = np.full((H, W), -1e9)
    tu_map = np.full((H, W), -1, int)
    tv_map = np.full((H, W), -1, int)
    shade_map = np.zeros((H, W), float)
    # 烘好的贴图可能放大过（外观按 n 倍合成）：按交来的那张的尺寸取
    shown = tex if texture_colour is None else texture_colour
    tw, th = shown.size
    scale = tw / geo["description"]["texture_width"]
    tarr = np.asarray(shown, float)
    for f in faces:
        if float(f.n @ toward) <= 1e-6:
            continue   # 背向观者
        P = [((v @ right) - u0) * px for v in f.v], [((v @ down) - v0) * px for v in f.v]
        x0, x1 = int(max(0, min(P[0]))), int(min(W - 1, max(P[0]) + 1))
        y0, y1 = int(max(0, min(P[1]))), int(min(H - 1, max(P[1]) + 1))
        if x1 < x0 or y1 < y0:
            continue
        # 屏幕上的 (x, y) -> 面上的 (a, b)：v1 + a·ea + b·eb 投影到屏幕
        ea = np.array([f.ea @ right, f.ea @ down]) * px
        eb = np.array([f.eb @ right, f.eb @ down]) * px
        m = np.array([[ea[0], eb[0]], [ea[1], eb[1]]])
        if abs(np.linalg.det(m)) < 1e-9:
            continue
        inv = np.linalg.inv(m)
        o = np.array([((f.v[1] @ right) - u0) * px, ((f.v[1] @ down) - v0) * px])
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        d = np.stack([xs + 0.5 - o[0], ys + 0.5 - o[1]], axis=-1)
        ab = d @ inv.T
        a, b = ab[..., 0], ab[..., 1]
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        p3 = f.v[1][None, None, :] + a[..., None] * f.ea + b[..., None] * f.eb
        z = p3 @ toward
        tu = np.clip(((f.u1 + a * (f.u2 - f.u1)) * scale).astype(int), 0, tw - 1)
        tv = np.clip(((f.v1 + b * (f.v2 - f.v1)) * scale).astype(int), 0, th - 1)
        col = tarr[tv, tu]
        ok = inside & (col[..., 3] > 0) & (z > depth[y0:y1 + 1, x0:x1 + 1] + 1e-4)
        if not ok.any():
            continue
        c = col[..., :3].copy()
        if tint is not None and tint(f.bone) is not None:
            lum = c.mean(axis=-1, keepdims=True) / 255
            c = np.array(tint(f.bone), float)[None, None, :] * (0.55 + 0.45 * lum)
        shade = 0.78 + 0.22 * abs(float(f.n @ toward))
        sub_img = img[y0:y1 + 1, x0:x1 + 1]
        sub_img[ok, :3] = c[ok] * shade
        sub_img[ok, 3] = 255
        depth[y0:y1 + 1, x0:x1 + 1][ok] = z[ok]
        tu_map[y0:y1 + 1, x0:x1 + 1][ok] = tu[ok]
        tv_map[y0:y1 + 1, x0:x1 + 1][ok] = tv[ok]
        shade_map[y0:y1 + 1, x0:x1 + 1][ok] = shade
        if ids is not None:
            for yy, xx in zip(*np.nonzero(ok)):
                ids[(x0 + int(xx), y0 + int(yy))] = f.bone
    if uv is not None:
        uv.update(tu=tu_map, tv=tv_map, shade=shade_map)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def sheet(images, labels, bg=(36, 36, 40)):
    """几张图上下排在一起，左上写标签。"""
    font = label_font(12)
    w = max(i.width for i in images) + 20
    h = sum(i.height + 26 for i in images) + 10
    out = Image.new("RGB", (w, h), bg)
    d = ImageDraw.Draw(out)
    y = 8
    for im, label in zip(images, labels):
        d.text((10, y), label, font=font, fill=(220, 220, 220))
        out.paste(im, (10, y + 16), im)
        y += im.height + 26
    return out


def legend(colours, width):
    font = label_font(12)
    names = sorted(colours)
    rows = (len(names) + 3) // 4
    out = Image.new("RGB", (width, rows * 16 + 10), (36, 36, 40))
    d = ImageDraw.Draw(out)
    col_w = width // 4
    for i, n in enumerate(names):
        x, y = 10 + (i % 4) * col_w, 5 + (i // 4) * 16
        d.rectangle([x, y + 2, x + 10, y + 12], fill=tuple(int(c) for c in colours[n]))
        d.text((x + 14, y), n, font=font, fill=(220, 220, 220))
    return out


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("gun", help="默认枪包里的枪名（不带 tacz:），如 ak47")
    ap.add_argument("--bones", action="store_true", help="按顶层骨骼分色")
    ap.add_argument("--under", default="", help="按这根骨骼的子骨骼分色（往下钻一层）")
    ap.add_argument("--at", default="", help="右侧图上的点 x,y（图里的像素，可多个用 ; 分隔）：打印那里的骨骼与它的上级")
    ap.add_argument("--paint", default="", help="逗号分隔的骨骼名：涂成品红")
    ap.add_argument("--lod", action="store_true", help="远处模型")
    ap.add_argument("--px", type=int, default=10, help="一个模型单位几个像素")
    args = ap.parse_args()
    geo, tex = load(args.gun, args.lod)
    tint = None
    extra = None
    if args.bones or args.under:
        hide = hidden_bones(geo)
        visible = [f for f in skingen.load_faces(geo) if f.bone not in hide]
        root, groups = groups_under(geo, visible, args.under or None)
        print(f"按 {root} 的子骨骼分色")
        names = sorted({groups[f.bone] for f in visible if f.bone in groups})
        colours = {n: tuple(255 * c for c in colorsys.hsv_to_rgb(i / max(1, len(names)), 0.75, 1.0))
                   for i, n in enumerate(names)}
        tint = lambda bone: colours.get(groups.get(bone))  # noqa: E731
        extra = colours
    elif args.paint:
        chosen = skingen.subtree(geo, [b for b in args.paint.split(",") if b])
        tint = lambda bone: (255, 40, 200) if bone in chosen else None  # noqa: E731
    ids = {}
    views = [render(geo, tex, "right", args.px, tint, ids=ids), render(geo, tex, "left", args.px, tint)]
    if args.at:
        parent = {b["name"]: b.get("parent") for b in geo["bones"]}
        for pt in args.at.split(";"):
            x, y = (int(c) for c in pt.split(","))
            # 图里右侧那张贴在 (10, 24)：标签一行 16 加上边距 8
            bone = ids.get((x - 10, y - 24))
            chain = []
            while bone:
                chain.append(bone)
                bone = parent.get(bone)
            print(f"({x},{y})：{' ← '.join(chain) if chain else '空'}")
    out = sheet(views, ["右侧", "左侧"])
    if extra:
        lg = legend(extra, out.width)
        both = Image.new("RGB", (out.width, out.height + lg.height))
        both.paste(out, (0, 0))
        both.paste(lg, (0, out.height))
        out = both
    OUT.mkdir(parents=True, exist_ok=True)
    suffix = f"_under_{args.under}" if args.under else "_bones" if args.bones else "_paint" if args.paint else ""
    path = OUT / f"{args.gun}{'_lod' if args.lod else ''}{suffix}.png"
    out.save(path)
    print(path)


if __name__ == "__main__":
    main()
