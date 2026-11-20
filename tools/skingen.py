#!/usr/bin/env python3
"""Finish generator library: finds the texture regions to paint on each gun of TaCZ's default gun pack (by bone),
the painted texels' geometry, and the pattern tiles. pack.py drives it; `python tools/skingen.py --check` only checks
bones and paths.

Only our own data is written (painted texel lists, pattern tiles). The default gun pack's textures are CC BY-NC-ND and
are never redistributed: the client composites a finish onto the texture already installed with TaCZ.

Models are rebuilt in 3D exactly as TaCZ reads them (BedrockModel, BedrockPart, BedrockCubePerFace, BedrockPolygon,
FaceUVsItem), and pattern and wear noise are taken by 3D position, so:
- patterns run on across neighbouring faces, and a cell is the same size everywhere on the gun;
- two faces lying in one plane (the same colour on the original, fighting once painted) can be found and handled.

Which TaCZ release's default pack is read: the directory in the TACZ_PACK environment variable (pack.py sets it;
extract.py fills build/tacz-pack/<version>/ from the TaCZ jars in libs/).
"""
import argparse
import json
import math
import os
import re
import shutil
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

import skinalgo as algo
import patterns

ROOT = Path(__file__).resolve().parents[1]
TACZ_PACK = Path(os.environ.get("TACZ_PACK", ROOT / "build/tacz-pack/1.2.0"))
BASE_PACK = TACZ_PACK / "pack"                   # the default gun pack's assets/tacz
INTERNAL_ANIMATIONS = TACZ_PACK / "animations"   # TaCZ's own assets/tacz/animations, inside the mod jar
# build() lays out an experiment pack under these names; with --check nothing is written
NS = "skinlab"
OUT_PACK = ROOT / "build/skinlab/assets" / NS
SPEC = Path(__file__).with_name("skins.json")

PLANE_EPS = 0.01     # 两个面算同一平面：法线平行、相距不到这么多（模型单位，1/16 格）
# 两个上漆面叠着（法线平行、相距不到这么多）就按同一个"主面"画：离得近的两面在远处分不清前后会闪，
# 0.01–0.2 之间的漆面对在 37 把枪上有上万对（2026-10-12 扫描）
TWIN_EPS = 0.2
# 斜着穿插的上漆面（TaCZ 的枪由转了角度的方块拼成：P90 弯的握把、加特林的连杆）：一个像素离穿过这个面的另一个
# 漆面不到这么多个"这个面的贴图像素"，也按主面画。原枪那里是同一个颜色，闪了也看不出（2026-10-12 实机）。
# 只认互相穿过的（两面都有一部分在对方平面两侧）：在棱上相接、L 形 T 形接上的不算，不然每个面靠棱的一圈都会算进来
NEAR_TEXELS = 0.75
# 外露程度：离凸包表面多深算完全不外露；离第二个凸包平面多远以内算在外轮廓的棱角上（模型单位）
EXPOSE_DEPTH = 0.75
EXPOSE_CORNER = 1.5
# 漆面一个样本的目标大小：原贴图放大到一个样本不超过这么大，贴图最大 MAX_SIZE 见方
SAMPLE_SIZE = 0.25
MAX_SIZE = 1024
CROSS_TOL = 1e-3


def crosses(f, g):
    """g 穿过 f：g 的四个角在 f 平面两侧都有，f 的四个角在 g 平面两侧也都有。"""
    df = [float(f.n @ v) - f.d for v in g.v]
    dg = [float(g.n @ v) - g.d for v in f.v]
    return min(df) < -CROSS_TOL and max(df) > CROSS_TOL and min(dg) < -CROSS_TOL and max(dg) > CROSS_TOL
# 默认状态下看不到、永不上漆的骨骼（连子骨骼）：手、定位点、加长弹匣、子弹、枪口火焰、抛壳。TaCZ 靠动画与配件决定它们
# 显不显示；"整把漆"选的是枪的根骨骼，这些要扣掉，否则枪口火焰也会被染色。手只认 lefthand / righthand 本身
# （gun_and_righthand、mag_and_lefthand 是整把枪、弹匣的父骨骼）；mag_and_bullet 是弹匣的父骨骼，子弹只认 bullet 开头
HIDDEN = re.compile(r"^(lefthand|righthand)$|_pos$|^positioning|mag_extended|extended_mag|extend_magazine"
                    r"|additional_magazine|^bullet|muzzle_flash|^shell|^drum|^extdmb", re.I)
# 装了配件才显示的（TaCZ BedrockGunModel 的 attachmentAdapterNodeRender、scopeHiddenRender、handguardTacticalRender）：
# 转接口下的枪托、瞄具座、折叠瞄具、战术护木。上漆时照样上（装上配件时要跟着外观），但默认看不见
WITH_ATTACHMENT = re.compile(r"^(attachment_adapter|mount|sight_folded|handguard_tactical)$", re.I)
# 看得见、但不上漆的：夜光瞄点（TaCZ 按名字里的 illuminated 全亮画），和贴花一样保留原色；装着的火箭弹是弹药，
# 打出去就没了（rpg7 的 rocket）
UNPAINTED = re.compile(r"illuminated|^rocket", re.I)
INSIDE_EPS = 1e-3    # 只碰到边不算叠着


# ---------- 读枪包的 json ----------

def loose_json(path):
    """读默认枪包的 json：TaCZ 用宽松的 GSON 读，有的文件带 // 与 /* */ 注释、末尾多逗号（ak47 的显示）。"""
    text = Path(path).read_text(encoding="utf-8-sig")
    out, i, n, in_str = [], 0, len(text), False
    while i < n:
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1])
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            out.append(c)
        elif text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            i = text.index("*/", i) + 2
            continue
        else:
            out.append(c)
        i += 1
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", "".join(out)))


# ---------- 模型：照 TaCZ 的读法还原成 3D ----------

def rot(axis, rad):
    c, s = math.cos(rad), math.sin(rad)
    m = np.eye(4)
    i, j = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
    m[i, i], m[i, j], m[j, i], m[j, j] = c, -s, s, c
    return m


def part_matrix(pos, rotation):
    """BedrockPart.translateAndRotateAndScale：先平移到旋转点，再依次绕 Z、Y、X 转（弧度，不改符号）。"""
    m = np.eye(4)
    m[:3, 3] = pos
    if rotation:
        rx, ry, rz = (math.radians(a) for a in rotation)
        m = m @ rot("z", rz) @ rot("y", ry) @ rot("x", rx)
    return m


# BedrockCubePerFace 的八个顶点与六个面；面的 UV 取 JSON 里的哪一个由 FaceUVsItem.getFace 决定（东西、上下对调）
CORNERS = {1: (0, 0, 0), 2: (1, 0, 0), 3: (1, 1, 0), 4: (0, 1, 0), 5: (0, 0, 1), 6: (1, 0, 1), 7: (1, 1, 1), 8: (0, 1, 1)}
POLYGONS = [((6, 5, 1, 2), "up"), ((3, 4, 8, 7), "down"), ((1, 5, 8, 4), "east"),
            ((2, 1, 4, 3), "north"), ((6, 2, 3, 7), "west"), ((5, 6, 7, 8), "south")]


class Face:
    """模型上的一个面：四个顶点（BedrockPolygon 的顺序）与贴图上的 UV。顶点 1 是 (u1, v1)，顶点 0 方向 u 到 u2，顶点 2 方向 v 到 v2。"""

    def __init__(self, bone, verts, u1, v1, u2, v2):
        self.bone = bone
        self.v = verts
        self.u1, self.v1, self.u2, self.v2 = u1, v1, u2, v2
        self.ea = verts[0] - verts[1]
        self.eb = verts[2] - verts[1]
        n = np.cross(self.ea, self.eb)
        self.n = n / (np.linalg.norm(n) or 1)
        self.d = float(self.n @ verts[1])
        self.la, self.lb = float(np.linalg.norm(self.ea)), float(np.linalg.norm(self.eb))
        self.painted = False

    def point(self, a, b):
        return self.v[1] + a * self.ea + b * self.eb

    def params(self, p):
        """p 在这个面上的 (a, b)，最小二乘。"""
        m = np.stack([self.ea, self.eb], axis=1)
        ab, *_ = np.linalg.lstsq(m, p - self.v[1], rcond=None)
        return ab

    def uv_rect(self):
        return min(self.u1, self.u2), min(self.v1, self.v2), max(self.u1, self.u2), max(self.v1, self.v2)


def depth_nudge(cube):
    """给模型里第 cube 个方块额外加的 inflate（client.DepthNudge）：0 到 0.0009 像素分 16 档，
    同一平面上叠着的面有了固定的前后，不再闪。"""
    return (((cube * 0x9E3779B1) & 0xFFFFFFFF) >> 28) * 6.0e-5


def load_faces(geo, nudge=False):
    """全部方块的全部非空面，位置在 TaCZ 的模型空间（单位 1/16 格），带所属骨骼。nudge：照游戏里那样加上
    depth_nudge（模拟深度用；算区域不用，差不到 0.001）。"""
    bones = {b["name"]: b for b in geo["bones"]}
    world = {}

    def bone_matrix(name):
        if name in world:
            return world[name]
        b = bones[name]
        p = b.get("pivot", [0, 0, 0])
        parent = b.get("parent")
        if parent:
            pp = bones[parent].get("pivot", [0, 0, 0])
            pos = (p[0] - pp[0], pp[1] - p[1], p[2] - pp[2])          # BedrockModel.convertPivot
            m = bone_matrix(parent) @ part_matrix(pos, b.get("rotation"))
        else:
            m = part_matrix((p[0], 24 - p[1], p[2]), b.get("rotation"))
        world[name] = m
        return m

    faces = []
    index = 0
    for b in geo["bones"]:
        bp = b.get("pivot", [0, 0, 0])
        for cube in b.get("cubes", []):
            extra = depth_nudge(index) if nudge else 0.0
            index += 1
            uv = cube.get("uv")
            if not isinstance(uv, dict):
                raise SystemExit(f"{b['name']} 用的是箱式 UV，生成器还不支持")
            o, s, inflate = cube["origin"], cube["size"], cube.get("inflate", 0) + extra
            if cube.get("rotation"):
                cp = cube["pivot"]
                m = bone_matrix(b["name"]) @ part_matrix((cp[0] - bp[0], bp[1] - cp[1], cp[2] - bp[2]), cube["rotation"])
                start = (o[0] - cp[0], cp[1] - o[1] - s[1], o[2] - cp[2])   # convertOrigin(cube)
            else:
                m = bone_matrix(b["name"])
                start = (o[0] - bp[0], bp[1] - o[1] - s[1], o[2] - bp[2])   # convertOrigin(bone, cube)
            lo = np.array(start, float) - inflate
            hi = np.array(start, float) + np.array(s, float) + inflate
            corner = {k: (m @ np.append(lo + (hi - lo) * np.array(c), 1.0))[:3] for k, c in CORNERS.items()}
            for order, key in POLYGONS:
                fuv = uv.get(key)
                if not fuv or fuv["uv_size"][0] == 0 or fuv["uv_size"][1] == 0:
                    continue
                (u, v), (w, h) = fuv["uv"], fuv["uv_size"]
                face = Face(b["name"], [corner[k] for k in order], u, v, u + w, v + h)
                if face.la > 1e-6 and face.lb > 1e-6:
                    faces.append(face)
    return faces


def subtree(geo, roots):
    children = {}
    for b in geo["bones"]:
        children.setdefault(b.get("parent"), []).append(b["name"])
    missing = set(roots) - {b["name"] for b in geo["bones"]}
    if missing:
        raise SystemExit(f"模型里没有这些骨骼：{sorted(missing)}")
    wanted, stack = set(), list(roots)
    while stack:
        name = stack.pop()
        if name not in wanted:
            wanted.add(name)
            stack.extend(children.get(name, []))
    return wanted


def overlapping(face, others, eps=PLANE_EPS):
    """和 face 在同一平面上（相距不到 eps）的其他面（法线同向或反向）。"""
    return [g for g in others if g is not face and abs(float(face.n @ g.n)) > 0.999
            and abs(float(face.n @ g.v[1]) - face.d) < eps]


def snap_to(owner, p, scale, w, h):
    """p 落在主面 owner 的哪个贴图像素上，返回那个像素中心在主面上的 3D 位置。叠着的几个面都借它来取图案、
    划痕与脏污，所以同一处画得一模一样（各面自己的像素网格在 3D 上是错开的，按自己的中心取会差一格）。"""
    a, b = (min(1.0, max(0.0, c)) for c in owner.params(p))
    u = owner.u1 + a * (owner.u2 - owner.u1)
    v = owner.v1 + b * (owner.v2 - owner.v1)
    x0, y0, x1, y1 = owner.uv_rect()
    x = min(max(int(math.floor(u * scale)), int(x0 * scale)), int(math.ceil(x1 * scale)) - 1)
    y = min(max(int(math.floor(v * scale)), int(y0 * scale)), int(math.ceil(y1 * scale)) - 1)
    ac = ((x + 0.5) / scale - owner.u1) / (owner.u2 - owner.u1)
    bc = ((y + 0.5) / scale - owner.v1) / (owner.v2 - owner.v1)
    return owner.point(ac, bc)


def inside(g, p):
    a, b = g.params(p)
    return INSIDE_EPS < a < 1 - INSIDE_EPS and INSIDE_EPS < b < 1 - INSIDE_EPS


# ---------- 图案：按规则生成，不用别人的图 ----------

def pattern_tile(spec, seed):
    """一块能无缝平铺的方图（patterns.py，按 kind 选画法；没写 kind 的是第 0 步的樱叶）。随 mod 下发（pattern.png）。"""
    return patterns.tile(spec, seed)


# ---------- 上漆的像素：模型几何 -> 随 mod 下发的数据 ----------

def hidden_bones(geo, rule=HIDDEN):
    roots = [b["name"] for b in geo["bones"] if rule.search(b["name"])]
    return subtree(geo, roots) if roots else set()


def exposure_of(points, vertices):
    """外露程度 0..1：离整把枪的外轮廓（可见顶点的凸包）越近越外露，同时离两个凸包平面都近（外轮廓的棱角、
    凸出的部分）更外露；凹进去的地方不外露。按整把枪算，不按每个小方块的边（那样磨损撒在所有小方块的边上）。"""
    from scipy.spatial import ConvexHull
    eq = ConvexHull(vertices).equations
    # 凸包把平面切成三角形：同一平面只留一个
    eq = np.unique(np.round(eq, 4), axis=0)
    vals = points @ eq[:, :3].T + eq[:, 3]          # 凸包内为负：离那个平面多远
    vals.sort(axis=1)
    d1, d2 = -vals[:, -1], -vals[:, -2]
    inner = np.clip(1 - d1 / EXPOSE_DEPTH, 0, 1)
    corner = np.clip(1 - d2 / EXPOSE_CORNER, 0, 1)
    return inner * (0.35 + 0.65 * corner)


def animated_bones(gun):
    """这把枪的动画里会动的骨骼（自己的动画文件、display 指的默认动画、TaCZ 自带的步枪或手枪默认动画）。
    GunDB 运行时并进来的排除卡壳动画不在这里，它动的是枪栓、套筒这些本来就会动的骨骼。"""
    disp = loose_json(BASE_PACK / "display/guns" / f"{gun}_display.json")
    paths = [resource_path(BASE_PACK, rl, "animations", ".animation.json")
             for rl in (disp.get("animation"), disp.get("default_animation")) if rl]
    if disp.get("use_default_animation"):
        paths.append(INTERNAL_ANIMATIONS / f"{disp['use_default_animation']}_default.animation.json")
    names = set()
    for path in paths:
        if not path.exists():
            raise SystemExit(f"{gun} 的动画文件不在：{path}")
        for anim in loose_json(path).get("animations", {}).values():
            names.update(anim.get("bones", {}).keys())
    return names


def paint_region(base, geo, bones, exclude=(), only=None, moving=None):
    """找出 bones 的面上要上漆的像素。返回 {像素: 记录}、一个贴图像素在模型上有多大、报告。记录：face（所在的面）、
    owner（叠着时的主面，否则 None）、shared、hidden（被朝同一边的主面完全盖住：透明）、p（中心的位置，叠着的借
    主面那个像素）、e（外露程度 0..1）。pack() 把它整理成下发的面表与像素表。

    不上漆的：
    - 贴花：面在贴图上大半透明（刻字、标记），字照原样留着
    - 同一块 UV 也被不上漆的面用着（上了漆会连那个面一起染）
    - 这一处在同一平面上被不上漆的面叠着（会互相抢着显示）

    moving：动画里会动的骨骼（animated_bones）。给了的话，只有和主面跟着同一根会动的骨骼走的才透明。
    """
    faces = load_faces(geo)
    painted_bones = (subtree(geo, bones) - hidden_bones(geo) - hidden_bones(geo, UNPAINTED)
                     - (subtree(geo, exclude) if exclude else set()))
    if only is not None:
        # 远处模型：不按骨骼，按 regions.lod_paint 映射出来的面（load_faces 的序号）
        for i, f in enumerate(faces):
            f.chosen = i in only
    else:
        for f in faces:
            f.chosen = f.bone in painted_bones
    w, h = base.size
    scale = w / geo["description"]["texture_width"]
    alpha = np.array(base.split()[3]) > 0
    for f in faces:
        x0, y0, x1, y1 = (int(round(c * scale)) for c in f.uv_rect())
        block = alpha[max(0, y0):max(0, y1), max(0, x0):max(0, x1)]
        f.opaque = float(block.mean()) if block.size else 0.0
        # 贴花（刻字、标记）：大半透明，只画几个字母。不上漆，字照原样留着
        f.painted = f.chosen and f.opaque >= 0.5
    mine = [f for f in faces if f.painted]
    # 不上漆、又看得见的面才会和漆面争：平时不显示的（加长弹匣、装配件才有的部件）不算。原先把它们也算进来，
    # 普通弹匣与加长弹匣叠着、共用贴图的地方就让了出去，弹匣只上了一部分漆（2026-10-12 实机，M4 524 个像素）
    unseen = hidden_bones(geo) | hidden_bones(geo, WITH_ATTACHMENT)
    others = [f for f in faces if not f.painted and f.bone not in unseen]
    if not mine:
        raise SystemExit("选中的骨骼没有面")
    seen = [f for f in faces if f.bone not in unseen]
    parent = {b["name"]: b.get("parent") for b in geo["bones"]}

    def rig(name):
        """这根骨骼跟着哪根会动的骨骼走（自己或最近的会动的祖先；都不动是 None）。"""
        while name is not None and name not in moving:
            name = parent.get(name)
        return name

    bp = base.load()

    def opaque_at(g, p):
        a, b = g.params(p)
        u, v = g.u1 + a * (g.u2 - g.u1), g.v1 + b * (g.v2 - g.v1)
        x, y = min(w - 1, max(0, int(u * scale))), min(h - 1, max(0, int(v * scale)))
        return bool(alpha[y, x])

    def covers(g, p):
        """不上漆的面 g 在 p 处真的画着东西（贴花透明的地方不算）。"""
        if not inside(g, p):
            return False
        a, b = g.params(p)
        u, v = g.u1 + a * (g.u2 - g.u1), g.v1 + b * (g.v2 - g.v1)
        x, y = min(w - 1, max(0, int(u * scale))), min(h - 1, max(0, int(v * scale)))
        return bool(alpha[y, x])

    # 每个贴图像素被模型里几个面用（不论上不上漆、平时画不画）：透明是写在贴图上的，几个面共用的像素一透明，
    # 用它的每个面都是洞。原先只在两个面都走到记录那一步时才拦，一个面因为贴着不上漆的东西被跳过就拦不住
    uv_users = np.zeros((h, w), np.int32)
    for g in faces:
        x0, y0, x1, y1 = g.uv_rect()
        uv_users[int(y0 * scale):int(math.ceil(y1 * scale)), int(x0 * scale):int(math.ceil(x1 * scale))] += 1

    # 不上漆的面占着的贴图像素
    reserved = np.zeros((h, w), bool)
    for g in others:
        if g.opaque < 0.5 and g.chosen:
            continue   # 上漆骨骼里的贴花：它的 UV 只有字母，不和漆面共用
        x0, y0, x1, y1 = g.uv_rect()
        reserved[int(y0 * scale):int(math.ceil(y1 * scale)), int(x0 * scale):int(math.ceil(x1 * scale))] = True

    # 每个上漆的像素：它在模型上的位置、所在的面
    texels, sizes = {}, []
    report = {"faces": len(mine), "decals": sum(1 for f in faces if f.chosen and not f.painted),
              "shared_uv": 0, "z_fight": 0, "z_fight_faces": set(), "shared": 0, "hidden": 0}
    order = {id(f): i for i, f in enumerate(faces)}
    # 点到每个上漆面（矩形）的最短距离用的数组
    m_v1 = np.array([g.v[1] for g in mine])
    m_ea = np.array([g.ea for g in mine])
    m_eb = np.array([g.eb for g in mine])
    m_la2 = np.maximum(1e-12, (m_ea * m_ea).sum(1))
    m_lb2 = np.maximum(1e-12, (m_eb * m_eb).sum(1))
    m_c = np.array([np.mean(g.v, axis=0) for g in mine])
    m_r = np.array([max(np.linalg.norm(v - c) for v in g.v) for g, c in zip(mine, m_c)])
    for fi, f in enumerate(mine):
        coplanar = overlapping(f, others)
        twins = overlapping(f, mine, TWIN_EPS)   # 叠着的上漆面：那一块几面必须画得一模一样
        f_texel = max(f.la / max(1e-6, abs(f.u2 - f.u1) * scale), f.lb / max(1e-6, abs(f.v2 - f.v1) * scale))
        near_eps = NEAR_TEXELS * f_texel
        f_c = np.mean(f.v, axis=0)
        f_r = max(np.linalg.norm(v - f_c) for v in f.v)
        cand = np.nonzero(np.linalg.norm(m_c - f_c, axis=1) <= m_r + f_r + near_eps)[0]
        cand = np.array([i for i in cand if i != fi and abs(float(f.n @ mine[i].n)) < 0.999 and crosses(f, mine[i])],
                        dtype=int)
        x0, y0, x1, y1 = f.uv_rect()
        xs = range(int(x0 * scale), int(math.ceil(x1 * scale)))
        ys = range(int(y0 * scale), int(math.ceil(y1 * scale)))
        near_of = {}
        if len(cand) and len(xs) and len(ys):
            # 这个面所有像素中心到每个候选漆面（矩形）的最短距离，一次算完
            gx, gy = np.meshgrid(np.array(xs), np.array(ys))
            ga = ((gx.ravel() + 0.5) / scale - f.u1) / (f.u2 - f.u1)
            gb = ((gy.ravel() + 0.5) / scale - f.v1) / (f.v2 - f.v1)
            P = f.v[1] + ga[:, None] * f.ea + gb[:, None] * f.eb
            d = P[:, None, :] - m_v1[cand][None, :, :]
            ua = np.clip((d * m_ea[cand][None]).sum(2) / m_la2[cand][None], 0, 1)
            ub = np.clip((d * m_eb[cand][None]).sum(2) / m_lb2[cand][None], 0, 1)
            Q = m_v1[cand][None] + ua[..., None] * m_ea[cand][None] + ub[..., None] * m_eb[cand][None]
            close = ((P[:, None, :] - Q) ** 2).sum(2) < near_eps * near_eps
            for k in np.nonzero(close.any(1))[0]:
                near_of[(int(gx.ravel()[k]), int(gy.ravel()[k]))] = [mine[i] for i in cand[close[k]]]
        for y in ys:
            for x in xs:
                if not (0 <= x < w and 0 <= y < h) or bp[x, y][3] == 0:
                    continue
                if reserved[y, x]:
                    report["shared_uv"] += 1
                    continue
                a = ((x + 0.5) / scale - f.u1) / (f.u2 - f.u1)
                b = ((y + 0.5) / scale - f.v1) / (f.v2 - f.v1)
                p = f.point(a, b)
                # 像素的任何一角被贴着的不上漆面盖住就不上漆，不只看中心：转了 45° 的小零件（P90 的螺丝）边上那圈像素
                # 中心在零件外、一角在零件下，上了漆就是漆与零件在那一角抢着显示（2026-10-12 实机）
                du, dv = 0.45 / scale / abs(f.u2 - f.u1), 0.45 / scale / abs(f.v2 - f.v1)   # 到像素四角（稍往里收）
                if coplanar and any(covers(g, q) for g in coplanar for q in
                                    [p] + [f.point(a + sa * du, b + sb * dv) for sa in (-1, 1) for sb in (-1, 1)]):
                    report["z_fight"] += 1
                    report["z_fight_faces"].add(f.bone)
                    continue
                # 像素的任何一角落在叠着的漆面上就算叠着：只看中心时，中心在对方面外、一角在对方面上的像素照自己的网格取色，
                # 那一角就是两面颜色不同在抢（2026-10-12：P90 的 Body2、grip2 同一根骨骼里叠着的面，深度差 0–0.002）
                corners = [p] + [f.point(a + sa * du, b + sb * dv) for sa in (-1, 1) for sb in (-1, 1)]
                covering = [g for g in twins if any(inside(g, c) for c in corners)]
                # 任何角度的漆面离这个像素不到 near_eps（上面一次算好的）
                covering += [g for g in near_of.get((x, y), ()) if g not in covering]
                shared = bool(covering)
                owner, hidden = None, False
                if shared:
                    report["shared"] += 1
                    # 叠着的几面里序号最小的是主面：都借它的位置与法线（斜着装的部件法线接近 45°，各取各的投影轴会不同）。
                    # 平时画出来的面优先当主面，平时不画的（装配件才有的枪托）只在叠着的全是这种面时才当
                    owner = min([f] + covering, key=lambda g: (g.bone in unseen, order[id(g)]))
                    if owner is not f:
                        p = snap_to(owner, p, scale, w, h)
                        # 被朝同一边的主面完全盖住（四角都在主面上、主面那里不透明）：这一点透明，只剩主面一层。
                        # 两面都得是平时画出来的：主面平时不画，这一点透明就是个洞；这一面平时不画，装上配件、
                        # 默认部件收起来时它那里也是洞（2026-10-12 实机：AK 枪托检视时的洞，主面是 oem_stock_heavy）
                        # 还得跟着同一根会动的骨骼走：静止时被弹匣盖住的握把，检视动画把弹匣拔出来就露出洞
                        # （2026-10-12 实机：沙漠之鹰整把 252 个、AWM 枪栓）
                        hidden = (f.bone not in unseen and owner.bone not in unseen
                                  and (moving is None or rig(f.bone) == rig(owner.bone))
                                  and uv_users[y, x] == 1
                                  # 还得真在同一平面上：借颜色容许相距 TWIN_EPS，透明不行——主面在前面 0.125 时，斜着看
                                  # 视线从主面边上穿过去，看到的就是这块透明的（2026-10-12 批量查洞：M700 机身底面）
                                  and abs(float(owner.n @ f.v[1]) - owner.d) < PLANE_EPS
                                  and float(f.n @ owner.n) > 0.999 and all(inside(owner, c) for c in corners)
                                  and all(opaque_at(owner, c) for c in corners))
                rec = {"face": f, "owner": owner, "shared": shared, "hidden": hidden, "p": p}
                if (x, y) in texels:
                    # 同一个贴图像素被几个上漆面共用：画在哪个面上都得看得见，不能透明
                    old = texels[(x, y)]
                    report["hidden"] -= old["hidden"]
                    old["hidden"] = rec["hidden"] = False
                    # 颜色只能按一个面的位置算：叠着别的面的那个优先（它得和叠着的面一样），不叠的那个换成哪种花纹都看不出。
                    # 原先后来的覆盖先来的：CZ75 弹匣侧面与握把同一平面叠着，它的贴图又被别处的弹匣模型（mag2）共用，
                    # 颜色按 mag2 的位置算，和握把对不上（2026-10-12 批量闪烁报告，整把 38‰）
                    # 平时画出来的面更优先：装配件才显示的枪托和默认枪托共用贴图，颜色按看不见的那个的位置算，默认枪托上
                    # 就是别处的颜色（2026-10-12 实机：MP5 机匣后面一条紫色，在一片橙黄里）
                    rank_old = (old["face"].bone not in unseen, old["shared"])
                    if rank_old > (f.bone not in unseen, shared):
                        continue
                texels[(x, y)] = rec
                report["hidden"] += rec["hidden"]
                sizes.append(max(f.la / max(1e-6, abs(f.u2 - f.u1) * scale), f.lb / max(1e-6, abs(f.v2 - f.v1) * scale)))
    report["painted"] = len(texels)
    texel_size = sorted(sizes)[len(sizes) // 2] if sizes else 0.1
    if texels:
        keys = list(texels)
        e = exposure_of(np.array([texels[k]["p"] for k in keys]), np.array([v for g in seen for v in g.v]))
        for k, v in zip(keys, e):
            texels[k]["e"] = float(v)
    return texels, texel_size, report


def f32(v):
    return struct.unpack(">f", struct.pack(">f", float(v)))[0]


def pack(texels, size, texel_size, scale):
    """下发前的样子（第 3 版）：面表（只含用到的面：所在的面与主面）、像素表、放大倍数 n。数都按 float32 取整，
    生成器的样张也用这份，与客户端的输入一模一样。size 是原贴图的宽高，scale 是原贴图像素 ÷ 模型 UV 单位。"""
    w, h = size
    n = 1
    while texel_size / n > SAMPLE_SIZE * 1.04 and max(w, h) * n * 2 <= MAX_SIZE:
        n *= 2
    index, faces = {}, []

    def face_id(f):
        if id(f) not in index:
            index[id(f)] = len(faces)
            faces.append({"v1": [f32(c) for c in f.v[1]], "ea": [f32(c) for c in f.ea], "eb": [f32(c) for c in f.eb],
                          "u1": f32(f.u1 * scale), "v1t": f32(f.v1 * scale), "u2": f32(f.u2 * scale), "v2t": f32(f.v2 * scale),
                          "axis": int(np.argmax(np.abs(f.n)))})
        return index[id(f)]

    out = []
    for (x, y), t in sorted(texels.items(), key=lambda kv: (kv[0][1], kv[0][0])):
        out.append({"x": x, "y": y, "face": face_id(t["face"]), "owner": face_id(t["owner"]) if t["owner"] else -1,
                    "shared": bool(t["shared"]), "hidden": bool(t["hidden"]), "e": int(round(255 * t["e"]))})
    if len(faces) > 65535:
        raise SystemExit("面太多，第 3 版的格式存不下")
    return {"faces": faces, "texels": out, "n": n, "texel_size": f32(texel_size), "size": (w, h)}


MAGIC = b"DSK1"


def texels_bytes(packed):
    """二进制格式（大端，client.SkinTexels 照这个读），第 3 版：
    "DSK1"、u16 版本 3、u16 原贴图宽、u16 原贴图高、u8 放大倍数 n、u8 0、f32 一个贴图像素在模型上多大、u16 面数、u32 像素数；
    之后每个面 53 字节：f32×3 角 v1、f32×3 边 ea、f32×3 边 eb、f32 u1 v1 u2 v2（原贴图的像素）、u8 投影轴；
    之后每个像素 10 字节：u16 x、u16 y、u16 面、u16 主面（65535 没有）、u8 标记（1 叠着、2 透明）、u8 外露程度。
    样本的位置由客户端按面算，不再每个像素各存一个位置。"""
    w, h = packed["size"]
    out = bytearray(MAGIC)
    out += struct.pack(">HHHBBfHI", 3, w, h, packed["n"], 0, packed["texel_size"], len(packed["faces"]),
                       len(packed["texels"]))
    for f in packed["faces"]:
        out += struct.pack(">13fB", *f["v1"], *f["ea"], *f["eb"], f["u1"], f["v1t"], f["u2"], f["v2t"], f["axis"])
    for t in packed["texels"]:
        out += struct.pack(">HHHHBB", t["x"], t["y"], t["face"], 65535 if t["owner"] < 0 else t["owner"],
                           (1 if t["shared"] else 0) | (2 if t["hidden"] else 0), t["e"])
    return bytes(out)


def bake(base, model, tile, skin_hash, item):
    """按一把枪的四个数合成整张贴图（只用于本机样张；正式的由客户端合成）。model 是 (pack() 的结果, 图案一格多大)；
    返回放大 n 倍的贴图：原贴图按最近邻放大，上漆的样本换成合成的颜色。"""
    packed, pixel = model
    n = packed["n"]
    samples = algo.expand(packed["texels"], packed["faces"], n)
    bp = base.load()
    tp = tile.load()
    painted = algo.compose(lambda x, y: bp[x, y], samples, packed["texel_size"] / n, lambda i, j: tp[i, j],
                           tile.size[0], pixel, skin_hash, item["f"], item.get("pattern", 0), item.get("wear", 0),
                           item.get("anim", ()), item.get("t", 0.0), item.get("projection", "box"))
    out = base.resize((base.width * n, base.height * n), Image.NEAREST)
    op = out.load()
    for xy, c in painted.items():
        op[xy] = c
    return out


# ---------- 外观 json ----------

def display_for(base_display, texture, lod_texture):
    """照抄原枪的外观，只换贴图：模型、动画、声音、枪口火焰、抛壳一律不动（换了就听不出、认不出是哪把枪）。"""
    d = json.loads(json.dumps(base_display))
    d["texture"] = texture
    if lod_texture and "lod" in d:
        d["lod"]["texture"] = lod_texture
    return d


def resource_path(pack, rl, folder, ext):
    ns, path = rl.split(":")
    if ns != "tacz":
        raise SystemExit(f"只支持默认枪包的枪：{rl}")
    return pack / folder / f"{path}{ext}"


def build(skin, check):
    gun = skin["gun"]
    base_display = loose_json(BASE_PACK / "display/guns" / f"{gun}_display.json")
    skin_hash = algo.str_hash(skin["id"])
    tile = pattern_tile(skin["pattern"], skin_hash)
    files = {}

    def one(label, model_rl, texture_rl, bones, pixel):
        geo = loose_json(resource_path(BASE_PACK, model_rl, "geo_models", ".json"))
        geo = geo["minecraft:geometry"][0]
        base = Image.open(resource_path(BASE_PACK, texture_rl, "textures", ".png")).convert("RGBA")
        texels, texel_size, r = paint_region(base, geo, bones)
        print(f"{skin['id']} {label}：上漆 {r['faces']} 个面 {r['painted']} 像素（其中 {r['shared']} 个与别的上漆面叠着，画成一样，"
              f"{r['hidden']} 个被主面盖住而透明），贴花 {r['decals']} 个面不上漆；"
              f"UV 与不上漆的面共用而跳过 {r['shared_uv']}；同一平面被叠着而跳过 {r['z_fight']}"
              + (f"（{', '.join(sorted(r['z_fight_faces']))}）" if r["z_fight_faces"] else ""))
        scale = base.size[0] / geo["description"]["texture_width"]
        return base, (pack(texels, base.size, texel_size, scale), pixel)

    main_base, main = one("主模型", base_display["model"], base_display["texture"], skin["bones"], skin["pixel"])
    lod = None
    if "lod" in base_display and skin.get("lod_bones"):
        lod_base, lod = one("远处模型", base_display["lod"]["model"], base_display["lod"]["texture"],
                            skin["lod_bones"], skin.get("lod_pixel", skin["pixel"]))

    # 客户端合成要的数据（只有我们自己的东西）：每个模型上漆的像素、图案、一格多大
    data = OUT_PACK / "skins" / skin["id"]
    files[data / "main.bin"] = texels_bytes(main[0])
    if lod:
        files[data / "lod.bin"] = texels_bytes(lod[0])
    files[data / "pattern.png"] = tile
    files[data / "skin.json"] = {"v": 1, "pixel": skin["pixel"], "lod_pixel": skin.get("lod_pixel", skin["pixel"])}
    # 抽出来的枪用的显示：贴图仍是原枪的，客户端按枪上的数换
    files[OUT_PACK / "display/guns" / f"{skin['id']}.json"] = display_for(base_display, base_display["texture"],
                                                                         base_display.get("lod", {}).get("texture"))

    # 样张：按指定的四个数在这里合成整张贴图，与客户端的结果应当逐像素相同（只在本机，含原贴图，不分发）
    for tag, item in skin.get("bake", {}).items():
        item = item if isinstance(item, dict) else {"f": item}
        name = f"{skin['id']}_{tag}"
        tex_rl = f"{NS}:gun/skin/{gun}/{name}"
        lod_rl = f"{NS}:gun/skin/{gun}/{name}_lod" if lod else None
        files[OUT_PACK / "textures/gun/skin" / gun / f"{name}.png"] = bake(main_base, main, tile, skin_hash, item)
        files[OUT_PACK / "display/guns" / f"{name}.json"] = display_for(base_display, tex_rl, lod_rl)
        if lod:
            files[OUT_PACK / "textures/gun/skin" / gun / f"{name}_lod.png"] = bake(lod_base, lod, tile, skin_hash, item)

    written = []
    for path, content in files.items():
        written.append(path)
        if check:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, Image.Image):
            content.save(path, optimize=True)
        elif isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return written


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="Check the finish spec's bones and paths against a TaCZ default pack")
    ap.add_argument("--check", action="store_true", help="check bones and paths, report overlapping faces")
    args = ap.parse_args()
    if not args.check:
        ap.error("the finishes are generated by pack.py; this script only checks (--check)")
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    for skin in spec["skins"]:
        build(skin, True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
