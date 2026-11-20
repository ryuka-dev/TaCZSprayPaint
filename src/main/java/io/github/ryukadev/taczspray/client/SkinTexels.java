package io.github.ryukadev.taczspray.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The painted texels of one model of one gun region, as {@code tools/skins/skingen.py} writes them ({@code
 * texels_bytes}), version 3: big-endian {@code "DSK1"}, the version, the texture's width and height, how many times it
 * is drawn up (n), the size of a texel on the model, the face count and the texel count; then each face (a
 * corner, two edges, where it lies on the texture in its texels, its projection axis) and each painted texel (where it
 * is, its face, the face it borrows from when it lies on another, whether it does and whether it is drawn see-through,
 * how exposed it is). {@link #expand} splits each texel into n×n samples placed by its face's geometry, line for line
 * as {@code skinalgo.expand}. Only our own data: the texture itself is the player's.
 */
public final class SkinTexels {
    public final int width;
    public final int height;
    public final int scale;
    public final double texelSize;
    public final int count;
    final int[] x;
    final int[] y;
    final int[] face;
    final int[] owner;
    final boolean[] shared;
    final boolean[] hidden;
    final int[] exposure;
    private final double[] faces;
    private final int[] faceAxis;

    private static final int FACE = 13;

    private SkinTexels(int width, int height, int scale, double texelSize, int faceCount, int count) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.texelSize = texelSize;
        this.count = count;
        x = new int[count];
        y = new int[count];
        face = new int[count];
        owner = new int[count];
        shared = new boolean[count];
        hidden = new boolean[count];
        exposure = new int[count];
        faces = new double[faceCount * FACE];
        faceAxis = new int[faceCount];
    }

    public static SkinTexels read(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] magic = new byte[4];
        data.readFully(magic);
        if (magic[0] != 'D' || magic[1] != 'S' || magic[2] != 'K' || magic[3] != '1') {
            throw new IOException("not a skin texel file");
        }
        int version = data.readUnsignedShort();
        if (version != 3) {
            throw new IOException("skin texel file version " + version + " is not the one this client reads (3)");
        }
        int width = data.readUnsignedShort();
        int height = data.readUnsignedShort();
        int scale = data.readUnsignedByte();
        data.readUnsignedByte();
        double texelSize = data.readFloat();
        int faceCount = data.readUnsignedShort();
        int count = data.readInt();
        if (scale < 1 || scale > 8 || count < 0 || count > width * height) {
            throw new IOException("bad texel file header");
        }
        SkinTexels t = new SkinTexels(width, height, scale, texelSize, faceCount, count);
        for (int f = 0; f < faceCount; f++) {
            for (int k = 0; k < FACE; k++) {
                t.faces[f * FACE + k] = data.readFloat();
            }
            t.faceAxis[f] = data.readUnsignedByte();
            if (t.faceAxis[f] > 2) {
                throw new IOException("face " + f + " has no axis");
            }
        }
        for (int k = 0; k < count; k++) {
            t.x[k] = data.readUnsignedShort();
            t.y[k] = data.readUnsignedShort();
            t.face[k] = data.readUnsignedShort();
            int o = data.readUnsignedShort();
            t.owner[k] = o == 65535 ? -1 : o;
            int flags = data.readUnsignedByte();
            t.shared[k] = (flags & 1) != 0;
            t.hidden[k] = (flags & 2) != 0;
            t.exposure[k] = data.readUnsignedByte();
            if (t.x[k] >= width || t.y[k] >= height || t.face[k] >= faceCount || t.owner[k] >= faceCount) {
                throw new IOException("texel " + k + " is outside the texture or names no face");
            }
        }
        return t;
    }

    // a face: v1 (0..2), ea (3..5), eb (6..8), u1 v1 u2 v2 on the texture in its texels (9..12)
    private double f(int face, int k) {
        return faces[face * FACE + k];
    }

    private void point(int face, double a, double b, double[] out) {
        for (int k = 0; k < 3; k++) {
            out[k] = f(face, k) + a * f(face, 3 + k) + b * f(face, 6 + k);
        }
    }

    /** Where p falls on the owner's fine grid: the centre of that sample on the owner (skinalgo.snap_to_owner). */
    private void snapToOwner(int o, double[] p, int n) {
        double d0 = p[0] - f(o, 0);
        double d1 = p[1] - f(o, 1);
        double d2 = p[2] - f(o, 2);
        double la = f(o, 3) * f(o, 3) + f(o, 4) * f(o, 4) + f(o, 5) * f(o, 5);
        double lb = f(o, 6) * f(o, 6) + f(o, 7) * f(o, 7) + f(o, 8) * f(o, 8);
        double a = Math.max(0.0, Math.min(1.0, (d0 * f(o, 3) + d1 * f(o, 4) + d2 * f(o, 5)) / la));
        double b = Math.max(0.0, Math.min(1.0, (d0 * f(o, 6) + d1 * f(o, 7) + d2 * f(o, 8)) / lb));
        double u1 = f(o, 9);
        double v1 = f(o, 10);
        double u2 = f(o, 11);
        double v2 = f(o, 12);
        double u = u1 + a * (u2 - u1);
        double v = v1 + b * (v2 - v1);
        int i = (int) Math.min(Math.max(Math.floor(u * n), Math.floor(Math.min(u1, u2) * n)), Math.ceil(Math.max(u1, u2) * n) - 1);
        int j = (int) Math.min(Math.max(Math.floor(v * n), Math.floor(Math.min(v1, v2) * n)), Math.ceil(Math.max(v1, v2) * n) - 1);
        point(o, ((i + 0.5) / n - u1) / (u2 - u1), ((j + 0.5) / n - v1) / (v2 - v1), p);
    }

    /**
     * Each painted texel split into n×n samples (skinalgo.expand): in texel order, rows first within a texel. A sample's
     * place comes from its face's geometry; one that lies on another face borrows the owner's sample there, place and
     * axis both, so faces lying on one another are painted alike.
     */
    public Samples expand() {
        int n = scale;
        Samples s = new Samples(count * n * n, width * n, n);
        double[] p = new double[3];
        int k = 0;
        for (int t = 0; t < count; t++) {
            int fa = face[t];
            int o = owner[t];
            double u1 = f(fa, 9);
            double v1 = f(fa, 10);
            double u2 = f(fa, 11);
            double v2 = f(fa, 12);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    double sx = x[t] + (i + 0.5) / n;
                    double sy = y[t] + (j + 0.5) / n;
                    point(fa, (sx - u1) / (u2 - u1), (sy - v1) / (v2 - v1), p);
                    int axis = faceAxis[fa];
                    if (o >= 0) {
                        snapToOwner(o, p, n);
                        axis = faceAxis[o];
                    }
                    s.at[k] = (y[t] * n + j) * width * n + x[t] * n + i;
                    s.texel[k] = y[t] * width + x[t];
                    s.pos[k * 3] = p[0];
                    s.pos[k * 3 + 1] = p[1];
                    s.pos[k * 3 + 2] = p[2];
                    s.axis[k] = axis;
                    s.exposure[k] = exposure[t];
                    s.shared[k] = shared[t];
                    s.hidden[k] = hidden[t];
                    k++;
                }
            }
        }
        return s;
    }

    /** The samples of one model: where each goes on the drawn-up texture, its texel on the gun's own, its place. */
    public static final class Samples {
        public final int count;
        /** The drawn-up texture's width (the gun's own times n). */
        public final int width;
        public final int scale;
        final int[] at;
        final int[] texel;
        final double[] pos;
        final int[] axis;
        final int[] exposure;
        final boolean[] shared;
        final boolean[] hidden;

        Samples(int count, int width, int scale) {
            this.count = count;
            this.width = width;
            this.scale = scale;
            at = new int[count];
            texel = new int[count];
            pos = new double[count * 3];
            axis = new int[count];
            exposure = new int[count];
            shared = new boolean[count];
            hidden = new boolean[count];
        }

        double pos(int k, int component) {
            return pos[k * 3 + component];
        }
    }
}
