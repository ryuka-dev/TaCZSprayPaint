package io.github.ryukadev.taczspray.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes one gun's skin texture: the gun's own texture with the skin's pattern painted on, worn down to this gun's
 * wear, its pattern and its scratches placed by this gun's numbers.
 *
 * <p><b>Line for line the same as {@code tools/skins/skinalgo.py}</b>, which is the reference: the generator draws its
 * previews with it, and {@code skingen.py --fixture} writes the test case this class must match pixel for pixel. Change
 * one, change the other and write the fixture again. Every random number comes from {@link #h32}, never from a JDK
 * random source; sine and cosine are {@link StrictMath}'s.
 *
 * <p>Pure: no game types, so it is tested off the game. Colours are packed ARGB.
 */
public final class SkinAlgo {
    static final int TAG_PATTERN = 1;
    static final int TAG_WEAR = 2;
    static final int TAG_GRIME = 3;
    static final int TAG_CELL = 4;
    static final int TAG_OFFSET = 5;
    static final double CELL = 1.0;

    private SkinAlgo() {
    }

    static int fmix(int h) {
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    static int h32(int... xs) {
        int h = 0x811C9DC5;
        for (int x : xs) {
            h = fmix(h ^ fmix(x + 0x9E3779B9));
        }
        return h;
    }

    static double unit(int h) {
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** FNV-1a 32 over UTF-8: a skin id as a number. */
    public static int strHash(String s) {
        int h = 0x811C9DC5;
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x01000193;
        }
        return h;
    }

    static double noise(int seed, double x, double y, double z, double cell) {
        double g0 = x / cell, g1 = y / cell, g2 = z / cell;
        double i0 = Math.floor(g0), i1 = Math.floor(g1), i2 = Math.floor(g2);
        double f0 = g0 - i0, f1 = g1 - i1, f2 = g2 - i2;
        f0 = f0 * f0 * (3 - 2 * f0);
        f1 = f1 * f1 * (3 - 2 * f1);
        f2 = f2 * f2 * (3 - 2 * f2);
        int a = (int) i0, b = (int) i1, c = (int) i2;
        double total = 0.0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    double w = (dx == 1 ? f0 : 1 - f0) * (dy == 1 ? f1 : 1 - f1) * (dz == 1 ? f2 : 1 - f2);
                    total += w * unit(h32(seed, a + dx, b + dy, c + dz));
                }
            }
        }
        return total;
    }

    /** The two axes of the plane a texel is projected on (its axis is the normal's largest). */
    static int[] plane(int axis) {
        return switch (axis) {
            case 0 -> new int[]{1, 2};
            case 1 -> new int[]{0, 2};
            default -> new int[]{0, 1};
        };
    }

    /** Pattern number to an offset in tiles and a number of quarter turns; 0 moves nothing. */
    static int[] placement(int skin, int patternNo, int size) {
        if (patternNo == 0) {
            return new int[]{0, 0, 0};
        }
        return new int[]{Integer.remainderUnsigned(h32(skin, TAG_PATTERN, patternNo, 0), size),
                Integer.remainderUnsigned(h32(skin, TAG_PATTERN, patternNo, 1), size),
                Integer.remainderUnsigned(h32(skin, TAG_PATTERN, patternNo, 2), 4)};
    }

    /** Scratches on the projection plane: 0-2 per cell, their length, angle and order set by the wear number. */
    static final class Scratches {
        private final int skin;
        private final int wearNo;
        private final double width;
        final double[] offset = new double[3];
        private final Map<Long, double[][]> cache = new HashMap<>();

        Scratches(int skin, int wearNo, double width) {
            this.skin = skin;
            this.wearNo = wearNo;
            this.width = width;
            if (wearNo != 0) {
                for (int k = 0; k < 3; k++) {
                    offset[k] = unit(h32(skin, TAG_OFFSET, wearNo, k)) * 64;
                }
            }
        }

        private double[][] cell(int axis, int i, int j, Double along) {
            long key = ((long) axis << 62) | (((long) i & 0x7FFFFFFFL) << 31) | ((long) j & 0x7FFFFFFFL);
            double[][] lines = cache.get(key);
            if (lines == null) {
                int base = h32(skin, TAG_CELL, wearNo, axis, i, j);
                int count = new int[]{0, 1, 1, 2}[base & 3];
                lines = new double[count][];
                for (int k = 0; k < count; k++) {
                    double cx = (i + unit(h32(base, k, 0))) * CELL;
                    double cy = (j + unit(h32(base, k, 1))) * CELL;
                    double ang = along != null && unit(h32(base, k, 2)) < 0.6
                            ? along + (unit(h32(base, k, 3)) * 0.7 - 0.35)
                            : unit(h32(base, k, 3)) * Math.PI;
                    double half = 0.15 + unit(h32(base, k, 4)) * 0.55;
                    double dx = StrictMath.cos(ang) * half, dy = StrictMath.sin(ang) * half;
                    lines[k] = new double[]{cx - dx, cy - dy, cx + dx, cy + dy, unit(h32(base, k, 5))};
                }
                cache.put(key, lines);
            }
            return lines;
        }

        /** Which scratch the shifted position lies on, as its order (0..1); -1 when on none. */
        double order(double qx, double qy, double qz, int axis) {
            int[] ab = plane(axis);
            double[] q = {qx, qy, qz};
            double x = q[ab[0]], y = q[ab[1]];
            Double along = ab[0] == 2 ? Double.valueOf(0.0) : ab[1] == 2 ? Double.valueOf(Math.PI / 2) : null;
            int ci = (int) Math.floor(x / CELL), cj = (int) Math.floor(y / CELL);
            double best = -1;
            for (int i = ci - 1; i <= ci + 1; i++) {
                for (int j = cj - 1; j <= cj + 1; j++) {
                    for (double[] l : cell(axis, i, j, along)) {
                        double vx = l[2] - l[0], vy = l[3] - l[1];
                        double u = Math.max(0.0, Math.min(1.0, ((x - l[0]) * vx + (y - l[1]) * vy) / (vx * vx + vy * vy)));
                        double ex = x - l[0] - u * vx, ey = y - l[1] - u * vy;
                        if (Math.sqrt(ex * ex + ey * ey) <= width / 2 && (best < 0 || l[4] < best)) {
                            best = l[4];
                        }
                    }
                }
            }
            return best;
        }
    }

    /** Each texel's place in the order paint comes off (0..255), and its grime (0..255). */
    /**
     * Each sample's place in the order the paint comes off (0..255) and its grime (0..255): scratches first, the exposed
     * ones first, then the exposed parts in patches (by how exposed the whole gun is there, not by each small
     * block's edges); grime gathers where it is not exposed. {@code sampleSize} sets the scratches' width.
     */
    static int[][] wearRanks(SkinTexels.Samples t, double sampleSize, int skin, int wearNo) {
        Scratches scratches = new Scratches(skin, wearNo, Math.max(0.06, sampleSize));
        int seed = h32(skin, TAG_GRIME, wearNo);
        int n = t.count;
        double[] raw = new double[n];
        int[] dirt = new int[n];
        for (int k = 0; k < n; k++) {
            double e = t.exposure[k] / 255.0;
            double qx = t.pos(k, 0) + scratches.offset[0];
            double qy = t.pos(k, 1) + scratches.offset[1];
            double qz = t.pos(k, 2) + scratches.offset[2];
            double o = scratches.order(qx, qy, qz, t.axis[k]);
            if (o >= 0) {
                raw[k] = 2 + (1 - o) * (0.4 + 0.6 * e);
            } else {
                raw[k] = 0.6 * e + 0.4 * (0.6 * noise(seed, qx, qy, qz, 1.5) + 0.4 * noise(seed, qx, qy, qz, 0.5));
            }
            dirt[k] = (int) (255 * noise(seed, qx + 100, qy + 100, qz + 100, 3.0) * (1 - 0.6 * e));
        }
        double[] ordered = raw.clone();
        Arrays.sort(ordered);
        int span = Math.max(1, n - 1);
        int[] ranks = new int[n];
        for (int k = 0; k < n; k++) {
            ranks[k] = (int) (255.0 * lowerBound(ordered, raw[k]) / span);
        }
        return new int[][]{ranks, dirt};
    }

    private static int lowerBound(double[] sorted, double v) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** How much of the paint is off at this wear: slowly at first, faster later. */
    static double chippedShare(double wear) {
        return 0.6 * wear * wear;
    }

    static double luminance(int argb) {
        return (0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF) + 0.114 * (argb & 0xFF)) / 255;
    }

    /**
     * The gun's texture with this skin on it.
     *
     * @param base    the gun's own texture, ARGB, row by row, {@code width} wide (not changed)
     * @param tile    the pattern, RGB, row by row, {@code size} wide
     * @param skin    {@link #strHash} of the skin id
     * @return a new texture; only the painted texels differ from {@code base}
     */
    public static int[] compose(int[] base, int width, SkinTexels t, int[] tile, int size, double pixel, int skin,
                                double wear, int patternNo, int wearNo) {
        return compose(base, width, t, tile, size, pixel, skin, wear, patternNo, wearNo, List.of(), 0, false);
    }

    /** The gun's own texture drawn up n times, each texel to an n×n block (nearest), ARGB row by row. */
    public static int[] drawUp(int[] base, int width, int height, int n) {
        int[] out = new int[width * n * height * n];
        for (int y = 0; y < height * n; y++) {
            for (int x = 0; x < width * n; x++) {
                out[y * width * n + x] = base[(y / n) * width + x / n];
            }
        }
        return out;
    }

    /**
     * The same at a moment {@code time} (seconds) of a moving skin's effects (the generator's skinalgo.compose with anim and t),
     * the pattern projected from the side when {@code side} (skinalgo.compose's projection "side").
     */
    public static int[] compose(int[] base, int width, SkinTexels t, int[] tile, int size, double pixel, int skin,
                                double wear, int patternNo, int wearNo, List<Effect> anim, double time, boolean side) {
        SkinTexels.Samples samples = t.expand();
        Prepared p = prepare(base, width, samples, t.texelSize / t.scale, size, pixel, skin, wear, patternNo, wearNo, side);
        int[] out = drawUp(base, width, t.height, t.scale);
        paint(p, tile, size, anim.isEmpty() ? null : new Frame(anim, time, p.zmin, p.zmax), out);
        return out;
    }

    /**
     * What stays the same from frame to frame of one gun's skin: for each painted texel where it goes, the gun's own
     * colour there, whether the paint is off, the shading taken from the gun, the grime, the pattern cell before any
     * flow, and its height along the gun (for the sweep). A still skin is painted from it once; a moving one each
     * time it moves, without working out the wear again.
     */
    public static final class Prepared {
        final int n;
        final int[] at;
        final int[] base;
        final boolean[] hidden;
        final boolean[] chipped;
        final double[] shade;
        final double[] grime;
        final int[] i;
        final int[] j;
        final int turns;
        final double[] z;
        final double zmin;
        final double zmax;

        Prepared(int n, int turns) {
            this.n = n;
            this.at = new int[n];
            this.base = new int[n];
            this.hidden = new boolean[n];
            this.chipped = new boolean[n];
            this.shade = new double[n];
            this.grime = new double[n];
            this.i = new int[n];
            this.j = new int[n];
            this.turns = turns;
            this.z = new double[n];
            this.zmin = 0;
            this.zmax = 0;
        }

        private Prepared(Prepared p, double zmin, double zmax) {
            this.n = p.n;
            this.at = p.at;
            this.base = p.base;
            this.hidden = p.hidden;
            this.chipped = p.chipped;
            this.shade = p.shade;
            this.grime = p.grime;
            this.i = p.i;
            this.j = p.j;
            this.turns = p.turns;
            this.z = p.z;
            this.zmin = zmin;
            this.zmax = zmax;
        }
    }

    /** The axis whose plane a side projection takes the pattern in: the gun lies along z, its sides face x. */
    static final int SIDE = 0;

    /**
     * @param side the pattern projected once from the gun's side and only shifted, never turned, by the pattern number:
     *             for patterns with a direction (stripes, waves, tartan), which otherwise turn from face to face. The
     *             scratches and the grime keep each face's own axis.
     */
    public static Prepared prepare(int[] base, int width, SkinTexels.Samples t, double sampleSize, int size, double pixel,
                                   int skin, double wear, int patternNo, int wearNo, boolean side) {
        int n = t.count;
        double[] lums = new double[n];
        for (int k = 0; k < n; k++) {
            lums[k] = luminance(base[t.texel[k]]);
        }
        Arrays.sort(lums);
        double mid = n > 0 ? lums[n / 2] : 0.0;
        if (mid == 0) {
            mid = 1e-3;
        }
        int[] place = placement(skin, patternNo, size);
        int[][] wr = wearRanks(t, sampleSize, skin, wearNo);
        double threshold = 1 - chippedShare(wear);
        Prepared p = new Prepared(n, side ? 0 : place[2]);
        double zmin = n > 0 ? Double.MAX_VALUE : 0;
        double zmax = n > 0 ? -Double.MAX_VALUE : 0;
        for (int k = 0; k < n; k++) {
            int b = base[t.texel[k]];
            p.at[k] = t.at[k];
            p.hidden[k] = t.hidden[k];
            p.base[k] = b;
            p.chipped[k] = wr[0][k] / 255.0 >= threshold;
            p.shade[k] = t.shared[k] ? 1.0 : Math.max(0.7, Math.min(1.15, 0.75 + 0.25 * luminance(b) / mid));
            p.grime[k] = 1 - 0.25 * wear * (0.4 + 1.2 * wr[1][k] / 255);
            int[] ab = plane(side ? SIDE : t.axis[k]);
            p.i[k] = (int) Math.floor(t.pos(k, ab[0]) / pixel) + place[0];
            p.j[k] = (int) Math.floor(t.pos(k, ab[1]) / pixel) + place[1];
            p.z[k] = t.pos(k, 2);
            zmin = Math.min(zmin, p.z[k]);
            zmax = Math.max(zmax, p.z[k]);
        }
        return new Prepared(p, zmin, zmax);
    }

    /**
     * Paints the prepared samples into {@code out} (the gun's texture drawn up n times, ARGB), still ({@code frame} null)
     * or at a frame.
     */
    public static void paint(Prepared p, int[] tile, int size, Frame frame, int[] out) {
        int flow = frame == null ? 0 : frame.flow;
        double[] rgb = new double[3];
        for (int k = 0; k < p.n; k++) {
            if (p.hidden[k]) {
                // under its owner, facing the same way: see-through, so only the owner is drawn there and the two never
                // fight for the same place
                out[p.at[k]] = 0;
                continue;
            }
            int b = p.base[k];
            int r;
            int g;
            int bl;
            if (p.chipped[k]) {
                r = (b >> 16) & 0xFF;
                g = (b >> 8) & 0xFF;
                bl = b & 0xFF;
            } else {
                int i = p.i[k];
                int j = p.j[k] + flow;
                for (int turn = 0; turn < p.turns; turn++) {
                    int ni = j;
                    j = -i - 1;
                    i = ni;
                }
                int pc = tile[Math.floorMod(j, size) * size + Math.floorMod(i, size)];
                double kk = p.shade[k];
                if (frame == null) {
                    r = Math.min(255, (int) (((pc >> 16) & 0xFF) * kk));
                    g = Math.min(255, (int) (((pc >> 8) & 0xFF) * kk));
                    bl = Math.min(255, (int) ((pc & 0xFF) * kk));
                } else {
                    rgb[0] = ((pc >> 16) & 0xFF) * kk;
                    rgb[1] = ((pc >> 8) & 0xFF) * kk;
                    rgb[2] = (pc & 0xFF) * kk;
                    frame.apply(rgb, p.z[k]);
                    r = channel(rgb[0]);
                    g = channel(rgb[1]);
                    bl = channel(rgb[2]);
                }
            }
            double grime = p.grime[k];
            out[p.at[k]] = (b & 0xFF000000) | ((int) (r * grime) << 16) | ((int) (g * grime) << 8) | (int) (bl * grime);
        }
    }

    static int channel(double v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    /**
     * One effect of a moving skin, as the skin's {@code skin.json} lists it under {@code anim}: {@code flow}
     * (speed, cells a second), {@code pulse} (period, amount), {@code hue} (period, degrees), {@code sweep} (period,
     * width in model units, strength). Unused fields are 0.
     */
    public record Effect(String type, double speed, double period, double amount, double degrees, double width,
                         double strength) {
        public Effect {
            boolean ok = switch (type) {
                case "flow" -> Double.isFinite(speed);
                case "pulse" -> period > 0 && amount >= 0 && amount <= 2;
                case "hue" -> period > 0 && Math.abs(degrees) <= 180;
                case "sweep" -> period > 0 && width > 0 && strength >= 0 && strength <= 1;
                default -> false;
            };
            if (!ok) {
                throw new IllegalArgumentException("bad skin effect " + type);
            }
        }
    }

    /** A moving skin at one moment: worked out once a frame, then applied to each texel (skinalgo.Frame). */
    public static final class Frame {
        final int flow;
        final boolean pulse;
        final double pulseWeight;
        final double dim;
        final boolean hue;
        final double cos;
        final double sin;
        final boolean sweep;
        final double sweepAt;
        final double sweepWidth;
        final double sweepStrength;

        public Frame(List<Effect> anim, double time, double zmin, double zmax) {
            int flow = 0;
            boolean pulse = false;
            double pw = 0;
            double dim = 1;
            boolean hue = false;
            double angle = 0;
            boolean sweep = false;
            double at = 0;
            double width = 1;
            double strength = 0;
            for (Effect e : anim) {
                switch (e.type()) {
                    case "flow" -> flow += (int) Math.floor(time * e.speed());
                    case "pulse" -> {
                        double w = 0.5 + 0.5 * StrictMath.sin(2 * Math.PI * time / e.period());
                        pulse = true;
                        pw = e.amount() * w;
                        dim = 0.85 + 0.15 * w;
                    }
                    case "hue" -> {
                        hue = true;
                        angle = e.degrees() * StrictMath.sin(2 * Math.PI * time / e.period()) * Math.PI / 180;
                    }
                    case "sweep" -> {
                        sweep = true;
                        width = e.width();
                        double span = zmax - zmin + 2 * width;
                        double cycles = time / e.period();
                        at = zmin - width + span * (cycles - Math.floor(cycles));
                        strength = e.strength();
                    }
                    default -> {
                    }
                }
            }
            this.flow = flow;
            this.pulse = pulse;
            this.pulseWeight = pw;
            this.dim = dim;
            this.hue = hue;
            this.cos = StrictMath.cos(angle);
            this.sin = StrictMath.sin(angle);
            this.sweep = sweep;
            this.sweepAt = at;
            this.sweepWidth = width;
            this.sweepStrength = strength;
        }

        void apply(double[] c, double z) {
            double r = c[0];
            double g = c[1];
            double b = c[2];
            if (pulse) {
                double lum = Math.min(1.0, (0.299 * r + 0.587 * g + 0.114 * b) / 255);
                double hot = pulseWeight * lum * lum;
                r = r * dim + (255 - r * dim) * hot;
                g = g * dim + (255 - g * dim) * hot;
                b = b * dim + (255 - b * dim) * hot;
            }
            if (hue) {
                double y = 0.299 * r + 0.587 * g + 0.114 * b;
                double i = 0.596 * r - 0.274 * g - 0.322 * b;
                double q = 0.211 * r - 0.523 * g + 0.312 * b;
                double i2 = i * cos - q * sin;
                double q2 = i * sin + q * cos;
                r = y + 0.956 * i2 + 0.621 * q2;
                g = y - 0.272 * i2 - 0.647 * q2;
                b = y - 1.106 * i2 + 1.703 * q2;
            }
            if (sweep) {
                double d = Math.abs(z - sweepAt) / sweepWidth;
                if (d < 1) {
                    double f = sweepStrength * (1 - d);
                    r = r + (255 - r) * f;
                    g = g + (255 - g) * f;
                    b = b + (255 - b) * f;
                }
            }
            c[0] = r;
            c[1] = g;
            c[2] = b;
        }
    }
}
