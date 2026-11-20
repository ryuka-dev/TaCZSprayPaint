package io.github.ryukadev.taczspray.client;

/**
 * A tiny extra inflate for the n-th cube of a model: 0 to 0.0009 pixels in 16 steps. Faces of different cubes that lie
 * in one plane otherwise sit at exactly the same depth and flicker against each other, which a finish that paints them
 * differently makes obvious everywhere. With a fixed step in between, the same face always wins. The steps stay below
 * the 0.001 that model makers leave between parts, so they never close a gap that was there on purpose.
 */
public final class DepthNudge {
    private DepthNudge() {
    }

    public static float forCube(int cube) {
        return ((cube * 0x9E3779B1) >>> 28) * 6.0e-5f;
    }
}
