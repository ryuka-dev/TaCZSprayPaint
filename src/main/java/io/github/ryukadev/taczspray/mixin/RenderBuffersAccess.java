package io.github.ryukadev.taczspray.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The game's shared buffers, swappable for a moment: TaCZ 1.1.8 draws a gun into them rather than into the buffers it
 * is handed, so measuring a gun's model ({@code client.GunModel}) has to stand in for them while it draws.
 */
@Mixin(RenderBuffers.class)
public interface RenderBuffersAccess {
    @Accessor("bufferSource")
    MultiBufferSource.BufferSource taczspray$bufferSource();

    @Mutable
    @Accessor("bufferSource")
    void taczspray$setBufferSource(MultiBufferSource.BufferSource source);
}
