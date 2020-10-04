package de.lolhens.minecraft.blockshifter.mixin;

import de.lolhens.minecraft.blockshifter.util.WorldUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(World.class)
public abstract class WorldMixin {
    @ModifyVariable(method = "setBlockEntity", at = @At("HEAD"))
    private BlockEntity setBlockEntity(BlockEntity blockEntity) {
        BlockEntity nextCreatedBlockEntity = WorldUtil.popNextCreatedBlockEntity();
        if (nextCreatedBlockEntity != null) {
            return nextCreatedBlockEntity;
        } else {
            return blockEntity;
        }
    }
}
