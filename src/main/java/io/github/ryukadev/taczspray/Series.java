package io.github.ryukadev.taczspray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A series of finishes a server owner made, from {@code config/taczspray/series/<id>.json}: a can of this series
 * gives one of these patterns, by these grade weights (the table's when it gives none).
 *
 * @param id     the file's name, which the can carries
 * @param names  the series' name by language ({@code zh_cn}, {@code en_us}...); {@code ""} for one name in any language
 * @param looks  the patterns
 * @param grades five grade weights, commonest first, or null for the table's
 */
public record Series(String id, Map<String, String> names, Set<String> looks, @Nullable double[] grades) {
    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    static final int MAX_NAME = 64;
    static final int MAX_LANGUAGES = 16;
    static final int MAX_LOOKS = 256;

    public Series {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("series id " + id + ": lower-case letters, digits and _ only");
        }
        names = Map.copyOf(names);
        looks = Set.copyOf(looks);
        if (looks.isEmpty()) {
            throw new IllegalArgumentException("series " + id + " has no patterns");
        }
        grades = grades == null ? null : grades.clone();
    }

    public FinishTable.Pool pool() {
        return new FinishTable.Pool(looks, grades);
    }

    /** The name for a language: that language's, else English, else any, else the id. */
    public String name(String language) {
        return name(names, language, id);
    }

    public static String name(Map<String, String> names, String language, String fallback) {
        String n = names.get(language);
        if (n == null) {
            n = names.get("en_us");
        }
        if (n == null) {
            n = names.get("");
        }
        if (n == null && !names.isEmpty()) {
            n = names.values().iterator().next();
        }
        return n == null ? fallback : n;
    }

    /**
     * {"v": 1, "name": "..." or {"zh_cn": "...", ...}, "looks": ["chrome", ...], "grades": [5 weights] (optional)}.
     * Anything malformed refuses the file. Patterns are checked against the table later, when both have loaded.
     */
    public static Series parse(String id, JsonObject json) {
        if (!json.has("v") || json.get("v").getAsInt() != 1) {
            throw new IllegalArgumentException("\"v\" must be 1");
        }
        Map<String, String> names = names(json.get("name"));
        JsonArray list = json.getAsJsonArray("looks");
        if (list == null) {
            throw new IllegalArgumentException("no \"looks\" list");
        }
        Set<String> looks = new LinkedHashSet<>();
        for (JsonElement e : list) {
            String look = e.getAsString();
            if (!ID.matcher(look).matches()) {
                throw new IllegalArgumentException("pattern " + look + ": lower-case letters, digits and _ only");
            }
            looks.add(look);
        }
        if (looks.size() > MAX_LOOKS) {
            throw new IllegalArgumentException("at most " + MAX_LOOKS + " patterns");
        }
        double[] grades = null;
        if (json.has("grades")) {
            JsonArray g = json.getAsJsonArray("grades");
            if (g.size() != Finish.GRADES) {
                throw new IllegalArgumentException("\"grades\" needs " + Finish.GRADES + " weights");
            }
            grades = new double[Finish.GRADES];
            double total = 0;
            for (int i = 0; i < Finish.GRADES; i++) {
                grades[i] = g.get(i).getAsDouble();
                if (!(grades[i] >= 0 && grades[i] < 1e9)) {
                    throw new IllegalArgumentException("grade weights must be 0 or more");
                }
                total += grades[i];
            }
            if (!(total > 0)) {
                throw new IllegalArgumentException("at least one grade must weigh something");
            }
        }
        return new Series(id, names, looks, grades);
    }

    /** A name: one text for every language, or {"zh_cn": "...", "en_us": "..."}; none (null) leaves it empty. */
    static Map<String, String> names(@Nullable JsonElement name) {
        Map<String, String> names = new LinkedHashMap<>();
        if (name != null && name.isJsonPrimitive()) {
            names.put("", text(name.getAsString()));
        } else if (name != null) {
            for (Map.Entry<String, JsonElement> e : name.getAsJsonObject().entrySet()) {
                if (!e.getKey().matches("[a-z]{2,3}_[a-z]{2,3}")) {
                    throw new IllegalArgumentException("language " + e.getKey() + " (like zh_cn, en_us)");
                }
                names.put(e.getKey(), text(e.getValue().getAsString()));
            }
            if (names.size() > MAX_LANGUAGES) {
                throw new IllegalArgumentException("at most " + MAX_LANGUAGES + " languages");
            }
        }
        return names;
    }

    private static String text(String s) {
        String t = s.strip();
        if (t.isEmpty() || t.length() > MAX_NAME) {
            throw new IllegalArgumentException("a name is 1 to " + MAX_NAME + " characters");
        }
        return t;
    }
}
