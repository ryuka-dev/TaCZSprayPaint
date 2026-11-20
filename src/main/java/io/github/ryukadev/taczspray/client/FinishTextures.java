package io.github.ryukadev.taczspray.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws each painted gun with its own texture: the gun's texture from the player's game, with the finish's pattern,
 * wear and scratches composited on top, by the numbers the server rolled for that gun ({@link Finish}).
 *
 * <p>The finish's data comes from this mod's resources, {@code taczspray:finishes/<finish>/}: {@code pattern.png},
 * {@code finish.json}, and the painted texels {@code main.bin}, {@code lod.bin} there or, when {@code finish.json}
 * names a directory under {@code texels}, in the shared {@code finishes/<texels>/} that every finish on that gun and
 * region uses. TaCZ's own textures are never shipped; they are read from the player's game.
 *
 * <p>A moving finish ({@code anim}) keeps what the wear made of it and repaints only its painted texels, at most every
 * {@value #FRAME_MS} ms and only on the full-detail model, uploading the box they lie in. New textures are composed one
 * at a time, at most one every {@value #COMPOSE_GAP_MS} ms, so that many guns coming into view together do not all
 * cost the same frame.
 *
 * <p>Textures are kept for the {@value #KEPT} guns drawn last and released after; a full-detail one is as large as the
 * gun's texture drawn up (4 MB for a 1024 square). Anything missing or wrong (no data, a texture of another size, a
 * newer format) draws the gun's own texture and is logged once. Render thread only.
 */
public final class FinishTextures implements GunTextures.Source, ResourceManagerReloadListener {
    static final int KEPT = 24;
    static final long FRAME_MS = 50;
    /** At most one new texture composed in this many milliseconds (about a frame at 60 fps). */
    static final long COMPOSE_GAP_MS = 15;
    private static final String DATA = "finishes/";

    private record Key(String finish, ResourceLocation original, boolean lod, long wear, int pattern, int wearNo) {
    }

    /** One finish's data; either model may be missing (a gun without a LOD model). */
    private record FinishData(@Nullable SkinTexels main, @Nullable SkinTexels lod, int[] tile, int tileSize,
                              double pixel, double lodPixel, List<SkinAlgo.Effect> anim, boolean side) {
    }

    /** A composed texture; a moving one also keeps what it needs to repaint. */
    private record Made(ResourceLocation id, @Nullable Moving moving) {
    }

    /** A moving finish's texture: the prepared texels, the image to paint into, and the box they lie in. */
    private static final class Moving {
        final SkinAlgo.Prepared prepared;
        final int[] tile;
        final int tileSize;
        final List<SkinAlgo.Effect> anim;
        final DynamicTexture texture;
        final int[] argb;
        final int width;
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        long paintedAt;

        Moving(SkinAlgo.Prepared prepared, int[] tile, int tileSize, List<SkinAlgo.Effect> anim, DynamicTexture texture,
               int[] argb, int width) {
            this.prepared = prepared;
            this.tile = tile;
            this.tileSize = tileSize;
            this.anim = anim;
            this.texture = texture;
            this.argb = argb;
            this.width = width;
            int ax = Integer.MAX_VALUE;
            int ay = Integer.MAX_VALUE;
            int bx = -1;
            int by = -1;
            for (int k = 0; k < prepared.n; k++) {
                int x = prepared.at[k] % width;
                int y = prepared.at[k] / width;
                ax = Math.min(ax, x);
                ay = Math.min(ay, y);
                bx = Math.max(bx, x);
                by = Math.max(by, y);
            }
            this.x0 = ax;
            this.y0 = ay;
            this.x1 = bx;
            this.y1 = by;
        }

        void repaint(long now) {
            if (now - paintedAt < FRAME_MS || x1 < 0) {
                return;
            }
            paintedAt = now;
            SkinAlgo.paint(prepared, tile, tileSize,
                    new SkinAlgo.Frame(anim, now / 1000.0, prepared.zmin, prepared.zmax), argb);
            NativeImage image = texture.getPixels();
            if (image == null) {
                return;
            }
            for (int k = 0; k < prepared.n; k++) {
                int at = prepared.at[k];
                image.setPixelRGBA(at % width, at / width, abgr(argb[at]));
            }
            texture.bind();
            image.upload(0, x0, y0, x0, y0, x1 - x0 + 1, y1 - y0 + 1, false, false, false, false);
        }
    }

    private static final FinishTextures INSTANCE = new FinishTextures();
    private static final ResourceLocation NONE = TaczSpray.id("finish/none");
    private static final Made NOTHING = new Made(NONE, null);

    private final Map<Key, Made> made = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<String, Optional<FinishData>> data = new HashMap<>();
    /** The texels that many finishes share (one gun's region, every pattern on it), by directory. */
    private final Map<String, SkinTexels[]> shared = new HashMap<>();
    private final Map<ResourceLocation, Optional<int[][]>> bases = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> reported = new HashSet<>();
    private int next;
    private long composedAt;

    private FinishTextures() {
    }

    public static FinishTextures instance() {
        return INSTANCE;
    }

    @Override
    public boolean paints(ItemStack gun) {
        return Finish.of(gun) != null;
    }

    @Nullable
    @Override
    public ResourceLocation texture(ItemStack gun, ResourceLocation original, boolean lod) {
        Finish finish = Finish.of(gun);
        if (finish == null || !Minecraft.getInstance().isSameThread()) {
            return null;
        }
        Key key = new Key(finish.id(), original, lod, Double.doubleToLongBits(finish.wear()), finish.pattern(),
                finish.wearNo());
        Made found = made.get(key);
        long now = Util.getMillis();
        if (found == null) {
            // one new texture a frame at most: many guns seen at once are composed over the next frames instead of
            // all in one; until its turn a gun is drawn with its own texture
            if (now - composedAt < COMPOSE_GAP_MS) {
                return null;
            }
            composedAt = now;
            found = make(key, finish.wear());
            made.put(key, found);
            evict();
        }
        // a moving finish moves on the full-detail model only: at a distance the low-detail one stays still
        if (found.moving() != null && !lod) {
            found.moving().repaint(now);
        }
        return found.id() == NONE ? null : found.id();
    }

    private static int abgr(int argb) {
        // NativeImage keeps ABGR
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    private Made make(Key key, double wear) {
        Optional<FinishData> finish = data.computeIfAbsent(key.finish(), this::load);
        if (finish.isEmpty()) {
            return NOTHING;
        }
        SkinTexels texels = key.lod() ? finish.get().lod() : finish.get().main();
        if (texels == null) {
            report(key.finish() + (key.lod() ? " (lod)" : ""), "the finish has no texels for this model");
            return NOTHING;
        }
        Optional<int[][]> base = bases.computeIfAbsent(key.original(), this::readTexture);
        while (bases.size() > 8) {
            bases.remove(bases.keySet().iterator().next());
        }
        if (base.isEmpty()) {
            return NOTHING;
        }
        int width = base.get()[1][0];
        int height = base.get()[1][1];
        if (width != texels.width || height != texels.height) {
            report(key.finish(), "texture " + key.original() + " is " + width + "x" + height
                    + ", the finish was made for " + texels.width + "x" + texels.height);
            return NOTHING;
        }
        FinishData d = finish.get();
        // the gun's texture drawn up n times: the paint in samples of about a quarter of a model unit, the rest of
        // the gun as it was; TaCZ takes a texture's coordinates as fractions, so a larger one fits the same model
        int n = texels.scale;
        SkinAlgo.Prepared prepared = SkinAlgo.prepare(base.get()[0], width, texels.expand(), texels.texelSize / n,
                d.tileSize(), key.lod() ? d.lodPixel() : d.pixel(), SkinAlgo.strHash(key.finish()), wear,
                key.pattern(), key.wearNo(), d.side());
        int[] argb = SkinAlgo.drawUp(base.get()[0], width, height, n);
        SkinAlgo.paint(prepared, d.tile(), d.tileSize(), null, argb);
        width *= n;
        height *= n;
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, abgr(argb[y * width + x]));
            }
        }
        ResourceLocation id = TaczSpray.id("finish/" + next++);
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        Moving moving = d.anim().isEmpty() ? null
                : new Moving(prepared, d.tile(), d.tileSize(), d.anim(), texture, argb, width);
        return new Made(id, moving);
    }

    private void evict() {
        while (made.size() > KEPT) {
            Map.Entry<Key, Made> eldest = made.entrySet().iterator().next();
            made.remove(eldest.getKey());
            if (eldest.getValue().id() != NONE) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
            }
        }
    }

    private Optional<FinishData> load(String finish) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        String dir = DATA + finish + "/";
        try {
            Optional<Resource> meta = rm.getResource(TaczSpray.id(dir + "finish.json"));
            if (meta.isEmpty()) {
                report(finish, "no " + TaczSpray.MOD_ID + ":" + dir + "finish.json");
                return Optional.empty();
            }
            JsonObject json;
            try (InputStream in = meta.get().open()) {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (json.get("v").getAsInt() != 1) {
                report(finish, "finish.json version " + json.get("v") + " is newer than this mod reads");
                return Optional.empty();
            }
            int[][] tile = readImage(rm, TaczSpray.id(dir + "pattern.png"))
                    .orElseThrow(() -> new IOException("no pattern.png"));
            // the texels: the finish's own, or a directory every finish on that gun and region shares
            SkinTexels[] models;
            if (json.has("texels")) {
                String from = json.get("texels").getAsString();
                if (!from.matches("[a-z0-9_]+")) {
                    throw new IOException("bad texels directory " + from);
                }
                String sdir = DATA + from + "/";
                models = shared.get(from);
                if (models == null) {
                    models = new SkinTexels[]{texels(rm, sdir + "main.bin"), texels(rm, sdir + "lod.bin")};
                    shared.put(from, models);
                }
            } else {
                models = new SkinTexels[]{texels(rm, dir + "main.bin"), texels(rm, dir + "lod.bin")};
            }
            return Optional.of(new FinishData(models[0], models[1], tile[0], tile[1][0], json.get("pixel").getAsDouble(),
                    json.get("lod_pixel").getAsDouble(), anim(json),
                    json.has("projection") && "side".equals(json.get("projection").getAsString())));
        } catch (IOException | RuntimeException e) {
            report(finish, e.toString());
            return Optional.empty();
        }
    }

    /** A moving finish's effects; none for a still one. A bad list is refused with the rest of the finish. */
    private static List<SkinAlgo.Effect> anim(JsonObject json) {
        if (!json.has("anim")) {
            return List.of();
        }
        List<SkinAlgo.Effect> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("anim")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new SkinAlgo.Effect(o.get("type").getAsString(), num(o, "speed"), num(o, "period"), num(o, "amount"),
                    num(o, "degrees"), num(o, "width"), num(o, "strength")));
        }
        return List.copyOf(out);
    }

    private static double num(JsonObject o, String name) {
        return o.has(name) ? o.get(name).getAsDouble() : 0;
    }

    @Nullable
    private static SkinTexels texels(ResourceManager rm, String path) throws IOException {
        Optional<Resource> r = rm.getResource(TaczSpray.id(path));
        if (r.isEmpty()) {
            return null;
        }
        try (InputStream in = r.get().open()) {
            return SkinTexels.read(in);
        }
    }

    /**
     * A gun's own texture, from the player's game. TaCZ binds the file's own location ("tacz:textures/gun/uv/x.png":
     * its display loader turns "tacz:gun/uv/x" into that), so it is read as it is.
     */
    private Optional<int[][]> readTexture(ResourceLocation texture) {
        try {
            Optional<int[][]> image = readImage(Minecraft.getInstance().getResourceManager(), texture);
            if (image.isEmpty()) {
                report(texture.toString(), "no such texture among the resources");
            }
            return image;
        } catch (IOException e) {
            report(texture.toString(), e.toString());
            return Optional.empty();
        }
    }

    /** {pixels as ARGB row by row, {width, height}}, or empty when there is no such resource. */
    private static Optional<int[][]> readImage(ResourceManager rm, ResourceLocation location) throws IOException {
        Optional<Resource> r = rm.getResource(location);
        if (r.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = img.getPixelRGBA(x, y);
                    argb[y * w + x] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
                }
            }
            return Optional.of(new int[][]{argb, {w, h}});
        }
    }

    private void report(String what, String why) {
        if (reported.add(what)) {
            TaczSpray.LOG.warn("{} is drawn with the gun's own texture: {}", what, why);
        }
    }

    /** New resources (a resource pack, F3+T): everything is read and composed again. */
    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        made.values().stream().filter(m -> m.id() != NONE)
                .forEach(m -> Minecraft.getInstance().getTextureManager().release(m.id()));
        made.clear();
        data.clear();
        shared.clear();
        bases.clear();
        reported.clear();
    }
}
