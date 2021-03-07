package de.lolhens.minecraft.blockshifter.util

import de.lolhens.minecraft.blockshifter.mixin.BlockEntityAccessor
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object WorldUtil {
  private var _nextCreatedBlockEntity: BlockEntity = _

  private def setNextCreatedBlockEntity(entity: BlockEntity): Unit =
    _nextCreatedBlockEntity = entity

  def popNextCreatedBlockEntity: BlockEntity = {
    val entity = _nextCreatedBlockEntity
    _nextCreatedBlockEntity = null
    entity
  }

  def setBlockStateWithBlockEntity(world: World, pos: BlockPos, state: BlockState, entity: BlockEntity, flags: Int): Boolean = {
    if (entity == null) {
      world.setBlockState(pos, state, flags)
    } else {
      val cancelRemoval = !entity.isRemoved
      world.removeBlockEntity(pos)
      if (cancelRemoval) entity.cancelRemoval()
      entity.asInstanceOf[BlockEntityAccessor].setPos(pos.toImmutable)
      setNextCreatedBlockEntity(entity)
      val result = world.setBlockState(pos, state, flags)
      entity.setCachedState(state)
      result
    }
  }
}
