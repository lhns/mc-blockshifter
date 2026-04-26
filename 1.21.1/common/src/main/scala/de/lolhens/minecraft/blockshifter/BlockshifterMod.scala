package de.lolhens.minecraft.blockshifter

import de.lolhens.minecraft.blockshifter.block.RailBlock
import net.minecraft.resources.ResourceLocation

object BlockshifterMod {
  val id: String = "blockshifter"

  val RAIL_BLOCK_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(id, "rail")
  val RAIL_BLOCK: RailBlock = new RailBlock()
}
