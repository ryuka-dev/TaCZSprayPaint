package io.github.ryukadev.taczspray.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The five wear bands side by side, each as wide as its share of 0 to 1 (so the first two are narrow, as they are),
 * the finish's band in full colour and the rest dimmed, and a mark where its wear is. In the reveal and, small, in a
 * painted gun's tooltip.
 */
public final class WearBar {
    /** Factory new to battle-scarred, green to red. */
    private static final int[] COLOURS = {0x3FAE5A, 0x9CCB3B, 0xE6C440, 0xE58A35, 0xD9443A};

    private WearBar() {
    }

    /**
     * @param band  the finish's band, 1 to 5, as the server placed it
     * @param bands where each band ends, from {@link ClientCatalog#bands()}
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, int band, double wear, double[] bands) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        double lower = 0;
        for (int i = 0; i < bands.length; i++) {
            int x0 = x + (int) Math.round(lower * w);
            int x1 = x + (int) Math.round(bands[i] * w);
            int c = i + 1 == band ? COLOURS[i] : dim(COLOURS[i]);
            g.fill(x0, y, x1, y + h, 0xFF000000 | c);
            lower = bands[i];
        }
        int mx = x + (int) Math.round(Math.max(0, Math.min(1, wear)) * (w - 1));
        // the mark: a white line through the bar with a cap above it, outlined in black to show on any colour
        g.fill(mx - 2, y - 4, mx + 3, y + h + 2, 0xFF000000);
        g.fill(mx - 1, y - 3, mx + 2, y - 1, 0xFFFFFFFF);
        g.fill(mx, y - 1, mx + 1, y + h + 1, 0xFFFFFFFF);
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 2 / 5 + 20;
        int gr = (rgb >> 8 & 0xFF) * 2 / 5 + 20;
        int b = (rgb & 0xFF) * 2 / 5 + 20;
        return r << 16 | gr << 8 | b;
    }

    /** The bar as a tooltip line. */
    public record Tooltip(int band, double wear) implements TooltipComponent {
    }

    /** Draws a {@link Tooltip}. */
    static final class Line implements ClientTooltipComponent {
        private static final int WIDTH = 100;
        private final Tooltip bar;

        Line(Tooltip bar) {
            this.bar = bar;
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return WIDTH;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            draw(g, x + 2, y + 4, WIDTH - 4, 3, bar.band(), bar.wear(), ClientCatalog.bands());
        }
    }
}
