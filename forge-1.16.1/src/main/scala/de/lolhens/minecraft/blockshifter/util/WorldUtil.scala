package de.lolhens.minecraft.blockshifter.util

import net.minecraft.block.BlockState
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object WorldUtil {
  private var _nextCreatedBlockEntity: TileEntity = _

  private def setNextCreatedBlockEntity(entity: TileEntity): Unit =
    _nextCreatedBlockEntity = entity

  def popNextCreatedBlockEntity: TileEntity = {
    val entity = _nextCreatedBlockEntity
    _nextCreatedBlockEntity = null
    entity
  }

  def setBlockStateWithBlockEntity(world: World, pos: BlockPos, state: BlockState, entity: TileEntity, flags: Int): Boolean = {
    if (entity == null) {
      world.setBlockState(pos, state, flags)
    } else {
      val cancelRemoval = !entity.isRemoved
      world.removeTileEntity(pos)
      if (cancelRemoval) entity.validate()
      setNextCreatedBlockEntity(entity)
      val result = world.setBlockState(pos, state, flags)
      entity.updateContainingBlockInfo()
      result
    }
  }
}
