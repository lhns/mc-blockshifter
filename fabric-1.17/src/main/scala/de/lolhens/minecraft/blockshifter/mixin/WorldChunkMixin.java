package de.lolhens.minecraft.blockshifter.mixin;

import de.lolhens.minecraft.blockshifter.util.WorldUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Inject(at = @At("HEAD"), method = "getBlockEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/chunk/WorldChunk$CreationType;)Lnet/minecraft/block/entity/BlockEntity;", cancellable = true)
    public void getBlockEntity(BlockPos pos, WorldChunk.CreationType creationType, CallbackInfoReturnable<BlockEntity> info) {
        BlockEntity nextCreatedBlockEntity = WorldUtil.popNextCreatedBlockEntity();
        if (nextCreatedBlockEntity != null) {
            ((WorldChunk) (Object) this).addBlockEntity(nextCreatedBlockEntity);
            info.setReturnValue(nextCreatedBlockEntity);
        }
    }
}
