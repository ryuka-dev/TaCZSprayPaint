package io.github.ryukadev.taczspray;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The language files: English is the source. English and Simplified Chinese have every string; another language may
 * leave some out (the game shows the English), but what it has must match: no key English lacks, the same
 * arguments, nothing empty.
 */
class LangTest {
    private static final Path DIR = Path.of("src/main/resources/assets/taczspray/lang");
    private static final Set<String> COMPLETE = Set.of("en_us", "zh_cn");
    /** The game's own argument syntax (TranslatableContents): %s, %1$s, %%. */
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z]{2,3}");

    @Test
    void everyLanguageMatchesEnglish() throws IOException {
        Map<String, String> english = read(DIR.resolve("en_us.json"));
        Map<String, Integer> missing = new TreeMap<>();
        List<String> wrong = new ArrayList<>();
        for (Path file : files()) {
            String language = file.getFileName().toString().replace(".json", "");
            assertTrue(LANGUAGE.matcher(language).matches(), file + ": not a language code the game uses");
            Map<String, String> strings = read(file);
            for (Map.Entry<String, String> e : strings.entrySet()) {
                String source = english.get(e.getKey());
                if (source == null) {
                    wrong.add(language + ": " + e.getKey() + " is not in English");
                } else if (e.getValue().isBlank()) {
                    wrong.add(language + ": " + e.getKey() + " is empty");
                } else if (!args(source).equals(args(e.getValue()))) {
                    wrong.add(language + ": " + e.getKey() + " has arguments " + args(e.getValue())
                            + ", English " + args(source));
                }
            }
            int lacks = (int) english.keySet().stream().filter(k -> !strings.containsKey(k)).count();
            if (lacks > 0) {
                if (COMPLETE.contains(language)) {
                    wrong.add(language + ": lacks " + lacks + " strings");
                }
                missing.put(language, lacks);
            }
        }
        if (!missing.isEmpty()) {
            System.out.println("[lang] shown in English for now: " + missing);
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void theArgumentsAreReadAsTheGameReadsThem() {
        assertEquals(Set.of(1, 2), args("Gave %1$s to %2$s"));
        assertEquals(Set.of(1, 2), args("%2$s : %1$s"));
        assertEquals(Set.of(1, 2), args("%s and %s"));
        assertEquals(Set.of(), args("100%% sure"));
    }

    private static List<Path> files() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** The argument numbers a string uses; fails on one the game would not format. */
    private static Set<Integer> args(String s) {
        Set<Integer> used = new TreeSet<>();
        Matcher m = ARG.matcher(s);
        int next = 0;
        while (m.find()) {
            String kind = m.group(2);
            if (kind.equals("%")) {
                continue;
            }
            if (!kind.equals("s")) {
                fail("\"" + s + "\": only %s formats");
            }
            used.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : ++next);
        }
        return used;
    }

    /** A flat object of strings; a key given twice fails (a plain parse would keep the last one quietly). */
    private static Map<String, String> read(Path file) throws IOException {
        Map<String, String> strings = new LinkedHashMap<>();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8); JsonReader json = new JsonReader(r)) {
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING) {
                    fail(file + ": " + key + " is not a string");
                }
                if (strings.put(key, json.nextString()) != null) {
                    fail(file + ": " + key + " twice");
                }
            }
            json.endObject();
            assertEquals(JsonToken.END_DOCUMENT, json.peek(), file + ": more after the object");
        }
        return strings;
    }
}
