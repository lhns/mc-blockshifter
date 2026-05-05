package de.lolhens.minecraft.blockshifter.util

import de.lolhens.minecraft.blockshifter.mixin.BlockEntityAccessor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

object WorldUtil {
  private var _nextCreatedBlockEntity: BlockEntity = _

  private def setNextCreatedBlockEntity(entity: BlockEntity): Unit =
    _nextCreatedBlockEntity = entity

  def popNextCreatedBlockEntity: BlockEntity = {
    val entity = _nextCreatedBlockEntity
    _nextCreatedBlockEntity = null
    entity
  }

  def setBlockStateWithBlockEntity(world: Level, pos: BlockPos, state: BlockState, entity: BlockEntity, flags: Int): Boolean = {
    if (entity == null) {
      world.setBlock(pos, state, flags)
    } else {
      val cancelRemoval = !entity.isRemoved
      world.removeBlockEntity(pos)
      if (cancelRemoval) entity.clearRemoved()
      entity.asInstanceOf[BlockEntityAccessor].setWorldPosition(pos.immutable)
      setNextCreatedBlockEntity(entity)
      val result = world.setBlock(pos, state, flags)
      entity.setBlockState(state)
      result
    }
  }
}
