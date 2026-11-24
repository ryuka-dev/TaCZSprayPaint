package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.Series;
import io.github.ryukadev.taczspray.net.CatalogMessage;
import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * What the server told this client about its series (names only), for tooltips. Replaced on every
 * {@link CatalogMessage}; empty until the first, and for series the server does not have (the id stands in).
 */
public final class ClientCatalog {
    private static volatile Map<String, Map<String, String>> series = Map.of();

    private ClientCatalog() {
    }

    public static void accept(CatalogMessage message) {
        series = message.series();
    }

    /** The series' name in the game's language. */
    public static String seriesName(String id) {
        return Series.name(series.getOrDefault(id, Map.of()), Minecraft.getInstance().getLanguageManager().getSelected(),
                id);
    }
}
