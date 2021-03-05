package de.lolhens.minecraft.blockshifter.mixin;

import de.lolhens.minecraft.blockshifter.util.WorldUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Inject(at = @At("HEAD"), method = "createBlockEntity", cancellable = true)
    private void createBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> info) {
        BlockEntity nextCreatedBlockEntity = WorldUtil.popNextCreatedBlockEntity();
        if (nextCreatedBlockEntity != null) {
            info.setReturnValue(nextCreatedBlockEntity);
        }
    }
    /*@ModifyVariable(method = "setBlockEntity", at = @At("HEAD"))
    private BlockEntity setBlockEntity(BlockEntity blockEntity) {

    }*/
}
