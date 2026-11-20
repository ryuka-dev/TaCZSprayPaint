"""外观的图案：按规则生成一块能无缝平铺的方图（pattern.png，随 mod 下发）。一个图案铺到所有枪，不用别人的图。

每种图案是 skins.json 里 pattern 的一个 kind，参数也写在那里；生成只用整数哈希（skinalgo.h32），同一份参数永远出同一张图。
不做接近全黑、也不做迷彩——外观不能让人在夜里或草里更难被看见。

平铺：图在枪上按 3D 位置一格一格重复（skinalgo.tile_index），所以每种图案的噪声、条纹、波浪都按图的边长取周期，接缝看不出来。
"""
import math

from PIL import Image

import skinalgo as algo


def rgb(hexes):
    return [tuple(int(c[j:j + 2], 16) for j in (0, 2, 4)) for c in hexes]


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


def ramp(colours, t):
    """在一串颜色里按 0..1 取，相邻两色之间线性插值。"""
    t = max(0.0, min(1.0, t)) * (len(colours) - 1)
    i = min(len(colours) - 2, int(t))
    return mix(colours[i], colours[i + 1], t - i)


def pnoise(seed, x, y, period, cells):
    """周期为 period 格的值噪声：一个周期里 cells×cells 个格点，格点之间平滑插值。0..1。"""
    cs = period / cells
    fx, fy = x / cs, y / cs
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)

    def g(i, j):
        return algo.unit(algo.h32(seed, 11, i % cells, j % cells))

    a = g(ix, iy) + (g(ix + 1, iy) - g(ix, iy)) * tx
    b = g(ix, iy + 1) + (g(ix + 1, iy + 1) - g(ix, iy + 1)) * tx
    return a + (b - a) * ty


def fbm(seed, x, y, period, cells, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += amp * pnoise(seed + o * 101, x, y, period, cells * (2 ** o))
        norm += amp
        amp *= 0.5
    return total / norm


def voronoi(seed, x, y, period, cells):
    """周期 Voronoi：到最近与次近格点的距离（格为单位）。"""
    cs = period / cells
    fx, fy = x / cs, y / cs
    ix, iy = math.floor(fx), math.floor(fy)
    d1 = d2 = 9.0
    for dj in (-1, 0, 1):
        for di in (-1, 0, 1):
            i, j = ix + di, iy + dj
            h = algo.h32(seed, 13, i % cells, j % cells)
            px = i + algo.unit(h)
            py = j + algo.unit(algo.h32(h, 1))
            d = math.hypot(fx - px, fy - py)
            if d < d1:
                d1, d2 = d, d1
            elif d < d2:
                d2 = d
    return d1, d2


def jitter(seed, x, y, amount):
    """逐格的一点抖动，像原版贴图那样一格一色，不是平滑渐变。"""
    return (algo.unit(algo.h32(seed, 17, x, y)) - 0.5) * amount


# ---------- 各种图案 ----------

def leaves(spec, seed, size):
    """樱：像原版树叶那样一格一色，四档深浅按低频噪声铺开，撒少量点缀色（第 0 步的做法，结果不变）。"""
    shades = rgb(spec["shades"])
    accents = rgb(spec.get("accents", []))
    tile = Image.new("RGB", (size, size))
    px = tile.load()
    for y in range(size):
        for x in range(size):
            n = 0.65 * algo.noise(seed, (x, y, 0), 4) + 0.35 * algo.unit(algo.h32(seed, 7, x, y))
            px[x, y] = shades[min(len(shades) - 1, int(n * len(shades)))]
    for k in range(int(size * size * spec.get("accent_rate", 0.0))):
        x, y = algo.h32(seed, 8, k, 0) % size, algo.h32(seed, 8, k, 1) % size
        px[x, y] = accents[algo.h32(seed, 8, k, 2) % len(accents)]
        if algo.h32(seed, 8, k, 3) % 2:
            px[(x + 1) % size, y] = px[x, y]
    return tile


def crackle(spec, seed, size):
    """冰裂、青瓷：一层带起伏的釉色，上面一张不规则的裂纹网（Voronoi 的边）。"""
    base = rgb(spec["base"])
    crack = rgb([spec["crack"]])[0]
    cells = spec.get("cells", 5)
    width = spec.get("width", 0.07)

    def f(x, y):
        d1, d2 = voronoi(seed, x + 0.5, y + 0.5, size, cells)
        if d2 - d1 < width * cells / 5:
            return crack
        return ramp(base, fbm(seed, x, y, size, 4) + jitter(seed, x, y, 0.12))
    return draw(size, f)


def blotches(spec, seed, size):
    """锈、苔斑这类：几档颜色按分形噪声的高低分片，边缘带一点逐格的毛刺。"""
    shades = rgb(spec["shades"])
    cells = spec.get("cells", 4)

    def f(x, y):
        n = fbm(seed, x, y, size, cells) + jitter(seed, x, y, 0.1)
        return shades[max(0, min(len(shades) - 1, int(n * len(shades) * 1.1 - 0.05 * len(shades))))]
    return draw(size, f)


def veins(spec, seed, size):
    """熔岩、大理石：底色上一道道弯曲的脉络，脉络中间最亮。"""
    base = rgb(spec["base"])
    vein = rgb(spec["vein"])
    freq = spec.get("freq", 2)
    warp = spec.get("warp", 2.5)
    thin = spec.get("thin", 0.12)

    def f(x, y):
        n = fbm(seed, x, y, size, 4)
        v = abs(math.sin(math.pi * (freq * (x + y) / size + warp * (n - 0.5))))
        b = ramp(base, pnoise(seed + 5, x, y, size, 3) + jitter(seed, x, y, 0.1))
        if v < thin:
            return ramp(vein, 1 - v / thin)
        return b
    return draw(size, f)


def stripes(spec, seed, size):
    """斜条纹：霓虹、警戒、糖果。colours 按顺序排，widths 是各自的宽（格），总宽要整除边长才能无缝。"""
    colours = rgb(spec["colours"])
    widths = spec["widths"]
    period = sum(widths)
    if size % period:
        raise SystemExit(f"stripes：条纹总宽 {period} 不整除边长 {size}")
    edges = []
    acc = 0
    for w in widths:
        acc += w
        edges.append(acc)

    def f(x, y):
        t = (x + y) % period
        i = next(k for k, e in enumerate(edges) if t < e)
        c = colours[i]
        return mix(c, (255, 255, 255) if jitter(seed, x, y, 1) > 0 else (0, 0, 0), abs(jitter(seed, x, y, 0.08)))
    return draw(size, f)


def dots(spec, seed, size):
    """波点：错开的圆点，底色与点色各一，点的大小一致。"""
    base = rgb([spec["base"]])[0]
    dot = rgb([spec["dot"]])[0]
    step = spec.get("step", 8)
    radius = spec.get("radius", 2.2)
    if size % step:
        raise SystemExit(f"dots：间距 {step} 不整除边长 {size}")

    def f(x, y):
        for cx, cy in ((0, 0), (step / 2, step / 2)):
            dx = (x + 0.5 - cx) % step
            dy = (y + 0.5 - cy) % step
            dx, dy = min(dx, step - dx), min(dy, step - dy)
            if dx * dx + dy * dy <= radius * radius:
                return dot
        return mix(base, (0, 0, 0), abs(jitter(seed, x, y, 0.06)))
    return draw(size, f)


def bands(spec, seed, size):
    """晚霞、海浪：沿一个方向的色带（三角波，首尾相接），被噪声与正弦扭弯；可再加一点逐格抖动。"""
    colours = rgb(spec["colours"])
    wave = spec.get("wave", 0.0)
    warp = spec.get("warp", 0.6)

    def f(x, y):
        t = y / size + wave * math.sin(2 * math.pi * x / size * 2) / 8 + warp * (fbm(seed, x, y, size, 3) - 0.5) / 4
        t = 1 - abs(((t % 1) * 2) - 1)
        return ramp(colours, t + jitter(seed, x, y, 0.08))
    return draw(size, f)


def plaid(spec, seed, size):
    """格纹：横竖两套条带叠在一起，交叉处取两色的平均（苏格兰格子）。"""
    base = rgb([spec["base"]])[0]
    lines = [(rgb([c])[0], at, w) for c, at, w in spec["lines"]]

    def band(v):
        for c, at, w in lines:
            if (v - at) % size < w:
                return c
        return None

    def f(x, y):
        a, b = band(x), band(y)
        if a and b:
            return mix(a, b, 0.5)
        c = a or b
        if c:
            return mix(base, c, 0.75)
        return mix(base, (0, 0, 0), abs(jitter(seed, x, y, 0.06)))
    return draw(size, f)


def draw(size, f):
    tile = Image.new("RGB", (size, size))
    px = tile.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = f(x, y)
    return tile


KINDS = {"leaves": leaves, "crackle": crackle, "blotches": blotches, "veins": veins, "stripes": stripes,
         "dots": dots, "bands": bands, "plaid": plaid}


def tile(spec, seed):
    kind = spec.get("kind", "leaves")
    if kind not in KINDS:
        raise SystemExit(f"没有这种图案：{kind}（有 {', '.join(KINDS)}）")
    return KINDS[kind](spec, seed, spec.get("size", 16))
