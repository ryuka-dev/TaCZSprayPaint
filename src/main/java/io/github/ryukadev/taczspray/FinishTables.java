package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IGun;
import io.github.ryukadev.taczspray.net.CatalogMessage;
import io.github.ryukadev.taczspray.net.CustomLookMessage;
import io.github.ryukadev.taczspray.net.Network;
import net.minecraft.network.chat.Component;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The server's finish table and what the server owner added to it. The table is read from
 * {@code data/taczspray/finish_table.json} with the other data packs (so a data pack can replace it); the owner's
 * series from {@code config/taczspray/series/*.json}, their own finishes from {@code config/taczspray/finishes/}, their
 * odds and which series a plain can draws from from {@link Settings}. All on start and on {@code /reload}. A table that
 * fails to read keeps the one before it (until one has read, nothing can be sprayed); a series or a finish that fails
 * to read is left out and logged, the others load.
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID)
public final class FinishTables extends SimplePreparableReloadListener<FinishTables.Loaded> {
    private static final ResourceLocation FILE = TaczSpray.id("finish_table.json");
    static final int MAX_SERIES = 256;
    /** The table as the data pack has it, before the owner's odds and finishes. */
    private static volatile FinishTable read = FinishTable.EMPTY;
    private static volatile FinishTable current = FinishTable.EMPTY;
    private static volatile Map<String, Series> series = Map.of();
    private static volatile Map<String, CustomFinish> custom = Map.of();
    private static volatile Plain plain = new Plain(FinishTable.Pool.ALL, null);

    /** What one reload read: the table (empty when it failed), and the series and finishes that read. */
    record Loaded(Optional<FinishTable> table, List<Series> series, List<CustomFinish> custom) {
    }

    /** What a plain can draws from: a pool, or none because the series the owner named is not there. */
    public record Plain(@Nullable FinishTable.Pool pool, @Nullable String missingSeries) {
    }

    public static FinishTable current() {
        return current;
    }

    /** The server owner's series, by id. */
    public static Map<String, Series> series() {
        return series;
    }

    /** The server owner's own finishes, by look ({@code custom_<folder>}). */
    public static Map<String, CustomFinish> custom() {
        return custom;
    }

    public static Plain plain() {
        return plain;
    }

    /**
     * A pattern's name for a message to a player: ours by the game's translation, a server owner's in the player's
     * language from its file.
     */
    public static Component lookName(String look, String language) {
        CustomFinish c = custom.get(look);
        if (c != null) {
            return Component.literal(c.look().name(language));
        }
        return CustomLook.isCustom(look) ? Component.literal(look) : Component.translatable("taczspray.pattern." + look);
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

    /**
     * A player joining, or everyone after {@code /reload}: tell the clients the series' names and which finishes of
     * the owner's there are, then send each of those (its pattern, names, effects) in a message of its own.
     */
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        List<Object> messages = new ArrayList<>();
        messages.add(CatalogMessage.of(series, custom.keySet(),
                current.ready() ? current.bandUppers() : Finish.defaultBandUppers()));
        custom.values().forEach(c -> messages.add(new CustomLookMessage(c.look())));
        for (Object message : messages) {
            if (event.getPlayer() != null) {
                Network.sendTo(event.getPlayer(), message);
            } else {
                Network.sendToAll(message);
            }
        }
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Loaded(readTable(manager), readSeries(folder().resolve("series")),
                CustomFinish.readAll(folder().resolve("finishes")));
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
            read = t;
            TaczSpray.LOG.info("{} finishes loaded", t.size());
        });
        FinishTable table = read;
        try {
            table = table.withOdds(Settings.grades(), Settings.wearBands());
        } catch (IllegalArgumentException e) {
            TaczSpray.LOG.error("settings.toml odds not used: {}", e.getMessage());
        }
        Map<String, Set<String>> skipped = new TreeMap<>();
        table = table.withCustom(loaded.custom(), skipped);
        skipped.forEach((look, guns) -> TaczSpray.LOG.warn("Custom finish {} is not on {}: no finish of ours is on "
                + "those guns (or the gun's name and the folder's are over 64 characters together)", look, guns));
        Map<String, CustomFinish> mine = new LinkedHashMap<>();
        Set<String> placed = table.looks();
        for (CustomFinish c : loaded.custom()) {
            if (placed.contains(c.look().look())) {
                mine.put(c.look().look(), c);
            } else {
                TaczSpray.LOG.error("Custom finish {} left out: it is on no gun", c.look().look());
            }
        }
        if (!mine.isEmpty()) {
            TaczSpray.LOG.info("{} custom finishes loaded: {}", mine.size(), mine.keySet());
        }
        current = table;
        custom = Collections.unmodifiableMap(mine);
        series = check(loaded.series(), table);
        plain = plainOf(Settings.plainSeries(), series, mine.values(), table);
    }

    /** A plain can's pool: the series the owner named, else every finish but the owner's kept out of plain cans. */
    static Plain plainOf(@Nullable String plainSeries, Map<String, Series> series, Collection<CustomFinish> custom,
                         FinishTable table) {
        if (plainSeries != null) {
            Series s = series.get(plainSeries);
            if (s == null) {
                TaczSpray.LOG.error("settings.toml: plain cans draw from series {}, which is not there: they spray "
                        + "nothing until it is", plainSeries);
                return new Plain(null, plainSeries);
            }
            return new Plain(s.pool(), null);
        }
        Set<String> kept = new HashSet<>();
        custom.stream().filter(c -> !c.plainCans()).forEach(c -> kept.add(c.look().look()));
        if (kept.isEmpty()) {
            return new Plain(FinishTable.Pool.ALL, null);
        }
        Set<String> looks = new HashSet<>(table.looks());
        looks.removeAll(kept);
        return new Plain(new FinishTable.Pool(looks, null), null);
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
