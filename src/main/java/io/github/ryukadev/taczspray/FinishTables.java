package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IGun;
import io.github.ryukadev.taczspray.net.CatalogMessage;
import io.github.ryukadev.taczspray.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The server's finish table and the server owner's series. The table is read from
 * {@code data/taczspray/finish_table.json} with the other data packs (so a data pack can replace it); the series from
 * {@code config/taczspray/series/*.json}. Both on start and on {@code /reload}. A table that fails to read keeps the
 * one before it (until one has read, nothing can be sprayed); a series file that fails to read is left out and logged,
 * the others load.
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID)
public final class FinishTables extends SimplePreparableReloadListener<FinishTables.Loaded> {
    private static final ResourceLocation FILE = TaczSpray.id("finish_table.json");
    static final int MAX_SERIES = 256;
    private static volatile FinishTable current = FinishTable.EMPTY;
    private static volatile Map<String, Series> series = Map.of();

    /** What one reload read: the table (empty when it failed) and the series files that read. */
    record Loaded(Optional<FinishTable> table, List<Series> series) {
    }

    public static FinishTable current() {
        return current;
    }

    /** The server owner's series, by id. */
    public static Map<String, Series> series() {
        return series;
    }

    /** Where the server owner's files live: {@code config/taczspray}. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(TaczSpray.MOD_ID);
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

    /** A player joining, or everyone after {@code /reload}: tell the clients the series' names. */
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        CatalogMessage message = CatalogMessage.of(series);
        if (event.getPlayer() != null) {
            Network.sendTo(event.getPlayer(), message);
        } else {
            Network.sendToAll(message);
        }
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Loaded(readTable(manager), readSeries(folder().resolve("series")));
    }

    private static Optional<FinishTable> readTable(ResourceManager manager) {
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

    static List<Series> readSeries(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().limit(MAX_SERIES)
                    .map(FinishTables::readOneSeries).flatMap(Optional::stream).toList();
        } catch (IOException e) {
            TaczSpray.LOG.error("Could not list {}; no series", dir, e);
            return List.of();
        }
    }

    private static Optional<Series> readOneSeries(Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - ".json".length());
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Optional.of(Series.parse(id, JsonParser.parseReader(in).getAsJsonObject()));
        } catch (Exception e) {
            TaczSpray.LOG.error("Series {} left out: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        loaded.table().ifPresent(t -> {
            current = t;
            TaczSpray.LOG.info("{} finishes loaded", t.size());
        });
        series = check(loaded.series(), current);
    }

    /** The series whose patterns the table knows; unknown patterns are dropped, a series left with none is too. */
    static Map<String, Series> check(List<Series> read, FinishTable table) {
        Set<String> known = table.looks();
        Map<String, Series> out = new TreeMap<>();
        for (Series s : read) {
            Set<String> looks = new HashSet<>(s.looks());
            looks.retainAll(known);
            if (looks.size() < s.looks().size()) {
                Set<String> unknown = new HashSet<>(s.looks());
                unknown.removeAll(known);
                TaczSpray.LOG.warn("Series {}: no such patterns {}", s.id(), unknown);
            }
            if (looks.isEmpty()) {
                TaczSpray.LOG.error("Series {} left out: none of its patterns exist", s.id());
                continue;
            }
            out.put(s.id(), new Series(s.id(), s.names(), looks, s.grades()));
        }
        if (!out.isEmpty()) {
            TaczSpray.LOG.info("{} series loaded: {}", out.size(), out.keySet());
        }
        return Map.copyOf(out);
    }
}
