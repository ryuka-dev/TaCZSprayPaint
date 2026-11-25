package io.github.ryukadev.taczspray;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How a server owner's own finish looks, everything a client needs to draw and name it: the server sends it to every
 * client (players install nothing). Which guns get it and how often is {@link CustomFinish}, the server's.
 *
 * @param look       {@code custom_<folder>}
 * @param names      by language, as a series' ({@link Series#name})
 * @param full       the whole gun painted, else only its shell
 * @param side       the pattern projected from the gun's side (for patterns with a direction)
 * @param pixel      one pattern pixel's size on the gun, in model units
 * @param anim       the moving effects, none for a still finish
 * @param material   how the paint looks under a LabPBR shader pack; null for plain paint
 * @param size       the pattern's side, in pixels
 * @param rgb        the pattern, {@code 0xRRGGBB} row by row
 */
public record CustomLook(String look, Map<String, String> names, boolean full, boolean side, double pixel,
                         List<Anim> anim, @Nullable Material material, int size, int[] rgb) {
    public static final String PREFIX = "custom_";
    /** A folder name: what follows the prefix. */
    public static final Pattern FOLDER = Pattern.compile("[a-z0-9_]{1,40}");
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 128;
    public static final double MIN_PIXEL = 0.05;
    public static final double MAX_PIXEL = 2;
    public static final double DEFAULT_PIXEL = 0.25;

    /** LabPBR: smoothness 0 to 1, and whether it is a metal. */
    public record Material(double smoothness, boolean metal) {
        public Material {
            if (!(smoothness >= 0 && smoothness <= 1)) {
                throw new IllegalArgumentException("smoothness is 0 to 1");
            }
        }
    }

    public CustomLook {
        if (!isValid(look)) {
            throw new IllegalArgumentException("bad custom finish " + look);
        }
        if (names.size() > Series.MAX_LANGUAGES) {
            throw new IllegalArgumentException("at most " + Series.MAX_LANGUAGES + " languages");
        }
        names.forEach((language, name) -> {
            if (language.length() > 8 || name.isEmpty() || name.length() > Series.MAX_NAME) {
                throw new IllegalArgumentException("a name is 1 to " + Series.MAX_NAME + " characters");
            }
        });
        names = Map.copyOf(names);
        if (!(pixel >= MIN_PIXEL && pixel <= MAX_PIXEL)) {
            throw new IllegalArgumentException("\"pixel\" is " + MIN_PIXEL + " to " + MAX_PIXEL);
        }
        if (anim.size() > Anim.MAX) {
            throw new IllegalArgumentException("at most " + Anim.MAX + " effects");
        }
        anim = List.copyOf(anim);
        if (size < MIN_SIZE || size > MAX_SIZE || rgb.length != size * size) {
            throw new IllegalArgumentException("the pattern must be square, " + MIN_SIZE + " to " + MAX_SIZE
                    + " pixels a side");
        }
        rgb = rgb.clone();
    }

    public static boolean isCustom(String look) {
        return look.startsWith(PREFIX);
    }

    /** {@code custom_<folder>} with a folder name the server would read. */
    public static boolean isValid(String look) {
        return isCustom(look) && FOLDER.matcher(look.substring(PREFIX.length())).matches();
    }

    public String name(String language) {
        return Series.name(names, language, look.substring(PREFIX.length()));
    }

    /** The pattern pixel at (x, y), {@code 0xRRGGBB}. */
    public int at(int x, int y) {
        return rgb[y * size + x] & 0xFFFFFF;
    }
}
