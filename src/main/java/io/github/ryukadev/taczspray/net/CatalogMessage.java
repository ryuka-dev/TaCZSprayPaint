package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.CustomFinish;
import io.github.ryukadev.taczspray.CustomLook;
import io.github.ryukadev.taczspray.Series;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server to client, on joining and after every {@code /reload}: the names of the server owner's series, so that a
 * labelled can's tooltip can say which series it is, and which finishes of the owner's there are (each follows in a
 * {@link CustomLookMessage}; a client forgets those not listed). Only names; what a can gives is the server's call.
 *
 * @param series series id to {language: name} ({@code ""} for a name in any language)
 * @param custom the owner's finishes, {@code custom_<folder>}
 */
public record CatalogMessage(Map<String, Map<String, String>> series, Set<String> custom) {
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
        if (custom.size() > CustomFinish.MAX) {
            throw new IllegalArgumentException("too many custom finishes");
        }
        for (String look : custom) {
            if (!CustomLook.isValid(look)) {
                throw new IllegalArgumentException("bad custom finish " + look);
            }
        }
        custom = Set.copyOf(custom);
    }

    public static CatalogMessage of(Map<String, Series> series, Set<String> custom) {
        Map<String, Map<String, String>> names = new LinkedHashMap<>();
        series.forEach((id, s) -> names.put(id, s.names()));
        return new CatalogMessage(names, custom);
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
        buf.writeVarInt(custom.size());
        custom.forEach(look -> buf.writeUtf(look, MAX_ID));
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
        int c = buf.readVarInt();
        if (c < 0 || c > CustomFinish.MAX) {
            throw new IllegalArgumentException("bad custom finish count " + c);
        }
        Set<String> custom = new LinkedHashSet<>();
        for (int i = 0; i < c; i++) {
            custom.add(buf.readUtf(MAX_ID));
        }
        return new CatalogMessage(series, custom);
    }
}
