package de.lolhens.minecraft.blockshifter.mixin;

import de.lolhens.minecraft.blockshifter.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(at = @At("HEAD"), method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;", cancellable = true)
    public void getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType, CallbackInfoReturnable<BlockEntity> info) {
        BlockEntity nextCreatedBlockEntity = WorldUtil.popNextCreatedBlockEntity();
        if (nextCreatedBlockEntity != null) {
            ((LevelChunk) (Object) this).addAndRegisterBlockEntity(nextCreatedBlockEntity);
            info.setReturnValue(nextCreatedBlockEntity);
        }
    }
}
