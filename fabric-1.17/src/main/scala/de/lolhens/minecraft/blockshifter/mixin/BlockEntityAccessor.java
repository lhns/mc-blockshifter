package de.lolhens.minecraft.blockshifter.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockEntity.class)
public interface BlockEntityAccessor {
    @Accessor("pos")
    void setPos(BlockPos pos);
}
