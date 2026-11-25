package io.github.ryukadev.taczspray.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.mixin.RenderBuffersAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A gun drawn in a screen as an item frame shows it: the 3D model, with its finish (the finish is on the stack's tag,
 * and the gun's texture is swapped wherever TaCZ draws it), fitted to a box and turning a little. TaCZ's item frame
 * pose keeps each gun's real size and does not centre it, so each gun's model is measured once (by catching the
 * corners it draws) and then scaled and centred to fit.
 */
final class GunModel {
    private static final Map<String, Bounds> BOUNDS = new HashMap<>();

    private GunModel() {
    }

    /** The model's extent in the item frame pose, in blocks. */
    private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        float width() {
            return maxX - minX;
        }

        float height() {
            return maxY - minY;
        }
    }

    /** Forgets the measured guns (a resource reload may change the models). */
    static void forget() {
        BOUNDS.clear();
    }

    /**
     * @param x, y  the middle of the box
     * @param w, h  the box, in GUI pixels
     * @param yaw   degrees about the vertical axis; 0 shows the gun's side
     */
    static void draw(GuiGraphics g, ItemStack gun, float x, float y, float w, float h, float yaw) {
        Bounds b = bounds(gun);
        if (b == null) {
            return;
        }
        float size = Math.min(w / b.width(), h / b.height());
        Minecraft mc = Minecraft.getInstance();
        // what the screen drew so far goes out first; TaCZ's model (a scope's, on 1.1.8) may leave the matrices
        // changed, so they are put back after it
        g.flush();
        RenderSystem.backupProjectionMatrix();
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        // screens count y downwards, models upwards
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(12));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-(b.minX() + b.maxX()) / 2, -(b.minY() + b.maxY()) / 2, -(b.minZ() + b.maxZ()) / 2);
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(gun, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, g.bufferSource(), mc.level, 0);
        g.flush();
        pose.popPose();
        view.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Measures the gun now, when a screen opens, rather than in the middle of its animation: the first time TaCZ
     * draws a gun it may set things up, which can spoil that one frame.
     */
    static void prepare(ItemStack gun) {
        bounds(gun);
    }

    private static Bounds bounds(ItemStack gun) {
        String key = FinishTables.gunId(gun);
        if (key == null) {
            return null;
        }
        Bounds known = BOUNDS.get(key);
        if (known != null) {
            return known;
        }
        Corners corners = new Corners();
        Minecraft mc = Minecraft.getInstance();
        // TaCZ 1.2 draws into the buffers it is handed, 1.1.8 into the game's shared ones: catch both
        RenderBuffersAccess buffers = (RenderBuffersAccess) mc.renderBuffers();
        MultiBufferSource.BufferSource shared = buffers.taczspray$bufferSource();
        buffers.taczspray$setBufferSource(new Catcher(corners));
        RenderSystem.backupProjectionMatrix();
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        try {
            mc.getItemRenderer().renderStatic(gun, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, new PoseStack(), type -> corners, mc.level, 0);
        } finally {
            buffers.taczspray$setBufferSource(shared);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
        Bounds b = corners.bounds();
        TaczSpray.LOG.debug("Gun model {} measured for screens: {} ({} vertices)", key, b, corners.count);
        if (b != null) {
            BOUNDS.put(key, b);
        }
        return b;
    }

    /** Stands in for the game's shared buffers while a gun is measured: every buffer is the one that notes corners. */
    private static final class Catcher extends MultiBufferSource.BufferSource {
        private final Corners corners;

        Catcher(Corners corners) {
            super(new BufferBuilder(256), Map.of());
            this.corners = corners;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return corners;
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatch(RenderType type) {
        }

        @Override
        public void endLastBatch() {
        }
    }

    /**
     * Takes in vertices to note how far the gun reaches. Some models also draw helper faces nobody sees (a transparent
     * sheet above the gun, a point at the muzzle); they lie apart from the gun. So on each axis, the faces' spans are
     * joined where they overlap or nearly touch, and the extent is that of the group with the most faces.
     */
    private static final class Corners implements VertexConsumer {
        /** Spans closer than this share of the whole extent count as touching. */
        private static final float TOUCH = 0.01f;
        private final List<float[]> faces = new ArrayList<>();
        private float[] face;
        private int corners;
        private int count;

        Bounds bounds() {
            if (faces.isEmpty()) {
                return null;
            }
            float[] x = run(0);
            float[] y = run(1);
            float[] z = run(2);
            return x[1] > x[0] && y[1] > y[0] ? new Bounds(x[0], x[1], y[0], y[1], z[0], z[1]) : null;
        }

        /** On one axis: the joined spans with the most faces. */
        private float[] run(int axis) {
            List<float[]> spans = new ArrayList<>();
            float lo = Float.MAX_VALUE;
            float hi = -Float.MAX_VALUE;
            for (float[] f : faces) {
                spans.add(new float[]{f[axis * 2], f[axis * 2 + 1]});
                lo = Math.min(lo, f[axis * 2]);
                hi = Math.max(hi, f[axis * 2 + 1]);
            }
            spans.sort((a, b) -> Float.compare(a[0], b[0]));
            float touch = Math.max(1e-4f, (hi - lo) * TOUCH);
            float[] best = null;
            int bestCount = 0;
            float[] now = null;
            int nowCount = 0;
            for (float[] span : spans) {
                if (now != null && span[0] <= now[1] + touch) {
                    now[1] = Math.max(now[1], span[1]);
                    nowCount++;
                } else {
                    now = span.clone();
                    nowCount = 1;
                }
                if (nowCount > bestCount) {
                    best = now;
                    bestCount = nowCount;
                }
            }
            return best;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            count++;
            if (face == null) {
                face = new float[]{Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                        Float.MAX_VALUE, -Float.MAX_VALUE};
            }
            float[] v = {(float) x, (float) y, (float) z};
            for (int a = 0; a < 3; a++) {
                face[a * 2] = Math.min(face[a * 2], v[a]);
                face[a * 2 + 1] = Math.max(face[a * 2 + 1], v[a]);
            }
            // quads: every four corners make a face
            if (++corners == 4) {
                faces.add(face);
                face = null;
                corners = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
