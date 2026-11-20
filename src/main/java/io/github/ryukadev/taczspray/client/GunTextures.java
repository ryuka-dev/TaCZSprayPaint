package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Which texture a gun is drawn with. TaCZ only knows textures per gun display, so the renderer hooks note which gun
 * stack is being drawn ({@link #enter}, {@link #exit}) and ask here whenever TaCZ reads that display's texture.
 * Render thread only.
 */
public final class GunTextures {
    /** Repaints some guns. */
    public interface Source {
        /** Cheap: whether this gun gets a texture of its own at all. */
        boolean paints(ItemStack gun);

        /** The texture for this gun, or null to keep TaCZ's own. Only asked when {@link #paints} said yes. */
        @Nullable
        ResourceLocation texture(ItemStack gun, ResourceLocation original, boolean lod);
    }

    private static final Source NONE = new Source() {
        @Override
        public boolean paints(ItemStack gun) {
            return false;
        }

        @Override
        public ResourceLocation texture(ItemStack gun, ResourceLocation original, boolean lod) {
            return null;
        }
    };

    private static Source source = NONE;
    private static ItemStack drawing = ItemStack.EMPTY;

    private GunTextures() {
    }

    public static void setSource(@Nullable Source newSource) {
        source = newSource == null ? NONE : newSource;
    }

    public static void enter(ItemStack gun) {
        drawing = gun;
    }

    public static void exit() {
        drawing = ItemStack.EMPTY;
    }

    /** The texture for the gun being drawn now; {@code original} when it has none of its own. */
    public static ResourceLocation resolve(ResourceLocation original, boolean lod) {
        if (original == null || drawing.isEmpty() || !paints(drawing)) {
            return original;
        }
        try {
            ResourceLocation replaced = source.texture(drawing, original, lod);
            return replaced == null ? original : replaced;
        } catch (RuntimeException e) {
            disable(e);
            return original;
        }
    }

    /** Whether this gun is repainted; such guns skip TaCZ's cached-mesh path, which bakes the display's texture. */
    public static boolean paints(ItemStack gun) {
        if (gun == null || gun.isEmpty()) {
            return false;
        }
        try {
            return source.paints(gun);
        } catch (RuntimeException e) {
            disable(e);
            return false;
        }
    }

    /** A broken source must not take gun rendering down with it: log once and draw TaCZ's own textures. */
    private static void disable(RuntimeException e) {
        if (source != NONE) {
            TaczSpray.LOG.error("Gun repainting failed; guns are drawn with TaCZ's own textures until restart", e);
            source = NONE;
        }
    }
}
