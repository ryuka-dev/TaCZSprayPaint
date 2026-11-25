package io.github.ryukadev.taczspray.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.ryukadev.taczspray.CustomLook;
import io.github.ryukadev.taczspray.Series;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.net.CatalogMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the server told this client: its series' names, and the server owner's own finishes (how each looks, and its
 * pattern as a texture for the reveal). Replaced by every {@link CatalogMessage} and the finishes that follow it; empty
 * until the first. Client thread only for the finishes (they make textures).
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID, value = Dist.CLIENT)
public final class ClientCatalog {
    private static volatile Map<String, Map<String, String>> series = Map.of();
    private static Set<String> listed = Set.of();
    private static final Map<String, CustomLook> custom = new HashMap<>();
    private static final Map<String, ResourceLocation> patterns = new HashMap<>();

    private ClientCatalog() {
    }

    public static void accept(CatalogMessage message) {
        series = message.series();
        listed = message.custom();
        boolean dropped = custom.keySet().removeIf(look -> !listed.contains(look));
        patterns.entrySet().removeIf(e -> {
            if (listed.contains(e.getKey())) {
                return false;
            }
            Minecraft.getInstance().getTextureManager().release(e.getValue());
            return true;
        });
        if (dropped) {
            FinishTextures.instance().forgetSoon();
        }
    }

    /** One of the owner's finishes, as the server sent it; one the last catalog did not list is ignored. */
    public static void accept(CustomLook look) {
        if (!listed.contains(look.look())) {
            TaczSpray.LOG.warn("The server sent custom finish {} without listing it; ignored", look.look());
            return;
        }
        custom.put(look.look(), look);
        NativeImage image = new NativeImage(look.size(), look.size(), false);
        for (int y = 0; y < look.size(); y++) {
            for (int x = 0; x < look.size(); x++) {
                int c = look.at(x, y);
                // NativeImage keeps ABGR
                image.setPixelRGBA(x, y, 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF));
            }
        }
        ResourceLocation id = TaczSpray.id("custom_pattern/" + look.look());
        // registering again under the same name closes the texture before it
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        patterns.put(look.look(), id);
        FinishTextures.instance().forgetSoon();
    }

    /** Left the server (or the world): nothing it sent is kept for the next one. */
    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        series = Map.of();
        listed = Set.of();
        custom.clear();
        patterns.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        patterns.clear();
        FinishTextures.instance().forgetSoon();
    }

    /** The series' name in the game's language. */
    public static String seriesName(String id) {
        return Series.name(series.getOrDefault(id, Map.of()), language(), id);
    }

    /** A pattern's name: ours by the game's translation, the owner's as the server sent it. */
    public static Component lookName(String look) {
        if (!CustomLook.isCustom(look)) {
            return Component.translatable("taczspray.pattern." + look);
        }
        CustomLook c = custom.get(look);
        return Component.literal(c != null ? c.name(language()) : look);
    }

    /** The owner's finish, or null when it is ours or the server did not send it. */
    @Nullable
    public static CustomLook custom(String look) {
        return custom.get(look);
    }

    /** The pattern of a finish as one texture, for the reveal's cells. */
    public static ResourceLocation pattern(String finish, String look) {
        if (CustomLook.isCustom(look)) {
            ResourceLocation id = patterns.get(look);
            return id != null ? id : MissingTextureAtlasSprite.getLocation();
        }
        return TaczSpray.id("finishes/" + finish + "/pattern.png");
    }

    private static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }
}
