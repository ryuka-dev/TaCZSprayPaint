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

import java.util.HashMap;
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

    /** Takes in vertices only to note how far they reach. */
    private static final class Corners implements VertexConsumer {
        private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        private int count;

        Bounds bounds() {
            return maxX > minX && maxY > minY ? new Bounds(minX, maxX, minY, maxY, minZ, maxZ) : null;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            count++;
            minX = Math.min(minX, (float) x);
            maxX = Math.max(maxX, (float) x);
            minY = Math.min(minY, (float) y);
            maxY = Math.max(maxY, (float) y);
            minZ = Math.min(minZ, (float) z);
            maxZ = Math.max(maxZ, (float) z);
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
