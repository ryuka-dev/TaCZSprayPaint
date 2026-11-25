package io.github.ryukadev.taczspray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A server owner's own finish, from {@code config/taczspray/finishes/<folder>/}: {@code pattern.png} and
 * {@code finish.json}. It goes on the guns that have our finishes, painted where ours of the same cover are.
 *
 * @param look      how it looks, which every client is sent
 * @param grade     1 to 5
 * @param wearMin   the least wear it is rolled with
 * @param wearMax   the most
 * @param guns      TaCZ gun ids, or null for every gun that has finishes
 * @param plainCans whether a plain spray can may give it; when not, only a series or a labelled can does
 */
public record CustomFinish(CustomLook look, int grade, double wearMin, double wearMax, @Nullable Set<String> guns,
                           boolean plainCans) {
    public static final int MAX = 64;
    /** Folders looked at, at most: broken or parked ones do not count against {@link #MAX}, but there is an end. */
    static final int MAX_FOLDERS = 1024;
    static final int MAX_GUNS = 256;
    /** A pattern file larger than this is not read. */
    static final long MAX_FILE = 1 << 20;

    public CustomFinish {
        if (grade < 1 || grade > Finish.GRADES) {
            throw new IllegalArgumentException("\"grade\" is 1 to " + Finish.GRADES);
        }
        if (!(wearMin >= 0 && wearMin <= wearMax && wearMax <= 1)) {
            throw new IllegalArgumentException("\"wear\" is [least, most], both 0 to 1");
        }
        guns = guns == null ? null : Set.copyOf(guns);
    }

    /**
     * {"v": 1, "name", "grade", "cover": "shell" | "full", "wear": [least, most], "guns": [...], "projection":
     * "side", "pixel", "anim": [...], "material": {"smoothness", "metal"}, "plain_cans"}; only {@code v} and
     * {@code grade} are needed. Anything malformed refuses the finish.
     *
     * @param rgb the pattern, square, {@code 0xRRGGBB} row by row
     */
    public static CustomFinish parse(String folder, JsonObject json, int size, int[] rgb) {
        if (!CustomLook.FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("the folder's name: lower-case letters, digits and _ only, at most 40");
        }
        if (!json.has("v") || json.get("v").getAsInt() != 1) {
            throw new IllegalArgumentException("\"v\" must be 1");
        }
        if (!json.has("grade")) {
            throw new IllegalArgumentException("no \"grade\" (1 to " + Finish.GRADES + ")");
        }
        String cover = json.has("cover") ? json.get("cover").getAsString() : "shell";
        if (!cover.equals("shell") && !cover.equals("full")) {
            throw new IllegalArgumentException("\"cover\" is \"shell\" or \"full\"");
        }
        String projection = json.has("projection") ? json.get("projection").getAsString() : "box";
        if (!projection.equals("box") && !projection.equals("side")) {
            throw new IllegalArgumentException("\"projection\" is \"box\" or \"side\"");
        }
        double wearMin = 0;
        double wearMax = 1;
        if (json.has("wear")) {
            JsonArray w = json.getAsJsonArray("wear");
            if (w.size() != 2) {
                throw new IllegalArgumentException("\"wear\" is [least, most]");
            }
            wearMin = w.get(0).getAsDouble();
            wearMax = w.get(1).getAsDouble();
        }
        Set<String> guns = null;
        if (json.has("guns")) {
            guns = new LinkedHashSet<>();
            for (JsonElement e : json.getAsJsonArray("guns")) {
                String gun = e.getAsString();
                if (!gun.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    throw new IllegalArgumentException("gun " + gun + ": a TaCZ gun id, like tacz:ak47");
                }
                guns.add(gun);
            }
            if (guns.isEmpty() || guns.size() > MAX_GUNS) {
                throw new IllegalArgumentException("\"guns\" lists 1 to " + MAX_GUNS + " guns (leave it out for all)");
            }
        }
        List<Anim> anim = new ArrayList<>();
        if (json.has("anim")) {
            for (JsonElement e : json.getAsJsonArray("anim")) {
                anim.add(Anim.parse(e.getAsJsonObject()));
            }
        }
        CustomLook.Material material = null;
        if (json.has("material")) {
            JsonObject m = json.getAsJsonObject("material");
            material = new CustomLook.Material(m.has("smoothness") ? m.get("smoothness").getAsDouble() : 0.35,
                    m.has("metal") && m.get("metal").getAsBoolean());
        }
        CustomLook look = new CustomLook(CustomLook.PREFIX + folder, Series.names(json.get("name")),
                cover.equals("full"), projection.equals("side"),
                json.has("pixel") ? json.get("pixel").getAsDouble() : CustomLook.DEFAULT_PIXEL, anim, material, size,
                rgb);
        return new CustomFinish(look, json.get("grade").getAsInt(), wearMin, wearMax, guns,
                !json.has("plain_cans") || json.get("plain_cans").getAsBoolean());
    }

    /** Every finish in the folder that reads, by folder name; one that does not is left out and logged. */
    static List<CustomFinish> readAll(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> folders;
        try (Stream<Path> list = Files.list(dir)) {
            folders = list.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            TaczSpray.LOG.error("Could not list {}; no custom finishes", dir, e);
            return List.of();
        }
        List<CustomFinish> out = new ArrayList<>();
        for (Path folder : folders.stream().limit(MAX_FOLDERS).toList()) {
            Optional<CustomFinish> f = read(folder);
            if (f.isEmpty()) {
                continue;
            }
            if (out.size() == MAX) {
                TaczSpray.LOG.error("More than {} custom finishes in {}; {} and those after it (by name) are left out",
                        MAX, dir, folder.getFileName());
                break;
            }
            out.add(f.get());
        }
        return out;
    }

    /** The image, its size checked from its header before any pixel is decoded (a small file can claim a huge one). */
    private static BufferedImage readImage(Path png) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(png.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("pattern.png is not a PNG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w != h || w < CustomLook.MIN_SIZE || w > CustomLook.MAX_SIZE) {
                    throw new IllegalArgumentException("pattern.png is " + w + "x" + h + "; it must be square, "
                            + CustomLook.MIN_SIZE + " to " + CustomLook.MAX_SIZE + " pixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Optional<CustomFinish> read(Path folder) {
        try {
            JsonObject json;
            try (Reader in = Files.newBufferedReader(folder.resolve("finish.json"), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(in).getAsJsonObject();
            }
            Path png = folder.resolve("pattern.png");
            if (!Files.isRegularFile(png)) {
                throw new IllegalArgumentException("no pattern.png");
            }
            if (Files.size(png) > MAX_FILE) {
                throw new IllegalArgumentException("pattern.png is over 1 MB");
            }
            BufferedImage image = readImage(png);
            int size = image.getWidth();
            int[] rgb = image.getRGB(0, 0, size, size, null, 0, size);
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] &= 0xFFFFFF;
            }
            return Optional.of(parse(folder.getFileName().toString(), json, size, rgb));
        } catch (Exception e) {
            TaczSpray.LOG.error("Custom finish {} left out: {}", folder, e.getMessage() != null ? e.getMessage() : e);
            return Optional.empty();
        }
    }
}
