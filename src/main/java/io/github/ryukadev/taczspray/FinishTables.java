package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The server's finish table, read from {@code data/taczspray/finish_table.json} with the other data packs (so a data
 * pack can replace it) and on {@code /reload}. A table that fails to read keeps the one before it; until one has
 * read, nothing can be sprayed.
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID)
public final class FinishTables extends SimplePreparableReloadListener<Optional<FinishTable>> {
    private static final ResourceLocation FILE = TaczSpray.id("finish_table.json");
    private static volatile FinishTable current = FinishTable.EMPTY;

    public static FinishTable current() {
        return current;
    }

    /** TaCZ's id for this gun, or null when the stack is not a TaCZ gun. */
    @Nullable
    public static String gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }
        ResourceLocation id = gun.getGunId(stack);
        return id == null ? null : id.toString();
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new FinishTables());
    }

    @Override
    protected Optional<FinishTable> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            TaczSpray.LOG.error("No {}: nothing can be sprayed", FILE);
            return Optional.empty();
        }
        try (InputStream in = resource.get().open()) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return Optional.of(FinishTable.parse(json));
        } catch (Exception e) {
            TaczSpray.LOG.error("Could not read {}; keeping the table before it", FILE, e);
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Optional<FinishTable> table, ResourceManager manager, ProfilerFiller profiler) {
        table.ifPresent(t -> {
            current = t;
            TaczSpray.LOG.info("{} finishes loaded", t.size());
        });
    }
}
