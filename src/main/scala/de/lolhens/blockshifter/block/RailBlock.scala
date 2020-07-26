package de.lolhens.blockshifter.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block._
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.world.BlockView

class RailBlock() extends Block(RailBlock.settings) with BlockEntityProvider {
  val blockEntityType: BlockEntityType[RailBlockEntity] =
    BlockEntityType.Builder.create(() => new RailBlockEntity(blockEntityType), this)
      .build(null)

  override def createBlockEntity(world: BlockView): BlockEntity = blockEntityType.instantiate()

  override def getPistonBehavior(state: BlockState): PistonBehavior = PistonBehavior.BLOCK
}

object RailBlock {
  private val settings =
    FabricBlockSettings
      .of(Material.STONE)
      .requiresTool()
      .hardness(2.0F)
}

