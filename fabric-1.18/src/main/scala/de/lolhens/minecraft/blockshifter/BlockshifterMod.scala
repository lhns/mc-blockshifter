package de.lolhens.minecraft.blockshifter

import de.lolhens.minecraft.blockshifter.block.RailBlock
import de.lolhens.minecraft.blockshifter.util.EntityMover
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object BlockshifterMod extends ModInitializer {
  val id: String = "blockshifter"

  val RAIL_BLOCK_ID = new Identifier(id, "rail")
  val RAIL_BLOCK: RailBlock = new RailBlock()

  override def onInitialize(): Unit = {
    Registry.register(Registry.BLOCK, RAIL_BLOCK_ID, RAIL_BLOCK)
    Registry.register(Registry.ITEM, RAIL_BLOCK_ID, new BlockItem(RAIL_BLOCK, new Item.Settings().group(ItemGroup.REDSTONE)))

    ServerTickEvents.START_WORLD_TICK.register { world =>
      EntityMover(world).moveAll()
    }
  }
}
