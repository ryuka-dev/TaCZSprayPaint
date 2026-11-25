package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.net.ChoiceMessage;
import io.github.ryukadev.taczspray.net.ChooseMessage;
import io.github.ryukadev.taczspray.net.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A ten-pull to pick from ({@link ChoiceMessage}). Fresh from the can, the ten turn over one by one, a tick each and
 * the best one's landing sound at the end; any key or click turns the rest over at once. Then hovering one shows it
 * on the gun, a click picks it, and the button keeps it. One has to be kept: Esc or taking damage closes the screen
 * but leaves the ten on the gun, with a chat link to come back ({@code /taczspray choose}).
 */
public final class PickScreen extends Screen {
    private static final long DIM_MS = 300;
    private static final long FLIP_MS = 180;
    private static final long GRACE_MS = 300;
    private static final int TILE = 40;
    private static final int GAP = 6;
    private static final int COLS = 5;
    private static final int STAGE_W = 300;
    private static final int STAGE_H = 64;
    private static final int HEIGHT = 222;
    private static final int WEAR_W = 160;
    private static final int BACKGROUND = 0x151515;
    private static final int HINT = 0x8A8A8A;
    private static final int SUB = 0xD0D0D0;

    private final ChoiceMessage choice;
    private final List<ResourceLocation> patterns = new ArrayList<>();
    private final List<ItemStack> guns = new ArrayList<>();
    private final long start = Util.getMillis();
    private boolean turnedAll;
    private int ticked;
    private boolean landed;
    private int picked = -1;
    private boolean kept;
    private int lastHurt = -1;
    private Button keep;
    private int top;

    private PickScreen(ChoiceMessage choice) {
        super(Component.translatable("taczspray.pick.title"));
        this.choice = choice;
        turnedAll = !choice.fresh();
        for (Finish f : choice.pull().finishes()) {
            patterns.add(ClientCatalog.pattern(f.id(), f.look()));
            ItemStack gun = choice.gun().copy();
            f.writeTo(gun);
            guns.add(gun);
        }
    }

    public static void open(ChoiceMessage choice) {
        Minecraft.getInstance().setScreen(new PickScreen(choice));
    }

    @Override
    protected void init() {
        top = Math.max(4, (height - HEIGHT) / 2);
        GunModel.prepare(choice.gun());
        keep = addRenderableWidget(Button.builder(Component.translatable("taczspray.pick.confirm"), b -> keep())
                .bounds(width / 2 - 60, top + HEIGHT - 20, 120, 20).build());
        keep.active = picked >= 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long elapsed() {
        return Util.getMillis() - start;
    }

    private boolean turned(int i) {
        return turnedAll || elapsed() >= DIM_MS + i * FLIP_MS;
    }

    private boolean allTurned() {
        return turned(choice.pull().finishes().size() - 1);
    }

    private int gridX() {
        return width / 2 - (COLS * TILE + (COLS - 1) * GAP) / 2;
    }

    private int gridY() {
        return top + 12;
    }

    /** The tile under the point, or -1. */
    private int tileAt(double x, double y) {
        for (int i = 0; i < choice.pull().finishes().size(); i++) {
            int tx = gridX() + (i % COLS) * (TILE + GAP);
            int ty = gridY() + (i / COLS) * (TILE + GAP);
            if (x >= tx && x < tx + TILE && y >= ty && y < ty + TILE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE && !allTurned()) {
            if (elapsed() >= GRACE_MS) {
                turnedAll = true;
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!allTurned()) {
            if (elapsed() >= GRACE_MS) {
                turnedAll = true;
            }
            return true;
        }
        int i = tileAt(x, y);
        if (i >= 0) {
            pick(i);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    /** Picks one of the ten (shown on the gun until another is picked); the button then keeps it. */
    public void pick(int i) {
        if (i >= 0 && i < choice.pull().finishes().size() && allTurned()) {
            picked = i;
            keep.active = true;
            RevealScreen.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f);
        }
    }

    /** Taking damage closes it (the ten stay on the gun). */
    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (lastHurt >= 0 && player.hurtTime > lastHurt) {
            onClose();
            return;
        }
        lastHurt = player.hurtTime;
    }

    /** What the button does: keeps the picked one, if one is. */
    public void keep() {
        if (picked < 0) {
            return;
        }
        Network.sendToServer(new ChooseMessage(choice.pull().nonce(), picked));
        kept = true;
        Minecraft.getInstance().gui.getChat().addMessage(RevealScreen.resultLine(choice.pull().finishes().get(picked)));
        onClose();
    }

    @Override
    public void onClose() {
        if (!kept) {
            Component link = Component.translatable("taczspray.pick.link").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/taczspray choose"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("taczspray.pick.link.hover"))));
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("taczspray.pick.later", link));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = elapsed();
        g.fill(0, 0, width, height, argb(0.78f * Math.min(1f, t / (float) DIM_MS), 0x08060C));
        Font font = Minecraft.getInstance().font;
        int cx = width / 2;
        g.drawString(font, title, cx - font.width(title) / 2, top, 0xFFFFFF, true);
        List<Finish> finishes = choice.pull().finishes();
        int hovered = allTurned() ? tileAt(mouseX, mouseY) : -1;
        for (int i = 0; i < finishes.size(); i++) {
            int x = gridX() + (i % COLS) * (TILE + GAP);
            int y = gridY() + (i / COLS) * (TILE + GAP);
            drawTile(g, font, i, x, y, i == picked, i == hovered);
        }
        sounds();
        int shown = hovered >= 0 ? hovered : picked;
        int stageY = gridY() + 2 * TILE + GAP + 8;
        int sw = Math.min(STAGE_W, width - 24);
        int border = shown >= 0 ? RevealScreen.gradeColour(finishes.get(shown).grade()) : 0x404040;
        g.fill(cx - sw / 2 - 2, stageY - 2, cx + sw / 2 + 2, stageY + STAGE_H + 2, 0xFF000000 | border);
        g.fill(cx - sw / 2, stageY, cx + sw / 2, stageY + STAGE_H, 0xFF000000 | BACKGROUND);
        int textY = stageY + STAGE_H + 6;
        Component line = shown >= 0 ? RevealScreen.summary(finishes.get(shown)) : Component.empty();
        // a darker backing, so the chat behind the screen does not show through the text
        int back = Math.min(width - 4, Math.max(sw + 4, font.width(line) + 12));
        g.fill(cx - back / 2, textY - 3, cx + back / 2, textY + 22, 0xB0000000);
        if (shown >= 0) {
            g.enableScissor(cx - sw / 2, stageY, cx + sw / 2, stageY + STAGE_H);
            float yaw = (float) Math.sin(t / 1100.0) * 28;
            GunModel.draw(g, guns.get(shown), cx, stageY + STAGE_H / 2f, sw - 24, STAGE_H - 12, yaw);
            g.disableScissor();
            Finish f = finishes.get(shown);
            g.drawString(font, line, cx - font.width(line) / 2, textY, SUB, true);
            WearBar.draw(g, cx - WEAR_W / 2, textY + 14, WEAR_W, 4, f.band(), f.wear(), ClientCatalog.bands());
        } else {
            Component hint = Component.translatable(allTurned() ? "taczspray.pick.hint" : "taczspray.pick.skip");
            g.drawString(font, hint, cx - font.width(hint) / 2, stageY + STAGE_H / 2 - 4, HINT, true);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** One of the ten: face down until turned, then its pattern with its grade's colour along the bottom. */
    private void drawTile(GuiGraphics g, Font font, int i, int x, int y, boolean picked, boolean hovered) {
        if (!turned(i)) {
            g.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, 0xFF000000);
            g.fill(x, y, x + TILE, y + TILE, 0xFF2A2A2A);
            g.drawString(font, "?", x + TILE / 2 - font.width("?") / 2, y + TILE / 2 - 4, HINT, true);
            return;
        }
        int colour = RevealScreen.gradeColour(choice.pull().finishes().get(i).grade());
        int frame = picked ? 0xFFFFFF : hovered ? colour : 0x000000;
        int edge = picked || hovered ? 2 : 1;
        g.fill(x - edge, y - edge, x + TILE + edge, y + TILE + edge, 0xFF000000 | frame);
        g.blit(patterns.get(i), x, y, TILE, TILE, 0, 0, 1, 1, 1, 1);
        g.fill(x, y + TILE - 4, x + TILE, y + TILE, 0xFF000000 | colour);
    }

    /** A tick as each turns over; when the last has, the best one's landing sound. */
    private void sounds() {
        List<Finish> finishes = choice.pull().finishes();
        int now = 0;
        while (now < finishes.size() && turned(now)) {
            now++;
        }
        if (choice.fresh() && now > ticked && !turnedAll) {
            RevealScreen.play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.35f);
        }
        ticked = now;
        if (now == finishes.size() && !landed) {
            landed = true;
            int best = finishes.stream().mapToInt(Finish::grade).max().orElse(1);
            RevealScreen.play(best >= 5 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                    1f, 0.6f);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(4, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb;
    }
}
