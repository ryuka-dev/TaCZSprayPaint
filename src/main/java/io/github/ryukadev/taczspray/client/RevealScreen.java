package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.net.RevealMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The reveal after a spray: the screen dims and a reel of finishes scrolls, easing out over six to seven and a half
 * seconds onto the one the gun got, the marker not always mid-cell; each reveal its own ending, from a crisp stop to a
 * long slow creep over the last cell. Then that cell grows in the middle, rings of its grade's colour spread, the name
 * twice the size and under it the grade, wear band and wear. A tick for each cell passing the marker, the landing's
 * sound by grade. Then the winning cell grows into a stage with the pattern and the gun itself, turning (the gun is
 * in the off hand, which TaCZ does not draw in first person), and the wear bar under the text.
 * <p>
 * Any key or click skips it (but not in its first moments, so the click that finished the spray does not, nor a key
 * held since before it opened, as sneak or a movement key, until it is let go); taking
 * damage closes it. Either way, if the finish was not shown yet it is put in the chat. Once shown, any key or click
 * closes it. The reel is the server's; this only plays it.
 */
public final class RevealScreen extends Screen {
    private static final long DIM_MS = 300;
    private static final long SHORTEST_MS = 6000;
    private static final long LONGEST_MS = 7500;
    private static final float CRISP = 2.6f;
    private static final float CREEP = 4.8f;
    private static final long GROW_MS = 400;
    private static final long RINGS_MS = 700;
    private static final long TWINKLE_MS = 250;
    /** Input in the first moments is the spray's own, not a skip. */
    private static final long GRACE_MS = 300;

    private static final int TILE = 60;
    private static final int STEP = TILE + 4;
    private static final int REEL_H = 72;
    private static final int REEL_W = 600;
    private static final int BLOCK_H = 172;
    private static final int FADE = 18;
    private static final int BACKGROUND = 0x151515;
    private static final int DIM = 0x08060C;
    private static final int GOLD = 0xF2C14E;
    private static final int SUB = 0xD0D0D0;
    private static final int HINT = 0x8A8A8A;
    private static final int STAGE_W = 300;
    private static final int STAGE_H = 96;
    private static final int WEAR_W = 160;

    private final RevealMessage reveal;
    private final List<ResourceLocation> patterns = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final float power;
    private final long reelMs;
    private final long landedAt;
    private final long revealAt;
    private final long textAt;
    private final long start = Util.getMillis();
    private int ticks = -1;
    private boolean landed;
    private boolean twinkled;
    private int lastHurt = -1;
    private final HeldKeys held = HeldKeys.now();

    private RevealScreen(RevealMessage reveal) {
        super(Component.translatable("taczspray.reveal.title"));
        this.reveal = reveal;
        // only the show, so drawn here; the reel and where it stops are the server's
        float u = (float) Math.random();
        power = CRISP + (CREEP - CRISP) * u;
        // a can for one finish has nothing to scroll past: the reel is that one cell, shown at once
        reelMs = reveal.reel().size() > 1 ? SHORTEST_MS + Math.round((LONGEST_MS - SHORTEST_MS) * u) : 1;
        landedAt = DIM_MS + reelMs;
        revealAt = landedAt + 150;
        textAt = revealAt + 300;
        for (RevealMessage.Cell c : reveal.reel()) {
            patterns.add(ClientCatalog.pattern(c.id(), c.look()));
            names.add(ClientCatalog.lookName(c.look()).getString());
        }
    }

    @Override
    protected void init() {
        GunModel.prepare(reveal.gun());
    }

    public static void open(RevealMessage reveal) {
        Minecraft.getInstance().setScreen(new RevealScreen(reveal));
    }

    public static int gradeColour(int grade) {
        return switch (grade) {
            case 1 -> 0x4FB848;
            case 2 -> 0x4B69FF;
            case 3 -> 0x8847FF;
            case 4 -> 0xD32CE6;
            case 5 -> 0xEB4B4B;
            default -> 0xA0A0A0;
        };
    }

    private long elapsed() {
        return Util.getMillis() - start;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return !held.fresh(key) || input();
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        held.released(key);
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return input();
    }

    /** A skip while it plays, a close once the finish is shown; nothing in its first moments. */
    private boolean input() {
        long t = elapsed();
        if (t >= GRACE_MS) {
            close(t < textAt);
        }
        return true;
    }

    /** Taking damage closes it: there may be someone to deal with. */
    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (lastHurt >= 0 && player.hurtTime > lastHurt) {
            close(elapsed() < textAt);
            return;
        }
        lastHurt = player.hurtTime;
    }

    private void close(boolean unseen) {
        if (unseen) {
            Minecraft.getInstance().gui.getChat().addMessage(resultLine(reveal.won()));
        }
        onClose();
    }

    /** The finish as one chat line: its name and grade in the grade's colour, the wear band and wear. */
    public static Component resultLine(Finish finish) {
        Style colour = Style.EMPTY.withColor(TextColor.fromRgb(gradeColour(finish.grade())));
        return Component.translatable("taczspray.reveal.chat",
                ClientCatalog.lookName(finish.look()).copy().withStyle(colour),
                FinishText.grade(finish.grade()).copy().withStyle(colour),
                FinishText.band(finish.band()), FinishText.wear(finish.wear()));
    }

    /** The same without the chat line's lead-in, for a screen: name · grade · band wear. */
    static Component summary(Finish finish) {
        Style colour = Style.EMPTY.withColor(TextColor.fromRgb(gradeColour(finish.grade())));
        Component dot = Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
        return Component.empty()
                .append(ClientCatalog.lookName(finish.look()).copy().withStyle(colour)).append(dot)
                .append(FinishText.grade(finish.grade()).copy().withStyle(colour)).append(dot)
                .append(FinishText.band(finish.band())).append(" " + FinishText.wear(finish.wear()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = elapsed();
        float dim = clamp01(t / (float) DIM_MS);
        g.fill(0, 0, width, height, argb(0.78f * dim, DIM));
        int rw = Math.min(REEL_W, width - 32);
        int rx = (width - rw) / 2;
        int ry = Math.max(8, (height - BLOCK_H) / 2);
        drawReel(g, t, rx, ry, rw);
        sounds(t);
        if (t < textAt) {
            Font font = Minecraft.getInstance().font;
            String skip = Component.translatable("taczspray.reveal.skip").getString();
            g.drawString(font, skip, (width - font.width(skip)) / 2, ry + 164, HINT, true);
        }
        if (t >= revealAt) {
            // above the reel's names: text sits a little in front of the fills drawn at the same depth
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            drawWinner(g, t, ry);
            g.pose().popPose();
        }
    }

    private void drawReel(GuiGraphics g, long t, int rx, int ry, int rw) {
        float p = clamp01((t - DIM_MS) / (float) reelMs);
        // the winner's middle reaches the marker less the landing offset, so the marker stops inside its cell
        float off = (1 - (float) Math.pow(1 - p, power)) * (reveal.winAt() + (float) reveal.land()) * STEP;
        int centre = rx + rw / 2;
        g.fill(rx, ry, rx + rw, ry + REEL_H, opaque(BACKGROUND));
        g.enableScissor(rx, ry, rx + rw, ry + REEL_H);
        int ty = ry + (REEL_H - TILE) / 2;
        for (int i = 0; i < reveal.reel().size(); i++) {
            int tx = Math.round(centre - off + i * STEP - TILE / 2f);
            if (tx + TILE < rx || tx > rx + rw) {
                continue;
            }
            drawCell(g, i, tx, ty, TILE, true);
        }
        boolean stopped = t >= landedAt;
        int marker = stopped ? gradeColour(reveal.won().grade()) : GOLD;
        g.fill(centre - 1, ry, centre + 1, ry + REEL_H, opaque(marker));
        g.fill(centre - 3, ry, centre + 3, ry + 2, opaque(GOLD));
        g.fill(centre - 3, ry + REEL_H - 2, centre + 3, ry + REEL_H, opaque(GOLD));
        // the ends fade into the dark
        for (int k = 0; k < FADE; k++) {
            int fade = argb(1 - k / (float) FADE, BACKGROUND);
            g.fill(rx + k, ry, rx + k + 1, ry + REEL_H, fade);
            g.fill(rx + rw - 1 - k, ry, rx + rw - k, ry + REEL_H, fade);
        }
        g.disableScissor();
        // a tick each time a cell's edge crosses the marker
        int n = (int) Math.floor((off + STEP / 2f) / STEP);
        if (n != ticks && t < landedAt + 30 && t >= DIM_MS) {
            ticks = n;
            play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.35f);
        }
    }

    /** A cell: its pattern, the grade's colour along the bottom, the name in the corner. */
    private void drawCell(GuiGraphics g, int i, int x, int y, int size, boolean named) {
        RevealMessage.Cell c = reveal.reel().get(i);
        int colour = gradeColour(c.grade());
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, opaque(0x000000));
        // the whole tile, whatever its size, nearest-neighbour as the game draws its textures
        g.blit(patterns.get(i), x, y, size, size, 0, 0, 1, 1, 1, 1);
        int bar = size >= 50 ? 5 : 3;
        g.fill(x, y + size - bar, x + size, y + size, opaque(colour));
        if (named) {
            Font font = Minecraft.getInstance().font;
            String name = fit(font, names.get(i), size - 6);
            g.drawString(font, name, x + 4, y + 3, 0xFFFFFF, true);
        }
    }

    /** As much of {@code s} as fits in {@code width}, ending in "..." when it was cut. */
    static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) {
            return s;
        }
        String more = CommonComponents.ELLIPSIS.getString();
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width(more))) + more;
    }

    private void drawWinner(GuiGraphics g, long t, int ry) {
        Finish won = reveal.won();
        int colour = gradeColour(won.grade());
        int cx = width / 2;
        int cy = ry + REEL_H / 2;
        // rings of the grade's colour spreading out
        float rp = clamp01((t - revealAt) / (float) RINGS_MS);
        for (int k = 0; k < 3; k++) {
            float r = (rp * 1.5f - k * 0.25f) * 140;
            if (r > 0 && r < 140) {
                ring(g, cx, cy, r, argb(1 - r / 140, colour));
            }
        }
        // the winning cell grows into a stage: the pattern on the left, the gun turning to the right of it
        float p = outQuart(clamp01((t - revealAt) / (float) GROW_MS));
        int sw = Math.round(TILE + (Math.min(STAGE_W, width - 24) - TILE) * p);
        int sh = Math.round(TILE + (STAGE_H - TILE) * p);
        int x = cx - sw / 2;
        int y = cy - sh / 2;
        g.fill(x - 2, y - 2, x + sw + 2, y + sh + 2, opaque(colour));
        g.fill(x, y, x + sw, y + sh, opaque(BACKGROUND));
        int swatch = sh - 12;
        drawCell(g, reveal.winAt(), x + 6, y + 6, swatch, false);
        if (p > 0.6f) {
            int gx = x + 6 + swatch + (sw - swatch - 6) / 2;
            float yaw = (float) Math.sin((t - revealAt) / 1100.0) * 28;
            g.enableScissor(x, y, x + sw, y + sh);
            GunModel.draw(g, reveal.gun(), gx, cy, sw - swatch - 30, sh - 20, yaw);
            g.disableScissor();
        }
        if (t < textAt) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        // a darker backing, so the chat behind the screen does not show through the text
        int back = Math.min(width - 8, 340);
        g.fill(cx - back / 2, ry + 94, cx + back / 2, ry + 176, 0xB0000000);
        // twice the size, unless that is too wide (a long name in some languages); then cut if still too wide
        int room = width - 16;
        String title = names.get(reveal.winAt());
        int scale = font.width(title) * 2 <= room ? 2 : 1;
        title = fit(font, title, room / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(font, title, Math.round((cx - font.width(title) * scale / 2f) / scale),
                Math.round((ry + 100 + (2 - scale) * 4.5f) / scale), 0xFFFFFF, true);
        g.pose().popPose();
        String grade = FinishText.grade(won.grade()).getString();
        String rest = "  " + FinishText.band(won.band()).getString() + " " + FinishText.wear(won.wear());
        int sx = cx - (font.width(grade) + font.width(rest)) / 2;
        g.drawString(font, grade, sx, ry + 128, colour, true);
        g.drawString(font, rest, sx + font.width(grade), ry + 128, SUB, true);
        WearBar.draw(g, cx - WEAR_W / 2, ry + 144, WEAR_W, 4, won.band(), won.wear(), ClientCatalog.bands());
        String hint = Component.translatable("taczspray.reveal.continue").getString();
        g.drawString(font, hint, cx - font.width(hint) / 2, ry + 164, HINT, true);
    }

    /** The landing: the first three grades one sound, the fourth with a twinkle after it, the fifth its own. */
    private void sounds(long t) {
        int grade = reveal.won().grade();
        if (t >= landedAt && !landed) {
            landed = true;
            if (grade >= 5) {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
            } else {
                play(SoundEvents.PLAYER_LEVELUP, 1f, 0.6f);
            }
        }
        if (grade == 4 && t >= landedAt + TWINKLE_MS && !twinkled) {
            twinkled = true;
            play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 0.7f);
        }
    }

    /** A ring three pixels thick, as squares round the circle. */
    private static void ring(GuiGraphics g, int cx, int cy, float r, int argb) {
        int n = Math.max(24, (int) (Math.PI * r));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 2, y + 2, argb);
        }
    }

    private static float outQuart(float p) {
        float q = 1 - p;
        return 1 - q * q * q * q;
    }

    private static float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    /** Text with an alpha under 4 is drawn opaque by the font; keeps it at least 4. */
    private static int argb(float alpha, int rgb) {
        int a = Math.max(4, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    static void play(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
