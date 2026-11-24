package io.github.ryukadev.taczspray.dev;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.FinishTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Development check, run by {@link Gallery} when the game is started with {@code -Dtaczspray.gallery.pbr=true} and
 * Oculus is loaded: logs which specular texture Oculus gives the gun's own texture, the painted texture that replaces
 * it, and a vanilla texture that has none, so one can see whether painting loses the gun pack's specular map. Reads
 * Oculus by reflection; not part of the mod's behaviour.
 */
final class PbrProbe {
    static final String PROPERTY = "taczspray.gallery.pbr";
    private static final ResourceLocation NO_SPECULAR = ResourceLocation.tryBuild("minecraft", "textures/entity/pig/pig.png");

    private PbrProbe() {
    }

    static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    static void probe(ItemStack gun) {
        if (!ModList.get().isLoaded("oculus")) {
            TaczSpray.LOG.info("[pbr] Oculus is not loaded");
            return;
        }
        ResourceLocation original = TimelessAPI.getGunDisplay(gun).map(GunDisplayInstance::getModelTexture).orElse(null);
        if (original == null) {
            TaczSpray.LOG.info("[pbr] no display for {}", gun);
            return;
        }
        ResourceLocation painted = FinishTextures.instance().texture(gun, original, false);
        TaczSpray.LOG.info("[pbr] gun texture {}: {}", original, specular(original));
        TaczSpray.LOG.info("[pbr] painted texture {}: {}", painted, painted == null ? "-" : specular(painted));
        TaczSpray.LOG.info("[pbr] vanilla texture without one {}: {}", NO_SPECULAR, specular(NO_SPECULAR));
    }

    private static String specular(ResourceLocation texture) {
        try {
            int gl = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
            Class<?> manager = Class.forName("net.irisshaders.iris.texture.pbr.PBRTextureManager");
            Object instance = manager.getField("INSTANCE").get(null);
            Object holder = manager.getMethod("getOrLoadHolder", int.class).invoke(instance, gl);
            Method specularTexture = holder.getClass().getMethod("specularTexture");
            specularTexture.setAccessible(true);
            Object specular = specularTexture.invoke(holder);
            return specular.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(specular))
                    + (specular instanceof net.minecraft.client.renderer.texture.SimpleTexture ? " (read from a file)" : "");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "failed: " + e;
        }
    }
}
