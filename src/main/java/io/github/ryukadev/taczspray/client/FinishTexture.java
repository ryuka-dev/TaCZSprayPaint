package io.github.ryukadev.taczspray.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.Optional;

/**
 * A painted gun's texture: the gun's own texture drawn up {@code scale} times with the paint on it. Besides the
 * colours it remembers where the paint shows, so that a shader pack reading LabPBR maps (through Oculus) can be given a
 * specular map to match: the gun pack's own {@code _s} map, drawn up the same way, with the paint's material wherever
 * paint shows. Bare metal where the paint has worn through keeps the gun pack's values. A gun pack without an
 * {@code _s} map gets none for its painted guns either, as before painting.
 */
public final class FinishTexture extends DynamicTexture {
    private final ResourceLocation original;
    private final BitSet paint;
    private final Material material;

    /** The paint's look under a LabPBR shader pack. */
    public record Material(double smoothness, boolean metal) {
        /** Gun paint: fairly matte, not metal. */
        public static final Material PAINT = new Material(0.35, false);
        /** LabPBR: an F0 of about 4 %, a dielectric's. */
        static final int DIELECTRIC_F0 = 10;
        /** LabPBR: 255 in the green channel is a metal that reflects in its own colour. */
        static final int ALBEDO_METAL = 255;

        public Material {
            if (!(smoothness >= 0 && smoothness <= 1)) {
                throw new IllegalArgumentException("smoothness " + smoothness);
            }
        }

        /** Red smoothness, green F0 or metal, blue porosity none, alpha no emission; as ARGB. */
        int labPbr() {
            int r = (int) Math.round(smoothness * 255);
            int g = metal ? ALBEDO_METAL : DIELECTRIC_F0;
            return 0xFF000000 | r << 16 | g << 8;
        }
    }

    FinishTexture(NativeImage image, ResourceLocation original, BitSet paint, Material material) {
        super(image);
        this.original = original;
        this.paint = paint;
        this.material = material;
    }

    /** The gun pack's {@code <texture>_s.png} drawn up with the paint's material on it, or null when it has none. */
    @Nullable
    public NativeImage specular(ResourceManager rm) throws IOException {
        Optional<Resource> resource = rm.getResource(suffixed(original, "_s"));
        NativeImage pixels = getPixels();
        if (resource.isEmpty() || pixels == null) {
            return null;
        }
        int width = pixels.getWidth();
        int height = pixels.getHeight();
        try (InputStream in = resource.get().open(); NativeImage own = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int[] argb = specular(argb(own), own.getWidth(), own.getHeight(), width, height, paint, material);
            NativeImage out = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    out.setPixelRGBA(x, y, abgr(argb[y * width + x]));
                }
            }
            return out;
        }
    }

    /** The gun pack's {@code <texture>_n.png}, unchanged (paint does not change the surface's shape), or null. */
    @Nullable
    public ResourceLocation normal(ResourceManager rm) {
        ResourceLocation n = suffixed(original, "_n");
        return rm.getResource(n).isPresent() ? n : null;
    }

    /**
     * The specular map, {@code width} by {@code height}, ARGB: the gun pack's map ({@code ownWidth} by
     * {@code ownHeight}) stretched to fit, nearest texel, then the material wherever {@code paint} is set.
     */
    static int[] specular(int[] own, int ownWidth, int ownHeight, int width, int height, BitSet paint,
                          Material material) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sy = (int) ((long) y * ownHeight / height);
            for (int x = 0; x < width; x++) {
                out[y * width + x] = own[sy * ownWidth + (int) ((long) x * ownWidth / width)];
            }
        }
        int value = material.labPbr();
        for (int at = paint.nextSetBit(0); at >= 0 && at < out.length; at = paint.nextSetBit(at + 1)) {
            out[at] = value;
        }
        return out;
    }

    static ResourceLocation suffixed(ResourceLocation texture, String suffix) {
        String path = texture.getPath();
        String withSuffix = path.endsWith(".png") ? path.substring(0, path.length() - 4) + suffix + ".png" : path + suffix;
        return ResourceLocation.tryBuild(texture.getNamespace(), withSuffix);
    }

    private static int[] argb(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = abgr(image.getPixelRGBA(x, y));
            }
        }
        return out;
    }

    /** ARGB to NativeImage's ABGR and back: the same swap. */
    private static int abgr(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }
}
