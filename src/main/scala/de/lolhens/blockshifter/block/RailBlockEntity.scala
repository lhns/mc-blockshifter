package de.lolhens.blockshifter.block

import net.minecraft.block.Blocks
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.util.Tickable

class RailBlockEntity(`type`: BlockEntityType[RailBlockEntity]) extends BlockEntity(`type`) with Tickable {
  override def tick(): Unit = {
    world.setBlockState(pos.up(), Blocks.MAGMA_BLOCK.getDefaultState)
  }
}
