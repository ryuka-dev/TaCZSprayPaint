package io.github.ryukadev.taczspray.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.FinishTexture;
import net.irisshaders.iris.texture.pbr.loader.PBRTextureLoader;
import net.irisshaders.iris.texture.pbr.loader.PBRTextureLoaderRegistry;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Gives painted guns the gun pack's LabPBR maps under an Oculus shader pack, as Oculus gives the guns' own textures:
 * Oculus finds {@code _s} and {@code _n} maps only for textures read from a file, and a painted gun's texture is
 * composed in memory. The only class that touches Oculus; loaded only when an Oculus with this loader API is present.
 * Oculus owns the maps it is handed and closes them with the painted texture.
 */
public final class OculusPbr {
    private static final String REGISTRY = "net.irisshaders.iris.texture.pbr.loader.PBRTextureLoaderRegistry";
    private static final Set<String> reported = new HashSet<>();

    private OculusPbr() {
    }

    /** Registers the loader when Oculus and its loader API are there; otherwise does nothing. Client setup only. */
    public static void registerIfPresent() {
        if (!ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            Class.forName(REGISTRY, false, OculusPbr.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            TaczSpray.LOG.info("This Oculus has no texture loader API; painted guns get no LabPBR maps under shaders");
            return;
        }
        try {
            Loader.register();
            TaczSpray.LOG.info("Painted guns get the gun pack's LabPBR maps under Oculus shader packs");
        } catch (LinkageError e) {
            TaczSpray.LOG.warn("Could not hook into Oculus; painted guns get no LabPBR maps under shaders", e);
        }
    }

    /** Separate, so that nothing of Oculus is resolved before the checks above. */
    private static final class Loader implements PBRTextureLoader<FinishTexture> {
        static void register() {
            PBRTextureLoaderRegistry.INSTANCE.register(FinishTexture.class, new Loader());
        }

        @Override
        public void load(FinishTexture texture, ResourceManager rm, PBRTextureConsumer consumer) {
            try {
                NativeImage specular = texture.specular(rm);
                if (specular != null) {
                    consumer.acceptSpecularTexture(new DynamicTexture(specular));
                }
                ResourceLocation normal = texture.normal(rm);
                if (normal != null) {
                    SimpleTexture n = new SimpleTexture(normal);
                    n.load(rm);
                    consumer.acceptNormalTexture(n);
                }
            } catch (IOException | RuntimeException e) {
                if (reported.add(e.toString())) {
                    TaczSpray.LOG.warn("Could not make the LabPBR maps for a painted gun", e);
                }
            }
        }
    }
}
