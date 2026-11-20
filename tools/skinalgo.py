# -*- coding: utf-8 -*-
"""枪械外观的合成算法：参照实现。客户端（本 mod 的 client.SkinAlgo）逐行照这里写，结果要逐像素相同。

改这里的任何一处，同一次提交里改 Java 那份，并重新生成它的测试样例（skingen.py --fixture），否则单测会失败。
随机数全部来自整数哈希（h32），不用 Python 的 random 或 blake2b：Java 那边要能逐位复现。

输入（生成器从模型算好、随 mod 下发的，只有我们自己的数据）：
- 面表：每个面的一个角 v1、两条边 ea、eb（模型单位，float32）、在原贴图上的范围 u1、v1、u2、v2（原贴图的像素）、
  投影轴 axis（法线最大的那根）
- 上漆的像素：原贴图坐标 x、y，属于哪个面 face，叠着时的主面 owner（-1 没有），是否叠着 shared、是否被主面完全
  盖住而透明 hidden，外露程度 e（0..255）
- 放大倍数 n
- 图案：一小块方图（tile）与一格多大（pixel，模型单位）
- 贴图像素在模型上有多大（texel_size；样本是它的 1/n，定划痕的宽度）
玩家本地的原贴图只在客户端读，不分发。

每个上漆像素按它的面拆成 n×n 个样本（expand），样本在模型上的位置由面的几何算出来；叠着的样本借主面细网格上
那一格的位置与法线（主面上同一处的样本取到一模一样的值），所以几个叠着的面画得一样，不会抢着显示。
"""
import math

M = 0xFFFFFFFF
TAG_PATTERN, TAG_WEAR, TAG_GRIME, TAG_CELL, TAG_OFFSET = 1, 2, 3, 4, 5
CELL = 1.0


def fmix(h):
    h &= M
    h ^= h >> 16
    h = (h * 0x85EBCA6B) & M
    h ^= h >> 13
    h = (h * 0xC2B2AE35) & M
    h ^= h >> 16
    return h


def h32(*xs):
    h = 0x811C9DC5
    for x in xs:
        h = fmix(h ^ fmix((x + 0x9E3779B9) & M))
    return h


def unit(h):
    return (h & M) / 4294967296.0


def str_hash(s):
    """FNV-1a 32 over UTF-8：外观编号 -> 整数。"""
    h = 0x811C9DC5
    for b in s.encode('utf-8'):
        h ^= b
        h = (h * 0x01000193) & M
    return h


def noise(seed, p, cell):
    """格点随机值三线性插值，0..1。"""
    g = [p[0] / cell, p[1] / cell, p[2] / cell]
    i = [math.floor(g[0]), math.floor(g[1]), math.floor(g[2])]
    f = [g[0] - i[0], g[1] - i[1], g[2] - i[2]]
    f = [f[0] * f[0] * (3 - 2 * f[0]), f[1] * f[1] * (3 - 2 * f[1]), f[2] * f[2] * (3 - 2 * f[2])]
    total = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[0] if dx else 1 - f[0]) * (f[1] if dy else 1 - f[1]) * (f[2] if dz else 1 - f[2])
                total += w * unit(h32(seed, i[0] + dx, i[1] + dy, i[2] + dz))
    return total


def plane(axis):
    return [k for k in range(3) if k != axis]


def placement(skin, pattern_no, size):
    """图案编号 -> 偏移两格数与转几个 90°。0 不偏不转。"""
    if not pattern_no:
        return 0, 0, 0
    return (h32(skin, TAG_PATTERN, pattern_no, 0) % size, h32(skin, TAG_PATTERN, pattern_no, 1) % size,
            h32(skin, TAG_PATTERN, pattern_no, 2) % 4)


def tile_index(p, axis, pixel, place, size, flow=0):
    """图案的哪一格。flow：会动的外观沿投影平面的第二根轴（侧面与顶面上都是枪身方向）走了几格。"""
    a, b = plane(axis)
    i = math.floor(p[a] / pixel) + place[0]
    j = math.floor(p[b] / pixel) + place[1] + flow
    for _ in range(place[2]):
        i, j = j, -i - 1
    return i % size, j % size


class Scratches:
    """划痕：投影平面上每格 0–2 道，长短、方向、第几个出现都由磨损编号定；磨损编号也决定整体平移。"""

    def __init__(self, skin, wear_no, width):
        self.skin, self.wear_no, self.width, self.cache = skin, wear_no, width, {}
        if wear_no:
            self.offset = [unit(h32(skin, TAG_OFFSET, wear_no, k)) * 64 for k in range(3)]
        else:
            self.offset = [0.0, 0.0, 0.0]

    def cell(self, axis, i, j, along):
        key = (axis, i, j)
        if key not in self.cache:
            base = h32(self.skin, TAG_CELL, self.wear_no, axis, i, j)
            lines = []
            for k in range((0, 1, 1, 2)[base & 3]):
                def r(n):
                    return unit(h32(base, k, n))
                cx, cy = (i + r(0)) * CELL, (j + r(1)) * CELL
                # 六成顺着枪身（模型的 z 轴），其余随便
                ang = along + (r(3) * 0.7 - 0.35) if along is not None and r(2) < 0.6 else r(3) * math.pi
                half = 0.15 + r(4) * 0.55
                dx, dy = math.cos(ang) * half, math.sin(ang) * half
                lines.append((cx - dx, cy - dy, cx + dx, cy + dy, r(5)))
            self.cache[key] = lines
        return self.cache[key]

    def order(self, q, axis):
        """平移后的位置 q 落在哪道划痕上就返回它第几个出现（0..1），不在划痕上返回 None。"""
        a, b = plane(axis)
        x, y = q[a], q[b]
        along = 0.0 if a == 2 else (math.pi / 2 if b == 2 else None)
        ci, cj = math.floor(x / CELL), math.floor(y / CELL)
        best = None
        for i in (ci - 1, ci, ci + 1):
            for j in (cj - 1, cj, cj + 1):
                for x0, y0, x1, y1, t in self.cell(axis, i, j, along):
                    vx, vy = x1 - x0, y1 - y0
                    u = max(0.0, min(1.0, ((x - x0) * vx + (y - y0) * vy) / (vx * vx + vy * vy)))
                    ex, ey = x - x0 - u * vx, y - y0 - u * vy
                    if math.sqrt(ex * ex + ey * ey) <= self.width / 2 and (best is None or t < best):
                        best = t
        return best


def face_point(f, a, b):
    return [f['v1'][k] + a * f['ea'][k] + b * f['eb'][k] for k in range(3)]


def snap_to_owner(o, p, n):
    """p 落在主面 o 的哪个样本上（主面的细网格），返回那个样本中心在主面上的位置。叠着的几个面都借它。"""
    d = [p[0] - o['v1'][0], p[1] - o['v1'][1], p[2] - o['v1'][2]]
    la = o['ea'][0] * o['ea'][0] + o['ea'][1] * o['ea'][1] + o['ea'][2] * o['ea'][2]
    lb = o['eb'][0] * o['eb'][0] + o['eb'][1] * o['eb'][1] + o['eb'][2] * o['eb'][2]
    a = max(0.0, min(1.0, (d[0] * o['ea'][0] + d[1] * o['ea'][1] + d[2] * o['ea'][2]) / la))
    b = max(0.0, min(1.0, (d[0] * o['eb'][0] + d[1] * o['eb'][1] + d[2] * o['eb'][2]) / lb))
    u = o['u1'] + a * (o['u2'] - o['u1'])
    v = o['v1t'] + b * (o['v2t'] - o['v1t'])
    u0, u1 = min(o['u1'], o['u2']), max(o['u1'], o['u2'])
    v0, v1 = min(o['v1t'], o['v2t']), max(o['v1t'], o['v2t'])
    i = min(max(math.floor(u * n), math.floor(u0 * n)), math.ceil(u1 * n) - 1)
    j = min(max(math.floor(v * n), math.floor(v0 * n)), math.ceil(v1 * n) - 1)
    a = ((i + 0.5) / n - o['u1']) / (o['u2'] - o['u1'])
    b = ((j + 0.5) / n - o['v1t']) / (o['v2t'] - o['v1t'])
    return face_point(o, a, b)


def expand(texels, faces, n):
    """上漆的像素拆成 n×n 个样本：放大后的贴图坐标 x、y，原贴图坐标 tx、ty，位置 p、投影轴 axis、外露 e、shared、hidden。
    顺序：按像素原来的顺序，每个像素里先行后列。"""
    out = []
    for t in texels:
        f = faces[t['face']]
        o = faces[t['owner']] if t['owner'] >= 0 else None
        for j in range(n):
            for i in range(n):
                sx, sy = t['x'] + (i + 0.5) / n, t['y'] + (j + 0.5) / n
                a = (sx - f['u1']) / (f['u2'] - f['u1'])
                b = (sy - f['v1t']) / (f['v2t'] - f['v1t'])
                p = face_point(f, a, b)
                axis = f['axis']
                if o is not None:
                    p = snap_to_owner(o, p, n)
                    axis = o['axis']
                out.append({'x': t['x'] * n + i, 'y': t['y'] * n + j, 'tx': t['x'], 'ty': t['y'], 'p': p, 'axis': axis,
                            'e': t['e'], 'shared': t['shared'], 'hidden': t['hidden']})
    return out


def wear_ranks(texels, texel_size, skin, wear_no):
    """每个样本"排第几先掉"（0..255）与脏污（0..255）。先是划痕（外露的地方先出现），划痕出完外露的地方再成片掉
    ；脏污积在不外露的地方。texel_size 是样本的大小。"""
    scratches = Scratches(skin, wear_no, max(0.06, texel_size))
    seed = h32(skin, TAG_GRIME, wear_no)
    raw, dirt = [], []
    for t in texels:
        p, axis, e = t['p'], t['axis'], t['e'] / 255.0
        q = [p[0] + scratches.offset[0], p[1] + scratches.offset[1], p[2] + scratches.offset[2]]
        o = scratches.order(q, axis)
        if o is not None:
            raw.append(2 + (1 - o) * (0.4 + 0.6 * e))
        else:
            raw.append(0.6 * e + 0.4 * (0.6 * noise(seed, q, 1.5) + 0.4 * noise(seed, q, 0.5)))
        dirt.append(int(255 * noise(seed, [q[0] + 100, q[1] + 100, q[2] + 100], 3.0) * (1 - 0.6 * e)))
    ordered = sorted(raw)
    n = max(1, len(raw) - 1)
    import bisect
    ranks = [int(255 * bisect.bisect_left(ordered, r) / n) for r in raw]
    return ranks, dirt


def chipped_share(f):
    """外观磨损率 f 下掉多少比例的漆：前慢后快。"""
    return 0.6 * f * f


def luminance(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


class Frame:
    """会动的外观在时刻 t 的状态。每帧算一次，再逐像素用。

    效果（一串，按顺序叠）：
    - flow：图案沿枪身每秒走 speed 格（tile_index 的 flow）
    - pulse：亮处朝白热走、落下时整体略暗，周期 period 秒，幅度 amount
    - hue：色相按正弦来回偏 degrees 度，周期 period 秒（按亮度不变的 YIQ 旋转）
    - sweep：一条宽 width（模型单位）的亮带沿枪身从后扫到前，周期 period 秒，强度 strength
    zmin、zmax：这个模型上漆像素的 z 范围（扫光从哪扫到哪）。
    """

    def __init__(self, anim, t, zmin, zmax):
        self.flow = 0
        self.pulse = self.hue = self.sweep = None
        for e in anim:
            kind = e["type"]
            if kind == "flow":
                self.flow += math.floor(t * e["speed"])
            elif kind == "pulse":
                w = 0.5 + 0.5 * math.sin(2 * math.pi * t / e["period"])
                self.pulse = (e["amount"] * w, 0.85 + 0.15 * w)
            elif kind == "hue":
                self.hue = e["degrees"] * math.sin(2 * math.pi * t / e["period"]) * math.pi / 180
            elif kind == "sweep":
                width = e["width"]
                span = zmax - zmin + 2 * width
                frac = t / e["period"] - math.floor(t / e["period"])
                self.sweep = (zmin - width + span * frac, width, e["strength"])

    def apply(self, r, g, b, z):
        if self.pulse:
            pw, dim = self.pulse
            lum = min(1.0, (0.299 * r + 0.587 * g + 0.114 * b) / 255)
            hot = pw * lum * lum
            r, g, b = (r * dim + (255 - r * dim) * hot, g * dim + (255 - g * dim) * hot, b * dim + (255 - b * dim) * hot)
        if self.hue is not None:
            c, s = math.cos(self.hue), math.sin(self.hue)
            y = 0.299 * r + 0.587 * g + 0.114 * b
            i = 0.596 * r - 0.274 * g - 0.322 * b
            q = 0.211 * r - 0.523 * g + 0.312 * b
            i, q = i * c - q * s, i * s + q * c
            r, g, b = y + 0.956 * i + 0.621 * q, y - 0.272 * i - 0.647 * q, y - 1.106 * i + 1.703 * q
        if self.sweep:
            pos, width, strength = self.sweep
            d = abs(z - pos) / width
            if d < 1:
                f = strength * (1 - d)
                r, g, b = r + (255 - r) * f, g + (255 - g) * f, b + (255 - b) * f
        return r, g, b


def channel(v):
    return max(0, min(255, int(v)))


SIDE = 0   # 从侧面整体投影时用的轴：枪身沿 z，侧面的法线沿 x


def compose(base, texels, texel_size, tile, size, pixel, skin, wear, pattern_no, wear_no, anim=(), t=0.0,
            projection="box"):
    """一把枪的贴图：base 是原贴图的像素表 {(x, y): (r, g, b, a)} 的取值函数，texels 是 expand 拆出来的样本，
    texel_size 是样本的大小；返回放大后的贴图上 {(x, y): (r, g, b, a)}，只含上漆的样本（其余是原贴图按最近邻放大）。

    tile(i, j) -> (r, g, b)。skin 是 str_hash(外观编号)。anim、t：会动的外观的效果与时刻（秒，Frame）；
    没有效果时与第 2 步的结果逐像素相同。projection：box 是每个面按自己的朝向取图案（无方向的图案）；side 是整把枪
    从侧面一次投影、图案编号只平移不转（条纹、波浪、格纹这类有方向的，否则面与面交界处方向乱）。划痕与脏污
    照样按每个面自己的朝向。
    """
    lums = sorted(luminance(base(t['tx'], t['ty'])) for t in texels)
    mid = lums[len(lums) // 2] if lums else 0.0
    mid = mid or 1e-3
    place = placement(skin, pattern_no, size)
    if projection == "side":
        place = (place[0], place[1], 0)
    ranks, dirt = wear_ranks(texels, texel_size, skin, wear_no)
    threshold = 1 - chipped_share(wear)
    frame = None
    if anim:
        zs = [tx['p'][2] for tx in texels] or [0.0]
        frame = Frame(anim, t, min(zs), max(zs))
    out = {}
    for tx, rank, d in zip(texels, ranks, dirt):
        b = base(tx['tx'], tx['ty'])
        if tx['hidden']:
            # 被主面完全盖住、朝同一边：透明，TaCZ 丢掉这一点，那里只剩主面一层，不会抢着显示
            out[(tx['x'], tx['y'])] = (0, 0, 0, 0)
            continue
        if rank / 255 >= threshold:
            c = b
        else:
            axis = SIDE if projection == "side" else tx['axis']
            pc = tile(*tile_index(tx['p'], axis, pixel, place, size, frame.flow if frame else 0))
            k = 1.0 if tx['shared'] else max(0.7, min(1.15, 0.75 + 0.25 * luminance(b) / mid))
            if frame:
                c = tuple(channel(v) for v in frame.apply(pc[0] * k, pc[1] * k, pc[2] * k, tx['p'][2]))
            else:
                c = (min(255, int(pc[0] * k)), min(255, int(pc[1] * k)), min(255, int(pc[2] * k)))
        grime = 1 - 0.25 * wear * (0.4 + 1.2 * d / 255)
        out[(tx['x'], tx['y'])] = (int(c[0] * grime), int(c[1] * grime), int(c[2] * grime), b[3])
    return out
