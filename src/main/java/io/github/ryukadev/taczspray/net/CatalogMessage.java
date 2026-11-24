package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.Series;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server to client, on joining and after every {@code /reload}: the names of the server owner's series, so that a
 * labelled can's tooltip can say which series it is. Only names; what a can gives is the server's call.
 *
 * @param series series id to {language: name} ({@code ""} for a name in any language)
 */
public record CatalogMessage(Map<String, Map<String, String>> series) {
    static final int MAX_SERIES = 256;
    static final int MAX_LANGUAGES = 16;
    private static final int MAX_ID = 64;
    private static final int MAX_LANGUAGE = 8;
    private static final int MAX_NAME = 64;

    public CatalogMessage {
        if (series.size() > MAX_SERIES) {
            throw new IllegalArgumentException("too many series");
        }
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        series.forEach((id, names) -> {
            if (!Series.ID.matcher(id).matches() || names.size() > MAX_LANGUAGES) {
                throw new IllegalArgumentException("bad series " + id);
            }
            names.forEach((language, name) -> {
                if (language.length() > MAX_LANGUAGE || name.isEmpty() || name.length() > MAX_NAME) {
                    throw new IllegalArgumentException("bad name for series " + id);
                }
            });
            copy.put(id, Map.copyOf(names));
        });
        series = Map.copyOf(copy);
    }

    public static CatalogMessage of(Map<String, Series> series) {
        Map<String, Map<String, String>> names = new LinkedHashMap<>();
        series.forEach((id, s) -> names.put(id, s.names()));
        return new CatalogMessage(names);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(series.size());
        series.forEach((id, names) -> {
            buf.writeUtf(id, MAX_ID);
            buf.writeVarInt(names.size());
            names.forEach((language, name) -> {
                buf.writeUtf(language, MAX_LANGUAGE);
                buf.writeUtf(name, MAX_NAME);
            });
        });
    }

    static CatalogMessage decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SERIES) {
            throw new IllegalArgumentException("bad series count " + n);
        }
        Map<String, Map<String, String>> series = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(MAX_ID);
            int languages = buf.readVarInt();
            if (languages < 0 || languages > MAX_LANGUAGES) {
                throw new IllegalArgumentException("bad language count " + languages);
            }
            Map<String, String> names = new LinkedHashMap<>();
            for (int j = 0; j < languages; j++) {
                names.put(buf.readUtf(MAX_LANGUAGE), buf.readUtf(MAX_NAME));
            }
            series.put(id, names);
        }
        return new CatalogMessage(series);
    }
}
